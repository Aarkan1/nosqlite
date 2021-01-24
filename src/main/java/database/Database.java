package database;

import org.reflections8.Reflections;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Database {
  private static Map<String, Collection> collections = new HashMap<>();
  private static Connection conn;

  public Database() {
    initDatabase("db/data.db");
  }

  private void initDatabase(String dbPath) {
    dbPath = dbPath.replaceAll("^/", "");
    File dir = new File(dbPath);
    dir.getParentFile().mkdirs();

    try {
      conn = DriverManager.getConnection("jdbc:sqlite:db/data.db");
    } catch (SQLException e) {
      e.printStackTrace();
    }

    Set<Class<?>> klasses = new Reflections().getTypesAnnotatedWith(Model.class);
    klasses.forEach(k -> collections.putIfAbsent(k.getSimpleName(), new Collection(conn, k)));
  }

  public static Collection collection(String entity) {
    return collections.get(entity);
  }
}
