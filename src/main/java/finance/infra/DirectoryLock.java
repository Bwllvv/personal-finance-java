package finance.infra;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class DirectoryLock implements AutoCloseable {
  private final FileChannel channel;

  private DirectoryLock(FileChannel channel) {
    this.channel = channel;
  }

  public static DirectoryLock acquire(Path root) throws IOException {
    Files.createDirectories(root);
    FileChannel channel =
        FileChannel.open(
            root.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    try {
      if (channel.tryLock() == null) {
        throw new IOException("Каталог данных уже открыт другим экземпляром программы.");
      }
      return new DirectoryLock(channel);
    } catch (OverlappingFileLockException e) {
      channel.close();
      throw new IOException("Каталог данных уже открыт другим экземпляром программы.", e);
    } catch (IOException | RuntimeException e) {
      channel.close();
      throw e;
    }
  }

  @Override
  public void close() throws IOException {
    channel.close();
  }
}
