import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import test_entities.TestCat;
import test_entities.TestRace;
import test_entities.TestUser;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static nosqlite.Database.collection;
import static nosqlite.Database.collectionNames;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static nosqlite.utilities.Filter.*;

/**
 * @author Johan Wirén
 */
public class CollectionTest {
  private long time;
  
  @BeforeAll
  public static void setup() {
    // init collections to memory
    collection(config -> {
//      config.runAsync = false;
//      config.dbPath = "db/test.db";
      config.dbPath = ":memory:";
      config.runTestSuite = true;
    });
  }
  
  @AfterEach
  public void tear() {
    // clear collections after each test
    collection(TestUser.class).delete();
    collection(TestCat.class).delete();
  }
  
  @AfterAll
  public static void tearAll() {
    // stop collection threads
    collection(TestUser.class).close();
    collection(TestCat.class).close();
    collection("map").close();
  
    File testdb = new File("db/test.db");
    testdb.delete();
  }
  
  @Test
  public void testSaveDocument() {
    TestUser testUser = new TestUser("Loke", "abc123");
    testUser.setUid("OMqGBZy-AIHXKcyZSGMQe");
    assertEquals(testUser, collection(TestUser.class).save(testUser));
    
    String json = "{\"uid\":\"OMqGBZy-AIHXKcyZSGMQe\",\"username\":\"Loke\",\"password\":\"abc123\",\"age\":0,\"testCats\":[]}";
    assertEquals(json, collection(TestUser.class).findByIdAsJson(testUser.getUid()));
    
    TestCat testCat = new TestCat("Tyson", "Gray");
    TestCat testCat2 = new TestCat("Cocos", "Orange");
    TestCat testCat3 = new TestCat("Snuggles", "White");
    
    List<TestCat> testCats = new ArrayList<>();
    testCats.add(testCat);
    testCats.add(testCat2);
    testCats.add(testCat3);
    collection(TestCat.class).save(testCats);
  
//    System.out.println(collection(TestCat.class).find());
  
//    System.out.println(testCats);
    
    TestUser testUser1 = new TestUser("loke@loke.se", "(abc'\"(){}£%/\\123åÄö$.-_!#@");
    testUser1.setUid("test123");
    
    collection(TestUser.class).save(testUser1);
  
    String testUser1JSON = "{\"uid\":\"test123\",\"username\":\"loke@loke.se\",\"password\":\"(abc'\\\"(){}£%/\\\\123åÄö$.-_!#@\",\"age\":0,\"testCats\":[]}";
  
    assertEquals(collection(TestUser.class).findOneAsJson("username==loke@loke.se"), testUser1JSON);
    assertEquals(collection(TestUser.class).findOneAsJson("password==(abc'\"(){}£%/\\123åÄö$.-_!#@"), testUser1JSON);
  }
  
  @Test
  public void testPut() {
    collection("map").put("key2", "{\"id\":null,\"name\":\"Tyson\",\"color\":\"Gray\",\"owner\":null,\"age\":0,\"testRace\":null}");
    assertEquals(collection("map").put("key", "value"), "\"value\"");
    assertEquals(collection("map").put("key", "test"), "\"test\"");
    assertEquals(collection("map").put("key", 10), "10");
    assertEquals(collection("map").put("key2", 10.5), "10.5");
    TestCat testCat = new TestCat("Tyson", "Gray");
    assertEquals(collection("map").put("key2", testCat), "{\"id\":null,\"name\":\"Tyson\",\"color\":\"Gray\",\"owner\":null,\"age\":0,\"testRace\":null}");
  }
  
  @Test
  public void testPutIfAbsent() {
    testPut();
    assertEquals(collection("map").putIfAbsent("key", "test"), "'key' already exists");
    assertEquals(collection("map").putIfAbsent("key2", "test"), "'key2' already exists");
    assertEquals(collection("map").putIfAbsent("key3", "test"), "\"test\"");
  }
  
  @Test
  public void testGetClass() {
    testPut();
    assertEquals(collection("map").count(), 2);
    assertTrue(collection("map").get("key2", TestCat.class) instanceof TestCat);
    assertEquals(collection("map").get("key2", TestCat.class).getName(), "Tyson");
    assertNull(collection("map").get("key2", TestUser.class).getUsername());
  }
  
  @Test
  public void testGetString() {}
  
  @Test
  public void testRemove() {}

  @Disabled
  @Test
  public void testFindOptions() {
    testSaveList();
    collection();

    long start = System.currentTimeMillis();
    System.out.println(collectionNames());
    System.out.println("collectionNames() in " + (System.currentTimeMillis() - start) + "ms");

    System.out.println(collection(TestUser.class).find(op -> {
      op.filter = "testCats[0].testRace.type==Main Coon";
      op.limit = 3;
      op.offset = 0;
      op.sort = "age=asc";
    }));

    System.out.println(collection(TestUser.class).find(op -> {
      op.sort = "age<";
      op.limit = 2;
    }));
  
//    System.out.println(collection(TestUser.class).find("testCats[0].testRace.type==Main Coon", 10));
  }
  
  @Test
  public void testFind() {
    testSaveList();
    assertEquals(collection(TestUser.class).find().size(), 100);

//  eq
    assertEquals(collection(TestUser.class).find("username=User-1").size(), 1);
    assertEquals(collection(TestUser.class).find("testCats[0].name = Cat-1").size(), 1);
    assertEquals(collection(TestUser.class).find("testCats[0].testRace.type=Main Coon").size(), 50);
    assertEquals(collection(TestUser.class).find(eq("testCats[0].testRace.type", "Main Coon")).size(), 50);
    
    TestUser testUser = (TestUser) collection(TestUser.class).find("username=User-1").get(0);
    assertTrue(testUser.getUsername().equals("User-1"));
    assertTrue(testUser.getAge() == 1);

//  ne
    assertEquals(collection(TestUser.class).find("username!=User-1").size(), 99);
    assertEquals(collection(TestUser.class).find(ne("username","User-1")).size(), 99);

//  gt
    assertEquals(collection(TestUser.class).find("age>50").size(), 49);
    assertEquals(collection(TestUser.class).find(gt("age", 50)).size(), 49);

//  gte
    assertEquals(collection(TestUser.class).find("age >= 50").size(), 50);
    assertEquals(collection(TestUser.class).find("testCats[0].age>=50").size(), 50);
    assertEquals(collection(TestUser.class).find(gte("testCats[0].age", 50)).size(), 50);

//  lt
    assertEquals(collection(TestUser.class).find("age < 20").size(), 20);
    assertEquals(collection(TestUser.class).find(lt("age", 20)).size(), 20);

//  lte
    assertEquals(collection(TestUser.class).find("age <= 20").size(), 21);
    assertEquals(collection(TestUser.class).find(lte("age", 20)).size(), 21);

//  text
    assertEquals(collection(TestUser.class).find("testCats[0].testRace.type=~Main%").size(), 50);
    assertEquals(collection(TestUser.class).find("username=~user%").size(), 100);
    assertEquals(collection(TestUser.class).find("username=~%er%").size(), 100);
    assertEquals(collection(TestUser.class).find(text("username","user%")).size(), 100);

//  regex
    assertEquals(collection(TestUser.class).find("username~~[0-3]$").size(), 40);
    assertEquals(collection(TestUser.class).find(regex("username","[0-3]$")).size(), 40);
    assertEquals(collection(TestUser.class).find("username~~([0-3])").size(), 58);

//  not
    assertEquals(collection(TestUser.class).find("!(testCats[0].testRace.type=Main Coon&&testCats[0].testRace.time>=80)").size(), 80);
    assertEquals(collection(TestUser.class).find("! ( testCats[0].testRace.type=Main Coon && testCats[0].testRace.time>=80 )").size(), 80);
    assertEquals(collection(TestCat.class).find("!age < 20").size(), 80);
    assertEquals(collection(TestCat.class).find(not(lt("age",20))).size(), 80);

//  in
    assertEquals(collection(TestUser.class).find("username=[User-1,User-2,User-3]").size(), 3);
    assertEquals(collection(TestUser.class).find("username=[User-1, User-2, User-3]").size(), 3);
    assertEquals(collection(TestUser.class).find(in("username", "User-1", "User-2", "User-3", "User-4")).size(), 4);
    
    List<String> usernames = new ArrayList<>();
    usernames.add("User-1");
    usernames.add("User-2");
    usernames.add("User-3");
    assertEquals(collection(TestUser.class).find(in("username", usernames)).size(), 3);
    
    String[] names = new String[3];
    names[0] = "User-1";
    names[1] = "User-2";
    names[2] = "User-3";
    assertEquals(collection(TestUser.class).find(in("username", names)).size(), 3);

//  and
    assertEquals(collection(TestUser.class).find("username=User-1 && age=1").size(), 1);
    assertEquals(collection(TestUser.class).find("testCats[0].testRace.type=Main Coon&&testCats[0].testRace.time>=80").size(), 20);
    assertEquals(collection(TestUser.class).find("  testCats[0].testRace.type  =  Main Coon  &&  testCats[0].testRace.time  >=  80  ").size(), 20);
    assertEquals(collection(TestUser.class).find("testCats[0].testRace.type=Main Coon&&testCats[0].testRace.time>=80").size(), 20);
    assertEquals(collection(TestCat.class).find("age>=40&&(testRace.type=Norwegian Forest||testRace.type=Main Coon)").size(), 60);
    assertEquals(collection(TestCat.class).find("age >= 40 && ( testRace.type = Norwegian Forest || testRace.type = Main Coon )").size(), 60);
    assertEquals(collection(TestCat.class).find(and(
        gte("age", 40),
          or(
              eq("testRace.type", "Norwegian Forest"),
              eq("testRace.type", "Main Coon")
          )
    )).size(), 60);
    assertEquals(and(
        gte("age", 40),
        or(
            eq("testRace.type", "Norwegian Forest"),
            eq("testRace.type", "Main Coon")
        )
    ), "age>=40&&(testRace.type=Norwegian Forest||testRace.type=Main Coon)");

//  or
    assertEquals(collection(TestUser.class).find("username==User-1 || username=User-2").size(), 2);
    assertEquals(collection(TestCat.class).find("age < 20 || age >= 80 || testRace.type = Norwegian Forest").size(), 70);
    assertEquals(or(
        lt("age", 20),
        gte("age", 80),
        eq("testRace.type", "Norwegian Forest")
    ), "age<20||age>=80||testRace.type=Norwegian Forest");
    assertEquals(collection(TestCat.class).find(or(
            lt("age", 20),
            gte("age", 80),
            eq("testRace.type", "Norwegian Forest")
        )).size(), 70);
  }
  
  @Test
  public void testFindById() {
    TestUser testUser = new TestUser("Stefan", "stiffe123");
    testUser.setUid("abc123");
    collection(TestUser.class).save(testUser);
    TestUser fromColl = collection(TestUser.class).findById("abc123");
    assertEquals(fromColl.getUsername(), "Stefan");
    assertEquals(fromColl.getPassword(), "stiffe123");
    assertNull(collection(TestUser.class).findById("123abc"));
  }
  
  @Test
  public void testFindAsJson() {}
  
  @Test
  public void testSaveJson() {}
  
  @Test
  public void testSaveList() {
    List<TestUser> testUsers = new ArrayList<>();
    List<TestCat> testCats = new ArrayList<>();
    
    for(int i = 0; i < 100; i++) {
      TestCat testCat1 = new TestCat("Cat-" + i, "gray", i,
          new TestRace(
              i < 50 ? "Norwegian Forest" : "Main Coon",
              i
          ));
      TestCat testCat2 = new TestCat("Cat-2-" + i, "white", i,
          new TestRace(
              i < 50 ? "Norwegian Forest" : "Main Coon",
              i
          ));
      testCats.add(testCat1);
      
      TestUser testUser = new TestUser("User-" + i,
          "abc-" + i,
          i);
  
      testUser.addTestCat(testCat1);
      testUser.addTestCat(testCat2);
      
      testUsers.add(testUser);
    }
    collection(TestUser.class).save(testUsers);
    collection(TestCat.class).save(testCats);
    
    assertEquals(collection(TestUser.class).count(), 100);
    assertEquals(collection(TestCat.class).count(), 100);
  }
  
  @Test
  public void testDelete() {
    testSaveList();
  
    collection(TestUser.class).deleteOne("username==User-97");
    collection(TestUser.class).delete("username==User-98");
    collection(TestUser.class).delete("username==User-99");
    assertEquals(collection(TestUser.class).count(), 97);
    
    collection(TestUser.class).delete("age>50");
    assertEquals(collection(TestUser.class).count(), 51);
    
    collection(TestUser.class).deleteOne("age>30");
    assertEquals(collection(TestUser.class).count(), 50);
    
    TestUser user1 = collection(TestUser.class).findOne("username==User-1");
    TestUser user2 = collection(TestUser.class).findOne("username==User-2");
    collection(TestUser.class).delete(user1);
    assertNull(collection(TestUser.class).findById(user1.getUid()));
    collection(TestUser.class).delete(user2);
    assertEquals(collection(TestUser.class).count(), 48);
  
    collection(TestUser.class).delete();
    assertEquals(collection(TestUser.class).count(), 0);
  }
  
  @Test
  public void testUpdateField() {
    TestUser testUser = new TestUser("User-A", "abc-A", 1);
    testUser.addTestCat(new TestCat("Cat-A", "gray", 1, new TestRace("Norwegian Forest", 1 )));
    testUser.setUid("abc123");
  
    collection(TestUser.class).save(testUser);
  
    collection(TestUser.class).updateFieldById("abc123", "username", "User-B");
    TestUser afterUpdate = collection(TestUser.class).findById("abc123");
    assertEquals(afterUpdate.getUsername(), "User-B");
    
    collection(TestUser.class).updateFieldById("abc123", "testCats[0].name", "Cat-B");
    afterUpdate = collection(TestUser.class).findById("abc123");
    assertEquals(afterUpdate.getTestCats().get(0).getName(), "Cat-B");
    
    collection(TestUser.class).updateField("testCats[0].color", "orange");
    afterUpdate = collection(TestUser.class).findById("abc123");
    assertEquals(afterUpdate.getTestCats().get(0).getColor(), "orange");
    
    collection(TestUser.class).updateField(testUser, "testCats[0].color", "blue");
    afterUpdate = collection(TestUser.class).findById("abc123");
    assertEquals(afterUpdate.getTestCats().get(0).getColor(), "blue");
    
    collection(TestUser.class).updateField(testUser, "testCats[0].testRace", new TestRace("Race-A", 2 ));
    afterUpdate = collection(TestUser.class).findById("abc123");
    assertEquals(afterUpdate.getTestCats().get(0).getTestRace().getType(), "Race-A");
    assertEquals(afterUpdate.getTestCats().get(0).getTestRace().getTime(), 2);
  }
  
  @Test
  public void testRemoveField() {}
  
  @Test
  public void testChangeFieldName() {}
  
  // run watch test separately because they're triggering on all tests
  @Disabled
  @Test
  public void testWatchers() {
    TestUser user = new TestUser("Test-1", 11);
    user.setUid("abc123");
    
    collection(TestUser.class).watch(watchData -> {
      assertEquals(watchData.model, TestUser.class.getSimpleName());
      TestUser userData = (TestUser) watchData.data.get(0);
      
      if(watchData.event.equals("insert")) {
        TestUser byId = collection(TestUser.class).findById("abc123");
        assertEquals(byId.getUsername(), "Test-1");
        assertEquals(user.getUsername(), userData.getUsername());
      }
      else if(watchData.event.equals("update")) {
        TestUser byId = collection(TestUser.class).findById("abc123");
        assertEquals(userData.getUsername(), "Test-2");
        assertEquals(byId.getUsername(), "Test-2");
      }
      else if(watchData.event.equals("delete")) {
        assertEquals(userData.getUsername(), "Test-2");
        assertNull(collection(TestUser.class).findById("abc123"));
      }
    });
    
    collection(TestUser.class).watch("insert", watchData -> {
      TestUser userData = (TestUser) watchData.data.get(0);
      TestUser byId = collection(TestUser.class).findById("abc123");
      assertEquals(byId.getUsername(), "Test-1");
      assertEquals(user.getUsername(), userData.getUsername());
    });
    
    collection(TestUser.class).watch("update", watchData -> {
      TestUser userData = (TestUser) watchData.data.get(0);
      assertEquals(userData.getUsername(), "Test-2");
      TestUser byId = collection(TestUser.class).findById("abc123");
      assertEquals(byId.getUsername(), "Test-2");
    });
    
    collection(TestUser.class).watch("delete", watchData -> {
      TestUser userData = (TestUser) watchData.data.get(0);
      assertEquals(userData.getUsername(), "Test-2");
      assertNull(collection(TestUser.class).findById("abc123"));
    });
  
    collection(TestUser.class).save(user);
    collection(TestUser.class).updateFieldById("abc123", "username", "Test-2");
    collection(TestUser.class).delete(user);
  
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    File testdb = new File("db/test.db");
    testdb.delete();
  }
  
  @Disabled
  @Test
  public void stressTest() {
    int iter = 100;
    populateUsers(iter);
    
    start();
    int sortedCount = collection(TestUser.class).find(null, "testCats[0].testRace.type=asc", 0, 0).size();
//    int sortedCount = collection(TestUser.class).find(op -> {
//      op.filter = "age>30";
//      op.sort = "testCats[0].testRace.type=asc";
////      op.limit = 100;
////      op.offset = 100;
//    }).size();
    stop("Sorted " + iter + "000 docs");
    System.out.println("Sorted count: " + sortedCount);
    
    start();
    int regexCount = collection(TestUser.class).find("username~~^(Joe|Ald|Ash).*").size();
    stop("Regex on " + iter + "000 docs");
    System.out.println("Regex count: " + regexCount);
    
//    start();
//    for(int i = 0; i < 1000; i++) {
//      collection(TestUser.class).save(new TestUser("name-" + i, "pass-" + i));
//    }
//    stop("saved 1000 individual docs");
    
    start();
    stop("size of " + collection(TestUser.class).count() + " docs");
  }
  
  private void populateUsers(int iter) {
    TestUser[] importedTestUsers = null;
    try {
      ObjectMapper mapper = new ObjectMapper();
      mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      importedTestUsers = mapper.readValue(new File(Paths.get("src/test/java/testUsers.json").toString()), TestUser[].class);
    } catch (IOException e) {
      e.printStackTrace();
    }
    
    long sum = 0;
    
    for(int i = 0; i < iter; i++) {
      for(TestUser u : importedTestUsers) {
        u.setUid(null);
      }
      start();
      collection(TestUser.class).save(importedTestUsers);
      sum += (System.currentTimeMillis() - time);
    }
    
    System.out.println("saved " + iter + "000 docs as " + iter + " arrays: " + sum + "ms");
  }
  
  private void start() {
    time = System.currentTimeMillis();
  }
  
  private void stop(String text) {
    System.out.println(text + ": " + ((System.currentTimeMillis() - time)) + "ms");
  }
}
