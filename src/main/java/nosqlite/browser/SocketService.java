package nosqlite.browser;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.common.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

public class SocketService extends WebSocketAdapter {
  private final CountDownLatch closureLatch = new CountDownLatch(1);
  private List<Session> clients = new CopyOnWriteArrayList<>();
  private static Map<String, List<Session>> clientsMap = new ConcurrentHashMap<>();
  
  @Override
  public void onWebSocketConnect(Session sess) {
    super.onWebSocketConnect(sess);
//    System.out.println("Socket Connected: " + sess);
    
    String uri = ((WebSocketSession)sess).getCoreSession().getRequestURI().toString();
//    System.out.println("Socket address: " + uri);
    
    String[] params = uri.split("events/");
    
    if(params.length > 1) {
      String coll = params[1];
      clientsMap.putIfAbsent(coll, new CopyOnWriteArrayList<>());
      clientsMap.get(coll).add(sess);
    } else {
      clients.add(sess);
    }
  
  }
  
  @Override
  public void onWebSocketText(String message) {
    super.onWebSocketText(message);
    System.out.println("Received TEXT message: " + message);
    
    broadcast(message);
    broadcast("users", message);
    
//    if (message.toLowerCase(Locale.US).contains("bye")) {
//      getSession().close(StatusCode.NORMAL, "Thanks");
//    }
  }
  
  @Override
  public void onWebSocketClose(int statusCode, String reason) {
    super.onWebSocketClose(statusCode, reason);
//    System.out.println("Socket Closed: [" + statusCode + "] " + reason);
    closureLatch.countDown();
  }
  
  @Override
  public void onWebSocketError(Throwable cause) {
    super.onWebSocketError(cause);
    cause.printStackTrace(System.err);
  }
  
  public void awaitClosure() throws InterruptedException {
    System.out.println("Awaiting closure from remote");
    closureLatch.await();
  }
  
  public void broadcast(String coll, String message) {
    if(clientsMap.containsKey(coll)) {
      broadcast(clientsMap.get(coll), message);
    }
  }
  
  public void broadcast(String message) {
    broadcast(clients, message);
  }
  
  private void broadcast(List<Session> clients, String message) {
    clients.forEach(client -> {
      try {
        if(!client.isOpen()) {
          clients.remove(client);
          String uri = ((WebSocketSession)client).getCoreSession().getRequestURI().toString();
          String[] params = uri.split("events/");
          if(params.length > 1) {
            clientsMap.get(params[1]).remove(client);
          }
        } else {
          client.getRemote().sendString(message);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    });
  }
}