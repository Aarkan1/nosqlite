package nosqlite.handlers;

/**
 * @author Johan Wirén
 */
@FunctionalInterface
public interface FindOptionsHandler {
  void handle(FindOptions options);
}
