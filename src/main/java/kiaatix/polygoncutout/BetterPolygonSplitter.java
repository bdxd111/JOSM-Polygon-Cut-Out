package kiaatix.polygoncutout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Geometry;

import kiaatix.polygoncutout.polygon.MultiPolygon;
import kiaatix.polygoncutout.polygon.MultiPolygonWay;
import kiaatix.polygoncutout.util.Commands;
import kiaatix.polygoncutout.util.DataUtils;

public class BetterPolygonSplitter {
	
	public static boolean debug_intersections = false;
	public static boolean debug_cuts = false;
	
	private static final Logger LOGGER = Logger.getLogger( BetterPolygonSplitter.class.getName() );
	
	private DataSet data;
	
	public BetterPolygonSplitter(DataSet data) {
		this.data = data;
	}
	
	
	/**
	 * 
	 * @param foreground
	 * @param background
	 * @param commands
	 * @return
	 */
	public List<MultiPolygon> cutOutPolygon(MultiPolygon foreground, MultiPolygon background, Commands commands) {

		// Split the background polygon along the lines of the foreground polygon
		// Get the list of the resulting polygons
		List<MultiPolygon> resultPolygons = splitPolygonAlongPolygon(foreground, background, commands);
		
		
		// Remove all polygons which are inside of the foreground polygon
		// Also remove all which are too small in size. Nessesary due to bugs in the splitting algorithm.
		if (!debug_cuts) {
			Iterator<MultiPolygon> iterator = resultPolygons.iterator();
			while (iterator.hasNext()) {
				MultiPolygon p = iterator.next();
				if (foreground.intersectsCutPolygon(p)) {
					iterator.remove();
					
				} 
//				else if (p.getArea() < 0.005) {
//					iterator.remove();
//				}
			}
		}
		
//		if (!debug_intersections) {
		// Remove left over temp tags on intersection nodes.
		// Should not happen but is nessesary due to bugs in the splitting algorithm not removing them.
			for (Way w : foreground) {
				for (Node n : w.getNodes()) {
					n.remove("temp");
				}
			}
			for (Way w : background) {
				for (Node n : w.getNodes()) {
					n.remove("temp");
				}
			}
//		}
		// Return the resulting polygons as a list
		return resultPolygons;
	}
	
	/**
	 * 
	 * @param foreground
	 * @param background
	 * @param commands
	 * @return
	 */
	public List<MultiPolygon> splitPolygonAlongPolygon(MultiPolygon foreground, MultiPolygon background, Commands commands) {
		
		// Intersect the foreground and background polygons.
		LOGGER.info("Intersecting the foreground polygon with the background polygon");
		new PolygonIntersector(data, commands).intersectPolygons(foreground, background);
		LOGGER.info("Done intersecting...");
		
		// Orient the multipolygons ways the correct way. 
		// The inner ways of the polygons are always oriented oposite of the outer way.
		// This is done because the algorithm only works if both ways are oriented in oposite directions.
		background.setClockwise(commands);
		foreground.setCounterClockwise(commands);
		
		// Start the actual splitting
		// Create the list of result polygons. 
		// Add the background polygon to it.
		List<MultiPolygon> resultPolygons = new ArrayList<MultiPolygon>();
		resultPolygons.add(background);
		
		// For debugging intersections...
		if (debug_intersections) return resultPolygons;
		
		// For each foreground polygon way:
		// Split the background polygon using that way
		for (Way foregroundWay : foreground) {	
			// FIXME intersection is on starting node of way. Will count twice as way is closed.
			// Should not happen in practice. But is nice if fixed anyway
			List<Integer> intersections = getIntersectionNodes(foregroundWay);
			LOGGER.info("Found " + intersections.size() + " intersections");
			
			// For each segment between intersection points...
			for (int i = 0; i < intersections.size(); i++) {
				int index0 = intersections.get(i);
				int index1 = intersections.get((i+1) % intersections.size());
				
				// Find all background polygons which this segment intersects..
				Set<MultiPolygon> intersectingBackgroundPolygons = new HashSet<MultiPolygon>();
				for (int j = index0; j != index1; j = (j+1) % (foregroundWay.getNodesCount()-1)) {
					
					int i0 = j % (foregroundWay.getNodesCount()-1);
					int i1 = (j+1) % (foregroundWay.getNodesCount()-1);
					
					Node testNode = DataUtils.getCenter(foregroundWay.getNode(i0), foregroundWay.getNode(i1));
					intersectingBackgroundPolygons.addAll(DataUtils.getMultiPolygonsWithNodeInside(resultPolygons, testNode));
				}
				
				// But not find polygons which are outside of the background polygon
				Iterator<MultiPolygon> iter = intersectingBackgroundPolygons.iterator();
				while (iter.hasNext()) {
					MultiPolygon intersectionCandidate = iter.next();
					if (!background.intersectsMultiPolygon(intersectionCandidate)) {
						iter.remove();
					}
				}
				
				// No intersecting polygons found, thus continue with next intersection.
				if (intersectingBackgroundPolygons.size() == 0) {
					LOGGER.info("Line segment does not intersect any other polygons");
					continue;
				}
				
				// For debugging purposes
				if (intersectingBackgroundPolygons.size() > 1) {
					LOGGER.info("Line segment intersects multiple polygons");
				}
				
				//
				LOGGER.info("Line segment intersects " + intersectingBackgroundPolygons.size() + " polygons");
				for (MultiPolygon intersectingBackgroundPolygon : intersectingBackgroundPolygons) {
					List<Node> splitWay = getWayNodesFromWay(foregroundWay, index0, index1);
					
					List<MultiPolygon> splitPolygon = splitMultiPolygon(intersectingBackgroundPolygon, splitWay);
					LOGGER.info("returned " + splitPolygon.size() + "polygons");
					
					// If there was a split
					if (splitPolygon.size() > 0) {
						resultPolygons.remove(intersectingBackgroundPolygon);
						resultPolygons.addAll(splitPolygon);
					}
				}
			}
		}
		return resultPolygons;
	}
	
	/**
	 * 
	 * @param foreground
	 * @param background
	 */
	public void fixIntersections(MultiPolygon foreground, MultiPolygon background) {
		Iterator<Way> foregroundIterator = foreground.iterator();
		while (foregroundIterator.hasNext()) {
			Way foregroundWay = foregroundIterator.next();
			
			for (int i = 0; i < foregroundWay.getNodesCount(); i++) {
				Node n = foregroundWay.getNode(i);
				if (n.hasKey("temp")) {
					Way backgroundWay = background.getWayFromNode(n).getWay();
					if (backgroundWay == null) {
						continue;
					}
					int j = DataUtils.getNodeIndex(backgroundWay, n);
					
					boolean b1 = backgroundWay.getNode((j+1) % (backgroundWay.getNodesCount()-1))
							.equals(foregroundWay.getNode((i+foregroundWay.getNodesCount()-1) % (foregroundWay.getNodesCount()-1)));
					
					boolean b2 = backgroundWay.getNode((j+backgroundWay.getNodesCount()-1) % (backgroundWay.getNodesCount()-1))
							.equals(foregroundWay.getNode((i+1) % (foregroundWay.getNodesCount()-1)));
					
					if (b1 & b2) {
						n.remove("temp");
					}
				}
			}
		}
	}
	
	
	
	
	public List<MultiPolygon> splitMultiPolygon(MultiPolygon polygon, List<Node> way) {
		List<MultiPolygon> resultingWays = new ArrayList<MultiPolygon>();
		
		// Way is not a proper intersection
		if (way.size() < 2) {
			LOGGER.warning("Split way has less than 2 nodes");
			return resultingWays;
		}
		
		if (polygon.containsAnyWaySegment(way)) {
			LOGGER.warning("Split way shares a segment with the polygon to split");
			return resultingWays;
		}
		
		// Get ways intersecting first and last node
		MultiPolygonWay way0 = polygon.getWayFromNode(way.get(0));
		MultiPolygonWay way1 = polygon.getWayFromNode(way.get(way.size()-1));
		
		// Way does not start or end on the polygon
		if (way0.getWay() == null || way1.getWay() == null) {
			LOGGER.warning("Split way does not start or end on the polygon to split");
			return resultingWays;
		}
		
		Node node0 = way.get(0);
		Node node1 = way.get(1);
		
		/*
		 * inner - inner way intersection
		 * 
		 * 
		 */
		if (way0.isInner() && way1.isInner()) {
			LOGGER.info("Splitting type: inner way -> inner way");
			// Way is outside of polygon to split
			// FIXME this does not work. seems to behave oposite of what is expected
			// FIXME Need better way of determining if way goes outside.
			if (polygon.containsNodeInWay(DataUtils.getCenter(node0, node1))) {
				LOGGER.warning("Split way does not cross polygon to split on the inside");
				return resultingWays;
			}
			
			if (way0.getWay().equals(way1.getWay())) {
				
				LOGGER.info("Splitting same inner way");
				List<Way> splitWays = splitWay(way0.getWay(), way);
				
				Way newInner = splitWays.get(0);
				Way newSection = splitWays.get(1);
				if (Geometry.computeArea(newInner) < Geometry.computeArea(newSection)) {
					Way temp = newInner;
					newInner = newSection;
					newSection = temp;
				}
				
				MultiPolygon p = new MultiPolygon(polygon.getOuterWay());
				p.copyTagsFrom(polygon);
				for (Way innerWay : polygon.getInnerWays()) {
					if (!innerWay.equals(way0.getWay())) {
						p.addInnerWay(innerWay);
					}
				}
				p.addInnerWay(newInner);
				resultingWays.add(p);
				
				MultiPolygon p1 = new MultiPolygon(newSection);
				p1.copyTagsFrom(polygon);
				resultingWays.add(p1);
				
				MultiPolygon p2 = new MultiPolygon(way0.getWay());
				p2.copyTagsFrom(way0.getWay());
				resultingWays.add(p2);
			} else {
				LOGGER.info("Splitting different inner ways");
				Way splitWay = splitMultiPolygon(way0.getWay(), way1.getWay(), way, true);
				
				MultiPolygon p = new MultiPolygon(polygon.getOuterWay());
				p.copyTagsFrom(polygon);
				for (Way innerWay : polygon.getInnerWays()) {
					if (!innerWay.equals(way0.getWay()) && !innerWay.equals(way1.getWay())) {
						p.addInnerWay(innerWay);
					}
				}
				p.addInnerWay(splitWay);
				resultingWays.add(p);

				MultiPolygon p1 = new MultiPolygon(way0.getWay());
				p1.copyTagsFrom(way0.getWay());
				resultingWays.add(p1);

				MultiPolygon p2 = new MultiPolygon(way1.getWay());
				p2.copyTagsFrom(way1.getWay());
				resultingWays.add(p2);
				
			}
			
		/* 
		 * outer - outer way intersection
		 * 
		 * Basically the same as the intersection between 2 normal polygons
		 */
		} else if (way0.isOuter() && way1.isOuter()) {
			LOGGER.info("Splitting type: outer way -> outer way");
			// Way is outside of polygon to split
			// FIXME this does not work. seems to behave oposite of what is expected
			// FIXME Need better way of determining if way goes inside.
			if (polygon.containsNodeInWay(DataUtils.getCenter(node0, node1))) {
				LOGGER.warning("Split way does not cross plygon to split on the inside");
				return resultingWays;
			}
			
			List<Way> splitWays = splitWay(way0.getWay(), way);
			for (Way splitWay : splitWays) {
				MultiPolygon p = new MultiPolygon(splitWay);
				p.copyTagsFrom(polygon);
				for (Way innerWay : polygon.getInnerWays()) {
					if (Geometry.nodeInsidePolygon(innerWay.firstNode(), splitWay.getNodes())) {
						p.addInnerWay(innerWay);
					}
				}
				resultingWays.add(p);
			}
		/*
		 * outer - inner way intersection
		 * 
		 * outer way now also contains inner way. Inner way is new polygon.
		 */
		} else {
			LOGGER.info("Splitting type: inner way -> outer way");
			Way innerWay;
			Way outerWay;
			if (way0.isInner()) {
				innerWay = way0.getWay();
				outerWay = way1.getWay();
			} else {
				innerWay = way1.getWay();
				outerWay = way0.getWay();
			}
			Way splitWay = splitMultiPolygon(innerWay, outerWay, way, false);
			
			MultiPolygon p0 = new MultiPolygon(splitWay);
			p0.copyTagsFrom(polygon);
			for (Way inner : polygon.getInnerWays()) {
				if (!inner.equals(innerWay)) {
					p0.addInnerWay(inner);
				}
			}
			resultingWays.add(p0);
			
			MultiPolygon p1 = new MultiPolygon(innerWay);
			p1.copyTagsFrom(innerWay);
			resultingWays.add(p1);
		}
		
		LOGGER.info("Polygon split done");
		
		return resultingWays;
	}
	
	private void validateMultiPolygons(List<MultiPolygon> polygons) {
		Iterator<MultiPolygon> iter = polygons.iterator();
		while (iter.hasNext()) {
			MultiPolygon poly = iter.next();
			if (!poly.hasInnerWays()) {
				if (doesWayCrossItself(poly.getOuterWay())) {
					iter.remove();
				}
			}
		}
	}
	
	private boolean doesWayCrossItself(Way way) {
		Set<Node> nodes = new HashSet<Node>();
		for (int i = 1; i < way.getNodesCount(); i++) {
			Node n = way.getNode(i);
			boolean newNode = nodes.add(n);
			if (!newNode) {
				return true;
			}
		}
		
		return false;
	}
	
	private Way splitMultiPolygon(Way inner, Way outer, List<Node> splitEdge, boolean bothInner) {
		Way resultWay = new Way();

		// Intersecting nodes
		Node n0 = splitEdge.get(0);
		Node n1 = splitEdge.get(splitEdge.size()-1);
		
		boolean innerToOuter = true;
		int innerIndex = DataUtils.getNodeIndex(inner, n0);
		int outerIndex = 0;
		if (innerIndex == -1) {
			innerIndex = DataUtils.getNodeIndex(inner, n1);
			outerIndex = DataUtils.getNodeIndex(outer, n0);
			innerToOuter = false;
		} else {
			outerIndex = DataUtils.getNodeIndex(outer, n1);
		}
		
		// loop outer
		for (int i = 0; i < outer.getNodesCount()-1; i++) {
			int index = (i+outerIndex) % (outer.getNodesCount()-1);
			resultWay.addNode(outer.getNode(index));
		}
		
		// go to inner
		if (innerToOuter) {
			for (int i = splitEdge.size() - 1; i > 0; i--) {
				resultWay.addNode(splitEdge.get(i));
			}
		} else {
			for (int i = 0; i < splitEdge.size() - 1; i++) {
				resultWay.addNode(splitEdge.get(i));
			}
		}
		
		// loop inner
		// FIXME should always go opposite direction as outer way
		if ((Geometry.isClockwise(inner) != Geometry.isClockwise(outer)) ^ bothInner) {
			for (int i = 0; i < inner.getNodesCount()-1; i++) {
				int index = (i+innerIndex) % (inner.getNodesCount()-1);
				resultWay.addNode(inner.getNode(index));
			}
		} else {
			for (int i = inner.getNodesCount()-1; i > 0; i--) {
				int index = (i+innerIndex) % (inner.getNodesCount()-1);
				resultWay.addNode(inner.getNode(index));
			}
		}
		
		
		// go to outer
		if (innerToOuter) {
			for (int i = 0; i < splitEdge.size() - 1; i++) {
				resultWay.addNode(splitEdge.get(i));
			}
		} else {
			for (int i = splitEdge.size() - 1; i > 0; i--) {
				resultWay.addNode(splitEdge.get(i));
			}
		}
		
		// close way
		resultWay.addNode(outer.getNode(outerIndex));
		
		// return way
		return resultWay;
	}
	
	/**
	 * Split a way and return a list containing the 2 resulting ways
	 * @param wayToSplit
	 * @param splitEdge
	 * @return
	 */
	private List<Way> splitWay(Way wayToSplit, List<Node> splitEdge) {
		LOGGER.info("splitting way on outside");
		// Intersecting nodes
		Node n0 = splitEdge.get(0);
		Node n1 = splitEdge.get(splitEdge.size()-1);
		
		// Index of intersecting nodes on the way to split
		int i0 = -1;
		int i1 = -1;
		
		for (int i = 0; i < wayToSplit.getNodesCount() - 1; i++) {
			if (wayToSplit.getNode(i).equals(n0)) {
				if (i0 >= 0) {
//					Node center = DataUtils.getCenter(splitEdge.get(0), splitEdge.get(1));
//					if (Geometry.nodeInsidePolygon(center, wayToSplit.getNodes())) {
//						i0 = i;
//					}
					EastNorth center = wayToSplit.getNode(i).getEastNorth();
					EastNorth p0 = wayToSplit.getNode(i - 1).getEastNorth().subtract(center);
					EastNorth p1 = wayToSplit.getNode(i + 1).getEastNorth().subtract(center);
					EastNorth e = splitEdge.get(1).getEastNorth().subtract(center);
					
					double dotPolygon = p0.getX() * p1.getX() + p0.getY() * p1.getY();
					double detPolygon = p0.getX() * p1.getY() - p0.getY() * p1.getX();
					double anglePolygon = Math.atan2(-detPolygon, -dotPolygon) + Math.PI;
					
					double dotWay = p0.getX() * e.getX() + p0.getY() * e.getY();
					double detWay = p0.getX() * e.getY() - p0.getY() * e.getX();
					double angleWay = Math.atan2(-detWay, -dotWay) + Math.PI;
					
					if (angleWay < anglePolygon) {
						i0 = i;
					}
				} else {
					i0 = i;
				}
			}
			if (wayToSplit.getNode(i).equals(n1)) {
				if (i1 >= 0) {
//					Node center = DataUtils.getCenter(splitEdge.get(splitEdge.size()-1), splitEdge.get(splitEdge.size()-2));
//					if (Geometry.nodeInsidePolygon(center, wayToSplit.getNodes())) {
////						i1 = i;
//					}
					EastNorth center = wayToSplit.getNode(i).getEastNorth();
					EastNorth p0 = wayToSplit.getNode(i - 1).getEastNorth().subtract(center);
					EastNorth p1 = wayToSplit.getNode(i + 1).getEastNorth().subtract(center);
					EastNorth e = splitEdge.get(splitEdge.size()-2).getEastNorth().subtract(center);
					
					double dotPolygon = p0.getX() * p1.getX() + p0.getY() * p1.getY();
					double detPolygon = p0.getX() * p1.getY() - p0.getY() * p1.getX();
					double anglePolygon = Math.atan2(-detPolygon, -dotPolygon) + Math.PI;
					
					double dotWay = p0.getX() * e.getX() + p0.getY() * e.getY();
					double detWay = p0.getX() * e.getY() - p0.getY() * e.getX();
					double angleWay = Math.atan2(-detWay, -dotWay) + Math.PI;
					
					if (angleWay < anglePolygon) {
						i0 = i;
					}
				} else {
					i1 = i;
				}
			}
		}

		// Find first way
		Way way0 = new Way();
		for (int i = i0; ; i = (i+1) % (wayToSplit.getNodesCount()-1)) {
			
			way0.addNode(wayToSplit.getNode(i));
			if (i == i1 % (wayToSplit.getNodesCount()-1)) {
				break;
			}
		}
		for (int i = splitEdge.size()-2; i >= 0; i--) {
			way0.addNode(splitEdge.get(i));
		}
		
		LOGGER.info("Find second way");
		// Find second way
		Way way1 = new Way();
		for (int i = 0; i < splitEdge.size() - 1; i++) {
			way1.addNode(splitEdge.get(i));
		}
		for (int i = i1; ; i = (i+1) % (wayToSplit.getNodesCount()-1)) {
			
			way1.addNode(wayToSplit.getNode(i));
			if (i == i0 % (wayToSplit.getNodesCount()-1)) {
				break;
			}
		}
		
		// Return the new ways as a list
		List<Way> splitWays = new ArrayList<Way>(2);
		splitWays.add(way0);
		splitWays.add(way1);
		
		for (Node n : wayToSplit.getNodes()) {
			if (!way0.containsNode(n) && !way1.containsNode(n)) {
				LOGGER.warning("Missing Node!");
			}
		}
		
		return splitWays;
	}
	
	/**
	 * Return the first polygon in the given list which contains the given node
	 * @param polygons
	 * @param n
	 * @return
	 */
	private MultiPolygon getPolygonFromNode(List<MultiPolygon> polygons, Node n) {
		for (MultiPolygon polygon : polygons) {
			Iterator<Way> wayIterator = polygon.iterator();
			while (wayIterator.hasNext()) {
				Way way = wayIterator.next();
				for (Node node : way.getNodes()) {
					if (node.equals(n)) {
						return polygon;
					}
				}
			}
		}
		throw new RuntimeException("Intersecting node is not in other polygon");
	}
	
	public List<Integer> getIntersectionNodes(Way way) {
		List<Integer> intersection = new ArrayList<Integer>();
		
		int x = 0;
		if (way.isClosed()) {
			x = 1;
		}
		
		for (int i = 0; i < way.getNodesCount() - x; i++) {
			Node n = way.getNode(i);
			if (n.hasKey("temp")) {
				intersection.add(i);
			}
		}
		return intersection;
	}
	
	/**
	 * Return the index of the next intersection node (node labeled 'temp') or -1 if no node found.
	 * @param way
	 * @param startIndex
	 * @return
	 */
	private int getNextIntersectionNode(Way way, int startIndex) {
		int n = -1;
		for (int i = startIndex; i < way.getNodesCount(); i++) {
			if (way.getNode(i).hasKey("temp")) {
				n = i;
				break;
			}
		}
		return n;
	}
	
	public List<Node> getWayNodesFromWay(Way way, int startIndex, int endIndex) {
		List<Node> nodes = new ArrayList<Node>();
		if (startIndex >= way.getNodesCount() || endIndex >= way.getNodesCount()) {
			return nodes;
		}
		
		LOGGER.info("splitting way from node " + startIndex + " to node " + endIndex);
		for (int i = 0; i < way.getNodesCount(); i++) {
//			int index = (i + startIndex) % (way.getNodesCount() - 1);
			int index = (i + startIndex) % (way.getNodesCount() - 1);
			
			Node n = way.getNode(index);
			
			// if previous node is the same as this node skip.
			// Prevent double nodes from happening.
//			boolean shouldAdd = true;
//			if (!nodes.isEmpty()) {
//				if (nodes.get(nodes.size()-1).equals(n)) {
//					shouldAdd = false;
//				}
//				if (index == way.getNodesCount()-1 && !(startIndex == index || endIndex == index)) {
//					shouldAdd = false;
//				}
//			}
//			
//			if (shouldAdd) {
				nodes.add(n);
				LOGGER.info("adding node: " + index);
//			}
			
			if (index == endIndex) {
				break;
			}
		}
		return nodes;
	}
}

