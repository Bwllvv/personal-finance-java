package finance.core;

import java.io.IOException;

public interface WalletRepository {
  Wallet load(String login) throws IOException;

  void save(String login, Wallet wallet) throws IOException;
}
