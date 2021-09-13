import org.httprpc.sql.Parameters;
import org.httprpc.sql.ResultSetAdapter;
import test_entities.TestUser;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static nosqlite.Database.collection;

public class CollectionMainTest {
  public static void main(String[] args) {
    collection(config -> {
      config.dbPath = "db/test.db";
      config.runTestSuite = true;
      config.useBrowser = true;
    });
    
    // username == 'Loke' && age == 7
//    testParameterSQL();
  }
  
  private static void testParameterSQL() {
//    collection(TestUser.class).save(new TestUser("Theo", "12"));
    String query = "SELECT value FROM TestUser WHERE json_extract(value, '$.username') = :name AND json_extract(value, '$.password') = :password";
    Parameters parameters = Parameters.parse(query);
    try {
      PreparedStatement statement = collection().conn().prepareStatement(parameters.getSQL());
      Map params = new HashMap();
      params.put("name", "Theo");
      params.put("password", "12");
      parameters.apply(statement, params);
  
//      ResultSetAdapter resultSetAdapter = new ResultSetAdapter(statement.executeQuery());
//      List<Map> users = resultSetAdapter.stream().collect(Collectors.toList());
//      users.forEach(System.out::println);
  
      ResultSet rs = statement.executeQuery();
      rs.next();
      System.out.println(rs.getString(1));
      
    } catch (SQLException throwables) {
      throwables.printStackTrace();
    }
  }
  
  private static void multithreadTest(int iterations) {
    System.out.println("Multithread test");
  
    for(int i = 0; i < iterations; i++) {
      int finalI = i;
      new Thread(() -> {
        TestUser user = new TestUser("user-" + finalI, "pass-" + finalI, finalI);
        collection(TestUser.class).save(user);
      }).start();
    }
  }
}
