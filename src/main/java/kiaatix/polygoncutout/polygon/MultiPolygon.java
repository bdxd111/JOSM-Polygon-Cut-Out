package kiaatix.polygoncutout.polygon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.Geometry.PolygonIntersection;
import org.openstreetmap.josm.tools.Pair;

import kiaatix.polygoncutout.util.Commands;
import kiaatix.polygoncutout.util.DataUtils;

public class MultiPolygon implements Iterable<Way> {

	/** The outer way */
	private Way outerWay;
	
	/** All inner ways */
	private List<Way> innerWays;
	
	/** Tags of this polygon */
	private Map<String, String> tags;
	
	/** The relation of this polygon */
	private Relation relation;
	
	/**
	 * Create a new and empty multipolygon
	 */
	public MultiPolygon(Way outerWay) {
		this.outerWay = outerWay;
		innerWays = new ArrayList<Way>();
		tags = new HashMap<String, String>();
	}
	
	public MultiPolygon() {
		this((Way)null);
	}
	
	public MultiPolygon(MultiPolygon source) {
		outerWay = source.outerWay;
		innerWays = source.innerWays;
		tags = new HashMap<String, String>(source.tags);
	}
	
	public void setOuter(Way outerWay) {
		this.outerWay = outerWay;
	}
	
	public void setInner(List<Way> innerWays) {
		this.innerWays = innerWays;
	}
	
	public void addInnerWay(Way innerWay) {
		this.innerWays.add(innerWay);
	}
	
	public void setRelation(Relation relation) {
		// FIXME Refactor to remove label
		outerloop:
		for (RelationMember member : relation.getMembers()) {
			if (member.hasRole("outer")) {
				if (!member.getWay().equals(outerWay)) {
//					throw new RuntimeException("Failed to add relation to multiPolygon: outer way not in relation");
				}
			}
			
			if (member.hasRole("inner")) {
				for (Way innerWay : innerWays) {
					if (member.getWay().equals(innerWay)) {
						continue outerloop;
					}
				}
//				throw new RuntimeException("Failed to add relation to multiPolygon: inner way not in relation");
			}
		}
		
		this.relation = relation;
	}
	
	/**
	 * Get the outer way of this polygon.
	 * 
	 * @return The outer way of the polygon.
	 */
	public Way getOuterWay() {
		return outerWay;
	}
	
	/**
	 * Get all inner ways of this polygon. 
	 * 
	 * @return A list of all inner ways.
	 */
	public List<Way> getInnerWays() {
		return innerWays;
	}
	
	/**
	 * Get the relation associated with this polygon. This is only if there are inner polygons.
	 * 
	 * @return	The associated relation or null if no relation exists.
	 */
	public Relation getRelation() {
		return relation;
	}
	
	public boolean hasInnerWays() {
		return innerWays.size() > 0;
	}
	
	public boolean hasRelation() {
		return relation != null;
	}
	
	
	
	
	public void clearTags() {
		tags.clear();
	}
	
	public void addTag(String key, String value) {
		tags.put(key, value);
	}
	
	public void addTags(Map<String,String> tags) {
		tags.forEach((key,value) -> this.tags.put(key, value));
	}
	
	public void copyTagsFrom(MultiPolygon source) {
		addTags(source.tags);
	}
	
	public void copyTagsFrom(IPrimitive primitive) {
		addTags(primitive.getKeys());
	}

	public void setTags(MultiPolygon source) {
		clearTags();
		addTags(source.tags);
	}
	
	public void setTags(IPrimitive primitive) {
		clearTags();
		addTags(primitive.getKeys());
	}
	
	public Map<String,String> getTags() {
		return tags;
	}
	
	
	
	
	public void copyTagsFromOuterWay() {
		if (outerWay != null) {
			tags = outerWay.getKeys();
		}
	}
	
	
	
	public boolean isValid() {
		if (outerWay == null) {
			return false;
		}
		
		if (!outerWay.isClosed()) {
			return false;
		}
		
		for (Way inner : innerWays) {
			if (!inner.isClosed()) {
				return false;
			}
		}
		
		return true;
	}
	
	
	public List<MultiPolygon> getInnerWaysAsPolygons() {
		List<MultiPolygon> polygons = new ArrayList<MultiPolygon>();
		for (Way inner : innerWays) {
			MultiPolygon p = new MultiPolygon();
			p.outerWay = inner;
		}
		return polygons;
	}
	
	public boolean containsNodeInWay(Node n) {
		if (outerWay.containsNode(n)) {
			return true;
		}
		for (Way inner : innerWays) {
			if (inner.containsNode(n)) {
				return true;
			}
		}
		return false;
	}
	
	public boolean containsAnyWaySegment(List<Node> way) {
		List<Pair<Node, Node>> e = getNodePairs(true);
		for (int i = 0; i < way.size() - 1; i++) {
			Node n0 = way.get(i);
			Node n1 = way.get(i+1);
			Pair<Node, Node> edge = Pair.sort(new Pair<Node, Node>(n0, n1));
			if (e.contains(edge)) {
				return true;
			}
		}
		return false;
	}
	
//	public boolean containsAnyWaySegment(List<Pair<Node, Node>> edges) {
//		List<Pair<Node, Node>> e = getNodePairs(true);
//		for (Pair<Node, Node> edge : edges) {
//			if (e.contains(edge)) {
//				return true;
//			}
//		}
//		return false;
//	}
	
	public boolean containsWaySegment(Node n0, Node n1) {
		List<Pair<Node, Node>> edges = getNodePairs(true);
		Pair<Node, Node> edge = Pair.sort(new Pair<Node, Node>(n0, n1));
		return edges.contains(edge);
	}
	
	public List<Pair<Node, Node>> getNodePairs(boolean sort) {
		List<Pair<Node, Node>> edges = new ArrayList<>();
		for (Way way : this) {
			edges.addAll(way.getNodePairs(sort));
		}
		return edges;
	}
	
	public void setCounterClockwise(Commands command) {
		setClockwise(command, false);
	}
	
	public void setClockwise(Commands command) {
		setClockwise(command, true);
	}
	
	private void setClockwise(Commands command, boolean setClockwise) {
		if (Geometry.isClockwise(outerWay) != setClockwise) {
			DataUtils.reverseWay(command, outerWay);
			command.reverseWay(outerWay);
		}
		
		for (Way innerWay : innerWays) {
			if (Geometry.isClockwise(innerWay) == setClockwise) {
				command.reverseWay(innerWay);
			}
		}
	}

	public MultiPolygonWay getWayFromNode(Node node) {
		for (Node n : outerWay.getNodes()) {
			if (n.equals(node)) {
				MultiPolygonWay polygonWay = new MultiPolygonWay(outerWay, false);
				return polygonWay;
			}
		}
		
		for (Way innerWay : innerWays) {
			for (Node n : innerWay.getNodes()) {
				if (n.equals(node)) {
					MultiPolygonWay polgonWay = new MultiPolygonWay(innerWay, true);
					return polgonWay;
				}
			}
		}
		
		return new MultiPolygonWay(null, false);
	}
	
	public boolean IntersectsWay(Way way) {
		Iterator<Way> iter = iterator();
		while (iter.hasNext()) {
			Way w = iter.next();
			if (Geometry.polygonIntersection(way.getNodes(), w.getNodes()) == PolygonIntersection.CROSSING) {
				return true;
			}
		}
		return false;
	}
	
	public boolean intersectsMultiPolygon(MultiPolygon polygon) {
		PolygonIntersection outerWayIntersection = Geometry.polygonIntersection(outerWay.getNodes(), polygon.outerWay.getNodes());
		switch (outerWayIntersection) {
		case CROSSING:
			return true;
		case OUTSIDE:
			return false;
		case FIRST_INSIDE_SECOND:
			//this inside other
			for (Way inner : polygon.innerWays) {
				PolygonIntersection intersection = Geometry.polygonIntersection(outerWay.getNodes(), inner.getNodes());
				if (intersection == PolygonIntersection.FIRST_INSIDE_SECOND) {
					return false;
				}
			}
			return true;
		case SECOND_INSIDE_FIRST:
			// other inside this
			for (Way inner : innerWays) {
				PolygonIntersection intersection = Geometry.polygonIntersection(polygon.outerWay.getNodes(), inner.getNodes());
				if (intersection == PolygonIntersection.FIRST_INSIDE_SECOND) {
					return false;
				}
			}
			return true;
		}
		return false;
	}
	
	public boolean intersectsCutPolygon(MultiPolygon polygon) {
		PolygonIntersection outerWayIntersection = Geometry.polygonIntersection(outerWay.getNodes(), polygon.outerWay.getNodes());
		switch (outerWayIntersection) {
		case CROSSING:
			return true;
		case OUTSIDE:
			return false;
		case FIRST_INSIDE_SECOND:
			//this inside other
			for (Way inner : polygon.innerWays) {
				PolygonIntersection intersection = Geometry.polygonIntersection(outerWay.getNodes(), inner.getNodes());
				if (intersection == PolygonIntersection.FIRST_INSIDE_SECOND) {
					return false;
				}
				if (intersection == PolygonIntersection.CROSSING) {
					return false;
				}
			}
			return true;
		case SECOND_INSIDE_FIRST:
			// other inside this
			for (Way inner : innerWays) {
				PolygonIntersection intersection = Geometry.polygonIntersection(polygon.outerWay.getNodes(), inner.getNodes());
				if (intersection == PolygonIntersection.FIRST_INSIDE_SECOND) {
					return false;
				}
				if (intersection == PolygonIntersection.CROSSING) {
					return false;
				}
			}
			return true;
		}
		return false;
	}
	
	public boolean canWayBeInnerWay(Way way) {
		if (Geometry.polygonIntersection(outerWay.getNodes(), way.getNodes()) != PolygonIntersection.SECOND_INSIDE_FIRST) {
			return false;
		}
		
		for (Way inner : innerWays) {
			if (Geometry.polygonIntersection(inner.getNodes(), way.getNodes()) != PolygonIntersection.OUTSIDE) {
				return false;
			}
		}
		return true;
	}
	
	public boolean isNodeInsidePolygon(Node n) {
		if (outerWay == null) {
			return false;
		}
		
		if (!Geometry.nodeInsidePolygon(n, outerWay.getNodes())) {
			return false;
		}
		
		for (Way innerWay : innerWays) {
			if (Geometry.nodeInsidePolygon(n, innerWay.getNodes())) {
				return false;
			}
		}
		
		return true;
	}
	
	@Override
	public Iterator<Way> iterator() {
		return new Iterator<Way>() {

			boolean hasIteratedOuterWay = false;
			int n = 0;
			
			@Override
			public boolean hasNext() {
				return !hasIteratedOuterWay || innerWays.size() > n;
			}

			@Override
			public Way next() {
				if (!hasIteratedOuterWay) {
					hasIteratedOuterWay = true;
					return outerWay;
				} 
				
				if (n < innerWays.size()) {
					Way way = innerWays.get(n);
					n++;
					return way;
				}
				return null;
			}
			
		};
	}
	
	private int getNodeIndex(Way way, Node node) {
		
		for (int i = 0; i < way.getNodes().size(); i++) {
			if (way.getNodes().get(i).equals(node)) {
				return i;
			}
		}
		
		return -1;
	}
	
	public NodeIndex getNodeIndex(Node n) {
		if (outerWay != null) {
			int index = getNodeIndex(outerWay, n);
			if (index != -1) {
				NodeIndex ni = new NodeIndex(outerWay, index);
				return ni;
			}
		}
		
		for (Way inner : innerWays) {
			int index = getNodeIndex(inner, n);
			if (index != -1) {
				NodeIndex ni = new NodeIndex(inner, index);
				return ni;
			}
		}
		return null;
	}
	
	public class NodeIndex {
		
		private Way way;
		private int index;
		
		private NodeIndex(Way way, int index) {
			this.way = way;
			this.index = index;
		}
		
		public Way getWay() {
			return way;
		}
		
		public int getIndex() {
			return index;
		}
	}
	
	/**
	 * Get all nodes contained in this polygon. This includes nodes from both the inner and outer ways.
	 * 
	 * @return A set of all nodes in this polygon.
	 */
	public Set<Node> getNodes() {
		HashSet<Node> nodes = new HashSet<Node>();
		nodes.addAll(outerWay.getNodes());
		for (Way inner : innerWays) {
			nodes.addAll(inner.getNodes());
		}
		return nodes;
		
	}
	
	public boolean hasWay(Way way) {
		if (outerWay.equals(way)) {
			return true;
		}
		
		for (Way inner : innerWays) {
			if (inner.equals(way)) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Calculate the total area of this polygon.
	 * 
	 * @return	The area of this polygon in square meters
	 */
	public double getArea() {
		if (outerWay == null) {
			return 0;
		}
		double area = Geometry.computeArea(outerWay);
		for (Way inner : innerWays) {
			area -= Geometry.computeArea(inner);
		}
		return area;
	}
	
	public double getOuterArea() {
		if (outerWay == null) {
			return 0;
		}
		return Geometry.computeArea(outerWay);
	}
	
	public boolean equals(MultiPolygon polygon) {
		if (!polygon.outerWay.equals(outerWay)) {
			return false;
		}
		
		if (polygon.getInnerWays().size() != innerWays.size()) {
			return false;
		}
		
		for (Way innerWay : innerWays) {
			boolean foundMatch = false;
			for (Way otherInnerWay : polygon.innerWays) {
				if (innerWay.equals(otherInnerWay)) {
					foundMatch = true;
					break;
				}
			}
			if (!foundMatch) {
				return false;
			}
		}
		
		return true;
	}
	
	public void removeTag(String tag) {
		if (relation != null) {
			relation.remove(tag);
		}
		if (outerWay != null) {
			outerWay.remove(tag);
		}
	}
	
	public void addToSelection(DataSet data) {
		if (hasInnerWays()) {
			if (data.containsRelation(relation)) {
				data.addSelected(relation);
			}
		} else {
			if (data.containsWay(outerWay)) {
				data.addSelected(outerWay);
			}
		}
	}
 }
