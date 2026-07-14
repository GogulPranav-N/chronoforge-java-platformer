package enemy;

import java.awt.*;
import java.awt.geom.AffineTransform;

/**
 * Bullet — projectile fired by gunner enemies, boss, or the player (kunai).
 * Stored in a shared pool in GamePanel.
 */
public class Bullet {

    public float x, y;
    public float velX, velY;
    public boolean active = true;
    public final boolean fromBoss;
    public final boolean isPlayerKunai; // true = fired by player

    private static final int LIFETIME       = 220;
    private static final int KUNAI_LIFETIME = 180;
    private int life;

    public Bullet(float x, float y, float velX, float velY, boolean fromBoss) {
        this(x, y, velX, velY, fromBoss, false);
    }

    public Bullet(float x, float y, float velX, float velY, boolean fromBoss, boolean isPlayerKunai) {
        this.x            = x;
        this.y            = y;
        this.velX         = velX;
        this.velY         = velY;
        this.fromBoss     = fromBoss;
        this.isPlayerKunai = isPlayerKunai;
        this.life         = isPlayerKunai ? KUNAI_LIFETIME : LIFETIME;
    }

    public void update() {
        x += velX;
        y += velY;
        // No gravity — bullets travel perfectly straight
        if (--life <= 0) active = false;
    }

    public Rectangle getRect() {
        return new Rectangle((int) x - 6, (int) y - 4, 14, 8);
    }

    public void draw(Graphics2D g2) {
        if (!active) return;
        float prog  = (float) life / (isPlayerKunai ? KUNAI_LIFETIME : LIFETIME);
        int   alpha = (int) (Math.min(1f, prog * 4) * 230);

        if (isPlayerKunai) drawKunai(g2, alpha);
        else               drawEnemyBullet(g2, alpha);
    }

    // ── Kunai (player projectile) ─────────────────────────────────────────────
    private void drawKunai(Graphics2D g2, int alpha) {
        double angle = Math.atan2(velY, velX);
        AffineTransform old = g2.getTransform();
        g2.translate(x, y);
        g2.rotate(angle);

        // Glow trail
        g2.setColor(new Color(180, 240, 255, alpha / 5));
        g2.fillOval(-14, -5, 20, 10);

        // Blade body (steel-blue triangle)
        g2.setColor(new Color(210, 235, 255, alpha));
        g2.fillPolygon(new int[]{-10, 8, -10}, new int[]{-2, 0, 2}, 3);

        // Blade shine
        g2.setColor(new Color(255, 255, 255, (int) (alpha * 0.7)));
        g2.drawLine(-8, -1, 5, 0);

        // Handle (dark wrap)
        g2.setColor(new Color(70, 45, 20, alpha));
        g2.fillRect(-14, -2, 5, 4);
        g2.setColor(new Color(150, 110, 55, alpha));
        g2.fillRect(-14, -1, 5, 1);

        // Cyan energy tip
        g2.setColor(new Color(100, 255, 230, alpha / 2));
        g2.fillOval(5, -3, 7, 6);
        g2.setColor(new Color(220, 255, 255, Math.min(255, alpha)));
        g2.fillOval(7, -1, 3, 3);

        g2.setTransform(old);
    }

    // ── Enemy / boss bullet ───────────────────────────────────────────────────
    private void drawEnemyBullet(Graphics2D g2, int alpha) {
        Color trailCore = fromBoss ? new Color(220, 60, 255) : new Color(255, 200, 40);
        for (int i = 3; i >= 1; i--) {
            float tx = x - velX * i * 0.5f;
            float ty = y - velY * i * 0.5f;
            g2.setColor(new Color(trailCore.getRed(), trailCore.getGreen(),
                    trailCore.getBlue(), Math.min(255, alpha / (i + 2))));
            g2.fillOval((int) tx - 3, (int) ty - 3, 7, 7);
        }
        g2.setColor(new Color(trailCore.getRed(), trailCore.getGreen(),
                trailCore.getBlue(), alpha / 4));
        g2.fillOval((int) x - 7, (int) y - 6, 15, 12);
        g2.setColor(fromBoss ? new Color(240, 100, 255, alpha) : new Color(255, 230, 60, alpha));
        g2.fillOval((int) x - 5, (int) y - 3, 10, 7);
        g2.setColor(new Color(255, 255, 255, Math.min(255, (int) (alpha * 0.85))));
        g2.fillOval((int) x - 2, (int) y - 1, 5, 3);
    }
}
