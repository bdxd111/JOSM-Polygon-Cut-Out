package kiaatix.polygoncutout.action;

import java.awt.event.ActionEvent;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.tools.Shortcut;

public abstract class AreaAction extends JosmAction{

	public AreaAction(String name, String iconName, String tooltip, Shortcut shortcut) {
		this(name, iconName, tooltip, shortcut, false, true);
	}
	
	public AreaAction(String name, String iconName, String tooltip, Shortcut shortcut, boolean registerInToolbar, boolean installAdapters) {
		super(name, iconName, tooltip, shortcut, registerInToolbar, "", installAdapters);
	}

	
	@Override
	public void actionPerformed(ActionEvent arg0) {
		MainLayerManager layerManager = MainApplication.getLayerManager();
		OsmDataLayer activeLayer = layerManager.getActiveDataLayer();
		
		if (activeLayer == null) {
			System.err.println("No active layer");
			return;
		}
		
		DataSet data = activeLayer.data;
		actionPerformed(arg0, data);
	}

	protected abstract void actionPerformed(ActionEvent event, DataSet data);
}
