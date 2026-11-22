package finance.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Rules {
  private static final Pattern USER = Pattern.compile("[a-z][a-z0-9_-]{2,31}");
  private static final Pattern CATEGORY = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} _-]{0,39}");
  private static final Pattern MONEY = Pattern.compile("[0-9]{1,12}(?:\\.[0-9]{1,2})?");
  private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
  public static final BigDecimal ZERO = new BigDecimal("0.00");

  private Rules() {}

  public static String user(String value) {
    String login = value.strip().toLowerCase(Locale.ROOT);
    if (!USER.matcher(login).matches()) {
      throw new IllegalArgumentException("Логин: 3–32 символа a–z, 0–9, _ или -, первый — буква.");
    }
    return login;
  }

  public static String category(String value) {
    String name = value.strip().toLowerCase(Locale.ROOT);
    if (!CATEGORY.matcher(name).matches()) {
      throw new IllegalArgumentException(
          "Категория: 1–40 букв, цифр, пробелов, знаков _ или -, первый символ — буква или цифра.");
    }
    return name;
  }

  public static BigDecimal amount(String value) {
    if (!MONEY.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "Сумма: до 12 цифр до точки и до двух после, например 120.50.");
    }
    BigDecimal amount = new BigDecimal(value).setScale(2);
    if (amount.signum() <= 0) {
      throw new IllegalArgumentException("Сумма должна быть больше нуля.");
    }
    return amount;
  }

  public static LocalDate date(String value) {
    if (DATE.matcher(value).matches()) {
      try {
        LocalDate date = LocalDate.parse(value);
        if (date.getYear() > 0) {
          return date;
        }
      } catch (DateTimeParseException ignored) {
        // Return one message for malformed and impossible dates.
      }
    }
    throw new IllegalArgumentException("Дата должна быть существующей датой в формате YYYY-MM-DD.");
  }
}
