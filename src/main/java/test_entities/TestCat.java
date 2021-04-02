package test_entities;

import nosqlite.annotations.Document;
import nosqlite.annotations.Id;

//@Document
public class TestCat {

  @Id
  private String id;
  private String name;
  private String color;
  private String owner;
  private int age;

  private TestRace testRace;

  public TestCat() {}

  public TestCat(String name, String color) {
    this.name = name;
    this.color = color;
  }

  public TestCat(String name, String color, TestRace testRace) {
    this.name = name;
    this.color = color;
    this.testRace = testRace;
  }
  
  public TestCat(String name, String color, int age, TestRace testRace) {
    this.name = name;
    this.color = color;
    this.age = age;
    this.testRace = testRace;
  }
  
  public int getAge() {
    return age;
  }

  public void setAge(int age) {
    this.age = age;
  }

  public TestRace getTestRace() {
    return testRace;
  }

  public void setTestRace(TestRace testRace) {
    this.testRace = testRace;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }

  @Override
  public String toString() {
    return "\nCat{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", color='" + color + '\'' +
            ", age='" + age + '\'' +
            ", testRace='" + testRace + '\'' +
            '}';
  }
}
