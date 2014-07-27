package stupaq.translation;

import com.google.common.base.Optional;

import stupaq.labview.UID;
import stupaq.vhdl93.ast.Position;
import stupaq.vhdl93.ast.SimpleNode;

public class SemanticException extends AbstractLocalisedException {
  public SemanticException(SimpleNode near, String message, Object... args) {
    super(String.format(message, args), near);
  }

  public SemanticException(String message, Object... args) {
    super(String.format(message, args), Optional.<Position>absent());
  }

  public static <T> T semanticNotNull(T x, SimpleNode n, String message, Object... args) {
    if (x == null) {
      throw new SemanticException(n, message, args);
    }
    return x;
  }

  public static <T> T semanticNotNull(T x, UID uid, String message, Object... args) {
    if (x == null) {
      throw new SemanticException(message, args);
    }
    return x;
  }

  public static void semanticCheck(boolean b, SimpleNode n, String message, Object... args) {
    if (!b) {
      throw new SemanticException(n, message, args);
    }
  }

  public static void semanticCheck(boolean b, UID uid, String message, Object... args) {
    if (!b) {
      throw new SemanticException(message, args);
    }
  }

  public static void semanticCheck(boolean b, String message, Object... args) {
    if (!b) {
      throw new SemanticException(message, args);
    }
  }
}
