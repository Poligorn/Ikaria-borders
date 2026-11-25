package com.poligorn.ikariaborders.claims;

import com.poligorn.ikariaborders.zone.Zone;
import com.poligorn.ikariaborders.zone.ZoneConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Incremental scan/sweep of existing claims (TZ §7). Call {@link #step} once per server tick: it handles up to
 * {@code perTick} chunks and stops early when the time budget is spent. Pure JVM; OPAC access sits behind
 * {@link ClaimSource}/{@link ClaimRemover}.
 */
public final class SweepJob {
    /** Per-owner outcome. {@code violations} counts offending claims/forceloads/anchors, {@code removed} the ones really removed. */
    public static final class OwnerResult {
        public final UUID owner;
        public final String name;
        public int violations;
        public int removed;
        public int claims, forceloads, anchors; // removed, by kind

        OwnerResult(UUID owner, String name) {
            this.owner = owner;
            this.name = name;
        }
    }

    private final ClaimSource source;
    private final ClaimRemover remover; // null when not applying
    private final ZoneConfig cfg;
    private final int perTick;
    private final long budgetNanos;
    private final Consumer<OwnerResult> onOwnerDone;

    private final List<UUID> owners;
    private int ownerIdx;
    private List<ClaimEntry> current;
    private int entryIdx;
    private OwnerResult currentResult;
    private final Map<UUID, OwnerResult> results = new LinkedHashMap<>();
    private int chunksProcessed;
    private int exemptOwners;
    private boolean done;

    /**
     * @param apply         false = report only (scan or dryRun); remover is not used
     * @param budgetNanos   soft time budget per step; 0 = unlimited (only {@code perTick} limits)
     * @param onOwnerDone   called on the stepping thread when an owner with violations has been fully processed
     */
    public SweepJob(ClaimSource source, ClaimRemover remover, ZoneConfig cfg, boolean apply, int perTick,
                    long budgetNanos, Consumer<OwnerResult> onOwnerDone) {
        if (apply && remover == null) throw new IllegalArgumentException("remover required when apply=true");
        this.source = source;
        this.remover = apply ? remover : null;
        this.cfg = cfg;
        this.perTick = Math.max(1, perTick);
        this.budgetNanos = budgetNanos;
        this.onOwnerDone = onOwnerDone == null ? r -> {} : onOwnerDone;
        this.owners = new ArrayList<>(source.owners());
    }

    public boolean applying() { return remover != null; }
    public boolean isDone() { return done; }
    public int chunksProcessed() { return chunksProcessed; }
    public int exemptOwners() { return exemptOwners; }
    public Map<UUID, OwnerResult> results() { return results; }

    public int totalViolations() { return results.values().stream().mapToInt(r -> r.violations).sum(); }
    public int totalRemoved() { return results.values().stream().mapToInt(r -> r.removed).sum(); }

    /** @return true when the job is finished */
    public boolean step(LongSupplier nanoClock) {
        if (done) return true;
        long start = nanoClock.getAsLong();
        int n = 0;
        while (n < perTick) {
            if (current == null) {
                if (ownerIdx >= owners.size()) { done = true; return true; }
                UUID o = owners.get(ownerIdx++);
                if (ClaimPolicy.isExempt(cfg, o)) { exemptOwners++; continue; }
                current = source.claimsOf(o);
                entryIdx = 0;
                currentResult = new OwnerResult(o, source.ownerName(o));
            }
            if (entryIdx >= current.size()) {
                finishOwner();
                continue;
            }
            process(currentResult.owner, current.get(entryIdx++));
            n++;
            chunksProcessed++;
            if (budgetNanos > 0 && (n & 15) == 0 && nanoClock.getAsLong() - start > budgetNanos) break;
        }
        return false;
    }

    private void finishOwner() {
        if (currentResult.violations > 0) {
            results.put(currentResult.owner, currentResult);
            onOwnerDone.accept(currentResult);
        }
        current = null;
        currentResult = null;
    }

    private void process(UUID owner, ClaimEntry e) {
        Zone z = cfg.zoneOfChunk(e.dimension(), e.cx(), e.cz());
        OwnerResult r = currentResult;
        if (e.kind() == ClaimEntry.Kind.ANCHOR) {
            if (z.anchorAllowed()) return;
            r.violations++;
            if (remover != null && remover.removeAnchor(owner, e)) { r.removed++; r.anchors++; }
        } else if (!z.claimsAllowed()) {
            r.violations++; // removing the claim also drops its forceload
            if (remover != null && remover.unclaim(owner, e)) { r.removed++; r.claims++; }
        } else if (e.forceloaded() && !z.forceloadAllowed()) {
            r.violations++;
            if (remover != null && remover.unforceload(owner, e)) { r.removed++; r.forceloads++; }
        }
    }
}
