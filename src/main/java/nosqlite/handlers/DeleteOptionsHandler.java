package nosqlite.handlers;

@FunctionalInterface
public interface DeleteOptionsHandler {
  void handle(DeleteOptions options);
}
