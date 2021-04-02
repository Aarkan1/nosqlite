package nosqlite.handlers;

@FunctionalInterface
public interface WatchHandler {
  void handle(WatchData watchData);
}
