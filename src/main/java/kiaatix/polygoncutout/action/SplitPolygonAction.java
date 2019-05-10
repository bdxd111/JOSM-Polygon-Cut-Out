package kiaatix.polygoncutout.action;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmUtils;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Shortcut;

import kiaatix.polygoncutout.BetterPolygonSplitter;
import kiaatix.polygoncutout.polygon.MultiPolygon;
import kiaatix.polygoncutout.util.Commands;
import kiaatix.polygoncutout.util.DataUtils;
import kiaatix.polygoncutout.util.QueryUtils;

public class SplitPolygonAction extends AreaAction {
	
	Logger logger = Logger.getLogger(SplitPolygonAction.class.getName());
	
	public SplitPolygonAction() {
		super("Split Polygon", "placeholder.png", "Split Polygon", Shortcut.registerShortcut("tools:AreaUtils:split", "Split", KeyEvent.VK_1, Shortcut.CTRL_SHIFT), false, true);
	}
	
	@Override
	public void actionPerformed(ActionEvent arg0, DataSet data) {
		split(data);
	}

	public void split(DataSet data) {
		
		// Get all polygons
		List<MultiPolygon> polygons = QueryUtils.getMultiPolygons(data);
		if (polygons.size() == 0) {
			showNoitifcation(tr("No polygons found in the current dataset."));
			return;
		}
		
		// Get the selected way
		List<Way> splitWays = QueryUtils.getSelectedWays(data);
		if (splitWays.size() == 0) {
			showNoitifcation(tr("Please select at least one way."));
			return;
		}
		
		logger.info("Found " + splitWays.size() + " selected ways");
		for (Way splitWay : splitWays) {
			splitAlongWay(data, polygons, splitWay);
		}
	}
	
	private void splitAlongWay(DataSet data, List<MultiPolygon> polygons, Way splitWay) {
		if (splitWay.getNodesCount() < 2) {
			showNoitifcation(tr("Selected way must contain at least 2 nodes"));
			return;
		}
		
		logger.info("Checking " + polygons.size() + " polygons for intersections");
		
		// Find candidate polygons. That is, any polygon the splitway starts and ends on.
		List<MultiPolygon> intersectionPolygonCandidates = DataUtils.getMultiPolygonsWithAllNodes(polygons, splitWay.firstNode(), splitWay.lastNode());
		if (intersectionPolygonCandidates.size() > 0) {
			logger.info("Found " + intersectionPolygonCandidates.size() + " intersecting candidate polygons");
		} else {
			showNoitifcation(tr("The selected line does not start and end on a polygon"));
			return;
		}
		
		// FIXME: Currently will only select 1 of the candidates.
		Node n0 = splitWay.getNode(0);
		Node n1 = splitWay.getNode(1);
		Node p = DataUtils.getCenter(n0, n1);
		
		MultiPolygon polygonToSplit = null;
		for (MultiPolygon polygon : intersectionPolygonCandidates) {
			if (polygon.isNodeInsidePolygon(p)) {
				polygonToSplit = polygon;
			}
		}
		
		if (polygonToSplit == null) {
			showNoitifcation(tr("The selected line does not fully cross a selected polygon."));
			return;
		}
		logger.info("Found polygon to split");
		
		doSplit(data, polygonToSplit, splitWay);
	}
	
	/**
	 * 
	 * @param data
	 * @param polygonToSplit
	 * @param splitWay
	 */
	private void doSplit(DataSet data, MultiPolygon polygonToSplit, Way splitWay) {
		// Do split
		Commands c = new Commands(data);
		BetterPolygonSplitter m = new BetterPolygonSplitter(data);
		List<MultiPolygon> newPolygons = m.splitMultiPolygon(polygonToSplit, splitWay.getNodes());
		
		logger.info("Original polygon was split into " + newPolygons.size() + " new polygons");
		// Add all new polygons
		for (MultiPolygon p : newPolygons) {
			c.addMultiPolygon(p);
		}
		
		// Delete old polygon
		if (polygonToSplit.hasRelation()) {
			c.removeRelation(polygonToSplit.getRelation());
		}
		for (Way oldWay : polygonToSplit) {
			boolean shouldDelete = true;
			for (MultiPolygon newPolygon : newPolygons) {
				for (Way newWay : newPolygon) {
					if (newWay.equals(oldWay)) {
						shouldDelete = false;
					}
				}
			}
			if (shouldDelete) {
				c.removeWay(oldWay);
			}
		}
		
		// Delete old way
		c.removeWay(splitWay);
		
		c.makeCommandSequence("Split Polygon");
	}
	
	@Override
	protected void updateEnabledState() {
		updateEnabledStateOnCurrentSelection();
	}
	
	@Override
	protected void updateEnabledState(Collection<? extends OsmPrimitive> selection) {
		setEnabled(OsmUtils.isOsmCollectionEditable(selection)
				&& selection.stream().anyMatch(o -> o instanceof Way && !o.isIncomplete()));
	}
	
//	private List<MultiPolygon> getMultiPolygonContainingNode(List<MultiPolygon> polygons, Node n) {
//		List<MultiPolygon> result = new ArrayList<MultiPolygon>();
//		for (MultiPolygon polygon : polygons) {
//			Iterator<Way> wayIterator = polygon.iterator();
//			while (wayIterator.hasNext()) {
//				Way way = wayIterator.next();
//				for (Node node : way.getNodes()) {
//					if (node.equals(n)) {
//						result.add(polygon);
//					}
//				}
//			}
//		}
//		return result;
//	}
//	
//	public void split() {
//		MainLayerManager layerManager = MainApplication.getLayerManager();
//		OsmDataLayer activeLayer = layerManager.getActiveDataLayer();
//		
//		if (activeLayer == null) {
//			return;
//		}
//		
//		DataSet data = activeLayer.data;
//		
//		// Get the selected multipolygon
//		List<MultiPolygon> selectedPolygons = QueryUtils.getSelectedMultiPolygons(data);
//		if (selectedPolygons.size() != 1) {
//			return;
//		}
//		MultiPolygon polygonToSplit = selectedPolygons.get(0);
//		
//		// Get the selected way
//		List<Way> selectedWays = QueryUtils.getSelectedWays(data);
//		if (selectedWays.size() != 1) {
//			return;
//		}
//		Way splitWay = selectedWays.get(0);
//		
//		doSplit(data, polygonToSplit, splitWay);
//	}


	private static final long serialVersionUID = -4447184748241029044L;
}
