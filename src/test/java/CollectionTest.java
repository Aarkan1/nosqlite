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

import static database.Database.collection;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static utilities.Filter.*;

public class CollectionTest {
  private long time;
  
  @BeforeAll
  public static void setup() {
    // init collections to memory
    collection(op -> {
//      op.dbPath = "db/test.db";
      op.dbPath = ":memory:";
      op.runAsync = false;
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
    
    File testdb = new File("db/test.db");
    testdb.delete();
  }
  
  @Test
  public void testSaveDocument() {
    TestUser testUser = new TestUser("Loke", "abc123");
    testUser.setUid("OMqGBZy-AIHXKcyZSGMQe");
    assertEquals(testUser, collection(TestUser.class).save(testUser));
    
    String json = "{\"uid\":\"OMqGBZy-AIHXKcyZSGMQe\",\"username\":\"Loke\",\"password\":\"abc123\",\"age\":0,\"testCat\":null}";
    assertEquals(json, collection(TestUser.class).findByIdAsJson(testUser.getUid()));
  }
  
  @Test
  public void testPut() {
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
  
  @Test
  public void testFind() {
    testSaveList();
    
    assertEquals(collection(TestUser.class).find().size(), 100);
//  eq
    assertEquals(collection(TestUser.class).find("username=User-1").size(), 1);
    assertEquals(collection(TestUser.class).find("testCat.name = Cat-1").size(), 1);
    assertEquals(collection(TestUser.class).find("testCat.testRace.type=Main Coon").size(), 50);
    assertEquals(collection(TestUser.class).find(eq("testCat.testRace.type", "Main Coon")).size(), 50);
    
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
    assertEquals(collection(TestUser.class).find("testCat.age>=50").size(), 50);
    assertEquals(collection(TestUser.class).find(gte("testCat.age", 50)).size(), 50);

//  lt
    assertEquals(collection(TestUser.class).find("age < 20").size(), 20);
    assertEquals(collection(TestUser.class).find(lt("age", 20)).size(), 20);

//  lte
    assertEquals(collection(TestUser.class).find("age <= 20").size(), 21);
    assertEquals(collection(TestUser.class).find(lte("age", 20)).size(), 21);

//  text
    assertEquals(collection(TestUser.class).find("username=~user").size(), 100);
    assertEquals(collection(TestUser.class).find("username=~er").size(), 100);
    assertEquals(collection(TestUser.class).find(text("username","user")).size(), 100);

//  regex
    assertEquals(collection(TestUser.class).find("username~~[0-3]$").size(), 40);
    assertEquals(collection(TestUser.class).find(regex("username","[0-3]$")).size(), 40);

//  not
    assertEquals(collection(TestUser.class).find("!(testCat.testRace.type=Main Coon&&testCat.testRace.time>=80)").size(), 80);
    assertEquals(collection(TestUser.class).find("! ( testCat.testRace.type=Main Coon && testCat.testRace.time>=80 )").size(), 80);
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
    assertEquals(collection(TestUser.class).find("testCat.testRace.type=Main Coon&&testCat.testRace.time>=80").size(), 20);
    assertEquals(collection(TestUser.class).find("  testCat.testRace.type  =  Main Coon  &&  testCat.testRace.time  >=  80  ").size(), 20);
    assertEquals(collection(TestUser.class).find("testCat.testRace.type=Main Coon&&testCat.testRace.time>=80").size(), 20);
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
      TestCat testCat = new TestCat("Cat-" + i, "gray", i,
          new TestRace(
              i < 50 ? "Norwegian Forest" : "Main Coon",
              i
          ));
      testCats.add(testCat);
      
      testUsers.add(new TestUser("User-" + i,
          "abc-" + i,
          i,
          testCat));
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
    TestUser testUser = new TestUser("User-A", "abc-A", 1,
        new TestCat("Cat-A", "gray", 1, new TestRace("Norwegian Forest", 1 )));
    testUser.setUid("abc123");
  
    collection(TestUser.class).save(testUser);
  
    collection(TestUser.class).updateFieldById("abc123", "username", "User-B");
    TestUser afterUpdate1 = collection(TestUser.class).findById("abc123");
    assertEquals(afterUpdate1.getUsername(), "User-B");
    
    collection(TestUser.class).updateFieldById("abc123", "testCat.name", "Cat-B");
    TestUser afterUpdate2 = collection(TestUser.class).findById("abc123");
    assertEquals(afterUpdate2.getTestCat().getName(), "Cat-B");
    
    collection(TestUser.class).updateField("testCat.color", "orange");
    TestUser afterUpdate3 = collection(TestUser.class).findById("abc123");
    assertEquals(afterUpdate3.getTestCat().getColor(), "orange");
    
    collection(TestUser.class).updateField(testUser, "testCat.color", "blue");
    TestUser afterUpdate4 = collection(TestUser.class).findById("abc123");
    assertEquals(afterUpdate4.getTestCat().getColor(), "blue");
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
      assertEquals(watchData.entity, TestUser.class.getSimpleName());
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
  }
  
  @Disabled
  @Test
  public void stressTest() {
    populateUsers(100);
    
    start();
    for(int i = 0; i < 1000; i++) {
      collection(TestUser.class).save(new TestUser("name-" + i, "pass-" + i));
    }
    stop("saved 1000 individual docs");
    
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
      for(var u : importedTestUsers) {
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
