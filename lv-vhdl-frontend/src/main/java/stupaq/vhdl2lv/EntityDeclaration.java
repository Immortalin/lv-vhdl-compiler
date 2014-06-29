package stupaq.vhdl2lv;

import com.google.common.collect.Lists;

import java.util.List;

import stupaq.vhdl93.ast.entity_declaration;
import stupaq.vhdl93.ast.interface_constant_declaration;
import stupaq.vhdl93.ast.interface_signal_declaration;
import stupaq.vhdl93.visitor.DepthFirstVisitor;

import static stupaq.vhdl93.ast.ASTGetters.representation;

public class EntityDeclaration extends HDLElement<entity_declaration> {
  public static final String DEFAULT_LIBRARY_PREFIX = "work.";
  public static final String LIBRARY_SEPARATOR = ".";
  private final String name;
  private final List<ConstantDeclaration> generics = Lists.newArrayList();
  private final List<PortDeclaration> ports = Lists.newArrayList();

  public EntityDeclaration(entity_declaration node) {
    super(node);
    String id = representation(node().entity_identifier.identifier);
    name = id.contains(LIBRARY_SEPARATOR) ? id : DEFAULT_LIBRARY_PREFIX + id;
    node.entity_header.accept(new DepthFirstVisitor() {
      @Override
      public void visit(interface_constant_declaration n) {
        generics.add(new ConstantDeclaration(n));
      }

      @Override
      public void visit(interface_signal_declaration n) {
        ports.add(new PortDeclaration(n));
      }
    });
  }

  public String name() {
    return name;
  }

  public List<ConstantDeclaration> generics() {
    return generics;
  }

  public List<PortDeclaration> ports() {
    return ports;
  }

  @Override
  public String toString() {
    return "EntityDeclaration{" + "generics=" + generics + ", ports=" + ports + '}';
  }
}
