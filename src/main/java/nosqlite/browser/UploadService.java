package nosqlite.browser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.httprpc.RequestMethod;
import org.httprpc.ResourcePath;
import org.httprpc.WebService;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static nosqlite.Database.collection;

public class UploadService extends WebService {
  Map<String, Class<?>> collNames;
  
  public UploadService(Map<String, Class<?>> collNames) {
    super();
    this.collNames = collNames;
  }
  
  // route to handle json mockdata imports
  @RequestMethod("POST")
  @ResourcePath("/mock/?:coll")
  public String upload(URL file) throws IOException {
    String coll = getKey("coll");
    try (InputStream inputStream = file.openStream()) {
      ObjectMapper mapper = new ObjectMapper();
      Object[] objects = mapper.readValue(inputStream, Object[].class);
      Class klass = collNames.get(coll);
      List models = new ArrayList();
      try {
        for (Object obj : objects) models.add(mapper.readValue(mapper.writeValueAsBytes(obj), klass));
      } catch (UnrecognizedPropertyException e) {
        e.printStackTrace();
        return e.getMessage();
      }
      return mapper.writeValueAsString(collection(klass).save(models));
    }
  }
}
