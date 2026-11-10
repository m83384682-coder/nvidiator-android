// Nvidiator Android: plain-Java decisions for moving through items, kept free of Android classes
// so they can be tested on a computer (see tests/java).
// Not affiliated with or endorsed by NV Access. Licensed under the GNU GPL version 2.
package com.irondonkey.nvidiator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FlowLogic {
    private FlowLogic() {}

    /**
     * Whether a swipe should scroll the list first. True when automatic scrolling is on, the
     * navigator is inside a list that can still scroll that way, and the next item in reading
     * order is outside that list (or there is none). That is the moment TalkBack users would
     * otherwise have to scroll with two fingers.
     */
    public static boolean shouldAutoScroll(boolean enabled, boolean insideScrollable, boolean canScrollThatWay,
                                           boolean nextIsInsideSameList) {
        return enabled && insideScrollable && canScrollThatWay && !nextIsInsideSameList;
    }

    /**
     * After a list scrolled, picks the item to move to.
     *
     * @param before the list's items before the scroll (each item's signature: text and type)
     * @param after the list's items after the scroll
     * @param navIndex where the navigator was in {@code before}, or -1
     * @param dir 1 for next, -1 for previous
     * @return the index in {@code after} to move to, or -1 when the scroll revealed nothing new
     */
    public static int pickAfterScroll(List<String> before, List<String> after, int navIndex, int dir) {
        if (after == null || after.isEmpty()) return -1;
        if (before == null) before = java.util.Collections.emptyList();
        String nav = navIndex >= 0 && navIndex < before.size() ? before.get(navIndex) : null;
        if (nav != null) {
            // Find the navigator's item in the new list. When several items look the same, use the one
            // whose neighbour (the item before it, for next; after it, for previous) also matches.
            int best = -1;
            int bestScore = -1;
            for (int j = 0; j < after.size(); j++) {
                if (!after.get(j).equals(nav)) continue;
                int score = 0;
                for (int k = 1; k <= 3; k++) {
                    int bi = navIndex - k * dir, ai = j - k * dir;
                    if (bi < 0 || ai < 0 || bi >= before.size() || ai >= after.size()) break;
                    if (before.get(bi).equals(after.get(ai))) score++; else break;
                }
                if (score > bestScore || (score == bestScore && dir > 0)) { best = j; bestScore = score; }
            }
            if (best >= 0) {
                int t = best + dir;
                if (t >= 0 && t < after.size()) return t;
                return -1;
            }
        }
        // The navigator's item scrolled away: take the first new item in the direction of travel.
        Set<String> old = new HashSet<>(before);
        if (dir > 0) {
            for (int j = 0; j < after.size(); j++) if (!old.contains(after.get(j))) return j;
        } else {
            for (int j = after.size() - 1; j >= 0; j--) if (!old.contains(after.get(j))) return j;
        }
        return -1;
    }
}
