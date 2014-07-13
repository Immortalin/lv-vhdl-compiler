package stupaq.vhdl2lv;

import com.google.common.base.Optional;
import com.google.common.base.Verify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map.Entry;

import stupaq.concepts.ComponentBindingResolver;
import stupaq.concepts.EntityDeclaration;
import stupaq.labview.scripting.hierarchy.FormulaNode;
import stupaq.labview.scripting.hierarchy.Terminal;
import stupaq.lvproject.LVProject;
import stupaq.naming.ArchitectureName;
import stupaq.naming.IOReference;
import stupaq.naming.Identifier;
import stupaq.vhdl2lv.IOSinks.Sink;
import stupaq.vhdl2lv.IOSources.Source;
import stupaq.vhdl2lv.WiringRules.PassLabels;
import stupaq.vhdl93.ast.NodeSequence;
import stupaq.vhdl93.ast.architecture_declaration;
import stupaq.vhdl93.ast.block_declarative_item;
import stupaq.vhdl93.ast.constant_declaration;
import stupaq.vhdl93.ast.context_clause;
import stupaq.vhdl93.ast.design_file;
import stupaq.vhdl93.ast.design_unit;
import stupaq.vhdl93.ast.entity_declaration;
import stupaq.vhdl93.ast.expression;
import stupaq.vhdl93.visitor.DepthFirstVisitor;
import stupaq.vhdl93.visitor.FlattenNestedListsVisitor;
import stupaq.vhdl93.visitor.GJNoArguDepthFirst;

import static com.google.common.base.Optional.of;
import static stupaq.vhdl93.ast.ASTBuilders.sequence;

public class DesignFileEmitter extends DepthFirstVisitor {
  private static final Logger LOGGER = LoggerFactory.getLogger(DesignFileEmitter.class);
  private static final Optional<String> ENTITY_CONTEXT = of("ENTITY CONTEXT");
  private static final Optional<String> ARCHITECTURE_CONTEXT = of("ARCHITECTURE CONTEXT");
  private static final Optional<String> ARCHITECTURE_DECLARATIVE_PART_LABEL =
      of("ARCHITECTURE EXTRA DECLARATIONS");
  private static final Optional<String> ARCHITECTURE_STATEMENT_PART_LABEL =
      of("ARCHITECTURE EXTRA BODY STATEMENTS");
  /** Context of {@link #visit(design_file)}. */
  private final ComponentBindingResolver resolver = new ComponentBindingResolver();
  /** Context of {@link #visit(design_file)}. */
  private final LVProject project;
  /** Context of {@link #visit(design_unit)}. */
  private context_clause lastContext;
  /** Context of {@link #visit(architecture_declaration)}. */
  private UniversalVI currentVi;
  /** Context of {@link #visit(architecture_declaration)}. */
  private IOSources namedSources;
  /** Context of {@link #visit(architecture_declaration)}. */
  private IOSinks danglingSinks;

  public DesignFileEmitter(LVProject project) {
    this.project = project;
  }

  @Override
  public void visit(design_file n) {
    n.accept(new FlattenNestedListsVisitor());
    super.visit(n);
  }

  @Override
  public void visit(context_clause n) {
    lastContext = n;
  }

  @Override
  public void visit(entity_declaration n) {
    EntityDeclaration entity = new EntityDeclaration(resolver, n, lastContext);
    resolver.addGlobal(entity);
  }

  @Override
  public void visit(architecture_declaration n) {
    danglingSinks = new IOSinks();
    namedSources = new IOSources();
    EntityDeclaration entity = resolver.getGlobal(Identifier.entity(n.entity_name));
    final ArchitectureName arch = Identifier.architecture(entity.name(), n);
    resolver.defaultArchitecture(arch);
    LOGGER.debug("ARCHITECTURE: {}", arch);
    // Create all generics, ports and eventually the VI itself.
    currentVi = new UniversalVI(project, arch, entity, namedSources, danglingSinks);
    // Fill local scope with component declarations.
    resolver.enterLocal(arch, n.architecture_declarative_part);
    // Emit architecture body.
    ConcurrentStatementsEmitter concurrentStatements =
        new ConcurrentStatementsEmitter(resolver, project, arch, currentVi, namedSources,
            danglingSinks);
    n.architecture_statement_part.accept(concurrentStatements);
    // Print out what we have found when it comes to wiring.
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Named sources:");
      for (Entry<IOReference, Source> entry : namedSources.entries()) {
        LOGGER.debug("\t{}", entry);
      }
      LOGGER.debug("Dangling sinks:");
      for (Entry<IOReference, Sink> entry : danglingSinks.entries()) {
        LOGGER.debug("\t{}", entry);
      }
    }
    // Emit entries from architecture declarative part.
    final StringBuilder declarativePartFallbacked = new StringBuilder();
    n.architecture_declarative_part.accept(new DepthFirstVisitor() {
      /** Context of {@link #visit(block_declarative_item)}. */
      boolean declarativePartFallback;

      @Override
      public void visit(constant_declaration n) {
        declarativePartFallback = false;
        IOReference ref = new IOReference(n.identifier_list.identifier);
        Verify.verify(n.nodeOptional.present(), "Missing value for constant: %s", ref);
        // There will be no more dangling sinks than we see right now, we can connect this
        // constant to every pending sink and forget about it.
        final String label = sequence(n.nodeToken, n.identifier_list, n.nodeToken1,
            n.subtype_indication).representation();
        Terminal terminal = n.nodeOptional.accept(new GJNoArguDepthFirst<Terminal>() {
          @Override
          public Terminal visit(NodeSequence n) {
            return n.nodes.get(1).accept(this);
          }

          @Override
          public Terminal visit(expression n) {
            return new SourceEmitter(currentVi, danglingSinks, namedSources).emitAsConstant(n,
                of(label));
          }
        });
        Verify.verify(!namedSources.containsKey(ref), "Constant: %s has other sources", ref);
        namedSources.put(ref, terminal);
      }

      @Override
      public void visit(block_declarative_item n) {
        declarativePartFallback = true;
        super.visit(n);
        if (declarativePartFallback) {
          declarativePartFallbacked.append(n.representation()).append(System.lineSeparator());
        }
      }
    });
    // Emit contexts.
    if (entity.context() != null) {
      String rep = entity.context().representation();
      if (!rep.isEmpty()) {
        new FormulaNode(currentVi, rep, ENTITY_CONTEXT);
      }
    }
    if (lastContext != null) {
      String rep = lastContext.representation();
      if (!rep.isEmpty()) {
        new FormulaNode(currentVi, rep, ARCHITECTURE_CONTEXT);
      }
    }
    // Emit declarative part leftovers.
    if (declarativePartFallbacked.length() > 0) {
      new FormulaNode(currentVi, declarativePartFallbacked.toString(),
          ARCHITECTURE_DECLARATIVE_PART_LABEL);
    }
    // Emit statement part leftovers.
    for (StringBuilder text : concurrentStatements.fallbackText().asSet()) {
      new FormulaNode(currentVi, text.toString(), ARCHITECTURE_STATEMENT_PART_LABEL);
    }
    // All references and labels are resolved now.
    new WiringRules(currentVi, namedSources, danglingSinks, new PassLabels(),
        concurrentStatements.wiresBlacklist()).applyAll();
    // Fallback for missing signal declarations and wires.
    for (Entry<IOReference, Source> entry : namedSources.entries()) {
      IOReference ref = entry.getKey();
      Source source = entry.getValue();
      if (source.label().isPresent()) {
        LOGGER.warn("Dangling source: {} with label: {}", ref, source.label().get());
      } else {
        LOGGER.info("Unconnected source: {}", ref);
      }
    }
    for (Entry<IOReference, Sink> entry : danglingSinks.entries()) {
      LOGGER.error("Dangling sink: {}", entry.getKey());
    }
    currentVi.cleanUpDiagram();
    currentVi = null;
    namedSources = null;
    danglingSinks = null;
    resolver.exitLocal();
  }
}
