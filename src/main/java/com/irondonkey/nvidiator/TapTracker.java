// Nvidiator Android: works out when the user double taps, or double taps and holds, from the touch
// events Android sends to a screen reader. Plain Java, so it can be tested on a computer.
// Not affiliated with or endorsed by NV Access. Licensed under the GNU GPL version 2.
package com.irondonkey.nvidiator;

/**
 * Android's touch explorer turns a double tap into a click, and a double tap and hold into a long
 * press, without telling the screen reader which gesture it was. What we do see is the touch
 * interaction starting and ending, and whether it turned into exploring or a swipe. A short touch
 * that neither explored nor swiped, followed quickly by another touch, is a double tap. A touch that
 * stays down without exploring or swiping is a hold.
 */
public final class TapTracker {
    public static final long TAP_MAX_MS = 300;       // longest touch that still counts as a tap
    public static final long DOUBLE_TAP_GAP_MS = 400; // longest gap between the two taps
    public static final long HOLD_MS = 450;          // how long a still touch takes to count as a hold

    public enum Result { NONE, DOUBLE_TAP, HOLD }

    private long startAt = -1;
    private boolean moved;          // explored or swiped during this touch
    private long lastTapEnd = -1;   // when the last plain tap ended
    private boolean interrupted;    // speech already interrupted for this touch

    /** A touch began. Returns DOUBLE_TAP when this is the second tap of a double tap. */
    public Result onStart(long now) {
        Result r = Result.NONE;
        if (lastTapEnd >= 0 && now - lastTapEnd <= DOUBLE_TAP_GAP_MS) r = Result.DOUBLE_TAP;
        startAt = now;
        moved = false;
        interrupted = r == Result.DOUBLE_TAP;
        lastTapEnd = -1;
        return r;
    }

    /** The touch turned into exploring by touch or into a gesture (a swipe). */
    public void onMoved() { moved = true; }

    /** Called a little after the touch began; HOLD when the finger is still down and still. */
    public Result checkHold(long now) {
        if (startAt < 0 || moved || interrupted) return Result.NONE;
        if (now - startAt >= HOLD_MS) { interrupted = true; return Result.HOLD; }
        return Result.NONE;
    }

    /** The touch ended. */
    public void onEnd(long now) {
        if (startAt >= 0 && !moved && now - startAt <= TAP_MAX_MS && !interrupted) lastTapEnd = now;
        else lastTapEnd = -1;
        startAt = -1;
        moved = false;
        interrupted = false;
    }

    public boolean touching() { return startAt >= 0; }
}
