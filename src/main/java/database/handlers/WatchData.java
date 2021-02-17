package database.handlers;

import java.util.List;

public class WatchData<T> {
  public String entity;
  public String event;
  public List<T> data;

  public WatchData() {
  }

  public WatchData(String entity, String event, List<T> data) {
    this.entity = entity;
    this.event = event;
    this.data = data;
  }
}
