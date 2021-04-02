package nosqlite.handlers;

@FunctionalInterface
public interface FindOptionsHandler {
  void handle(FindOptions options);
}
