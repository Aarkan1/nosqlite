package nosqlite.handlers;

/**
 * @author Johan Wirén
 */
@FunctionalInterface
public interface DeleteOptionsHandler {
  void handle(DeleteOptions options);
}
