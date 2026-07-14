package player;

import engine.InputManager;
import fx.ParticleSystem;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayDeque;

/**
 * Player — Moon Samurai protagonist.
 *
 * Phase 2 additions:
 * - MoveController: squash & stretch applied in draw via AffineTransform
 * - Ghost afterimage: stores last N positions for dash ghost trail
 * - Wall-slide sparks: particles while sliding a wall
 * - Landing impact: calls moveCtrl.onLand() with velY magnitude
 * - Double jump distinction: stronger stretch via onDoubleJump()
 */

public class Player {

    // ── Position & physics ────────────────────────────────────────────────
    public float x, y;
    public float velX, velY;
    public boolean facingRight = true;
    public boolean onGround = false;
    private boolean wasOnGround = false;
    private float landingVelY = 0f; // capture velY the tick before landing

    // ── Movement tuning ───────────────────────────────────────────────────
    private static final float ACCEL_GROUND = 2.4f;
    private static final float ACCEL_AIR = 1.5f;
    private static final float FRICTION = 0.76f;
    private static final float AIR_FRICTION = 0.91f;
    private static final float MAX_FALL = 18f;

    // ── Jump ──────────────────────────────────────────────────────────────
    public int jumpCount = 0;
    private static final float JUMP_FORCE_N1 = -20f;
    private static final float JUMP_FORCE_N2 = -17f;
    private static final float JUMP_FORCE_D1 = -16f;
    private static final float JUMP_FORCE_D2 = -13f;
    private static final float JUMP_CUT_VEL = -6f;

    // ── Dash ──────────────────────────────────────────────────────────────
    public boolean canDash = true;
    public boolean isDashing = false;
    public int dashTimer = 0;
    public int dashCooldown = 0;
    private static final int DASH_FRAMES = 13;
    private static final float DASH_SPEED = 22f;
    private static final int DASH_COOLDOWN_MAX = 35;

    // ── Attack ────────────────────────────────────────────────────────────
    public boolean isAttacking = false;
    public int attackTimer = 0;
    private static final int ATTACK_DURATION = 14;

    // ── Wall ──────────────────────────────────────────────────────────────
    public boolean touchingWallLeft = false;
    public boolean touchingWallRight = false;
    private int wallJumpLock = 0;
    private int wallSlideParticleTimer = 0;

    // ── Health ────────────────────────────────────────────────────────────
    public int maxHealth = 8;
    public int health = 8;
    public int invincibleTimer = 0;

    // ── Animation ─────────────────────────────────────────────────────────
    public String animState = "idle";
    public int animFrame = 0;
    private int animTick = 0;
    private String prevState = "";

    // ── Knockback ─────────────────────────────────────────────────────────
    private float knockX = 0, knockY = 0;

    // ── Ghost afterimage (dash) ────────────────────────────────────────────
    private static final int GHOST_MAX = 6;
    private final ArrayDeque<float[]> ghosts = new ArrayDeque<>(); // [x, y, alpha, scaleX, scaleY]

    // ── Abilities ─────────────────────────────────────────────────────────
    // DAY — Invisibility: player vanishes, enemies skip targeting / bullets pass
    // through
    public boolean isInvisible = false;
    public int invisibleTimer = 0;
    private static final int INVISIBLE_DURATION = 300; // 5 seconds @ 60 fps

    // DUSK — Time Warp: all enemy bullets and movement slow to 30%
    public boolean isTimeWarp = false;
    public int timeWarpTimer = 0;
    public int timeWarpCooldown = 0;
    private static final int TIME_WARP_DURATION = 240; // 4 seconds
    private static final int TIME_WARP_COOLDOWN = 900; // 15 seconds

    // NIGHT — Shadow Clone: 2 decoy positions that attract enemy fire
    public boolean isShadowClone = false;
    public int shadowCloneTimer = 0;
    public float[] clonePositions = new float[4]; // [x1,y1,x2,y2]
    private static final int CLONE_DURATION = 300; // 5 seconds

    // Shared visual feedback tick
    private float abilityPulse = 0f;

    // ── Systems ───────────────────────────────────────────────────────────
    private final InputManager input;
    private final ParticleSystem particles;
    public final MoveController moveCtrl = new MoveController();

    // ── Dimensions ────────────────────────────────────────────────────────
    public static final int W = 35, H = 50;

    public Player(float startX, float startY, InputManager input, ParticleSystem particles) {
        this.x = startX;
        this.y = startY;
        this.input = input;
        this.particles = particles;
    }

    // ─────────────────────────────────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────────────────────────────────
    public void update(String phase, Rectangle[] platforms,
            int wallThickness, int screenW, int screenH) {

        if (invincibleTimer > 0)
            invincibleTimer--;
        if (dashCooldown > 0)
            dashCooldown--;
        if (timeWarpCooldown > 0)
            timeWarpCooldown--;

        // ── Ability timers ────────────────────────────────────────────────
        abilityPulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() * 0.005));
        if (isInvisible) {
            invisibleTimer--;
            if (invisibleTimer <= 0) {
                isInvisible = false;
                particles.spawn(x + W / 2f, y + H / 2f, ParticleSystem.Type.SPARK);
            }
        }
        if (isTimeWarp) {
            timeWarpTimer--;
            if (timeWarpTimer <= 0) {
                isTimeWarp = false;
                timeWarpCooldown = TIME_WARP_COOLDOWN;
                particles.spawn(x + W / 2f, y + H / 2f, ParticleSystem.Type.MOON);
            }
        }
        if (isShadowClone) {
            shadowCloneTimer--;
            if (shadowCloneTimer <= 0) {
                isShadowClone = false;
            }
        }

        // ── Dash trigger ──────────────────────────────────────────────────
        if (input.dashJustPressed && canDash && !isDashing && dashCooldown == 0
                && phase.equals("NIGHT")
                && (input.leftPressed || input.rightPressed)) {
            isDashing = true;
            dashTimer = DASH_FRAMES;
            dashCooldown = DASH_COOLDOWN_MAX;
            canDash = false;
            velY = 0;
            moveCtrl.onDash();
            particles.spawn(x + W / 2f, y + H / 2f, ParticleSystem.Type.DASH, facingRight);
        }

        // ── Dash movement & ghost trail ───────────────────────────────────
        if (isDashing) {
            // Store ghost
            if (ghosts.size() >= GHOST_MAX)
                ghosts.pollFirst();
            ghosts.addLast(new float[] { x, y, 0.7f, moveCtrl.scaleX, moveCtrl.scaleY });

            dashTimer--;
            x += facingRight ? DASH_SPEED : -DASH_SPEED;
            if (dashTimer <= 0)
                isDashing = false;
        } else {
            // Fade ghosts
            ghosts.removeIf(g -> (g[2] -= 0.14f) <= 0);
        }

        // ── Horizontal movement ───────────────────────────────────────────
        if (!isDashing) {
            if (wallJumpLock > 0) {
                wallJumpLock--;
            } else {
                float accel = onGround ? ACCEL_GROUND : ACCEL_AIR;
                if (input.leftPressed) {
                    velX -= accel;
                    facingRight = false;
                }
                if (input.rightPressed) {
                    velX += accel;
                    facingRight = true;
                }
                float maxSpd = phase.equals("NIGHT") ? 5f : phase.equals("DUSK") ? 3.5f : 2.5f;
                velX = Math.max(-maxSpd, Math.min(maxSpd, velX));
            }
            velX *= onGround ? FRICTION : AIR_FRICTION;
            if (Math.abs(velX) < 0.3f)
                velX = 0;
        }
        x += velX;

        // ── Gravity / variable jump ───────────────────────────────────────
        if (!isDashing) {
            float grav = phase.equals("NIGHT") ? 1.1f : 1.6f;
            if (velY < JUMP_CUT_VEL && !input.jumpHeld)
                velY = Math.max(velY + grav * 2f, JUMP_CUT_VEL);
            else
                velY = Math.min(velY + grav, MAX_FALL);
            if (input.downPressed && velY > 0)
                velY += 1.5f;
        }
        landingVelY = velY; // capture before collision resolves velY
        y += velY;

        // ── Platform collision ─────────────────────────────────────────────
        wasOnGround = onGround;
        onGround = false;
        Rectangle pr = getRect();
        for (Rectangle p : platforms) {
            if (pr.intersects(p)) {
                if (velY >= 0 && (int) y + H <= p.y + p.height + (int) velY + 3) {
                    y = p.y - H;
                    velY = 0;
                    onGround = true;
                    jumpCount = 0;
                    canDash = true;
                } else if (velY < 0) {
                    y = p.y + p.height;
                    velY = 2;
                }
            }
        }

        // ── Landing event ─────────────────────────────────────────────────
        if (onGround && !wasOnGround) {
            moveCtrl.onLand(landingVelY);
            particles.spawn(x + W / 2f, y + H, ParticleSystem.Type.LAND);
        }

        // ── Coyote time ───────────────────────────────────────────────────
        if (wasOnGround && !onGround && velY > 0)
            input.startCoyoteTime();

        // ── Wall detection ────────────────────────────────────────────────
        touchingWallLeft = (x <= wallThickness + 2);
        touchingWallRight = (x + W >= screenW - wallThickness - 2);
        boolean onWall = (touchingWallLeft || touchingWallRight) && !onGround;

        // Wall slide (slow fall + sparks)
        if (onWall && velY > 3) {
            boolean pressing = (touchingWallLeft && input.leftPressed)
                    || (touchingWallRight && input.rightPressed);
            if (pressing) {
                velY = 3f;
                // Sparks every 6 frames
                wallSlideParticleTimer++;
                if (wallSlideParticleTimer >= 6) {
                    wallSlideParticleTimer = 0;
                    float sparkX = touchingWallRight ? x + W + 2 : x - 4;
                    particles.spawn(sparkX, y + H * 0.6f, ParticleSystem.Type.SPARK, !touchingWallRight);
                }
            }
        } else {
            wallSlideParticleTimer = 0;
        }

        // Clamp to walls
        if (x < wallThickness)
            x = wallThickness;
        if (x + W > screenW - wallThickness)
            x = screenW - wallThickness - W;

        // ── Jump (buffered + coyote) ───────────────────────────────────────
        if (input.hasBufferedJump()) {
            if (onWall && !onGround) {
                boolean night = phase.equals("NIGHT");
                velY = night ? -18f : -15f;
                velX = touchingWallRight ? -14f : 14f;
                wallJumpLock = 14;
                jumpCount = 1;
                input.consumeJump();
                moveCtrl.onWallJump();
                particles.spawn(x + W / 2f, y + H / 2f, ParticleSystem.Type.JUMP);
            } else if (jumpCount == 0 || input.hasCoyoteTime()) {
                if (onGround || input.hasCoyoteTime()) {
                    boolean night = phase.equals("NIGHT");
                    velY = night ? JUMP_FORCE_N1 : JUMP_FORCE_D1;
                    jumpCount = 1;
                    input.consumeJump();
                    moveCtrl.onJump();
                }
            } else if (jumpCount == 1) {
                boolean night = phase.equals("NIGHT");
                velY = night ? JUMP_FORCE_N2 : JUMP_FORCE_D2;
                jumpCount = 2;
                input.consumeJump();
                moveCtrl.onDoubleJump();
                particles.spawn(x + W / 2f, y + H / 2f, ParticleSystem.Type.JUMP);
            }
        }

        // ── Knockback ─────────────────────────────────────────────────────
        if (knockX != 0 || knockY != 0) {
            x += knockX;
            y += knockY;
            knockX *= 0.82f;
            knockY *= 0.82f;
            if (Math.abs(knockX) < 0.5f)
                knockX = 0;
            if (Math.abs(knockY) < 0.5f)
                knockY = 0;
        }

        // ── Attack ────────────────────────────────────────────────────────
        if (input.attackJustPressed && !isAttacking) {
            isAttacking = true;
            attackTimer = ATTACK_DURATION;
            moveCtrl.onAttack();
            float sx = facingRight ? x + W + 10 : x - 10;
            particles.spawn(sx, y + H / 2f, ParticleSystem.Type.SLASH, facingRight);
        }
        if (isAttacking) {
            attackTimer--;
            if (attackTimer <= 0)
                isAttacking = false;
        }

        // ── Fall off screen ───────────────────────────────────────────────
        if (y > screenH + 100) {
            takeDamage();
            respawn();
        }

        // ── Squash & Stretch ──────────────────────────────────────────────
        moveCtrl.update(velY);

        // ── Animation state machine ───────────────────────────────────────
        updateAnimState();
    }

    private void updateAnimState() {
        String next;
        if (isDashing)
            next = "dash";
        else if (isAttacking)
            next = "attack";
        else if (!onGround && velY < 0)
            next = "jump";
        else if (!onGround)
            next = "fall";
        else if (touchingWallLeft || touchingWallRight)
            next = "wall";
        else if (Math.abs(velX) > 0.5f)
            next = "run";
        else
            next = "idle";

        if (!next.equals(prevState)) {
            animFrame = 0;
            animTick = 0;
            prevState = next;
        }
        animState = next;

        animTick++;
        if (animTick >= 8) {
            animTick = 0;
            int max = switch (animState) {
                case "run" -> 6;
                case "idle" -> 4;
                case "dash" -> 2;
                case "attack" -> 3;
                default -> 1;
            };
            if (++animFrame >= max)
                animFrame = 0;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // DRAW
    // ─────────────────────────────────────────────────────────────────────
    public void draw(Graphics2D g2, String phase) {

        // ── Ghost afterimage (behind player) ─────────────────────────────
        for (float[] ghost : ghosts) {
            int ga = (int) (ghost[2] * 120);
            if (ga <= 0)
                continue;
            AffineTransform gt = g2.getTransform();
            float gcx = ghost[0] + W / 2f, gcy = ghost[1] + H / 2f;
            g2.translate(gcx, gcy);
            g2.scale(ghost[3], ghost[4]);
            g2.translate(-gcx, -gcy);
            g2.setColor(new Color(80, 140, 255, ga));
            g2.fillRoundRect((int) ghost[0], (int) ghost[1], W, H, 6, 6);
            g2.setTransform(gt);
        }

        // ── Invisibility shimmer — draw translucent shell, skip solid body ─
        if (isInvisible) {
            float prog = (float) invisibleTimer / INVISIBLE_DURATION;
            int shimAlpha = (int) (30 + 25 * abilityPulse); // barely visible
            // Ripple ring expanding outward
            long now = System.currentTimeMillis();
            for (int r = 0; r < 3; r++) {
                float ringOff = (now * 0.003f + r * 1.4f) % (float) (Math.PI * 2);
                int rSize = 8 + (int) (Math.sin(ringOff) * 6);
                g2.setColor(new Color(200, 220, 255, shimAlpha / 2));
                g2.drawOval((int) x - rSize + W / 2, (int) y - rSize + H / 2,
                        W + rSize * 2, H + rSize * 2);
            }
            // Ghost outline
            g2.setColor(new Color(180, 200, 255, shimAlpha));
            g2.fillRoundRect((int) x + 2, (int) y, W - 4, H, 8, 8);
            g2.fillOval((int) x + 5, (int) y - 12, 25, 23);
            // "INVISIBLE" indicator
            if (prog > 0.8f || (int) (now / 300) % 2 == 0) {
                g2.setFont(new Font("Arial Narrow", Font.BOLD, 11));
                g2.setColor(new Color(200, 220, 255, 200));
                g2.drawString("INVISIBLE", (int) x - 10, (int) y - 20);
            }
            return; // Skip drawing solid player body
        }

        // ── Flicker when invincible (but not invisible) ────────────────────
        if (invincibleTimer > 0 && (invincibleTimer % 8 < 4))
            return;

        // ── Apply squash & stretch transform ──────────────────────────────
        AffineTransform saved = g2.getTransform();
        float cx = x + W / 2f, cy = y + H / 2f;
        g2.translate(cx, cy);
        g2.scale(moveCtrl.scaleX, moveCtrl.scaleY);
        g2.translate(-cx, -cy);

        // ── Time Warp visual: orange tint halo ────────────────────────────
        if (isTimeWarp) {
            float tw = (float) timeWarpTimer / TIME_WARP_DURATION;
            int twa = (int) (60 * abilityPulse);
            g2.setColor(new Color(255, 150, 30, twa));
            g2.fillOval((int) x - 10, (int) y - 14, W + 20, H + 18);
        }

        boolean night = phase.equals("NIGHT");
        boolean dusk = phase.equals("DUSK");
        int ix = (int) x, iy = (int) y;

        // ── Scarf / tail cloth (drawn first, behind body) ─────────────────
        // Scarf trails opposite to movement; dashes far back
        int scarfOff = isDashing
                ? (facingRight ? -55 : 55)
                : (int) Math.max(-42, Math.min(42, -velX * 3.8f));
        int scarfRoot = facingRight ? ix + 8 : ix + W - 8;
        // Strip 1 – wide, shorter
        Color sc1 = new Color(35, 25, 70, 210);
        int[] s1x = { scarfRoot, scarfRoot + scarfOff,
                scarfRoot + scarfOff + (facingRight ? -10 : 10), scarfRoot + 9 * (facingRight ? -1 : 1) };
        int[] s1y = { iy + 10, iy + 16, iy + 44, iy + 38 };
        g2.setColor(sc1);
        g2.fillPolygon(s1x, s1y, 4);
        // Strip 2 – narrow, longer
        Color sc2 = new Color(25, 15, 55, 165);
        int[] s2x = { scarfRoot + (facingRight ? 2 : -2), scarfRoot + scarfOff * 2 / 3,
                scarfRoot + scarfOff * 2 / 3 + (facingRight ? -6 : 6), scarfRoot + 5 * (facingRight ? -1 : 1) };
        int[] s2y = { iy + 20, iy + 25, iy + 62, iy + 56 };
        g2.setColor(sc2);
        g2.fillPolygon(s2x, s2y, 4);
        // Scarf edge highlight
        g2.setColor(new Color(80, 55, 140, 100));
        g2.drawLine(s1x[0], s1y[0], s1x[1], s1y[1]);

        // ── Katana sheathed (diagonal behind body, handle at shoulder) ─────
        if (!isAttacking && night) {
            int ksx = facingRight ? ix + W / 2 - 4 : ix + W / 2 + 4;
            int kex = facingRight ? ix - 10 : ix + W + 10;
            // Saya (scabbard)
            g2.setColor(new Color(25, 15, 38));
            g2.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(ksx, iy + 12, kex, iy + 40);
            // Blade glint
            g2.setColor(new Color(160, 165, 210, 160));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(ksx, iy + 12, kex, iy + 40);
            g2.setStroke(new BasicStroke(1f));
            // Tsuka (handle) wrapping
            g2.setColor(new Color(80, 55, 30));
            g2.fillRoundRect(ksx - 3, iy + 10, 7, 12, 2, 2);
            g2.setColor(new Color(120, 85, 40, 180));
            for (int ti = 0; ti < 3; ti++)
                g2.drawLine(ksx - 3, iy + 12 + ti * 3, ksx + 4, iy + 12 + ti * 3);
        }

        // ── Tabi boots (split-toe ninja boots) ────────────────────────────
        Color bootBase = new Color(14, 9, 22);
        Color bootEdge = new Color(28, 18, 40);
        // Left boot
        g2.setColor(bootBase);
        g2.fillRoundRect(ix + 2, iy + H - 11, 14, 13, 5, 5);
        g2.setColor(bootEdge);
        g2.fillRect(ix + 3, iy + H - 11, 12, 4); // ankle cuff
        g2.setColor(new Color(8, 5, 15));
        g2.drawLine(ix + 9, iy + H - 5, ix + 9, iy + H + 2); // toe split
        // Right boot
        g2.setColor(bootBase);
        g2.fillRoundRect(ix + W - 16, iy + H - 11, 14, 13, 5, 5);
        g2.setColor(bootEdge);
        g2.fillRect(ix + W - 15, iy + H - 11, 12, 4);
        g2.setColor(new Color(8, 5, 15));
        g2.drawLine(ix + W - 9, iy + H - 5, ix + W - 9, iy + H + 2);

        // ── Hakama (wide flowing leg panels) ──────────────────────────────
        Color hakamaCol = new Color(18, 12, 32);
        Color hakamaLine = new Color(32, 22, 52);
        // Left panel
        g2.setColor(hakamaCol);
        g2.fillRoundRect(ix + 1, iy + 31, 16, 22, 3, 3);
        g2.setColor(hakamaLine);
        g2.drawLine(ix + 2, iy + 36, ix + 14, iy + 36);
        g2.drawLine(ix + 2, iy + 41, ix + 14, iy + 41);
        // Right panel
        g2.setColor(hakamaCol);
        g2.fillRoundRect(ix + W - 17, iy + 31, 16, 22, 3, 3);
        g2.setColor(hakamaLine);
        g2.drawLine(ix + W - 16, iy + 36, ix + W - 4, iy + 36);
        g2.drawLine(ix + W - 16, iy + 41, ix + W - 4, iy + 41);

        // ── Obi / Hip sash ────────────────────────────────────────────────
        Color obiCol = night ? new Color(75, 42, 130) : dusk ? new Color(90, 55, 30) : new Color(70, 65, 55);
        g2.setColor(obiCol);
        g2.fillRect(ix + 2, iy + 29, W - 4, 5);
        // Sash knot
        g2.setColor(new Color(Math.min(255, obiCol.getRed() + 35), obiCol.getGreen() + 25,
                Math.min(255, obiCol.getBlue() + 35)));
        g2.fillRoundRect(facingRight ? ix + W - 11 : ix + 1, iy + 28, 10, 7, 3, 3);

        // ── Chest plate (layered armor) ────────────────────────────────────
        Color plateBase = night ? new Color(238, 242, 255) : dusk ? new Color(195, 170, 120) : new Color(150, 145, 130);
        Color plateShadow = night ? new Color(170, 178, 220) : dusk ? new Color(140, 110, 70) : new Color(100, 96, 85);
        Color plateGold = night ? new Color(255, 215, 80) : dusk ? new Color(200, 155, 60) : new Color(170, 140, 55);
        // Main plate
        g2.setPaint(new GradientPaint(ix + 3, iy + 9, plateBase, ix + W - 3, iy + 31, plateShadow));
        g2.fillRoundRect(ix + 3, iy + 9, W - 6, 23, 6, 6);
        // Lamellar armor lines
        g2.setColor(new Color(100, 110, 150, 80));
        g2.drawLine(ix + 5, iy + 15, ix + W - 5, iy + 15);
        g2.drawLine(ix + 5, iy + 21, ix + W - 5, iy + 21);
        g2.drawLine(ix + 5, iy + 27, ix + W - 5, iy + 27);
        // Gold trim border
        g2.setColor(plateGold);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(ix + 3, iy + 9, W - 6, 23, 6, 6);
        g2.setStroke(new BasicStroke(1f));
        // Center vertical gold line
        g2.setColor(new Color(plateGold.getRed(), plateGold.getGreen(), plateGold.getBlue(), 180));
        g2.drawLine(ix + W / 2, iy + 10, ix + W / 2, iy + 30);

        // Crescent moon emblem (night only)
        if (night) {
            g2.setColor(new Color(255, 242, 145, 215));
            g2.fillArc(ix + 10, iy + 15, 15, 12, 25, 195);
            g2.setColor(plateBase);
            g2.fillOval(ix + 13, iy + 16, 10, 10);
        } else {
            // Sun emblem for day
            g2.setColor(new Color(255, 215, 60, 180));
            g2.fillOval(ix + W / 2 - 5, iy + 18, 10, 10);
        }

        // ── Pauldrons (shoulder plates) ────────────────────────────────────
        Color pauldCol = night ? new Color(58, 52, 80) : new Color(85, 78, 68);
        Color pauldEdge = night ? new Color(118, 110, 160) : new Color(140, 130, 110);
        // Left pauldron
        g2.setColor(pauldCol);
        g2.fillRoundRect(ix - 5, iy + 8, 14, 15, 5, 5);
        g2.setColor(pauldEdge);
        g2.drawLine(ix - 4, iy + 9, ix + 7, iy + 9);
        g2.setColor(plateGold);
        g2.drawLine(ix - 5, iy + 8, ix + 9, iy + 8);
        // Right pauldron
        g2.setColor(pauldCol);
        g2.fillRoundRect(ix + W - 9, iy + 8, 14, 15, 5, 5);
        g2.setColor(pauldEdge);
        g2.drawLine(ix + W - 8, iy + 9, ix + W + 3, iy + 9);
        g2.setColor(plateGold);
        g2.drawLine(ix + W - 9, iy + 8, ix + W + 5, iy + 8);

        // ── Kote / forearm guards ─────────────────────────────────────────
        Color koteCol = new Color(38, 32, 55);
        g2.setColor(koteCol);
        g2.fillRoundRect(ix - 3, iy + 20, 9, 13, 3, 3);
        g2.fillRoundRect(ix + W - 6, iy + 20, 9, 13, 3, 3);
        g2.setColor(pauldEdge);
        g2.drawLine(ix - 2, iy + 21, ix + 4, iy + 21);
        g2.drawLine(ix + W - 5, iy + 21, ix + W + 1, iy + 21);

        // ── Head / Balaclava ─────────────────────────────────────────────
        Color hoodBase = night ? new Color(205, 212, 245) : dusk ? new Color(150, 140, 110) : new Color(115, 108, 92);
        Color hoodDark = night ? new Color(130, 138, 180) : dusk ? new Color(95, 88, 68) : new Color(72, 66, 55);
        Color hoodGold = plateGold;
        // Hood back flap (behind head)
        g2.setColor(hoodDark);
        int[] flapX = { ix + (facingRight ? 14 : W - 14), ix + (facingRight ? -5 : W + 5),
                ix + (facingRight ? 9 : W - 9) };
        int[] flapY = { iy - 7, iy + 8, iy + 13 };
        g2.fillPolygon(flapX, flapY, 3);
        // Head shape
        g2.setColor(hoodBase);
        g2.fillOval(ix + 5, iy - 13, 25, 24);
        // Hood shadow top
        g2.setColor(hoodDark);
        g2.fillOval(ix + 6, iy - 13, 23, 14);
        // Lower shadow
        g2.setColor(new Color(0, 0, 0, 25));
        g2.fillOval(ix + 6, iy - 4, 23, 12);
        // Gold trim at hood bottom
        g2.setColor(hoodGold);
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawArc(ix + 5, iy - 13, 25, 24, 185, 180);
        g2.setStroke(new BasicStroke(1f));

        // ── Face wrap / ninja mask ────────────────────────────────────────
        Color wrapCol = night ? new Color(145, 150, 192) : dusk ? new Color(95, 88, 68) : new Color(75, 70, 58);
        Color wrapDark = new Color(Math.max(0, wrapCol.getRed() - 35), Math.max(0, wrapCol.getGreen() - 35),
                Math.max(0, wrapCol.getBlue() - 35));
        g2.setColor(wrapCol);
        g2.fillRoundRect(ix + 6, iy - 3, 23, 9, 4, 4);
        // Wrap fabric lines
        g2.setColor(wrapDark);
        g2.drawLine(ix + 7, iy, ix + 28, iy);
        g2.drawLine(ix + 7, iy + 3, ix + 28, iy + 3);
        g2.drawLine(ix + 7, iy + 6, ix + 28, iy + 6);

        // ── Eye slit (glowing narrow slot) ────────────────────────────────
        int eyeX = facingRight ? ix + 19 : ix + 8;
        Color eyeCore = night ? new Color(140, 228, 255) : dusk ? new Color(255, 180, 80) : new Color(210, 175, 65);
        Color eyeGlow = new Color(eyeCore.getRed(), eyeCore.getGreen(), eyeCore.getBlue(), 110);
        // Outer glow
        g2.setColor(eyeGlow);
        g2.fillOval(eyeX - 2, iy - 5, 14, 8);
        // Eye slit (horizontal rect)
        g2.setColor(eyeCore);
        g2.fillRect(eyeX, iy - 4, 10, 5);
        // Bright pupil
        g2.setColor(new Color(230, 245, 255, 235));
        g2.fillOval(eyeX + 3, iy - 3, 4, 3);

        // ── Katana drawn (attack) ─────────────────────────────────────────
        if (isAttacking) {
            float prog = 1f - (float) attackTimer / ATTACK_DURATION;
            if (night) {
                int kx1 = facingRight ? ix + W - 2 : ix + 2;
                int kx2 = facingRight ? ix + W + 40 : ix - 40;
                // Blade glow
                g2.setColor(new Color(170, 200, 255, (int) (65 * (1f - prog))));
                g2.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(kx1, iy + 24, kx2, iy + 20);
                // Blade body
                g2.setColor(new Color(205, 220, 255, 240));
                g2.setStroke(new BasicStroke(3.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(kx1, iy + 24, kx2, iy + 20);
                // Blade edge
                g2.setColor(new Color(245, 250, 255, 200));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawLine(kx1, iy + 22, kx2, iy + 18);
                g2.setStroke(new BasicStroke(1f));
                // Tsuba (guard)
                g2.setColor(new Color(160, 140, 65));
                g2.fillOval(kx1 - 4, iy + 20, 8, 9);
                g2.setColor(plateGold);
                g2.drawOval(kx1 - 4, iy + 20, 8, 9);
            } else {
                // Kunai / dagger in day mode
                int kx = facingRight ? ix + W + 4 : ix - 22;
                g2.setColor(new Color(185, 175, 145));
                g2.fillRect(kx, iy + 26, 18, 4);
                int[] tpX = facingRight ? new int[] { kx + 18, kx + 25, kx + 18 } : new int[] { kx, kx - 7, kx };
                g2.fillPolygon(tpX, new int[] { iy + 24, iy + 28, iy + 32 }, 3);
                g2.setColor(new Color(120, 100, 80));
                g2.fillRect(kx + (facingRight ? -2 : 16), iy + 23, 5, 10);
            }
        }

        // ── Attack arc ────────────────────────────────────────────────────
        if (isAttacking) {
            float prog = 1f - (float) attackTimer / ATTACK_DURATION;
            int arcX = facingRight ? ix + W : ix - 52;
            g2.setColor(new Color(200, 220, 255, (int) (110 * (1f - prog))));
            g2.fillArc(arcX, iy, 52, 52, facingRight ? -75 : 105, 140);
            g2.setColor(new Color(225, 238, 255, 240));
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawArc(arcX + 2, iy + 2, 48, 48, facingRight ? -75 : 105, 140);
            g2.setStroke(new BasicStroke(1f));
        }

        // ── Restore squash & stretch ───────────────────────────────────────
        g2.setTransform(saved);
    }

    // ── Combat ────────────────────────────────────────────────────────────
    public void takeDamage() {
        if (invincibleTimer > 0)
            return;
        health--;
        invincibleTimer = 140; // longer iframes = easier
    }

    public void knockBack(boolean hitFromLeft) {
        knockX = hitFromLeft ? 12f : -12f;
        knockY = -9f;
    }

    public void respawn() {
        x = 100;
        y = 380;
        velX = 0;
        velY = 0;
        jumpCount = 0;
        canDash = true;
        knockX = 0;
        knockY = 0;
        moveCtrl.reset();
        ghosts.clear();
        // Reset abilities
        isInvisible = false;
        invisibleTimer = 0;
        isTimeWarp = false;
        timeWarpTimer = 0;
        isShadowClone = false;
        shadowCloneTimer = 0;
        timeWarpCooldown = 0;
    }

    public boolean isDead() {
        return health <= 0;
    }

    public Rectangle getRect() {
        return new Rectangle((int) x, (int) y, W, H);
    }

    /**
     * Returns the sword/katana attack hitbox — a rectangle extending forward
     * from the player's body during an attack swing.
     */
    public Rectangle getAttackBox() {
        int reach = 55;
        int ax = facingRight ? (int) x + W : (int) x - reach;
        return new Rectangle(ax, (int) y + 5, reach, H - 10);
    }

    // ── Ability System ────────────────────────────────────────────────────
    /**
     * Activate the phase ability. Called by GamePanel on Q press.
     * 
     * @param phase current time phase string
     * @param heals how many heal charges remain
     * @return heal cost deducted (0 or positive), or -1 if cannot activate
     */
    public int activateAbility(String phase, int heals) {
        switch (phase) {
            case "DAY" -> {
                if (heals < 2 || isInvisible)
                    return -1;
                isInvisible = true;
                invisibleTimer = INVISIBLE_DURATION;
                invincibleTimer = INVISIBLE_DURATION; // also make immune to hits
                particles.spawn(x + W / 2f, y + H / 2f, ParticleSystem.Type.MOON);
                particles.spawn(x + W / 2f, y + H / 2f, ParticleSystem.Type.SPARK);
                return 2;
            }
            case "DUSK" -> {
                if (timeWarpCooldown > 0 || isTimeWarp)
                    return -1;
                isTimeWarp = true;
                timeWarpTimer = TIME_WARP_DURATION;
                particles.spawn(x + W / 2f, y + H / 2f, ParticleSystem.Type.MOON);
                return 0;
            }
            case "NIGHT" -> {
                if (heals < 1 || isShadowClone)
                    return -1;
                isShadowClone = true;
                shadowCloneTimer = CLONE_DURATION;
                // Place 2 clones: one behind, one on the opposite side
                clonePositions[0] = x + (facingRight ? -100f : 100f);
                clonePositions[1] = y;
                clonePositions[2] = x + (facingRight ? -200f : 200f);
                clonePositions[3] = y + 20f;
                particles.spawn(clonePositions[0] + W / 2f, clonePositions[1] + H / 2f, ParticleSystem.Type.MOON);
                particles.spawn(clonePositions[2] + W / 2f, clonePositions[3] + H / 2f, ParticleSystem.Type.MOON);
                return 1;
            }
            default -> {
                return -1;
            }
        }
    }

    /**
     * Draw shadow clone decoys (called from GamePanel before player draw).
     */
    public void drawClones(Graphics2D g2) {
        if (!isShadowClone)
            return;
        float prog = (float) shadowCloneTimer / CLONE_DURATION;
        int alpha = (int) (prog * 140 + 30);
        for (int c = 0; c < 2; c++) {
            int cx = (int) clonePositions[c * 2];
            int cy = (int) clonePositions[c * 2 + 1];
            // Pulsing silhouette
            float pulse = (float) (0.6 + 0.4 * Math.sin(System.currentTimeMillis() * 0.006 + c * 2.1));
            int a = (int) (alpha * pulse);
            g2.setColor(new Color(100, 60, 220, a));
            g2.fillRoundRect(cx + 3, cy, W - 6, H, 6, 6);
            // Head
            g2.fillOval(cx + 5, cy - 13, 25, 24);
            // Glow ring
            g2.setColor(new Color(180, 130, 255, a / 3));
            g2.fillOval(cx - 8, cy - 18, W + 16, H + 20);
            // Shimmer line
            g2.setColor(new Color(200, 170, 255, (int) (a * 0.7)));
            g2.drawLine(cx + 5, cy + (int) (System.currentTimeMillis() % 50),
                    cx + W - 5, cy + (int) (System.currentTimeMillis() % 50));
        }
    }
}