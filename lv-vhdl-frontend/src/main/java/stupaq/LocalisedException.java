package stupaq;

import com.google.common.base.Optional;

import stupaq.vhdl93.ast.Position;
import stupaq.vhdl93.ast.SimpleNode;

abstract class LocalisedException extends RuntimeException {
  protected LocalisedException(String message, Optional<Position> position) {
    super((position.isPresent() ? position.get().toString() : "") + message);
  }

  protected LocalisedException(String message, SimpleNode near) {
    this(message, near.position());
  }
}
