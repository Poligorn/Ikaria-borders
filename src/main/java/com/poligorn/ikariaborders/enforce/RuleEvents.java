package com.poligorn.ikariaborders.enforce;

import com.poligorn.ikariaborders.IkariaBorders;
import com.poligorn.ikariaborders.SableCompat;
import com.poligorn.ikariaborders.ZoneManager;
import com.poligorn.ikariaborders.rules.DamagePolicy;
import com.poligorn.ikariaborders.rules.ExplosionPolicy;
import com.poligorn.ikariaborders.rules.PvpPolicy;
import com.poligorn.ikariaborders.rules.SpawnPolicy;
import com.poligorn.ikariaborders.script.ScriptBridge;
import com.poligorn.ikariaborders.zone.Zone;
import com.poligorn.ikariaborders.zone.ZoneConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.function.Predicate;

/**
 * Built-in zone rules (TZ §6.1–6.3) on the NeoForge bus. Positions go through the Sable projection first.
 * Order is fixed: built-in rules, then scripts; scripts can only tighten (they are not called for what the rules
 * already cancelled). OPAC / OPAC Warfare protection is independent: the strictest wins.
 */
@EventBusSubscriber(modid = IkariaBorders.MODID)
public final class RuleEvents {
    private RuleEvents() {}

    // ---- damage: PvP, damage.deny, then scripts ----

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide) return;
        ZoneConfig cfg = ZoneManager.config();
        DamageSource src = e.getSource();
        Zone vz = null;

        if (cfg.anyPvpRules()) {
            vz = ZoneLookup.of(victim);
            if (pvpDenied(vz, victim, src)) {
                e.setCanceled(true);
                final Zone zf = vz;
                DebugLog.log("PvP отменён", () -> describe(src.getEntity()) + " → " + describe(victim) + ", зона жертвы " + zf.id());
                return;
            }
        }
        if (cfg.anyDamageRules()) {
            if (vz == null) vz = ZoneLookup.of(victim);
            Predicate<String> hasTag = tag -> src.is(TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse(tag)));
            String typeId = src.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("");
            if (DamagePolicy.denied(vz.rules(), victim instanceof Player, typeId, hasTag)) {
                e.setCanceled(true);
                final Zone zf = vz;
                DebugLog.log("Урон отменён", () -> typeId + " → " + describe(victim) + ", зона " + zf.id());
                return;
            }
        }
        // scripts last, and only for what the rules allowed
        if (ScriptBridge.active()) {
            if (vz == null) vz = ZoneLookup.of(victim);
            ScriptBridge.onDamage(e, vz);
        }
    }

    /** PvP verdict for this hit; false if it is not player-vs-player (or player-vs-protected-pet) at all. */
    static boolean pvpDenied(Zone victimZone, LivingEntity victim, DamageSource src) {
        Player attacker = attackingPlayer(src);
        if (attacker == null) return false;
        java.util.UUID victimPlayer = null;
        if (victim instanceof Player p) {
            victimPlayer = p.getUUID();
        } else if (PvpPolicy.petProtected(victimZone.rules()) && victim instanceof OwnableEntity pet) {
            victimPlayer = pet.getOwnerUUID(); // tamed pet of some player; zone is the pet's own position
        }
        if (victimPlayer == null || victimPlayer.equals(attacker.getUUID())) return false; // not PvP / own pet / self damage
        return PvpPolicy.denied(ZoneLookup.of(attacker).rules(), victimZone.rules());
    }

    /** The player responsible for the damage: direct, arrow/TNT owner (getEntity), or the owner of a tamed pet. */
    static Player attackingPlayer(DamageSource src) {
        Entity a = src.getEntity();
        if (a instanceof Player p) return p;
        if (a instanceof OwnableEntity pet && pet.getOwner() instanceof Player p) return p;
        return null;
    }

    // ---- explosions ----

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate e) {
        Level level = e.getLevel();
        if (level.isClientSide) return;
        ZoneConfig cfg = ZoneManager.config();
        if (!cfg.anyExplosionRules()) return;
        String dim = ZoneLookup.dim(level);
        int blocks = ExplosionPolicy.removeProtected(e.getAffectedBlocks(), pos -> {
            SableCompat.Projection p = SableCompat.project(level, Vec3.atCenterOf(pos));
            return ExplosionPolicy.blockProtectedAt(cfg, dim, p.x(), p.z());
        });
        int ents = ExplosionPolicy.removeProtected(e.getAffectedEntities(), ent -> {
            SableCompat.Projection p = SableCompat.project(ent);
            return ExplosionPolicy.entityProtected(cfg, dim, p.x(), p.z());
        });
        if (blocks > 0 || ents > 0) {
            Vec3 c = e.getExplosion().center();
            DebugLog.log("Взрыв ограничен", () -> String.format(java.util.Locale.ROOT,
                    "(%.0f, %.0f, %.0f) в %s: блоков защищено %d, сущностей %d", c.x, c.y, c.z, dim, blocks, ents));
        }
    }

    // ---- spawn ----

    @SubscribeEvent
    public static void onSpawnPositionCheck(MobSpawnEvent.PositionCheck e) {
        if (e.getSpawnType() == null) return;
        Mob mob = e.getEntity();
        if (mob.level().isClientSide) return;
        if (!ZoneManager.config().anySpawnRules()) return;
        if (spawnDenied(mob, e.getSpawnType().name())) {
            e.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    /** Safety net for spawns that do not go through PositionCheck, then scripts. */
    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent e) {
        Mob mob = e.getEntity();
        if (mob.level().isClientSide) return;
        boolean denied = ZoneManager.config().anySpawnRules() && spawnDenied(mob, e.getSpawnType().name());
        if (denied) {
            e.setSpawnCancelled(true);
            return;
        }
        if (ScriptBridge.active()) ScriptBridge.onMobSpawn(e);
    }

    private static boolean spawnDenied(Mob mob, String reason) {
        Zone z = ZoneLookup.of(mob);
        EntityType<?> type = mob.getType();
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        Predicate<String> hasTag = tag -> type.is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(tag)));
        boolean denied = SpawnPolicy.denied(z.rules(), type.getCategory().getName(), reason, id, hasTag);
        if (denied) DebugLog.log("Спавн отменён", () -> id + " (" + reason + ") в зоне " + z.id());
        return denied;
    }

    private static String describe(Entity e) {
        return e == null ? "?" : e.getName().getString();
    }
}
