package finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import finance.core.Accounts;
import finance.core.Rules;
import finance.core.Transaction;
import finance.core.Wallet;
import finance.infra.CsvSnapshots;
import finance.infra.DirectoryLock;
import finance.infra.FileAccounts;
import finance.infra.FileWallets;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageTest {
  @TempDir Path directory;

  private static Wallet sample() {
    return Wallet.empty()
        .addCategory("Ремонт")
        .setBudget("ремонт", Rules.amount("900.10"))
        .add(
            new Transaction(
                Transaction.Kind.EXPENSE,
                Rules.amount("41.50"),
                "ремонт",
                Rules.date("2026-11-22")));
  }

  @Test
  void persistsSeparateWalletsAcrossInstances() throws IOException {
    var repo = new FileWallets(directory);
    repo.save("alice", sample());
    repo.save("bob", Wallet.empty().addCategory("Дом"));
    var reopened = new FileWallets(directory);
    assertEquals(sample(), reopened.load("alice"));
    assertEquals(Wallet.empty().addCategory("Дом"), reopened.load("bob"));
  }

  @Test
  void loadsAbsentWalletAsEmpty() throws IOException {
    assertEquals(Wallet.empty(), new FileWallets(directory).load("new-user"));
  }

  @Test
  void rejectsDamagedPersistedWallet() throws IOException {
    var repo = new FileWallets(directory);
    Files.writeString(directory.resolve("wallets/alice.csv"), "damaged");
    assertThrows(IOException.class, () -> repo.load("alice"));
  }

  @Test
  void exportsAndImportsCompleteSnapshot() throws IOException {
    var snapshots = new CsvSnapshots();
    var file = directory.resolve("backup.csv");
    snapshots.export(file, sample());
    assertEquals(sample(), snapshots.read(file));
    assertTrue(Files.readString(file).contains("EXPENSE,ремонт,41.50,2026-11-22"));
  }

  @Test
  void doesNotOverwriteExistingExport() throws IOException {
    var snapshots = new CsvSnapshots();
    var file = directory.resolve("existing.csv");
    Files.writeString(file, "existing");
    assertThrows(FileAlreadyExistsException.class, () -> snapshots.export(file, sample()));
    assertEquals("existing", Files.readString(file));
  }

  @Test
  void rejectsDanglingAndDuplicateCsvRows() throws IOException {
    var snapshots = new CsvSnapshots();
    var file = directory.resolve("invalid.csv");
    String headers = "finance-wallet,1,,\ntype,category,amount,date\n";
    Files.writeString(file, headers + "EXPENSE,unknown,25.00,2026-11-22\n");
    assertThrows(IOException.class, () -> snapshots.read(file));
    Files.writeString(file, headers + "CATEGORY,дом,,\nCATEGORY,ДОМ,,\n");
    var failure = assertThrows(IOException.class, () -> snapshots.read(file));
    assertTrue(failure.getMessage().contains("строка 4"));
  }

  @Test
  void authenticatesUsersAndUsesDifferentSaltsForSamePassword() throws IOException {
    var files = new FileAccounts(directory);
    var accounts = new Accounts(files);
    var password = "example123".toCharArray();
    accounts.register("Alice", password);
    accounts.register("Bob", password);
    assertEquals(
        "alice", new Accounts(new FileAccounts(directory)).authenticate("ALICE", password));
    assertEquals("bob", accounts.authenticate("bob", password));
    assertNotEquals(files.find("alice").orElseThrow(), files.find("bob").orElseThrow());
    assertFalse(
        Files.readString(directory.resolve("accounts/alice.account")).contains("example123"));
    assertThrows(IllegalArgumentException.class, () -> accounts.register("alice", password));
    assertThrows(
        IllegalArgumentException.class,
        () -> accounts.authenticate("alice", "incorrect".toCharArray()));
    assertThrows(IllegalArgumentException.class, () -> accounts.authenticate("unknown", password));
  }

  @Test
  void validatesCredentials() throws IOException {
    var accounts = new Accounts(new FileAccounts(directory));
    assertThrows(
        IllegalArgumentException.class,
        () -> accounts.register("../alice", "example123".toCharArray()));
    assertThrows(
        IllegalArgumentException.class, () -> accounts.register("alice", "123".toCharArray()));
    assertThrows(
        IllegalArgumentException.class, () -> accounts.register("alice", "        ".toCharArray()));
  }

  @Test
  @SuppressWarnings("try")
  void preventsConcurrentAccessToSameDataDirectory() throws IOException {
    try (var first = DirectoryLock.acquire(directory)) {
      assertThrows(IOException.class, () -> DirectoryLock.acquire(directory));
    }
    try (var second = DirectoryLock.acquire(directory)) {
      assertTrue(Files.exists(directory.resolve(".lock")));
    }
  }
}
