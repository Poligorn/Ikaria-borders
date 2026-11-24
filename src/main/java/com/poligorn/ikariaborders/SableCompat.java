package com.poligorn.ikariaborders;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Position;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Soft Sable integration (TZ §5.3) via reflection: the build and tests need no Sable jar.
 * Uses {@code Sable.HELPER.getContaining(Entity|Level,pos)} and {@code projectOutOfSubLevel(Level, Position)}.
 * Method signatures were taken from the TZ, NOT verified in game; any failure disables the hook
 * (logged once) and plain coordinates are used.
 */
public final class SableCompat {
    private static final Logger LOG = LogUtils.getLogger();

    /** Result of a projection. {@code onSubLevel=false} means coordinates are the original ones. */
    public record Projection(boolean onSubLevel, double x, double y, double z) {}

    private static boolean initDone;
    private static boolean available;
    private static Object helper;
    private static Method getContainingEntity; // (Entity)
    private static Method getContainingPos;    // (Level, Position)
    private static Method project;             // (Level, Position)

    private SableCompat() {}

    private static synchronized void init() {
        if (initDone) return;
        initDone = true;
        try {
            Class<?> sable = Class.forName("dev.ryanhcode.sable.Sable", true, SableCompat.class.getClassLoader());
            Field f = sable.getField("HELPER");
            helper = f.get(null);
            if (helper == null) throw new IllegalStateException("Sable.HELPER is null");
            Class<?> hc = helper.getClass();
            for (Method m : hc.getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getName().equals("getContaining")) {
                    if (p.length == 1 && p[0].isAssignableFrom(Entity.class)) getContainingEntity = m;
                    else if (p.length == 2 && p[0].isAssignableFrom(Level.class) && p[1].isAssignableFrom(Vec3.class)) getContainingPos = m;
                } else if (m.getName().equals("projectOutOfSubLevel") && p.length == 2
                        && p[0].isAssignableFrom(Level.class) && p[1].isAssignableFrom(Vec3.class)) {
                    project = m;
                }
            }
            available = project != null && (getContainingEntity != null || getContainingPos != null);
            if (available) LOG.info("[ikaria_borders] Sable обнаружен: проекция кораблей включена");
            else LOG.warn("[ikaria_borders] Sable найден, но нужные методы не найдены; проекция отключена");
        } catch (ClassNotFoundException e) {
            available = false; // Sable not installed: normal
        } catch (Throwable t) {
            available = false;
            LOG.warn("[ikaria_borders] Не удалось подключить Sable, проекция отключена: {}", t.toString());
        }
    }

    public static boolean isAvailable() {
        init();
        return available;
    }

    /** Position of the entity projected out of its Sable sub-level, if it is on one. */
    public static Projection project(Entity e) {
        Vec3 pos = e.position();
        init();
        if (!available) return plain(pos);
        try {
            Object sub = getContainingEntity != null
                    ? getContainingEntity.invoke(helper, e)
                    : getContainingPos.invoke(helper, e.level(), pos);
            if (sub == null) return plain(pos);
            return projected(e.level(), pos);
        } catch (Throwable t) {
            disable(t);
            return plain(pos);
        }
    }

    /** Same for an arbitrary level position (blocks, explosions, spawns). */
    public static Projection project(Level level, Vec3 pos) {
        init();
        if (!available) return plain(pos);
        try {
            if (getContainingPos != null && getContainingPos.invoke(helper, level, pos) == null) return plain(pos);
            return projected(level, pos);
        } catch (Throwable t) {
            disable(t);
            return plain(pos);
        }
    }

    private static Projection projected(Level level, Vec3 pos) throws ReflectiveOperationException {
        Object r = project.invoke(helper, level, pos);
        if (r instanceof Position p) return new Projection(true, p.x(), p.y(), p.z());
        return plain(pos);
    }

    private static Projection plain(Vec3 p) {
        return new Projection(false, p.x, p.y, p.z);
    }

    private static void disable(Throwable t) {
        available = false;
        LOG.warn("[ikaria_borders] Ошибка вызова Sable, проекция отключена до перезапуска: {}", t.toString());
    }
}
