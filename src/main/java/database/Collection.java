package database;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import database.annotations.Id;
import database.exceptions.IdAnnotationMissingException;
import database.exceptions.TypeMismatchException;
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
    db.run("queryOne", "create table if not exists " + this.collName +
            "(key TEXT PRIMARY KEY UNIQUE NOT NULL, " +
            "value JSON NOT NULL)", collName);
  }

  public <T1> T1 get(String key, Class<T1> klass) {
    String json = get(key);
    if (json == null) return null;
    return JSONparse(json, klass);
  }

  public String get(String key) {
    String query = String.format("select value from %1$s where key = ?", collName);
    Object[] params = {key};
    return db.get(query, params);
  }

  public boolean put(String key, Object value) {
    String json = JSONstringify(value);
    String query = String.format("insert into %s values(?, json(?)) " +
            "on conflict(key) do update set value=json(excluded.value)", collName);
    Object[] params = {key, json};
    return db.run("queryOne", query, params, collName) != null;
  }

  public boolean putIfAbsent(String key, Object value) {
    String json = JSONstringify(value);
    String query = String.format("insert into %1$s select ?, json(?)" +
            " where not exists(select * from %1$s where %1$s.key = ?)", collName);
    Object[] params = {key, json, key};
    return db.run("queryOne", query, params, collName) != null;
  }

  public String remove(String key) {
    String json = get(key);
    String query = String.format("delete from %1$s where key = ?", collName);
    Object[] params = {key};
    db.run("queryOne", query, params, collName);
    return json;
  }

  public String save(String json) {
    Map<String, String> field = getIdField();
    String query = String.format("insert into %s values(?, json(json_set(?, '$.%s', ?))) " +
            "on conflict(key) do update set value=json(excluded.value)", collName, field.get("name"));
    Object[] params = {field.get("id"), json, field.get("id")};
    return db.run("queryOne", query, params, collName);
  }

  public T save(Object document) {
    if (document == null) throw new NullPointerException();

    try {
      if (document.getClass() != klass) {
        throw new TypeMismatchException(String.format("'%s' cannot be saved in a '%s' collection", document.getClass().getSimpleName(), collName));
      }
    } catch (TypeMismatchException e) {
      e.printStackTrace();
      return null;
    }

    Map<String, String> field = getIdField(document);
    String json = JSONstringify(document);
    String query = String.format("insert into %s values(?, json(?)) " +
            "on conflict(key) do update set value=json(excluded.value)", collName);
    Object[] params = {field.get("id"), json};
    db.run("queryOne", query, params, collName);
    return (T) document;
  }

  public List<T> save(List<T> documents) {
    if (documents == null) throw new NullPointerException();
    return Arrays.asList(save(documents.toArray()));
  }

  public T[] save(Object[] documents) {
    if (documents == null) throw new NullPointerException();
    for (Object doc : documents) if (doc == null) throw new NullPointerException();

    if (documents[0].getClass() != klass) try {
      throw new TypeMismatchException(String.format("'%s' cannot be saved in a '%s' collection", documents[0].getClass().getSimpleName(), collName));
    } catch (TypeMismatchException e) {
      e.printStackTrace();
      return null;
    }
    String q = "insert into " + collName + " values(?, json(?)) " +
            "on conflict(key) do update set value=json(excluded.value)";
    db.run("queryMany", q, documents, collName);
//
//    try {
//      db.conn.setAutoCommit(false);
//      String q = "insert into " + collName + " values(?, json(?)) " +
//              "on conflict(key) do update set value=json(excluded.value)";
//      PreparedStatement stmt = db.conn.prepareStatement(q);
//
//      for (Object model : documents) {
//        Map<String, String> field = getIdField(model);
//        String json = mapper.writeValueAsString(model);
//
//        stmt.setString(1, field.get("id"));
//        stmt.setString(2, json);
//        stmt.executeUpdate();
//      }
//
//      db.conn.commit();
//      stmt.close();
//
//      db.updateWatchers("save", new WatchData(collName, "save", Arrays.asList(documents)));
//    } catch (SQLException | JsonProcessingException e) {
//      e.printStackTrace();
//    } finally {
//      try {
//        db.conn.setAutoCommit(true);
//      } catch (SQLException e) {
//        e.printStackTrace();
//      }
//    }
    return (T[]) documents;
  }

  public List<T> find() {
    return find(0, 0);
  }

  public List<T> find(int limit) {
    return find(limit, 0);
  }

  public List<T> find(int limit, int offset) {
    String jsonArray = findAsJson(null, limit, offset);
    if (jsonArray == null) return new ArrayList<>();
    try {
      return mapper.readValue(jsonArray, mapper.getTypeFactory().constructCollectionType(List.class, klass));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    return new ArrayList<>();
  }

  public List<T> find(String filter) {
    return find(filter, 0, 0);
  }

  public List<T> find(String filter, int limit) {
    return find(filter, limit, 0);
  }

  public List<T> find(String filter, int limit, int offset) {
    String jsonArray = findAsJson(filter, limit, offset);
    if (jsonArray == null) return new ArrayList<>();
    try {
      return mapper.readValue(jsonArray, mapper.getTypeFactory().constructCollectionType(List.class, klass));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    return new ArrayList<>();
  }

  public String findAsJson() {
    return findAsJson(null, 0, 0);
  }

  public String findAsJson(int limit) {
    return findAsJson(null, limit, 0);
  }

  public String findAsJson(int limit, int offset) {
    return findAsJson(null, limit, offset);
  }

  public String findAsJson(String filter) {
    return findAsJson(filter, 0, 0);
  }

  public String findAsJson(String filter, int limit) {
    return findAsJson(filter, limit, 0);
  }

  public String findAsJson(String filter, int limit, int offset) {
    return "[" + findOneAsJson(filter, limit, offset) + "]";
  }

  public String findOneAsJson(String filter) {
    return findOneAsJson(filter, 1, 0);
  }

  public String findOneAsJson(String filter, int limit, int offset) {
    if (filter == null) {
      return db.get(String.format("select group_concat(value) from (select value from %1$s" +
              (limit == 0 ? ")" : " limit %2$d offset %3$d)"), collName, limit, offset));
    }

    Map<String, List<String>> filters = generateWhereClause(filter);
    String q = String.format("select group_concat(value) from (select value from %1$s"
            + filters.get("query").get(0) + (limit == 0 ? ")" : " limit %2$d offset %3$d)"), collName, limit, offset);

    List params = new ArrayList();

    for (int i = 0; i < filters.get("paths").size(); i++) {
      params.add(filters.get("paths").get(i));
      String value = filters.get("values").get(i);

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
    if (id == null) throw new NullPointerException();
    String query = String.format("select value from %1$s where key=? limit 1", collName);
    Object[] params = {id};
    return db.get(query, params);
  }

  public boolean delete(Object document) {
    if (document == null) throw new NullPointerException();

    Map<String, String> field = getIdField(document);
    String q = String.format("delete from %1$s where key = ?", collName);
    Object[] params = {field.get("id")};
    return db.run("queryOne", q, params, collName) != null;
  }

  public boolean deleteById(String id) {
    if (id == null) throw new NullPointerException();
    String q = String.format("delete from %1$s where key = ?", collName);
    Object[] params = {id};
    return db.run("queryOne", q, params, collName) != null;
  }

  public boolean deleteOne(String filter) {
    return deleteDocs(filter, 1);
  }

  public boolean delete(String filter) {
    return deleteDocs(filter, 0);
  }

  public boolean delete() {
    return deleteDocs(null, 0);
  }

  private boolean deleteDocs(String filter, int limit) {
    if (filter == null) {
      return db.run("queryOne", String.format("delete from %1$s", collName), collName) != null;
    }

    Map<String, List<String>> filters = generateWhereClause(filter);
    String q;
    if (limit == 0) {
      q = String.format("delete from %1$s" + filters.get("query").get(0), collName);
    } else {
      q = String.format("delete from %1$s where %1$s.key = (select %1$s.key from %1$s"
              + filters.get("query").get(0) + " limit %2$d)", collName, limit);
    }
    List params = new ArrayList();

    for (int i = 0; i < filters.get("paths").size(); i++) {
      params.add(filters.get("paths").get(i));
      String value = filters.get("values").get(i);

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

    return db.run("queryOne", q, params.toArray(), collName) != null;
  }

  // find sort
  // find filter sort
  // change field name
  // update single field - update User set value = json_set(value, '$.cat.color', 'blue')
  // updateById single field - update User set value = json_set(value, '$.cat.color', 'blue') where key = id
  // remove single field - update User set value = json_remove(value, '$.cat.color')

  public void watch(WatchHandler watcher) {
    db.watch(watcher);
  }

  public void watch(String event, WatchHandler watcher) {
    db.watch(event, watcher);
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

  private Map<String, List<String>> generateWhereClause(String filter) {
    filter = filter.replace(" ", "");
    List<String> paths = new ArrayList<>();
    List<String> values = new ArrayList<>();
    String query = " where" + new Rewriter("([\\w\\.\\[\\]]+)(=~|>=|<=|!=|<|>|=)([\\w\\.\\[\\]]+)(\\)\\|\\||\\)&&|&&|\\|\\|)?") {
      public String replacement() {
        paths.add("$." + group(1));
        String comparator = group(2) + " ?";
        if (group(2).equals("=~")) {
          comparator = "like ?";
          String val = group(3);
          if (val.contains("%") || val.contains("_")) {
            values.add(val);
          } else {
            values.add("%" + val + "%");
          }
        } else {
          values.add(group(3));
        }
        String andOr = group(4) == null ? ""
                : (group(4).equals("&&") ? "and"
                : group(4).equals("||") ? "or"
                : group(4).equals(")&&") ? ")and" : ")or");
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
