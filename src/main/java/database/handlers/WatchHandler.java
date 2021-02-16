package database.handlers;

import database.handlers.WatchData;

@FunctionalInterface
public interface WatchHandler {
  void handle(WatchData watchData);
}
