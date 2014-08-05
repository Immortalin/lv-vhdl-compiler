package stupaq.translation.lv2vhdl.parsing;

import org.xml.sax.SAXException;

import java.io.IOException;

import javax.xml.bind.JAXBException;

import stupaq.labview.VIPath;
import stupaq.labview.parsing.VIElementsVisitor;
import stupaq.labview.scripting.ScriptingTools;

public class ParsedVI extends stupaq.labview.parsing.ParsedVI {
  public ParsedVI(ScriptingTools tools, VIPath viPath)
      throws IOException, SAXException, JAXBException {
    super(tools, viPath);
  }

  @Override
  public <E extends Exception> void accept(VIElementsVisitor<E> visitor) throws E {
    super.accept(visitor);
  }
}
