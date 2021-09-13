package nosqlite.utilities;

import org.eclipse.jetty.util.log.Logger;

public class JettyUtil {
  private static Logger defaultLogger = null;
  
  public static void disableJettyLogger() {
    System.setProperty("org.eclipse.jetty.util.log.Slf4jLog.logFile", "System.out");
    System.setProperty("org.slf4j.simpleLogger.logFile", "System.out");
    System.setProperty("org.slf4j.logFile", "System.out");
    
    if(defaultLogger == null) {
      defaultLogger = org.eclipse.jetty.util.log.Log.getLog();
    }
    org.eclipse.jetty.util.log.Log.setLog(new NoopLogger());
  }
  
  private static class NoopLogger implements Logger {
    
    @Override
    public String getName() {
      return "noop";
    }
    
    @Override
    public boolean isDebugEnabled() {
      return false;
    }
  
    @Override
    public void setDebugEnabled(boolean b) {
    
    }
    
    @Override
    public void debug(String s, Object... objects) {
    
    }
  
    @Override
    public void debug(String s, long l) {
    
    }
  
    @Override
    public void debug(Throwable throwable) {
    
    }
  
    @Override
    public void debug(String s, Throwable throwable) {
    
    }
  
    @Override
    public Logger getLogger(String s) {
      return null;
    }
  
    @Override
    public void ignore(Throwable throwable) {
    
    }
    
    @Override
    public void info(String s, Object... objects) {
    
    }
  
    @Override
    public void info(Throwable throwable) {
    
    }
  
    @Override
    public void info(String s, Throwable throwable) {
    
    }
    
    @Override
    public void warn(String s, Object... objects) {
    
    }
  
    @Override
    public void warn(Throwable throwable) {
    
    }
    
    
    @Override
    public void warn(String s, Throwable throwable) {
    
    }
  }
}
