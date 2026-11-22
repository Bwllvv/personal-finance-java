package finance.cli;

import finance.core.Reports.Summary;
import finance.core.Wallet;
import java.util.Locale;

final class ReportView {
  private static final String ROW = "%-40s  %14s  %14s  %14s  %14s";

  private ReportView() {}

  static void print(Terminal terminal, Summary summary, boolean filtered) {
    var totals = summary.totals();
    terminal.line(
        (filtered ? "Итого по выборке" : "Итого по кошельку")
            + " (RUB): доходы "
            + totals.income().toPlainString()
            + ", расходы "
            + totals.expenses().toPlainString()
            + ", баланс "
            + totals.balance().toPlainString());
    if (summary.operationCount() == 0) {
      terminal.line("Нет операций по заданной выборке.");
    }
    if (summary.categories().isEmpty()) {
      return;
    }
    terminal.line("Бюджеты и их остатки рассчитаны за всё время.");
    printRow(terminal, "Категория", "Доходы", "Расходы", "Бюджет", "Остаток");
    for (var row : summary.categories()) {
      printRow(
          terminal,
          row.name(),
          row.selection().income().toPlainString(),
          row.selection().expenses().toPlainString(),
          row.limit() == null ? "—" : row.limit().toPlainString(),
          row.remaining() == null ? "—" : row.remaining().toPlainString());
    }
  }

  static void history(Terminal terminal, Wallet wallet) {
    if (wallet.transactions().isEmpty()) {
      terminal.line("Нет операций.");
      return;
    }
    terminal.line("Дата        Вид       Сумма (RUB)  Категория");
    for (var tx : wallet.transactions()) {
      terminal.line(
          String.format(
              Locale.ROOT,
              "%-10s  %-8s  %11s  %s",
              tx.date(),
              tx.kind(),
              tx.amount().toPlainString(),
              tx.category()));
    }
  }

  private static void printRow(Terminal terminal, String... cells) {
    terminal.line(String.format(Locale.ROOT, ROW, (Object[]) cells));
  }
}
