package com.poligorn.ikariaborders.claims;

import java.util.List;
import java.util.function.LongSupplier;

/** Batched removal of a fixed list of leftover server claims ({@code /borders legacy-cleanup}). Pure JVM. */
public final class LegacyCleanupJob {
    private final List<ClaimEntry> entries;
    private final ClaimRemover remover;
    private final boolean apply;
    private final int perTick;
    private final long budgetNanos;
    private int idx;
    private int removed;

    public LegacyCleanupJob(List<ClaimEntry> entries, ClaimRemover remover, boolean apply, int perTick, long budgetNanos) {
        this.entries = entries;
        this.remover = remover;
        this.apply = apply;
        this.perTick = Math.max(1, perTick);
        this.budgetNanos = budgetNanos;
    }

    public int total() { return entries.size(); }
    public int removed() { return removed; }
    public boolean applying() { return apply; }
    public boolean isDone() { return idx >= entries.size(); }

    public boolean step(LongSupplier nanoClock) {
        long start = nanoClock.getAsLong();
        int n = 0;
        while (idx < entries.size() && n < perTick) {
            ClaimEntry e = entries.get(idx++);
            n++;
            if (apply && remover.unclaim(ClaimPolicy.SERVER_OWNER, e)) removed++;
            if (budgetNanos > 0 && (n & 15) == 0 && nanoClock.getAsLong() - start > budgetNanos) break;
        }
        return isDone();
    }
}
