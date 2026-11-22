package finance.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class Reports {
  public record Totals(BigDecimal income, BigDecimal expenses) {
    static Totals zero() {
      return new Totals(Rules.ZERO, Rules.ZERO);
    }

    Totals plus(Transaction tx) {
      return switch (tx.kind()) {
        case INCOME -> new Totals(income.add(tx.amount()), expenses);
        case EXPENSE -> new Totals(income, expenses.add(tx.amount()));
      };
    }

    public BigDecimal balance() {
      return income.subtract(expenses);
    }
  }

  public record Category(String name, Totals selection, BigDecimal limit, BigDecimal allSpent) {
    public BigDecimal remaining() {
      return limit == null ? null : limit.subtract(allSpent);
    }
  }

  public record Summary(Totals totals, List<Category> categories, int operationCount) {}

  private Reports() {}

  public static Summary summarize(
      Wallet wallet, Set<String> selection, LocalDate from, LocalDate to) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new IllegalArgumentException("Начальная дата позже конечной.");
    }
    Set<String> names = new TreeSet<>();
    if (selection.isEmpty()) {
      names.addAll(wallet.categories());
    } else {
      selection.forEach(category -> names.add(wallet.requireCategory(category)));
    }

    Map<String, Totals> selected = new TreeMap<>();
    Map<String, BigDecimal> spending = new TreeMap<>();
    Totals total = Totals.zero();
    int count = 0;
    for (Transaction tx : wallet.transactions()) {
      if (!names.contains(tx.category())) {
        continue;
      }
      if (tx.kind() == Transaction.Kind.EXPENSE) {
        spending.merge(tx.category(), tx.amount(), BigDecimal::add);
      }
      if ((from != null && tx.date().isBefore(from)) || (to != null && tx.date().isAfter(to))) {
        continue;
      }
      selected.compute(
          tx.category(), (key, subtotal) -> (subtotal == null ? Totals.zero() : subtotal).plus(tx));
      total = total.plus(tx);
      count++;
    }

    List<Category> categories = new ArrayList<>();
    for (String name : names) {
      categories.add(
          new Category(
              name,
              selected.getOrDefault(name, Totals.zero()),
              wallet.budgets().get(name),
              spending.getOrDefault(name, Rules.ZERO)));
    }
    return new Summary(total, List.copyOf(categories), count);
  }
}
