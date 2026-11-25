package com.poligorn.ikariaborders.script;

import com.poligorn.ikariaborders.ZoneManager;
import com.poligorn.ikariaborders.api.IkariaBordersAPI;
import com.poligorn.ikariaborders.zone.Zone;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * The global {@code IkariaBorders} binding (TZ §21.3): a thin wrapper over {@link IkariaBordersAPI}.
 * No KubeJS types, so it can be unit-tested and never drags KubeJS into always-loaded code.
 * All methods must be called on the server thread. Query positions given to {@code zoneAt/zonesAt} are taken
 * as real world coordinates; {@code zoneOf(entity)} and {@code depth(entity)} project Sable ships first.
 */
public final class IkariaBordersJS {
    public static final IkariaBordersJS INSTANCE = new IkariaBordersJS();

    private IkariaBordersJS() {}

    public ZoneJS zoneAt(Level level, double x, double z) {
        ScriptRuntime.requireServerThread("IkariaBorders.zoneAt");
        return ZoneJS.of(IkariaBordersAPI.zoneAt(level, x, z).zone());
    }

    public ZoneJS zoneOf(Entity entity) {
        ScriptRuntime.requireServerThread("IkariaBorders.zoneOf");
        return ZoneJS.of(IkariaBordersAPI.zoneOf(entity).zone());
    }

    /** All zones at the point, highest priority first. */
    public List<ZoneJS> zonesAt(Level level, double x, double z) {
        ScriptRuntime.requireServerThread("IkariaBorders.zonesAt");
        List<ZoneJS> out = new ArrayList<>();
        for (Zone zone : ZoneManager.config().zonesAt(level.dimension().location().toString(), x, z)) out.add(ZoneJS.of(zone));
        return out;
    }

    /** Zone by id or null. */
    public ZoneJS zone(String id) {
        ScriptRuntime.requireServerThread("IkariaBorders.zone");
        Zone z = ZoneManager.config().zone(id);
        return z == null ? null : ZoneJS.of(z);
    }

    /** Zones of a dimension ("minecraft:overworld"), config order. */
    public List<ZoneJS> zones(String dimension) {
        ScriptRuntime.requireServerThread("IkariaBorders.zones");
        List<ZoneJS> out = new ArrayList<>();
        for (Zone z : ZoneManager.config().zones(dimension)) out.add(ZoneJS.of(z));
        return out;
    }

    public List<ZoneJS> zones(Level level) {
        return zones(level.dimension().location().toString());
    }

    /** Players currently in the zone (cache). */
    public List<ServerPlayer> playersIn(String id) {
        ScriptRuntime.requireServerThread("IkariaBorders.playersIn");
        return com.poligorn.ikariaborders.PlayerZoneTracker.playersIn(ScriptRuntime.server(), id);
    }

    /** Point inside the zone's own shape (priorities ignored). */
    public boolean isInside(String id, Level level, double x, double z) {
        ScriptRuntime.requireServerThread("IkariaBorders.isInside");
        return IkariaBordersAPI.isInside(id, level.dimension().location().toString(), x, z);
    }

    /** Blocks to the border of the zone's shape: positive inside, negative outside. Unknown id: error. */
    public double distanceToBorder(String id, double x, double z) {
        ScriptRuntime.requireServerThread("IkariaBorders.distanceToBorder");
        return IkariaBordersAPI.distanceToBorder(id, x, z);
    }

    /** Depth of an entity in its current zone (Sable-projected), see TZ §21.3. */
    public double depth(Entity entity) {
        ScriptRuntime.requireServerThread("IkariaBorders.depth");
        return IkariaBordersAPI.depth(entity);
    }

    public double depth(String id, double x, double z) {
        ScriptRuntime.requireServerThread("IkariaBorders.depth");
        return IkariaBordersAPI.depth(id, x, z);
    }

    /** Declares a custom rule flag, e.g. {@code registerFlag('ikaria:danger_mult', 1.0)}. */
    public void registerFlag(String name, Object defaultValue) {
        ScriptRuntime.requireServerThread("IkariaBorders.registerFlag");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("[ikaria_borders] имя флага не может быть пустым");
        FlagRegistry.register(name, defaultValue);
    }

    @Override public String toString() { return "IkariaBorders"; }
}
