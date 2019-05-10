package kiaatix.polygoncutout.action;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.tools.Shortcut;

public abstract class AreaAction extends JosmAction {

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
			showNoitifcation(tr("No active layer"));

			return;
		}
		
		DataSet data = activeLayer.data;
		actionPerformed(arg0, data);
	}

	protected void showNoitifcation(String message) {
			new Notification(message)
			.setIcon(JOptionPane.WARNING_MESSAGE)
			.show();
	}
	
	protected abstract void actionPerformed(ActionEvent event, DataSet data);
}
