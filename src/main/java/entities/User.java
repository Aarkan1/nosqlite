package entities;

import database.Id;
import database.Model;

@Model
public class User {

  @Id
  private String uid;
  private String username;
  private String password;
  private int age;

  private Cat cat;

  public User() {
  }

  public User(String username, String password) {
    this.username = username;
    this.password = password;
  }

  public int getAge() {
    return age;
  }

  public void setAge(int age) {
    this.age = age;
  }

  public Cat getCat() {
    return cat;
  }

  public void setCat(Cat cat) {
    this.cat = cat;
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
    return "\nUser{" +
            "id=" + uid +
            ", username='" + username + '\'' +
            ", password='" + password + '\'' +
            ", cat='" + cat + '\'' +
            '}';
  }
}
