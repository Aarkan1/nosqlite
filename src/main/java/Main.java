import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import entities.Cat;
import entities.User;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import static database.Database.collection;
import static utilities.Filter.*;

public class Main {
  public static void main(String[] args) {
//    var db = new Database();

//    collection("map").putIfAbsent("name", "Aarkan");
//    collection("map").put("name", "Loke");
//    collection("map").put("age", 32);
//    collection("map").put("saldo", 123.45);
//    collection("map").put("dude", new User("Dude", "hej_hopp"));
//    System.out.println(collection("map").remove("saldo"));

//    System.out.println(collection("map").get("name"));
//    System.out.println(collection("map").get("saldo"));
//    System.out.println(collection("map").get("dude"));

//    collection("User").watch(watchData -> {
//      System.out.println("Entity: " + watchData.getEntity());
//      System.out.println("Event: " + watchData.getEvent());
//      System.out.println("Data: " + watchData.getData());
//    });

    long start = System.currentTimeMillis();

    User[] importedUsers = null;
    try {
      ObjectMapper mapper = new ObjectMapper();
      mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      importedUsers = mapper.readValue(new File(Paths.get("src/main/java/users.json").toString()), User[].class);
    } catch (IOException e) {
      e.printStackTrace();
    }
//    System.out.println("import file: " + ((System.currentTimeMillis() - start)) + "ms");
//    System.out.println(importedUsers.length);

    var user = new User("arn", "abc123");
    var cat = new Cat("Bamse", "Pink");
    user.setCat(cat);

//    collection("User").save(cat);
//    collection("User").save(user);
//    collection("User").save(user);

    start = System.currentTimeMillis();

    /*
        saved 1'000 users
        jsoniter: 5494ms
        jackson:  5483ms
     */
//    saveUsers(importedUsers, 3);
//    System.out.println("saved 1'000 users: " + ((System.currentTimeMillis() - start)) + "ms");

    start = System.currentTimeMillis();
    System.out.println(collection("User").find(
        and(
            gte("cat.race.time", 50),
            or(
                text("cat.race.type", "ma"),
                eq("cat.race.type", "Houma")
            ),
            not("cat.race.type", "Houma")
        ), 20).size());
//    System.out.println(collection("User").find("username=Aarkan&&cat.color=blue", 1));
    System.out.println("find users deep: " + ((System.currentTimeMillis() - start)) + "ms");

    int size = 10000;
    start = System.currentTimeMillis();
    System.out.println(collection("User").find(size).size());
    System.out.println("find all users: " + ((System.currentTimeMillis() - start)) + "ms");

    start = System.currentTimeMillis();
    collection("User").findAsJson(size);
    System.out.println("findAsJson all users: " + ((System.currentTimeMillis() - start)) + "ms");

  }

  private static void saveUsers(User[] users, int iter) {
    for(int i = 0; i < iter; i++) {
//      new Thread(() -> {
        for(var u : users) {
          u.setUid(null);
        }
        collection("User").save(users);
//      }).start();
    }
  }
}
