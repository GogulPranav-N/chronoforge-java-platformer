package engine;

import enemy.Boss;
import enemy.Bullet;
import enemy.Enemy;
import fx.ParticleSystem;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.JPanel;
import levels.Level;
import player.Player;
import ui.HUD;

/**
 * GamePanel — main game loop and render orchestrator.
 *
 * Phase 4 additions:
 * - Boss fight system (BOSS_CINEMATIC + BOSS_FIGHT states)
 * - Bullet pool shared across enemies and boss
 * - Heal-on-kill mechanic (2 charges per level)
 * - Cinematic camera pan + letterbox
 * - "SHADOW COMMANDER" title card
 */
public class GamePanel extends JPanel implements Runnable, KeyListener {

    public static final int WIDTH  = 800;
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
    Boss boss;
    List<Bullet> bullets = new ArrayList<>();

    // ── Day / Night ───────────────────────────────────────────────────────
    float timeOfDay  = 0f;
    float timeSpeed  = 0.000397f;
    String phase     = "NIGHT";

    // ── Kill tracking ─────────────────────────────────────────────────────
    int killCount = 0;

    // ── Heal system ───────────────────────────────────────────────────────
    int healChargesLeft = 2;

    // ── Boss cinematic ────────────────────────────────────────────────────
    boolean bossTriggered = false;
    boolean bossDefeated  = false;
    int cinematicTimer    = 0;
    static final int CINEMATIC_FRAMES = 210; // 3.5 seconds @ 60 fps

    // ── Ambient animations ────────────────────────────────────────────────
    private long frameCount = 0;
    private final float[] starTwinkle = new float[20];
    private final int[][] starPositions;
    private final float[] fireflyX = new float[12];
    private final float[] fireflyY = new float[12];
    private final float[] fireflyPhase = new float[12];

    public GamePanel() {
        this.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        this.setFocusable(true);

        input     = new InputManager();
        camera    = new Camera(WIDTH, HEIGHT);
        particles = new ParticleSystem();
        hitStop   = new HitStop();
        hud       = new HUD();

        this.addKeyListener(input);
        this.addKeyListener(this);

        for (int i = 0; i < starTwinkle.length; i++)
            starTwinkle[i] = (float)(Math.random() * Math.PI * 2);

        starPositions = new int[][] {
            {55,22},{148,48},{275,15},{388,62},{510,30},{625,55},{712,18},
            {190,95},{340,80},{460,105},{590,70},{730,90},{80,130},{220,115},
            {380,145},{530,120},{660,140},{760,55},{420,35},{680,110}
        };
        for (int i = 0; i < fireflyX.length; i++) {
            fireflyX[i]    = (float)(Math.random() * WIDTH);
            fireflyY[i]    = (float)(100 + Math.random() * 350);
            fireflyPhase[i]= (float)(Math.random() * Math.PI * 2);
        }
        loadLevel(1);
    }

    void loadLevel(int num) {
        currentLevelNum  = num;
        currentLevel     = Level.getLevel(num);
        particles.clear();
        bullets.clear();
        player           = new Player(100, 380, input, particles);
        boss             = new Boss(currentLevel.bossSpawnX, currentLevel.bossSpawnY,
                                   currentLevel.bossLeftBound, currentLevel.bossRightBound);
        bossTriggered    = false;
        bossDefeated     = false;
        cinematicTimer   = 0;
        healChargesLeft  = 2;
        camera.snapTo(100, 380);
        camera.closeLetterbox();
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
                try { Thread.sleep(1); } catch (Exception ignored) {}
            }
        }
    }

    // ── Update ────────────────────────────────────────────────────────────
    void update() {
        frameCount++;
        input.tick();
        hitStop.tick();

        // Drift fireflies
        for (int i = 0; i < fireflyX.length; i++) {
            fireflyPhase[i] += 0.018f;
            fireflyX[i] += (float)(Math.sin(fireflyPhase[i] * 0.7) * 0.4);
            fireflyY[i] += (float)(Math.cos(fireflyPhase[i] * 0.5) * 0.25);
            if (fireflyX[i] < 0)  fireflyX[i] += WIDTH;
            if (fireflyX[i] > WIDTH) fireflyX[i] -= WIDTH;
            if (fireflyY[i] < 50)  fireflyY[i] = 50;
            if (fireflyY[i] > 420) fireflyY[i] = 420;
        }

        if (state == GameState.MENU || state == GameState.GAMEOVER
                || state == GameState.WIN || state == GameState.PAUSED)
            return;

        if (hitStop.isActive()) {
            particles.update();
            return;
        }

        // ── Day / Night ───────────────────────────────────────────────────
        timeOfDay += timeSpeed;
        if (timeOfDay > 1f) timeOfDay = 0f;
        phase = timeOfDay < 0.57f ? "NIGHT" : timeOfDay < 0.71f ? "DUSK" : "DAY";

        // ── Boss cinematic ─────────────────────────────────────────────────
        if (state == GameState.BOSS_CINEMATIC) {
            updateBossCinematic();
            particles.update();
            return;
        }

        // ── Player ────────────────────────────────────────────────────────
        player.update(phase, currentLevel.platforms, WALL,
                currentLevel.worldW, currentLevel.worldH);

        // ── Camera ────────────────────────────────────────────────────────
        camera.update((int)player.x, (int)player.y, player.facingRight,
                currentLevel.worldW, currentLevel.worldH);

        // ── Trigger boss cinematic ─────────────────────────────────────────
        if (!bossTriggered && !bossDefeated
                && player.x > currentLevel.bossZoneX
                && (state == GameState.PLAYING || state == GameState.BOSS_FIGHT)) {
            bossTriggered   = true;
            cinematicTimer  = CINEMATIC_FRAMES;
            state           = GameState.BOSS_CINEMATIC;
            camera.openLetterbox(65);
            boss.startDrop();
            return;
        }

        // ── Q Ability activation ──────────────────────────────────────────
        if (input.abilityJustPressed) {
            int cost = player.activateAbility(phase, healChargesLeft);
            if (cost >= 0) healChargesLeft -= cost;
        }

        // ── Regular enemies (frozen during boss states) ───────────────────
        Rectangle attackBox = player.isAttacking ? player.getAttackBox() : null;

        // Compute decoy target for clones (pick the closer clone to each enemy)
        float decoyX = player.isShadowClone ? player.clonePositions[0] : -1f;
        float decoyY = player.isShadowClone ? player.clonePositions[1] : -1f;

        if (state == GameState.PLAYING) {
            Iterator<Enemy> iter = currentLevel.enemies.iterator();
            while (iter.hasNext()) {
                Enemy enemy = iter.next();
                boolean gone = enemy.update((int)player.x, (int)player.y, bullets,
                        player.isInvisible, decoyX, decoyY);
                if (gone) {
                    particles.spawn(enemy.x + Enemy.W / 2f, enemy.y + Enemy.H / 2f,
                            ParticleSystem.Type.DEATH);
                    iter.remove();
                    killCount++;
                    // Heal on kill
                    if (healChargesLeft > 0 && player.health < player.maxHealth) {
                        player.health++;
                        healChargesLeft--;
                        particles.spawn(player.x + Player.W / 2f, player.y + Player.H / 2f,
                                ParticleSystem.Type.HEAL);
                    }
                    continue;
                }
                if (enemy.isDead()) continue;

                // Player melee hits enemy
                if (attackBox != null && attackBox.intersects(enemy.getRect())
                        && enemy.invincibleTimer == 0) {
                    enemy.takeDamage(1);
                    camera.addTrauma(0.28f);
                    hitStop.trigger(4);
                    particles.spawn(enemy.x + Enemy.W / 2f, enemy.y + Enemy.H / 2f,
                            ParticleSystem.Type.BLOOD);
                    particles.spawn(enemy.x + Enemy.W / 2f, enemy.y + Enemy.H / 2f,
                            ParticleSystem.Type.SPARK, player.facingRight);
                }
                // Enemy contact hits player (skip if invisible)
                if (!player.isInvisible
                        && player.getRect().intersects(enemy.getAttackRect())
                        && player.invincibleTimer == 0) {
                    player.takeDamage();
                    player.knockBack(player.x > enemy.x);
                    camera.addTrauma(0.38f);
                    hitStop.trigger(3);
                    particles.spawn(player.x + Player.W / 2f, player.y + Player.H / 2f,
                            ParticleSystem.Type.BLOOD);
                }
            }
        }

        // ── Boss fight ────────────────────────────────────────────────────
        if (state == GameState.BOSS_FIGHT) {
            boolean bossDone = boss.update(
                    player.x + Player.W / 2f, player.y + Player.H / 2f,
                    bullets, currentLevel.platforms);
            if (bossDone) {
                // Boss fully gone
                particles.spawn(boss.x + Boss.W / 2f, boss.y + Boss.H / 2f,
                        ParticleSystem.Type.DEATH);
                bossDefeated = true;
                camera.closeLetterbox();
                state = GameState.WIN;
            } else if (!boss.dead) {
                // Player melee hits boss
                if (attackBox != null && attackBox.intersects(boss.getRect())
                        && boss.invincibleTimer == 0) {
                    boss.takeDamage(1);
                    camera.addTrauma(0.35f);
                    hitStop.trigger(5);
                    particles.spawn(boss.x + Boss.W / 2f, boss.y + Boss.H / 2f,
                            ParticleSystem.Type.BLOOD);
                    particles.spawn(boss.x + Boss.W / 2f, boss.y + Boss.H / 2f,
                            ParticleSystem.Type.SPARK, player.facingRight);
                }
                // Boss contact hits player
                if (player.getRect().intersects(boss.getRect())
                        && player.invincibleTimer == 0) {
                    player.takeDamage();
                    player.knockBack(player.x > boss.x);
                    camera.addTrauma(0.5f);
                    hitStop.trigger(4);
                }
            }
        }

        // ── Bullets (time warp slows them; invisibility lets them pass) ────
        float bulletMult = player.isTimeWarp ? 0.3f : 1.0f;
        Iterator<Bullet> bIter = bullets.iterator();
        while (bIter.hasNext()) {
            Bullet b = bIter.next();
            // Apply time warp slow
            if (player.isTimeWarp) {
                b.x += b.velX * bulletMult;
                b.y += b.velY * bulletMult;
                // Still age the bullet, but don't call full update (avoids double move)
            } else {
                b.update();
            }
            if (!b.active) { bIter.remove(); continue; }
            // Bullets pass through invisible player
            if (!player.isInvisible
                    && b.getRect().intersects(player.getRect())
                    && player.invincibleTimer == 0) {
                player.takeDamage();
                player.knockBack(b.velX < 0);
                camera.addTrauma(0.42f);
                hitStop.trigger(3);
                particles.spawn(player.x + Player.W / 2f, player.y + Player.H / 2f,
                        ParticleSystem.Type.BLOOD);
                b.active = false;
                bIter.remove();
            }
        }

        // ── Particles ─────────────────────────────────────────────────────
        particles.update();

        // ── Player death ──────────────────────────────────────────────────
        if (player.isDead()) {
            state = GameState.GAMEOVER;
            return;
        }

        // ── Portal (night only, boss must be defeated) ─────────────────────
        Rectangle portal = new Rectangle(currentLevel.portalX - 25, currentLevel.portalY, 50, 60);
        if (player.getRect().intersects(portal) && phase.equals("NIGHT") && bossDefeated) {
            state = GameState.WIN;
        }
    }

    // ── Cinematic update ──────────────────────────────────────────────────
    private void updateBossCinematic() {
        cinematicTimer--;
        // Camera pans toward boss
        camera.panTo(boss.x + Boss.W / 2f,
                     boss.y + Boss.H / 2f,
                     currentLevel.worldW, currentLevel.worldH);
        // Boss drops in during middle of cinematic
        if (cinematicTimer < 150) {
            boss.update(boss.x + Boss.W / 2f, boss.y + Boss.H / 2f,
                        bullets, currentLevel.platforms);
        }
        // Cinematic over → boss fight
        if (cinematicTimer <= 0) {
            boss.startFight();
            camera.closeLetterbox();
            state = GameState.BOSS_FIGHT;
        }
    }

    // ── Render ────────────────────────────────────────────────────────────
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        // ── Sky gradient ──────────────────────────────────────────────────
        Color skyTop, skyMid, skyBot;
        if (phase.equals("NIGHT")) {
            skyTop = new Color(2, 2, 14); skyMid = new Color(6, 5, 28); skyBot = new Color(14, 10, 50);
        } else if (phase.equals("DUSK")) {
            skyTop = new Color(12, 3, 22); skyMid = new Color(80, 20, 40); skyBot = new Color(220, 80, 25);
        } else {
            skyTop = new Color(30, 90, 200); skyMid = new Color(90, 160, 240); skyBot = new Color(180, 220, 255);
        }
        g2.setPaint(new GradientPaint(0, 0, skyTop, 0, HEIGHT / 2, skyMid));
        g2.fillRect(0, 0, WIDTH, HEIGHT / 2);
        g2.setPaint(new GradientPaint(0, HEIGHT / 2, skyMid, 0, HEIGHT, skyBot));
        g2.fillRect(0, HEIGHT / 2, WIDTH, HEIGHT / 2);

        if (state == GameState.MENU) {
            hud.draw(g2, state, phase, false, 0, timeOfDay, 5, 5, WIDTH, HEIGHT, killCount,
                    0, 0, 0, false,
                    false, 0, 300, false, 0, 240, false, 0, 300, 0, 900);
            return;
        }

        if (!phase.equals("DAY")) drawStars(g2);
        drawCelestialBody(g2);
        drawParallax(g2);
        drawFog(g2);
        if (phase.equals("NIGHT")) drawFireflies(g2);

        // ── World space ───────────────────────────────────────────────────
        AffineTransform saved = camera.apply(g2);

        // Walls
        g2.setPaint(new GradientPaint(0, 0, new Color(15, 8, 28), 0, HEIGHT, new Color(25, 12, 45)));
        g2.fillRect(0, 0, WALL, HEIGHT);
        g2.fillRect(currentLevel.worldW - WALL, 0, WALL, HEIGHT);
        g2.setColor(new Color(120, 60, 200, 40));
        g2.fillRect(WALL, 0, 4, HEIGHT);
        g2.fillRect(currentLevel.worldW - WALL - 4, 0, 4, HEIGHT);

        drawPlatforms(g2);
        drawPortal(g2);

        // Boss zone warning line (visible before trigger)
        if (!bossTriggered && !bossDefeated && state == GameState.PLAYING) {
            float warningAlpha = 0.3f + 0.3f * (float)Math.sin(frameCount * 0.08);
            g2.setColor(new Color(200, 50, 50, (int)(warningAlpha * 200)));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    1f, new float[]{8, 8}, 0));
            g2.drawLine(currentLevel.bossZoneX, 0, currentLevel.bossZoneX, HEIGHT);
            g2.setStroke(new BasicStroke(1f));
            g2.setFont(new Font("Arial Narrow", Font.BOLD, 12));
            g2.setColor(new Color(220, 80, 80, (int)(warningAlpha * 230)));
            g2.drawString("⚠ DANGER ZONE", currentLevel.bossZoneX - 60, 80);
        }

        // Enemies (only during PLAYING)
        if (state == GameState.PLAYING) {
            for (Enemy enemy : currentLevel.enemies) enemy.draw(g2);
        }

        // Boss
        if (bossTriggered) boss.draw(g2);

        // Bullets
        for (Bullet b : bullets) b.draw(g2);

        // Particles (world space)
        particles.draw(g2);

        // Shadow clones (world space, drawn behind player)
        player.drawClones(g2);

        // Player
        player.draw(g2, phase);

        // Wall-jump hint
        if ((player.touchingWallLeft || player.touchingWallRight) && !player.onGround) {
            g2.setColor(new Color(200, 200, 255, 180));
            g2.setFont(new Font("Arial Narrow", Font.BOLD, 11));
            g2.drawString("WALL ▶ SPACE",
                    player.touchingWallRight ? (int)player.x - 80 : (int)player.x + 40,
                    (int)player.y + 20);
        }

        // ── Screen space ──────────────────────────────────────────────────
        camera.restore(g2, saved);
        camera.drawLetterbox(g2);

        // Cinematic title card
        if (state == GameState.BOSS_CINEMATIC) drawBossTitle(g2);

        drawLevelName(g2);

        // HUD
        hud.draw(g2, state, phase,
                player.canDash, player.jumpCount,
                timeOfDay, player.health, player.maxHealth,
                WIDTH, HEIGHT, killCount,
                healChargesLeft, boss.health, boss.maxHealth,
                state == GameState.BOSS_FIGHT && !boss.dead,
                player.isInvisible, player.invisibleTimer, 300,
                player.isTimeWarp, player.timeWarpTimer, 240,
                player.isShadowClone, player.shadowCloneTimer, 300,
                player.timeWarpCooldown, 900);
    }

    // ── Boss title card ───────────────────────────────────────────────────
    private void drawBossTitle(Graphics2D g2) {
        int timer = cinematicTimer;
        if (timer > 120 || timer <= 0) return;

        float fadeIn  = Math.min(1f, (120f - timer) / 30f);
        float fadeOut = timer <= 30 ? timer / 30f : 1f;
        float alpha   = fadeIn * fadeOut;
        int   ia      = (int)(alpha * 255);

        // Dark overlay band
        g2.setColor(new Color(0, 0, 0, (int)(alpha * 150)));
        g2.fillRect(0, HEIGHT / 2 - 75, WIDTH, 150);

        // Decorative lines
        g2.setColor(new Color(180, 40, 40, (int)(alpha * 180)));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(80, HEIGHT / 2 - 30, WIDTH - 80, HEIGHT / 2 - 30);
        g2.drawLine(80, HEIGHT / 2 + 50, WIDTH - 80, HEIGHT / 2 + 50);
        g2.setStroke(new BasicStroke(1f));

        // Boss name shadow
        g2.setFont(new Font("Arial Narrow", Font.BOLD, 54));
        String name = "SHADOW COMMANDER";
        int nw = g2.getFontMetrics().stringWidth(name);
        g2.setColor(new Color(140, 20, 20, (int)(alpha * 180)));
        g2.drawString(name, WIDTH / 2 - nw / 2 + 3, HEIGHT / 2 + 8);
        // Boss name
        g2.setColor(new Color(240, 70, 70, Math.min(255, (int)(alpha * 255))));
        g2.drawString(name, WIDTH / 2 - nw / 2, HEIGHT / 2 + 5);

        // Subtitle
        g2.setFont(new Font("Arial Narrow", Font.PLAIN, 16));
        String sub = "Guardian of the Eclipse Portal";
        int sw = g2.getFontMetrics().stringWidth(sub);
        g2.setColor(new Color(200, 150, 150, (int)(alpha * 210)));
        g2.drawString(sub, WIDTH / 2 - sw / 2, HEIGHT / 2 + 34);

        // Phase indicator
        g2.setFont(new Font("Arial Narrow", Font.BOLD, 11));
        g2.setColor(new Color(255, 100, 100, (int)(alpha * 180)));
        g2.drawString("PHASE I — ENCOUNTER", WIDTH / 2 - 52, HEIGHT / 2 - 40);
    }

    // ── Stars ─────────────────────────────────────────────────────────────
    private void drawStars(Graphics2D g2) {
        long now = System.currentTimeMillis();
        float duskFade = phase.equals("DUSK") ? 0.4f : 1f;
        for (int i = 0; i < starPositions.length; i++) {
            float twinkle = 0.5f + 0.5f * (float)Math.sin(now * 0.001 + starTwinkle[i]);
            int alpha = (int)(twinkle * 220 * duskFade);
            if (alpha < 10) continue;
            int sz = (i % 3 == 0) ? 3 : 2;
            int sx = starPositions[i][0], sy = starPositions[i][1];
            g2.setColor(new Color(200, 210, 255, alpha / 4));
            g2.fillOval(sx - 2, sy - 2, sz + 4, sz + 4);
            g2.setColor(new Color(240, 240, 255, alpha));
            g2.fillOval(sx, sy, sz, sz);
        }
    }

    // ── Celestial body ────────────────────────────────────────────────────
    private void drawCelestialBody(Graphics2D g2) {
        long now = System.currentTimeMillis();
        if (phase.equals("NIGHT")) {
            float pulse = 0.8f + 0.2f * (float)Math.sin(now * 0.0007);
            g2.setColor(new Color(180, 200, 255, (int)(18 * pulse)));
            g2.fillOval(618, 8, 120, 120);
            g2.setColor(new Color(200, 215, 255, (int)(35 * pulse)));
            g2.fillOval(632, 18, 90, 90);
            g2.setColor(new Color(248, 248, 230, (int)(210 * pulse)));
            g2.fillOval(648, 28, 60, 60);
            g2.setColor(new Color(6, 4, 22));
            g2.fillOval(658, 28, 46, 52);
            g2.setColor(new Color(248, 248, 230, (int)(220 * pulse)));
            g2.fillOval(648, 28, 50, 60);
            g2.setColor(new Color(200, 200, 180, 60));
            g2.fillOval(654, 36, 12, 10); g2.fillOval(660, 55, 9, 8); g2.fillOval(650, 65, 6, 6);
        } else if (phase.equals("DAY")) {
            g2.setColor(new Color(255, 230, 100, 30)); g2.fillOval(110, 15, 100, 100);
            g2.setColor(new Color(255, 240, 120, 60)); g2.fillOval(125, 28, 74, 74);
            g2.setPaint(new GradientPaint(135, 35, new Color(255, 255, 180), 162, 62, new Color(255, 200, 50)));
            g2.fillOval(140, 38, 55, 55);
        } else {
            g2.setColor(new Color(255, 100, 30, 80)); g2.fillOval(90, HEIGHT - 80, 200, 120);
            g2.setColor(new Color(255, 140, 50, 120)); g2.fillOval(150, HEIGHT - 50, 80, 60);
        }
    }

    // ── Parallax ──────────────────────────────────────────────────────────
    private void drawParallax(Graphics2D g2) {
        float cx = camera.camX;
        boolean night = phase.equals("NIGHT"), dusk = phase.equals("DUSK");

        // Layer 1: far mountains (0.08x)
        int off1 = (int)(cx * 0.08f);
        g2.setColor(night ? new Color(8, 6, 22) : dusk ? new Color(35, 10, 30) : new Color(40, 70, 130));
        for (int i = -1; i < 9; i++) {
            int bx = i * 250 - (off1 % 250), bh = 100 + (i * 37 + 200) % 70;
            g2.fillPolygon(new int[]{bx, bx+125, bx+250}, new int[]{HEIGHT-20, HEIGHT-20-bh, HEIGHT-20}, 3);
        }
        // Layer 2: mid mountains (0.18x)
        int off2 = (int)(cx * 0.18f);
        g2.setColor(night ? new Color(12, 8, 30) : dusk ? new Color(50, 18, 38) : new Color(55, 90, 155));
        for (int i = -1; i < 7; i++) {
            int bx = i * 340 - (off2 % 340), bh = 140 + (i * 53 + 100) % 90, midX = bx + 170;
            g2.fillPolygon(new int[]{bx, midX-20, midX, midX+20, bx+340},
                    new int[]{HEIGHT-10, HEIGHT-10-bh+30, HEIGHT-10-bh, HEIGHT-10-bh+30, HEIGHT-10}, 5);
        }
        // Layer 3: ruined castles (0.38x)
        int off3 = (int)(cx * 0.38f);
        Color ruinCol = night ? new Color(18, 12, 38) : dusk ? new Color(55, 22, 45) : new Color(70, 110, 170);
        g2.setColor(ruinCol);
        for (int i = -1; i < 5; i++) {
            int bx = i * 520 - (off3 % 520);
            g2.fillRect(bx+80, HEIGHT-220, 70, 200);
            g2.fillRect(bx+110, HEIGHT-265, 20, 50);
            g2.fillRect(bx+90, HEIGHT-255, 10, 40);
            g2.fillRect(bx+130, HEIGHT-255, 10, 40);
            g2.fillRect(bx+30, HEIGHT-175, 40, 155);
            g2.fillRect(bx+25, HEIGHT-195, 50, 25);
            g2.fillRect(bx+155, HEIGHT-155, 40, 135);
            g2.fillRect(bx+150, HEIGHT-175, 50, 25);
            Color wc = night ? new Color(50, 80, 160, 100) : dusk ? new Color(200, 80, 30, 80) : new Color(120, 160, 220, 120);
            g2.setColor(wc);
            g2.fillOval(bx+100, HEIGHT-240, 14, 18); g2.fillRect(bx+100, HEIGHT-230, 14, 10);
            g2.fillRect(bx+42, HEIGHT-155, 10, 14); g2.fillRect(bx+166, HEIGHT-140, 10, 14);
            g2.setColor(ruinCol);
            g2.fillRect(bx+185, HEIGHT-80, 50, 60); g2.fillRect(bx+195, HEIGHT-90, 20, 15);
        }
        // Ground blend
        Color gt = night ? new Color(16, 10, 36) : dusk ? new Color(55, 22, 45) : new Color(60, 95, 150);
        Color gb = night ? new Color(20, 14, 45) : dusk ? new Color(65, 30, 55) : new Color(70, 110, 170);
        g2.setPaint(new GradientPaint(0, HEIGHT-25, gt, 0, HEIGHT, gb));
        g2.fillRect(0, HEIGHT-25, WIDTH, 25);
    }

    // ── Fog ───────────────────────────────────────────────────────────────
    private void drawFog(Graphics2D g2) {
        long now = System.currentTimeMillis();
        Color fogCol = phase.equals("NIGHT") ? new Color(30, 20, 60, 28)
                : phase.equals("DUSK") ? new Color(80, 30, 40, 22) : new Color(160, 190, 220, 18);
        float off1 = (float)((camera.camX * 0.05 + now * 0.008) % WIDTH);
        float off2 = (float)((camera.camX * 0.07 + now * 0.005 + 300) % WIDTH);
        g2.setColor(fogCol);
        g2.fillRoundRect((int)(WIDTH - off1) - WIDTH, HEIGHT - 120, WIDTH * 3, 35, 80, 80);
        g2.setColor(new Color(fogCol.getRed(), fogCol.getGreen(), fogCol.getBlue(), 18));
        g2.fillRoundRect((int)(WIDTH - off2) - WIDTH, HEIGHT - 85, WIDTH * 3, 28, 80, 80);
        g2.setColor(new Color(fogCol.getRed(), fogCol.getGreen(), fogCol.getBlue(), 35));
        g2.fillRect(0, HEIGHT - 40, WIDTH, 40);
    }

    // ── Fireflies ─────────────────────────────────────────────────────────
    private void drawFireflies(Graphics2D g2) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < fireflyX.length; i++) {
            float brightness = 0.4f + 0.6f * (float)Math.sin(now * 0.003 + fireflyPhase[i]);
            if (brightness < 0.1f) continue;
            int alpha = (int)(brightness * 180);
            g2.setColor(new Color(180, 255, 140, alpha / 5));
            g2.fillOval((int)fireflyX[i] - 5, (int)fireflyY[i] - 5, 10, 10);
            g2.setColor(new Color(200, 255, 160, alpha));
            g2.fillOval((int)fireflyX[i] - 2, (int)fireflyY[i] - 2, 4, 4);
        }
    }

    // ── Platforms ─────────────────────────────────────────────────────────
    private void drawPlatforms(Graphics2D g2) {
        long now = System.currentTimeMillis();
        for (Rectangle p : currentLevel.platforms) {
            g2.setPaint(new GradientPaint(p.x, p.y, new Color(35, 22, 55), p.x, p.y+p.height, new Color(22, 14, 38)));
            g2.fillRect(p.x, p.y, p.width, p.height);
            float glow = 0.55f + 0.45f * (float)Math.sin(now * 0.002 + p.x * 0.005);
            g2.setPaint(new GradientPaint(p.x, p.y, new Color(160, 80, 240, (int)(glow*200)), p.x, p.y+7, new Color(80, 40, 160, 0)));
            g2.fillRect(p.x, p.y, p.width, 7);
            g2.setColor(new Color(200, 140, 255, (int)(glow*180)));
            g2.drawLine(p.x+1, p.y, p.x+p.width-1, p.y);
            g2.setColor(new Color(100, 60, 160, 60));
            g2.drawLine(p.x, p.y, p.x, p.y+p.height);
            g2.drawLine(p.x+p.width-1, p.y, p.x+p.width-1, p.y+p.height);
            g2.setColor(new Color(160, 100, 240, 40));
            for (int rx = p.x+20; rx < p.x+p.width-10; rx+=28) {
                g2.fillRect(rx, p.y+5, 10, 3);
                g2.fillPolygon(new int[]{rx+5, rx+8, rx+5, rx+2}, new int[]{p.y+5, p.y+8, p.y+11, p.y+8}, 4);
            }
            if (p.width > 80) {
                drawCrystal(g2, p.x+12, p.y, glow);
                if (p.width > 140) drawCrystal(g2, p.x+p.width-20, p.y, glow);
                if (p.width > 220) drawCrystal(g2, p.x+p.width/2, p.y, glow);
            }
        }
    }

    private void drawCrystal(Graphics2D g2, int cx, int platTop, float glow) {
        int h = 14, w = 6, alpha = (int)(glow*160+40);
        g2.setColor(new Color(180, 100, 255, alpha/4));
        g2.fillOval(cx-4, platTop-h-4, w+8, h+8);
        g2.setColor(new Color(160, 80, 240, alpha));
        g2.fillPolygon(new int[]{cx, cx+w/2, cx+w, cx+w-1, cx+1}, new int[]{platTop-h, platTop-h-5, platTop-h, platTop, platTop}, 5);
        g2.setColor(new Color(220, 170, 255, (int)(alpha*0.6)));
        g2.drawLine(cx+1, platTop-h+2, cx+w/2, platTop-h-4);
    }

    // ── Portal ────────────────────────────────────────────────────────────
    private void drawPortal(Graphics2D g2) {
        long now = System.currentTimeMillis();
        int px = currentLevel.portalX, py = currentLevel.portalY;
        boolean active = phase.equals("NIGHT") && bossDefeated;
        if (active) {
            float pulse = (float)(0.55 + 0.45*Math.sin(now*0.003));
            int gs = (int)(90*pulse);
            g2.setColor(new Color(100, 80, 220, 18)); g2.fillOval(px-gs/2, py-gs/4, gs+60, gs+60);
            g2.setColor(new Color(140, 100, 255, 30)); g2.fillOval(px-gs/3, py-gs/6, gs+40, gs+40);
            g2.setColor(new Color(200, 180, 255, 130)); g2.fillOval(px-30, py, 60, 60);
            g2.setColor(new Color(30, 20, 70)); g2.fillOval(px-24, py+4, 48, 48);
            float swirl = (float)(now*0.001);
            for (int s = 0; s < 3; s++) {
                float a = swirl + s*(float)(Math.PI*2/3);
                g2.setColor(new Color(160, 130, 255, (int)(80+60*pulse)));
                g2.fillOval(px+(int)(Math.cos(a)*10)-4, py+28+(int)(Math.sin(a)*6)-4, 8, 8);
            }
            g2.setColor(new Color(240, 230, 255, (int)(200*pulse)));
            g2.fillOval(px-7, py+22, 14, 14);
            g2.setColor(new Color(200, 190, 255, (int)(200*pulse)));
            g2.setFont(new Font("Arial Narrow", Font.BOLD, 12));
            g2.drawString("▲ ASCEND", px-26, py-10);
        } else {
            // Dormant — show lock icon if boss not defeated yet
            g2.setColor(new Color(30, 22, 48)); g2.fillOval(px-18, py+10, 36, 36);
            g2.setColor(new Color(60, 40, 90, 100)); g2.drawOval(px-18, py+10, 36, 36);
            if (!bossDefeated && phase.equals("NIGHT")) {
                g2.setFont(new Font("Arial Narrow", Font.BOLD, 11));
                g2.setColor(new Color(200, 100, 100, 180));
                g2.drawString("⚔ DEFEAT BOSS", px-42, py-8);
            }
        }
    }

    // ── Level name ────────────────────────────────────────────────────────
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
        if (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_SPACE) {
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
            if (state == GameState.PLAYING)      state = GameState.PAUSED;
            else if (state == GameState.PAUSED)  state = GameState.PLAYING;
        }
    }

    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}
}