package nosqlite;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import nosqlite.annotations.Id;
import nosqlite.exceptions.IdAnnotationMissingException;
import nosqlite.exceptions.TypeMismatchException;
import nosqlite.handlers.*;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.*;
import java.util.*;

/**
 * @author Johan Wirén
 */
@SuppressWarnings("unchecked")
public class Collection {
  private Class klass;
  private String collName;
  private DbHelper db;
  private ObjectMapper mapper = new ObjectMapper();
  private String idField;
  
  Collection(DbHelper db, Class klass, String collName) {
    this.klass = klass;
    this.db = db;
    this.collName = collName;
    
    if (klass == null) {
      idField = "_id";
    } else {
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

    // create table for this document
    db.run("create", "CREATE TABLE IF NOT EXISTS " + collName +
        "(key TEXT PRIMARY KEY UNIQUE NOT NULL, " +
        "value JSON NOT NULL)", klass, collName);
  }
  
  public Connection conn() {
    return db.conn;
  }
  
  public void close() {
    db.close();
  }
  
  // TODO: indexing doesn't seem to do anything
//  public void createIndex(String field) {
//    if (field == null) throw new NullPointerException();
//    Object[] params = {"$." + field};
//    db.run("create", "CREATE INDEX IF NOT EXISTS " + collName + "_idx ON " + collName + "(json_extract(value, ?))", params, klass, collName);
//  }
  
  public <T> T get(String key, Class<T> klass) {
    String json = get(key);
    if (json == null) return null;
    return JSONparse(json, klass);
  }
  
  public String get(String key) {
    if (key == null) throw new NullPointerException();
    String query = "SELECT value FROM " + collName + " WHERE key = ?";
    Object[] params = {key};
    return db.get(query, params);
  }
  
  public String put(String key, Object value) {
    boolean exists = get(key) != null;
    boolean isJson = false;
  
    if (value instanceof String) {
      Object[] params = {value};
      isJson = db.get("SELECT json_valid(?)", params).equals("1");
    }
    
    if(!isJson) {
      value = JSONstringify(value);
    }
    
    String query = String.format("INSERT INTO %s values(?, json(?))" +
        "ON CONFLICT(key) DO UPDATE SET value=json(excluded.value)", collName);
    Object[] params = {key, value};
    return db.run(exists ? "insert" : "update", query, params, klass, collName);
  }
  
  public String putIfAbsent(String key, Object value) {
    boolean exists = get(key) != null;
    if (exists) return '\'' + key + "' already exists";
    
    return put(key, value);
  }
  
  public String remove(String key) {
    String query = String.format("DELETE FROM %1$s WHERE key = ?", collName);
    Object[] params = {key};
    return db.run("delete", query, params, klass, collName);
  }
  
  public String save(String json) {
    if (json == null) throw new NullPointerException();
    Object[] jsonParams = {json, "$." + idField};
    String jsonId = db.get("SELECT json_extract(json(?), ?)", jsonParams);
    
    if (jsonId == null) {
      Map<String, String> field = klass == null ? getIdField(new HashMap<>()) : getIdField();
      jsonId = field.get("id");
    }
    String exists = get(jsonId);
    if (json.equals(exists)) return json; // don't update document which have no changes
    
    String query = String.format("INSERT INTO %s VALUES(?, json(json_set(?, '$.%s', ?))) " +
        "ON CONFLICT(key) DO UPDATE SET value=json(excluded.value)", collName, idField);
    
    Object[] params = {jsonId, json, jsonId};
    return db.run(exists != null ? "update" : "insert", query, params, klass, collName);
  }
  
  public <T> T save(Object document) {
    if (document == null) throw new NullPointerException();
    
    if (document.getClass() != klass) try {
      throw new TypeMismatchException(String.format("'%s' cannot be saved in a '%s' collection", document.getClass().getSimpleName(), collName));
    } catch (TypeMismatchException e) {
      e.printStackTrace();
      return null;
    }
    
    Map<String, String> field = getIdField(document);
    String json = JSONstringify(document);
    String exists = get(field.get("id"));
    if (json.equals(exists)) return (T) document; // don't update document which have no changes
    
    String query = String.format("INSERT INTO %s VALUES(?, json(?)) " +
        "ON CONFLICT(key) DO UPDATE SET value=json(excluded.value)", collName);
    Object[] params = {field.get("id"), json};
    db.run(exists != null ? "update" : "insert", query, params, klass, collName);
    return (T) document;
  }
  
  public <T> List<T> save(List<T> documents) {
    return Arrays.asList(save(documents.toArray()));
  }
  
  public <T> T[] save(Object[] documents) {
    if (documents == null) throw new NullPointerException();
  
    boolean isJson = false;
    
    if (documents[0] instanceof String) {
      Object[] params = {documents[0]};
      isJson = db.get("SELECT json_valid(?)", params).equals("1");
    }
    
    if(!isJson) {
      for (Object doc : documents) {
        if (doc == null) throw new NullPointerException();
        
        if (doc.getClass() != klass) try {
          throw new TypeMismatchException(String.format("'%s' cannot be saved in a '%s' collection", documents[0].getClass().getSimpleName(), collName));
        } catch (TypeMismatchException e) {
          e.printStackTrace();
          return null;
        }
      }
    }
    
    String q = "INSERT INTO " + collName + " VALUES(?, json(?)) " +
        "ON CONFLICT(key) DO UPDATE SET value=json(excluded.value)";
    db.run("queryMany", q, documents, klass, collName);
    return (T[]) documents;
  }
  
  public <T> List<T> find() {
    return find(null, null, 0, 0);
  }
  
  public <T> List<T> find(int limit, int offset) {
    return find(null, null, limit, offset);
  }
  
  public <T> List<T> find(String filter) {
    return find(filter, null, 0, 0);
  }
  
  public <T> List<T> find(String filter, int limit) {
    return find(filter, null, limit, 0);
  }
  
  public <T> List<T> find(String filter, int limit, int offset) {
    return find(filter, null, limit, offset);
  }
  
  public <T> List<T> find(String filter, String sort, int limit, int offset) {
    String jsonArray = findAsJson(filter, sort, limit, offset);
    if (jsonArray == null) return new ArrayList<>();
    try {
      return mapper.readValue(jsonArray, mapper.getTypeFactory().constructCollectionType(List.class, klass));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    return new ArrayList<>();
  }
  
  public <T> List<T> find(FindOptionsHandler option) {
    FindOptions op = new FindOptions();
    option.handle(op);
    return (List<T>) find(op.filter, op.sort, op.limit, op.offset);
  }
  
  public <T> T findOne(String filter) {
    List docs = find(filter, 1);
    return docs != null ? (T) docs.get(0) : null;
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
    return "[" + db.findAsJson(collName, filter, sort, limit, offset) + "]";
  }
  
  public String findOneAsJson(String filter) {
    return db.findAsJson(collName, filter, null, 1, 0);
  }
  
  public <T> T findById(String id) {
    String json = findByIdAsJson(id);
    if (json == null) return null;
    try {
      return (T) mapper.readValue(json, klass);
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
    return deleteById(field.get("id"));
  }
  
  public String deleteById(String id) {
    if (id == null) throw new NullPointerException();
    return delete("key=" + id);
  }
  
  public String deleteOne(String filter) {
    return db.deleteDocs(collName, filter, 1, klass);
  }
  
  public String delete(String filter) {
    return db.deleteDocs(collName, filter, 0, klass);
  }
  
  public String delete() {
    return db.deleteDocs(collName, null, 0, klass);
  }
  
  public String delete(DeleteOptionsHandler option) {
    DeleteOptions op = new DeleteOptions();
    option.handle(op);
    return db.deleteDocs(collName, op.filter, op.limit, klass);
  }
  
  public String updateFieldById(String id, String field, Object value) {
    if (id == null) throw new NullPointerException();
    return updateField(id, field, value);
  }
  
  public String updateField(Object document, String field, Object value) {
    Map<String, String> idField = getIdField(document);
    if(get(idField.get("id")) == null) throw new NullPointerException();
    
    return updateFieldById(idField.get("id"), field, value);
  }
  
  public String updateField(String field, Object value) {
    return updateField(null, field, value);
  }
  
  public String updateField(String filter, String field, Object value) {
    if (field == null) throw new NullPointerException();
    
    boolean isJson = false;
    
    if (!(value instanceof String) && !value.getClass().isPrimitive()) {
      value = JSONstringify(value);
      Object[] params = {value};
      isJson = db.get("SELECT json_valid(?)", params).equals("1");
    }
    
    if (filter != null) {
      Object[] params = {"$." + field, filter};
      String oldValue = db.get("SELECT json_extract(value, ?) FROM " + collName + " WHERE key = ?", params);
      if (value.equals(oldValue)) return "same value"; // don't update same value
    }
    
    // by id
    if (filter != null && filter.matches("[\\w_]+")) {
      Object[] params = {"$." + field, value, filter};
      return db.run("update", "UPDATE " + collName + " SET value = json_replace(value, ?, " + (isJson ? "json(?)" : "?") + ") WHERE key = ?", params, klass, collName);
    }
    
    String query = "UPDATE " + collName + " SET value = json_replace(value, ?, " + (isJson ? "json(?))" : "?)");
    List params = new ArrayList();
    
    if (filter != null) {
      Map<String, List<String>> filters = db.generateWhereClause(filter);
      params = db.populateParams(filters);
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
    if (fieldTaken != null) {
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
  
  public int count() {
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
  
  private Map<String, String> getIdField() {
    try {
      return getIdField(klass.getDeclaredConstructor().newInstance());
    } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
      e.printStackTrace();
    }
    return null;
  }
  
  private Map<String, String> getIdField(Object model) {
    Map<String, String> idValues = new HashMap<>();
  
    if(model instanceof Map) {
      idValues.put("name", "_id");
      idValues.put("id", NanoIdUtils.randomNanoId());
      return idValues;
    }
    
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
