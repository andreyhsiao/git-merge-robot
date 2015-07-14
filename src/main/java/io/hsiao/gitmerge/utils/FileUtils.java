package io.hsiao.gitmerge.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.Set;

public final class FileUtils {
  private FileUtils() {}

  public static boolean isEmptyDirectory(final File dir) throws IOException {
    if (dir == null) {
      throw new NullPointerException("argument 'dir' is null");
    }

    try (final DirectoryStream<Path> entries = Files.newDirectoryStream(dir.toPath())) {
      return !entries.iterator().hasNext();
    }
  }

  public static File mkdir(final File dir) throws IOException {
    if (dir == null) {
      throw new NullPointerException("argument 'dir' is null");
    }

    return Files.createDirectories(dir.toPath()).toFile();
  }

  public static void rmdir(final File dir, final boolean followLinks) throws IOException {
    if (dir == null) {
      throw new NullPointerException("argument 'dir' is null");
    }

    final boolean exists;
    final Set<FileVisitOption> options;
    if (followLinks) {
      exists = Files.isDirectory(dir.toPath());
      options = EnumSet.of(FileVisitOption.FOLLOW_LINKS);
    }
    else {
      exists = Files.isDirectory(dir.toPath(), LinkOption.NOFOLLOW_LINKS);
      options = EnumSet.noneOf(FileVisitOption.class);
    }

    if (!exists) {
      return;
    }

    Files.walkFileTree(dir.toPath(), options, Integer.MAX_VALUE,
        new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  public static InputStream loadFileAsStream(final String name) throws IOException {
    if (name == null) {
      throw new NullPointerException("argument 'name' is null");
    }

    final File file = new File(name);
    if (file.isFile()) {
      return new FileInputStream(file);
    }

    final InputStream ins = new Object(){}.getClass().getEnclosingClass().getResourceAsStream(name);
    if (ins != null) {
      return ins;
    }

    throw new RuntimeException("failed to load file (not found on both filesystem and classpath) [" + name + "]");
  }
}
