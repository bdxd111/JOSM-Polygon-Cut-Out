package kiaatix.polygoncutout.polygon;

import java.util.Collection;
import java.util.HashSet;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;

public class MultiPolygonWay {

	private Way way;
	private boolean isInner;
	
	protected MultiPolygonWay(Way way, boolean isInner) {
		this.way = way;
		this.isInner = isInner;
	}
	
	public Way getWay() {
		return way;
	}
	
	public boolean isInner() {
		return isInner;
	}
	
	public boolean isOuter() {
		return !isInner;
	}
	
	public Collection<Node> getNodes() {
		return new HashSet<Node>(way.getNodes());
	}
}
