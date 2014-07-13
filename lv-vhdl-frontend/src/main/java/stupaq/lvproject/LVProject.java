package stupaq.lvproject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import stupaq.labview.VIPath;
import stupaq.labview.scripting.ScriptingTools;
import stupaq.labview.scripting.tools.activex.ActiveXScriptingTools;
import stupaq.vhdl2lv.DesignFileEmitter;
import stupaq.vhdl93.ast.design_file;

public class LVProject {
  private final ScriptingTools tools;
  private final Path root;

  public LVProject(Path root) {
    this.root = root;
    tools = new ActiveXScriptingTools();
  }

  public void update(design_file n) {
    n.accept(new DesignFileEmitter(this));
  }

  public VIPath allocate(InstanceName name, boolean override) {
    VIPath path = resolve(name);
    if (override) {
      try {
        Files.deleteIfExists(path.path());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return path;
  }

  public ScriptingTools tools() {
    return tools;
  }

  public VIPath resolve(InstanceName lvName) {
    return new VIPath(root, lvName.projectPathPart() + ".vi");
  }
}
