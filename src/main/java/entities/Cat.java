package entities;

import database.annotations.Document;
import database.annotations.Id;

@Document
public class Cat {

  @Id
  private String id;
  private String name;
  private String color;
  private String owner;
  private int age;

  private Race race;

  public Cat() {}

  public Cat(String name, String color) {
    this.name = name;
    this.color = color;
  }

  public Cat(String name, String color, Race race) {
    this.name = name;
    this.color = color;
    this.race = race;
  }

  public int getAge() {
    return age;
  }

  public void setAge(int age) {
    this.age = age;
  }

  public Race getRace() {
    return race;
  }

  public void setRace(Race race) {
    this.race = race;
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
            ", race='" + race + '\'' +
            '}';
  }
}
