package kiaatix.polygoncutout.action;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
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
		split2(data);
	}

	public void split2(DataSet data) {
		
		// Get all polygons
		List<MultiPolygon> polygons = QueryUtils.getMultiPolygons(data);
		if (polygons.size() == 0) {
			logger.warning("No polygons found in the current dataset.");
			return;
		}
		
		// Get the selected way
		List<Way> splitWays = QueryUtils.getSelectedWays(data);
		if (splitWays.size() == 0) {
			logger.warning("Please select at least one way.");
			return;
		}
		
		logger.info("Found " + splitWays.size() + " selected ways");
		for (Way splitWay : splitWays) {
			split3(data, polygons, splitWay);
		}
	}
	
	private void split3(DataSet data, List<MultiPolygon> polygons, Way splitWay) {
		if (splitWay.getNodesCount() < 2) {
			logger.warning("Selected way must contain at least 2 nodes");
			return;
		}
		
		logger.info("Checking " + polygons.size() + " polygons for intersections");
		
		
		List<MultiPolygon> intersectionPolygonCandidates = DataUtils.getMultiPolygonsWithAllNodes(polygons, splitWay.firstNode(), splitWay.lastNode());
		if (intersectionPolygonCandidates.size() > 0) {
			logger.info("Found " + intersectionPolygonCandidates.size() + " intersecting candidate polygons");
		} else {
			logger.warning("The selected line does not split any polygon");
			return;
		}
		
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
			logger.warning("Could not find suitable polygon to split");
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
		Iterator<Way> oldWayIterator = polygonToSplit.iterator();
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
		
		// Delete old way
		c.removeWay(splitWay);
		
		c.makeCommandSequence("Split Polygon");
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
}