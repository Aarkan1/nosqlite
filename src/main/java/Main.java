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
  public static void main(String[] args) throws IOException {
//    var db = new Database();

//    collection("map").putIfAbsent("name", "Tea");
//    collection("map").put("name", "Aarkan");
//    collection("map").put("age", 22);
//    collection("map").put("saldo", 123.45);
//    collection("map").put("dude", new User("Dude", "hej_hopp"));
//    System.out.println(collection("map").remove("saldo"));

//    System.out.println(collection("map").get("name"));
//    System.out.println(collection("map").get("saldo"));
//    System.out.println(collection("map").get("dude"));

    collection("User").watch("insert", watchData -> {
      System.out.println("[insert] User: " + watchData.data);
    });
    collection("User").watch("update", watchData -> {
      System.out.println("[update] User: " + watchData.data);
    });
    collection("User").watch("delete", watchData -> {
      System.out.println("[delete] User: " + watchData.data);
    });
    collection("Cat").watch("update", watchData -> {
      System.out.println("[update] Cat: " + watchData.data);
    });


    long start = System.currentTimeMillis();
    System.out.println("users size: " + collection("User").size() + ", found in " + ((System.currentTimeMillis() - start)) + "ms");

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

//    collection("Cat").save(cat);
    collection("User").save(user);
//    System.out.println(collection("User").save(user));
    System.out.println("deleted user: " + collection("User").delete(user));

    start = System.currentTimeMillis();
    collection("User").updateFieldById("BcooRXpbRJnvfB91IViXk", "username", "Aarkan");
    System.out.println("update field by id: " + ((System.currentTimeMillis() - start)) + "ms");

//    TODO: filter update is MUCH slower on large datasets
//    start = System.currentTimeMillis();
//    System.out.println("update: " + collection("User").updateField("uid=BcooRXpbRJnvfB91IViXk", "cat.name", "Tyson"));
//    System.out.println("update field: " + ((System.currentTimeMillis() - start)) + "ms");

//    System.out.println("number of users: " + collection("User").find().size());
//    System.out.println("find all users: " + ((System.currentTimeMillis() - start)) + "ms");

    /*
        saved 1'000 users
        jsoniter: 5494ms
        jackson:  5483ms
     */
//    collection("User").delete();
//      saveUsers(importedUsers, 1);
//    System.out.println("saved 1'000 users: " + ((System.currentTimeMillis() - start)) + "ms");

    start = System.currentTimeMillis();
    collection("User").find(
        and(
            gte("cat.race.time", 50),
            or(
                text("cat.race.type", "ma"),
                eq("cat.race.type", "Houma")
            ),
            not("cat.race.type", "Houma")
        ), 20);
//    System.out.println(collection("User").find("username=Aarkan&&cat.color=blue", 1));
    System.out.println("find with deep search: " + ((System.currentTimeMillis() - start)) + "ms");

//    System.out.println(regex("cat.name", "^Bad[a-z]*"));

//    System.out.println(in("cat.age", 2,5));
//    System.out.println(in("cat.age", values));

    int size = 1000;
    start = System.currentTimeMillis();
    collection("User").find(size, 0);
    System.out.println("find "+size+" users: " + ((System.currentTimeMillis() - start)) + "ms");

    start = System.currentTimeMillis();
    collection("User").findAsJson(size, 0);
    System.out.println("findAsJson "+size+" users: " + ((System.currentTimeMillis() - start)) + "ms");

    start = System.currentTimeMillis();
    collection("User").find("username~~^Sa", null, 10, 0);
    System.out.println("find with regex: " + ((System.currentTimeMillis() - start)) + "ms");

//    TODO: sort is slow on large datasets
//    start = System.currentTimeMillis();
//    System.out.println(collection("User").find(op -> {
//      op.filter = "username=~ma";
//      op.sort = "cat.age<";
//      op.limit = 10;
//    }).size());
//    System.out.println("find with sort: " + ((System.currentTimeMillis() - start)) + "ms");

    System.out.println("num of cats: " + collection("Cat").size());
    collection("Cat").updateFieldById("6YKGSNmc91sLOeG4XAC0b", "age", 10);
    System.out.println(collection("Cat").findByIdAsJson("6YKGSNmc91sLOeG4XAC0b"));
  }

  private static void saveUsers(User[] users, int iter) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    collection("User").find(10, 0);
    System.out.println("before saveUsers: " + collection("User").find().size());
    long start = System.currentTimeMillis();
    for(int i = 0; i < iter; i++) {
      User[] copy = mapper.readValue(mapper.writeValueAsBytes(users), User[].class);
      for(var u : copy) {
//      new Thread(() -> {
        u.getCat().setId(null);
        collection("Cat").save(u.getCat());
//      }).start();
      }
//        collection("User").save(copy);

//      new Thread(() -> {
//        System.out.println("Concurrent read " + collection("User").find(100).size());
//      }).start();
    }
//    System.out.println("commit users: " + ((System.currentTimeMillis() - start)) + "ms");
//    System.out.println("after saveUsers: " + collection("User").find().size());
  }
}
