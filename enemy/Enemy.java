package enemy;

import java.awt.*;
import java.util.List;

/**
 * Enemy — Tactical Gunner mercenary.
 *
 * Phase 3 additions:
 * - Shoots aimed bullets at the player on a timer
 * - Health bar above head (shown when damaged)
 * - Professional gunner visual: helmet, vest, gun arm, cargo pants
 * - Death flash + expand sequence
 */
public class Enemy {

    // ── Position ──────────────────────────────────────────────────────────
    public float x, y;

    // ── Size ──────────────────────────────────────────────────────────────
    public static final int W = 34;
    public static final int H = 50;

    // ── Movement ──────────────────────────────────────────────────────────
    public int speed = 2;
    public int direction = 1;

    // ── Patrol limits ─────────────────────────────────────────────────────
    public int leftLimit;
    public int rightLimit;

    // ── Health ────────────────────────────────────────────────────────────
    public int maxHealth = 3;
    public int health = 3;
    public int invincibleTimer = 0;
    private static final int IFRAME = 18;

    // ── Shooting ──────────────────────────────────────────────────────────
    private static final int SHOOT_INTERVAL = 130; // frames between shots (~2.2 s)
    private static final float BULLET_SPEED  = 5.5f;  // slower, more dodgeable
    private int muzzleFlash = 0;
    private int shootTimer = 0;

    // ── Death ─────────────────────────────────────────────────────────────
    public boolean dead = false;
    public int deathTimer = 0;
    private static final int DEATH_FRAMES = 22;

    // ── Visual ────────────────────────────────────────────────────────────
    private int animTick = 0;
    private float visorGlow = 0f;

    public Enemy(int x, int y, int leftLimit, int rightLimit) {
        this.x = x;
        this.y = y;
        this.leftLimit = leftLimit;
        this.rightLimit = rightLimit;
        animTick = (int)(Math.random() * 60);
    }

    // ── Update ────────────────────────────────────────────────────────────
    /**
     * @param playerX  player world X for aiming
     * @param playerY  player world Y for aiming
     * @param bullets  shared bullet list
     * @param playerInvisible  true → enemy skips shooting entirely
     * @param decoyX   shadow clone decoy X (-1 = none)
     * @param decoyY   shadow clone decoy Y
     * @return true when the death animation is complete
     */
    public boolean update(float playerX, float playerY, java.util.List<enemy.Bullet> bullets,
                          boolean playerInvisible, float decoyX, float decoyY) {
        animTick++;
        visorGlow = (float)(0.6 + 0.4 * Math.sin(animTick * 0.08));
        if (muzzleFlash > 0) muzzleFlash--;

        if (dead) {
            deathTimer--;
            return deathTimer <= 0;
        }

        if (invincibleTimer > 0) invincibleTimer--;

        // Patrol
        x += speed * direction;
        if (x <= leftLimit)     { x = leftLimit;          direction = 1;  }
        if (x + W >= rightLimit){ x = rightLimit - W;     direction = -1; }

        // Shoot — skip if player invisible
        if (!playerInvisible && ++shootTimer >= SHOOT_INTERVAL) {
            shootTimer = 0;
            muzzleFlash = 7;
            // Aim at decoy if one exists, otherwise aim at player
            float targetX = (decoyX >= 0) ? decoyX : playerX;
            float targetY = (decoyX >= 0) ? decoyY : playerY;
            float gunX = direction == 1 ? x + W + 4 : x - 8;
            float gunY = y + H * 0.42f;
            float dx = targetX - gunX;
            float dy = targetY - gunY;
            float len = (float)Math.sqrt(dx * dx + dy * dy);
            if (len > 0) {
                bullets.add(new Bullet(gunX, gunY,
                        dx / len * BULLET_SPEED, dy / len * BULLET_SPEED, false));
            }
        }

        return false;
    }

    /** Convenience overload — no ability effects (backwards compat). */
    public boolean update(float playerX, float playerY, java.util.List<enemy.Bullet> bullets) {
        return update(playerX, playerY, bullets, false, -1f, -1f);
    }

    // ── Combat ────────────────────────────────────────────────────────────
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

    public boolean isDead() { return dead; }

    // ── Collision ─────────────────────────────────────────────────────────
    public Rectangle getRect()        { return new Rectangle((int)x, (int)y, W, H); }
    /** Exact body bounds — no padding. Player must physically touch enemy to take damage. */
    public Rectangle getAttackRect()  { return new Rectangle((int)x, (int)y, W, H); }

    // ── Draw ──────────────────────────────────────────────────────────────
    public void draw(Graphics2D g2) {
        if (dead) { drawDeath(g2); return; }
        if (invincibleTimer > 0 && (invincibleTimer % 6 < 3)) return;

        boolean facingRight = direction == 1;
        int dx = (int)x;
        int dy = (int)y;

        // Shadow
        g2.setColor(new Color(0, 0, 0, 45));
        g2.fillOval(dx + 4, dy + H + 2, W - 8, 6);

        // Health bar
        if (health < maxHealth) drawHealthBar(g2, dx, dy);

        // ── Boots ─────────────────────────────────────────────────────────
        Color bootCol = new Color(18, 14, 22);
        g2.setColor(bootCol);
        g2.fillRoundRect(dx + 2, dy + H - 10, 13, 12, 4, 4);
        g2.fillRoundRect(dx + W - 15, dy + H - 10, 13, 12, 4, 4);
        g2.setColor(new Color(35, 28, 40));
        g2.fillRect(dx + 3, dy + H - 10, 11, 4);
        g2.fillRect(dx + W - 14, dy + H - 10, 11, 4);

        // ── Cargo pants ───────────────────────────────────────────────────
        Color pantsCol = new Color(38, 45, 38); // olive/dark green
        g2.setColor(pantsCol);
        g2.fillRoundRect(dx + 2, dy + 28, 14, 24, 3, 3);
        g2.fillRoundRect(dx + W - 16, dy + 28, 14, 24, 3, 3);
        // Cargo pocket seam
        g2.setColor(new Color(28, 34, 28));
        g2.drawLine(dx + 3, dy + 36, dx + 14, dy + 36);
        g2.drawLine(dx + W - 15, dy + 36, dx + W - 4, dy + 36);
        g2.drawRect(dx + 4, dy + 37, 8, 8);
        g2.drawRect(dx + W - 12, dy + 37, 8, 8);

        // ── Tactical vest / Chest rig ──────────────────────────────────────
        Color vestCol = new Color(45, 52, 45);
        Color vestDark = new Color(30, 35, 30);
        g2.setPaint(new GradientPaint(dx + 3, dy + 10, vestCol, dx + W - 3, dy + 30, vestDark));
        g2.fillRoundRect(dx + 3, dy + 10, W - 6, 22, 5, 5);
        // Chest rig straps
        g2.setColor(new Color(22, 26, 22));
        g2.drawLine(dx + 8, dy + 10, dx + W / 2, dy + 26);
        g2.drawLine(dx + W - 8, dy + 10, dx + W / 2, dy + 26);
        // Pouches (MOLLE)
        g2.setColor(new Color(35, 42, 35));
        g2.fillRoundRect(dx + 6, dy + 18, 8, 10, 2, 2);
        g2.fillRoundRect(dx + W - 14, dy + 18, 8, 10, 2, 2);
        g2.setColor(new Color(25, 30, 25));
        g2.drawRoundRect(dx + 6, dy + 18, 8, 10, 2, 2);
        g2.drawRoundRect(dx + W - 14, dy + 18, 8, 10, 2, 2);
        // Vest border
        g2.setColor(new Color(55, 65, 55, 140));
        g2.drawRoundRect(dx + 3, dy + 10, W - 6, 22, 5, 5);

        // ── Shoulders ─────────────────────────────────────────────────────
        g2.setColor(new Color(38, 44, 38));
        g2.fillRoundRect(dx - 3, dy + 9, 12, 14, 4, 4);
        g2.fillRoundRect(dx + W - 9, dy + 9, 12, 14, 4, 4);
        g2.setColor(new Color(60, 68, 60, 130));
        g2.drawRoundRect(dx - 3, dy + 9, 12, 14, 4, 4);
        g2.drawRoundRect(dx + W - 9, dy + 9, 12, 14, 4, 4);

        // ── Arms ──────────────────────────────────────────────────────────
        g2.setColor(new Color(35, 40, 35));
        g2.fillRoundRect(facingRight ? dx - 2 : dx + W - 6, dy + 18, 8, 16, 3, 3);
        g2.fillRoundRect(facingRight ? dx + W - 6 : dx - 2, dy + 18, 8, 16, 3, 3);

        // ── Gun arm (extended toward direction) ────────────────────────────
        int gunArmX = facingRight ? dx + W - 4 : dx - 4;
        g2.setColor(new Color(30, 34, 30));
        g2.fillRoundRect(gunArmX, dy + 20, 8, 14, 3, 3);

        // Gun body
        int gunBaseX = facingRight ? dx + W + 2 : dx - 24;
        int gunBaseY = dy + 26;
        g2.setColor(new Color(22, 18, 20));
        g2.fillRoundRect(gunBaseX, gunBaseY, 22, 9, 3, 3);
        // Barrel
        g2.setColor(new Color(30, 25, 28));
        int barrelX = facingRight ? gunBaseX + 18 : gunBaseX - 8;
        g2.fillRect(barrelX, gunBaseY + 2, 10, 5);
        // Magazine
        g2.setColor(new Color(18, 14, 16));
        g2.fillRoundRect(gunBaseX + (facingRight ? 6 : 6), gunBaseY + 7, 8, 11, 2, 2);
        // Gun sight
        g2.setColor(new Color(50, 45, 48));
        g2.fillRect(gunBaseX + (facingRight ? 8 : 8), gunBaseY - 3, 8, 3);

        // Muzzle flash
        if (muzzleFlash > 0) {
            int mfX = facingRight ? gunBaseX + 26 : gunBaseX - 12;
            int fa = muzzleFlash * 36;
            g2.setColor(new Color(255, 220, 100, Math.min(255, fa)));
            g2.fillOval(mfX - 4, gunBaseY - 2, 14, 13);
            g2.setColor(Color.WHITE);
            g2.fillOval(mfX, gunBaseY + 1, 6, 6);
        }

        // ── Helmet ────────────────────────────────────────────────────────
        Color helmCol = new Color(28, 32, 28);
        Color helmLight = new Color(50, 56, 50);
        g2.setColor(helmCol);
        g2.fillRoundRect(dx + 4, dy - 10, W - 8, 23, 8, 8);
        // Helmet shine
        g2.setColor(helmLight);
        g2.drawArc(dx + 5, dy - 9, W - 10, 22, 30, 120);
        // Brim
        g2.setColor(new Color(22, 26, 22));
        g2.fillRect(dx + 2, dy + 8, W - 4, 5);
        // Antenna
        g2.setColor(new Color(50, 55, 50));
        g2.drawLine(facingRight ? dx + W - 6 : dx + 6, dy - 10, facingRight ? dx + W - 2 : dx + 2, dy - 20);
        g2.fillOval(facingRight ? dx + W - 4 : dx, dy - 23, 5, 5);

        // ── Visor (flip-down) ──────────────────────────────────────────────
        Color visorCol = new Color(40, 80, 60, 220);
        g2.setColor(visorCol);
        g2.fillRoundRect(dx + 5, dy - 4, W - 10, 12, 4, 4);
        // Visor scan line glow
        int va = (int)(visorGlow * 140);
        g2.setColor(new Color(60, 220, 120, va));
        g2.fillRect(dx + 6, dy - 4 + (int)(animTick % 12), W - 12, 2);
        // Visor border
        g2.setColor(new Color(30, 100, 60, 200));
        g2.drawRoundRect(dx + 5, dy - 4, W - 10, 12, 4, 4);
        // Visor edge highlights
        g2.setColor(new Color(100, 220, 160, 160));
        g2.drawLine(dx + 6, dy - 4, dx + W - 6, dy - 4);
    }

    private void drawHealthBar(Graphics2D g2, int dx, int dy) {
        int bw = W + 4, bh = 5;
        int bx = dx - 2, by = dy - 14;
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRoundRect(bx, by, bw, bh, 3, 3);
        float ratio = (float) health / maxHealth;
        int fillW = (int)(bw * ratio);
        if (fillW > 0) {
            Color fc = ratio > 0.5f ? new Color(220, 50, 50) : new Color(255, 120, 20);
            g2.setColor(fc);
            g2.fillRoundRect(bx, by, fillW, bh, 3, 3);
            g2.setColor(new Color(255, 200, 200, 80));
            g2.fillRoundRect(bx, by, fillW, 2, 3, 3);
        }
        g2.setColor(new Color(150, 50, 50, 200));
        g2.drawRoundRect(bx, by, bw, bh, 3, 3);
    }

    private void drawDeath(Graphics2D g2) {
        if (deathTimer <= 0) return;
        float prog = (float) deathTimer / DEATH_FRAMES;
        float scale = 1f + (1f - prog) * 0.6f;
        java.awt.geom.AffineTransform saved = g2.getTransform();
        float cx = x + W / 2f, cy = y + H / 2f;
        g2.translate(cx, cy);
        g2.scale(scale, scale);
        g2.translate(-cx, -cy);
        int alpha = (int)(prog * 230);
        int r = Math.min(255, (int)(255 * prog + 180 * (1 - prog)));
        int gv = (int)(180 * prog);
        g2.setColor(new Color(r, gv, 50, alpha));
        g2.fillRoundRect((int)x, (int)y, W, H, 6, 6);
        g2.setTransform(saved);
    }
}