package kiaatix.polygoncutout.action;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Shortcut;

import kiaatix.polygoncutout.util.Commands;
import kiaatix.polygoncutout.util.DataUtils;
import kiaatix.polygoncutout.util.QueryUtils;
import kiaatix.polygoncutout.util.Vector;

public class RoadCutoutAction extends AreaAction {

	public RoadCutoutAction() {
		super("Cut Out Road", "placeholder.png", "Cut Out Way in land", Shortcut.registerShortcut("tools:AreaUtils:Cut_Out_WWay", "Cut_Out_Way", KeyEvent.VK_5, Shortcut.CTRL_SHIFT), false, true);
	}

	@Override
	protected void actionPerformed(ActionEvent event, DataSet data) {
		List<Way> selectedWays = QueryUtils.getSelectedWays(data);
		Node n = QueryUtils.getSelectedNode(data, p -> true);
		
		
		data.clearSelection();
		
		for (Way w : selectedWays) {
			CutoutWay cutoutWay = new CutoutWay(w);
			cutoutWay.startNodePairs = QueryUtils.getEdgesWithNode(data, w.firstNode(), p -> p.hasTag("highway"), true);
			cutoutWay.startNodePairs.remove(w.getNode(1));
			cutoutWay.endNodePairs = QueryUtils.getEdgesWithNode(data, w.lastNode(), p -> p.hasTag("highway"), false);
			cutoutWay.endNodePairs.remove(w.getNode(w.getNodesCount()-2));
			
			if (n != null) {
				double distance = DataUtils.getDistanceFromWay(w, n);
				cutoutWay.width = distance;
			} else {
				cutoutWay.width = 4;
			}
			
			cutoutWay(data, cutoutWay);
		}
	}
	
	/**
	 * 
	 * @param data
	 * @param way
	 */
	private void cutoutWay(DataSet data, CutoutWay way) {
		
		List<Node> nodes = new ArrayList<Node>();
		
		// get nodes one way
		nodes.addAll(getNodes(way.way.getNodes(), way.width));
		
		// Get first node correct
		
		// Get nodes other way
		List<Node> wayCopy = way.way.getNodes().subList(0, way.way.getNodesCount());
		Collections.reverse(wayCopy);
		nodes.addAll(getNodes(wayCopy, way.width));
		
		
		
		Node n0 = getNodes(way.startNodePairs, false, way.way.getNode(0)                        , way.way.getNode(1)                        , nodes.get(0)                              , way.width);
		Node n1 = getNodes(way.endNodePairs  , true , way.way.getNode(way.way.getNodesCount()-2), way.way.getNode(way.way.getNodesCount()-1), nodes.get(way.way.getNodesCount()-1)      , way.width);
		Node n2 = getNodes(way.endNodePairs  , false, way.way.getNode(way.way.getNodesCount()-1), way.way.getNode(way.way.getNodesCount()-2), nodes.get(way.way.getNodesCount())        , way.width);
		Node n3 = getNodes(way.startNodePairs, true , way.way.getNode(1)                        , way.way.getNode(0)                        , nodes.get(way.way.getNodesCount() * 2 - 1), way.width);

		nodes.set(0, n0);
		nodes.set(way.way.getNodesCount()-1, n1);
		nodes.set(way.way.getNodesCount(), n2);
		nodes.set(way.way.getNodesCount()*2-1, n3);
		
		// Close way
		nodes.add(nodes.get(0));
		// Add all nodes
		Commands com = new Commands(data);
		Way newWay = new Way();
		
		
		newWay.setNodes(nodes);
		com.addWay(newWay);
		com.makeCommandSequence("way cutout");
		data.addSelected(newWay);
	}
	
	private Node getNodes(List<Node> adjecentEdgeNodes, boolean endOfWay, Node n0, Node n1, Node original, double width) {
		if (adjecentEdgeNodes.size() == 0) {
			return original;
		}
		
		Vector result = null;// = new Vector(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
		
		Vector edgeNormal0 = getNormal(n0, n1, width);
		for (Node n : adjecentEdgeNodes) {
			Vector edgeNormal1 = getNormal(n, n0, width);
			
			if (endOfWay) {
				edgeNormal1 = getNormal(n1, n, width);
			}
			
			Vector p1 = new Vector(n0).add(edgeNormal0);
			Vector p2 = new Vector(n1).add(edgeNormal0);
			Vector p3 = new Vector(n).add(edgeNormal1);
			Vector p4 = new Vector(n0).add(edgeNormal1);
			if (endOfWay) {
				p4 = new Vector(n1).add(edgeNormal1);
			}
			Vector p = intersection(p1, p2, p3, p4);
			
			if (result == null) {
				result = p;
			} else {
				if (endOfWay) {
					if (p.distance(n0) < result.distance(n0)) {
						result = p;
					}
				} else {
					if (p.distance(n1) < result.distance(n1)) {
						result = p;
					}
				}
			}
		}
		
		if (endOfWay) {
			if (new Vector(original).distance(n0) < result.distance(n0)) {
				return new Node(new EastNorth(result.x, result.y));
			}
		} else {
			if (new Vector(original).distance(n1) < result.distance(n1)) {
				return new Node(new EastNorth(result.x, result.y));
			}
		}
		
		return new Node(original.getEastNorth());
	}
	
	private List<Node> getNodes(List<Node> way, double width) {
		List<Node> nodes = new ArrayList<Node>();
		
//		for (int i = 1; i < way.size() - 1; i++) {
		int i = 1;
		do {
			Node n0, n1, n2, n3;
			
			if (way.size() > 2) {
				n0 = way.get(i - 1);
				n1 = way.get(i);
				n2 = way.get(i);
				n3 = way.get(i + 1);
			} else {
				n0 = way.get(i - 1);
				n1 = way.get(i);
				n2 = way.get(i - 1);
				n3 = way.get(i);
			}
//			double m_per_deg_lat = 111132.954 - 559.822 * Math.cos(2 * n1.lat()) + 1.175 * Math.cos(4 * n1.lat());
//			double m_per_deg_lon = 111132.954 * Math.cos(n1.lat());
			
			Vector edgeNormal0 = getNormal(n0, n1, width);
			Vector edgeNormal1 = getNormal(n2, n3, width);
			
			Vector p1 = new Vector(n0).add(edgeNormal0);
			Vector p2 = new Vector(n1).add(edgeNormal0);
			Vector p3 = new Vector(n2).add(edgeNormal1);
			Vector p4 = new Vector(n3).add(edgeNormal1);
			
			Vector p = intersection(p1, p2, p3, p4);

			if (way.size() > 2) {
				if (i == 1) {
//					Vector v = new Vector(n1).subtract(n0).normalize().scale(-width).add(p1);
					nodes.add(new Node(new EastNorth(p1.x, p1.y)));
				}
				
				nodes.add(new Node(new EastNorth(p.x, p.y)));
				
				if (i == way.size() - 2) {
					nodes.add(new Node(new EastNorth(p4.x, p4.y)));
				}
			} else {
				nodes.add(new Node(new EastNorth(p1.x, p1.y)));
				nodes.add(new Node(new EastNorth(p4.x, p4.y)));
			}
			i++;
		} while (i < way.size() - 1);
		
		return nodes;
	}
	
	private Vector intersection(Vector p1, Vector p2, Vector p3, Vector p4) {
		double a = p1.x * p2.y - p1.y * p2.x;
		double b = p3.x * p4.y - p3.y * p4.x;
		double c = (p1.x - p2.x) * (p3.y - p4.y) - (p1.y - p2.y) * (p3.x - p4.x);

		double px = (a * (p3.x - p4.x) - (p1.x - p2.x) * b) / c;
		double py = (a * (p3.y - p4.y) - (p1.y - p2.y) * b) / c;
		return new Vector(px, py);
	}
	
	private Vector getNormal(Node n0, Node n1, double width) {
		return new Vector(n1).subtract(n0).rotate90Degrees().normalize().scale(width);
	}
	
	
	private Node addNode(Vector v, String tag) {
		Node n = new Node(new EastNorth(v.x, v.y));
		n.put(tag, "");
		return n;		
	}
	
	public class CutoutWay {
		
		List<Node> startNodePairs;
		List<Node> endNodePairs;
		
		Way way;
		
		double width;
		
		public CutoutWay(Way way) {
			this.way = way;
			startNodePairs = new ArrayList<>();
			endNodePairs = new ArrayList<>();
		}
	}
	

	
}

