package stupaq.vhdl2lv;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;

import java.util.Set;

import stupaq.concepts.IOReference;
import stupaq.labview.scripting.hierarchy.Formula;
import stupaq.labview.scripting.hierarchy.FormulaNode;
import stupaq.labview.scripting.hierarchy.FormulaParameter;
import stupaq.labview.scripting.hierarchy.Generic;
import stupaq.labview.scripting.hierarchy.LazyTerminal;
import stupaq.labview.scripting.hierarchy.Terminal;
import stupaq.vhdl93.ast.SimpleNode;
import stupaq.vhdl93.ast.attribute_designator;
import stupaq.vhdl93.ast.identifier;
import stupaq.vhdl93.ast.signature;
import stupaq.vhdl93.ast.suffix;
import stupaq.vhdl93.visitor.DepthFirstVisitor;

import static stupaq.vhdl93.ast.ASTGetters.representation;

class ExpressionSourceEmitter extends ExpressionEmitter {
  /** Context of {@link ExpressionSourceEmitter}. */
  private final IOSinks danglingSinks;

  public ExpressionSourceEmitter(Generic owner, IOSinks danglingSinks) {
    super(owner);
    this.danglingSinks = danglingSinks;
  }

  @Override
  public Terminal formula(SimpleNode n) {
    Formula formula = new FormulaNode(owner, representation(n), Optional.<String>absent());
    terminals(formula, n);
    return formula.addOutput("RVALUE");
  }

  @Override
  public void terminals(final Formula formula, final Set<IOReference> blacklist, SimpleNode n) {
    n.accept(new DepthFirstVisitor() {
      @Override
      public void visit(identifier n) {
        final IOReference ref = new IOReference(n);
        LOGGER.debug("Possible terminal: {}", ref);
        if (!blacklist.contains(ref)) {
          blacklist.add(ref);
          Terminal<FormulaParameter> terminal =
              new LazyTerminal<>(new Supplier<Terminal<FormulaParameter>>() {
                @Override
                public Terminal<FormulaParameter> get() {
                  return formula.addInput(ref.toString());
                }
              });
          danglingSinks.put(ref, terminal);
        }
      }

      @Override
      public void visit(attribute_designator n) {
        // We are not interested in identifiers from some internal scope.
      }

      @Override
      public void visit(signature n) {
        // We are not interested in identifiers from some internal scope.
      }

      @Override
      public void visit(suffix n) {
        // We are not interested in identifiers from some internal scope.
      }
    });
  }
}
