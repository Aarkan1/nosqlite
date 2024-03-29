package nosqlite.utilities;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import nosqlite.annotations.Id;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Johan Wirén
 */
public abstract class Utils {
  private static final ObjectMapper mapper = new ObjectMapper();
  
  public static <T> T resultSetToObject(ResultSet rs, Class<T> klass) throws SQLException, JsonProcessingException {
    String json = resultSetToJson(rs);
    if(json == null) return null;
    
    return mapper.readValue(json, klass);
  }
  
  public static String resultSetToJson(ResultSet rs) throws SQLException, JsonProcessingException {
    List<Map<String, Object>> rows = new ArrayList<>();
    ResultSetMetaData rsmd = rs.getMetaData();
    int columnCount = rsmd.getColumnCount();
    
    while (rs.next()) {
      // Represent a row in DB. Key: Column name, Value: Column value
      Map<String, Object> row = new HashMap<>();
      for (int i = 1; i <= columnCount; i++) {
        // Note that the index is 1-based
        String colName = rsmd.getColumnName(i);
        Object colVal = rs.getObject(i);
        row.put(colName, colVal);
      }
      rows.add(row);
    }
    
    return mapper.writeValueAsString(rows);
  }
  
  public static boolean isNumeric(String str) {
    if (str == null)
      return false;
    char[] data = str.toCharArray();
    if (data.length <= 0)
      return false;
    int index = 0;
    if ((data[0] == '-' || data[0] == '+') && data.length > 1)
      index = 1;
    for (; index < data.length; index++) {
      if(data[index] < '.') continue;
      if (data[index] < '0' || data[index] > '9') // Character.isDigit() can go here too.
        return false;
    }
    return true;
  }
  public static void setParams(int index, Object param, PreparedStatement stmt) throws SQLException {
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

  public static Map<String, String> getIdField(Object model) {
    Map<String, String> idValues = new HashMap<>();
    
    if(model instanceof Map) {
      idValues.put("name", "_id");
      idValues.put("id", NanoIdUtils.randomNanoId());
      return idValues;
    }

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
