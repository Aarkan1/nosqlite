package database;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import database.annotations.Id;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

class DbHelper extends Thread {
  private Connection conn;
  private BlockingDeque<Task> tasks = new LinkedBlockingDeque<>();
  private List<WatchHandler> watchers = new ArrayList<>();
  private Map<String, List<WatchHandler>> eventWatchers = new HashMap<>();
  private AtomicBoolean isRunning = new AtomicBoolean(true);
  private ObjectMapper mapper = new ObjectMapper();

  /**
   * Object to queue in the BlockingDeque
   */
  private class Task {
    String method;
    String query;
    Object[] params;
    String collName;
    CompletableFuture<String> future;

    public Task(String method, String query, Object[] params, String collName, CompletableFuture<String> future) {
      this.method = method;
      this.query = query;
      this.params = params;
      this.collName = collName;
      this.future = future;
    }
  }

  /**
   * Single thread to handle all write operations
   * to prevent concurrency
   *
   * @param conn The database connection
   */
  DbHelper(Connection conn) {
    this.conn = conn;

    new Thread(() -> {
      while (isRunning.get() || !tasks.isEmpty()) {
        try {
          Task task = tasks.take();

          if (task.method.equals("queryOne"))
            task.future.complete(query(task.query, task.params, task.collName));
          else if (task.method.equals("queryMany"))
            task.future.complete(queryMany(task.query, task.params, task.collName));
        } catch (InterruptedException | SQLException e) {
          e.printStackTrace();
        }
      }

      try {
        conn.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }).start();

    Runtime.getRuntime().addShutdownHook(new Thread(this::close));
  }

  void close() {
    isRunning.set(false);
  }

  String run(String method, String query, Object[] params, String collName) {
    CompletableFuture<String> future = new CompletableFuture<>();
    tasks.add(new Task(method, query, params, collName, future));
    try {
      if (!query.startsWith("create")) {
        String doc = future.get();
        String event = "save";
        if (query.startsWith("insert")) event = "save";
        if (query.startsWith("delete")) event = "delete";
        if (query.startsWith("update")) event = "update";

        updateWatchers(event, new WatchData(collName, event, Collections.singletonList(doc)));
        return doc;
      }
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
    return null;
  }

  String run(String method, String query, String collName) {
    return run(method, query, null, collName);
  }

  private String query(String query, Object[] params, String collName) throws SQLException {
    PreparedStatement stmt = conn.prepareStatement(query);
    if (params != null) {
      for (int i = 0; i < params.length; i++) {
        setParams(i + 1, params[i], stmt);
      }
    }

    // fetch doc before delete
    if (query.startsWith("delete")) {
      String doc;
      if (params == null) {
        doc = "deleted all";
      } else {
        Object[] id = {params[0]};
        doc = get("select value from " + collName + " where key = ?", id);
      }
      stmt.executeUpdate();
      return doc;
    }

    stmt.executeUpdate();
    if (params == null) return null;
    Object[] id = {params[0]};
    return get("select value from " + collName + " where key = ?", id);
  }

  // get don't require thread safety
  String get(String query) {
    return get(query, null);
  }

  String get(String query, Object[] params) {
    try {
      PreparedStatement stmt = conn.prepareStatement(query);
      if (params != null) {
        for (int i = 0; i < params.length; i++) {
          setParams(i + 1, params[i], stmt);
        }
      }
      ResultSet rs = stmt.executeQuery();
      return rs.getString(1);
    } catch (SQLException e) {
      // no document found
      if(!e.getMessage().equals("ResultSet closed")) {
        e.printStackTrace();
      }
    }
    return null;
  }

  private String queryMany(String query, Object[] documents, String collName) {
    List<String> docs = new ArrayList<>();
    try {
      conn.setAutoCommit(false);
      PreparedStatement stmt = conn.prepareStatement(query);

      for (Object model : documents) {
        Map<String, String> field = getIdField(model);
        String json = mapper.writeValueAsString(model);
        docs.add(json);

        stmt.setString(1, field.get("id"));
        stmt.setString(2, json);
        stmt.executeUpdate();
      }

      conn.commit();
      stmt.close();

      updateWatchers("save", new WatchData(collName, "save", docs));
      return mapper.writeValueAsString(docs);
    } catch (SQLException | JsonProcessingException e) {
      e.printStackTrace();
    } finally {
      try {
        conn.setAutoCommit(true);
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return null;
  }

  void watch(WatchHandler watcher) {
    watchers.add(watcher);
  }

  void watch(String event, WatchHandler watcher) {
    eventWatchers.putIfAbsent(event.toLowerCase(), new ArrayList<>());
    eventWatchers.get(event.toLowerCase()).add(watcher);
  }

  void updateWatchers(String event, WatchData watchData) {
    if (eventWatchers.get(event) != null) {
      eventWatchers.get(event).forEach(w -> w.handle(watchData));
    }
    watchers.forEach(w -> w.handle(watchData));
  }

  private void setParams(int index, Object param, PreparedStatement stmt) throws SQLException {
    if (param == null) {
      stmt.setNull(index, 0);
    } else if (param instanceof String) {
      stmt.setString(index, (String) param);
    } else if (param instanceof Integer) {
      stmt.setInt(index, (int) param);
    } else if (param instanceof Double) {
      stmt.setDouble(index, (double) param);
    } else if (param instanceof Float) {
      stmt.setFloat(index, (float) param);
    } else if (param instanceof Long) {
      stmt.setLong(index, (long) param);
    } else if (param instanceof Blob) {
      stmt.setBlob(index, (Blob) param);
    } else if (param instanceof Byte) {
      stmt.setByte(index, (byte) param);
    } else if (param instanceof byte[]) {
      stmt.setBytes(index, (byte[]) param);
    } else if (param instanceof InputStream) {
      stmt.setBlob(index, (InputStream) param);
    } else if (param instanceof Boolean) {
      stmt.setBoolean(index, (boolean) param);
    } else if (param instanceof Short) {
      stmt.setShort(index, (short) param);
    } else if (param instanceof Timestamp) {
      stmt.setTimestamp(index, (Timestamp) param);
    } else if (param instanceof URL) {
      stmt.setURL(index, (URL) param);
    } else {
      stmt.setObject(index, param);
    }
  }

  private Map<String, String> getIdField(Object model) {
    Map<String, String> idValues = new HashMap<>();

    try {
      for (Field field : model.getClass().getDeclaredFields()) {
        if (field.isAnnotationPresent(Id.class)) {
          field.setAccessible(true);
          if (field.get(model) == null) {
            // generate random id
            field.set(model, NanoIdUtils.randomNanoId());
          }
          idValues.put("name", field.getName());
          idValues.put("id", (String) field.get(model));
          break;
        }
      }
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    }
    return idValues;
  }
}
