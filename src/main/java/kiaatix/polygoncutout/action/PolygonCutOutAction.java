package kiaatix.polygoncutout.action;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmUtils;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.Geometry.PolygonIntersection;
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
		// Allowed list of values for the 'natural' tag
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

		// Allowed list of values for the 'landuse'tag
		allowedTags.addTags("landuse", 
				"allotments", 
				"basin", 
				"brownfield", 
				"farmland",
				"flowerbed",
				"forest", 
				"grass", 
				"greenfield", 
				"greenhouse_horticulture",
				"meadow", 
				"orchard", 
				"plant_nursery", 
				"village_green", 
				"vineyard"
		);

		// Disallowed list of values for the 'landuse'tag. 
		// These are background polygons and are allowed to overlap with other polygons.
		disallowedTags.addTags("landuse",
				"commercial", 
				"construction",
				"education",
				"fairground",
				"industrial",
				"institutional",
				"military",
				"residential",
				"retail"
		);
		
		
		allowedTags.addTag("area", "yes");
        allowedTags.addTag("area:highway");

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

//			if (backgroundPolygon.canWayBeInnerWay(selectedMultiPolygon.getOuterWay())) {
//				doCreateMultiPolygon(data, selectedMultiPolygon, backgroundPolygon);
//			}
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
			// If the background polygon is contained within the foreground polygon
			// No multipolygon must be created.
			if (!foreground.canWayBeInnerWay(background.getOuterWay())) {
				doCreateMultiPolygon(data, c, foreground, background);
			}
		} else {
			
			// Add all new polygons
			for (MultiPolygon p : newPolygons) {
				c.addMultiPolygon(p);
			}
			
			
			if (background.hasRelation()) {
				c.removeRelation(background.getRelation());
			}

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
				Set<Relation> parentRelations = Way.getParentRelations(Collections.singleton(oldWay));
				if (parentRelations.size() > 0) {
					// Does oldWay have inner as role for all parent relations
					if (parentRelations.stream().allMatch(pr -> pr.getMembers().stream().anyMatch(rm -> rm.getMember() == oldWay && rm.hasRole("inner")))) {
						if (Geometry.polygonIntersection(oldWay.getNodes(), foreground.getOuterWay().getNodes()) == PolygonIntersection.SECOND_INSIDE_FIRST) {
							c.removeTags(oldWay);
						}
					}
					shouldDelete = false;
				}

				// If inner way and is itself a polygon
				if (background.isInner(oldWay) && oldWay.hasAreaTags()) {
					shouldDelete = false;
				}

				if (shouldDelete) {
					c.removeWay(oldWay);
				}
			}
		}

		c.makeCommandSequence("Cutout polygon");
	}

	private void doCreateMultiPolygon(DataSet data, Commands c, MultiPolygon inner, MultiPolygon outer) {
		
		if (outer.hasRelation()) {
			c.addInnerWayToPolygon(outer.getRelation(), inner.getOuterWay());
		} else {
			MultiPolygon multiPolygon = new MultiPolygon();
			multiPolygon.setOuter(outer.getOuterWay());
			multiPolygon.addInnerWay(inner.getOuterWay());
			multiPolygon.addTags(outer.getTags());
			
			c.addMultiPolygon(multiPolygon);
			
			Way w = new Way(outer.getOuterWay());
			w.removeAll();
			c.addCommand(new ChangeCommand(data, outer.getOuterWay(), w));
		}
	}
	
	private boolean hasValidTag(OsmPrimitive object) {
	    boolean hasDisallowedTags = false;
	    boolean hasAllowedTags = false;
		for (Entry<String, String> e : object.getKeys().entrySet()) {
            if (disallowedTags.contains(e.getKey(), e.getValue())) {
                hasDisallowedTags = true;
            }
            if (allowedTags.contains(e.getKey(), e.getValue())) {
                hasAllowedTags = true;
            }
		}
		return hasAllowedTags && !hasDisallowedTags;
	}

	@Override
	protected void updateEnabledState() {
		updateEnabledStateOnCurrentSelection();
	}

	@Override
	protected void updateEnabledState(Collection<? extends OsmPrimitive> selection) {
		setEnabled(OsmUtils.isOsmCollectionEditable(selection)
				&& selection.stream().anyMatch(o -> {
					if (o.isIncomplete()) {
						return false;
					}
					
					if (o instanceof Way) {
//						Way w = (Way) o;
//						if (w.isClosed()) {
							return true;
//						}
					}
					
					if (o instanceof Relation) {
						Relation r = (Relation) o;
						if (r.hasTag("type", "multipolygon")) {
							return true;
						}
					}
					
					return false;
				}));
	}

	private static final long serialVersionUID = -4666864264518649294L;
}
