package nosqlite.utilities;

import java.util.Arrays;
import java.util.List;

public abstract class Filter {
  public static String eq(String field, Object value) {
    return field + "=" + value;
  }

  public static String ne(String field, Object value) {
    return field + "!=" + value;
  }

  public static String gt(String field, Object value) {
    return field + ">" + value;
  }

  public static String gte(String field, Object value) {
    return field + ">=" + value;
  }

  public static String lt(String field, Object value) {
    return field + "<" + value;
  }

  public static String lte(String field, Object value) {
    return field + "<=" + value;
  }

  public static String text(String field, Object value) {
    return field + "=~" + value;
  }

  public static String regex(String field, String regex) { return field + "~~" + regex; }
  
  public static String not(String value) { return "!(" + value + ")"; }

  public static String in(String field, Object... values) {
    if(values[0] instanceof List) {
      return field + "==" + values[0];
    }
    return field + "==" + Arrays.asList(values);
  }

  public static String and(String... filters) {
    for(int i = 0; i < filters.length; i++) {
      if(filters[i].contains("&&") || filters[i].contains("||")) {
        filters[i] = "(" + filters[i] + ")";
      }
    }
    return String.join("&&", filters);
  }

  public static String or(String... filters) {
    for (int i = 0; i < filters.length; i++) {
      if (filters[i].contains("&&") || filters[i].contains("||")) {
        filters[i] = "(" + filters[i] + ")";
      }
    }
    return String.join("||", filters);
  }

}
