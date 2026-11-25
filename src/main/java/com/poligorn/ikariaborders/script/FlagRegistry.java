package com.poligorn.ikariaborders.script;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Custom rule flags declared by scripts via {@code IkariaBorders.registerFlag(name, default)} (TZ §21.5). Pure JVM. */
public final class FlagRegistry {
    private static final Map<String, Object> FLAGS = new ConcurrentHashMap<>();
    private static final Object NULL = new Object();

    private FlagRegistry() {}

    public static void register(String name, Object defaultValue) {
        FLAGS.put(name, defaultValue == null ? NULL : defaultValue);
    }

    public static boolean isKnown(String name) { return FLAGS.containsKey(name); }

    /** Registered default, or null (unknown or null default). */
    public static Object defaultOf(String name) {
        Object o = FLAGS.get(name);
        return o == NULL ? null : o;
    }

    public static void clear() { FLAGS.clear(); }

    public static Map<String, Object> all() { return Map.copyOf(FLAGS); }
}
