package stupaq.translation.lv2vhdl.syntax;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

import stupaq.labview.UID;
import stupaq.labview.parsing.NoOpVisitor;
import stupaq.translation.lv2vhdl.wiring.Endpoint;
import stupaq.translation.lv2vhdl.wiring.EndpointsMap;
import stupaq.vhdl93.ParseException;
import stupaq.vhdl93.ast.constant_declaration;
import stupaq.vhdl93.ast.expression;
import stupaq.vhdl93.ast.signal_declaration;

import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.FluentIterable.from;
import static stupaq.translation.SemanticException.semanticCheck;
import static stupaq.translation.TranslationConventions.*;
import static stupaq.translation.lv2vhdl.syntax.VHDL93ParserPartial.Parsers.forString;
import static stupaq.vhdl93.VHDL93ParserConstants.ASSIGN;
import static stupaq.vhdl93.VHDL93ParserConstants.SEMICOLON;
import static stupaq.vhdl93.VHDL93ParserTotal.tokenString;

public abstract class VIContextualVisitor<E extends Exception> extends NoOpVisitor<E> {
  private static final Logger LOGGER = LoggerFactory.getLogger(VIContextualVisitor.class);
  private final Set<UID> whileLoops = Sets.newHashSet();

  protected EndpointsMap endpointsMap() {
    return null;
  }

  @Override
  public final void Wire(UID ownerUID, UID uid, Optional<String> label) throws E {
    if (label.isPresent()) {
      try {
        String declString = label.get().trim();
        if (!declString.endsWith(tokenString(SEMICOLON))) {
          declString += tokenString(SEMICOLON);
        }
        signal_declaration declaration = forString(declString).signal_declaration();
        WireWithSignalDeclaration(uid, label.get(), declaration);
        return;
      } catch (ParseException ignored) {
      }
      try {
        expression expression = forString(label.get()).expression();
        WireWithExpression(uid, label.get(), expression);
        return;
      } catch (ParseException ignored) {
      }
    }
    semanticCheck(!label.isPresent(), "Cannot recognize label of the wire.");
    WirePlain(uid);
  }

  protected void WirePlain(UID uid) {
  }

  protected void WireWithExpression(UID uid, String label, expression expression) {
  }

  protected void WireWithSignalDeclaration(UID uid, String label, signal_declaration declaration) {
  }

  @Override
  public final void WhileLoop(UID owner, UID uid) {
    whileLoops.add(uid);
  }

  @Override
  public final void FormulaNode(UID ownerUID, UID uid, String expression, Optional<String> label,
      List<UID> termUIDs) throws E {
    if (label.equals(ENTITY_CONTEXT)) {
      FormulaWithEntityContext(uid, expression);
      return;
    }
    if (label.equals(ENTITY_EXTRA_DECLARATIONS)) {
      FormulaWithEntityDeclarations(uid, expression);
      return;
    }
    if (label.equals(ARCHITECTURE_CONTEXT)) {
      FormulaWithArchitectureContext(uid, expression);
      return;
    }
    if (label.equals(ARCHITECTURE_EXTRA_DECLARATIONS)) {
      FormulaWithArchitectureDeclarations(uid, expression);
      return;
    }
    if (endpointsMap() == null) {
      LOGGER.warn("Cannot classify node: {}, missing endpoints map.", uid);
      return;
    }
    FluentIterable<Endpoint> parameters = from(termUIDs).transform(new Function<UID, Endpoint>() {
      @Override
      public Endpoint apply(UID uid) {
        return endpointsMap().get(uid);
      }
      });
    if (whileLoops.contains(ownerUID)) {
      FormulaWithProcessStatement(uid, expression, parameters);
      return;
    }
    if (label.isPresent()) {
      try {
        VHDL93ParserPartial parser =
            forString(label.get() + tokenString(ASSIGN) + expression + tokenString(SEMICOLON));
        constant_declaration constant = parser.constant_declaration();
        FormulaWithDeclaredConstant(uid, constant, parameters);
        return;
      } catch (ParseException ignored) {
      }
    }
    Endpoint lvalue = null, rvalue = null;
    for (Endpoint param : parameters) {
      if (param.name().equals(LVALUE_PARAMETER)) {
        semanticCheck(!param.isSource(), uid, "L-value must be data sink.");
        lvalue = param;
      } else if (param.name().equals(RVALUE_PARAMETER)) {
        semanticCheck(param.isSource(), uid, "R-value must be data source.");
        rvalue = param;
      }
    }
    semanticCheck(lvalue == null || rvalue == null, uid,
        "Expression cannot be both l- and r-value.");
    if (lvalue != null) {
      FormulaWithLvalue(uid, expression, lvalue, parameters.filter(not(equalTo(lvalue))));
    } else if (rvalue != null) {
      FormulaWithRvalue(uid, expression, rvalue, parameters.filter(not(equalTo(rvalue))));
    } else {
      FormulaWithConcurrentStatements(uid, expression, parameters);
    }
  }

  protected void FormulaWithEntityContext(UID uid, String expression) throws E {
  }

  protected void FormulaWithEntityDeclarations(UID uid, String expression) throws E {
  }

  protected void FormulaWithArchitectureContext(UID uid, String expression) throws E {
  }

  protected void FormulaWithArchitectureDeclarations(UID uid, String expression) throws E {
  }

  protected void FormulaWithConcurrentStatements(UID uid, String expression,
      Iterable<Endpoint> parameters) throws E {
  }

  protected void FormulaWithProcessStatement(UID uid, String expression,
      Iterable<Endpoint> parameters) throws E {
  }

  protected void FormulaWithLvalue(UID uid, String expression, Endpoint lvalue,
      Iterable<Endpoint> otherParameters) throws E {
  }

  protected void FormulaWithRvalue(UID uid, String expression, Endpoint rvalue,
      Iterable<Endpoint> otherParameters) throws E {
  }

  protected void FormulaWithDeclaredConstant(UID uid, constant_declaration constant,
      Iterable<Endpoint> parameters) throws E {
  }
}