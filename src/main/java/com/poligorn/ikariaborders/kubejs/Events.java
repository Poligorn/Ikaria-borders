package com.poligorn.ikariaborders.kubejs;

import com.poligorn.ikariaborders.api.ClaimActionType;
import com.poligorn.ikariaborders.script.ReplaceQueue;
import com.poligorn.ikariaborders.script.ScriptRuntime;
import com.poligorn.ikariaborders.script.ZoneJS;
import com.poligorn.ikariaborders.script.LoadedChunkIndex;
import com.poligorn.ikariaborders.zone.Zone;
import dev.latvian.mods.kubejs.core.LevelKJS;
import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.level.LevelBlock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** KubeJS event objects (TZ §21.2). Getters become properties in scripts: {@code e.player}, {@code e.zone}, ... */
final class Events {
    private Events() {}

    /** {@code ready}: server started, KubeJS /reload or /borders reload. */
    static final class Ready implements KubeEvent {
        private final MinecraftServer server;
        Ready(MinecraftServer server) { this.server = server; }
        public MinecraftServer getServer() { return server; }
    }

    /** {@code playerEnter} / {@code playerLeave}. {@code from}/{@code to} may be the "none" zone (login / no default zone). */
    static final class PlayerZone extends BaseZoneEvent {
        private final ServerPlayer player;
        private final Zone from, to;
        PlayerZone(String name, ServerPlayer player, Zone zone, Zone from, Zone to) {
            super(name, zone);
            this.player = player;
            this.from = from;
            this.to = to;
        }
        public ServerPlayer getPlayer() { return player; }
        public ZoneJS getFrom() { return ZoneJS.of(from); }
        public ZoneJS getTo() { return ZoneJS.of(to); }
    }

    static final class PlayerTick extends BaseZoneEvent {
        private final ServerPlayer player;
        private final double depth;
        PlayerTick(ServerPlayer player, Zone zone, double depth) {
            super("playerTick", zone);
            this.player = player;
            this.depth = depth;
        }
        public ServerPlayer getPlayer() { return player; }
        /** How far inside the zone the player is (TZ §21.3). */
        public double getDepth() { return depth; }
    }

    static final class ZoneTick extends BaseZoneEvent {
        private static final int DEFAULT_LIMIT = 64, MAX_LIMIT = 4096;
        private final ServerLevel level;
        private final List<ServerPlayer> players;
        ZoneTick(Zone zone, ServerLevel level, List<ServerPlayer> players) {
            super("zoneTick", zone);
            this.level = level;
            this.players = players;
        }
        public ServerLevel getLevel() { return level; }
        /** Players in the zone (tracker cache). */
        public List<ServerPlayer> getPlayers() { return players; }

        public void forEachLoadedChunk(Consumer<ChunkPos> fn) { forEachLoadedChunk(fn, DEFAULT_LIMIT); }

        /** Calls fn(ChunkPos) for at most {@code limit} loaded chunks of the zone; the next call continues where this one stopped. */
        public void forEachLoadedChunk(Consumer<ChunkPos> fn, int limit) {
            ScriptRuntime.requireServerThread("forEachLoadedChunk");
            ScriptRuntime.CHUNKS.forEach(zone.id(), Math.min(Math.max(limit, 0), MAX_LIMIT), (cx, cz) -> fn.accept(new ChunkPos(cx, cz)));
        }
    }

    static final class ChunkLoad extends BaseZoneEvent {
        private final ServerLevel level;
        private final ChunkPos chunkPos;
        /** Chunk was generated for the first time. */
        public final boolean isNew;
        ChunkLoad(Zone zone, ServerLevel level, ChunkPos pos, boolean isNew) {
            super("chunkLoad", zone);
            this.level = level;
            this.chunkPos = pos;
            this.isNew = isNew;
        }
        public ServerLevel getLevel() { return level; }
        public ChunkPos getChunkPos() { return chunkPos; }
    }

    /** {@code mobSpawn}: {@code e.cancel()} stops the spawn, {@code e.replace(id)} swaps the mob. */
    static final class MobSpawn extends BaseZoneEvent {
        private final FinalizeSpawnEvent ev;
        private final double depth;
        boolean replaced;
        MobSpawn(FinalizeSpawnEvent ev, Zone zone, double depth) {
            super("mobSpawn", zone);
            this.ev = ev;
            this.depth = depth;
        }
        public Mob getEntity() { return ev.getEntity(); }
        public ServerLevel getLevel() { return ev.getLevel().getLevel(); }
        /** MobSpawnType name, e.g. NATURAL. */
        public String getSpawnType() { return ev.getSpawnType().name(); }
        public double getDepth() { return depth; }

        /**
         * Cancels this spawn and spawns {@code entityId} at the same place on the next tick (max 20 replacements per
         * server tick). Returns false (and logs) if the id is unknown or the limit is exhausted.
         */
        public boolean replace(String entityId) {
            ScriptRuntime.requireServerThread("mobSpawn.replace");
            Mob m = ev.getEntity();
            boolean ok = ReplaceQueue.request(ev.getLevel().getLevel(), entityId, ev.getX(), ev.getY(), ev.getZ(), m.getYRot(),
                    ev.getSpawnType(), m.getServer() == null ? 0 : m.getServer().getTickCount());
            if (ok) replaced = true;
            return ok;
        }
    }

    /** {@code blockBreak} / {@code blockPlace}. */
    static final class BlockAction extends BaseZoneEvent {
        private final ServerPlayer player;
        private final Level level;
        private final BlockPos pos;
        private final BlockState state;
        BlockAction(String name, ServerPlayer player, Level level, BlockPos pos, BlockState state, Zone zone) {
            super(name, zone);
            this.player = player;
            this.level = level;
            this.pos = pos;
            this.state = state;
        }
        public ServerPlayer getPlayer() { return player; }
        public Level getLevel() { return level; }
        public BlockPos getPos() { return pos; }
        public BlockState getState() { return state; }
        /** KubeJS block wrapper at the position. */
        public LevelBlock getBlock() { return ((LevelKJS) level).kjs$getBlock(pos); }
    }

    static final class Damage extends BaseZoneEvent {
        private final LivingEntity entity;
        private final DamageSource source;
        private double amount;
        boolean amountChanged;
        Damage(LivingEntity entity, DamageSource source, double amount, Zone zone) {
            super("damage", zone);
            this.entity = entity;
            this.source = source;
            this.amount = amount;
        }
        public LivingEntity getEntity() { return entity; }
        public DamageSource getSource() { return source; }
        /** The responsible entity (projectile owner, TNT igniter, ...) or null. */
        public Entity getAttacker() { return source.getEntity(); }
        public double getAmount() { return amount; }
        public void setAmount(double amount) {
            this.amount = Math.max(0, amount);
            amountChanged = true;
        }
        double amountValue() { return amount; }
    }

    /** {@code claimAttempt}: only {@code e.deny('message')}. */
    static final class ClaimAttempt extends BaseZoneEvent {
        private final UUID owner;
        private final ServerPlayer player;
        private final ClaimActionType action;
        private final ChunkPos chunk;
        String denyMessage;
        ClaimAttempt(UUID owner, ServerPlayer player, ClaimActionType action, ChunkPos chunk, Zone zone) {
            super("claimAttempt", zone);
            this.owner = owner;
            this.player = player;
            this.action = action;
            this.chunk = chunk;
        }
        public String getOwnerUuid() { return owner.toString(); }
        /** The owner if online, else null. */
        public ServerPlayer getPlayer() { return player; }
        /** CLAIM, FORCELOAD or ANCHOR (lower case). */
        public String getAction() { return action.name().toLowerCase(java.util.Locale.ROOT); }
        public ChunkPos getChunkPos() { return chunk; }

        /** Forbids the action with this message. Allowing something the rules forbid is not possible. */
        public void deny(String message) {
            if (denyMessage == null) denyMessage = message == null || message.isBlank() ? "Приват запрещён правилами зоны" : message;
        }
    }

    /** Declared for 0.45 (dynamic zones); not fired in 0.3. */
    static final class DynamicZoneExpired extends BaseZoneEvent {
        DynamicZoneExpired(Zone zone) { super("dynamicZoneExpired", zone); }
    }
}
