package database;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import database.annotations.Id;
import database.exceptions.TypeMismatchException;
import utilities.Rewriter;
import utilities.Utils;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;

public class Collection<T> {
  private Class<T> klass;
  private String collName;
  private Connection conn;
  private List<WatchHandler> watchers = new ArrayList<>();
  private Map<String, List<WatchHandler>> eventWatchers = new HashMap<>();
  private ObjectMapper mapper = new ObjectMapper();
  private String idField;

  Collection(Connection conn, Class<T> klass, String collName) {
    this.klass = klass;
    this.conn = conn;
    this.collName = collName;

    if(klass != null) {
      for(Field field : klass.getDeclaredFields()) {
        if(field.isAnnotationPresent(Id.class)) {
          idField = field.getName();
          break;
        }
      }
      if(idField == null) idField = "id";
    }


    // ignore failure on field name mismatch
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    try {
//      conn.createStatement().execute("drop table if exists " + klassName);
      conn.createStatement().execute("create table if not exists " + this.collName +
              "(key TEXT PRIMARY KEY UNIQUE NOT NULL, " +
              "value JSON NOT NULL)");
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  public <T1> T1 get(String key, Class<T1> klass) {
    String json = get(key);
    if(json == null) return null;
    try {
      return mapper.readValue(json, klass);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    return null;
  }

  public String get(String key) {
    String q = String.format("select value from %1$s where key = ?", collName);
    try {
      PreparedStatement stmt = conn.prepareStatement(q);
      stmt.setString(1, key);
      ResultSet rs = stmt.executeQuery();
      return rs.getString(1);
    } catch (SQLException e) {
      if(e.getMessage().startsWith("ResultSet closed")) {
        return null;
      }
      e.printStackTrace();
    }
    return null;
  }

  public boolean put(String key, Object value) {
    try {
      String json = mapper.writeValueAsString(value);
      String q = String.format("insert into %s values(?, json(?)) " +
            "on conflict(key) do update set value=json(excluded.value)", collName);
      PreparedStatement stmt = conn.prepareStatement(q);
      stmt.setString(1, key);
      stmt.setString(2, json);
      stmt.executeUpdate();
      updateWatchers("save", new WatchData(collName, "save", Collections.singletonList(json)));
      return true;
    } catch (SQLException | JsonProcessingException e) {
      e.printStackTrace();
    }
    return false;
  }

  public boolean putIfAbsent(String key, Object value) {
    try {
      String json = mapper.writeValueAsString(value);
      String q = String.format("insert into %1$s select ?, json(?)" +
              " where not exists(select * from %1$s where %1$s.key = ?)", collName);
      PreparedStatement stmt = conn.prepareStatement(q);
      stmt.setString(1, key);
      stmt.setString(2, json);
      stmt.setString(3, key);
      stmt.executeUpdate();
      updateWatchers("save", new WatchData(collName, "save", Collections.singletonList(json)));
      return true;
    } catch (SQLException | JsonProcessingException e) {
      e.printStackTrace();
    }
    return false;
  }

  public String remove(String key) {
    try {
      String json = get(key);
      String q = String.format("delete from %1$s where key = ?", collName);
      PreparedStatement stmt = conn.prepareStatement(q);
      stmt.setString(1, key);
      stmt.executeUpdate();
      updateWatchers("delete", new WatchData(collName, "delete", Collections.singletonList(json)));
      return json;
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return null;
  }

  public String save(String json) {
    Map<String, String> field = getIdField();
    try {
      String q = String.format("insert into %s values(?, json(json_set(?, '$.%s', ?))) " +
              "on conflict(key) do update set value=json(excluded.value)", collName, field.get("name"));
      PreparedStatement stmt = conn.prepareStatement(q);
      stmt.setString(1, field.get("id"));
      stmt.setString(2, json);
      stmt.setString(3, field.get("id"));
      stmt.executeUpdate();
      updateWatchers("save", new WatchData(collName, "save", Collections.singletonList(json)));
      return json;
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return null;
  }

  public T save(Object document) {
    try {
      if(document.getClass() != klass) {
        throw new TypeMismatchException(String.format("'%s' cannot be saved in a '%s' collection", document.getClass().getSimpleName(), collName));
      }
    } catch (TypeMismatchException e) {
      e.printStackTrace();
      return null;
    }

    Map<String, String> field = getIdField(document);
    try {
      String json = mapper.writeValueAsString(document);
      String q = String.format("insert into %s values(?, json(?)) " +
              "on conflict(key) do update set value=json(excluded.value)", collName);
      PreparedStatement stmt = conn.prepareStatement(q);
      stmt.setString(1, field.get("id"));
      stmt.setString(2, json);
      stmt.executeUpdate();
      updateWatchers("save", new WatchData(collName, "save", Collections.singletonList(document)));
    } catch (SQLException | JsonProcessingException e) {
      e.printStackTrace();
    }
    return (T) document;
  }

  public synchronized T save(Object[] documents) {
    try {
      if(documents[0].getClass() != klass) {
        throw new TypeMismatchException(String.format("'%s' cannot be saved in a '%s' collection", documents[0].getClass().getSimpleName(), collName));
      }
    } catch (TypeMismatchException e) {
      e.printStackTrace();
      return null;
    }

    try {
      conn.setAutoCommit(false);
      String q = "insert into "+ collName +" values(?, json(?)) " +
              "on conflict(key) do update set value=json(excluded.value)";
      PreparedStatement stmt = conn.prepareStatement(q);

      for(Object model : documents) {
        Map<String, String> field = getIdField(model);
        String json = mapper.writeValueAsString(model);

        stmt.setString(1, field.get("id"));
        stmt.setString(2, json);
        stmt.executeUpdate();
      }

      conn.commit();
      stmt.close();

      updateWatchers("save", new WatchData(collName, "save", Arrays.asList(documents)));
    } catch (SQLException | JsonProcessingException e) {
      e.printStackTrace();
    } finally {
      try {
        conn.setAutoCommit(true);
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return (T) documents;
  }

  public List<T> find() {
    return find(0, 0);
  }

  public List<T> find(int limit) {
    return find(limit, 0);
  }

  public List<T> find(int limit, int offset) {
    String jsonArray = findAsJson(null, limit, offset);
    if(jsonArray == null) return new ArrayList<>();
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
    if(jsonArray == null) return new ArrayList<>();
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
    try {
      if(filter == null) {
        ResultSet rs = conn.createStatement().executeQuery(
                String.format("select group_concat(value) from (select value from %1$s" + (limit == 0 ? ")" : " limit %2$d offset %3$d)"),
                        collName, limit, offset));
        return rs.getString(1);
      }

      Map<String, List<String>> filters = generateWhereClause(filter);
      String q = String.format("select group_concat(value) from (select value from %1$s"
              + filters.get("query").get(0) + (limit == 0 ? ")" : " limit %2$d offset %3$d)"), collName, limit, offset);
      PreparedStatement stmt = conn.prepareStatement(q);

      for(int i = 0; i < filters.get("paths").size() * 2; i+=2) {
        stmt.setString(i+1, "$." + filters.get("paths").get(i / 2));
        String value = filters.get("values").get(i / 2);

        if(Utils.isNumeric(value)) {
          if(value.contains(".")) { // with decimals
            try {
              stmt.setDouble(i+2, Double.parseDouble(value));
            } catch (Exception tryFloat) {
              try {
                stmt.setFloat(i+2, Float.parseFloat(value));
              } catch (Exception e) {
                e.printStackTrace();
              }
            }
          } else { // without decimals
            try {
              stmt.setInt(i+2, Integer.parseInt(value));
            } catch (Exception tryLong) {
              try {
                stmt.setLong(i+2, Long.parseLong(value));
              } catch (Exception e) {
                e.printStackTrace();
              }
            }
          }
        } else { // not a number
          stmt.setString(i+2, value);
        }
      }
      ResultSet rs = stmt.executeQuery();
      return rs.getString(1);
    } catch (SQLException e) {
      if(e.getMessage().startsWith("ResultSet closed")) {
        return null;
      }
      e.printStackTrace();
    }
    return null;
  }

  public T findById(String id) {
    String json = findByIdAsJson(id);
    if(json == null) return null;
    try {
      return mapper.readValue(json, klass);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    return null;
  }

  public String findByIdAsJson(String id) {
    try {
      String q = String.format("select value from %1$s where key=? limit 1", collName);
      PreparedStatement stmt = conn.prepareStatement(q);
      stmt.setString(1, id);
      ResultSet rs = stmt.executeQuery();
      return rs.getString(1);
    } catch (SQLException e) {
      if(e.getMessage().startsWith("ResultSet closed")) {
        return null;
      }
      e.printStackTrace();
    }
    return null;
  }

  public boolean delete(Object document) {
    if(document == null) return false;

    Map<String, String> field = getIdField(document);
    try {
      String q = String.format("delete from %1$s where key = ?", collName);
      PreparedStatement stmt = conn.prepareStatement(q);
      stmt.setString(1, field.get("id"));
      stmt.executeUpdate();
      updateWatchers("delete", new WatchData(collName, "delete", Collections.singletonList(document)));
      return true;
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return false;
  }

  public boolean deleteById(String id) {
    try {
      String deletedDoc = findByIdAsJson(id);
      String q = String.format("delete from %1$s where key = ?", collName);
      PreparedStatement stmt = conn.prepareStatement(q);
      stmt.setString(1, id);
      stmt.executeUpdate();
      updateWatchers("delete", new WatchData(collName, "delete", Collections.singletonList(deletedDoc)));
      return true;
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return false;
  }

  public boolean deleteOne(String filter) {
    String deletedDoc = findOneAsJson(filter);
    boolean deleted = deleteDocs(filter, 1);
    if(deleted) updateWatchers("delete", new WatchData(collName, "delete", Collections.singletonList(deletedDoc)));
    return deleted;
  }

  public boolean delete(String filter) {
    String deletedDocs = findAsJson(filter);
    boolean deleted = deleteDocs(filter, 0);
    if(deleted) updateWatchers("delete", new WatchData(collName, "delete", Collections.singletonList(deletedDocs)));
    return deleted;
  }

  public boolean delete() {
    String deletedDocs = findAsJson();
    boolean deleted = deleteDocs(null, 0);
    if(deleted) updateWatchers("delete", new WatchData(collName, "delete", Collections.singletonList(deletedDocs)));
    return deleted;
  }

  private boolean deleteDocs(String filter, int limit) {
    try {
      if(filter == null) {
        ResultSet rs = conn.createStatement().executeQuery(
                String.format("delete from %1$s", collName));
        return true;
      }

      Map<String, List<String>> filters = generateWhereClause(filter);
      String q;
      if(limit == 0) {
        q = String.format("delete from %1$s" + filters.get("query").get(0), collName);
      } else {
        q = String.format("delete from %1$s where %1$s.key = (select %1$s.key from %1$s"
              + filters.get("query").get(0) + " limit %2$d)", collName, limit);
      }
      PreparedStatement stmt = conn.prepareStatement(q);

      for(int i = 0; i < filters.get("paths").size() * 2; i+=2) {
        stmt.setString(i+1, "$." + filters.get("paths").get(i / 2));
        String value = filters.get("values").get(i / 2);

        if(Utils.isNumeric(value)) {
          if(value.contains(".")) { // with decimals
            try {
              stmt.setDouble(i+2, Double.parseDouble(value));
            } catch (Exception tryFloat) {
              try {
                stmt.setFloat(i+2, Float.parseFloat(value));
              } catch (Exception e) {
                e.printStackTrace();
              }
            }
          } else { // without decimals
            try {
              stmt.setInt(i+2, Integer.parseInt(value));
            } catch (Exception tryLong) {
              try {
                stmt.setLong(i+2, Long.parseLong(value));
              } catch (Exception e) {
                e.printStackTrace();
              }
            }
          }
        } else { // not a number
          stmt.setString(i+2, value);
        }
      }
      stmt.executeUpdate();
      return true;
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return false;
  }

  // find sort
  // find filter sort
  // deleteById
  // delete by filter
  // change field name
  // update single field - update User set value = json_set(value, '$.cat.color', 'blue')
  // updateById single field - update User set value = json_set(value, '$.cat.color', 'blue') where key = id
  // remove single field - update User set value = json_remove(value, '$.cat.color')

  public void watch(WatchHandler watcher) {
    watchers.add(watcher);
  }

  public void watch(String event, WatchHandler watcher) {
    eventWatchers.putIfAbsent(event, new ArrayList<>());
    eventWatchers.get(event).add(watcher);
  }

  private void updateWatchers(String event, WatchData watchData) {
    if(eventWatchers.get(event) != null) {
      eventWatchers.get(event).forEach(w -> w.handle(watchData));
    }
    watchers.forEach(w -> w.handle(watchData));
  }

  private Map<String, List<String>> generateWhereClause(String filter) {
    filter = filter.replace(" ", "");
    List<String> paths = new ArrayList<>();
    List<String> values = new ArrayList<>();
    String query = " where" + new Rewriter("([\\w\\.\\[\\]]+)(=~|>=|<=|!=|<|>|=)([\\w\\.\\[\\]]+)(\\)\\|\\||\\)&&|&&|\\|\\|)?") {
      public String replacement() {
        paths.add(group(1));
        String comparator = group(2) + " ?";
        if (group(2).equals("=~")) {
          comparator = "like ?";
          String val = group(3);
          if(val.contains("%") || val.contains("_")) {
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

    if(idField != null) {
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
        for(Field field : model.getClass().getDeclaredFields()) {
          if(field.isAnnotationPresent(Id.class)) {
            field.setAccessible(true);
            if(field.get(model) == null) {
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
