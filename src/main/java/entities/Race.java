package entities;

public class Race {
  private String type;
  private int time;

  public Race() {
  }

  public Race(String type) {
    this.type = type;
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
