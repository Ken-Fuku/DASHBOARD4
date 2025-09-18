package com.kenfukuda.dashboard.domain.service;

import com.kenfukuda.dashboard.domain.model.ChangeLogEntry;

import java.util.Set;

/**
 * Simple conflict resolver: Last-Write-Wins by default, but allows prefer-main for master tables.
 */
public class ConflictResolver {
    private final Set<String> preferMainTables = Set.of("company", "store");

    /**
     * Decide which entry wins between existing and incoming.
     * Returns the winning entry (existing if it should remain, or incoming if it should replace).
     */
    public ChangeLogEntry resolve(ChangeLogEntry existing, ChangeLogEntry incoming, boolean incomingFromMain) {
        return resolve(existing, incoming, incomingFromMain, isFromMain(existing));
    }

    // overloaded: caller can pass whether existing is from main (based on configured main node id)
    public ChangeLogEntry resolve(ChangeLogEntry existing, ChangeLogEntry incoming, boolean incomingFromMain, boolean existingFromMain) {
        if (existing == null) return incoming;
        // if this table is prefer-main and incoming is not main, prefer existing when existing is from main
        if (preferMainTables.contains(existing.getTableName())) {
            boolean efm = existingFromMain;
            // fallback: if provenance not present, use previous LSN heuristic
            if (!efm) {
                try { efm = existing.getLsn() < incoming.getLsn(); } catch (Exception ignored) {}
            }
            if (incomingFromMain && !efm) return incoming; // incoming main beats non-main existing
            if (!incomingFromMain && efm) return existing; // existing main beats incoming non-main
        }
        // default LWW: compare created_at (ISO string), later timestamp wins
        try {
            String e = existing.getCreatedAt();
            String i = incoming.getCreatedAt();
            if (e == null) return incoming;
            if (i == null) return existing;
            if (i.compareTo(e) >= 0) return incoming; else return existing;
        } catch (Exception ex) {
            return incoming;
        }
    }

    // heuristic: determine if entry is from main by checking source_node payload or tx_id pattern
    private boolean isFromMain(ChangeLogEntry e) {
        if (e == null) return false;
        try {
            String src = e.getSourceNode();
            if (src == null) return false;
            // convention: when source_node equals 'local' or a configured main node id, caller-side
            // will set incomingFromMain appropriately. Here we treat any non-null source_node as provenance
            // and return true if it equals the configured 'main' identifier. Since ConflictResolver does not
            // know the global main id, we conservatively interpret a sourceNode of 'main' or 'local' as main.
            // Prefer explicit marker: if source_node equals 'main' return true; if equals 'local' return true as well
            // (deployment may use different conventions). For now, treat non-empty source_node as indicative of origin
            // and rely on the incomingFromMain flag passed to resolve to break ties.
            String s = src.trim();
            if (s.isEmpty()) return false;
            if (s.equalsIgnoreCase("main")) return true;
            if (s.equalsIgnoreCase("local")) return true;
            return false;
        } catch (Exception ex) {
            return false;
        }
    }
}
