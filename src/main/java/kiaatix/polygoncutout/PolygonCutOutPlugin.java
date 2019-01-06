package kiaatix.polygoncutout;

import java.util.List;

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

    SplitPolygonAction splitPolygonAction = new SplitPolygonAction();
    MainMenu.add(MainApplication.getMenu().toolsMenu, splitPolygonAction);
    
    PolygonCutOutAction polygonCutOutAction = new PolygonCutOutAction();
    MainMenu.add(MainApplication.getMenu().toolsMenu, polygonCutOutAction);
    
    InversePolygonCutOutAction inverseCutAction = new InversePolygonCutOutAction();
    MainMenu.add(MainApplication.getMenu().toolsMenu, inverseCutAction);

//    RoadCutoutAction roadCutoutAction = new RoadCutoutAction();
//    MainMenu.add(MainApplication.getMenu().toolsMenu, roadCutoutAction);
    
  }

  @Override
  public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
    super.mapFrameInitialized(oldFrame, newFrame);
  }

  @Override
  public PluginInformation getPluginInformation() {
    // Supply an editor for the plugin preferences, if needed.
    return super.getPluginInformation();
  }

  @Override
  public void addDownloadSelection(List<DownloadSelection> list) {
    super.addDownloadSelection(list);
    // You can supply your own download method
  }
}
