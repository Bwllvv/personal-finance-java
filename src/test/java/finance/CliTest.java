package finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import finance.cli.FinanceCli;
import finance.cli.Terminal;
import finance.core.Accounts;
import finance.core.Wallet;
import finance.core.WalletRepository;
import finance.infra.CsvSnapshots;
import finance.infra.FileAccounts;
import finance.infra.FileWallets;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-22T12:00:00Z"), ZoneOffset.UTC);
  private static final String ALICE = "register alice\nsecret-alice\nlogin alice\nsecret-alice\n";

  @TempDir Path directory;

  private String run(String input) throws IOException {
    return run(input, new FileWallets(directory));
  }

  private String run(String input, WalletRepository repository) throws IOException {
    var output = new StringWriter();
    var cli =
        new FinanceCli(
            new Accounts(new FileAccounts(directory)),
            repository,
            new CsvSnapshots(),
            new Terminal(new StringReader(input), new PrintWriter(output)),
            CLOCK);
    assertEquals(0, cli.run());
    return output.toString();
  }

  @Test
  void supportsHelpAndKeepsReadingAfterInvalidCommands() throws IOException {
    var result = run("  \nwrong\nsummary\nhelp\nexit typo\nexit\nsummary\n");
    assertTrue(result.contains("Введите команду"));
    assertTrue(result.contains("Неизвестная команда"));
    assertTrue(result.contains("Сначала войдите"));
    assertTrue(result.contains("Пример: report"));
    assertTrue(result.contains("Формат: exit"));
    assertTrue(result.endsWith("До свидания." + System.lineSeparator()));
  }

  @Test
  void handlesWrongPasswordAndRequiresLogoutForSwitching() throws IOException {
    var result =
        run(
            """
                register alice
                secret-alice
                login alice
                wrong-pass
                login alice
                secret-alice
                login bob
                logout
                exit
                """);
    assertTrue(result.contains("Неверный логин или пароль"));
    assertTrue(result.contains("Вход выполнен: alice"));
    assertTrue(result.contains("Сначала завершите текущий сеанс"));
    assertTrue(result.contains("Кошелёк сохранён"));
  }

  @Test
  void savesAndReloadsDataOnExitAndEof() throws IOException {
    var result =
        run(
            ALICE
                + """
                category add | зарплата
                income 1200.50 | зарплата
                exit
                """);
    assertTrue(result.contains("Операция сохранена"));
    var next =
        run(
            """
                login alice
                secret-alice
                history
                summary
                expense 50.50 | зарплата
                """);
    assertTrue(next.contains("2026-11-22"));
    assertTrue(next.contains("баланс 1200.50"));
    assertTrue(run("login alice\nsecret-alice\nsummary\nexit\n").contains("баланс 1150.00"));
  }

  @Test
  void isolatesUsersAfterLogoutAndReauthentication() throws IOException {
    var result =
        run(
            ALICE
                + """
                category add | personal
                income 50 | personal
                logout
                register bob
                secret-bob
                login bob
                secret-bob
                summary
                expense 1 | personal
                logout
                login alice
                secret-alice
                summary
                exit
                """);
    assertTrue(result.contains("баланс 0.00"));
    assertTrue(result.contains("Неизвестная категория: personal"));
    assertTrue(result.contains("баланс 50.00"));
  }

  @Test
  void validatesCommandsWithoutChangingHistory() throws IOException {
    var result =
        run(
            ALICE
                + """
                category add | Еда
                income 5 | Еда | 2026-02-30
                expense 0 | Еда
                expense 1 |
                expense ten | Еда
                expense 1 | транспорт
                report Еда, | * | *
                history
                exit
                """);
    for (String message :
        new String[] {
          "существующей датой",
          "больше нуля",
          "Пустой параметр",
          "до двух после",
          "Неизвестная категория",
          "Нет операций."
        }) {
      assertTrue(result.contains(message), message);
    }
    assertTrue(new FileWallets(directory).load("alice").transactions().isEmpty());
  }

  @Test
  void handlesCategoryAndBudgetEditsInCli() throws IOException {
    var result =
        run(
            ALICE
                + """
                category add | еда
                budget еда | 100
                expense 80 | еда
                category rename | еда | продукты
                budget продукты | 70
                summary
                unbudget продукты
                category delete | продукты
                category add | книги
                category delete | книги
                exit
                """);
    for (String message :
        new String[] {
          "80%",
          "Перерасход кошелька",
          "превышен на 10.00",
          "продукты",
          "-10.00",
          "Бюджет снят",
          "только категорию без операций"
        }) {
      assertTrue(result.contains(message), message);
    }
    var wallet = new FileWallets(directory).load("alice");
    assertTrue(wallet.budgets().isEmpty());
    assertEquals("продукты", wallet.transactions().get(0).category());
    assertEquals(1, wallet.categories().size());
  }

  @Test
  void filtersReportsByPeriodAndCategory() throws IOException {
    var result =
        run(
            ALICE
                + """
                category add | еда
                category add | дом
                expense 15 | еда | 2026-11-01
                expense 25 | дом | 2026-11-02
                expense 30 | дом | 2026-12-01
                report еда,дом | 2026-11-01 | 2026-11-02
                report еда | 2027-01-01 | *
                report * | 2026-12-01 | 2026-11-01
                exit
                """);
    assertTrue(
        result.contains("Итого по выборке (RUB): доходы 0.00, расходы 40.00, баланс -40.00"));
    assertTrue(result.contains("Нет операций по заданной выборке"));
    assertTrue(result.contains("Начальная дата позже конечной"));
  }

  @Test
  void rejectsUnconfirmedOrInvalidImportAndRestoresSnapshot() throws IOException {
    Path snapshot = directory.resolve("demo backup.csv");
    Path invalid = directory.resolve("invalid.csv");
    Files.writeString(invalid, "bad format");
    var result =
        run(
            ALICE
                + """
                category add | еда
                expense 40 | еда
                export %s
                expense 3 | еда
                import %s | yes
                import %s | REPLACE
                summary
                import %s | REPLACE
                summary
                exit
                """
                    .formatted(snapshot, snapshot, invalid, snapshot));
    assertTrue(result.contains("укажите подтверждение REPLACE"));
    assertTrue(result.contains("Несовместимый формат снимка"));
    assertTrue(result.contains("расходы 43.00"));
    assertTrue(result.contains("расходы 40.00"));
    assertEquals(1, new FileWallets(directory).load("alice").transactions().size());
  }

  @Test
  void saveFailureDoesNotApplyMutationToInMemoryWallet() throws IOException {
    var failed =
        new WalletRepository() {
          @Override
          public Wallet load(String login) {
            return Wallet.empty();
          }

          @Override
          public void save(String login, Wallet wallet) throws IOException {
            if (!wallet.categories().isEmpty()) {
              throw new IOException("Диск недоступен.");
            }
          }
        };
    var result = run(ALICE + "category add | еда\nexpense 1 | еда\nexit\n", failed);
    assertTrue(result.contains("Диск недоступен"));
    assertTrue(result.contains("Неизвестная категория: еда"));
    assertFalse(result.contains("Категории обновлены"));
  }
}
