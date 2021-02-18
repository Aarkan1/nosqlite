package database;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import database.handlers.WatchData;
import database.handlers.WatchHandler;
import org.sqlite.Function;
import utilities.Utils;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

class DbHelper {
  Connection conn;
  private BlockingDeque<Task> tasks = new LinkedBlockingDeque<>();
  private Map<String, List<WatchHandler>> watchers = new HashMap<>();
  private Map<String, Map<String, List<WatchHandler>>> eventWatchers = new HashMap<>();
  private AtomicBoolean isRunning = new AtomicBoolean(true);
  private boolean runAsync = true;
  private ObjectMapper mapper = new ObjectMapper();
  ThreadPoolExecutor watchExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(5);
  
  /**
   * Object to queue in the BlockingDeque
   */
  private class Task<T> {
    String method;
    String query;
    Object[] params;
    Class<T> coll;
    String collName;
    CompletableFuture<String[]> future;
    
    public Task(String method, String query, Object[] params, Class<T> coll, String collName, CompletableFuture<String[]> future) {
      this.method = method;
      this.query = query;
      this.params = params;
      this.coll = coll;
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
  DbHelper(Connection conn, boolean useRegex, boolean runAsync) throws SQLException {
    this.conn = conn;
    this.runAsync = runAsync;
    if (useRegex) addRegex(conn);
    
    if (runAsync) {
      new Thread(() -> {
        while (isRunning.get() || !tasks.isEmpty()) {
          try {
            Task task = tasks.take();
            
            if (task.method.equals("queryMany")) {
              String[] future = {"insert", queryMany(task.query, task.params, task.coll, task.collName)};
              task.future.complete(future);
            } else {
              String[] future = {task.method, query(task.query, task.params, task.collName)};
              task.future.complete(future);
            }
          } catch (InterruptedException | SQLException e) {
            e.printStackTrace();
          }
        }
        
        // stop
        watchExecutor.shutdown();
        
        try {
          conn.close();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }).start();
    }
    
    Runtime.getRuntime().addShutdownHook(new Thread(this::close));
  }
  
  void close() {
    isRunning.set(false);
    
    if(!runAsync) {
      try {
        conn.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }
  
  <T> String run(String method, String query, Object[] params, Class<T> coll, String collName) {
    String[] get = new String[2];
    // get[0] == event
    // get[1] == document
    
    if (runAsync) {
      CompletableFuture<String[]> future = new CompletableFuture<>();
      tasks.add(new Task(method, query, params, coll, collName, future));
      try {
        get = future.get();
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
    } else {
      try {
        if (method.equals("queryMany")) {
          get[0] = "insert";
          get[1] = queryMany(query, params, coll, collName);
        } else {
          get[0] = method;
          get[1] = query(query, params, collName);
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    
    if (!query.startsWith("CREATE")) {
      
      try {
        // don't bother converting json if there's no watchers
        if (!method.equals("none") && !get[1].endsWith("all") && (eventWatchers.get(collName) != null || watchers.get(collName) != null)) {
          updateWatchers(collName, get[0], new WatchData(collName, get[0],
              mapper.readValue("[" + get[1] + "]",
                  mapper.getTypeFactory().constructCollectionType(List.class, coll))));
        }
        
        return get[1];
      } catch (JsonProcessingException e) {
        e.printStackTrace();
      }
    }
    return null;
  }
  
  <T> String run(String method, String query, Class<T> coll, String collName) {
    return run(method, query, null, coll, collName);
  }
  
  private String query(String query, Object[] params, String collName) throws SQLException {
    PreparedStatement stmt = conn.prepareStatement(query);
    if (params != null) {
      for (int i = 0; i < params.length; i++) {
        Utils.setParams(i + 1, params[i], stmt);
      }
    }
    
    // fetch doc before delete
    if (query.startsWith("DELETE")) {
      String doc;
      if (params == null) {
        doc = "deleted all";
      } else {
        Object[] id = {params[0]};
        doc = get("SELECT value FROM " + collName + " WHERE key = ?", id);
      }
      stmt.executeUpdate();
      return doc;
    }
    
    stmt.executeUpdate();
    if (params == null) return null;
    
    if (query.startsWith("INSERT")) {
      Object[] id = {params[0]};
      return get("SELECT value FROM " + collName + " WHERE key = ?", id);
    }
    
    if (query.startsWith("CREATE")) return "created";
    
    if (params.length > 2) {
      String where = " " + query.substring(query.indexOf("WHERE"));
      Object[] p = new Object[params.length - 2];
      for (int i = 2; i < params.length; i++) {
        p[i - 2] = params[i];
      }
      return get("SELECT value FROM " + collName + where, p);
    }
    return "updated all";
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
          Utils.setParams(i + 1, params[i], stmt);
        }
      }
      ResultSet rs = stmt.executeQuery();
      return rs.getString(1);
    } catch (SQLException e) {
      // no document found
      if (!e.getMessage().equals("ResultSet closed")) {
        // TODO: Print useful message for bad search query
        
        e.printStackTrace(); // debug
      }
    }
    return null;
  }
  
  private <T> String queryMany(String query, Object[] documents, Class<T> coll, String collName) {
    List<String> docs = new ArrayList<>();
    try {
      conn.setAutoCommit(false);
      PreparedStatement stmt = conn.prepareStatement(query);
      
      for (Object model : documents) {
        Map<String, String> field = Utils.getIdField(model);
        String json = mapper.writeValueAsString(model);
        docs.add(json);
        
        stmt.setString(1, field.get("id"));
        stmt.setString(2, json);
        stmt.executeUpdate();
      }
      
      conn.commit();
      stmt.close();
      
      // don't bother converting json if there's no watchers
      if (eventWatchers.get(collName) != null || watchers.get(collName) != null) {
        updateWatchers(collName, "insert", new WatchData(collName, "insert",
            mapper.readValue(docs.toString(), mapper.getTypeFactory().constructCollectionType(List.class, coll))));
      }
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
  
  void watch(String collName, WatchHandler watcher) {
    watchers.putIfAbsent(collName, new ArrayList<>());
    watchers.get(collName).add(watcher);
  }
  
  void watch(String collName, String event, WatchHandler watcher) {
    eventWatchers.putIfAbsent(collName, new HashMap<>());
    eventWatchers.get(collName).putIfAbsent(event.toLowerCase(), new ArrayList<>());
    eventWatchers.get(collName).get(event.toLowerCase()).add(watcher);
  }
  
  void updateWatchers(String collName, String event, WatchData watchData) {
    if (event.equals("none")) return;
    
    if (eventWatchers.get(collName) != null && eventWatchers.get(collName).get(event) != null) {
      if (runAsync)
        watchExecutor.submit(() -> eventWatchers.get(collName).get(event).forEach(w -> w.handle(watchData)));
      else eventWatchers.get(collName).get(event).forEach(w -> w.handle(watchData));
    }
    if (watchers.get(collName) != null) {
      if (runAsync) watchExecutor.submit(() -> watchers.get(collName).forEach(w -> w.handle(watchData)));
      else watchers.get(collName).forEach(w -> w.handle(watchData));
    }
  }
  
  private void addRegex(Connection conn) throws SQLException {
    // Create regexp() function to make the REGEXP operator available
    Function.create(conn, "REGEXP", new Function() {
      @Override
      protected void xFunc() throws SQLException {
        String expression = value_text(0);
        String value = value_text(1);
        if (value == null)
          value = "";
        
        Pattern pattern = Pattern.compile(expression);
        result(pattern.matcher(value).find() ? 1 : 0);
      }
    });
  }
}
