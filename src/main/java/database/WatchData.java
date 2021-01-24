package database;

import java.util.List;

public class WatchData {
  private String entity;
  private String event;
  private List data;

  public WatchData() {
  }

  public WatchData(String entity, String event, List data) {
    this.entity = entity;
    this.event = event;
    this.data = data;
  }

  public String getEntity() {
    return entity;
  }

  public void setEntity(String entity) {
    this.entity = entity;
  }

  public String getEvent() {
    return event;
  }

  public void setEvent(String event) {
    this.event = event;
  }

  public List getData() {
    return data;
  }

  public void setData(List data) {
    this.data = data;
  }
}
