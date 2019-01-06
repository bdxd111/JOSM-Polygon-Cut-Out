package kiaatix.polygoncutout.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.ChangeNodesCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;

import kiaatix.polygoncutout.polygon.MultiPolygon;

public class Commands {

	private DataSet data;
	private List<Command> commands;
	
	public Commands(DataSet data) {
		this.data = data;
		this.commands = new ArrayList<Command>();
	}
	
	public void reverseWay(Way way) {
//		Way newWay = new Way()
		List<Node> nodes = way.getNodes();
		Collections.reverse(nodes);
		setWayNodes(way, nodes);
//		way.setNodes(nodes);
//		Command c = new ChangeNodesCommand(way, nodes);
//		executeCommand(c);
	}
	
	public void setWayNodes(Way way, List<Node> newNodes) {
		Command c = new ChangeNodesCommand(way, newNodes);
		executeCommand(c);
	}
	
	public void addMultiPolygon(MultiPolygon polygon) {
		if (polygon.hasInnerWays()) {
			Relation relation = new Relation();
			relation.put("type", "multipolygon");
			polygon.getTags().forEach((key, value) -> relation.put(key, value));
			addWay(polygon.getOuterWay());
			RelationMember outerMember = new RelationMember("outer", polygon.getOuterWay());
			relation.addMember(outerMember);
			
			for (Way innerWay : polygon.getInnerWays()) {
				addWay(innerWay);
				RelationMember innerMember = new RelationMember("inner", innerWay);
				relation.addMember(innerMember);
			}
			
			addRelation(relation);
			polygon.setRelation(relation);
		} else {
			Way outerWay = polygon.getOuterWay();
			polygon.getTags().entrySet().stream()
			.filter(entry -> !("type".equals(entry.getKey()) && "multipolygon".equals(entry.getValue())))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
			.forEach((key, value) -> outerWay.put(key, value));
			addWay(polygon.getOuterWay());
		}
	}
	
	public void addWay(Way way) {	
		
		if (data.containsWay(way)) {
			return;
		}
		// Make sure all nodes of the given way are contained in the dataset
		for (Node n : way.getNodes()) {
			if (!data.containsNode(n)) {
				addNode(n);
			}
		}
		
		// Add the way to the dataset
		Command c = new AddCommand(data, way);
		executeCommand(c);
	}
	
	public void addNode(Node n) {
		Command c = new AddCommand(data, n);
		executeCommand(c);
	}
	
	public void addRelation(Relation r) {
		Command c = new AddCommand(data, r);
		executeCommand(c);
	}
	
	public void addNodeToWay(Way way, Node n, int index) {
		List<Node> oldWayNodes = way.getNodes();
		if (way.isClosed() && index > way.getNodesCount()-1) {
			index = 1;
		}
		oldWayNodes.add(index, n);
		Command c = new ChangeNodesCommand(way, oldWayNodes);
		executeCommand(c);
	}
	
	public void removeNodeFromWay(Way way, int index)  {
		List<Node> oldWayNodes = way.getNodes();
		oldWayNodes.remove(index);
		Command c = new ChangeNodesCommand(way, oldWayNodes);
		executeCommand(c);
	}
	
	public void removeWay(Way way) {
		Command c = new DeleteCommand(data, way);
		executeCommand(c);
	}
	
	public void removeRelation(Relation relation) {
		Command c = new DeleteCommand(data, relation);
		executeCommand(c);
	}
	
	private void executeCommand(Command command) {
		UndoRedoHandler.getInstance().add(command);
		commands.add(command);
	}
	
	public void makeCommandSequence(String name) {
		if (commands.size() == 0) {
			return;
		}
		
		UndoRedoHandler.getInstance().undo(commands.size());
		SequenceCommand commandSequence = new SequenceCommand(name, commands);
		UndoRedoHandler.getInstance().add(commandSequence);
		commands.clear();
	}
}
