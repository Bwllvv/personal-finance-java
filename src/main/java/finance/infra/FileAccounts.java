package finance.infra;

import finance.core.AccountRepository;
import finance.core.Rules;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

public final class FileAccounts implements AccountRepository {
  private static final String VERSION = "pbkdf2-sha256:600000";
  private final Path directory;

  public FileAccounts(Path root) throws IOException {
    directory = Files.createDirectories(root.resolve("accounts"));
  }

  @Override
  public Optional<Credential> find(String login) throws IOException {
    Path file = file(login);
    try {
      var lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      if (lines.size() != 3 || !lines.get(0).equals(VERSION)) {
        throw new IOException("Повреждена запись учётной записи.");
      }
      return Optional.of(new Credential(lines.get(1), lines.get(2)));
    } catch (NoSuchFileException absent) {
      return Optional.empty();
    }
  }

  @Override
  public boolean create(String login, Credential credential) throws IOException {
    Path path = file(login);
    if (Files.exists(path)) {
      return false;
    }
    AtomicFiles.write(path, VERSION + "\n" + credential.salt() + "\n" + credential.hash() + "\n");
    return true;
  }

  private Path file(String login) {
    return directory.resolve(Rules.user(login) + ".account");
  }
}
