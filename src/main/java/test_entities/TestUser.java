package test_entities;

import nosqlite.annotations.Id;
import nosqlite.annotations.Document;

@Document
public class TestUser {

  @Id
  private String uid;
  private String username;
  private String password;
  private int age;

  private TestCat testCat;

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
  
  public TestUser(String username, String password, int age, TestCat testCat) {
    this.username = username;
    this.password = password;
    this.age = age;
    this.testCat = testCat;
  }
  
  public int getAge() {
    return age;
  }

  public void setAge(int age) {
    this.age = age;
  }

  public TestCat getTestCat() {
    return testCat;
  }

  public void setTestCat(TestCat testCat) {
    this.testCat = testCat;
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
        ", testCat=" + testCat +
        '}';
  }
}
