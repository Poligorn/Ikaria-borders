package com.poligorn.ikariaborders.script;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Optional;

/**
 * {@code mobSpawn.replace(entityId)} (TZ §21.2 / §21.4): the original spawn is cancelled, the replacement is
 * spawned on the next tick. At most {@value #MAX_PER_TICK} replacements per tick per server; extra requests return false.
 * Replacement spawns still go through FinalizeSpawnEvent (so built-in rules apply) but do not call scripts again.
 */
public final class ReplaceQueue {
    private static final Logger LOG = LogUtils.getLogger();
    public static final int MAX_PER_TICK = 20;

    private record Pending(ServerLevel level, EntityType<?> type, double x, double y, double z, float yRot,
                           MobSpawnType spawnType, int tick) {}

    private static final ArrayDeque<Pending> QUEUE = new ArrayDeque<>();
    private static int countTick = -1;
    private static int count;
    private static volatile boolean spawning;

    private ReplaceQueue() {}

    /** True while a replacement is being spawned: scripts must not see (and re-replace) it. */
    public static boolean isSpawning() { return spawning; }

    /** @return false if the id is unknown or the per-tick limit is exhausted (logged at most once a minute) */
    public static boolean request(ServerLevel level, String entityId, double x, double y, double z, float yRot,
                                  MobSpawnType spawnType, int serverTick) {
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        Optional<EntityType<?>> type = id == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (type.isEmpty()) {
            ScriptRuntime.warnThrottled("replace-bad:" + entityId, "mobSpawn.replace: неизвестный тип сущности '" + entityId + "'");
            return false;
        }
        if (countTick != serverTick) {
            countTick = serverTick;
            count = 0;
        }
        if (count >= MAX_PER_TICK) {
            ScriptRuntime.warnThrottled("replace-limit", "mobSpawn.replace: лимит " + MAX_PER_TICK + " подмен за тик исчерпан, подмена отклонена");
            return false;
        }
        count++;
        QUEUE.add(new Pending(level, type.get(), x, y, z, yRot, spawnType, serverTick));
        return true;
    }

    /** Spawns replacements queued on earlier ticks. */
    @SuppressWarnings("deprecation") // Mob#finalizeSpawn is what the event hook wraps
    public static void process(int serverTick) {
        while (!QUEUE.isEmpty() && QUEUE.peek().tick() < serverTick) {
            Pending p = QUEUE.poll();
            try {
                Entity ent = p.type().create(p.level());
                if (ent == null) continue;
                ent.moveTo(p.x(), p.y(), p.z(), p.yRot(), 0);
                if (ent instanceof Mob mob) {
                    DifficultyInstance diff = p.level().getCurrentDifficultyAt(mob.blockPosition());
                    FinalizeSpawnEvent ev = new FinalizeSpawnEvent(mob, p.level(), p.x(), p.y(), p.z(), diff, p.spawnType(), null, null);
                    spawning = true;
                    try {
                        NeoForge.EVENT_BUS.post(ev);
                    } finally {
                        spawning = false;
                    }
                    if (ev.isCanceled()) continue; // a built-in rule (or another mod) forbids it
                    mob.finalizeSpawn(p.level(), ev.getDifficulty(), ev.getSpawnType(), ev.getSpawnData());
                }
                p.level().addFreshEntityWithPassengers(ent);
            } catch (RuntimeException e) {
                LOG.error("[ikaria_borders/script] подмена моба {} не удалась: {}", p.type(), e.toString());
            }
        }
    }

    public static void clear() {
        QUEUE.clear();
        countTick = -1;
        count = 0;
    }
}
