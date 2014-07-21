package stupaq.lv2vhdl;

import com.google.common.base.Optional;
import com.google.common.base.Verify;
import com.google.common.collect.Maps;
import com.google.common.primitives.UnsignedInteger;

import com.ni.labview.VIDump;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import stupaq.SemanticException;
import stupaq.commons.IntegerMap;
import stupaq.labview.UID;
import stupaq.labview.VIPath;
import stupaq.labview.hierarchy.Control;
import stupaq.labview.hierarchy.ControlCluster;
import stupaq.labview.hierarchy.FormulaNode;
import stupaq.labview.hierarchy.Panel;
import stupaq.labview.parsing.NoOpVisitor;
import stupaq.labview.parsing.PrintingVisitor;
import stupaq.labview.parsing.VIParser;
import stupaq.labview.scripting.tools.ControlStyle;
import stupaq.naming.ComponentName;
import stupaq.naming.EntityName;
import stupaq.project.VHDLProject;
import stupaq.vhdl93.ast.*;

import static stupaq.SemanticException.semanticCheck;
import static stupaq.TranslationConventions.ENTITY_CONTEXT;
import static stupaq.TranslationConventions.ENTITY_EXTRA_DECLARATIONS;
import static stupaq.lv2vhdl.VHDL93PartialParser.parser;
import static stupaq.vhdl93.VHDL93ParserConstants.IS;
import static stupaq.vhdl93.VHDL93ParserConstants.SEMICOLON;
import static stupaq.vhdl93.ast.ASTBuilders.*;

class InterfaceDeclaration extends NoOpVisitor<Exception> {
  private static final Logger LOGGER = LoggerFactory.getLogger(InterfaceDeclaration.class);
  private final NodeListOptional entityDeclarations = new NodeListOptional();
  private final IntegerMap<interface_constant_declaration> generics = new IntegerMap<>();
  private final IntegerMap<interface_signal_declaration> ports = new IntegerMap<>();
  private final IntegerMap<IntegerMap<String>> connIndexToNames = new IntegerMap<>();
  private final Map<UID, Integer> controlIndexWithinCluster = Maps.newHashMap();
  private final Map<UID, IntegerMap<String>> controlOwnerToNames = Maps.newHashMap();
  private UID rootPanel;
  private boolean clustered;
  private context_clause entityContext;

  public InterfaceDeclaration(VHDLProject project, VIPath viPath) throws Exception {
    this(VIParser.parseVI(project.tools(), viPath));
  }

  public InterfaceDeclaration(VIDump theVi) throws Exception {
    VIParser.visitVI(theVi, PrintingVisitor.create());
    VIParser.visitVI(theVi, this);
  }

  public boolean isClustered() {
    return clustered;
  }

  public IntegerMap<String> clusteredNames(int connPaneIndex) {
    return connIndexToNames.getPresent(connPaneIndex);
  }

  public design_unit emitAsEntity(EntityName name) throws Exception {
    context_clause context =
        entityContext != null ? entityContext : new context_clause(listOptional());
    entity_identifier identifier = parser(name.entity().toString()).entity_identifier();
    entity_header header = new entity_header(createGenerics(), createPorts());
    entity_declaration declaration =
        new entity_declaration(identifier, header, new entity_declarative_part(entityDeclarations),
            optional(), optional(), optional());
    return new design_unit(context,
        new library_unit(choice(new primary_unit(choice(declaration)))));
  }

  public component_declaration emitAsComponent(ComponentName name) throws Exception {
    component_identifier identifier = parser(name.component().toString()).component_identifier();
    component_header header = new component_header(createGenerics(), createPorts());
    return new component_declaration(identifier, optional(token(IS)), header, optional());
  }

  private NodeOptional createGenerics() throws Exception {
    if (generics.isEmpty()) {
      return optional();
    } else {
      NodeListOptional rest = listOptional();
      interface_constant_declaration first =
          split(generics.values(), tokenSupplier(SEMICOLON), rest);
      return optional(new formal_generic_clause(
          new generic_clause(new generic_list(new generic_interface_list(first, rest)))));
    }
  }

  private NodeOptional createPorts() throws Exception {
    if (ports.isEmpty()) {
      return optional();
    } else {
      NodeListOptional rest = listOptional();
      interface_signal_declaration first = split(ports.values(), tokenSupplier(SEMICOLON), rest);
      return optional(new formal_port_clause(
          new port_clause(new port_list(new port_interface_list(first, rest)))));
    }
  }

  @Override
  public Iterable<String> parsersOrder() {
    return Arrays.asList(Panel.XML_NAME, FormulaNode.XML_NAME, ControlCluster.XML_NAME,
        Control.NUMERIC_XML_NAME);
  }

  @Override
  public void Panel(Optional<UID> ownerUID, UID uid) {
    if (!ownerUID.isPresent()) {
      Verify.verify(rootPanel == null);
      rootPanel = uid;
    }
  }

  @Override
  public void FormulaNode(UID ownerUID, UID uid, String expression, Optional<String> label,
      List<UID> termUIDs) throws Exception {
    VHDL93PartialParser parser = parser(expression);
    if (label.equals(ENTITY_CONTEXT)) {
      entityContext = parser.context_clause();
    } else if (label.equals(ENTITY_EXTRA_DECLARATIONS)) {
      NodeListOptional extra = parser.entity_declarative_part().nodeListOptional;
      entityDeclarations.nodes.addAll(extra.nodes);
    }
  }

  @Override
  public void ControlCluster(UID ownerUID, UID uid, Optional<String> label, UID terminalUID,
      boolean isIndicator, int controlIndex, List<UID> controlUIDs) {
    clustered = true;
    int index = 0;
    IntegerMap<String> names = new IntegerMap<>();
    // FIXME for some reason indicator is hooked up to 1 and control to 0, I don't want to know
    connIndexToNames.put(controlIndex, names);
    controlOwnerToNames.put(uid, names);
    for (UID control : controlUIDs) {
      controlIndexWithinCluster.put(control, index);
      ++index;
    }
  }

  @Override
  public void Control(UID ownerUID, UID uid, Optional<String> label, UID terminalUID,
      boolean isIndicator, ControlStyle style, int controlIndex, String description)
      throws Exception {
    Verify.verifyNotNull(rootPanel);
    semanticCheck(label.isPresent(), "Missing control label (should contain port declaration).");
    if (clustered) {
      semanticCheck(!rootPanel.equals(ownerUID),
          "VI is clustered, but some control has front panel as an owner.");
      Verify.verify(controlIndex == 0);
      // Fill information about clustered control.
      IntegerMap<String> names = controlOwnerToNames.get(ownerUID);
      names.put(controlIndexWithinCluster.get(uid), label.get());
      // Read virtual controlIndex.
      try {
        controlIndex = UnsignedInteger.valueOf(description.trim()).intValue();
      } catch (NumberFormatException e) {
        throw new SemanticException(
            "Control description: %s does not contain port or generic index.", description);
      }
    } else {
      semanticCheck(rootPanel.equals(ownerUID),
          "VI is not clustered, but some control has owner other than front panel.");
    }
    String declaration = label.get().trim();
    VHDL93PartialParser labelParser = parser(declaration);
    if (style == ControlStyle.NUMERIC_I32) {
      // This is a generic.
      interface_constant_declaration generic = labelParser.interface_constant_declaration();
      semanticCheck(!generic.identifier_list.nodeListOptional.present(),
          "Multiple identifiers in generic declaration.");
      generics.put(controlIndex, generic);
    } else if (style == ControlStyle.NUMERIC_DBL) {
      // This is a port.
      interface_signal_declaration port = labelParser.interface_signal_declaration();
      semanticCheck(!port.identifier_list.nodeListOptional.present(),
          "Multiple identifiers in port declaration.");
      ports.put(controlIndex, port);
    } else {
      throw new SemanticException("Control style not recognised: %s", style);
    }
  }
}
