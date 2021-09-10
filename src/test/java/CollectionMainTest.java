import test_entities.TestUser;

import static nosqlite.Database.collection;

public class CollectionMainTest {
  public static void main(String[] args) {
    System.out.println("Multithread test");
    // init collections to memory
    collection(config -> {
//      config.runAsync = true;
      config.dbPath = "db/test.db";
//      config.dbPath = ":memory:";
      config.runTestSuite = true;
    });
    
    int iterations = 1000;
  
    for(int i = 0; i < iterations; i++) {
      int finalI = i;
      new Thread(() -> {
        TestUser user = new TestUser("user-" + finalI, "pass-" + finalI, finalI);
        collection(TestUser.class).save(user);
      }).start();
    }
  }
}
