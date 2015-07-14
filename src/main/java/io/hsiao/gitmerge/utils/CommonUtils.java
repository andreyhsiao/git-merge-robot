package io.hsiao.gitmerge.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class CommonUtils {
  private CommonUtils() {}

  public static Properties loadProperties(final String name) throws IOException {
    if (name == null) {
      throw new NullPointerException("argument 'name' is null");
    }

    try (final InputStream ins = FileUtils.loadFileAsStream(name)) {
      final Properties props = new Properties();
      props.load(ins);
      return props;
    }
  }

  public static String getProperty(final Properties props, final String name, final boolean allowEmpty) {
    if (props == null) {
      throw new NullPointerException("argument 'props' is null");
    }

    if (name == null) {
      throw new NullPointerException("argument 'name' is null");
    }

    final String value = props.getProperty(name, "");

    if (value.isEmpty() && !allowEmpty) {
      throw new RuntimeException("failed to get property (property not found or may be empty) [" + name + "]");
    }

    return value;
  }

  public static String getSystemProperty(final String name, final boolean allowEmpty) {
    if (name == null) {
      throw new NullPointerException("argument 'name' is null");
    }

    final String value = System.getProperty(name, "");

    if (value.isEmpty() && !allowEmpty) {
      throw new RuntimeException("failed to get system property (property not found or may be empty) [" + name + "]");
    }

    return value;
  }
}
