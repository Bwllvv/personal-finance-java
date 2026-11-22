package finance.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record Transaction(Kind kind, BigDecimal amount, String category, LocalDate date) {
  public enum Kind {
    INCOME,
    EXPENSE
  }

  public Transaction {
    Objects.requireNonNull(kind);
    amount = Rules.amount(amount.toPlainString());
    category = Rules.category(category);
    date = Rules.date(date.toString());
  }

  public Transaction inCategory(String name) {
    return new Transaction(kind, amount, name, date);
  }
}
