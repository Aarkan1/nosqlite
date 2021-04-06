package nosqlite.handlers;

/**
 * @author Johan Wirén
 */
@FunctionalInterface
public interface CollectionConfigHandler {
  void handle(CollectionConfig config);
}
