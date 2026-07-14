package enemy;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.List;

/**
 * Boss — Shadow Commander.
 *
 * Three combat phases based on remaining HP.
 * Cinematic drop: y animates from off-screen to spawnY.
 * Phase 1: slow patrol + single bullet aimed at player.
 * Phase 2: faster + charge attack + 3-bullet spread.
 * Phase 3: rage aura + rapid fire 5-bullet spread.
 */
public class Boss {

    public float x, y;
    public static final int W = 62, H = 82;

    public int maxHealth = 15;
    public int health = 15;
    public int invincibleTimer = 0;
    private static final int IFRAME = 12;

    // ── State ─────────────────────────────────────────────────────────────
    public boolean dead = false;
    public int deathTimer = 0;
    private static final int DEATH_FRAMES = 55;

    // Spawn
    private final float spawnX, spawnY;
    public boolean dropping = false;
    public boolean fighting = false;

    // ── Combat movement ───────────────────────────────────────────────────
    private float velX = 0;
    private int direction = -1;
    private final int leftBound, rightBound;

    // Shoot
    private int shootTimer = 0;

    // Charge
    private boolean isCharging = false;
    private float chargeVelX = 0;
    private int chargeFrames = 0;
    private int chargeReadyTimer = 0;

    // ── Visual ────────────────────────────────────────────────────────────
    private float animTick = 0;
    private float auraPulse = 0;
    public boolean facingRight = false;

    // Muzzle flash
    private int muzzleFlash = 0;

    public Boss(float spawnX, float spawnY, int leftBound, int rightBound) {
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.x = spawnX;
        this.y = spawnY - 520; // starts above screen
        this.leftBound = leftBound;
        this.rightBound = rightBound;
    }

    /** Called when cinematic triggers — begin drop animation. */
    public void startDrop() {
        dropping = true;
    }

    /** Called by GamePanel when cinematic finishes — begin actual fight. */
    public void startFight() {
        dropping = false;
        fighting = true;
        y = spawnY;
    }

    /** Returns 1, 2, or 3 based on remaining HP. */
    public int getPhase() {
        if (health > 10) return 1;
        if (health > 5)  return 2;
        return 3;
    }

    // ── Update ────────────────────────────────────────────────────────────
    /** @return true when the boss is fully gone and should be cleaned up. */
    public boolean update(float playerX, float playerY, List<Bullet> bullets,
                          Rectangle[] platforms) {
        animTick++;
        auraPulse = (float)(0.55 + 0.45 * Math.sin(animTick * 0.055));
        if (muzzleFlash > 0) muzzleFlash--;

        if (dead) {
            deathTimer--;
            return deathTimer <= 0;
        }

        // ── Drop animation ────────────────────────────────────────────────
        if (dropping) {
            y += (spawnY - y) * 0.10f;
            if (Math.abs(y - spawnY) < 3) {
                y = spawnY;
                // drop finished — GamePanel will call startFight() when cinematic ends
            }
            return false;
        }

        if (!fighting) return false;

        if (invincibleTimer > 0) invincibleTimer--;

        int phase = getPhase();
        facingRight = playerX > x + W / 2f;

        // ── Movement ──────────────────────────────────────────────────────
        if (isCharging) {
            x += chargeVelX;
            chargeFrames--;
            if (chargeFrames <= 0 || x < leftBound || x + W > rightBound) {
                isCharging = false;
            }
        } else {
            float speed = phase == 1 ? 1.8f : phase == 2 ? 2.8f : 3.8f;
            x += speed * direction;
            if (x <= leftBound) { x = leftBound; direction = 1; }
            if (x + W >= rightBound) { x = rightBound - W; direction = -1; }
        }

        // ── Shooting ──────────────────────────────────────────────────────
        int shootInterval = phase == 1 ? 80 : phase == 2 ? 55 : 38;
        if (++shootTimer >= shootInterval) {
            shootTimer = 0;
            fire(playerX, playerY, bullets, phase);
            muzzleFlash = 8;
        }

        // ── Charge (phase 2+) ─────────────────────────────────────────────
        if (phase >= 2 && !isCharging) {
            int chargeInterval = phase == 2 ? 200 : 130;
            if (++chargeReadyTimer >= chargeInterval) {
                chargeReadyTimer = 0;
                isCharging = true;
                chargeVelX = playerX > x ? 14f : -14f;
                chargeFrames = 35;
            }
        }

        // ── Gravity / platform snap ────────────────────────────────────────
        y += 3f;
        for (Rectangle p : platforms) {
            if (getRect().intersects(p)) {
                if (y + H >= p.y && y + H <= p.y + p.height + 5) {
                    y = p.y - H;
                }
            }
        }

        return false;
    }

    private void fire(float playerX, float playerY, List<Bullet> bullets, int phase) {
        int count = phase == 1 ? 1 : phase == 2 ? 3 : 5;
        float gunX = x + (facingRight ? W + 5 : -5);
        float gunY = y + H * 0.45f;
        float baseAngle = (float)Math.atan2(playerY - gunY, playerX - gunX);
        float spread = phase == 1 ? 0f : 0.18f;

        for (int i = 0; i < count; i++) {
            float angle = baseAngle + (i - count / 2) * spread;
            float speed = phase == 3 ? 10f : 8f;
            bullets.add(new Bullet(gunX, gunY,
                    (float)Math.cos(angle) * speed,
                    (float)Math.sin(angle) * speed,
                    true));
        }
    }

    public void takeDamage(int amount) {
        if (invincibleTimer > 0 || dead) return;
        health -= amount;
        invincibleTimer = IFRAME;
        if (health <= 0) {
            health = 0;
            dead = true;
            deathTimer = DEATH_FRAMES;
        }
    }

    public Rectangle getRect() {
        return new Rectangle((int)x, (int)y, W, H);
    }

    // ── Draw ──────────────────────────────────────────────────────────────
    public void draw(Graphics2D g2) {
        if (dead) { drawDeath(g2); return; }

        int phase = getPhase();
        int dx = (int)x;
        int dy = (int)y;

        // Flash white every 4 frames during iframes (subtle, not invisible)
        if (invincibleTimer > 0 && (invincibleTimer % 8 < 2)) return;

        // ── Phase aura ────────────────────────────────────────────────────
        if (phase == 3) {
            int a = (int)(50 + 40 * auraPulse);
            g2.setColor(new Color(220, 30, 30, a));
            g2.fillOval(dx - 24, dy - 20, W + 48, H + 40);
            g2.setColor(new Color(255, 80, 30, a / 2));
            g2.fillOval(dx - 12, dy - 10, W + 24, H + 20);
        } else if (phase == 2) {
            int a = (int)(25 + 20 * auraPulse);
            g2.setColor(new Color(255, 130, 20, a));
            g2.fillOval(dx - 12, dy - 10, W + 24, H + 20);
        }

        // Ground shadow
        g2.setColor(new Color(0, 0, 0, 55));
        g2.fillOval(dx + 6, dy + H + 2, W - 12, 10);

        // ── Cape ──────────────────────────────────────────────────────────
        Color capeBase = new Color(12, 6, 20);
        Color capeEdge = new Color(55, 20, 75);
        int[] capeXl = { dx + 5, dx - 24, dx + 10 };
        int[] capeYl = { dy + 18, dy + H + 18, dy + H + 12 };
        g2.setColor(capeBase);
        g2.fillPolygon(capeXl, capeYl, 3);
        int[] capeXr = { dx + W - 5, dx + W + 24, dx + W - 10 };
        int[] capeYr = { dy + 18, dy + H + 18, dy + H + 12 };
        g2.fillPolygon(capeXr, capeYr, 3);
        int[] capeXc = { dx + 8, dx - 28, dx + W / 2, dx + W + 28, dx + W - 8 };
        int[] capeYc = { dy + 16, dy + H + 24, dy + H + 36, dy + H + 24, dy + 16 };
        g2.setColor(new Color(8, 4, 16));
        g2.fillPolygon(capeXc, capeYc, 5);
        g2.setColor(capeEdge);
        g2.drawLine(dx + 8, dy + 16, dx - 28, dy + H + 24);
        g2.drawLine(dx + W - 8, dy + 16, dx + W + 28, dy + H + 24);

        // ── Legs / Greaves ────────────────────────────────────────────────
        g2.setColor(new Color(16, 10, 28));
        g2.fillRoundRect(dx + 8, dy + H - 24, 20, 30, 5, 5);
        g2.fillRoundRect(dx + W - 28, dy + H - 24, 20, 30, 5, 5);
        // Knee plates
        Color kneeCol = new Color(35, 22, 55);
        g2.setColor(kneeCol);
        g2.fillRoundRect(dx + 9, dy + H - 18, 18, 14, 4, 4);
        g2.fillRoundRect(dx + W - 27, dy + H - 18, 18, 14, 4, 4);
        g2.setColor(new Color(80, 50, 110, 140));
        g2.drawRoundRect(dx + 9, dy + H - 18, 18, 14, 4, 4);
        g2.drawRoundRect(dx + W - 27, dy + H - 18, 18, 14, 4, 4);

        // ── Body ──────────────────────────────────────────────────────────
        Color chestColor = phase == 3 ? new Color(50, 10, 10)
                : phase == 2 ? new Color(30, 14, 40)
                : new Color(20, 12, 32);
        Color chestLight = phase == 3 ? new Color(70, 20, 20)
                : phase == 2 ? new Color(42, 22, 58)
                : new Color(32, 20, 50);
        g2.setPaint(new GradientPaint(dx + 2, dy + 14, chestLight,
                dx + W - 2, dy + H - 22, chestColor));
        g2.fillRoundRect(dx + 2, dy + 14, W - 4, H - 32, 9, 9);

        // Armor engrave lines
        Color etchCol = phase == 3 ? new Color(220, 60, 30, 140)
                : phase == 2 ? new Color(220, 130, 30, 110)
                : new Color(110, 65, 180, 90);
        g2.setColor(etchCol);
        g2.drawLine(dx + 8, dy + 26, dx + W - 8, dy + 26);
        g2.drawLine(dx + 8, dy + 36, dx + W - 8, dy + 36);
        g2.drawLine(dx + 8, dy + 46, dx + W - 8, dy + 46);

        // Central chest orb
        int orbA = (int)(190 + 65 * auraPulse);
        Color orbC = phase == 3 ? new Color(255, 50, 30, orbA)
                : phase == 2 ? new Color(255, 140, 30, orbA)
                : new Color(220, 30, 30, orbA);
        g2.setColor(orbC);
        g2.fillOval(dx + W / 2 - 9, dy + 31, 18, 18);
        g2.setColor(new Color(255, 200, 200, orbA / 2));
        g2.fillOval(dx + W / 2 - 5, dy + 35, 10, 10);
        g2.setColor(new Color(255, 255, 255, 200));
        g2.fillOval(dx + W / 2 - 2, dy + 37, 5, 5);

        // ── Pauldrons + Spikes ────────────────────────────────────────────
        Color paulC = phase == 3 ? new Color(40, 8, 8) : new Color(22, 12, 34);
        g2.setColor(paulC);
        g2.fillRoundRect(dx - 12, dy + 10, 22, 26, 6, 6);
        g2.fillRoundRect(dx + W - 10, dy + 10, 22, 26, 6, 6);
        g2.setColor(new Color(80, 50, 110, 130));
        g2.drawRoundRect(dx - 12, dy + 10, 22, 26, 6, 6);
        g2.drawRoundRect(dx + W - 10, dy + 10, 22, 26, 6, 6);

        Color spikeC = phase >= 2 ? new Color(230, 60, 30) : new Color(160, 80, 210);
        // Left spikes
        int[][] lSpikes = {{dx - 8, dy + 12, dx - 16, dy + 2, dx - 4, dy + 2},
                           {dx,     dy + 12, dx - 6,  dy + 1, dx + 5, dy + 1}};
        for (int[] s : lSpikes) {
            g2.setColor(spikeC);
            g2.fillPolygon(new int[]{s[0], s[2], s[4]}, new int[]{s[1], s[3], s[5]}, 3);
        }
        // Right spikes
        int[][] rSpikes = {{dx + W + 8, dy + 12, dx + W + 16, dy + 2, dx + W + 4, dy + 2},
                           {dx + W,     dy + 12, dx + W + 6,  dy + 1, dx + W - 5, dy + 1}};
        for (int[] s : rSpikes) {
            g2.setColor(spikeC);
            g2.fillPolygon(new int[]{s[0], s[2], s[4]}, new int[]{s[1], s[3], s[5]}, 3);
        }
        int spkGlow = (int)(70 + 60 * auraPulse);
        g2.setColor(new Color(spikeC.getRed(), spikeC.getGreen(), spikeC.getBlue(), spkGlow));
        g2.fillOval(dx - 20, dy, 18, 18);
        g2.fillOval(dx + W + 2, dy, 18, 18);

        // ── Left arm — Broadsword ─────────────────────────────────────────
        g2.setColor(new Color(20, 13, 32));
        g2.fillRoundRect(dx - 10, dy + 22, 14, 36, 4, 4);
        // Sword blade (diagonal)
        Color bladeC = phase >= 2 ? new Color(255, 160, 50) : new Color(190, 195, 230);
        int[] bldX = {dx - 14, dx - 10, dx - 6, dx - 10};
        int[] bldY = {dy + 20, dy + 22, dy + 60, dy + 62};
        g2.setColor(new Color(20, 13, 32));
        g2.fillPolygon(bldX, bldY, 4);
        g2.setColor(bladeC);
        g2.drawLine(dx - 14, dy + 20, dx - 6, dy + 60);
        if (phase >= 2) {
            g2.setColor(new Color(255, 160, 50, (int)(80 * auraPulse)));
            g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(dx - 14, dy + 20, dx - 6, dy + 60);
            g2.setStroke(new BasicStroke(1f));
        }
        g2.setColor(new Color(80, 55, 100));
        g2.fillRect(dx - 16, dy + 57, 18, 5);

        // ── Right arm — Cannon/Gun ─────────────────────────────────────────
        g2.setColor(new Color(20, 13, 32));
        g2.fillRoundRect(dx + W - 4, dy + 22, 14, 36, 4, 4);
        // Gun body
        g2.setColor(new Color(28, 18, 22));
        g2.fillRoundRect(dx + W + 6, dy + 38, 28, 14, 5, 5);
        // Barrel
        g2.setColor(new Color(40, 28, 28));
        g2.fillRect(dx + W + 30, dy + 41, 16, 8);
        // Barrel glow
        int gunGlow = muzzleFlash > 0 ? 255 : (int)(80 + 80 * auraPulse);
        g2.setColor(new Color(255, muzzleFlash > 0 ? 200 : 100, 30, gunGlow));
        g2.fillOval(dx + W + 44, dy + 39, 8, 12);
        // Grip
        g2.setColor(new Color(20, 12, 12));
        g2.fillRoundRect(dx + W + 10, dy + 50, 10, 18, 3, 3);
        // Scope
        g2.setColor(new Color(50, 35, 35));
        g2.fillRect(dx + W + 14, dy + 35, 14, 5);
        g2.setColor(new Color(100, 200, 255, 180));
        g2.fillOval(dx + W + 14, dy + 35, 5, 5);

        // Muzzle flash
        if (muzzleFlash > 0) {
            int fa = muzzleFlash * 30;
            g2.setColor(new Color(255, 220, 100, Math.min(255, fa)));
            g2.fillOval(dx + W + 42, dy + 36, 16, 18);
            g2.setColor(Color.WHITE);
            g2.fillOval(dx + W + 47, dy + 40, 6, 8);
        }

        // ── Head / Helm ───────────────────────────────────────────────────
        Color helmBase = phase == 3 ? new Color(42, 8, 8) : new Color(18, 10, 30);
        g2.setColor(helmBase);
        g2.fillRoundRect(dx + 8, dy - 10, W - 16, 28, 9, 9);

        // Helm crest
        g2.setColor(new Color(28, 16, 44));
        int[] crestX = {dx + W / 2 - 5, dx + W / 2, dx + W / 2 + 5,
                        dx + W / 2 + 10, dx + W / 2 - 10};
        int[] crestY = {dy - 10, dy - 24, dy - 10, dy - 4, dy - 4};
        g2.fillPolygon(crestX, crestY, 5);
        // Horn spikes
        g2.setColor(spikeC);
        g2.fillPolygon(new int[]{dx + 10, dx + 6, dx + 14}, new int[]{dy - 8, dy - 18, dy - 8}, 3);
        g2.fillPolygon(new int[]{dx + W - 10, dx + W - 6, dx + W - 14}, new int[]{dy - 8, dy - 18, dy - 8}, 3);

        // Face plate
        g2.setColor(new Color(12, 7, 22));
        g2.fillRoundRect(dx + 10, dy - 1, W - 20, 20, 5, 5);

        // Eye slits
        int eyeA = (int)(200 + 55 * auraPulse);
        Color eyeC = phase == 3 ? new Color(255, 50, 30, eyeA)
                : phase == 2 ? new Color(255, 130, 30, eyeA)
                : new Color(255, 50, 20, eyeA);
        g2.setColor(eyeC);
        g2.fillRoundRect(dx + 12, dy + 4, 16, 6, 3, 3);
        g2.fillRoundRect(dx + W - 28, dy + 4, 16, 6, 3, 3);
        g2.setColor(new Color(eyeC.getRed(), eyeC.getGreen(), eyeC.getBlue(), eyeA / 2));
        g2.fillRoundRect(dx + 10, dy + 2, 20, 10, 4, 4);
        g2.fillRoundRect(dx + W - 30, dy + 2, 20, 10, 4, 4);

        // Phase 2+ energy cracks
        if (phase >= 2) {
            Color crackC = phase == 3 ? new Color(255, 80, 30) : new Color(255, 160, 50);
            g2.setColor(new Color(crackC.getRed(), crackC.getGreen(), crackC.getBlue(),
                    (int)(100 + 80 * auraPulse)));
            g2.drawLine(dx + W / 2, dy + 14, dx + W / 2 - 6, dy + 32);
            g2.drawLine(dx + W / 2 - 6, dy + 32, dx + W / 2 + 4, dy + 48);
            g2.drawLine(dx + 16, dy + 20, dx + 10, dy + 38);
        }

        // HP bar below the boss during fight
        drawHealthBar(g2, dx, dy);
    }

    private void drawHealthBar(Graphics2D g2, int dx, int dy) {
        int bw = W + 20, bh = 7;
        int bx = dx - 10, by = dy - 22;
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(bx, by, bw, bh, 4, 4);
        float ratio = (float) health / maxHealth;
        int fillW = (int)(bw * ratio);
        if (fillW > 0) {
            Color fc = ratio > 0.6f ? new Color(220, 40, 40)
                    : ratio > 0.3f ? new Color(255, 130, 20)
                    : new Color(255, 60, 30);
            g2.setColor(fc);
            g2.fillRoundRect(bx, by, fillW, bh, 4, 4);
            g2.setColor(new Color(255, 200, 200, 80));
            g2.fillRoundRect(bx, by, fillW, 3, 4, 4);
        }
        g2.setColor(new Color(160, 60, 60, 200));
        g2.drawRoundRect(bx, by, bw, bh, 4, 4);
    }

    private void drawDeath(Graphics2D g2) {
        if (deathTimer <= 0) return;
        float prog = (float) deathTimer / DEATH_FRAMES;
        AffineTransform saved = g2.getTransform();
        float cx = x + W / 2f, cy = y + H / 2f;
        float scale = 1f + (1f - prog) * 1.0f;
        g2.translate(cx, cy);
        g2.scale(scale, scale);
        g2.translate(-cx, -cy);
        int alpha = (int)(prog * 240);
        int r = Math.min(255, (int)(255 * prog + 200 * (1 - prog)));
        int gv = (int)(80 * prog);
        g2.setColor(new Color(r, gv, 30, alpha));
        g2.fillRoundRect((int)x, (int)y, W, H, 10, 10);
        g2.setTransform(saved);
    }
}
