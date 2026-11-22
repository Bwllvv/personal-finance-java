package finance.infra;

import finance.core.Rules;
import finance.core.Wallet;
import finance.core.WalletRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public final class FileWallets implements WalletRepository {
  private final Path directory;
  private final CsvSnapshots snapshots = new CsvSnapshots();

  public FileWallets(Path root) throws IOException {
    directory = Files.createDirectories(root.resolve("wallets"));
  }

  @Override
  public Wallet load(String login) throws IOException {
    try {
      return snapshots.read(file(login));
    } catch (NoSuchFileException absent) {
      return Wallet.empty();
    }
  }

  @Override
  public void save(String login, Wallet wallet) throws IOException {
    AtomicFiles.write(file(login), CsvSnapshots.encode(wallet));
  }

  private Path file(String login) {
    return directory.resolve(Rules.user(login) + ".csv");
  }
}
