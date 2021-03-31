package test_entities;

public class TestRace {
  private String type;
  private int time;

  public TestRace() {
  }

  public TestRace(String type) {
    this.type = type;
  }
  
  public TestRace(String type, int time) {
    this.type = type;
    this.time = time;
  }
  
  public int getTime() {
    return time;
  }

  public void setTime(int time) {
    this.time = time;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  @Override
  public String toString() {
    return "Race{" +
            "type='" + type + '\'' +
            ", time=" + time +
            '}';
  }
}
