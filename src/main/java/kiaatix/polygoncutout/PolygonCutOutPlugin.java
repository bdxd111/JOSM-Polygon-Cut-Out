package kiaatix.polygoncutout;

import java.util.List;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.download.DownloadSelection;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

import kiaatix.polygoncutout.action.InversePolygonCutOutAction;
import kiaatix.polygoncutout.action.PolygonCutOutAction;
import kiaatix.polygoncutout.action.SplitPolygonAction;

public class PolygonCutOutPlugin extends Plugin {
  public PolygonCutOutPlugin(PluginInformation info) {
    super(info);

    addAction(new SplitPolygonAction());
    addAction(new PolygonCutOutAction());
  }

  private void addAction(JosmAction action) {
	  MainMenu.add(MainApplication.getMenu().toolsMenu, action);
  }
  
  @Override
  public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
    super.mapFrameInitialized(oldFrame, newFrame);
  }

  @Override
  public PluginInformation getPluginInformation() {
    return super.getPluginInformation();
  }

  @Override
  public void addDownloadSelection(List<DownloadSelection> list) {
    super.addDownloadSelection(list);
  }
}
