package finance.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class Alerts {
  private static final BigDecimal THRESHOLD = new BigDecimal("0.80");

  private Alerts() {}

  public static List<String> afterTransaction(Wallet wallet, Transaction transaction) {
    var summary = Reports.summarize(wallet, Set.of(), null, null);
    var messages = new ArrayList<String>();
    if (transaction.kind() == Transaction.Kind.EXPENSE) {
      summary.categories().stream()
          .filter(row -> row.name().equals(transaction.category()))
          .findFirst()
          .ifPresent(row -> messages.addAll(budgetMessages(row)));
    }
    messages.addAll(balanceMessages(summary.totals().balance()));
    return List.copyOf(messages);
  }

  public static List<String> forBudget(Wallet wallet, String category) {
    return budgetMessages(
        Reports.summarize(wallet, Set.of(category), null, null).categories().get(0));
  }

  public static List<String> forWallet(Wallet wallet) {
    var summary = Reports.summarize(wallet, Set.of(), null, null);
    var messages = new ArrayList<>(balanceMessages(summary.totals().balance()));
    summary.categories().forEach(row -> messages.addAll(budgetMessages(row)));
    return List.copyOf(messages);
  }

  private static List<String> balanceMessages(BigDecimal balance) {
    if (balance.signum() < 0) {
      return List.of("Перерасход кошелька: баланс " + balance.toPlainString() + " RUB.");
    }
    if (balance.signum() == 0) {
      return List.of("Баланс кошелька равен нулю.");
    }
    return List.of();
  }

  private static List<String> budgetMessages(Reports.Category row) {
    if (row.limit() == null) {
      return List.of();
    }
    BigDecimal remaining = row.remaining();
    if (remaining.signum() < 0) {
      return List.of(
          "Бюджет «"
              + row.name()
              + "» превышен на "
              + remaining.negate().toPlainString()
              + " RUB.");
    }
    if (remaining.signum() == 0) {
      return List.of("Бюджет «" + row.name() + "» исчерпан.");
    }
    if (row.allSpent().compareTo(row.limit().multiply(THRESHOLD)) >= 0) {
      return List.of(
          "Израсходовано не менее 80% бюджета «"
              + row.name()
              + "». Остаток "
              + remaining.toPlainString()
              + " RUB.");
    }
    return List.of();
  }
}
