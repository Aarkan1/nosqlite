package database;

@FunctionalInterface
public interface WatchHandler {
  void handle(WatchData watchData);
}
