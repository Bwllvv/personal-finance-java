package finance.infra;

import finance.core.Rules;
import finance.core.SnapshotStore;
import finance.core.Transaction;
import finance.core.Wallet;
import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public final class CsvSnapshots implements SnapshotStore {
  private static final String VERSION = "finance-wallet,1,,";
  private static final String HEADER = "type,category,amount,date";

  @Override
  public Wallet read(Path file) throws IOException {
    var categories = new TreeSet<String>();
    var budgets = new TreeMap<String, BigDecimal>();
    var transactions = new ArrayList<Transaction>();
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      if (!VERSION.equals(reader.readLine()) || !HEADER.equals(reader.readLine())) {
        throw new IOException("Несовместимый формат снимка: требуется finance-wallet,1.");
      }
      int number = 2;
      String line;
      while ((line = reader.readLine()) != null) {
        number++;
        try {
          String[] fields = line.split(",", -1);
          if (fields.length != 4) {
            throw new IllegalArgumentException("Ожидаются четыре колонки.");
          }
          String category = Rules.category(fields[1]);
          switch (fields[0]) {
            case "CATEGORY" -> {
              requireEmpty(fields[2]);
              requireEmpty(fields[3]);
              if (!categories.add(category)) {
                throw new IllegalArgumentException("Повторяющаяся категория.");
              }
            }
            case "BUDGET" -> {
              requireEmpty(fields[3]);
              if (budgets.putIfAbsent(category, Rules.amount(fields[2])) != null) {
                throw new IllegalArgumentException("Повторяющийся бюджет.");
              }
            }
            case "INCOME", "EXPENSE" ->
                transactions.add(
                    new Transaction(
                        Transaction.Kind.valueOf(fields[0]),
                        Rules.amount(fields[2]),
                        category,
                        Rules.date(fields[3])));
            default -> throw new IllegalArgumentException("Неизвестный тип строки.");
          }
        } catch (IllegalArgumentException e) {
          throw new IOException("Снимок, строка " + number + ": " + e.getMessage(), e);
        }
      }
    }
    try {
      return new Wallet(categories, budgets, transactions);
    } catch (IllegalArgumentException e) {
      throw new IOException("Некорректный снимок: " + e.getMessage(), e);
    }
  }

  private static void requireEmpty(String value) {
    if (!value.isEmpty()) {
      throw new IllegalArgumentException("Ожидается пустая ячейка.");
    }
  }

  @Override
  public void export(Path file, Wallet wallet) throws IOException {
    Files.writeString(
        file,
        encode(wallet),
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);
  }

  static String encode(Wallet wallet) {
    var result = new StringBuilder(VERSION).append('\n').append(HEADER).append('\n');
    for (String category : wallet.categories()) {
      result.append("CATEGORY,").append(category).append(",,\n");
    }
    for (Map.Entry<String, BigDecimal> budget : wallet.budgets().entrySet()) {
      result
          .append("BUDGET,")
          .append(budget.getKey())
          .append(',')
          .append(budget.getValue().toPlainString())
          .append(",\n");
    }
    for (Transaction tx : wallet.transactions()) {
      result
          .append(tx.kind())
          .append(',')
          .append(tx.category())
          .append(',')
          .append(tx.amount().toPlainString())
          .append(',')
          .append(tx.date())
          .append('\n');
    }
    return result.toString();
  }
}
