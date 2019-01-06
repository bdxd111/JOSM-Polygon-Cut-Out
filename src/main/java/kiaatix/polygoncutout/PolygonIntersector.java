package kiaatix.polygoncutout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Geometry;

import kiaatix.polygoncutout.polygon.MultiPolygon;
import kiaatix.polygoncutout.util.Commands;
import kiaatix.polygoncutout.util.DataUtils;

public class PolygonIntersector {

	private static final Logger LOGGER = Logger.getLogger( PolygonIntersector.class.getName() );
	
	Commands command;
	DataSet data;
	
	public PolygonIntersector(DataSet data, Commands command) {
		this.command = command;
		this.data = data;
	}
	
	
	/**
	 * Intersect the given polygons.
	 * 
	 * @param polygon0
	 * @param polygon1
	 */
	public void intersectPolygons(MultiPolygon polygon0, MultiPolygon polygon1) {
		
		// Merge overlapping nodes
		mergeOverlappingNodes(polygon0, polygon1);
		
		// Intersect the ways of the two polygons
		for (Way way0 : polygon0) {
			for (Way way1 : polygon1) {
				doWayWayIntersection(way1, way0);
			}
		}
		
		// Make a map of all edges to ways
		Map<String, Way> edges = new HashMap<String, Way>();
		data.getWays().forEach(way -> {
			if (way.isClosed()) {
				if (!polygon0.hasWay(way) && !polygon1.hasWay(way)) {
					List<Node> nodes = way.getNodes();
					for (int i = 0; i < nodes.size() - 1; i++) {
						edges.put(new WayEdge(nodes.get(i), nodes.get(i+1)).toString(), way);
					}
				}
			}
		});
		
		// Add intersection nodes to other adjacent ways
		LOGGER.fine("added all edges to map: " + edges.size() + " edges");
		addNodeAtIntersections(edges, polygon0);
		addNodeAtIntersections(edges, polygon1);
		
		
		
		// Remove duplicate intersections
		List<Node> removeTempFromNodes = new ArrayList<Node>();
		polygon0.iterator().forEachRemaining(way -> {
			for (int i = 0; i < way.getNodesCount(); i++) {
				Node n = way.getNode(i);
				if (!n.hasKey("temp")) {
					continue;
				}
				Way otherWay = polygon1.getWayFromNode(n).getWay();
				int index = DataUtils.getNodeIndex(otherWay, n);
				
				int way1NodeCount = way.getNodesCount()-1;
				int way2NodeCount = otherWay.getNodesCount()-1;
				Node n10 = way.getNode((i + way1NodeCount - 1) % way1NodeCount);
				Node n11 = way.getNode((i + way1NodeCount + 1) % way1NodeCount);
				Node n20 = otherWay.getNode((i + way2NodeCount - 1) % way2NodeCount);
				Node n21 = otherWay.getNode((i + way2NodeCount + 1) % way2NodeCount);
				
				if ((n10.equals(n20) && n11.equals(n21)) || (n10.equals(n21) && n11.equals(n20))) {
					removeTempFromNodes.add(n);
				}
			}
		});
		LOGGER.info("removing " + removeTempFromNodes.size() + " adjecent intersection nodes");
		for (Node n : removeTempFromNodes) {
			n.remove("temp");
		}
	}
	

	private void mergeOverlappingNodes(MultiPolygon foreground, MultiPolygon background) {
		for (Way w0 : background) {
			for (Node n0 : w0.getNodes()) {
				for (Way w : foreground) {
					for (int i = 0; i < w.getNodesCount(); i++) {
						Node n = w.getNode(i);
						if (n0.getEastNorth().distance(n.getEastNorth()) < 0.0001) {
							List<Node> nodes = w.getNodes();
							nodes.set(i, n0);
							command.setWayNodes(w, nodes);
						}
					}
				}
			}
		}
	}
	
	private void addNodeAtIntersections(Map<String, Way> edges, MultiPolygon polygon) {
		polygon.iterator().forEachRemaining(way -> {
			
			int startIndex = 0;
			
			while (startIndex < way.getNodesCount()) {
				// Find first node in segment
				Node n0 = way.getNode(startIndex);
				Node n1 = null;
				
				// Find second node in segment
				int endIndex = startIndex + 1;
				while (endIndex < way.getNodesCount()) {
					Node n = way.getNode(endIndex);
					if (!n.hasKey("temp")) {
						n1 = n;
						break;
					}
					endIndex++;
				}
				
				if (n1 == null) {
					break;
				}
				
				
				WayEdge edge = new WayEdge(n0, n1);
				Way neigborPolygon = edges.get(edge.toString());
				if (neigborPolygon != null) {
					
					int neighborStartIndex = neigborPolygon.getNodes().indexOf(n0);
					int neighborEndIndex = neigborPolygon.getNodes().indexOf(n1);
					
					
					if (neighborStartIndex == 0 && neighborEndIndex != 1) {
						neighborStartIndex = neigborPolygon.getNodesCount()-1;
					}
					if (neighborEndIndex == 0 && neighborStartIndex != 1) {
						neighborEndIndex = neigborPolygon.getNodesCount()-1;
					}
					
					if (neighborStartIndex > neighborEndIndex) {
						int temp = neighborStartIndex;
						neighborStartIndex = neighborEndIndex;
						neighborEndIndex = temp;
						
						int insertionIndex = neighborStartIndex + 1;
						for (int i = endIndex - 1; i > startIndex; i--) {
							command.addNodeToWay(neigborPolygon, way.getNode(i), insertionIndex);
							insertionIndex++;
						}
						
					} else {
					
						int insertionIndex = neighborStartIndex + 1;
						for (int i = startIndex + 1; i < endIndex; i++) {
							command.addNodeToWay(neigborPolygon, way.getNode(i), insertionIndex);
							insertionIndex++;
						}
					}
					
				}
				startIndex = endIndex;
			}
		});
	}
	
	private class WayEdge {
		Node n0;
		Node n1;
		
		WayEdge(Node n0, Node n1) {
			if (n0.getEastNorth().east() < n1.getEastNorth().east()) {
				this.n0 = n0;
				this.n1 = n1;
			} else if (n0.getEastNorth().east() > n1.getEastNorth().east()) {
				this.n0 = n1;
				this.n1 = n0;
			} else {
				if (n0.getEastNorth().north() < n1.getEastNorth().north()) {
					this.n0 = n0;
					this.n1 = n1;
				} else {
					this.n0 = n1;
					this.n1 = n0;
				}
			}
		}
		
		public String toString() {
			return n0 + " " + n1;
		}
	}
	
	/**
	 * Intersect the given ways.
	 * 
	 * @param existingWay
	 * @param newWay
	 * @return
	 */
	public void doWayWayIntersection(Way existingWay, Way newWay) {
		
		// Get all nodes of the old way as a new list
		List<Node> existingWayNodes = new ArrayList<Node>(existingWay.getNodes());
		
		// Old way nodes after
		Way w = new Way();
		w.addNode(existingWayNodes.get(0));
		
		// For each old way line segment...
		for (int i = 1; i < existingWayNodes.size(); i++) {
			Node n0 = existingWayNodes.get(i-1);
			Node n1 = existingWayNodes.get(i);

			// Find all intersection points of the line segment and new polygon
			// Intersection points are sorted
			List<Node> intersectionPoints = doLinePloygonIntersection(n0, n1, newWay);
			
			// If there are intersections
			if (!intersectionPoints.isEmpty()) {
				for (Node n : intersectionPoints) {
					w.addNode(n);
				}
			}
			w.addNode(n1);
		}
		
		command.setWayNodes(existingWay, w.getNodes());
	}
	
	/**
	 * Get a list of all intersection points of a given line and polygon.
	 * 
	 * Also add the intersection points to the given polygon.
	 * 
	 * @param p0
	 * @param p1
	 * @param polygon
	 * @return An ordered list of all intersection point on line p0,p1
	 */
	public List<Node> doLinePloygonIntersection(Node p0, Node p1, Way polygon) {
		List<Node> intersections = new ArrayList<Node>();
		
		// For each line segment in the given polygon...
		for (int i = 1; i < polygon.getNodes().size(); i++) {
			Node n0 = polygon.getNodes().get(i-1);
			Node n1 = polygon.getNodes().get(i);

//			// Fix double intersection of intersecting nodes at same position
//			List<Node> polygonNodes = polygon.getNodes();
//			if (p0.getEastNorth().distance(n0.getEastNorth()) < 0.001 && !p0.equals(n0)) {
//				polygonNodes.set(i, p0);
//				n0 = p0;
//				n0.put("temp", "temp");
//				command.setWayNodes(polygon, polygonNodes);
//			}
//			if (p0.getEastNorth().distance(n1.getEastNorth()) < 0.001 && !p0.equals(n1)) {
//				polygonNodes.set(i-1, p0);
//				n1 = p0;
//				n1.put("temp", "temp");
//				command.setWayNodes(polygon, polygonNodes);
//			}
//			if (p1.getEastNorth().distance(n0.getEastNorth()) < 0.001 && !p1.equals(n0)) {
//				polygonNodes.set(i, p1);
//				n0 = p1;
//				n0.put("temp", "temp");
//				command.setWayNodes(polygon, polygonNodes);
//			}
//			if (p1.getEastNorth().distance(n1.getEastNorth()) < 0.001 && !p1.equals(n1)) {
//				polygonNodes.set(i-1, p1);
//				n1 = p1;
//				n1.put("temp", "temp");
//				command.setWayNodes(polygon, polygonNodes);
//			}
			
			// Check for intersection of common nodes.
			boolean foundIntersection = false;
			if (p0.equals(n0) || p1.equals(n0)) {
				n0.put("temp", "temp");
				foundIntersection = true;
			}
			if (p0.equals(n1) || p1.equals(n1)) {
				n1.put("temp", "temp");
				foundIntersection = true;
			}
			if (foundIntersection) {
				continue;
			}
			
			// Intersect given line and polygon segment
			EastNorth intersection = Geometry.getSegmentSegmentIntersection(
					p0.getEastNorth(), p1.getEastNorth(), n0.getEastNorth(), n1.getEastNorth());
			
			// If an intersection exists, add that point to our list
			if (intersection != null) {
				
				Node n = null;
				float minDistance = 0.0001f;
				
				// Intersection at p0
				if (p0.getEastNorth().distance(intersection) < minDistance) {
					n = p0;
					p0.put("temp", "temp");
					command.addNodeToWay(polygon, p0, i);
					i++;
					
				// Intersection at p1
				} else if (p1.getEastNorth().distance(intersection) < minDistance) {
					n = p1;
					p1.put("temp", "temp");
					command.addNodeToWay(polygon, p1, i);
					i++;
					
				// Intersection at n0
				} else if (n0.getEastNorth().distance(intersection) < minDistance) {
					n = n0;
					n0.put("temp", "temp");
					intersections.add(n0);
					
				// Intersection at n1
				} else if (n1.getEastNorth().distance(intersection) < minDistance) {
					n = n1;
					n1.put("temp", "temp");
					intersections.add(n1);
					
				// Intersection not close to any point
				} else {
					n = new Node(intersection);
					n.put("temp", "temp");
					command.addNode(n);
					command.addNodeToWay(polygon, n, i);
					i++;
					intersections.add(n);
				}
			}
		}
		
		// Sort intersection list such that all intersection point follow a line from p0 to p1
		intersections.sort(new Comparator<Node>() {
			@Override
			public int compare(Node e1, Node e2) {
				double d1 = p0.getEastNorth().distanceSq(e1.getEastNorth());
				double d2 = p0.getEastNorth().distanceSq(e2.getEastNorth());
				if (d1 < d2) {
					return -1;
				}
				return 1;
			}
		});
		
		// Return list of all intersections
		return intersections;
	}
}
