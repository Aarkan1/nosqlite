package database;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import entities.User;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;

public class Collection<T> {
  private Class<T> klass;
  private Connection conn;
  private List<WatchHandler> watchers = new ArrayList<>();
  private ObjectMapper mapper = new ObjectMapper();
  private String idField;

  Collection(Connection conn, Class<T> klass) {
    this.klass = klass;
    this.conn = conn;

    for(Field field : klass.getDeclaredFields()) {
      if(field.isAnnotationPresent(Id.class)) {
        idField = field.getName();
        break;
      }
    }

    if(idField == null) idField = "id";

    try {
//      conn.createStatement().execute("drop table if exists " + klass.getSimpleName());
      conn.createStatement().execute("create table if not exists " + klass.getSimpleName() +
              "(key TEXT PRIMARY KEY UNIQUE NOT NULL, " +
              "value JSON NOT NULL)");
    } catch (SQLException e) {
      e.printStackTrace();
    }

    long start = System.currentTimeMillis();
//     parse json array: 2070ms

//    find();
//    System.out.println("parse each json: " + ((System.currentTimeMillis() - start)) + "ms");
//    start = System.currentTimeMillis();
//    json_group_array
    try {
      var rs = conn.createStatement().executeQuery( // cat.race.type=Gekko gecko
              String.format("select group_concat(value) from %1$s" +
                              " where json_extract(value, '$.cat.race.time') > 50" +
                              " and json_extract(value, '$.cat.race.type') = 'Tongan'", klass.getSimpleName()));
      String table = "[" + rs.getString(1) + "]";

      List<T> list = mapper.readValue(table, mapper.getTypeFactory().constructCollectionType(List.class, klass));
      System.out.println("list: " + list.size());
      System.out.println("parse json list: " + ((System.currentTimeMillis() - start)) + "ms");

    } catch (SQLException | JsonProcessingException e) {
      e.printStackTrace();
    }
  }

  public T save(Object model) {
    Map<String, String> field = getIdField(model);
    try {
      String json = mapper.writeValueAsString(model);
      String q = String.format("insert into %s values(?, json(?)) " +
              "on conflict(key) do update set value=json(excluded.value)", klass.getSimpleName());
      PreparedStatement stmt = conn.prepareStatement(q);
      stmt.setString(1, field.get("id"));
      stmt.setString(2, json);
      stmt.executeUpdate();
      updateWatchers(new WatchData(klass.getSimpleName(), "save", Collections.singletonList(model)));
    } catch (SQLException | JsonProcessingException e) {
      e.printStackTrace();
    }
    return (T) model;
  }

  public T save(Object[] models) {
    String klassName = klass.getSimpleName();

    try {
      conn.setAutoCommit(false);
      String q = "insert into "+klassName+" values(?, json(?)) " +
              "on conflict(key) do update set value=json(excluded.value)";
      PreparedStatement stmt = conn.prepareStatement(q);

      for(Object model : models) {
        Map<String, String> field = getIdField(model);
        String json = mapper.writeValueAsString(model);

        stmt.setString(1, field.get("id"));
        stmt.setString(2, json);
        stmt.executeUpdate();
      }

      conn.commit();
      stmt.close();

      updateWatchers(new WatchData(klass.getSimpleName(), "save", Arrays.asList(models)));
    } catch (SQLException | JsonProcessingException e) {
      e.printStackTrace();
    } finally {
      try {
        conn.setAutoCommit(true);
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return (T) models;
  }

  public List<T> find() {
    try {
      var rs = conn.createStatement().executeQuery(String.format("select group_concat(value) from %1$s", klass.getSimpleName()));
      String jsonArray = "[" + rs.getString(1) + "]";
      return mapper.readValue(jsonArray, mapper.getTypeFactory().constructCollectionType(List.class, klass));
    } catch (SQLException | JsonProcessingException e) {
      e.printStackTrace();
    }
    return new ArrayList<>();
  }

  public List<T> find(int limit) {
    try {
      var rs = conn.createStatement().executeQuery(
              String.format("select group_concat(value) from (select value from %1$s LIMIT %2$d)",
                      klass.getSimpleName(), limit));
      String jsonArray = "[" + rs.getString(1) + "]";
      return mapper.readValue(jsonArray, mapper.getTypeFactory().constructCollectionType(List.class, klass));
    } catch (SQLException | JsonProcessingException e) {
      e.printStackTrace();
    }
    return new ArrayList<>();
  }

  public List<T> find(int limit, int offset) {
    try {
      var rs = conn.createStatement().executeQuery(
              String.format("select group_concat(value) from (select value from %1$s LIMIT %2$d OFFSET %3$d)",
                      klass.getSimpleName(), limit, offset));
      String jsonArray = "[" + rs.getString(1) + "]";
      return mapper.readValue(jsonArray, mapper.getTypeFactory().constructCollectionType(List.class, klass));
    } catch (SQLException | JsonProcessingException e) {
      e.printStackTrace();
    }
    return new ArrayList<>();
  }

  public List<T> find(String filter) {
    Map<String, String> filters = getFilter(filter);
    try {
      String q = String.format("select group_concat(value) from (select %1$s.value from %1$s, json_tree(%1$s.value"
              + filters.get("query") + ")", klass.getSimpleName());
      PreparedStatement stmt = conn.prepareStatement(q);
      stmt.setString(1, filters.get("key"));
      stmt.setString(2, filters.get("value"));
      ResultSet rs = stmt.executeQuery();
      String jsonArray = "[" + rs.getString(1) + "]";
      return mapper.readValue(jsonArray, mapper.getTypeFactory().constructCollectionType(List.class, klass));
    } catch (SQLException | JsonProcessingException e) {
      e.printStackTrace();
    }
    return new ArrayList<>();
  }

  public boolean delete(Object obj) {

    updateWatchers(new WatchData(klass.getSimpleName(), "delete", Collections.singletonList(obj)));
    return false;
  }

  public List<T> query(String query) {
    return this.query(query, null);
  }

  public List<T> query(String query, Map<String, Object> params) {

    return null;
  }

  public void watch(WatchHandler watcher) {
    watchers.add(watcher);
  }

  private void updateWatchers(WatchData watchData) {
    watchers.forEach(w -> w.handle(watchData));
  }

  private Map<String, String> getFilter(String filter) {
    String[] filters = null;
    String f = null;
    String key = null;

    if(filter.contains("=")) {
      filters = filter.split("=");
      String[] deep = getDeepFilter(filters[0]);
      key = deep[1];
      f = deep[0] + " where json_tree.key=? and json_tree.value=?";
    }
    else if(filter.contains(">")) {
      filters = filter.split(">");
      String[] deep = getDeepFilter(filters[0]);
      key = deep[1];
      f = deep[0] + " where json_tree.key=? and json_tree.value>?";
    }
    else if(filter.contains("<")) {
      filters = filter.split("<");
      String[] deep = getDeepFilter(filters[0]);
      key = deep[1];
      f = deep[0] + " where json_tree.key=? and json_tree.value<?";
    }

    Map<String, String> map = new HashMap<>();
    map.put("query", f);
    map.put("key", key);
    map.put("value", filters[1]);

    return map;
  }

  private String[] getDeepFilter(String filter) {
    String[] f = new String[2];
    if(filter.contains(".")) {
      f[0] = filter.split("\\.")[0];
      f[0] = ", '$." + f[0] + "')";
    } else {
      f[0] = ")";
    }
    int i = filter.indexOf(".");
    f[1] = filter.substring(i == -1 ? 0 : i + 1 );

    return f;
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
