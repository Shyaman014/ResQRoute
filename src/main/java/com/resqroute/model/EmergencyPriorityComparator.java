package com.resqroute.model;

import java.util.Comparator;

/**
 * Priority comparator for sorting emergency cases for triage and future dispatch integration.
 *
 * <p>Ranking hierarchy:
 * <ol>
 *   <li>Triage severity rank (CRITICAL &gt; HIGH &gt; MEDIUM &gt; LOW).</li>
 *   <li>First-in-first-out (FIFO) based on creation timestamp.</li>
 *   <li>Lexicographical case identifier as deterministic tie-breaker.</li>
 * </ol>
 * </p>
 */
public final class EmergencyPriorityComparator implements Comparator<EmergencyCase> {

    public static final EmergencyPriorityComparator INSTANCE = new EmergencyPriorityComparator();

    @Override
    public int compare(EmergencyCase c1, EmergencyCase c2) {
        if (c1 == c2) {
            return 0;
        }
        if (c1 == null) {
            return 1;
        }
        if (c2 == null) {
            return -1;
        }

        // 1. Severity rank comparison (1 = CRITICAL is lowest int, highest priority)
        int severityComparison = Integer.compare(
                c1.getSeverity().getPriorityRank(),
                c2.getSeverity().getPriorityRank()
        );
        if (severityComparison != 0) {
            return severityComparison;
        }

        // 2. FIFO order by creation timestamp
        int timeComparison = c1.getCreatedAt().compareTo(c2.getCreatedAt());
        if (timeComparison != 0) {
            return timeComparison;
        }

        // 3. Deterministic ID tie-breaker
        return c1.getId().compareTo(c2.getId());
    }
}
