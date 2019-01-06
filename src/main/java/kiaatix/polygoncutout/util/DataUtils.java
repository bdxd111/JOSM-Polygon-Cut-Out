package kiaatix.polygoncutout.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Geometry;

import kiaatix.polygoncutout.polygon.MultiPolygon;

public class DataUtils {

	public static void reverseWay(Commands command, Way way) {
//		List<Node> nodes = way.getNodes();
//		Collections.reverse(nodes);
//		way.setNodes(nodes);
		command.reverseWay(way);
	}
	
	public static Node getCenter(Node n0, Node n1) {
		return new Node(getCenter(n0.getEastNorth(), n1.getEastNorth()));
	}
	
	public static EastNorth getCenter(EastNorth p0, EastNorth p1) {
		double x = p0.getX() + 0.5 * (p1.getX() - p0.getX());
		double y = p0.getY() + 0.5 * (p1.getY() - p0.getY());
		return new EastNorth(x, y);
	}
	
	public static boolean isCenterOfEdgeInsidePolygon(Node n0, Node n1, List<Node> polygon) {
		Node p = new Node(getCenter(n0, n1));
		return Geometry.nodeInsidePolygon(p, polygon);
	}
	
	public static int getNodeIndex(Way way, Node node) {
		for (int i = 0; i < way.getNodes().size(); i++) {
			if (node.equals(way.getNodes().get(i))) {
				return i;
			}
		}
		return -1;
	}
	
	
	public static double getDistanceFromWay(Way way, Node node) {
		double distance = Double.POSITIVE_INFINITY;
		for (int i = 1; i < way.getNodesCount(); i++) {
			Node n0 = way.getNode(i-1);
			Node n1 = way.getNode(i);
			double d = distance(node, n0, n1);
			distance = Math.min(distance, d);
		}
		
		return distance;
	}
	
	private static double distance(Node p, Node a, Node b) {
		Vector pa = new Vector(p).subtract(a);
		Vector ba = new Vector(b).subtract(a);
		double h = Math.min(Math.max(pa.dot(ba) / ba.dot(ba), 0), 1);
		return pa.subtract(ba.scale(h)).length();
	}
	
	public static List<MultiPolygon> getMultiPolygonsWithNode(List<MultiPolygon> polygons, Node n) {
		List<MultiPolygon> result = new ArrayList<MultiPolygon>();
		for (MultiPolygon polygon : polygons) {
			if (polygon.containsNodeInWay(n)) {
				result.add(polygon);
			}
		}
		return result;
	}

	public static List<MultiPolygon> getMultiPolygonsWithAllNodes(List<MultiPolygon> polygons, Node...nodes) {
		return getMultiPolygonsWithAllNodes(polygons, Arrays.asList(nodes));
	}
	
	public static List<MultiPolygon> getMultiPolygonsWithAllNodes(List<MultiPolygon> polygons, List<Node> nodes) {
		List<MultiPolygon> result = new ArrayList<MultiPolygon>();
		
		if (nodes.size() == 0) {
			return result;
		}
		
		result.addAll(polygons);
		for (Node n : nodes) {
			List<MultiPolygon> candidatePolygons = getMultiPolygonsWithNode(polygons, n);
			result = getIntersection(candidatePolygons, result);
		}
		return result;
	}
	
	
	
	
	public static List<MultiPolygon> getMultiPolygonsWithNodeInside(List<MultiPolygon> polygons, Node n) {
		List<MultiPolygon> result = new ArrayList<MultiPolygon>();
		for (MultiPolygon polygon : polygons) {
			if (polygon.isNodeInsidePolygon(n)) {
				result.add(polygon);
			}
		}
		return result;
	}
	
	public static List<MultiPolygon> getMultiPolygonsWithAllNodesInside(List<MultiPolygon> polygons, Node...nodes) {
		return getMultiPolygonsWithAllNodesInside(polygons, Arrays.asList(nodes));
	}
	
	public static List<MultiPolygon> getMultiPolygonsWithAllNodesInside(List<MultiPolygon> polygons, List<Node> nodes) {
		List<MultiPolygon> result = new ArrayList<MultiPolygon>();
		
		if (nodes.size() == 0) {
			return result;
		}
		
		result.addAll(polygons);
		for (Node n : nodes) {
			List<MultiPolygon> candidatePolygons = getMultiPolygonsWithNodeInside(polygons, n);
			result = getIntersection(candidatePolygons, result);
		}
		return result;
	}
	
	
	
	public static <E> List<E> getIntersection(List<E> list0, List<E> list1) {
		List<E> intersection = new ArrayList<E>();
		for (E object : list0) {
			if (list1.contains(object)) {
				intersection.add(object);
			}
		}
		return intersection;
	}
	
	public static <E> List<E> getDifference(List<E> list0, List<E> list1) {
		List<E> difference = new ArrayList<E>();
		for (E object : list0) {
			if (!list1.contains(object)) {
				difference.add(object);
			}
		}
		return difference;
	}
	
	public static <E> Collection<E> getDifference(Collection<E> collection0, Collection<E> collection1) {
		return collection0.stream().filter(object -> !collection1.contains(object)).collect(Collectors.toList());
	}
}
