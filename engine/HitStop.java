package engine;

/**
 * HitStop — freeze-frame system.
 *
 * When a hit connects, call trigger(frames).
 * GamePanel skips its physics/logic update while isActive() == true,
 * but still renders — the frozen frame makes hits feel impactful.
 *
 * Typical values:
 * Light hit : 2 frames (~33ms)
 * Heavy hit : 4 frames (~66ms)
 * Boss hit : 6 frames (~100ms)
 * Perfect parry: 8 frames (~133ms) with slow-motion
 */
public class HitStop {

    private int timer = 0;

    /**
     * Trigger a freeze. Stacks upward (never shortens an active stop).
     * 
     * @param frames number of frames to freeze
     */
    public void trigger(int frames) {
        timer = Math.max(timer, frames);
    }

    /**
     * Must be called every game tick (even when frozen — it's how we count down).
     */
    public void tick() {
        if (timer > 0)
            timer--;
    }

    /** Returns true while the game should be frozen. */
    public boolean isActive() {
        return timer > 0;
    }

    /** Remaining freeze frames. */
    public int framesLeft() {
        return timer;
    }
}
