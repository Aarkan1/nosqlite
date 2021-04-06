package nosqlite.handlers;

/**
 * @author Johan Wirén
 */
@FunctionalInterface
public interface WatchHandler {
  void handle(WatchData watchData);
}
