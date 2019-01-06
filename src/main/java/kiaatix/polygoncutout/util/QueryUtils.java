package kiaatix.polygoncutout.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import kiaatix.polygoncutout.polygon.MultiPolygon;

public class QueryUtils {

	private static final Logger LOGGER = Logger.getLogger( QueryUtils.class.getName() );
	
	public static List<Node> getSelectedNodes(DataSet data, Predicate<OsmPrimitive> predicate) {
		return data.getSelectedNodes().stream()
				.filter(predicate)
				.collect(Collectors.toList());
	}
	
	public static Node getSelectedNode(DataSet data, Predicate<OsmPrimitive> predicate) {
		List<Node> selectedNodes = getSelectedNodes(data, predicate);
		if (selectedNodes.size() == 0) {
			return null;
		}
		return selectedNodes.get(0);
	}
	
	/**
	 * Return a list of all selected ways in the given dataset
	 * @param data
	 * @return
	 */
	public static List<Way> getSelectedWays(DataSet data) {
		return getSelectedWays(data, p -> true);
	}
	
	/**
	 * Return a list of all selected ways in the given dataset which satisfy the given predicate.
	 * @param data
	 * @param predicate
	 * @return
	 */
	public static List<Way> getSelectedWays(DataSet data, Predicate<OsmPrimitive> predicate) {
		return data.getSelectedWays().stream()
				.filter(way -> !way.isClosed())
				.filter(predicate)
				.collect(Collectors.toList());
	}
	
	/**
	 * Return a single selected way or null if no way was selected.
	 * @param data
	 * @return
	 */
	public static Way getSelectedWay(DataSet data) {
		List<Way> selectedWays = getSelectedWays(data);
		if (selectedWays.size() == 0) {
			return null;
		}
		return selectedWays.get(0);
	}
	
	public static List<Way> getWays(DataSet data, Predicate<OsmPrimitive> predicate) {
		return data.getWays().stream()
				.filter(way -> !way.isClosed())
				.filter(predicate)
				.collect(Collectors.toList());
	}
	
	
	/*
	 * Get all multipolygons from data
	 */
	
	public static List<MultiPolygon> getMultiPolygons(DataSet data) {
		return getMultiPolygons(data, p -> true);
	}
	
	public static List<MultiPolygon> getMultiPolygons(DataSet data, Predicate<OsmPrimitive> predicate) {
		return getMultiPolygons(data.getWays(), data.getRelations(), predicate);
	}
	
	/*
	 * Get all selected multipolygons from data
	 */
	
	public static List<MultiPolygon> getSelectedMultiPolygons(DataSet data) {
		return getSelectedMultiPolygons(data, p -> true);
	}
	
	public static List<MultiPolygon> getSelectedMultiPolygons(DataSet data, Predicate<OsmPrimitive> predicate) {
		return getMultiPolygons(data.getSelectedWays(), data.getSelectedRelations(), predicate);
	}
	
	/*
	 * Get all unselected multipolygons from data
	 */
	
	public static List<MultiPolygon> getUnselectedMultiPolygons(DataSet data) {
		return getUnselectedMultiPolygons(data, p -> true);
	}
	
	public static List<MultiPolygon> getUnselectedMultiPolygons(DataSet data, Predicate<OsmPrimitive> predicate) {
		Collection<Way> unselectedWays = DataUtils.getDifference(data.getWays(), data.getSelectedWays());
		Collection<Relation> unselectedRelations = DataUtils.getDifference(data.getRelations(), data.getSelectedRelations());
		return getMultiPolygons(unselectedWays, unselectedRelations, predicate);
	}
	
	
	/*
	 * Get single selected multipolygon
	 */
	
	public static MultiPolygon getSelectedMultiPolygon(DataSet data) {
		List<MultiPolygon> selectedPolygons = getSelectedMultiPolygons(data);
		if (selectedPolygons.size() != 1) {
			return null;
		}
		return selectedPolygons.get(0);
	}
	
	public static List<Way> getWaysWithNode(DataSet data, Node n, Predicate<OsmPrimitive> predicate) {
		List<Way> foundWays = new ArrayList<Way>();
		List<Way> ways = getWays(data, predicate);
		
		for (Way w : ways) {
			if (w.containsNode(n)) {
				foundWays.add(w);
			}
		}
		
		return foundWays;
	}
	
	public static List<Node> getEdgesWithNode(DataSet data, Node n, Predicate<OsmPrimitive> predicate, boolean givenNodeAsFirstNode) {
		List<Way> ways = getWaysWithNode(data, n, predicate);
		
		List<Node> edges = new ArrayList<>();
		
		for (Way w : ways) {
			List<Node> wayNodes = w.getNodes();
			for (int i = 1; i < wayNodes.size(); i++) {
				Node n0 = wayNodes.get(i - 1);
				Node n1 = wayNodes.get(i);
				
				if (n0.equals(n)) {
					edges.add(n1);
				}
				
				if (n1.equals(n)) {
					edges.add(n0);
				}
				
			}
		}
		
		return edges;
	}
	
	
	
	private static List<MultiPolygon> getMultiPolygons(Collection<Way> ways, Collection<Relation> relations, Predicate<OsmPrimitive> predicate) {
		List<MultiPolygon> selectedMultiPolygons = new ArrayList<MultiPolygon>();
		
		
		List<Way> selectedWays = ways.stream()
				.filter(w -> !w.hasTag("highway") && w.isClosed())
				.filter(predicate)
				.collect(Collectors.toList());
		List<Relation> selectedRelations = relations.stream()
				.filter(predicate)
				.collect(Collectors.toList());
		
		// Remove also outer ways of the filtered relations.
		List<Relation> falseRelations = relations.stream()
				.filter(predicate.negate())
				.collect(Collectors.toList());
		
		for (Relation falseRelation : falseRelations) {
			for (RelationMember falseWay : falseRelation.getMembers()) {
				if (falseWay.hasRole("outer")) {
					selectedWays.remove(falseWay.getMember());
				}
			}
		}
		
		
		
		LOGGER.info("Found " + selectedRelations.size() + " relations and " + selectedWays.size() + " ways");
		Set<Way> outerPolygons = new HashSet<Way>();
		for (Relation relation : selectedRelations) {
			if (relation.isMultipolygon()) {
				MultiPolygon multiPolygon = new MultiPolygon();
				multiPolygon.copyTagsFrom(relation);
				for (RelationMember member : relation.getMembers()) {
					if (member.hasRole("inner")) {
						multiPolygon.addInnerWay(member.getWay());
					}
					if (member.hasRole("outer")) {
						if (multiPolygon.getOuterWay() != null) {
							System.err.println("Polygon already has an outer way");
						}
						multiPolygon.setOuter(member.getWay());
						outerPolygons.add(member.getWay());
					}
				}
				multiPolygon.setRelation(relation);
				if (multiPolygon.isValid()) {
					selectedMultiPolygons.add(multiPolygon);
				}
			}
		}
		
		for (Way way : selectedWays) {
			if (way.isClosed()) {
				if (!outerPolygons.contains(way)) {
					MultiPolygon multiPolygon = new MultiPolygon();
					multiPolygon.copyTagsFrom(way);
					multiPolygon.setOuter(way);
					// Should always be valid...
					if (multiPolygon.isValid()) {
						selectedMultiPolygons.add(multiPolygon);
					} else {
						System.err.println("Invalid Polygon");
					}
				} else {
					System.err.println("Invalid Polygon");
				}
			} else {
				System.err.println("Invalid Polygon");	
			}
		}
		LOGGER.info("Found " + selectedMultiPolygons.size() + " multipolygons");
		return selectedMultiPolygons;
	}
}
