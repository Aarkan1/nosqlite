package nosqlite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import nosqlite.handlers.WatchData;
import nosqlite.handlers.WatchHandler;
import org.sqlite.Function;
import nosqlite.utilities.Rewriter;
import nosqlite.utilities.Utils;

import java.lang.reflect.InvocationTargetException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * @author Johan Wirén
 */
@SuppressWarnings("unchecked")
class DbHelper {
  Connection conn;
  private final BlockingDeque<Task> tasks = new LinkedBlockingDeque<>();
  private final Map<String, List<WatchHandler>> watchers = new HashMap<>();
  private final Map<String, Map<String, List<WatchHandler>>> eventWatchers = new HashMap<>();
  private AtomicBoolean isRunning = new AtomicBoolean(true);
  private boolean runAsync;
  private boolean useRegex;
  private final ObjectMapper mapper = new ObjectMapper();
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
    this.useRegex = useRegex;
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
        
        // stop watch handlers
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
    
    if (!runAsync) {
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
      // don't bother converting json if there's no watchers
      if (!method.equals("none")
          && get[1] != null
          && !get[1].equals("deleted")
          && !get[1].endsWith("all")
          && (eventWatchers.get(collName) != null || watchers.get(collName) != null)) {
        
        // must be a json array for watchers
        if(!get[1].startsWith("[")) get[1] = "[" + get[1] + "]";
        updateWatchers(collName, get[0], get[1], coll);
      }
      
      return get[1];
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
      stmt.executeUpdate();
      return "deleted";
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
//      System.out.println(stmt.toString()); // debug
      
      ResultSet rs = stmt.executeQuery();
      return rs.getString(1);
    } catch (SQLException e) {
      // no document found
      if (e.getMessage().equals("ResultSet closed")) {
        // TODO: Print useful message for bad search query
      } else {
        e.printStackTrace(); // debug
      }
    }
    return null;
  }
  
  private <T> String queryMany(String query, Object[] documents, Class<T> coll, String collName) {
    List<String> docs = new ArrayList<>();
    List<String> jsonDocs = new ArrayList<>();
    
    boolean isJson = false;
    
    if (documents[0] instanceof String) {
      Object[] params = {documents[0]};
      isJson = get("SELECT json_valid(?)", params).equals("1");
    }
    
    try {
      conn.setAutoCommit(false);
      PreparedStatement stmt = conn.prepareStatement(query);
      
      if(isJson) {
        for (Object model : documents) {
          String json = (String) model;
          String idField = "_id";
          
          if(coll != null) try {
            idField = Utils.getIdField(coll.getClass().getDeclaredConstructor().newInstance()).get("name");
          } catch (InstantiationException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
          }
          
          Object[] jsonParams = {json, "$." + idField};
          String jsonId = get("SELECT json_extract(json(?), ?)", jsonParams);
          docs.add(json);
          jsonDocs.add(json);
    
          stmt.setString(1, jsonId);
          stmt.setString(2, json);
          stmt.executeUpdate();
        }
      } else {
        for (Object model : documents) {
          Map<String, String> field = Utils.getIdField(model);
          String json = mapper.writeValueAsString(model);
          docs.add(json);
          jsonDocs.add(json);
          
          stmt.setString(1, field.get("id"));
          stmt.setString(2, json);
          stmt.executeUpdate();
        }
      }
      
      conn.commit();
      stmt.close();
      
      // don't bother converting json if there's no watchers
      if (eventWatchers.get(collName) != null || watchers.get(collName) != null) {
        updateWatchers(collName, "insert", docs.toString(), coll);
      }
      
      return "[" + String.join(",", jsonDocs) + "]";
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
  
  private String findAsJson(String collName, String filter, Object[] params, int limit) {
    Map<String, List<String>> filters = generateWhereClause(filter);
    String q = String.format("SELECT GROUP_CONCAT(value) FROM (SELECT value FROM %1$s"
        + filters.get("query").get(0) + (limit == 0 ? ")" : " LIMIT %2$d)"), collName, limit);
  
    return get(q, params);
  }
  
  String findAsJson(String collName, String filter, String sort, int limit, int offset) {
    if (filter == null) {
      return get(String.format("SELECT GROUP_CONCAT(value) FROM (SELECT value FROM %1$s" +
          (limit == 0 ? ")" : " LIMIT %2$d OFFSET %3$d)"), collName, limit, offset));
    }
    
    String orderBy = "";
    String[] order = new String[2];
    if (sort != null) {
      if (sort.endsWith("<")) {
        order[0] = "$." + sort.substring(0, sort.length() - 1);
        order[1] = "ASC";
      } else if (sort.endsWith(">")) {
        order[0] = "$." + sort.substring(0, sort.length() - 1);
        order[1] = "DESC";
      } else {
        order = sort.split("==|=");
        order[0] = "$." + order[0];
      }
      orderBy = " ORDER BY json_extract(value, ?) " + order[1];
    }
    
    Map<String, List<String>> filters = generateWhereClause(filter);
    String q = String.format("SELECT GROUP_CONCAT(value) FROM (SELECT value FROM %1$s"
        + filters.get("query").get(0) + orderBy + (limit == 0 ? ")" : " LIMIT %2$d OFFSET %3$d)"), collName, limit, offset);
  
//    System.out.println(q); // debug
    
    List params = populateParams(filters);
    if (sort != null) params.add(order[0]);
    
    return get(q, params.toArray());
  }
  
  String deleteDocs(String collName, String filter, int limit, Class klass) {
    if (filter == null) {
      return run("delete", String.format("DELETE FROM %1$s", collName), klass, collName);
    }
    
    Map<String, List<String>> filters = generateWhereClause(filter);
    String q;
    if (limit == 0) {
      q = String.format("DELETE FROM %1$s" + filters.get("query").get(0), collName);
    } else {
      q = String.format("DELETE FROM %1$s WHERE %1$s.key = (SELECT %1$s.key FROM %1$s"
          + filters.get("query").get(0) + " LIMIT %2$d)", collName, limit);
    }
    List params = populateParams(filters);
    
    String deletedDocs;
    String deleted;
    if(filter.startsWith("key=")) {
      Object[] param = { params.get(1) };
      deletedDocs = get("SELECT value FROM " + collName + " WHERE key = ?", param);
      deleted = run("delete", "DELETE FROM " + collName + " WHERE key = ?", param, klass, collName);
    } else {
      deletedDocs = findAsJson(collName, filter, params.toArray(), limit);
      deleted = run("delete", q, params.toArray(), klass, collName);
    }
    deletedDocs = "[" + deletedDocs +"]";
  
    if (deleted.equals("deleted"))
      // don't bother converting json if there's no watchers
      if (eventWatchers.get(collName) != null || watchers.get(collName) != null) {
        updateWatchers(collName, "delete", deletedDocs, klass);
      }
    
    return deletedDocs;
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
  
  void updateWatchers(String collName, String event, String docs, Class coll) {
    if (event.equals("none")) return;
    WatchData watchData = null;
    if(!runAsync) try {
      watchData = new WatchData(collName, event,
          mapper.readValue(docs, mapper.getTypeFactory().constructCollectionType(List.class, coll)));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    
    if (eventWatchers.get(collName) != null && eventWatchers.get(collName).get(event) != null) {
      if (runAsync) watchExecutor.submit(() -> {
          WatchData watchDataAsync = null;
          try {
            watchDataAsync = new WatchData(collName, event,
                mapper.readValue(docs, mapper.getTypeFactory().constructCollectionType(List.class, coll)));
          } catch (JsonProcessingException e) {
            e.printStackTrace();
          }
  
        WatchData finalWatchDataAsync = watchDataAsync;
        eventWatchers.get(collName).get(event).forEach(w -> w.handle(finalWatchDataAsync));
        });
      else {
        WatchData finalWatchData = watchData;
        eventWatchers.get(collName).get(event).forEach(w -> w.handle(finalWatchData));
      }
    }
    if (watchers.get(collName) != null) {
      if (runAsync) watchExecutor.submit(() -> {
        WatchData watchDataAsync = null;
        try {
          watchDataAsync = new WatchData(collName, event,
              mapper.readValue(docs, mapper.getTypeFactory().constructCollectionType(List.class, coll)));
        } catch (JsonProcessingException e) {
          e.printStackTrace();
        }
  
        WatchData finalWatchDataAsync = watchDataAsync;
        watchers.get(collName).forEach(w -> w.handle(finalWatchDataAsync));
      });
      else {
        WatchData finalWatchData1 = watchData;
        watchers.get(collName).forEach(w -> w.handle(finalWatchData1));
      }
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
  
  List populateParams(Map<String, List<String>> filters) {
    List params = new ArrayList();
    
    for (int i = 0; i < filters.get("paths").size(); i++) {
      params.add(filters.get("paths").get(i));
      String[] inValues = {filters.get("values").get(i)};
      
      if (inValues[0].startsWith("[") && inValues[0].endsWith("]")) {
        inValues[0] = inValues[0].replaceAll("^\\[", "").replaceAll("]$", "");
        inValues = inValues[0].split(",");
      }
      
      for (String value : inValues) {
        if (Utils.isNumeric(value)) {
          if (value.contains(".")) { // with decimals
            try {
              params.add(Double.parseDouble(value));
            } catch (Exception tryFloat) {
              try {
                params.add(Float.parseFloat(value));
              } catch (Exception e) {
                e.printStackTrace();
              }
            }
          } else { // without decimals
            try {
              params.add(Integer.parseInt(value));
            } catch (Exception tryLong) {
              try {
                params.add(Long.parseLong(value));
              } catch (Exception e) {
                e.printStackTrace();
              }
            }
          }
        } else { // not a number
          params.add(value);
        }
      }
    }
    return params;
  }
  
  Map<String, List<String>> generateWhereClause(String filter) {
    List<String> paths = new ArrayList<>();
    List<String> values = new ArrayList<>();
    
    String regex =  "(\\s*\\!\\s*)?([\\(\\w\\s\\.\\[\\]]+)\\s*(~~|=~|==|>=|<=|!=|<|>|=)\\s*(([!-%'-{\\}£~\\såäöÅÄÖ]*\\|{0,1}\\&{0,1}[!-%'-{\\}£~\\såäöÅÄÖ])*(?<!\\|))(&&|\\|\\|)?";
    
    String query = " WHERE" + new Rewriter(regex) {
      public String replacement() {
        
        boolean isRegex = group(3).equals("~~");
        
        String path = group(2).replace(" ", "");
        String startParam =  "";
        if(group(1) != null && group(1).trim().equals("!")) {
          startParam = " NOT ";
        }
        if(path.startsWith("(")) {
          startParam += "(";
          path = path.replaceAll("^\\(", "");
        }
        
        paths.add("$." + path);
        String val = group(4).trim();
        String comparator;
        
        
        if ((group(3).equals("==") || group(3).equals("="))
            && (val.startsWith("[") && val.endsWith("]"))) {
          
          val = val.replace(" ", "");
          
          String[] inValues = val.split(",");
          comparator = " IN (";
          
          for (String in : inValues) {
            comparator += "?,";
          }
          comparator = comparator.replaceAll(",$", ")");
        } else if (group(3).equals("=~")) {
          comparator = "LIKE ?";
        } else if (isRegex) {
          comparator = "REGEXP ?";
        } else {
          comparator = group(3) + " ?";
        }
        
        if (!isRegex && val.endsWith(")")) {
          comparator += ")";
          val = val.replaceAll("\\s*\\)$", "");
        }
        
        values.add(val);
        
        String andOr = group(6) == null ? "" : (group(6).equals("&&") ? "AND" : "OR");
        return String.format(startParam + " json_extract(value, ?) %s %s", comparator, andOr);
      }
    }.rewrite(filter);
    
    Map<String, List<String>> map = new HashMap<>();
    map.put("query", Collections.singletonList(query));
    map.put("paths", paths);
    map.put("values", values);
    
    return map;
  }
  
}
