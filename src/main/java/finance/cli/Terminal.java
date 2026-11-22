package finance.cli;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;

public final class Terminal {
  private final Console console;
  private final BufferedReader reader;
  private final PrintWriter writer;

  public Terminal(Console console) {
    this.console = console;
    this.reader = null;
    this.writer = console.writer();
  }

  public Terminal(Reader reader, PrintWriter writer) {
    this.console = null;
    this.reader = new BufferedReader(reader);
    this.writer = writer;
  }

  String command(String login) throws IOException {
    return console == null
        ? reader.readLine()
        : console.readLine("%s> ", login == null ? "finance" : login);
  }

  char[] password() throws IOException {
    if (console != null) {
      return console.readPassword("Пароль: ");
    }
    String line = reader.readLine();
    return line == null ? null : line.toCharArray();
  }

  void line(String message) {
    writer.println(message);
    writer.flush();
  }
}
