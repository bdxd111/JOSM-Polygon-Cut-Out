package kiaatix.polygoncutout.util;

import java.util.Set;

import org.openstreetmap.josm.tools.MultiMap;

public class TagSet {

//	private Map<String, String[]> tagMap;
	MultiMap<String, String> tagMap;
	public TagSet() {
		tagMap = new MultiMap<>();
	}
	
	public void addTag(String key) {
		addTags(key, "");
	}
	
	public void addTag(String key, String value) {
		addTags(key, value);
	}
	
	public void addTags(String key, String... values) {
		for (String value : values) {
			tagMap.put(key, value);
		}
	}
	
	public boolean contains(String key) {
		return tagMap.containsKey(key);
	}
	
	public boolean contains(String key, String value) {
		Set<String> values = tagMap.get(key);
		if (values == null) {
			return false;
		}
		
		return values.contains(value);
	}
}
