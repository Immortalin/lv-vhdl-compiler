package stupaq.translation.vhdl2lv;

import com.google.common.base.Predicate;

import stupaq.translation.errors.MissingFeatureException;
import stupaq.vhdl93.VHDL93ParserTotal;
import stupaq.vhdl93.ast.NodeToken;
import stupaq.vhdl93.ast.interface_signal_declaration;
import stupaq.vhdl93.ast.mode;
import stupaq.vhdl93.visitor.NonTerminalsNoOpVisitor;

import static stupaq.vhdl93.VHDL93ParserConstants.IN;
import static stupaq.vhdl93.VHDL93ParserConstants.OUT;
import static stupaq.vhdl93.VHDL93ParserConstants.SIGNAL;
import static stupaq.vhdl93.ast.Builders.optional;
import static stupaq.vhdl93.ast.Builders.token;

class PortDeclaration extends SignalDeclaration implements ConnectorPaneTerminal {
  private final interface_signal_declaration node;
  private final int listIndex;
  private PortDirection direction;
  private int connectorIndex;

  public PortDeclaration(interface_signal_declaration node, int listIndex) {
    super(node);
    this.node = node;
    this.listIndex = listIndex;
    node.nodeOptional = optional(token(SIGNAL));
    direction = PortDirection.IN;
    node.nodeOptional1.accept(new NonTerminalsNoOpVisitor() {
      public void visit(mode n) {
        int mode = ((NodeToken) n.nodeChoice.choice).kind;
        switch (mode) {
          case IN:
            direction = PortDirection.IN;
            break;
          case OUT:
            direction = PortDirection.OUT;
            break;
          default:
            throw new MissingFeatureException(n,
                "Port direction: " + VHDL93ParserTotal.tokenString(mode) + " is not supported.");
        }
      }
    });
  }

  public PortDirection direction() {
    return direction;
  }

  @Override
  public boolean isInput() {
    return direction == PortDirection.IN;
  }

  @Override
  public boolean isConstant() {
    return false;
  }

  @Override
  public int listIndex() {
    return listIndex;
  }

  @Override
  public int connectorIndex() {
    return connectorIndex;
  }

  @Override
  public void connectorIndex(int index) {
    this.connectorIndex = index;
  }

  @Override
  public String representation() {
    return node.representation();
  }

  public static enum PortDirection {
    IN,
    OUT
  }

  public static class DirectionPredicate implements Predicate<PortDeclaration> {
    private final PortDirection direction;

    public DirectionPredicate(PortDirection direction) {
      this.direction = direction;
    }

    @Override
    public boolean apply(PortDeclaration input) {
      return direction == input.direction();
    }
  }
}
