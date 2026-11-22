package finance.cli;

import finance.core.Accounts;
import finance.core.Alerts;
import finance.core.Reports;
import finance.core.Rules;
import finance.core.SnapshotStore;
import finance.core.Transaction;
import finance.core.Wallet;
import finance.core.WalletRepository;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class FinanceCli {
  private static final String HELP =
      """
            Команды (параметры разделяются |):
              register ЛОГИН                        создать аккаунт, пароль на следующей строке
              login ЛОГИН                           войти и загрузить кошелёк
              logout                                сохранить кошелёк и выйти из аккаунта
              category add | ИМЯ                     добавить категорию
              category rename | СТАРОЕ | НОВОЕ       переименовать категорию и перенести историю
              category delete | ИМЯ                  удалить пустую категорию без бюджета
              budget КАТЕГОРИЯ | СУММА               задать или изменить лимит
              unbudget КАТЕГОРИЯ                    снять лимит
              income СУММА | КАТЕГОРИЯ [| ДАТА]      добавить доход
              expense СУММА | КАТЕГОРИЯ [| ДАТА]     добавить расход
              summary                               показать итоги и бюджеты
              report КАТЕГОРИИ | С | ПО              выборка; * означает все категории или открытую границу
              history                               показать операции
              export ПУТЬ.csv                       выгрузить снимок в новый файл
              import ПУТЬ.csv | REPLACE             заменить кошелёк из снимка
              help                                  показать справку
              exit                                  сохранить кошелёк и выйти
            Пример: expense 125.50 | еда | 2026-11-15
            Пример: report еда,дом | 2026-11-01 | *""";

  private final Accounts accounts;
  private final WalletRepository wallets;
  private final SnapshotStore snapshots;
  private final Terminal terminal;
  private final Clock clock;
  private String login;
  private Wallet wallet;

  public FinanceCli(
      Accounts accounts,
      WalletRepository wallets,
      SnapshotStore snapshots,
      Terminal terminal,
      Clock clock) {
    this.accounts = accounts;
    this.wallets = wallets;
    this.snapshots = snapshots;
    this.terminal = terminal;
    this.clock = clock;
  }

  public static String help() {
    return HELP;
  }

  public int run() throws IOException {
    terminal.line("Личные финансы. Для списка команд введите help.");
    String line;
    while ((line = terminal.command(login)) != null) {
      if (line.isBlank()) {
        terminal.line("Введите команду. Список: help.");
        continue;
      }
      String[] command = line.strip().split("\\s+", 2);
      String name = command[0].toLowerCase(Locale.ROOT);
      String input = command.length == 1 ? "" : command[1];
      try {
        if (name.equals("exit")) {
          fields(input, 0, 0, "exit");
          endSession();
          terminal.line("До свидания.");
          return 0;
        }
        dispatch(name, input);
      } catch (IllegalArgumentException | IOException e) {
        terminal.line("Ошибка: " + e.getMessage());
      }
    }
    endSession();
    return 0;
  }

  private void dispatch(String name, String input) throws IOException {
    switch (name) {
      case "help" -> {
        fields(input, 0, 0, "help");
        terminal.line(HELP);
      }
      case "register", "login" -> authorize(name, input);
      case "logout" -> {
        fields(input, 0, 0, "logout");
        requireWallet();
        endSession();
        terminal.line("Кошелёк сохранён. Сеанс завершён.");
      }
      case "category" -> category(input);
      case "budget" -> budget(input);
      case "unbudget" -> {
        String[] args = fields(input, 1, 1, "unbudget КАТЕГОРИЯ");
        save(requireWallet().removeBudget(args[0]));
        terminal.line("Бюджет снят.");
      }
      case "income", "expense" -> transaction(name, input);
      case "summary" -> {
        fields(input, 0, 0, "summary");
        ReportView.print(terminal, Reports.summarize(requireWallet(), Set.of(), null, null), false);
      }
      case "report" -> report(input);
      case "history" -> {
        fields(input, 0, 0, "history");
        ReportView.history(terminal, requireWallet());
      }
      case "export" -> export(input);
      case "import" -> importSnapshot(input);
      default ->
          throw new IllegalArgumentException("Неизвестная команда «" + name + "». Список: help.");
    }
  }

  private void authorize(String command, String input) throws IOException {
    if (login != null) {
      throw new IllegalArgumentException("Сначала завершите текущий сеанс командой logout.");
    }
    String[] args = fields(input, 1, 1, command + " ЛОГИН");
    String requested = Rules.user(args[0]);
    char[] password = terminal.password();
    if (password == null) {
      throw new IllegalArgumentException("Ввод завершился до получения пароля.");
    }
    try {
      if (command.equals("register")) {
        terminal.line(
            "Аккаунт " + accounts.register(requested, password) + " создан. Для входа: login.");
      } else {
        String verified = accounts.authenticate(requested, password);
        Wallet loaded = wallets.load(verified);
        login = verified;
        wallet = loaded;
        terminal.line("Вход выполнен: " + login + ".");
      }
    } finally {
      Arrays.fill(password, '\0');
    }
  }

  private void category(String input) throws IOException {
    Wallet current = requireWallet();
    String[] args = fields(input, 2, 3, "category add|rename|delete | ИМЯ [| НОВОЕ]");
    String action = args[0].toLowerCase(Locale.ROOT);
    if (args.length != (action.equals("rename") ? 3 : 2)) {
      throw new IllegalArgumentException(
          "Для rename нужны старое и новое имена, для add/delete — одно.");
    }
    Wallet next =
        switch (action) {
          case "add" -> current.addCategory(args[1]);
          case "rename" -> current.renameCategory(args[1], args[2]);
          case "delete" -> current.removeCategory(args[1]);
          default ->
              throw new IllegalArgumentException("Действия с категорией: add, rename, delete.");
        };
    save(next);
    terminal.line("Категории обновлены.");
  }

  private void budget(String input) throws IOException {
    String[] args = fields(input, 2, 2, "budget КАТЕГОРИЯ | СУММА");
    save(requireWallet().setBudget(args[0], Rules.amount(args[1])));
    terminal.line("Бюджет сохранён.");
    notify(Alerts.forBudget(wallet, args[0]));
  }

  private void transaction(String name, String input) throws IOException {
    String[] args = fields(input, 2, 3, name + " СУММА | КАТЕГОРИЯ [| ДАТА]");
    Wallet current = requireWallet();
    var kind = Transaction.Kind.valueOf(name.toUpperCase(Locale.ROOT));
    LocalDate date = args.length == 3 ? Rules.date(args[2]) : LocalDate.now(clock);
    var tx = new Transaction(kind, Rules.amount(args[0]), current.requireCategory(args[1]), date);
    save(current.add(tx));
    terminal.line("Операция сохранена.");
    notify(Alerts.afterTransaction(wallet, tx));
  }

  private void report(String input) {
    Wallet current = requireWallet();
    String[] args = fields(input, 3, 3, "report КАТЕГОРИИ | С | ПО");
    Set<String> categories = new TreeSet<>();
    if (!args[0].equals("*")) {
      for (String category : args[0].split(",", -1)) {
        categories.add(current.requireCategory(category));
      }
    }
    LocalDate from = args[1].equals("*") ? null : Rules.date(args[1]);
    LocalDate to = args[2].equals("*") ? null : Rules.date(args[2]);
    ReportView.print(terminal, Reports.summarize(current, categories, from, to), true);
  }

  private void export(String input) throws IOException {
    Wallet current = requireWallet();
    String[] args = fields(input, 1, 1, "export ПУТЬ.csv");
    try {
      snapshots.export(Path.of(args[0]), current);
    } catch (FileAlreadyExistsException e) {
      throw new IOException("Файл уже существует. Укажите новый путь.", e);
    }
    terminal.line("Снимок выгружен.");
  }

  private void importSnapshot(String input) throws IOException {
    requireWallet();
    String[] args = fields(input, 2, 2, "import ПУТЬ.csv | REPLACE");
    if (!args[1].equals("REPLACE")) {
      throw new IllegalArgumentException("Для замены кошелька укажите подтверждение REPLACE.");
    }
    Wallet imported = snapshots.read(Path.of(args[0]));
    save(imported);
    terminal.line("Кошелёк заменён данными из снимка.");
    notify(Alerts.forWallet(wallet));
  }

  private void notify(List<String> messages) {
    messages.forEach(message -> terminal.line("Оповещение: " + message));
  }

  private Wallet requireWallet() {
    if (wallet == null) {
      throw new IllegalArgumentException("Сначала войдите в аккаунт командой login.");
    }
    return wallet;
  }

  private void save(Wallet next) throws IOException {
    wallets.save(login, next);
    wallet = next;
  }

  private void endSession() throws IOException {
    if (login != null) {
      wallets.save(login, wallet);
      wallet = null;
      login = null;
    }
  }

  private static String[] fields(String input, int minimum, int maximum, String syntax) {
    String[] args = input.isBlank() ? new String[0] : input.split("\\|", -1);
    if (args.length < minimum || args.length > maximum) {
      throw new IllegalArgumentException("Формат: " + syntax);
    }
    for (int i = 0; i < args.length; i++) {
      args[i] = args[i].strip();
      if (args[i].isEmpty()) {
        throw new IllegalArgumentException("Пустой параметр. Формат: " + syntax);
      }
    }
    return args;
  }
}
