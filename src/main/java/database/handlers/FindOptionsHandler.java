package database.handlers;

import database.handlers.FindOptions;

@FunctionalInterface
public interface FindOptionsHandler {
  void handle(FindOptions options);
}
