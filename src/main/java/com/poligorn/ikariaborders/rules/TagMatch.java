package com.poligorn.ikariaborders.rules;

import java.util.List;
import java.util.function.Predicate;

/** Matching of "id", "#tag" and "modid:*" masks (damage types, entity types). Pure JVM. */
public final class TagMatch {
    private TagMatch() {}

    /** Adds the {@code minecraft:} namespace to a bare id. */
    public static String normalize(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    /**
     * @param hasTag tests whether the subject belongs to the tag with the given id (without {@code #})
     */
    public static boolean matches(List<String> masks, String id, Predicate<String> hasTag) {
        for (String m : masks) {
            if (m.startsWith("#")) {
                if (hasTag != null && hasTag.test(normalize(m.substring(1)))) return true;
            } else if (m.equals("*")) {
                return true;
            } else if (m.endsWith(":*")) {
                if (id.startsWith(m.substring(0, m.length() - 1))) return true;
            } else if (normalize(m).equals(id)) {
                return true;
            }
        }
        return false;
    }
}
