package com.poligorn.ikariaborders.rules;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.poligorn.ikariaborders.zone.ConfigLoader;
import com.poligorn.ikariaborders.zone.ZoneConfig;

/** Builds configs for rule tests. Zones are in the overworld. */
final class RulesTestSupport {
    static final String OW = "minecraft:overworld";

    private RulesTestSupport() {}

    static ZoneConfig config(String zonesJson) {
        ConfigLoader.Result r = ConfigLoader.parse("{\"version\":1,\"zones\":[" + zonesJson + "]}", null);
        assertTrue(r.ok(), () -> "config must be valid: " + r.errors());
        return r.config();
    }

    static final String WILD = "{\"id\":\"wild\",\"dimension\":\"minecraft:overworld\",\"default\":true,\"priority\":0,\"rules\":{\"pvp\":true}}";

    static String square(String id, int half, int priority, String parent, String rules) {
        return "{\"id\":\"" + id + "\",\"dimension\":\"minecraft:overworld\",\"priority\":" + priority
                + (parent == null ? "" : ",\"parent\":\"" + parent + "\"")
                + ",\"shape\":{\"type\":\"square\",\"center\":[0,0],\"half\":" + half + "},\"rules\":" + rules + "}";
    }
}
