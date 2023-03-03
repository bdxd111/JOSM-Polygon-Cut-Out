package kiaatix.polygoncutout.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.tools.MultiMap;

public class TagSettings {
    public Set<String> allowedKeys = new HashSet<>();
    public Set<String> disAllowedKeys = new HashSet<>();
    public MultiMap<String, String> allowedTags = new MultiMap<>();
    public MultiMap<String, String> disAllowedTags = new MultiMap<>();
    
    /**
     * Allow a single key.
     * 
     * @param key
     */
    public void allowKey(String key) {
        allowedKeys.add(key);
    }
    
    /**
     * Disallow a single key
     * 
     * @param key
     */
    public void disAllowKey(String key) {
        disAllowedKeys.add(key);
    }
    
    /**
     * Allow a collection of keys
     * 
     * @param keys
     */
    public void allowKeys(Collection<String> keys) {
        allowedKeys.addAll(keys);
    }
    
    /**
     * Disallow a collection of keys
     * 
     * @param keys
     */
    public void disAllowKeys(Collection<String> keys) {
        disAllowedKeys.addAll(keys);
    }

    /**
     * Allow a single tag
     * @param key
     * @param value
     */
    public void allowTag(String key, String value) {
        allowedTags.put(key, value);
    }

    /**
     * Disallow a single tag
     * @param tag
     * @param value
     */
    public void disAllowTag(String tag, String value) {
        disAllowedTags.put(tag, value);
    }

    /**
     * Allow a set of tags with the same key and different values
     * @param key
     * @param values
     */
    public void allowTags(String key, Collection<String> values) {
        allowedTags.putAll(key, values);
    }

    /**
     * Disallow a set of tags with the same key and different values
     * @param key
     * @param values
     */
    public void disAllowTags(String tag, Collection<String> keys) {
        disAllowedTags.putAll(tag, keys);
    }

    /**
     * Check if a primitive is valid according to the settings in this class.
     * In case of a contradiction, i.e. the primitive is allowed for one or more tags, bus disallowed
     * for one or more other tags, disallowed is prioritized, so the result is not valid. 
     * 
     * @param primitive
     * @return
     */
    public boolean isValid(IPrimitive primitive) {
        return isAllowed(primitive) && ! isDissalowed(primitive);
    }

    /**
     * Check if a primitive is disallowed for at least 1 tag.
     * 
     * @param primitive
     * @return
     */
    private boolean isDissalowed(IPrimitive primitive) {
        for (Entry<String, String> entry : primitive.getKeys().entrySet()) {
            if (disAllowedKeys.contains(entry.getKey()) && 
                    !allowedTags.contains(entry.getKey(), entry.getValue())) {
                return true;
            }
            if (disAllowedTags.contains(entry.getKey(), entry.getValue())) return true;
        };
        return false;
    }

    /**
     * Check if a primitive is allowed for at least 1 tag.
     * 
     * @param primitive
     * @return
     */
    private boolean isAllowed(IPrimitive primitive) {
        for (Entry<String, String> entry : primitive.getKeys().entrySet()) {
            if (allowedKeys.contains(entry.getKey()) && 
                    !disAllowedTags.contains(entry.getKey(), entry.getValue())) {
                return true;
            }
            if (allowedTags.contains(entry.getKey(), entry.getValue())) return true;
        };
        return false;
    }
}
