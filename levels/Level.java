package levels;

import enemy.Enemy;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;

/**
 * Level — data container and level factory.
 *
 * Phase 2 additions:
 * - worldW / worldH — actual level size; Camera scrolls within these bounds
 * - 3 full levels designed for ~2400px wide scrollable worlds
 */
public class Level {

    public final String name;
    public final Rectangle[] platforms;
    public final List<Enemy> enemies;
    public final int portalX, portalY;
    public final int worldW, worldH;

    public Level(String name, Rectangle[] platforms, List<Enemy> enemies,
            int portalX, int portalY, int worldW, int worldH) {
        this.name = name;
        this.platforms = platforms;
        this.enemies = enemies;
        this.portalX = portalX;
        this.portalY = portalY;
        this.worldW = worldW;
        this.worldH = worldH;
    }

    public static Level getLevel(int num) {
        return switch (num) {
            case 2 -> level2();
            case 3 -> level3();
            default -> level1();
        };
    }

    // ── Level 1: "The Awakening" ──────────────────────────────────────────
    // Gentle introduction. Teaches movement, jump, wall-jump, dash, portal.
    // World: 2400 × 600
    private static Level level1() {
        Rectangle[] p = {
                // Starting zone
                new Rectangle(0, 470, 220, 18),
                new Rectangle(260, 470, 280, 18),
                new Rectangle(600, 470, 200, 18),
                // First climb
                new Rectangle(120, 360, 110, 14),
                new Rectangle(280, 295, 100, 14),
                new Rectangle(440, 230, 110, 14),
                // Bridge gap
                new Rectangle(620, 350, 80, 14),
                new Rectangle(760, 280, 90, 14),
                // Section 2
                new Rectangle(900, 470, 300, 18),
                new Rectangle(930, 370, 90, 14),
                new Rectangle(1080, 300, 100, 14),
                new Rectangle(1230, 220, 110, 14),
                new Rectangle(1380, 160, 120, 14),
                // Tricky jumps
                new Rectangle(1550, 380, 70, 14),
                new Rectangle(1660, 300, 70, 14),
                new Rectangle(1770, 220, 80, 14),
                // Section 3 (finale)
                new Rectangle(1900, 470, 280, 18),
                new Rectangle(1940, 360, 90, 14),
                new Rectangle(2060, 280, 100, 14),
                new Rectangle(2180, 180, 110, 14),
                new Rectangle(2300, 100, 80, 18), // portal platform
        };
        List<Enemy> e = Arrays.asList(
                new Enemy(310, 422, 260, 530),
                new Enemy(950, 422, 900, 1180),
                new Enemy(1250, 168, 1230, 1460),
                new Enemy(2060, 228, 1950, 2250));
        return new Level("The Awakening", p, e, 2330, 40, 2400, 600);
    }

    // ── Level 2: "The Ruined Spire" ───────────────────────────────────────
    // More vertical. Larger gaps. Timing matters.
    // World: 2400 × 600
    private static Level level2() {
        Rectangle[] p = {
                // Ground sections
                new Rectangle(0, 470, 160, 18),
                new Rectangle(220, 470, 180, 18),
                // Vertical left column
                new Rectangle(50, 370, 80, 14),
                new Rectangle(160, 290, 80, 14),
                new Rectangle(60, 210, 80, 14),
                new Rectangle(160, 130, 90, 14),
                // Bridge to right
                new Rectangle(300, 200, 100, 14),
                new Rectangle(450, 260, 90, 14),
                new Rectangle(580, 320, 90, 14),
                // Spire section
                new Rectangle(700, 470, 150, 18),
                new Rectangle(720, 380, 70, 14),
                new Rectangle(820, 300, 80, 14),
                new Rectangle(950, 230, 80, 14),
                new Rectangle(1070, 155, 90, 14),
                // Dropped section
                new Rectangle(1220, 400, 200, 18),
                new Rectangle(1260, 310, 80, 14),
                new Rectangle(1380, 240, 80, 14),
                // Mid platforms
                new Rectangle(1500, 470, 200, 18),
                new Rectangle(1540, 360, 90, 14),
                new Rectangle(1680, 290, 90, 14),
                new Rectangle(1800, 210, 100, 14),
                // Final approach
                new Rectangle(1950, 470, 220, 18),
                new Rectangle(1980, 370, 80, 14),
                new Rectangle(2100, 280, 90, 14),
                new Rectangle(2220, 190, 100, 14),
                new Rectangle(2310, 90, 80, 18), // portal platform
        };
        List<Enemy> e = Arrays.asList(
                new Enemy(230, 422, 220, 380),
                new Enemy(820, 248, 820, 1020),
                new Enemy(1260, 258, 1260, 1450),
                new Enemy(1680, 238, 1680, 1870),
                new Enemy(2100, 228, 2100, 2290));
        return new Level("The Ruined Spire", p, e, 2330, 30, 2400, 600);
    }

    // ── Level 3: "The Eclipse" ─────────────────────────────────────────────
    // Most challenging. Narrow platforms, many enemies, dash & wall-jump required.
    // World: 2500 × 600
    private static Level level3() {
        Rectangle[] p = {
                // Opening — unstable ground
                new Rectangle(0, 470, 120, 18),
                new Rectangle(160, 470, 120, 18),
                new Rectangle(330, 470, 120, 18),
                // Vertical towers
                new Rectangle(80, 360, 60, 14),
                new Rectangle(200, 280, 60, 14),
                new Rectangle(100, 200, 60, 14),
                new Rectangle(220, 130, 70, 14),
                // Center chaos
                new Rectangle(380, 380, 60, 14),
                new Rectangle(480, 300, 60, 14),
                new Rectangle(580, 220, 60, 14),
                new Rectangle(680, 140, 70, 14),
                // Sector 2
                new Rectangle(800, 470, 120, 18),
                new Rectangle(980, 470, 120, 18),
                new Rectangle(840, 380, 60, 14),
                new Rectangle(960, 300, 60, 14),
                new Rectangle(840, 220, 60, 14),
                new Rectangle(960, 140, 70, 14),
                // Crossing
                new Rectangle(1100, 260, 70, 14),
                new Rectangle(1220, 180, 70, 14),
                // Sector 3
                new Rectangle(1350, 470, 100, 18),
                new Rectangle(1500, 470, 100, 18),
                new Rectangle(1380, 370, 60, 14),
                new Rectangle(1500, 290, 60, 14),
                new Rectangle(1620, 210, 70, 14),
                new Rectangle(1740, 130, 70, 14),
                // Final plateau
                new Rectangle(1880, 400, 200, 18),
                new Rectangle(1920, 310, 80, 14),
                new Rectangle(2060, 230, 90, 14),
                new Rectangle(2190, 150, 100, 14),
                new Rectangle(2330, 350, 100, 18),
                new Rectangle(2370, 250, 80, 14),
                new Rectangle(2420, 140, 80, 18), // portal platform
        };
        List<Enemy> e = Arrays.asList(
                new Enemy(170, 422, 160, 310),
                new Enemy(490, 248, 380, 660),
                new Enemy(680, 88, 610, 750),
                new Enemy(960, 248, 840, 1080),
                new Enemy(1220, 128, 1150, 1290),
                new Enemy(1500, 238, 1500, 1700),
                new Enemy(1740, 78, 1670, 1810),
                new Enemy(2060, 178, 1880, 2180));
        return new Level("The Eclipse", p, e, 2450, 100, 2500, 600);
    }
}