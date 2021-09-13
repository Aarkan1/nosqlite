package nosqlite.browser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.httprpc.Content;
import org.httprpc.RequestMethod;
import org.httprpc.ResourcePath;
import org.httprpc.WebService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static nosqlite.Database.collection;

public class RestService extends WebService {
  Map<String, Class<?>> collNames;
  Map<String, String> idFields;
  
  public RestService(Map<String, Class<?>> collNames, Map<String, String> idFields) throws IOException {
    super();
    this.collNames = collNames;
    this.idFields = idFields;
  }
  
  @RequestMethod("GET")
  @ResourcePath("/docs")
  public String getDocs() {
    return BrowserDocumentation.docs;
  }
  
  @RequestMethod("GET")
  @ResourcePath("/coll-names")
  public String getCollNames() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.writeValueAsString(collNames.keySet());
  }
  
  @RequestMethod("GET")
  @ResourcePath("/coll/?:coll")
  @Content(Map.class)
  public Map getColl() {
    String coll = getKey("coll");
    Map data = new HashMap();
    data.put(idFields.get(coll), collection(coll).findAsJson());
    return data;
  }
  
  @RequestMethod("DELETE")
  @ResourcePath("/coll/?:coll/?:id")
  public String deleteDocument() {
    String coll = getKey("coll");
    String id = getKey("id");
    collection(coll).deleteById(id);
    return "OK";
  }
  
  @RequestMethod("DELETE")
  @ResourcePath("/drop-collection/?:coll")
  public String deleteCollection() {
    String coll = getKey("coll");
    collection(coll).delete();
    return "OK";
  }
  
  @RequestMethod("GET")
  @ResourcePath("/export-collection/?:coll")
  public String exportCollection() throws IOException {
    String coll = getKey("coll");
    List models = collection(coll).find();
    String path = Paths.get("db/" + coll + ".json").toString();
    File file = new File(path);
    file.getParentFile().mkdirs();   // create parent dirs if necessary
    if (file.exists()) file.delete(); // replace existing file
    new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(new File(path), models);
    return "OK";
  }
}
