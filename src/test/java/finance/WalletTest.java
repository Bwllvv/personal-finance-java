package finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import finance.core.Alerts;
import finance.core.Reports;
import finance.core.Rules;
import finance.core.Transaction;
import finance.core.Transaction.Kind;
import finance.core.Wallet;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WalletTest {
  private static final LocalDate NOVEMBER = LocalDate.of(2026, 11, 1);
  private static final LocalDate DECEMBER = LocalDate.of(2026, 12, 1);

  private static Transaction tx(Kind kind, String amount, String category, LocalDate date) {
    return new Transaction(kind, Rules.amount(amount), category, date);
  }

  private static Wallet wallet() {
    return Wallet.empty().addCategory("Еда").addCategory("Дом").addCategory("Зарплата");
  }

  @Test
  void calculatesExactDecimalBalance() {
    var wallet =
        wallet()
            .add(tx(Kind.INCOME, "0.30", "Зарплата", NOVEMBER))
            .add(tx(Kind.EXPENSE, "0.10", "Еда", NOVEMBER))
            .add(tx(Kind.EXPENSE, "0.20", "Дом", NOVEMBER));
    var totals = Reports.summarize(wallet, Set.of(), null, null).totals();
    assertEquals("0.30", totals.income().toPlainString());
    assertEquals("0.30", totals.expenses().toPlainString());
    assertEquals("0.00", totals.balance().toPlainString());
  }

  @Test
  void selectsSeveralCategoriesAndInclusiveDates() {
    var wallet =
        wallet()
            .add(tx(Kind.INCOME, "200", "Зарплата", NOVEMBER))
            .add(tx(Kind.EXPENSE, "40", "Еда", NOVEMBER))
            .add(tx(Kind.EXPENSE, "30", "Дом", NOVEMBER.plusDays(1)))
            .add(tx(Kind.EXPENSE, "90", "Еда", DECEMBER));
    var report = Reports.summarize(wallet, Set.of("ЕДА", "дом"), NOVEMBER, NOVEMBER.plusDays(1));
    assertEquals(2, report.operationCount());
    assertEquals("70.00", report.totals().expenses().toPlainString());
    assertEquals("0.00", report.totals().income().toPlainString());
  }

  @Test
  void keepsLifetimeBudgetWhenReportFiltersDates() {
    var wallet =
        wallet()
            .setBudget("Еда", Rules.amount("100"))
            .add(tx(Kind.EXPENSE, "30", "Еда", NOVEMBER))
            .add(tx(Kind.EXPENSE, "50", "Еда", DECEMBER));
    var row = Reports.summarize(wallet, Set.of("еда"), DECEMBER, null).categories().get(0);
    assertEquals("50.00", row.selection().expenses().toPlainString());
    assertEquals("20.00", row.remaining().toPlainString());
  }

  @Test
  void reportsNoMatchingTransactions() {
    var wallet = wallet().add(tx(Kind.EXPENSE, "42", "Еда", NOVEMBER));
    var report = Reports.summarize(wallet, Set.of("дом"), null, NOVEMBER);
    assertEquals(0, report.operationCount());
    assertEquals(Rules.ZERO, report.totals().expenses());
  }

  @Test
  void rejectsInvertedPeriods() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Reports.summarize(wallet(), Set.of(), DECEMBER, NOVEMBER));
  }

  @Test
  void renamesCategoryWithBudgetAndHistory() {
    var renamed =
        wallet()
            .setBudget("Еда", Rules.amount("100"))
            .add(tx(Kind.EXPENSE, "30", "Еда", NOVEMBER))
            .renameCategory("ЕДА", "Продукты");
    assertFalse(renamed.categories().contains("еда"));
    assertEquals("продукты", renamed.transactions().get(0).category());
    assertEquals(Rules.amount("100"), renamed.budgets().get("продукты"));
  }

  @Test
  void rejectsDuplicateCategoriesAndRenameCollision() {
    var wallet = wallet();
    assertThrows(IllegalArgumentException.class, () -> wallet.addCategory(" ЕДА "));
    assertThrows(IllegalArgumentException.class, () -> wallet.renameCategory("дом", "еда"));
  }

  @Test
  void deletesOnlyUnusedCategory() {
    var wallet =
        wallet().setBudget("Еда", Rules.amount("100")).add(tx(Kind.EXPENSE, "1", "Дом", NOVEMBER));
    assertThrows(IllegalArgumentException.class, () -> wallet.removeCategory("Еда"));
    assertThrows(IllegalArgumentException.class, () -> wallet.removeCategory("Дом"));
    assertFalse(wallet.removeCategory("зарплата").categories().contains("зарплата"));
  }

  @Test
  void changesAndRemovesBudget() {
    var original = wallet().setBudget("Еда", Rules.amount("100"));
    var updated = original.setBudget("Еда", Rules.amount("120"));
    assertEquals(Rules.amount("100"), original.budgets().get("еда"));
    assertEquals(Rules.amount("120"), updated.budgets().get("еда"));
    assertTrue(updated.removeBudget("еда").budgets().isEmpty());
  }

  @Test
  void warnsAtEightyPercentAndAtBudgetExhaustion() {
    var original = wallet().setBudget("Еда", Rules.amount("100"));
    var below = original.add(tx(Kind.EXPENSE, "79.99", "Еда", NOVEMBER));
    assertTrue(Alerts.forBudget(below, "Еда").isEmpty());
    var eighty = below.add(tx(Kind.EXPENSE, "0.01", "Еда", NOVEMBER));
    assertTrue(Alerts.forBudget(eighty, "еда").get(0).contains("80%"));
    var exact = eighty.add(tx(Kind.EXPENSE, "20", "Еда", NOVEMBER));
    assertTrue(Alerts.forBudget(exact, "еда").get(0).contains("исчерпан"));
    var above = exact.add(tx(Kind.EXPENSE, "0.01", "Еда", NOVEMBER));
    assertTrue(Alerts.forBudget(above, "еда").get(0).contains("превышен на 0.01"));
  }

  @Test
  void warnsForNegativeAndZeroBalance() {
    var expense = tx(Kind.EXPENSE, "25", "еда", NOVEMBER);
    var wallet = wallet().add(expense);
    assertTrue(Alerts.afterTransaction(wallet, expense).get(0).contains("-25.00"));
    var income = tx(Kind.INCOME, "25", "еда", DECEMBER);
    assertTrue(Alerts.afterTransaction(wallet.add(income), income).get(0).contains("равен нулю"));
  }

  @Test
  void validatesAmounts() {
    for (String invalid :
        new String[] {"", "0.00", "-2", "1,2", "1.234", "NaN", "1e2", "1234567890123"}) {
      assertThrows(IllegalArgumentException.class, () -> Rules.amount(invalid), invalid);
    }
    assertEquals("999999999999.99", Rules.amount("999999999999.99").toPlainString());
  }

  @Test
  void validatesDates() {
    for (String invalid : new String[] {"2026-02-29", "26-01-05", "0000-01-01", "2026-11-31"}) {
      assertThrows(IllegalArgumentException.class, () -> Rules.date(invalid), invalid);
    }
    assertEquals(LocalDate.of(2024, 2, 29), Rules.date("2024-02-29"));
  }

  @Test
  void rejectsUnknownCategoriesInTransactionsAndReports() {
    var wallet = wallet();
    assertThrows(
        IllegalArgumentException.class,
        () -> wallet.add(tx(Kind.EXPENSE, "1", "транспорт", NOVEMBER)));
    assertThrows(
        IllegalArgumentException.class,
        () -> Reports.summarize(wallet, Set.of("транспорт"), null, null));
  }
}
