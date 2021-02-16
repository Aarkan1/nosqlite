package database.handlers;

@FunctionalInterface
public interface DeleteOptionsHandler {
  void handle(DeleteOptions options);
}
