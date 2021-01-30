package utilities;

public abstract class Filter {
  public static String eq(String field, Object value) {
    return field + "=" + value;
  }

  public static String not(String field, Object value) {
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