package finance.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public record Wallet(
    Set<String> categories, Map<String, BigDecimal> budgets, List<Transaction> transactions) {
  public Wallet {
    var sorted = new TreeSet<String>();
    for (String category : categories) {
      if (!sorted.add(Rules.category(category))) {
        throw new IllegalArgumentException("Повторяющаяся категория: " + category);
      }
    }
    var limits = new TreeMap<String, BigDecimal>();
    budgets.forEach(
        (name, amount) -> {
          if (!sorted.contains(name)) {
            throw new IllegalArgumentException("Неизвестная категория бюджета: " + name);
          }
          limits.put(name, Rules.amount(amount.toPlainString()));
        });
    categories = Collections.unmodifiableSet(sorted);
    budgets = Collections.unmodifiableMap(limits);
    transactions = List.copyOf(transactions);
    for (Transaction transaction : transactions) {
      if (!categories.contains(transaction.category())) {
        throw new IllegalArgumentException(
            "Неизвестная категория операции: " + transaction.category());
      }
    }
  }

  public static Wallet empty() {
    return new Wallet(Set.of(), Map.of(), List.of());
  }

  public String requireCategory(String category) {
    String name = Rules.category(category);
    if (!categories.contains(name)) {
      throw new IllegalArgumentException("Неизвестная категория: " + name);
    }
    return name;
  }

  public Wallet addCategory(String value) {
    var names = new TreeSet<>(categories);
    String name = Rules.category(value);
    if (!names.add(name)) {
      throw new IllegalArgumentException("Категория уже существует: " + name);
    }
    return new Wallet(names, budgets, transactions);
  }

  public Wallet renameCategory(String oldValue, String newValue) {
    String oldName = requireCategory(oldValue);
    String newName = Rules.category(newValue);
    if (categories.contains(newName)) {
      throw new IllegalArgumentException("Категория уже существует: " + newName);
    }
    var names = new TreeSet<>(categories);
    names.remove(oldName);
    names.add(newName);
    var limits = new TreeMap<>(budgets);
    BigDecimal limit = limits.remove(oldName);
    if (limit != null) {
      limits.put(newName, limit);
    }
    var history =
        transactions.stream()
            .map(tx -> tx.category().equals(oldName) ? tx.inCategory(newName) : tx)
            .toList();
    return new Wallet(names, limits, history);
  }

  public Wallet removeCategory(String value) {
    String name = requireCategory(value);
    if (budgets.containsKey(name)
        || transactions.stream().anyMatch(tx -> tx.category().equals(name))) {
      throw new IllegalArgumentException("Удалить можно только категорию без операций и бюджета.");
    }
    var names = new TreeSet<>(categories);
    names.remove(name);
    return new Wallet(names, budgets, transactions);
  }

  public Wallet setBudget(String value, BigDecimal amount) {
    var limits = new TreeMap<>(budgets);
    limits.put(requireCategory(value), amount);
    return new Wallet(categories, limits, transactions);
  }

  public Wallet removeBudget(String value) {
    var limits = new TreeMap<>(budgets);
    String name = requireCategory(value);
    if (limits.remove(name) == null) {
      throw new IllegalArgumentException("Для категории нет бюджета: " + name);
    }
    return new Wallet(categories, limits, transactions);
  }

  public Wallet add(Transaction transaction) {
    requireCategory(transaction.category());
    var next = new ArrayList<>(transactions);
    next.add(transaction);
    return new Wallet(categories, budgets, next);
  }
}
