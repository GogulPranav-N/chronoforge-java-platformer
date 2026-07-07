package fx;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * ParticleSystem — global particle pool for ChronoForge.
 *
 * All game objects (player, enemies, boss) call ParticleSystem.spawn()
 * and never manage their own particle lists.
 *
 * Each particle: [worldX, worldY, velX*100, velY*100, life, maxLife, r, g, b,
 * size*10]
 *
 * Types encode colour, spread, gravity, and count automatically.
 */
public class ParticleSystem {

    public enum Type {
        JUMP, // Blue dust burst from feet
        LAND, // Heavier dust ring on landing
        DASH, // Cyan/white streaks opposing dash direction
        SLASH, // White/gold sparks in slash arc
        SLASH_HEAVY, // Orange sparks, bigger spread
        BLOOD, // Red splatter on enemy hit
        SPARK, // Small generic spark
        MOON, // Purple/blue glow (night power)
        DEATH // Large grey smoke explosion
    }

    // Each row: [x, y, velX*100, velY*100, life, maxLife, r, g, b, size*10]
    private final ArrayList<int[]> pool = new ArrayList<>(256);

    // ── Spawn ────────────────────────────────────────────────────────────
    public void spawn(float wx, float wy, Type type, boolean facingRight) {
        switch (type) {
            case JUMP -> spawnCount(wx, wy, 10, 120, 180, 255, 3, 4, -6, -1, 15, 6);
            case LAND -> spawnRing(wx, wy, 16, 200, 200, 220, 25, 25);
            case DASH -> spawnDash(wx, wy, facingRight);
            case SLASH -> spawnSlash(wx, wy, facingRight, 12, 230, 230, 255, 6);
            case SLASH_HEAVY -> spawnSlash(wx, wy, facingRight, 18, 255, 140, 30, 9);
            case BLOOD -> spawnCount(wx, wy, 14, 200, 20, 20, 4, 6, -8, -2, 22, 7);
            case SPARK -> spawnCount(wx, wy, 6, 255, 200, 100, 3, 3, -4, -2, 12, 4);
            case MOON -> spawnCount(wx, wy, 8, 120, 100, 255, 2, 2, -3, -3, 30, 5);
            case DEATH -> spawnDeath(wx, wy);
        }
    }

    /** Convenience — no direction needed. */
    public void spawn(float wx, float wy, Type type) {
        spawn(wx, wy, type, true);
    }

    // ── Update ───────────────────────────────────────────────────────────
    public void update() {
        Iterator<int[]> it = pool.iterator();
        while (it.hasNext()) {
            int[] p = it.next();
            p[4]--; // life decrement
            p[0] += p[2]; // x += velX*100 → *0.01 when drawing
            p[1] += p[3]; // y += velY*100
            p[3] += 15; // gravity (velY*100 grows)
            if (p[4] <= 0)
                it.remove();
        }
    }

    // ── Draw (world space) ────────────────────────────────────────────────
    public void draw(Graphics2D g2) {
        for (int[] p : pool) {
            float life = (float) p[4] / p[5]; // 1.0 → 0.0
            int alpha = (int) (life * 230);
            int r = p[6], gr = p[7], b = p[8];
            float size = p[9] / 10f * life; // shrink over time
            if (size < 0.5f || alpha < 5)
                continue;

            g2.setColor(new Color(r, gr, b, Math.min(255, alpha)));
            float px = p[0] / 100f;
            float py = p[1] / 100f;
            g2.fillOval((int) (px - size / 2), (int) (py - size / 2), (int) size, (int) size);
        }
    }

    public void clear() {
        pool.clear();
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    /**
     * Spawn `count` particles with random velocity in [velXMin,velXMax],
     * [velYMin,velYMax].
     * Velocities are stored * 100 as integers.
     */
    private void spawnCount(float wx, float wy, int count,
            int r, int g, int b,
            int velXMin, int velXMax,
            int velYMin, int velYMax,
            int life, int size) {
        for (int i = 0; i < count; i++) {
            int vx = (int) ((Math.random() * (velXMax - velXMin) + velXMin) * 100);
            int vy = (int) ((Math.random() * (velYMax - velYMin) + velYMin) * 100);
            pool.add(new int[] { (int) (wx * 100), (int) (wy * 100), vx, vy, life, life, r, g, b, size * 10 });
        }
    }

    private void spawnRing(float wx, float wy, int count, int r, int g, int b, int speed, int size) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count;
            int vx = (int) (Math.cos(angle) * speed * 100);
            int vy = (int) (Math.sin(angle) * speed * 100 * 0.3); // squash vertically
            pool.add(new int[] { (int) (wx * 100), (int) (wy * 100), vx, vy, 18, 18, r, g, b, size * 10 });
        }
    }

    private void spawnDash(float wx, float wy, boolean facingRight) {
        int dir = facingRight ? -1 : 1;
        for (int i = 0; i < 14; i++) {
            int vx = (int) ((Math.random() * 8 + 4) * dir * 100);
            int vy = (int) ((Math.random() * 6 - 3) * 100);
            int r = 150 + (int) (Math.random() * 100);
            pool.add(new int[] { (int) (wx * 100), (int) (wy * 100), vx, vy, 20, 20, r, 220, 255, 60 });
        }
    }

    private void spawnSlash(float wx, float wy, boolean facingRight, int count,
            int r, int g, int b, int size) {
        int dir = facingRight ? 1 : -1;
        for (int i = 0; i < count; i++) {
            double angle = (Math.random() * 0.8 - 0.4); // narrow arc forward
            int vx = (int) (Math.cos(angle) * dir * (6 + Math.random() * 6) * 100);
            int vy = (int) (Math.sin(angle) * (4 + Math.random() * 4) * 100 - 200);
            pool.add(new int[] { (int) (wx * 100), (int) (wy * 100), vx, vy, 14, 14, r, g, b, size * 10 });
        }
    }

    private void spawnDeath(float wx, float wy) {
        for (int i = 0; i < 28; i++) {
            double angle = Math.random() * Math.PI * 2;
            double speed = Math.random() * 12 + 3;
            int vx = (int) (Math.cos(angle) * speed * 100);
            int vy = (int) (Math.sin(angle) * speed * 100 - 800);
            int shade = 80 + (int) (Math.random() * 100);
            pool.add(new int[] { (int) (wx * 100), (int) (wy * 100), vx, vy, 35, 35, shade, shade, shade, 110 });
        }
    }
}
