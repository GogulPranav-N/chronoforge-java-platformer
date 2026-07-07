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
    public int maxHealth = 5;
    public int health = 5;
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
            int ga = (int) (ghost[2] * 130);
            if (ga <= 0)
                continue;
            AffineTransform gt = g2.getTransform();
            float gcx = ghost[0] + W / 2f;
            float gcy = ghost[1] + H / 2f;
            g2.translate(gcx, gcy);
            g2.scale(ghost[3], ghost[4]);
            g2.translate(-gcx, -gcy);
            g2.setColor(new Color(120, 160, 255, ga));
            g2.fillRoundRect((int) ghost[0], (int) ghost[1], W, H, 6, 6);
            g2.setTransform(gt);
        }

        // ── Flicker when invincible ────────────────────────────────────────
        if (invincibleTimer > 0 && (invincibleTimer % 8 < 4))
            return;

        // ── Apply squash & stretch transform ─────────────────────────────
        AffineTransform saved = g2.getTransform();
        float cx = x + W / 2f;
        float cy = y + H / 2f;
        g2.translate(cx, cy);
        g2.scale(moveCtrl.scaleX, moveCtrl.scaleY);
        g2.translate(-cx, -cy);

        boolean night = phase.equals("NIGHT");

        // ── Attack arc ───────────────────────────────────────────────────
        if (isAttacking) {
            float prog = 1f - (float) attackTimer / ATTACK_DURATION;
            int arcX = facingRight ? (int) x + W : (int) x - 44;
            g2.setColor(new Color(220, 220, 255, (int) (80 * (1f - prog))));
            g2.fillArc(arcX, (int) y, 44, 44, facingRight ? -60 : 120, 120);
            g2.setColor(new Color(200, 220, 255, 220));
            g2.setStroke(new BasicStroke(2f));
            g2.drawArc(arcX, (int) y + 2, 40, 40, facingRight ? -60 : 120, 120);
            g2.setStroke(new BasicStroke(1f));
        }

        // Cape (billows opposite to velX)
        g2.setColor(night ? new Color(140, 140, 240) : new Color(70, 70, 70));
        int capeOffX = (int) (-velX * 1.8f);
        int[] cx2 = { (int) x + (facingRight ? 0 : W),
                (int) x + (facingRight ? -18 : W + 18) + capeOffX,
                (int) x + (facingRight ? 6 : W - 6) };
        int[] cy2 = { (int) y + 14, (int) y + 52, (int) y + 54 };
        g2.fillPolygon(cx2, cy2, 3);

        // Split cape (second panel)
        g2.setColor(night ? new Color(100, 100, 200, 160) : new Color(50, 50, 50, 160));
        int[] cx3 = { (int) x + (facingRight ? 5 : W - 5),
                (int) x + (facingRight ? -10 : W + 10) + capeOffX,
                (int) x + (facingRight ? 10 : W - 10) };
        int[] cy3 = { (int) y + 22, (int) y + 50, (int) y + 52 };
        g2.fillPolygon(cx3, cy3, 3);

        // Ponytail
        g2.setColor(night ? new Color(200, 200, 255) : new Color(120, 100, 80));
        int ponyX = (int) x + (facingRight ? -5 : W + 1) + capeOffX / 2;
        g2.fillRoundRect(ponyX, (int) y - 2, 6, 22, 3, 3);

        // Body
        Color bodyCol = night ? Color.WHITE
                : phase.equals("DUSK")
                        ? new Color(190, 150, 90)
                        : new Color(130, 130, 130);
        g2.setColor(bodyCol);
        g2.fillRoundRect((int) x, (int) y, W, H, 6, 6);

        // Armour panel lines
        g2.setColor(new Color(150, 150, 200, 90));
        g2.drawLine((int) x + 7, (int) y + 15, (int) x + W - 7, (int) y + 15);
        g2.drawLine((int) x + 7, (int) y + 28, (int) x + W - 7, (int) y + 28);
        g2.drawLine((int) x + 7, (int) y + 38, (int) x + W - 7, (int) y + 38);

        // Crescent moon emblem (night only)
        if (night) {
            g2.setColor(new Color(255, 240, 160, 210));
            g2.fillArc((int) x + 9, (int) y + 17, 17, 14, 30, 180);
            g2.setColor(bodyCol);
            g2.fillOval((int) x + 12, (int) y + 18, 11, 10);
        }

        // Hood
        g2.setColor(night ? new Color(220, 220, 255) : new Color(100, 100, 100));
        g2.fillOval((int) x + 4, (int) y - 11, 27, 24);
        // Hood shadow
        g2.setColor(new Color(0, 0, 0, 50));
        g2.fillOval((int) x + 6, (int) y - 6, 23, 12);

        // Face wrap
        g2.setColor(night ? new Color(170, 170, 210) : new Color(80, 80, 80));
        g2.fillRoundRect((int) x + 6, (int) y + 2, 23, 7, 4, 4);

        // Glowing eye
        int eyeX = facingRight ? (int) x + 19 : (int) x + 7;
        g2.setColor(night ? new Color(160, 210, 255) : new Color(160, 140, 80));
        g2.fillOval(eyeX, (int) y - 5, 9, 6);
        g2.setColor(night ? new Color(240, 250, 255, 230) : new Color(210, 190, 100));
        g2.fillOval(eyeX + 2, (int) y - 4, 5, 4);

        // Weapon
        if (night) {
            // Katana
            int kx = facingRight ? (int) x + W : (int) x - 30;
            g2.setColor(new Color(200, 215, 255));
            g2.fillRect(kx, (int) y + 27, 30, 3);
            g2.setColor(new Color(255, 250, 200, 180)); // blade edge
            g2.drawLine(kx, (int) y + 27, kx + 30, (int) y + 27);
            g2.setColor(new Color(140, 120, 70)); // guard
            g2.fillRect(facingRight ? (int) x + W - 3 : (int) x - 1, (int) y + 23, 5, 11);
        } else {
            // Kunai
            g2.setColor(new Color(170, 160, 130));
            int kx = facingRight ? (int) x + W : (int) x - 14;
            g2.fillRect(kx, (int) y + 30, 14, 3);
            int[] tipX = facingRight ? new int[] { kx + 14, kx + 20, kx + 14 } : new int[] { kx, kx - 6, kx };
            g2.fillPolygon(tipX, new int[] { (int) y + 27, (int) y + 31, (int) y + 35 }, 3);
        }

        // ── Wall-slide effect: glow on wall side ─────────────────────────
        if (!onGround && (touchingWallLeft || touchingWallRight)) {
            boolean pressing = (touchingWallLeft && input.leftPressed)
                    || (touchingWallRight && input.rightPressed);
            if (pressing) {
                int wallSide = touchingWallRight ? (int) x + W : (int) x;
                g2.setColor(new Color(180, 180, 255, 80));
                g2.fillRect(wallSide - 2, (int) y, 4, H);
            }
        }

        // Restore transform (squash & stretch)
        g2.setTransform(saved);
    }

    // ── Combat ────────────────────────────────────────────────────────────
    public void takeDamage() {
        if (invincibleTimer > 0)
            return;
        health--;
        invincibleTimer = 90;
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
    }

    public boolean isDead() {
        return health <= 0;
    }

    public Rectangle getRect() {
        return new Rectangle((int) x, (int) y, W, H);
    }
}