package com.poligorn.ikariaborders.zone;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Parses and fully validates zones.json (TZ §8.3). Pure JVM.
 * On any error {@link Result#config()} is null so the caller keeps the previous config.
 */
public final class ConfigLoader {
    public static final int SUPPORTED_VERSION = 1;
    private static final int MAX_SHAPE_DEPTH = 16;
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_.\\-]+");
    private static final Pattern DIM = Pattern.compile("[a-z0-9_.\\-]+:[a-z0-9_./\\-]+");
    private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");

    public record Result(ZoneConfig config, List<ConfigIssue> errors, List<ConfigIssue> warnings) {
        public boolean ok() { return config != null; }
    }

    private final List<ConfigIssue> errors = new ArrayList<>();
    private final List<ConfigIssue> warnings = new ArrayList<>();
    private final Predicate<String> dimensionExists;
    private final Predicate<String> flagKnown; // null = do not check custom flags

    private ConfigLoader(Predicate<String> dimensionExists, Predicate<String> flagKnown) {
        this.dimensionExists = dimensionExists;
        this.flagKnown = flagKnown;
    }

    /** @param dimensionExists checks that a dimension id exists; null = only check syntax */
    public static Result parse(String json, Predicate<String> dimensionExists) {
        return parse(json, dimensionExists, null);
    }

    /**
     * @param flagKnown tells whether a custom flag ({@code rules.custom}) was declared via {@code registerFlag};
     *                  an unknown flag is only a warning. null = skip the check (flags are registered by scripts after start)
     */
    public static Result parse(String json, Predicate<String> dimensionExists, Predicate<String> flagKnown) {
        ConfigLoader l = new ConfigLoader(dimensionExists, flagKnown);
        ZoneConfig cfg = null;
        try {
            JsonElement root = JsonParser.parseString(json);
            cfg = l.root(root);
        } catch (RuntimeException e) { // JsonParseException and friends
            l.err("$", "некорректный JSON: " + e.getMessage());
        }
        if (!l.errors.isEmpty()) cfg = null;
        return new Result(cfg, List.copyOf(l.errors), List.copyOf(l.warnings));
    }

    private void err(String path, String msg) { errors.add(new ConfigIssue(path, msg)); }
    private void warn(String path, String msg) { warnings.add(new ConfigIssue(path, msg)); }

    private ZoneConfig root(JsonElement rootEl) {
        if (!rootEl.isJsonObject()) {
            err("$", "корень должен быть объектом");
            return null;
        }
        JsonObject root = rootEl.getAsJsonObject();

        JsonElement ver = root.get("version");
        if (ver == null) err("version", "обязательное поле");
        else if (!isInt(ver)) err("version", "должно быть целым числом");
        else if (ver.getAsInt() != SUPPORTED_VERSION) err("version", "поддерживается только версия " + SUPPORTED_VERSION + ", найдено " + ver.getAsInt());

        JsonObject globalRules = new JsonObject();
        GlobalSettings settings = GlobalSettings.DEFAULT;
        JsonElement g = root.get("global");
        if (g != null) {
            if (!g.isJsonObject()) err("global", "должно быть объектом");
            else {
                settings = globalSettings(g.getAsJsonObject());
                JsonElement gr = g.getAsJsonObject().get("rules");
                if (gr != null) {
                    if (!gr.isJsonObject()) err("global.rules", "должно быть объектом");
                    else {
                        validateRules(gr.getAsJsonObject(), "global.rules");
                        globalRules = gr.getAsJsonObject();
                    }
                }
            }
        }

        JsonElement zs = root.get("zones");
        if (zs == null) { err("zones", "обязательное поле"); return null; }
        if (!zs.isJsonArray()) { err("zones", "должно быть массивом"); return null; }
        JsonArray arr = zs.getAsJsonArray();

        List<Zone> zones = new ArrayList<>();
        Map<String, Integer> idIndex = new HashMap<>();
        Map<String, Integer> zoneIdx = new HashMap<>(); // id -> index in zones list
        Set<String> defaultDims = new HashSet<>();
        for (int i = 0; i < arr.size(); i++) {
            Zone z = zone(arr.get(i), i, idIndex, defaultDims);
            if (z != null) {
                zones.add(z);
                zoneIdx.put(z.id(), i);
            }
        }

        // parents exist
        for (int i = 0; i < arr.size(); i++) {
            JsonElement e = arr.get(i);
            if (!e.isJsonObject()) continue;
            JsonElement p = e.getAsJsonObject().get("parent");
            if (p != null && p.isJsonPrimitive() && p.getAsJsonPrimitive().isString()) {
                String pid = p.getAsString();
                if (!pid.equals("global") && !idIndex.containsKey(pid)) {
                    err("zones[" + i + "].parent", "родительская зона '" + pid + "' не найдена");
                }
            }
        }
        // parent cycles
        Map<String, String> parentOf = new HashMap<>();
        for (Zone z : zones) if (z.parentId() != null && !z.parentId().equals("global")) parentOf.put(z.id(), z.parentId());
        Set<String> reported = new HashSet<>();
        for (Zone z : zones) {
            List<String> path = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            String cur = z.id();
            while (cur != null && seen.add(cur)) {
                path.add(cur);
                cur = parentOf.get(cur);
            }
            if (cur != null && cur.equals(z.id()) && !reported.contains(z.id())) {
                path.add(cur);
                reported.addAll(path);
                err("zones[" + zoneIdx.get(z.id()) + "].parent", "цикл родителей: " + String.join(" → ", path));
            }
        }

        if (!errors.isEmpty()) return null;

        // normalise "global" parent to null
        List<Zone> finalZones = new ArrayList<>(zones.size());
        for (Zone z : zones) {
            finalZones.add("global".equals(z.parentId())
                    ? new Zone(z.id(), z.name(), z.dimension(), z.shape(), z.isDefault(), z.priority(), null,
                    z.ownRules(), z.mapOnly(), z.map(), z.meta(), z.index())
                    : z);
        }
        tieWarnings(finalZones, zoneIdx);
        return new ZoneConfig(globalRules, finalZones, settings, claimSignature(globalRules, arr));
    }

    /** Parses global.messages / global.exempt / global.sweep (TZ §8.2). Unknown keys are ignored. */
    private GlobalSettings globalSettings(JsonObject g) {
        String msg = GlobalSettings.DEFAULT_CLAIM_DENIED;
        boolean exemptServer = true;
        GlobalSettings.Sweep sw = GlobalSettings.Sweep.DEFAULT;

        JsonElement m = g.get("messages");
        if (m != null) {
            if (!m.isJsonObject()) err("global.messages", "должно быть объектом");
            else {
                String s = str(m.getAsJsonObject(), "claimDenied", "global.messages", false);
                if (s != null) msg = s;
            }
        }
        JsonElement ex = g.get("exempt");
        if (ex != null) {
            if (!ex.isJsonObject()) err("global.exempt", "должно быть объектом");
            else exemptServer = bool(ex.getAsJsonObject(), "serverClaims", "global.exempt", true);
        }
        JsonElement s = g.get("sweep");
        if (s != null) {
            if (!s.isJsonObject()) err("global.sweep", "должно быть объектом");
            else {
                JsonObject o = s.getAsJsonObject();
                boolean onStart = bool(o, "onStart", "global.sweep", sw.onStart());
                boolean dry = bool(o, "dryRun", "global.sweep", sw.dryRun());
                Integer interval = integer(o, "intervalMinutes", "global.sweep");
                Integer perTick = integer(o, "perTick", "global.sweep");
                if (interval != null && interval < 0) err("global.sweep.intervalMinutes", "должно быть >= 0 (0 — без периодической чистки)");
                if (perTick != null && perTick < 1) err("global.sweep.perTick", "должно быть >= 1");
                sw = new GlobalSettings.Sweep(onStart, interval == null ? sw.intervalMinutes() : interval,
                        perTick == null ? sw.perTick() : perTick, dry);
            }
        }
        return new GlobalSettings(msg, exemptServer, sw);
    }

    /** Stable text of everything that affects claim decisions; used to decide on a sweep after reload. */
    private static String claimSignature(JsonObject globalRules, JsonArray zones) {
        StringBuilder sb = new StringBuilder();
        JsonElement gc = globalRules.get("claims");
        sb.append("g=").append(gc == null ? "" : gc).append('\n');
        for (JsonElement e : zones) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            for (String k : new String[]{"id", "dimension", "default", "priority", "parent", "mapOnly", "shape"}) {
                sb.append(k).append('=').append(o.get(k)).append(';');
            }
            JsonElement r = o.get("rules");
            if (r != null && r.isJsonObject()) sb.append("claims=").append(r.getAsJsonObject().get("claims"));
            else sb.append("rules=").append(r);
            sb.append('\n');
        }
        return sb.toString();
    }

    private void tieWarnings(List<Zone> zones, Map<String, Integer> zoneIdx) {
        for (int a = 0; a < zones.size(); a++) {
            Zone za = zones.get(a);
            if (!za.isLookupCandidate()) continue;
            for (int b = a + 1; b < zones.size(); b++) {
                Zone zb = zones.get(b);
                if (!zb.isLookupCandidate() || !za.dimension().equals(zb.dimension())) continue;
                if (za.priority() == zb.priority() && za.bounds().intersects(zb.bounds())) {
                    warn("zones[" + zoneIdx.get(zb.id()) + "].priority", "приоритет " + zb.priority() + " совпадает с зоной '"
                            + za.id() + "' и области пересекаются; побеждает '" + za.id() + "' как объявленная выше");
                }
            }
        }
    }

    private Zone zone(JsonElement el, int i, Map<String, Integer> idIndex, Set<String> defaultDims) {
        String p = "zones[" + i + "]";
        if (!el.isJsonObject()) { err(p, "должно быть объектом"); return null; }
        JsonObject o = el.getAsJsonObject();
        int before = errors.size();

        String id = str(o, "id", p, true);
        if (id != null) {
            if (!ID.matcher(id).matches()) err(p + ".id", "допустимы только латинские буквы, цифры и _ . -");
            else if (id.equals("none") || id.equals("global")) err(p + ".id", "'" + id + "' зарезервировано");
            else if (idIndex.containsKey(id)) err(p + ".id", "id '" + id + "' уже используется в zones[" + idIndex.get(id) + "]");
            else idIndex.put(id, i);
        }
        String name = str(o, "name", p, false);
        String dim = str(o, "dimension", p, true);
        if (dim != null) {
            if (!DIM.matcher(dim).matches()) err(p + ".dimension", "некорректный id измерения '" + dim + "'");
            else if (dimensionExists != null && !dimensionExists.test(dim)) err(p + ".dimension", "измерение '" + dim + "' не существует");
        }
        boolean isDefault = bool(o, "default", p, false);
        boolean mapOnly = bool(o, "mapOnly", p, false);
        Integer priority = integer(o, "priority", p);
        String parent = str(o, "parent", p, false);

        Shape shape = null;
        JsonElement sh = o.get("shape");
        if (isDefault) {
            if (sh != null) err(p + ".shape", "у зоны по умолчанию формы быть не должно (она покрывает всё измерение)");
            if (dim != null && !defaultDims.add(dim)) err(p + ".default", "в измерении " + dim + " уже есть зона по умолчанию");
        } else if (sh == null) {
            err(p + ".shape", "обязательное поле (если зона не default)");
        } else {
            shape = shape(sh, p + ".shape", 0);
        }

        JsonObject rules = new JsonObject();
        JsonElement r = o.get("rules");
        if (r != null) {
            if (r.isJsonPrimitive() && r.getAsJsonPrimitive().isBoolean()) {
                if (!r.getAsBoolean()) mapOnly = true; // rules:false == map-only
                else err(p + ".rules", "допустимо false или объект правил");
            } else if (r.isJsonObject()) {
                rules = r.getAsJsonObject();
                validateRules(rules, p + ".rules");
            } else err(p + ".rules", "должно быть объектом или false");
        }

        Zone.MapStyle map = Zone.MapStyle.DEFAULT;
        JsonElement m = o.get("map");
        if (m != null) {
            if (!m.isJsonObject()) err(p + ".map", "должно быть объектом");
            else map = mapStyle(m.getAsJsonObject(), p + ".map");
        }
        JsonObject meta = new JsonObject();
        JsonElement me = o.get("meta");
        if (me != null) {
            if (!me.isJsonObject()) err(p + ".meta", "должно быть объектом");
            else meta = me.getAsJsonObject();
        }

        if (errors.size() > before) return null;
        return new Zone(id, name == null ? id : name, dim, shape, isDefault, priority == null ? 0 : priority,
                parent, rules, mapOnly, map, meta, i);
    }

    // ---- shapes ----

    private Shape shape(JsonElement el, String p, int depth) {
        if (depth > MAX_SHAPE_DEPTH) { err(p, "слишком глубокая вложенность форм"); return null; }
        if (!el.isJsonObject()) { err(p, "должно быть объектом"); return null; }
        JsonObject o = el.getAsJsonObject();
        String type = str(o, "type", p, true);
        if (type == null) return null;
        switch (type) {
            case "circle": {
                double[] c = point(o.get("center"), p + ".center", true);
                Double r = positive(o, "radius", p);
                return c == null || r == null ? null : new Shape.Circle(c[0], c[1], r);
            }
            case "square": {
                double[] c = point(o.get("center"), p + ".center", true);
                Double h = positive(o, "half", p);
                return c == null || h == null ? null : new Shape.Square(c[0], c[1], h);
            }
            case "rect": {
                double[] a = point(o.get("from"), p + ".from", true);
                double[] b = point(o.get("to"), p + ".to", true);
                return a == null || b == null ? null : Shape.Rect.of(a[0], a[1], b[0], b[1]);
            }
            case "polygon": {
                JsonElement pe = o.get("points");
                if (pe == null) { err(p + ".points", "обязательное поле"); return null; }
                if (!pe.isJsonArray()) { err(p + ".points", "должно быть массивом точек [[x,z],…]"); return null; }
                JsonArray pa = pe.getAsJsonArray();
                int n = pa.size();
                if (n < 3) err(p + ".points", "многоугольнику нужно минимум 3 точки, найдено " + n);
                double[] xs = new double[n], zs = new double[n];
                boolean ok = n >= 3;
                for (int i = 0; i < n; i++) {
                    double[] pt = point(pa.get(i), p + ".points[" + i + "]", true);
                    if (pt == null) ok = false;
                    else { xs[i] = pt[0]; zs[i] = pt[1]; }
                }
                if (!ok) return null;
                Shape.Polygon poly = new Shape.Polygon(xs, zs);
                if (poly.selfIntersects()) warn(p + ".points", "многоугольник самопересекается; используется правило even-odd");
                return poly;
            }
            case "union": {
                List<Shape> list = shapeList(o.get("shapes"), p + ".shapes", depth, true);
                return list == null ? null : new Shape.Union(list);
            }
            case "difference": {
                JsonElement be = o.get("base");
                Shape base = be == null ? null : shape(be, p + ".base", depth + 1);
                if (be == null) err(p + ".base", "обязательное поле");
                List<Shape> minus = shapeList(o.get("minus"), p + ".minus", depth, true);
                return base == null || minus == null ? null : new Shape.Difference(base, minus);
            }
            default:
                err(p + ".type", "неизвестная форма '" + type + "' (circle, square, rect, polygon, union, difference)");
                return null;
        }
    }

    private List<Shape> shapeList(JsonElement el, String p, int depth, boolean nonEmpty) {
        if (el == null) { err(p, "обязательное поле"); return null; }
        if (!el.isJsonArray()) { err(p, "должно быть массивом форм"); return null; }
        JsonArray a = el.getAsJsonArray();
        if (nonEmpty && a.isEmpty()) { err(p, "список не должен быть пустым"); return null; }
        List<Shape> out = new ArrayList<>();
        boolean ok = true;
        for (int i = 0; i < a.size(); i++) {
            Shape s = shape(a.get(i), p + "[" + i + "]", depth + 1);
            if (s == null) ok = false; else out.add(s);
        }
        return ok ? out : null;
    }

    private double[] point(JsonElement el, String p, boolean required) {
        if (el == null) { if (required) err(p, "обязательное поле"); return null; }
        if (!el.isJsonArray() || el.getAsJsonArray().size() != 2) { err(p, "должно быть [x, z]"); return null; }
        JsonArray a = el.getAsJsonArray();
        double[] out = new double[2];
        boolean ok = true;
        for (int i = 0; i < 2; i++) {
            Double d = number(a.get(i));
            if (d == null) { err(p + "[" + i + "]", "должно быть конечным числом"); ok = false; }
            else out[i] = d;
        }
        return ok ? out : null;
    }

    // ---- rules / map ----

    private void validateRules(JsonObject r, String p) {
        for (var e : r.entrySet()) {
            String k = e.getKey();
            JsonElement v = e.getValue();
            String kp = p + "." + k;
            switch (k) {
                case "pvp", "pvpPets", "fireSpread" -> needBool(v, kp);
                case "pvpCheck" -> needEnum(v, kp, "victim", "both");
                case "claims" -> boolGroup(v, kp, "allow", "forceload", "anchor");
                case "explosions" -> boolGroup(v, kp, "blockDamage", "entityDamage");
                case "mobs" -> boolGroup(v, kp, "removeHostileOnEnter");
                case "damage" -> {
                    if (!v.isJsonObject()) { err(kp, "должно быть объектом"); break; }
                    for (var d : v.getAsJsonObject().entrySet()) {
                        String dp = kp + "." + d.getKey();
                        switch (d.getKey()) {
                            case "deny" -> needStringArray(d.getValue(), dp);
                            case "targets" -> needEnum(d.getValue(), dp, "players", "all");
                            default -> warn(dp, "неизвестное правило");
                        }
                    }
                }
                case "spawn" -> {
                    if (!v.isJsonObject()) { err(kp, "должно быть объектом"); break; }
                    for (var d : v.getAsJsonObject().entrySet()) {
                        String dp = kp + "." + d.getKey();
                        switch (d.getKey()) {
                            case "categories" -> {
                                if (!d.getValue().isJsonObject()) err(dp, "должно быть объектом {категория: true|false}");
                                else for (var c : d.getValue().getAsJsonObject().entrySet()) needBool(c.getValue(), dp + "." + c.getKey());
                            }
                            case "reasons", "denyEntities", "allowEntities" -> needStringArray(d.getValue(), dp);
                            default -> warn(dp, "неизвестное правило");
                        }
                    }
                }
                case "custom" -> {
                    if (!v.isJsonObject()) { err(kp, "должно быть объектом {флаг: значение}"); break; }
                    for (var c : v.getAsJsonObject().entrySet()) {
                        String cp = kp + "." + c.getKey();
                        if (c.getValue().isJsonNull()) err(cp, "значение флага не может быть null");
                        else if (c.getKey().indexOf(':') < 0) warn(cp, "имя флага лучше писать с пространством имён, например 'ikaria:" + c.getKey() + "'");
                        else if (flagKnown != null && !flagKnown.test(c.getKey())) warn(cp, "неизвестный флаг (не объявлен через registerFlag); значение сохранено");
                    }
                }
                case "scripting" -> {
                    if (!v.isJsonObject()) { err(kp, "должно быть объектом"); break; }
                    for (var d : v.getAsJsonObject().entrySet()) {
                        String dp = kp + "." + d.getKey();
                        int min = switch (d.getKey()) {
                            case "playerTickInterval" -> 5;
                            case "zoneTickInterval" -> 20;
                            default -> -1;
                        };
                        if (min < 0) { warn(dp, "неизвестное правило"); continue; }
                        JsonElement dv = d.getValue();
                        if (!(dv.isJsonPrimitive() && dv.getAsJsonPrimitive().isNumber()) || dv.getAsDouble() != Math.rint(dv.getAsDouble())) {
                            err(dp, "должно быть целым числом (тиков)");
                        } else if (dv.getAsDouble() < min) {
                            warn(dp, "минимум " + min + " тиков; будет использовано " + min);
                        }
                    }
                }
                default -> warn(kp, "неизвестное правило (сохраняется как есть)");
            }
        }
    }

    private void boolGroup(JsonElement v, String p, String... keys) {
        if (!v.isJsonObject()) { err(p, "должно быть объектом"); return; }
        Set<String> known = Set.of(keys);
        for (var e : v.getAsJsonObject().entrySet()) {
            if (!known.contains(e.getKey())) warn(p + "." + e.getKey(), "неизвестное правило");
            else needBool(e.getValue(), p + "." + e.getKey());
        }
    }

    private void needBool(JsonElement v, String p) {
        if (!(v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean())) err(p, "должно быть true или false");
    }

    private void needEnum(JsonElement v, String p, String... allowed) {
        if (!(v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) || !List.of(allowed).contains(v.getAsString())) {
            err(p, "допустимые значения: " + String.join(", ", allowed));
        }
    }

    private void needStringArray(JsonElement v, String p) {
        if (!v.isJsonArray()) { err(p, "должно быть массивом строк"); return; }
        JsonArray a = v.getAsJsonArray();
        for (int i = 0; i < a.size(); i++) {
            JsonElement x = a.get(i);
            if (!(x.isJsonPrimitive() && x.getAsJsonPrimitive().isString())) err(p + "[" + i + "]", "должно быть строкой");
        }
    }

    private Zone.MapStyle mapStyle(JsonObject m, String p) {
        boolean show = bool(m, "show", p, false);
        String color = str(m, "color", p, false);
        if (color != null && !COLOR.matcher(color).matches()) err(p + ".color", "ожидается формат #RRGGBB");
        String style = str(m, "style", p, false);
        if (style != null && !List.of("outline", "fill", "fill+outline").contains(style)) err(p + ".style", "допустимо: outline, fill, fill+outline");
        double opacity = 0.6;
        JsonElement oe = m.get("opacity");
        if (oe != null) {
            Double d = number(oe);
            if (d == null || d < 0 || d > 1) err(p + ".opacity", "должно быть числом от 0 до 1");
            else opacity = d;
        }
        String side = str(m, "side", p, false);
        if (side != null && !List.of("inner", "outer").contains(side)) err(p + ".side", "допустимо: inner, outer");
        String label = str(m, "label", p, false);
        return new Zone.MapStyle(show, color, style == null ? "outline" : style, opacity, side == null ? "inner" : side, label);
    }

    // ---- primitives ----

    private String str(JsonObject o, String key, String p, boolean required) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            if (required) err(p + "." + key, "обязательное поле");
            return null;
        }
        if (!(e.isJsonPrimitive() && e.getAsJsonPrimitive().isString())) { err(p + "." + key, "должно быть строкой"); return null; }
        String s = e.getAsString();
        if (required && s.isBlank()) { err(p + "." + key, "не должно быть пустым"); return null; }
        return s;
    }

    private boolean bool(JsonObject o, String key, String p, boolean dflt) {
        JsonElement e = o.get(key);
        if (e == null) return dflt;
        if (!(e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean())) { err(p + "." + key, "должно быть true или false"); return dflt; }
        return e.getAsBoolean();
    }

    private Integer integer(JsonObject o, String key, String p) {
        JsonElement e = o.get(key);
        if (e == null) return null;
        if (!isInt(e)) { err(p + "." + key, "должно быть целым числом"); return null; }
        return e.getAsInt();
    }

    private Double positive(JsonObject o, String key, String p) {
        JsonElement e = o.get(key);
        if (e == null) { err(p + "." + key, "обязательное поле"); return null; }
        Double d = number(e);
        if (d == null) { err(p + "." + key, "должно быть числом"); return null; }
        if (d <= 0) { err(p + "." + key, "должно быть больше 0, найдено " + d); return null; }
        return d;
    }

    private static boolean isInt(JsonElement e) {
        Double d = number(e);
        return d != null && d == Math.rint(d) && Math.abs(d) <= Integer.MAX_VALUE;
    }

    private static Double number(JsonElement e) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return null;
        double d = e.getAsDouble();
        return Double.isFinite(d) ? d : null;
    }
}
