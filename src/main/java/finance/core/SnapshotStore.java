package finance.core;

import java.io.IOException;
import java.nio.file.Path;

public interface SnapshotStore {
  Wallet read(Path file) throws IOException;

  void export(Path file, Wallet wallet) throws IOException;
}
