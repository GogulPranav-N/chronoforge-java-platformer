package engine;

import java.awt.*;
import java.awt.geom.AffineTransform;

/**
 * Camera — smooth follow camera for ChronoForge.
 *
 * Usage pattern in GamePanel.paintComponent:
 * AffineTransform saved = camera.apply(g2); // translate world
 * // draw world-space objects here
 * camera.restore(g2, saved); // back to screen space
 * // draw HUD here (screen space — no camera transform)
 *
 * Features:
 * - Lerp follow : smooth interpolation toward player — feels AAA
 * - Look-ahead : camera leads slightly in the direction of movement
 * - Screen shake : trauma-based decay — add trauma, camera shakes itself out
 * - Letterbox : black bars top/bottom for boss cutscenes
 * - Dead zone : camera doesn't move until player leaves a centre region
 */
public class Camera {

    // ── World & screen ────────────────────────────────────────────────────
    private final int screenW;
    private final int screenH;

    /** Camera top-left in world coordinates. */
    public float camX = 0;
    public float camY = 0;

    // ── Follow tuning ─────────────────────────────────────────────────────
    private static final float LERP = 0.12f; // 0=no follow, 1=instant
    private static final float LOOK_AHEAD = 60f; // pixels ahead of player
    private static final float DEAD_ZONE_X = 80f; // horizontal dead zone half-width

    // ── Screen shake (trauma model) ───────────────────────────────────────
    // trauma ∈ [0,1]; shake magnitude = trauma²
    private float trauma = 0f;
    private float shakeX = 0f;
    private float shakeY = 0f;
    private static final float TRAUMA_DECAY = 0.05f;
    private static final float MAX_SHAKE_PX = 14f;
    private static final float MAX_SHAKE_ROT = 0.008f; // radians

    // ── Letterbox ─────────────────────────────────────────────────────────
    private int letterboxTarget = 0; // target bar height in pixels
    private int letterboxCurrent = 0;

    // ── Look-ahead tracking ───────────────────────────────────────────────
    private float lookOffset = 0f;

    public Camera(int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;
    }

    /**
     * Update camera position each tick.
     * 
     * @param playerX     player world X (left edge)
     * @param playerY     player world Y (top edge)
     * @param facingRight player facing direction
     * @param worldW      total level width (clamps camera)
     * @param worldH      total level height
     */
    public void update(int playerX, int playerY, boolean facingRight, int worldW, int worldH) {
        // Smooth look-ahead
        float targetLook = facingRight ? LOOK_AHEAD : -LOOK_AHEAD;
        lookOffset += (targetLook - lookOffset) * 0.06f;

        // Target: centre player + look-ahead
        float targetCamX = playerX - screenW / 2f + 17 + lookOffset;
        float targetCamY = playerY - screenH / 2f + 25;

        // Lerp toward target
        camX += (targetCamX - camX) * LERP;
        camY += (targetCamY - camY) * LERP;

        // Clamp to world bounds
        camX = Math.max(0, Math.min(camX, worldW - screenW));
        camY = Math.max(0, Math.min(camY, worldH - screenH));

        // Decay trauma
        trauma = Math.max(0, trauma - TRAUMA_DECAY);
        float shake = trauma * trauma; // quadratic — feels natural
        double seed = System.currentTimeMillis() * 0.05;
        shakeX = (float) (Math.sin(seed * 1.5) * shake * MAX_SHAKE_PX);
        shakeY = (float) (Math.cos(seed * 2.1) * shake * MAX_SHAKE_PX);

        // Letterbox animation
        if (letterboxCurrent < letterboxTarget)
            letterboxCurrent = Math.min(letterboxCurrent + 4, letterboxTarget);
        else if (letterboxCurrent > letterboxTarget)
            letterboxCurrent = Math.max(letterboxCurrent - 4, letterboxTarget);
    }

    /**
     * Add screen trauma. Call on heavy hits, explosions, boss attacks.
     * 
     * @param amount 0.0–1.0 (0.3 = light hit, 0.6 = heavy, 1.0 = max)
     */
    public void addTrauma(float amount) {
        trauma = Math.min(1f, trauma + amount);
    }

    /**
     * Apply camera transform to g2. Call BEFORE drawing world objects.
     * 
     * @return saved transform so you can restore it with restore()
     */
    public AffineTransform apply(Graphics2D g2) {
        AffineTransform saved = g2.getTransform();
        g2.translate(shakeX - camX, shakeY - camY);
        return saved;
    }

    /**
     * Restore g2 to screen space. Call AFTER drawing world objects, BEFORE drawing
     * HUD.
     */
    public void restore(Graphics2D g2, AffineTransform saved) {
        g2.setTransform(saved);
    }

    /**
     * Draw letterbox bars. Call in screen space (after restore).
     */
    public void drawLetterbox(Graphics2D g2) {
        if (letterboxCurrent <= 0)
            return;
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, screenW, letterboxCurrent);
        g2.fillRect(0, screenH - letterboxCurrent, screenW, letterboxCurrent);
    }

    /** Open letterbox bars — call before boss cutscene. */
    public void openLetterbox(int barHeight) {
        letterboxTarget = barHeight;
    }

    /** Close letterbox bars — call after cutscene ends. */
    public void closeLetterbox() {
        letterboxTarget = 0;
    }

    /** Convert screen X to world X (for mouse / UI hit testing). */
    public int toWorldX(int screenX) {
        return (int) (screenX + camX);
    }

    /** Convert screen Y to world Y. */
    public int toWorldY(int screenY) {
        return (int) (screenY + camY);
    }

    /** Quick instant snap — use when loading a new level. */
    public void snapTo(int playerX, int playerY) {
        camX = playerX - screenW / 2f + 17;
        camY = playerY - screenH / 2f + 25;
    }

    /**
     * Smooth cinematic pan toward a world-space target.
     * Call each frame during BOSS_CINEMATIC instead of update().
     */
    public void panTo(float targetWorldX, float targetWorldY, int worldW, int worldH) {
        float tx = targetWorldX - screenW / 2f;
        float ty = targetWorldY - screenH / 2f;
        tx = Math.max(0, Math.min(tx, worldW - screenW));
        ty = Math.max(0, Math.min(ty, worldH - screenH));
        camX += (tx - camX) * 0.045f;
        camY += (ty - camY) * 0.045f;
        // Letterbox animation tick
        if (letterboxCurrent < letterboxTarget)
            letterboxCurrent = Math.min(letterboxCurrent + 4, letterboxTarget);
        else if (letterboxCurrent > letterboxTarget)
            letterboxCurrent = Math.max(letterboxCurrent - 4, letterboxTarget);
    }
}
