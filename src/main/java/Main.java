import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import database.Database;
import entities.Cat;
import entities.User;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import static database.Database.collection;

public class Main {
  public static void main(String[] args) {
    var db = new Database();

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
    user.setCat(new Cat("Bamse", "Pink"));

    collection("User").save(user);
    collection("User").save(user);
    collection("User").save(user);

    start = System.currentTimeMillis();

    /*
        saved 1'000 users
        jsoniter: 5494ms
        jackson:  5483ms
     */
//    saveUsers(importedUsers, 100);
//    System.out.println("saved 100'000 users: " + ((System.currentTimeMillis() - start)) + "ms");

    start = System.currentTimeMillis();
    System.out.println(collection("User").find("cat.race.time >= 50 && (cat.race.type =~ ma || cat.race.type = Houma)", 20).size());
    System.out.println("find users deep: " + ((System.currentTimeMillis() - start)) + "ms");

    start = System.currentTimeMillis();
    System.out.println(collection("User").find(200).size());
     /*
        find 3'000 users
        jsoniter: 30ms
        jackson:  124ms

        before group_concat(json)
        928
        find users deep: 453ms
        232038
        find all users: 766ms
     */
    System.out.println("find all users: " + ((System.currentTimeMillis() - start)) + "ms");

    System.out.println(collection("User").findById("yvhXGcmLXJCztIHAPgdb-"));
  }

  private static void saveUsers(User[] users, int iter) {
    for(int i = 0; i < iter; i++) {
      for(var u : users) {
        u.setUid(null);
      }
      collection("User").save(users);
    }
  }
}
