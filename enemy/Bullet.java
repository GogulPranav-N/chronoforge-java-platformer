package enemy;

import java.awt.*;

/**
 * Bullet — projectile fired by gunner enemies and the boss.
 * Stored in a shared pool in GamePanel.
 */
public class Bullet {

    public float x, y;
    public float velX, velY;
    public boolean active = true;
    public final boolean fromBoss;

    private static final int LIFETIME = 220;
    private int life = LIFETIME;

    public Bullet(float x, float y, float velX, float velY, boolean fromBoss) {
        this.x = x;
        this.y = y;
        this.velX = velX;
        this.velY = velY;
        this.fromBoss = fromBoss;
    }

    public void update() {
        x += velX;
        y += velY;
        velY += 0.08f; // gentle gravity on bullets
        if (--life <= 0) active = false;
    }

    public Rectangle getRect() {
        return new Rectangle((int) x - 5, (int) y - 4, 12, 8);
    }

    public void draw(Graphics2D g2) {
        if (!active) return;
        float prog = (float) life / LIFETIME;
        int alpha = (int) (Math.min(1f, prog * 4) * 230);

        // Trail streaks
        Color trailCore = fromBoss ? new Color(220, 60, 255) : new Color(255, 200, 40);
        for (int i = 3; i >= 1; i--) {
            float tx = x - velX * i * 0.5f;
            float ty = y - velY * i * 0.5f;
            int ta = alpha / (i + 2);
            g2.setColor(new Color(trailCore.getRed(), trailCore.getGreen(),
                    trailCore.getBlue(), Math.min(255, ta)));
            g2.fillOval((int) tx - 3, (int) ty - 3, 7, 7);
        }

        // Glow halo
        g2.setColor(new Color(trailCore.getRed(), trailCore.getGreen(),
                trailCore.getBlue(), alpha / 4));
        g2.fillOval((int) x - 7, (int) y - 6, 15, 12);

        // Bullet core
        g2.setColor(fromBoss ? new Color(240, 100, 255, alpha) : new Color(255, 230, 60, alpha));
        g2.fillOval((int) x - 5, (int) y - 3, 10, 7);

        // Bright center
        g2.setColor(new Color(255, 255, 255, Math.min(255, (int) (alpha * 0.85))));
        g2.fillOval((int) x - 2, (int) y - 1, 5, 3);
    }
}
