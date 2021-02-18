package database;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import database.annotations.Id;
import database.exceptions.IdAnnotationMissingException;
import database.exceptions.TypeMismatchException;
import database.handlers.*;
import utilities.Rewriter;
import utilities.Utils;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;

public class Collection<T> {
  private Class<T> klass;
  private String collName;
  private DbHelper db;
  private ObjectMapper mapper = new ObjectMapper();
  private String idField;
  
  Collection(DbHelper db, Class<T> klass, String collName) {
    this.klass = klass;
    this.db = db;
    this.collName = collName;
    
    if (klass != null) {
      for (Field field : klass.getDeclaredFields()) {
        if (field.isAnnotationPresent(Id.class)) {
          idField = field.getName();
          break;
        }
      }
      if (idField == null) try {
        throw new IdAnnotationMissingException("No @Id in " + collName);
      } catch (IdAnnotationMissingException e) {
        e.printStackTrace();
        return;
      }
    }
    
    // ignore failure on field name mismatch
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

//      conn.createStatement().execute("drop table if exists " + klassName);
    db.run("create", "CREATE TABLE IF NOT EXISTS " + collName +
        "(key TEXT PRIMARY KEY UNIQUE NOT NULL, " +
        "value JSON NOT NULL)", klass, collName);
    
    // index id field
    if (idField != null) {
      createIndex(idField);
    }
  }
  
  public Connection conn() {
    return db.conn;
  }
  
  // TODO: doesn't seem to do anything
  public void createIndex(String field) {
    if (field == null) throw new NullPointerException();
    Object[] params = {"$." + field};
    db.run("create", "CREATE INDEX IF NOT EXISTS " + collName + "_idx ON " + collName + "(json_extract(value, ?))", params, klass, collName);
  }
  
  public <T1> T1 get(String key, Class<T1> klass) {
    String json = get(key);
    if (json == null) return null;
    return JSONparse(json, klass);
  }
  
  public String get(String key) {
    if (key == null) throw new NullPointerException();
    String query = String.format("SELECT value FROM %1$s WHERE key = ?", collName);
    Object[] params = {key};
    return db.get(query, params);
  }
  
  public String put(String key, Object value) {
    boolean exists = get(key) != null;
    String json = JSONstringify(value);
    String query = String.format("INSERT INTO %s values(?, json(?)) " +
        "ON CONFLICT(key) DO UPDATE SET value=json(excluded.value)", collName);
    Object[] params = {key, json};
    return db.run(exists ? "insert" : "update", query, params, klass, collName);
  }
  
  public String putIfAbsent(String key, Object value) {
    boolean exists = get(key) != null;
    if(exists) return '\'' + key + "' already exists";
    
    String json = JSONstringify(value);
    String query = String.format("INSERT INTO %s values(?, json(?))", collName);
    Object[] params = {key, json};
    return db.run("insert", query, params, klass, collName);
  }
  
  public String remove(String key) {
    String query = String.format("DELETE FROM %1$s WHERE key = ?", collName);
    Object[] params = {key};
    return db.run("delete", query, params, klass, collName);
  }
  
  public String save(String json) {
    if (json == null) throw new NullPointerException();
    Map<String, String> field = getIdField();
    String query = String.format("INSERT INTO %s VALUES(?, json(json_set(?, '$.%s', ?))) " +
        "ON CONFLICT(key) DO UPDATE SET value=json(excluded.value)", collName, field.get("name"));
    Object[] params = {field.get("id"), json, field.get("id")};
    return db.run("insert", query, params, klass, collName);
  }
  
  public T save(Object document) {
    if (document == null) throw new NullPointerException();
  
    if (document.getClass() != klass) try {
      throw new TypeMismatchException(String.format("'%s' cannot be saved in a '%s' collection", document.getClass().getSimpleName(), collName));
    } catch (TypeMismatchException e) {
      e.printStackTrace();
      return null;
    }
    
    Map<String, String> field = getIdField(document);
    String json = JSONstringify(document);
    String query = String.format("INSERT INTO %s VALUES(?, json(?)) " +
        "ON CONFLICT(key) DO UPDATE SET value=json(excluded.value)", collName);
    Object[] params = {field.get("id"), json};
    db.run("insert", query, params, klass, collName);
    return (T) document;
  }
  
  public List<T> save(List<T> documents) {
    return Arrays.asList(save(documents.toArray()));
  }
  
  public T[] save(Object[] documents) {
    if (documents == null) throw new NullPointerException();
    for (Object doc : documents) {
      if (doc == null) throw new NullPointerException();
      
      if (doc.getClass() != klass) try {
        throw new TypeMismatchException(String.format("'%s' cannot be saved in a '%s' collection", documents[0].getClass().getSimpleName(), collName));
      } catch (TypeMismatchException e) {
        e.printStackTrace();
        return null;
      }
    }
    
    String q = "INSERT INTO " + collName + " VALUES(?, json(?)) " +
        "ON CONFLICT(key) DO UPDATE SET value=json(excluded.value)";
    db.run("queryMany", q, documents, klass, collName);
    return (T[]) documents;
  }
  
  public List<T> find() {
    return find(null, null,0, 0);
  }
  
  public List<T> find(int limit, int offset) {
    return find(null, null, limit, offset);
  }
  
  public List<T> find(String filter) {
    return find(filter, null, 0, 0);
  }
  
  public List<T> find(String filter, int limit) {
    return find(filter, null, limit, 0);
  }
  
  public List<T> find(String filter, int limit, int offset) {
    return find(filter, null, limit, offset);
  }
  
  public List<T> find(String filter, String sort, int limit, int offset) {
    String jsonArray = findAsJson(filter, sort, limit, offset);
    if (jsonArray == null) return new ArrayList<>();
    try {
      return mapper.readValue(jsonArray, mapper.getTypeFactory().constructCollectionType(List.class, klass));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    return new ArrayList<>();
  }
  
  public List<T> find(FindOptionsHandler option) {
    FindOptions op = new FindOptions();
    option.handle(op);
    return find(op.filter, op.sort, op.limit, op.offset);
  }
  
  public String findAsJson(FindOptionsHandler option) {
    FindOptions op = new FindOptions();
    option.handle(op);
    return findAsJson(op.filter, op.sort, op.limit, op.offset);
  }
  
  public String findAsJson() {
    return findAsJson(null, null, 0, 0);
  }
  
  public String findAsJson(int limit, int offset) {
    return findAsJson(null, null, limit, offset);
  }
  
  public String findAsJson(String filter) {
    return findAsJson(filter, null, 0, 0);
  }
  
  public String findAsJson(String filter, int limit, int offset) {
    return findAsJson(filter, null, limit, offset);
  }
  
  public String findAsJson(String filter, String sort, int limit, int offset) {
    return "[" + findOneAsJson(filter, sort, limit, offset) + "]";
  }
  
  public String findOneAsJson(String filter) {
    return findOneAsJson(filter, null,1, 0);
  }
  
  private String findOneAsJson(String filter, String sort, int limit, int offset) {
    if (filter == null) {
      return db.get(String.format("SELECT GROUP_CONCAT(value) FROM (SELECT value FROM %1$s" +
          (limit == 0 ? ")" : " LIMIT %2$d OFFSET %3$d)"), collName, limit, offset));
    }
  
    // TODO: sort is slow on large datasets
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
        order = sort.split("=|==");
        order[0] = "$." + order[0];
      }
      orderBy = " ORDER BY json_extract(value, ?) " + order[1];
    }
    
    Map<String, List<String>> filters = generateWhereClause(filter);
    String q = String.format("SELECT GROUP_CONCAT(value) FROM (SELECT value FROM %1$s"
        + filters.get("query").get(0) + orderBy + (limit == 0 ? ")" : " LIMIT %2$d OFFSET %3$d)"), collName, limit, offset);
    
    List params = populateParams(filters);
    if (sort != null) params.add(order[0]);
    
    return db.get(q, params.toArray());
  }
  
  public T findById(String id) {
    String json = findByIdAsJson(id);
    if (json == null) return null;
    try {
      return mapper.readValue(json, klass);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    return null;
  }
  
  public String findByIdAsJson(String id) {
    return get(id);
  }
  
  public String delete(Object document) {
    if (document == null) throw new NullPointerException();
    
    Map<String, String> field = getIdField(document);
    String q = String.format("DELETE FROM %1$s WHERE key=?", collName);
    Object[] params = {field.get("id")};
    return db.run("delete", q, params, klass, collName);
  }
  
  public String deleteById(String id) {
    if (id == null) throw new NullPointerException();
    String q = String.format("DELETE FROM %1$s WHERE key=?", collName);
    Object[] params = {id};
    return db.run("delete", q, params, klass, collName);
  }
  
  public String deleteOne(String filter) {
    return deleteDocs(filter, 1);
  }
  
  public String delete(String filter) {
    return deleteDocs(filter, 0);
  }
  
  public String delete() {
    return deleteDocs(null, 0);
  }
  
  public String delete(DeleteOptionsHandler option) {
    DeleteOptions op = new DeleteOptions();
    option.handle(op);
    return deleteDocs(op.filter, op.limit);
  }
  
  private String deleteDocs(String filter, int limit) {
    if (filter == null) {
      return db.run("delete", String.format("DELETE FROM %1$s", collName), klass, collName);
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
    
    return db.run("delete", q, params.toArray(), klass, collName);
  }
  
  public String updateFieldById(String id, String field, Object value) {
    if (id == null) throw new NullPointerException();
    return updateField(id, field, value);
  }
  
  public String updateField(String field, Object value) {
    return updateField(null, field, value);
  }
  
  public String updateField(String filter, String field, Object value) {
    if (field == null) throw new NullPointerException();
  
    if (filter != null && filter.matches("[\\w_]+")) {
      Object[] params = {"$." + field, value, filter};
      return db.run("update", "UPDATE " + collName + " SET value = json_replace(value, ?, ?) WHERE key = ?", params, klass, collName);
    }
    
    String query = "UPDATE " + collName + " SET value = json_replace(value, ?, ?)";
    List params = new ArrayList();
    
    if (filter != null) {
      Map<String, List<String>> filters = generateWhereClause(filter);
      params = populateParams(filters);
      query += filters.get("query").get(0);
    }
    
    params.add(0, value);
    params.add(0, "$." + field);
    
    return db.run("update", query, params.toArray(), klass, collName);
  }
  
  public String removeField(String field) {
    Object[] params = {"$." + field};
    return db.run("update", "UPDATE " + collName + " SET value = json_remove(value, ?)", params, klass, collName);
  }
  
  public String changeFieldName(String newField, String oldField) {
    Object[] params1 = {"$." + newField};
    String fieldTaken = db.get("SELECT json_extract(value, ?) FROM " + collName + " LIMIT 1", params1);
    
    // field name exists
    if(fieldTaken != null) {
      // throw error?
      System.err.printf("Field '%s' is already in use in document '%s'\n", newField, collName);
      return null;
    }
    
    // must do 2 queries to change field name.
    // first set the new field
    // and then remove the old field
    Object[] params2 = {"$." + newField, "$." + oldField};
    db.run("update", String.format("UPDATE %1$s SET value = json_insert(value, ?, json_extract(value, ?))", collName), params2, klass, collName);
    Object[] params3 = {"$." + oldField};
    return db.run("update", String.format("UPDATE %1$s SET value = json_remove(value, ?)", collName), params3, klass, collName);
  }
  
  public int size() {
    try {
      PreparedStatement stmt = db.conn.prepareStatement("SELECT count(*) FROM " + collName);
      ResultSet rs = stmt.executeQuery();
      return rs.getInt(1);
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return 0;
  }
  
  public void watch(WatchHandler watcher) {
    db.watch(collName, watcher);
  }
  
  public void watch(String event, WatchHandler watcher) {
    db.watch(collName, event, watcher);
  }
  
  private String JSONstringify(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
      return null;
    }
  }
  
  private <T2> T2 JSONparse(String json, Class<T2> klass) {
    try {
      return mapper.readValue(json, klass);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
      return null;
    }
  }
  
  private List populateParams(Map<String, List<String>> filters) {
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
  
  private Map<String, List<String>> generateWhereClause(String filter) {
    filter = filter.replace(" ", "");
    List<String> paths = new ArrayList<>();
    List<String> values = new ArrayList<>();
    boolean useRegex = true;
    String regex = useRegex ? "([\\w\\.\\[\\]]+)(~~|=~|==|>=|<=|!=|<|>|=)([%\\-,\\^_\\w\\.\\[\\]\\(\\)\\?\\>\\<\\:\\=\\{\\}\\+\\*\\$]+)(&&|\\|\\|)?"
        : "([\\w\\.\\[\\]]+)(=~|==|>=|<=|!=|<|>|=)([%\\-,\\_\\w\\.\\[\\]!?]+)(&&|\\|\\|)?";
    String query = " WHERE" + new Rewriter(regex) {
      public String replacement() {
        paths.add("$." + group(1));
        String val = group(3);
        String comparator;
        if ((group(2).equals("==") || group(2).equals("="))
            && (val.startsWith("[") && val.endsWith("]"))) {
          
          String[] inValues = val.split(",");
          comparator = " IN (";
          
          for (String in : inValues) {
            comparator += "?,";
          }
          values.add(val);
          comparator = comparator.replaceAll(",$", ")");
        } else {
          comparator = group(2) + " ?";
        }
        if (group(2).equals("=~")) {
          comparator = "LIKE ?";
          if (val.contains("%") || val.contains("_")) {
            values.add(val);
          } else {
            values.add("%" + val + "%");
          }
        } else if (useRegex && group(2).equals("~~")) {
          comparator = "REGEXP ?";
          values.add(val);
        } else {
          if (useRegex && val.endsWith(")")) {
            comparator += ")";
            val = val.replaceAll("\\)$", "");
          }
          values.add(val);
        }
        String andOr = group(4) == null ? "" : (group(4).equals("&&") ? "AND" : "OR");
        return String.format(" json_extract(value, ?) %s %s", comparator, andOr);
      }
    }.rewrite(filter);
    
    Map<String, List<String>> map = new HashMap<>();
    map.put("query", Collections.singletonList(query));
    map.put("paths", paths);
    map.put("values", values);
    
    return map;
  }
  
  private Map<String, String> getIdField() {
    try {
      return getIdField(klass.newInstance());
    } catch (InstantiationException | IllegalAccessException e) {
      e.printStackTrace();
    }
    return null;
  }
  
  private Map<String, String> getIdField(Object model) {
    Map<String, String> idValues = new HashMap<>();
    
    if (idField != null) {
      try {
        Field field = model.getClass().getDeclaredField(idField);
        field.setAccessible(true);
        if (field.get(model) == null) {
          // generate random id
          field.set(model, NanoIdUtils.randomNanoId());
        }
        idValues.put("name", field.getName());
        idValues.put("id", (String) field.get(model));
      } catch (NoSuchFieldException | IllegalAccessException e) {
        e.printStackTrace();
      }
    } else { // @Id has a custom field name
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
    }
    
    return idValues;
  }
}
