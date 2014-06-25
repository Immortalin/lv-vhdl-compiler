package stupaq.vhdl2vhdl;

import java.io.FileInputStream;

import stupaq.vhdl93.VHDL93Parser;
import stupaq.vhdl93.ast.SimpleNode;
import stupaq.vhdl93.visitor.TreeDumper;

public class VHDL2VHDL {
  public static void main(String args[]) throws Exception {
    if (args.length == 1) {
      FileInputStream file = new FileInputStream(args[0]);
      VHDL93Parser parser = new VHDL93Parser(file);
      try {
        SimpleNode root = parser.design_file();
        root.accept(new TreeDumper(System.err));
        System.err.println();
        root.accept(new VHDLTreeFormatter());
        root.accept(new TreeDumper(System.out));
        System.out.println();
      } catch (Exception e) {
        e.printStackTrace();
        throw e;
      }
    } else {
      System.err.println("usage: filename");
    }
  }
}
