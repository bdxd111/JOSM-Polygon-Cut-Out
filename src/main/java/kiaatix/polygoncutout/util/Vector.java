package kiaatix.polygoncutout.util;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Node;

public class Vector {
	public double x;
	public double y;
	
	public Vector(Node n) {
		x = n.getEastNorth().getX();
		y = n.getEastNorth().getY();
	}
	
	public Vector(Vector v) {
		x = v.x;
		y = v.y;
	}
	
	public Vector(double x, double y) {
		this.x = x;
		this.y = y;
	}
	
	public Vector(EastNorth n) {
		x = n.getX();
		y = n.getY();
	}
	
	public Vector subtract(Node n) {
		x -= n.getEastNorth().getX();
		y -= n.getEastNorth().getY();
		return this;
	}
	
	public Vector subtract(Vector v) {
		x -= v.x;
		y -= v.y;
		return this;
	}
	
	public Vector add(Node n) {
		x += n.getEastNorth().getX();
		y += n.getEastNorth().getY();
		return this;
	}
	
	public Vector add(Vector v) {
		x += v.x;
		y += v.y;
		return this;
	}
	
	public Vector scale(double s) {
		x *= s;
		y *= s;
		return this;
	}
	
	public Vector scale(double sx, double sy) {
		x *= sx;
		y *= sy;
		return this;
	}
	
	public Vector rotate90Degrees() {
		double temp = x;
		x = -y;
		y = temp;
		return this;
	}
	
	public Vector normalize() {
		double length = length();
		x /= length;
		y /= length;
		
		return this;
	}
	
	public double length() {
		return Math.sqrt(x * x + y * y);
	}
	
	public double distance(Vector v) {
		double dx = v.x - x;
		double dy = v.y - y;
		return Math.sqrt(dx * dx + dy * dy);
	}

	public double distance(Node n) {
		double dx = n.getEastNorth().getX() - x;
		double dy = n.getEastNorth().getY() - y;
		return Math.sqrt(dx * dx + dy * dy);
	}
	
	public double dot(Vector v) {
		return v.x * x + v.y * y;
	}
}
