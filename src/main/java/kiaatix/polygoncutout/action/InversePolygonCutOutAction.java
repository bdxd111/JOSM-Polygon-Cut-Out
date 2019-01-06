package kiaatix.polygoncutout.action;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.Set;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Shortcut;

import kiaatix.polygoncutout.BetterPolygonSplitter;
import kiaatix.polygoncutout.polygon.MultiPolygon;
import kiaatix.polygoncutout.util.Commands;
import kiaatix.polygoncutout.util.QueryUtils;

public class InversePolygonCutOutAction extends AreaAction {

	private static final Logger LOGGER = Logger.getLogger( InversePolygonCutOutAction.class.getName() );
	
	private static Set<String> allowedTags = new HashSet<String>();
	
	static {
		allowedTags.add("natural=wood");
		allowedTags.add("natural=scrub");
		allowedTags.add("natural=heath");
		allowedTags.add("natural=moor");
		allowedTags.add("natural=grassland");
		allowedTags.add("natural=fell");
		allowedTags.add("natural=bare_rock");
		allowedTags.add("natural=scree");
		allowedTags.add("natural=shingle");
		allowedTags.add("natural=sand");
		allowedTags.add("natural=mud");
		allowedTags.add("natural=wetland");
		allowedTags.add("natural=glacier");
		allowedTags.add("natural=beach");
					
		allowedTags.add("landuse=allotments");
		allowedTags.add("landuse=basin");
		allowedTags.add("landuse=brownfield");
		allowedTags.add("landuse=farmland");
		allowedTags.add("landuse=farmyard");
		allowedTags.add("landuse=forest");
		allowedTags.add("landuse=grass");
		allowedTags.add("landuse=greenfield");
		allowedTags.add("landuse=meadow");
		allowedTags.add("landuse=orchard");
		allowedTags.add("landuse=plant_nursery");
		allowedTags.add("landuse=village_green");
		allowedTags.add("landuse=vineyard");
	}
	
	public InversePolygonCutOutAction() {
		super("Inverse Cut Out Overlapping Polygons", "cutout-inv.png", "InverseCut Out Overlapping Polygons", Shortcut.registerShortcut("tools:AreaUtils:Cut_Out_Inverse", "Cut_Out_Inverse", KeyEvent.VK_4, Shortcut.CTRL_SHIFT), false, true);
	}
	
	@Override
	public void actionPerformed(ActionEvent e, DataSet data) {
		displace(data);
	}
	
	private void displace(DataSet data) {
		// Get all selected polygons as a list
		List<MultiPolygon> selectedPolygons = QueryUtils.getSelectedMultiPolygons(data);

		// For each selected polygon, do displace action
		for (MultiPolygon selectedMultiPolygon : selectedPolygons) {
		}
		
		// For each selected polygon, do displace action
		for (MultiPolygon selectedMultiPolygon : selectedPolygons) {
//			createMultiPolygon(data, selectedMultiPolygon);
			displace(data, selectedMultiPolygon);
		}
	}
	
	
	private void displace(DataSet data, MultiPolygon selectedMultiPolygon) {
		// Get all suitable background polygons
		List<MultiPolygon> backgroundPolygons = QueryUtils.getUnselectedMultiPolygons(data,
			p -> {
				boolean containsKey = false;
				for (Entry<String, String> e : p.getKeys().entrySet()) {
					if (allowedTags.contains(e.getKey() + "=" + e.getValue())) {
						return true;
					}
				}	
				return containsKey;
			}
		);	
		
		LOGGER.info("Intersecting " + backgroundPolygons.size() + " polygons");
		// For each background polygon...
		for (MultiPolygon backgroundPolygon : backgroundPolygons) {
			
			if (backgroundPolygon.canBeInnerWay(selectedMultiPolygon.getOuterWay())) {
//				doCreateMultiPolygon(data, selectedMultiPolygon, backgroundPolygon);
			}
			// If that background polygon intersects the selected polygon
			if (selectedMultiPolygon.intersectsMultiPolygon(backgroundPolygon)) {
				
				// If they do not share same outer way
				if (!selectedMultiPolygon.getOuterWay().equals(backgroundPolygon.getOuterWay())) {
					
					// Do displacement of the selected polygon and the background polygon
					doDisplacePolygon(data, selectedMultiPolygon, backgroundPolygon);
				}
			}
		}
	}
	

	
	private void doDisplacePolygon(DataSet data, MultiPolygon foreground, MultiPolygon background) {
		// Actually do the displacing and get a list of all new polygons.
		Commands c = new Commands(data);
		BetterPolygonSplitter m = new BetterPolygonSplitter(data);
		List<MultiPolygon> newPolygons = m.splitPolygonAlongPolygon(foreground, background, c);
		
		// Add all new polygons to the dataset
		for (MultiPolygon p : newPolygons) {
			if (p.intersectsMultiPolygon(foreground)) {
				p.setTags(foreground);
			}
			c.addMultiPolygon(p);
		}

		if (newPolygons.size() > 0) {
			if (background.hasRelation()) {
				c.removeRelation(background.getRelation());
			}
			
			Iterator<Way> oldWayIterator = background.iterator();
			while (oldWayIterator.hasNext()) {
				boolean shouldDelete = true;
				Way oldWay = oldWayIterator.next();
				for (MultiPolygon newPolygon : newPolygons) {
					Iterator<Way> newWayIterator = newPolygon.iterator();
					while (newWayIterator.hasNext()) {
						Way newWay = newWayIterator.next();
						if (newWay.equals(oldWay)) {
							shouldDelete = false;
						}
					}
				}
				if (shouldDelete) {
					c.removeWay(oldWay);
				}
			}
//			if (background.hasRelation()) {
//				m.removeRelation(background.getRelation());
//			}
//			background.iterator().forEachRemaining(way -> m.removeWay(way));
		}
		
		foreground.iterator().forEachRemaining(way -> way.getNodes().forEach(node -> node.remove("temp")));
		c.makeCommandSequence("Inverse cutout polygon");
	}
	
}
