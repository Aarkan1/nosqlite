package nosqlite;

import nosqlite.annotations.Document;
import nosqlite.annotations.Id;
import nosqlite.browser.Browser;
import nosqlite.handlers.CollectionConfig;
import nosqlite.handlers.CollectionConfigHandler;
import org.reflections8.Reflections;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteOpenMode;

import java.io.File;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Johan Wirén
 */
public class Database {
  private static Map<String, Collection> collections = new ConcurrentHashMap<>();
  private static Connection conn;
  private static DbHelper dbHelper = null;
  private static Database singleton = null;
  
  public static boolean runAsync = false;
  public static boolean useBrowser = false;
  public static boolean useWatchers = false;
  public static boolean runTestSuite = false;
  public static String dbPath = "db/data.db";

  private Database() {
    initDatabase();
  }

  private void initDatabase() {
    if(!dbPath.equals(":memory:")) {
      dbPath = dbPath.replaceAll("^/", "");
      File dir = new File(dbPath);
      if(dir.getParentFile() != null) {
        dir.getParentFile().mkdirs();
      }
    }

    try {
      // mode: Serialize, works with multiple threads
      SQLiteConfig config = new SQLiteConfig();
      config.setOpenMode(SQLiteOpenMode.FULLMUTEX);
      conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath, config.toProperties());
      dbHelper = new DbHelper(conn, true, runAsync);
    } catch (SQLException e) {
      e.printStackTrace();
      return;
    }
  
    Map<String, Class<?>> collNames = new HashMap<>();
    Map<String, String> idFields = new HashMap<>();

    Set<Class<?>> klasses = new Reflections().getTypesAnnotatedWith(Document.class);
    for(Class<?> k : klasses) {
      if(runTestSuite || !k.getPackage().getName().contains("test_entities")) {
        String name = k.getAnnotation(Document.class).collection();
        name = name.equals("default_coll") ? k.getSimpleName() : name;
        collections.putIfAbsent(name, new Collection(dbHelper, k, name));
        collNames.putIfAbsent(name, k);
  
        for(Field field : k.getDeclaredFields()) {
          if(field.isAnnotationPresent(Id.class)) {
            idFields.putIfAbsent(name, field.getName());
            break;
          }
        }
      }
    }
    
//    if (useWatchers) watchCollections(collNames);
    if (useBrowser) {
      new Browser(collNames, idFields);
    }
  }

  /**
   *
   * @return name of collections that contains saved documents
   */
  public static List<String> collectionNames() {
    String tablesQuery = dbHelper.get("SELECT GROUP_CONCAT(name) FROM sqlite_master WHERE type='table'");
    String[] tables = tablesQuery.split(",");
    List<String> asList = new ArrayList<>();

    for(String table : tables) {
      int count = Integer.parseInt(dbHelper.get(String.format("SELECT COUNT(*) FROM %s", table)));
      if(count > 0) {
        asList.add(table);
      }
    }
    return asList;
  }

  public static Collection collection(Class klass) { return collection(klass.getSimpleName()); }
  
  public static Collection collection() { return collection("default_coll"); }

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
      CollectionConfig op = new CollectionConfig();
      config.handle(op);
      collection(op);
  }
  
  // Must be called before other collection calls
  public static void collection(CollectionConfig config) {
    if(singleton == null) {
      runAsync = config.runAsync;
      dbPath = config.dbPath;
      useBrowser = config.useBrowser;
      useWatchers = config.useWatcher;
      runTestSuite = config.runTestSuite;
      singleton = new Database();
    } else {
      System.err.println("collection with config must be called before any other collection call");
    }
  }
}
