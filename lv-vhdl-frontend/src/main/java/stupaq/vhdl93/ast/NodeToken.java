package stupaq.vhdl93.ast;

import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.Vector;

/**
 * Represents a single token in the grammar.  If the "-tk" option is used, also contains a Vector of
 * preceding special tokens.
 */
public class NodeToken extends SimpleNode implements Node {
  public NodeToken(String s) {
    this(s, TokenKindCache.resolveKind(s), -1, -1, -1, -1);
  }

  public NodeToken(String s, int kind, int beginLine, int beginColumn, int endLine, int endColumn) {
    tokenImage = s;
    specialTokens = null;
    this.kind = kind;
    this.beginLine = beginLine;
    this.beginColumn = beginColumn;
    this.endLine = endLine;
    this.endColumn = endColumn;
  }

  public NodeToken getSpecialAt(int i) {
    if (specialTokens == null) {
      throw new NoSuchElementException("No specials in token");
    }
    return specialTokens.elementAt(i);
  }

  public int numSpecials() {
    if (specialTokens == null) {
      return 0;
    }
    return specialTokens.size();
  }

  public void addSpecial(NodeToken s) {
    if (specialTokens == null) {
      specialTokens = new Vector<NodeToken>();
    }
    specialTokens.addElement(s);
  }

  public void trimSpecials() {
    if (specialTokens == null) {
      return;
    }
    specialTokens.trimToSize();
  }

  public String toString() {
    return tokenImage;
  }

  public String withSpecials() {
    if (specialTokens == null) {
      return tokenImage;
    }

    StringBuffer buf = new StringBuffer();

    for (Enumeration<NodeToken> e = specialTokens.elements(); e.hasMoreElements(); ) {
      buf.append(e.nextElement().toString());
    }

    buf.append(tokenImage);
    return buf.toString();
  }

  public void accept(stupaq.vhdl93.visitor.Visitor v) {
    v.visit(this);
  }

  public <R, A> R accept(stupaq.vhdl93.visitor.GJVisitor<R, A> v, A argu) {
    return v.visit(this, argu);
  }

  public <R> R accept(stupaq.vhdl93.visitor.GJNoArguVisitor<R> v) {
    return v.visit(this);
  }

  public <A> void accept(stupaq.vhdl93.visitor.GJVoidVisitor<A> v, A argu) {
    v.visit(this, argu);
  }

  public String tokenImage;

  // Stores a list of NodeTokens
  public Vector<NodeToken> specialTokens;

  // -1 for these ints means no position info is available.
  public int beginLine, beginColumn, endLine, endColumn;

  // Equal to the JavaCC token "kind" integer.
  // -1 if not available.
  public int kind;
}
