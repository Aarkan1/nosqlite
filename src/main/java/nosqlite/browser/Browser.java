package nosqlite.browser;

import nosqlite.utilities.JettyUtil;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;

import javax.servlet.MultipartConfigElement;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import static nosqlite.Database.collection;

public class Browser {
  
  public Browser(Map<String, Class<?>> collNames, Map<String, String> idFields) {
    this(collNames, idFields, 9595); // default port
  }
  
  public Browser(Map<String, Class<?>> collNames, Map<String, String> idFields, int port) {
    Server server = new Server(port);
  
    ServletContextHandler context = new ServletContextHandler();
    context.setContextPath("/");
  
    DefaultServlet defaultServlet = new DefaultServlet();
    ServletHolder holderPwd = new ServletHolder("default", defaultServlet);
    holderPwd.setInitParameter("resourceBase", "./src/main/resources/static/");
    context.addServlet(holderPwd, "/*");
    try {
      context.addServlet(new ServletHolder(new RestService(collNames, idFields)), "/rest/*");
      ServletHolder uploadHolder = new ServletHolder(new UploadService(collNames));
      // make servlet handle multipart files
      uploadHolder.getRegistration().setMultipartConfig(new MultipartConfigElement("./tmp"));
      context.addServlet(uploadHolder, "/api/*");
    } catch (IOException e) {
      e.printStackTrace();
    }
    server.setHandler(context);
  
    JettyWebSocketServletContainerInitializer.configure(context, (servletContext, wsContainer) -> {
      // Configure default max size
      wsContainer.setMaxTextMessageSize(65535);
      
      // Set connection timeout to a year
      wsContainer.setIdleTimeout(Duration.ofDays(365L));
  
      // Add websockets
      wsContainer.addMapping("/events/*", SocketService.class);
    });
  
    // start server
    new Thread(() -> {
      JettyUtil.disableJettyLogger();
      try {
        server.start();
        server.join();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }).start();
  
    // close server gracefully on shutdown
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        server.stop();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }));
  }
}
