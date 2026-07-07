package player;

/**
 * MoveController — owns squash & stretch state for the player.
 *
 * Squash & Stretch is the #1 juice technique in platformers.
 * It makes the player feel physically present and weighty.
 *
 * Jump up → tall & thin (stretch upward)
 * Double jump→ wider stretch burst
 * Fast fall → tall & thin (compressed by speed)
 * Land → short & wide (impact squash), then springs back
 * Dash → wide & flat (squeezed in motion direction)
 *
 * Usage in Player.draw():
 * g2.translate(cx, cy);
 * g2.scale(ctrl.scaleX, ctrl.scaleY);
 * g2.translate(-cx, -cy);
 */
public class MoveController {

    // Current scale applied to draw transform
    public float scaleX = 1f;
    public float scaleY = 1f;

    // Where the spring is pulling toward
    private float targetX = 1f;
    private float targetY = 1f;

    // Spring constants
    private static final float SPRING_STIFFNESS = 0.30f; // how fast it returns
    private static final float SPRING_DAMP = 0.18f; // how fast target drifts back to 1

    // ── Event triggers ────────────────────────────────────────────────────

    /** Called when the player executes a normal (first) jump. */
    public void onJump() {
        targetX = 0.78f;
        targetY = 1.30f;
    }

    /** Called on double jump — more dramatic stretch. */
    public void onDoubleJump() {
        targetX = 0.70f;
        targetY = 1.40f;
    }

    /** Called the frame player touches ground. */
    public void onLand(float velY) {
        // Harder landing = more squash
        float intensity = Math.min(Math.abs(velY) / 18f, 1f);
        targetX = 1f + 0.45f * intensity;
        targetY = 1f - 0.38f * intensity;
    }

    /** Called when dash starts. */
    public void onDash() {
        targetX = 0.72f;
        targetY = 1.15f;
    }

    /** Called on wall jump. */
    public void onWallJump() {
        targetX = 1.20f;
        targetY = 0.75f;
    }

    /** Called on attack swing. */
    public void onAttack() {
        targetX = 1.10f;
        targetY = 0.90f;
    }

    // ── Per-frame update ─────────────────────────────────────────────────

    /**
     * Call once per tick AFTER triggers.
     * 
     * @param velY current vertical velocity — adds passive stretch during fast fall
     */
    public void update(float velY) {
        // Passive stretch: fast upward = slightly tall, fast fall = slightly tall
        if (Math.abs(velY) > 10f && targetX > 0.95f && targetY < 1.05f) {
            float stretch = Math.min((Math.abs(velY) - 10f) / 14f, 0.15f);
            targetX = 1f - stretch * 0.5f;
            targetY = 1f + stretch;
        }

        // Spring toward target
        scaleX += (targetX - scaleX) * SPRING_STIFFNESS;
        scaleY += (targetY - scaleY) * SPRING_STIFFNESS;

        // Target drifts back to 1.0
        targetX += (1f - targetX) * SPRING_DAMP;
        targetY += (1f - targetY) * SPRING_DAMP;

        // Clamp to avoid insane values
        scaleX = Math.max(0.5f, Math.min(scaleX, 1.6f));
        scaleY = Math.max(0.5f, Math.min(scaleY, 1.6f));
    }

    /** Reset scales instantly — use on respawn/death. */
    public void reset() {
        scaleX = targetX = 1f;
        scaleY = targetY = 1f;
    }
}
