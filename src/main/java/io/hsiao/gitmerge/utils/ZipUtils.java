package io.hsiao.gitmerge.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ZipUtils {
  private ZipUtils() {};

  public static void pack(final File source, final File dest, final boolean zipEmpty) throws IOException {
    if (source == null) {
      throw new NullPointerException("argument 'source' is null");
    }

    if (dest == null) {
      throw new NullPointerException("argument 'dest' is null");
    }

    if (FileUtils.isEmptyDirectory(source) && !zipEmpty) {
      return;
    }

    try (final FileOutputStream fos = new FileOutputStream(dest);
      final ZipOutputStream zos = new ZipOutputStream(fos)) {

      Files.walkFileTree(source.toPath(), EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            final Path relative = source.toPath().toAbsolutePath().relativize(file.toAbsolutePath());
            zos.putNextEntry(new ZipEntry(relative.toString()));

            final byte[] buffer = new byte[1024];
            int len;
            try (final FileInputStream fis = new FileInputStream(file.toFile())) {
              while ((len = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
              }
            }

            zos.closeEntry();

            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (exc != null) {
              throw exc;
            }

            final Path relative = source.toPath().toAbsolutePath().relativize(dir.toAbsolutePath());
            if (!relative.toString().isEmpty()) {
              zos.putNextEntry(new ZipEntry(relative.toString() + "/"));
              zos.closeEntry();
            }

            return FileVisitResult.CONTINUE;
          }
      });
    }
  }
}
