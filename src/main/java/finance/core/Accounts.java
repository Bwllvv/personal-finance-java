package finance.core;

import finance.core.AccountRepository.Credential;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Accounts {
  private static final int ITERATIONS = 600_000;
  private static final int KEY_BITS = 256;
  private final AccountRepository repository;
  private final SecureRandom random = new SecureRandom();

  public Accounts(AccountRepository repository) {
    this.repository = repository;
  }

  public String register(String value, char[] password) throws IOException {
    String login = Rules.user(value);
    validatePassword(password);
    byte[] salt = new byte[16];
    random.nextBytes(salt);
    var encoder = Base64.getEncoder();
    byte[] hash = derive(password, salt);
    try {
      var credential = new Credential(encoder.encodeToString(salt), encoder.encodeToString(hash));
      if (!repository.create(login, credential)) {
        throw new IllegalArgumentException("Этот логин уже занят.");
      }
      return login;
    } finally {
      Arrays.fill(hash, (byte) 0);
    }
  }

  public String authenticate(String value, char[] password) throws IOException {
    String login = Rules.user(value);
    validatePassword(password);
    Credential credential = repository.find(login).orElseThrow(Accounts::incorrect);
    var decoder = Base64.getDecoder();
    byte[] expected;
    byte[] salt;
    try {
      salt = decoder.decode(credential.salt());
      expected = decoder.decode(credential.hash());
      if (salt.length != 16 || expected.length != KEY_BITS / Byte.SIZE) {
        throw new IllegalArgumentException("Wrong credential size");
      }
    } catch (IllegalArgumentException e) {
      throw new IOException("Повреждена запись учётной записи.", e);
    }
    byte[] actual = derive(password, salt);
    try {
      if (!MessageDigest.isEqual(expected, actual)) {
        throw incorrect();
      }
      return login;
    } finally {
      Arrays.fill(expected, (byte) 0);
      Arrays.fill(actual, (byte) 0);
    }
  }

  private static void validatePassword(char[] password) {
    if (password.length < 8 || password.length > 128 || allWhitespace(password)) {
      throw new IllegalArgumentException(
          "Пароль: 8–128 символов, включая хотя бы один непробельный.");
    }
  }

  private static boolean allWhitespace(char[] value) {
    for (char character : value) {
      if (!Character.isWhitespace(character)) {
        return false;
      }
    }
    return true;
  }

  private static IllegalArgumentException incorrect() {
    return new IllegalArgumentException("Неверный логин или пароль.");
  }

  private static byte[] derive(char[] password, byte[] salt) {
    var spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("PBKDF2 недоступен в текущем JDK.", e);
    } finally {
      spec.clearPassword();
    }
  }
}
