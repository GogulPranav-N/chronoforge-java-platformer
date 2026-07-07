package engine;

import enemy.Enemy;
import fx.ParticleSystem;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.AffineTransform;
import javax.swing.JPanel;
import levels.Level;
import player.Player;
import ui.HUD;

/**
 * GamePanel — main game loop and render orchestrator.
 *
 * Phase 2 additions:
 * - HitStop integration: physics update skips while frozen
 * - Parallax background: 2 layers scrolling at 0.15x and 0.40x camera speed
 * - worldW passed to Camera so it scrolls correctly through large levels
 * - Level progression: WIN advances to next level (up to 3)
 */
public class GamePanel extends JPanel implements Runnable, KeyListener {

    public static final int WIDTH = 800;
    public static final int HEIGHT = 600;
    static final int WALL = 15;

    Thread gameThread;
    GameState state = GameState.MENU;
    int currentLevelNum = 1;

    // ── Systems ───────────────────────────────────────────────────────────
    InputManager input;
    Camera camera;
    ParticleSystem particles;
    HitStop hitStop;
    HUD hud;

    // ── Objects ───────────────────────────────────────────────────────────
    Player player;
    Level currentLevel;

    // ── Day / Night ───────────────────────────────────────────────────────
    float timeOfDay = 0f;
    float timeSpeed = 0.00012f;
    String phase = "NIGHT";

    public GamePanel() {
        this.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        this.setFocusable(true);

        input = new InputManager();
        camera = new Camera(WIDTH, HEIGHT);
        particles = new ParticleSystem();
        hitStop = new HitStop();
        hud = new HUD();

        this.addKeyListener(input);
        this.addKeyListener(this);

        loadLevel(1);
    }

    void loadLevel(int num) {
        currentLevelNum = num;
        currentLevel = Level.getLevel(num);
        particles.clear();
        player = new Player(100, 380, input, particles);
        camera.snapTo(100, 380);
    }

    public void startGameThread() {
        gameThread = new Thread(this);
        gameThread.start();
    }

    // ── Game loop (fixed 60 fps) ──────────────────────────────────────────
    @Override
    public void run() {
        final long TARGET_NS = 1_000_000_000L / 60;
        long lastTime = System.nanoTime();
        while (gameThread != null) {
            long now = System.nanoTime();
            if (now - lastTime >= TARGET_NS) {
                lastTime = now;
                update();
                repaint();
            } else {
                try {
                    Thread.sleep(1);
                } catch (Exception ignored) {
                }
            }
        }
    }

    // ── Update ────────────────────────────────────────────────────────────
    void update() {
        input.tick();
        hitStop.tick(); // always ticked — counts down the freeze

        if (state == GameState.MENU || state == GameState.GAMEOVER
                || state == GameState.WIN || state == GameState.PAUSED)
            return;

        // While frozen: skip all physics / logic, only camera & particles continue
        if (hitStop.isActive()) {
            particles.update();
            return;
        }

        // ── Day / Night ────────────────────────────────────────────────────
        timeOfDay += timeSpeed;
        if (timeOfDay > 1f)
            timeOfDay = 0f;
        if (timeOfDay < 0.25f || timeOfDay > 0.75f)
            phase = "NIGHT";
        else if (timeOfDay < 0.35f || timeOfDay > 0.65f)
            phase = "DUSK";
        else
            phase = "DAY";

        // ── Player ────────────────────────────────────────────────────────
        player.update(phase, currentLevel.platforms, WALL,
                currentLevel.worldW, currentLevel.worldH);

        // ── Camera ────────────────────────────────────────────────────────
        camera.update((int) player.x, (int) player.y, player.facingRight,
                currentLevel.worldW, currentLevel.worldH);

        // ── Enemies ───────────────────────────────────────────────────────
        for (Enemy enemy : currentLevel.enemies) {
            enemy.update();

            // Enemy hits player
            if (player.getRect().intersects(enemy.getRect())
                    && player.invincibleTimer == 0) {
                player.takeDamage();
                player.knockBack(player.x > enemy.x);
                camera.addTrauma(0.4f);
                hitStop.trigger(3);
                particles.spawn(player.x + Player.W / 2f, player.y + Player.H / 2f,
                        ParticleSystem.Type.BLOOD);
            }

            // Player attacks enemy (Phase 3 CombatSystem will own this fully)
            if (player.isAttacking && player.attackTimer == player.attackTimer
                    && player.getRect().intersects(enemy.getRect())) {
                camera.addTrauma(0.25f);
                hitStop.trigger(2);
                particles.spawn(enemy.x + enemy.width / 2f, enemy.y + enemy.height / 2f,
                        ParticleSystem.Type.SPARK, player.facingRight);
            }
        }

        // ── Particles ────────────────────────────────────────────────────
        particles.update();

        // ── Death check ───────────────────────────────────────────────────
        if (player.isDead()) {
            state = GameState.GAMEOVER;
            return;
        }

        // ── Portal (night only) ───────────────────────────────────────────
        Rectangle portal = new Rectangle(
                currentLevel.portalX - 25, currentLevel.portalY, 50, 60);
        if (player.getRect().intersects(portal) && phase.equals("NIGHT")) {
            state = GameState.WIN;
        }
    }

    // ── Render ────────────────────────────────────────────────────────────
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // ── SKY ───────────────────────────────────────────────────────────
        Color skyTop, skyBot;
        if (phase.equals("NIGHT")) {
            skyTop = new Color(4, 4, 20);
            skyBot = new Color(10, 10, 50);
        } else if (phase.equals("DUSK")) {
            skyTop = new Color(20, 5, 30);
            skyBot = new Color(200, 80, 30);
        } else {
            skyTop = new Color(70, 140, 255);
            skyBot = new Color(170, 210, 255);
        }
        g2.setPaint(new GradientPaint(0, 0, skyTop, 0, HEIGHT, skyBot));
        g2.fillRect(0, 0, WIDTH, HEIGHT);

        if (state == GameState.MENU) {
            hud.draw(g2, state, phase, false, 0, timeOfDay, 5, 5, WIDTH, HEIGHT);
            return;
        }

        // ── Stars (screen space — don't scroll) ───────────────────────────
        if (phase.equals("NIGHT")) {
            g2.setColor(Color.WHITE);
            int[][] stars = { { 60, 25 }, { 160, 55 }, { 290, 18 }, { 490, 75 }, { 660, 38 },
                    { 730, 85 }, { 210, 105 }, { 410, 48 }, { 590, 108 }, { 350, 160 } };
            for (int[] s : stars)
                g2.fillOval(s[0], s[1], s[0] % 3 == 0 ? 3 : 2, s[0] % 3 == 0 ? 3 : 2);
        }

        // ── Moon (screen space) ───────────────────────────────────────────
        if (phase.equals("NIGHT")) {
            float pulse = 0.85f + 0.15f * (float) Math.sin(System.currentTimeMillis() * 0.0008);
            g2.setColor(new Color(255, 255, 220, (int) (200 * pulse)));
            g2.fillOval(650, 30, 60, 60);
            g2.setColor(new Color(240, 240, 200, 120)); // glow ring
            g2.fillOval(642, 22, 76, 76);
            g2.setColor(new Color(10, 6, 30)); // crater bite
            g2.fillOval(670, 40, 30, 30);
        }

        // ── PARALLAX BACKGROUND ───────────────────────────────────────────
        drawParallax(g2);

        // ── WORLD SPACE ───────────────────────────────────────────────────
        AffineTransform saved = camera.apply(g2);

        // Walls
        g2.setColor(new Color(25, 15, 40));
        g2.fillRect(0, 0, WALL, HEIGHT);
        g2.fillRect(currentLevel.worldW - WALL, 0, WALL, HEIGHT);

        // Platforms
        for (Rectangle p : currentLevel.platforms) {
            // Body
            g2.setColor(new Color(45, 30, 65));
            g2.fillRect(p.x, p.y, p.width, p.height);
            // Ledge glow
            GradientPaint ledge = new GradientPaint(
                    p.x, p.y, new Color(160, 90, 240, 220),
                    p.x, p.y + 5, new Color(100, 60, 180, 0));
            g2.setPaint(ledge);
            g2.fillRect(p.x, p.y, p.width, 5);
            // Top line
            g2.setColor(new Color(120, 80, 170));
            g2.drawLine(p.x, p.y, p.x + p.width, p.y);
            // Rune marks
            g2.setColor(new Color(170, 140, 240, 55));
            for (int rx = p.x + 15; rx < p.x + p.width - 10; rx += 30)
                g2.fillRect(rx, p.y + 7, 8, 3);
        }

        // Portal
        drawPortal(g2);

        // Wall-jump hint
        if ((player.touchingWallLeft || player.touchingWallRight) && !player.onGround) {
            g2.setColor(new Color(200, 200, 255, 180));
            g2.setFont(new Font("Arial Narrow", Font.BOLD, 11));
            g2.drawString("WALL ▶ SPACE",
                    player.touchingWallRight ? (int) player.x - 80 : (int) player.x + 40,
                    (int) player.y + 20);
        }

        // Enemies
        for (Enemy enemy : currentLevel.enemies)
            enemy.draw(g2);

        // Particles (world space)
        particles.draw(g2);

        // Player
        player.draw(g2, phase);

        // ── SCREEN SPACE ──────────────────────────────────────────────────
        camera.restore(g2, saved);

        // Letterbox (boss cutscenes later)
        camera.drawLetterbox(g2);

        // HUD — level name sub-label
        drawLevelName(g2);

        // HUD overlay
        hud.draw(g2, state, phase,
                player.canDash, player.jumpCount,
                timeOfDay, player.health, player.maxHealth,
                WIDTH, HEIGHT);
    }

    // ── Parallax ──────────────────────────────────────────────────────────
    private void drawParallax(Graphics2D g2) {
        float cx = camera.camX;
        boolean night = phase.equals("NIGHT");

        // Layer 1 — far mountains (0.15× scroll)
        int off1 = (int) (cx * 0.15f);
        Color mtnCol = night ? new Color(15, 12, 35)
                : phase.equals("DUSK") ? new Color(50, 20, 40) : new Color(60, 100, 160);
        g2.setColor(mtnCol);
        for (int i = 0; i < 8; i++) {
            int bx = (i * 320 - off1 % 320) - 160;
            int bh = 120 + (i * 47) % 80;
            g2.fillPolygon(
                    new int[] { bx, bx + 160, bx + 320 },
                    new int[] { HEIGHT - 30, HEIGHT - 30 - bh, HEIGHT - 30 }, 3);
        }

        // Layer 2 — ruined structures (0.40× scroll)
        int off2 = (int) (cx * 0.40f);
        Color ruinCol = night ? new Color(20, 14, 42)
                : phase.equals("DUSK") ? new Color(60, 25, 50) : new Color(80, 120, 180);
        g2.setColor(ruinCol);
        for (int i = 0; i < 6; i++) {
            int bx = (i * 420 - off2 % 420) - 210;
            // Column
            g2.fillRect(bx + 60, HEIGHT - 160, 22, 140);
            // Capital
            g2.fillRect(bx + 54, HEIGHT - 165, 34, 12);
            // Broken top
            g2.fillRect(bx + 160, HEIGHT - 200, 18, 180);
            g2.fillRect(bx + 154, HEIGHT - 208, 30, 14);
        }
    }

    // ── Portal ────────────────────────────────────────────────────────────
    private void drawPortal(Graphics2D g2) {
        int px = currentLevel.portalX, py = currentLevel.portalY;
        if (phase.equals("NIGHT")) {
            float pulse = (float) (0.6 + 0.4 * Math.sin(System.currentTimeMillis() * 0.003));
            int gs = (int) (70 * pulse);
            g2.setColor(new Color(140, 140, 255, 35));
            g2.fillOval(px - gs / 2, py - gs / 4, gs + 50, gs + 50);
            g2.setColor(new Color(200, 200, 255, 110));
            g2.fillOval(px - 30, py, 60, 60);
            g2.setColor(new Color(235, 235, 255));
            g2.fillOval(px - 22, py + 5, 44, 44);
            g2.setColor(new Color(4, 4, 20));
            g2.fillOval(px - 10, py + 2, 38, 38);
            g2.setColor(new Color(220, 220, 180, 200));
            g2.fillOval(px - 6, py + 6, 26, 26);
            g2.setColor(new Color(255, 255, 200, 180));
            g2.setFont(new Font("Arial Narrow", Font.BOLD, 12));
            g2.drawString("▲ ASCEND", px - 26, py - 8);
        } else {
            g2.setColor(new Color(40, 40, 55));
            g2.fillOval(px - 18, py + 10, 36, 36);
        }
    }

    // ── Level name label ──────────────────────────────────────────────────
    private void drawLevelName(Graphics2D g2) {
        g2.setFont(new Font("Arial Narrow", Font.BOLD, 11));
        g2.setColor(new Color(180, 160, 200, 160));
        g2.drawString("LEVEL " + currentLevelNum + " — " + currentLevel.name.toUpperCase(),
                WIDTH / 2 - 80, 22);
    }

    // ── State transitions ─────────────────────────────────────────────────
    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_ENTER) {
            if (state == GameState.MENU) {
                state = GameState.PLAYING;
            } else if (state == GameState.GAMEOVER) {
                loadLevel(currentLevelNum);
                state = GameState.PLAYING;
            } else if (state == GameState.WIN) {
                int next = currentLevelNum < 3 ? currentLevelNum + 1 : 1;
                loadLevel(next);
                state = GameState.PLAYING;
            }
        }
        if (code == KeyEvent.VK_ESCAPE) {
            if (state == GameState.PLAYING)
                state = GameState.PAUSED;
            else if (state == GameState.PAUSED)
                state = GameState.PLAYING;
        }
        if (code == KeyEvent.VK_SPACE && state == GameState.MENU) {
            state = GameState.PLAYING;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }
}