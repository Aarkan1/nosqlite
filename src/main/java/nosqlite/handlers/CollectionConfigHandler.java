package nosqlite.handlers;

@FunctionalInterface
public interface CollectionConfigHandler {
  void handle(CollectionConfig config);
}
