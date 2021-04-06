package nosqlite.handlers;

import java.util.List;

/**
 * @author Johan Wirén
 */
public class WatchData<T> {
  public String model;
  public String event;
  public List<T> data;

  public WatchData() {
  }

  public WatchData(String model, String event, List<T> data) {
    this.model = model;
    this.event = event;
    this.data = data;
  }
  
  @Override
  public String toString() {
    return "WatchData {" +
        "\n  model='" + model + '\'' +
        "\n  event='" + event + '\'' +
        "\n  data=" + data +
        "\n}";
  }
}
