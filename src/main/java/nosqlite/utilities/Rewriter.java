package nosqlite.utilities;

import java.util.regex.*;

public abstract class Rewriter {
  private Pattern pattern;
  private Matcher matcher;

  /**
   * Constructs a rewriter using the given regular expression; the syntax is
   * the same as for 'Pattern.compile'.
   */
  public Rewriter(String regex) {
    this.pattern = Pattern.compile(regex);
  }

  /**
   * Returns the input subsequence captured by the given group during the
   * previous match operation.
   */
  public String group(int i) {
    return matcher.group(i);
  }

  /**
   * Overridden to compute a replacement for each match. Use the method
   * 'group' to access the captured groups.
   */
  public abstract String replacement();

  /**
   * Returns the result of rewriting 'original' by invoking the method
   * 'replacement' for each match of the regular expression supplied to the
   * constructor.
   */
  public String rewrite(CharSequence original) {
    this.matcher = pattern.matcher(original);
    StringBuffer result = new StringBuffer(original.length());
    while (matcher.find()) {
      matcher.appendReplacement(result, "");
      result.append(replacement());
    }
    matcher.appendTail(result);
    return result.toString();
  }
}
