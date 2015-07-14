package io.hsiao.gitmerge.utils;

public final class StringUtils {
  private StringUtils() {};

  public static String repeat(final String str, final int repeat) {
    if (str == null) {
      return null;
    }

    if (str.isEmpty() || repeat <= 0) {
      return "";
    }

    final StringBuilder sb = new StringBuilder(str.length() * repeat);

    for (int idx = 0; idx < repeat; ++idx) {
      sb.append(str);
    }

    return sb.toString();
  }

  public static String prettyFormat(final String symbol, final int repeat, final String... messages) {
    if (symbol == null) {
      throw new NullPointerException("argument 'symbol' is null");
    }

    if ((messages == null) || (messages.length == 0)) {
      return "";
    }

    final StringBuilder sb = new StringBuilder();
    final String banner = repeat(symbol, repeat);

    sb.append(banner).append("\n");
    final int margin = (banner.length() - messages[0].length()) / 2;
    sb.append(repeat(" ", margin)).append(messages[0]).append("\n");
    sb.append(banner).append("\n");

    for (int idx = 1; idx < messages.length; ++idx) {
      sb.append(messages[idx]).append("\n");
      sb.append(banner).append("\n");
    }

    return sb.toString();
  }
}
