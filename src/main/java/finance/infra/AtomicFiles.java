package finance.infra;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class AtomicFiles {
  private AtomicFiles() {}

  static void write(Path destination, String content) throws IOException {
    Path absolute = destination.toAbsolutePath();
    Path temporary = Files.createTempFile(absolute.getParent(), ".finance-", ".tmp");
    try {
      Files.writeString(temporary, content, StandardCharsets.UTF_8);
      Files.move(
          temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
