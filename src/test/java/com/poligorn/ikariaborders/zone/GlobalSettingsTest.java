package com.poligorn.ikariaborders.zone;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GlobalSettingsTest {
    static String example() throws IOException {
        return new String(GlobalSettingsTest.class.getResourceAsStream("/ikaria_borders/zones.example.json").readAllBytes(), StandardCharsets.UTF_8);
    }

    static final String Z = "\"zones\":[{\"id\":\"w\",\"dimension\":\"minecraft:overworld\",\"default\":true}]";

    @Test void exampleSettings() throws IOException {
        GlobalSettings s = ConfigLoader.parse(example(), null).config().settings();
        assertEquals("В зоне «{zone}» приватить нельзя", s.claimDeniedMessage());
        assertTrue(s.exemptServerClaims());
        assertEquals(new GlobalSettings.Sweep(true, 60, 200, false), s.sweep());
    }

    @Test void defaultsWhenAbsent() {
        GlobalSettings s = ConfigLoader.parse("{\"version\":1," + Z + "}", null).config().settings();
        assertEquals(GlobalSettings.DEFAULT, s);
    }

    @Test void customValues() {
        String j = "{\"version\":1,\"global\":{\"messages\":{\"claimDenied\":\"нет\"},\"sweep\":{\"onStart\":false,\"intervalMinutes\":0,\"perTick\":50,\"dryRun\":true}}," + Z + "}";
        GlobalSettings s = ConfigLoader.parse(j, null).config().settings();
        assertEquals("нет", s.claimDeniedMessage());
        assertEquals(new GlobalSettings.Sweep(false, 0, 50, true), s.sweep());
    }

    @Test void errorsCarryFieldPath() {
        String j = "{\"version\":1,\"global\":{\"sweep\":{\"perTick\":0,\"intervalMinutes\":-1,\"dryRun\":\"yes\"},\"messages\":{\"claimDenied\":5},\"exempt\":3}," + Z + "}";
        ConfigLoader.Result r = ConfigLoader.parse(j, null);
        assertFalse(r.ok());
        var paths = r.errors().stream().map(ConfigIssue::toString).toList();
        assertTrue(paths.stream().anyMatch(p -> p.contains("global.sweep.perTick")), paths.toString());
        assertTrue(paths.stream().anyMatch(p -> p.contains("global.sweep.intervalMinutes")), paths.toString());
        assertTrue(paths.stream().anyMatch(p -> p.contains("global.sweep.dryRun")), paths.toString());
        assertTrue(paths.stream().anyMatch(p -> p.contains("global.messages.claimDenied")), paths.toString());
        assertTrue(paths.stream().anyMatch(p -> p.contains("global.exempt")), paths.toString());
    }

    @Test void claimSignatureTracksOnlyClaimRelevantChanges() throws IOException {
        String base = example();
        String sig = ConfigLoader.parse(base, null).config().claimSignature();
        assertFalse(sig.isEmpty());
        // cosmetic changes (name, map colour, sweep, messages) must not change the signature
        String cosmetic = base.replace("Зона застройки", "Другое имя").replace("#FF3030", "#00FF00")
                .replace("\"perTick\": 200", "\"perTick\": 10").replace("приватить нельзя", "нельзя");
        assertEquals(sig, ConfigLoader.parse(cosmetic, null).config().claimSignature());
        // a changed border or claims rule must
        String moved = base.replace("\"half\": 1500", "\"half\": 1600");
        assertNotEquals(sig, ConfigLoader.parse(moved, null).config().claimSignature());
        String rule = base.replace("\"claims\": { \"allow\": true, \"forceload\": true, \"anchor\": true }",
                "\"claims\": { \"allow\": true, \"forceload\": false, \"anchor\": true }");
        assertNotEquals(sig, ConfigLoader.parse(rule, null).config().claimSignature());
    }
}
