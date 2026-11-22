package finance;

import finance.cli.FinanceCli;
import finance.cli.Terminal;
import finance.core.Accounts;
import finance.infra.CsvSnapshots;
import finance.infra.DirectoryLock;
import finance.infra.FileAccounts;
import finance.infra.FileWallets;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;

public final class Main {
  private Main() {}

  public static void main(String[] args) {
    System.exit(run(args));
  }

  private static int run(String[] args) {
    if (args.length == 1 && args[0].equals("--help")) {
      System.out.println("Запуск: java -jar target/personal-finance.jar [--data-dir КАТАЛОГ]");
      System.out.println(FinanceCli.help());
      return 0;
    }
    if (args.length != 0
        && (args.length != 2 || !args[0].equals("--data-dir") || args[1].isBlank())) {
      System.err.println("Используйте --help или --data-dir КАТАЛОГ.");
      return 2;
    }
    try {
      return start(Path.of(args.length == 0 ? ".finance-data" : args[1]));
    } catch (InvalidPathException e) {
      System.err.println("Некорректный каталог данных.");
      return 2;
    } catch (IOException e) {
      System.err.println("Ошибка ввода-вывода: " + e.getMessage());
      return 1;
    }
  }

  @SuppressWarnings("try")
  private static int start(Path directory) throws IOException {
    try (var lock = DirectoryLock.acquire(directory)) {
      var console = System.console();
      var terminal =
          console == null
              ? new Terminal(
                  new InputStreamReader(System.in, StandardCharsets.UTF_8),
                  new PrintWriter(System.out, true, StandardCharsets.UTF_8))
              : new Terminal(console);
      return new FinanceCli(
              new Accounts(new FileAccounts(directory)),
              new FileWallets(directory),
              new CsvSnapshots(),
              terminal,
              Clock.systemDefaultZone())
          .run();
    }
  }
}
