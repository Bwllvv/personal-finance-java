package finance.core;

import java.io.IOException;
import java.util.Optional;

public interface AccountRepository {
  record Credential(String salt, String hash) {}

  Optional<Credential> find(String login) throws IOException;

  boolean create(String login, Credential credential) throws IOException;
}
