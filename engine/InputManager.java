package engine;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * InputManager — central input authority for ChronoForge.
 *
 * Improvements over KeyHandler:
 * - Jump Buffering : jump press is remembered for JUMP_BUFFER_FRAMES frames.
 * If the player lands within that window the jump fires.
 * Eliminates the "I pressed jump but nothing happened" feeling.
 * - Coyote Time : notified by Player when it leaves a platform edge.
 * Allows a jump for COYOTE_FRAMES frames after leaving.
 * Eliminates the "I pressed jump a millisecond too late" feeling.
 * - Parry key : K (separate from attack so parry timing is intentional)
 * - consumeJump() : clears the buffer — only one system uses each press.
 */
public class InputManager implements KeyListener {

    // ── Tuning ─────────────────────────────────────────────────────────────
    private static final int JUMP_BUFFER_FRAMES = 10; // ~160 ms at 60 fps
    private static final int COYOTE_FRAMES = 7; // ~112 ms

    // ── Raw held states ────────────────────────────────────────────────────
    public boolean leftPressed;
    public boolean rightPressed;
    public boolean downPressed;
    public boolean dashPressed;
    public boolean attackPressed; // J
    public boolean parryPressed; // K
    public boolean escPressed;
    public boolean enterPressed;

    // ── Jump buffering ────────────────────────────────────────────────────
    /** Raw held state — also drives the buffer. */
    public boolean jumpHeld;
    /** Counts down when jump was pressed but not yet consumed. */
    private int jumpBufferTimer = 0;

    // ── Coyote time ───────────────────────────────────────────────────────
    /** Set by Player the frame it leaves a grounded surface. */
    private int coyoteTimer = 0;

    // ── One-shot edge detectors (set each frame, cleared next) ────────────
    private boolean jumpWasHeld = false;
    private boolean attackWasHeld = false;
    private boolean parryWasHeld = false;
    private boolean dashWasHeld = false;

    /** True only on the first frame the key was pressed. */
    public boolean jumpJustPressed;
    public boolean attackJustPressed;
    public boolean parryJustPressed;
    public boolean dashJustPressed;

    // ─────────────────────────────────────────────────────────────────────
    // Must be called once per game tick BEFORE physics/player update.
    // ─────────────────────────────────────────────────────────────────────
    public void tick() {
        // Edge detection
        jumpJustPressed = jumpHeld && !jumpWasHeld;
        attackJustPressed = attackPressed && !attackWasHeld;
        parryJustPressed = parryPressed && !parryWasHeld;
        dashJustPressed = dashPressed && !dashWasHeld;

        jumpWasHeld = jumpHeld;
        attackWasHeld = attackPressed;
        parryWasHeld = parryPressed;
        dashWasHeld = dashPressed;

        // Jump buffer: start/refresh on first press
        if (jumpJustPressed)
            jumpBufferTimer = JUMP_BUFFER_FRAMES;
        else if (jumpBufferTimer > 0)
            jumpBufferTimer--;

        // Coyote timer counts down automatically
        if (coyoteTimer > 0)
            coyoteTimer--;
    }

    /**
     * Call from Player the frame it walks off a platform edge (not a jump).
     * Starts the coyote time window.
     */
    public void startCoyoteTime() {
        coyoteTimer = COYOTE_FRAMES;
    }

    /**
     * Returns true if a jump can be executed right now:
     * - buffered jump press is active, AND
     * - coyote timer still has frames (player was recently grounded)
     * OR the player is still grounded (handled externally via jumpCount == 0).
     */
    public boolean hasBufferedJump() {
        return jumpBufferTimer > 0;
    }

    public boolean hasCoyoteTime() {
        return coyoteTimer > 0;
    }

    /**
     * Consume the jump buffer so the same press doesn't fire twice.
     * Call this whenever the jump action actually executes.
     */
    public void consumeJump() {
        jumpBufferTimer = 0;
    }

    // ── KeyListener ───────────────────────────────────────────────────────
    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_A -> leftPressed = true;
            case KeyEvent.VK_D -> rightPressed = true;
            case KeyEvent.VK_S -> downPressed = true;
            case KeyEvent.VK_SPACE -> jumpHeld = true;
            case KeyEvent.VK_SHIFT -> dashPressed = true;
            case KeyEvent.VK_J -> attackPressed = true;
            case KeyEvent.VK_K -> parryPressed = true;
            case KeyEvent.VK_ESCAPE -> escPressed = true;
            case KeyEvent.VK_ENTER -> enterPressed = true;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_A -> leftPressed = false;
            case KeyEvent.VK_D -> rightPressed = false;
            case KeyEvent.VK_S -> downPressed = false;
            case KeyEvent.VK_SPACE -> jumpHeld = false;
            case KeyEvent.VK_SHIFT -> dashPressed = false;
            case KeyEvent.VK_J -> attackPressed = false;
            case KeyEvent.VK_K -> parryPressed = false;
            case KeyEvent.VK_ESCAPE -> escPressed = false;
            case KeyEvent.VK_ENTER -> enterPressed = false;
        }
    }
}
