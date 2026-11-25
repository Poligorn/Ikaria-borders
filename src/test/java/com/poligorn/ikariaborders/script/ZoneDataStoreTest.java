package com.poligorn.ikariaborders.script;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ZoneDataStoreTest {
    @Test void storesAndReadsValues() {
        ZoneDataStore s = new ZoneDataStore();
        assertTrue(s.set("wild", "infection", new JsonPrimitive(3)));
        assertTrue(s.set("wild", "name", new JsonPrimitive("x")));
        assertTrue(s.set("wild", "flag", new JsonPrimitive(true)));
        assertEquals(3, s.get("wild", "infection").getAsInt());
        assertEquals(List.of("infection", "name", "flag"), s.keys("wild"));
        assertTrue(s.remove("wild", "name"));
        assertFalse(s.has("wild", "name"));
        assertNull(s.get("build", "infection"), "zones do not share data");
    }

    @Test void rejectsOverSixteenKiBAndKeepsOldData() {
        ZoneDataStore s = new ZoneDataStore();
        assertTrue(s.set("wild", "small", new JsonPrimitive("ok")));
        String big = "a".repeat(ZoneDataStore.MAX_BYTES); // value alone is already over the limit once quoted
        assertFalse(s.set("wild", "big", new JsonPrimitive(big)));
        assertFalse(s.has("wild", "big"));
        assertEquals("ok", s.get("wild", "small").getAsString(), "old data intact");
    }

    @Test void limitIsPerZoneAndExactAtTheBoundary() {
        ZoneDataStore s = new ZoneDataStore();
        // {"k":"<n a's>"} = n + 8 bytes
        int fits = ZoneDataStore.MAX_BYTES - 8;
        assertTrue(s.set("a", "k", new JsonPrimitive("a".repeat(fits))));
        assertEquals(ZoneDataStore.MAX_BYTES, s.sizeBytes("a"));
        assertFalse(s.set("a", "k2", new JsonPrimitive("b")), "one more key does not fit");
        assertFalse(s.set("b", "k", new JsonPrimitive("a".repeat(fits + 1))), "one byte too many");
        assertTrue(s.set("b", "k", new JsonPrimitive("a".repeat(fits))), "zone b has its own budget");
    }

    @Test void overwritingAKeyCountsOnlyTheNewValue() {
        ZoneDataStore s = new ZoneDataStore();
        int fits = ZoneDataStore.MAX_BYTES - 8;
        assertTrue(s.set("a", "k", new JsonPrimitive("a".repeat(fits))));
        assertTrue(s.set("a", "k", new JsonPrimitive("b".repeat(fits))), "replace, not add");
    }

    @Test void sizeIsMeasuredInUtf8Bytes() {
        ZoneDataStore s = new ZoneDataStore();
        // Cyrillic letters are 2 bytes in UTF-8
        assertFalse(s.set("a", "k", new JsonPrimitive("ж".repeat(ZoneDataStore.MAX_BYTES / 2))));
        assertTrue(s.set("a", "k", new JsonPrimitive("ж".repeat(ZoneDataStore.MAX_BYTES / 2 - 8))));
    }

    @Test void persistsThroughJson() {
        ZoneDataStore a = new ZoneDataStore();
        JsonObject nested = new JsonObject();
        nested.addProperty("level", 2);
        a.set("wild", "state", nested);
        ZoneDataStore b = new ZoneDataStore();
        b.load(a.toJson());
        assertEquals(2, b.get("wild", "state").getAsJsonObject().get("level").getAsInt());
        b.load(null);
        assertFalse(b.has("wild", "state"));
    }

    @Test void changeCallbackFiresOnlyOnRealChanges() {
        ZoneDataStore s = new ZoneDataStore();
        int[] n = {0};
        s.onChange(() -> n[0]++);
        s.set("a", "k", new JsonPrimitive(1));
        s.set("a", "big", new JsonPrimitive("x".repeat(ZoneDataStore.MAX_BYTES)));
        s.remove("a", "nope");
        s.remove("a", "k");
        assertEquals(2, n[0]);
    }

    @Test void jsonConversionAcceptsSmallStructuresRejectsJunk() {
        assertEquals("[1,2]", JsonConv.toJson(List.of(1, 2)).toString());
        assertEquals("{\"a\":{\"b\":true}}", JsonConv.toJson(Map.of("a", Map.of("b", true))).toString());
        assertEquals("x", JsonConv.toJson(new StringBuilder("x")).getAsString(), "CharSequence (Rhino ConsString)");
        assertThrows(IllegalArgumentException.class, () -> JsonConv.toJson(null));
        assertThrows(IllegalArgumentException.class, () -> JsonConv.toJson(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> JsonConv.toJson(new Object()));
        Object deep = "x";
        for (int i = 0; i < 20; i++) deep = List.of(deep);
        Object tooDeep = deep;
        assertThrows(IllegalArgumentException.class, () -> JsonConv.toJson(tooDeep));
        JsonArray unused = new JsonArray();
        assertEquals(0, unused.size());
    }
}
