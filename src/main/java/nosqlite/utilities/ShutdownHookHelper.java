package nosqlite.utilities;

public class ShutdownHookHelper {
  public static void setShutdownHook(final Runnable r) {
    Runtime.getRuntime().addShutdownHook(new Thread(r));
  }
}