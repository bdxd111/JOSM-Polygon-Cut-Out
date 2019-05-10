package kiaatix.polygoncutout.action;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmUtils;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Shortcut;

import kiaatix.polygoncutout.BetterPolygonSplitter;
import kiaatix.polygoncutout.polygon.MultiPolygon;
import kiaatix.polygoncutout.util.Commands;
import kiaatix.polygoncutout.util.QueryUtils;
import kiaatix.polygoncutout.util.TagSet;

public class PolygonCutOutAction extends AreaAction {

	private static final Logger LOGGER = Logger.getLogger(PolygonCutOutAction.class.getName());

	private static TagSet allowedTags = new TagSet();
	private static TagSet disallowedTags = new TagSet();

	static {
		allowedTags.addTags("natural", 
				"wood", 
				"scrub", 
				"heath", 
				"moor", 
				"grassland", 
				"fell", 
				"bare_rock", 
				"scree", 
				"shingle", 
				"sand", 
				"mud", 
				"water", 
				"wetland", 
				"glacier", 
				"beach"
		);

		allowedTags.addTags("landuse", 
				"allotments", 
				"basin", 
				"brownfield", 
				"farmland", 
				"forest", 
				"grass", 
				"greenfield", 
				"meadow", 
				"orchard", 
				"plant_nursery", 
				"village_green", 
				"vineyard"
		);
		
		allowedTags.addTag("area", "yes");

		disallowedTags.addTag("building");
		disallowedTags.addTag("boundary");
		disallowedTags.addTag("leisure");
		disallowedTags.addTag("man_made");
		
		disallowedTags.addTag("highway");
		disallowedTags.addTag("railway");
		disallowedTags.addTag("public_transport");
	}

	public PolygonCutOutAction() {
		super(tr("Cut Out Overlapping Polygons"), "cutout.png", tr("Cut Out Overlapping Polygons"),
				Shortcut.registerShortcut("tools:AreaUtils:Cut_Out", "Cut_Out", KeyEvent.VK_3, Shortcut.CTRL_SHIFT),
				false, true);
	}

	@Override
	public void actionPerformed(ActionEvent e, DataSet data) {
		displaceSelectedPolygons(data);
	}

	private void displaceSelectedPolygons(DataSet data) {
		// Get all selected polygons as a list
		List<MultiPolygon> selectedPolygons = QueryUtils.getSelectedMultiPolygons(data);

		if (selectedPolygons.size() == 0) {
			showNoitifcation(tr("No polygons selected"));
		}
		
		// For each selected polygon, do displace action
		for (MultiPolygon selectedMultiPolygon : selectedPolygons) {
			displacePolygon(data, selectedMultiPolygon);
		}
	}

	private void displacePolygon(DataSet data, MultiPolygon selectedMultiPolygon) {
		// Get all background polygons with allowed tags
		List<MultiPolygon> backgroundPolygons = QueryUtils.getUnselectedMultiPolygons(data, p -> {
			return hasValidTag(p);
		});

		LOGGER.info("Found " + backgroundPolygons.size() + " background polygon candidates");

		// For each background polygon...
		for (MultiPolygon backgroundPolygon : backgroundPolygons) {

			if (backgroundPolygon.canBeInnerWay(selectedMultiPolygon.getOuterWay())) {
//				doCreateMultiPolygon(data, selectedMultiPolygon, backgroundPolygon);
			}
			// If that background polygon intersects the selected polygon
			if (selectedMultiPolygon.intersectsMultiPolygon(backgroundPolygon)) {

				// If they do not share same outer way
				if (!selectedMultiPolygon.getOuterWay().equals(backgroundPolygon.getOuterWay())) {

					LOGGER.info("Found overlapping polygon");
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
		List<MultiPolygon> newPolygons = m.cutOutPolygon(foreground, background, c);

		LOGGER.info(
				"Split of background polygon done. Polygon was split into " + newPolygons.size() + " smaller polygons");

		// Add all new polygons to the dataset


		if (newPolygons.size() == 0) {
			
		} else {
			
			// Add all new polygons
			for (MultiPolygon p : newPolygons) {
				c.addMultiPolygon(p);
			}
			
			
			if (background.hasRelation()) {
				c.removeRelation(background.getRelation());
			}

			int i = 0;
			for (Way oldWay : background) {
				boolean shouldDelete = true;

				// If it is part of a new polygon do not delete
				for (MultiPolygon newPolygon : newPolygons) {
					for (Way newWay : newPolygon) {

						if (newWay.equals(oldWay)) {
							shouldDelete = false;
						}
					}
				}

				// If still part of other relations do not delete
				if (Way.getParentRelations(Collections.singleton(oldWay)).size() > 0) {
					shouldDelete = false;
				}

				// If inner way and is itself a polygon
				if (i > 0 && oldWay.hasAreaTags()) {
					shouldDelete = false;
				}

				if (shouldDelete) {
					c.removeWay(oldWay);
				}

				i++;
			}
		}

		c.makeCommandSequence("Cutout polygon");
	}

	private boolean hasValidTag(OsmPrimitive object) {

		for (Entry<String, String> e : object.getKeys().entrySet()) {
			if (disallowedTags.contains(e.getKey(), e.getValue()) || disallowedTags.contains(e.getKey())) {
				return false;
			}
			if (allowedTags.contains(e.getKey(), e.getValue()) || allowedTags.contains(e.getKey())) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void updateEnabledState() {
		updateEnabledStateOnCurrentSelection();
	}

	@Override
	protected void updateEnabledState(Collection<? extends OsmPrimitive> selection) {
		setEnabled(OsmUtils.isOsmCollectionEditable(selection)
				&& selection.stream().anyMatch(o -> (o instanceof Way || o instanceof Relation) && !o.isIncomplete()));
	}

	private static final long serialVersionUID = -4666864264518649294L;
}
