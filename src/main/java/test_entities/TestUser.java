package test_entities;

import nosqlite.annotations.Id;
import nosqlite.annotations.Document;

import java.util.ArrayList;
import java.util.List;

@Document
public class TestUser {

  @Id
  private String uid;
  private String username;
  private String password;
  private int age;

  private List<TestCat> testCats = new ArrayList<>();

  public TestUser() {
  }

  public TestUser(String username, String password) {
    this.username = username;
    this.password = password;
  }
  
  public TestUser(String username, int age) {
    this.username = username;
    this.age = age;
  }
  
  public TestUser(String username, String password, int age) {
    this.username = username;
    this.password = password;
    this.age = age;
  }
  
  public int getAge() {
    return age;
  }

  public void setAge(int age) {
    this.age = age;
  }
  
  public void addTestCat(TestCat testCat) { testCats.add(testCat); }

  public List<TestCat> getTestCats() {
    return testCats;
  }

  public void setTestCats(List<TestCat> testCats) {
    this.testCats = testCats;
  }

  public String getUid() {
    return uid;
  }

  public void setUid(String uid) {
    this.uid = uid;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
  
  @Override
  public String toString() {
    return "\nTestUser{" +
        "uid='" + uid + '\'' +
        ", username='" + username + '\'' +
        ", password='" + password + '\'' +
        ", age=" + age +
        ", testCats=" + testCats +
        '}';
  }
}
