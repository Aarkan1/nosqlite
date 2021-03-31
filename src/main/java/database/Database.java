package database;

import database.annotations.Document;
import database.handlers.CollectionConfig;
import database.handlers.CollectionConfigHandler;
import org.reflections8.Reflections;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Database {
  private static Map<String, Collection> collections = new ConcurrentHashMap<>();
  private static Connection conn;
  private static DbHelper dbHelper = null;
  private static Database singleton = null;
  private static boolean runAsync = true;
  private static String dbPath = "db/data.db";

  private Database() {
    initDatabase();
  }

  private void initDatabase() {
    if(!dbPath.equals(":memory:")) {
      dbPath = dbPath.replaceAll("^/", "");
      File dir = new File(dbPath);
      dir.getParentFile().mkdirs();
    }

    try {
      conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
      dbHelper = new DbHelper(conn, true, runAsync);
    } catch (SQLException e) {
      e.printStackTrace();
      return;
    }

    Set<Class<?>> klasses = new Reflections().getTypesAnnotatedWith(Document.class);
    klasses.forEach(k -> {
      String name = k.getAnnotation(Document.class).collection();
      name = name.equals("default") ? k.getSimpleName() : name;
      collections.putIfAbsent(name, new Collection(dbHelper, k, name));
    });
  }

  public static Collection collection(Class klass) {
    return collection(klass.getSimpleName());
  }

  public static Collection collection(String doc) {
    if(singleton == null) singleton = new Database();
    Collection coll = collections.get(doc);
    if(coll == null) {
      coll = new Collection(dbHelper,null, doc);
      collections.put(doc, coll);
    }
    return coll;
  }
  
  // Must be called before other collection calls
  public static void collection(CollectionConfigHandler config) {
    if(singleton == null) {
      CollectionConfig op = new CollectionConfig();
      config.handle(op);
      runAsync = op.runAsync;
      dbPath = op.dbPath;
      singleton = new Database();
    }
  }
}
