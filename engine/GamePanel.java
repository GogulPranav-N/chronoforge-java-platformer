package engine;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import javax.swing.JPanel;
import levels.Level;
import player.KeyHandler;
import player.Player;
import ui.HUD;

public class GamePanel extends JPanel implements Runnable, KeyListener {

    static final int WIDTH = 800, HEIGHT = 600;
    static final int WALL = 15;

    // Engine
    Thread gameThread;
    GameState state = GameState.MENU;

    // Objects
    Player player;
    Level currentLevel;
    HUD hud;
    KeyHandler keyH;

    // Day/Night
    float timeOfDay = 0f;
    float timeSpeed = 0.00015f;
    String phase = "NIGHT";
    int speed = 5;

    // Screen shake
    int shakeTimer = 0;
    int shakeX, shakeY;

    // Input guards
    boolean enterWasPressed = false;
    boolean escWasPressed   = false;

    public GamePanel() {
        this.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        this.setFocusable(true);
        keyH = new KeyHandler();
        this.addKeyListener(keyH);
        this.addKeyListener(this);
        hud = new HUD();
        loadLevel(1);
    }

    void loadLevel(int num) {
        currentLevel = Level.getLevel(num);
        player = new Player(100, 380);
    }

    public void startGameThread() {
        gameThread = new Thread(this);
        gameThread.start();
    }

    @Override
    public void run() {
        while (gameThread != null) {
            update();
            repaint();
            try { Thread.sleep(16); } catch (Exception e) { e.printStackTrace(); }
        }
    }

    void update() {
        // --- State machine ---
        if (state == GameState.MENU) {
            if (keyH.jumpPressed) state = GameState.PLAYING; // also allow space
            return;
        }

        if (state == GameState.GAMEOVER || state == GameState.WIN) {
            return; // wait for ENTER via keyPressed
        }

        if (state == GameState.PAUSED) return;

        // --- Day/Night ---
        timeOfDay += timeSpeed;
        if (timeOfDay > 1f) timeOfDay = 0f;
        if      (timeOfDay < 0.25f || timeOfDay > 0.75f) { phase = "NIGHT"; speed = 5; }
        else if (timeOfDay < 0.35f || timeOfDay > 0.65f) { phase = "DUSK";  speed = 3; }
        else                                              { phase = "DAY";   speed = 2; }

        // Screen shake
        if (shakeTimer > 0) {
            shakeTimer--;
            shakeX = (int)(Math.random()*6-3);
            shakeY = (int)(Math.random()*6-3);
        } else { shakeX = 0; shakeY = 0; }

        // --- Update player ---
        player.update(keyH, speed, phase,
                      currentLevel.platforms, WALL, WIDTH, HEIGHT);

        // Game over check
        if (player.isDead()) {
            state = GameState.GAMEOVER;
            return;
        }

        // Portal check (night only)
        Rectangle portalRect = new Rectangle(
            currentLevel.portalX - 25, currentLevel.portalY, 50, 60);
        if (player.getRect().intersects(portalRect) && phase.equals("NIGHT")) {
            state = GameState.WIN;
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(shakeX, shakeY);

        // Sky
        Color skyTop, skyBot;
        if      (phase.equals("NIGHT")) { skyTop=new Color(4,4,20);    skyBot=new Color(10,10,50);  }
        else if (phase.equals("DUSK"))  { skyTop=new Color(20,5,30);   skyBot=new Color(200,80,30); }
        else                            { skyTop=new Color(70,140,255); skyBot=new Color(170,210,255); }
        g2.setPaint(new GradientPaint(0,0,skyTop,0,HEIGHT,skyBot));
        g2.fillRect(0, 0, WIDTH, HEIGHT);

        if (state != GameState.MENU) {
            // Stars
            if (phase.equals("NIGHT")) {
                g2.setColor(Color.WHITE);
                int[][] stars = {{60,25},{160,55},{290,18},{490,75},{660,38},
                                 {730,85},{210,105},{410,48},{590,108},{350,160}};
                for (int[] s : stars) g2.fillOval(s[0],s[1],s[0]%3==0?3:2,s[0]%3==0?3:2);
            }

            // Walls
            g2.setColor(new Color(30,20,50));
            g2.fillRect(0, 0, WALL, HEIGHT);
            g2.fillRect(WIDTH-WALL, 0, WALL, HEIGHT);

            // Platforms
            for (Rectangle p : currentLevel.platforms) {
                g2.setColor(new Color(50,35,75));
                g2.fillRect(p.x, p.y, p.width, p.height);
                g2.setColor(new Color(130,90,180));
                g2.fillRect(p.x, p.y, p.width, 4);
                g2.setColor(new Color(180,150,255,80));
                for (int rx = p.x+15; rx < p.x+p.width-10; rx+=30)
                    g2.fillRect(rx, p.y+6, 8, 3);
            }

            // Portal
            int px = currentLevel.portalX, py = currentLevel.portalY;
            if (phase.equals("NIGHT")) {
                float pulse = (float)(0.6 + 0.4*Math.sin(System.currentTimeMillis()*0.003));
                int gs = (int)(70*pulse);
                g2.setColor(new Color(140,140,255,40));
                g2.fillOval(px-gs/2, py-gs/4, gs+50, gs+50);
                g2.setColor(new Color(200,200,255,100));
                g2.fillOval(px-30, py, 60, 60);
                g2.setColor(new Color(235,235,255));
                g2.fillOval(px-22, py+5, 44, 44);
                g2.setColor(new Color(4,4,20));
                g2.fillOval(px-10, py+2, 38, 38);
                g2.setColor(new Color(255,255,200,180));
                g2.setFont(new Font("Arial", Font.BOLD, 13));
                g2.drawString("▲ PORTAL", px-28, py-8);
            } else {
                g2.setColor(new Color(40,40,55));
                g2.fillOval(px-18, py+10, 36, 36);
            }

            // Wall jump hint
            if ((player.touchingWallLeft || player.touchingWallRight) && !player.onGround) {
                g2.setColor(new Color(200,200,255,180));
                g2.setFont(new Font("Arial", Font.BOLD, 11));
                g2.drawString("WALL ▶ SPACE",
                    player.touchingWallRight ? player.x-80 : player.x+40, player.y+20);
            }

            // Player
            player.draw(g2, phase);
        }

        // HUD / screens
        hud.draw(g2, state, phase, player != null && player.canDash,
                 player != null ? player.jumpCount : 0,
                 timeOfDay, player != null ? player.health : 5,
                 5, WIDTH, HEIGHT);
    }

    // ENTER key for state transitions
    @Override public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            if (state == GameState.MENU)     { state = GameState.PLAYING; }
            else if (state == GameState.WIN) { loadLevel(1); state = GameState.PLAYING; }
            else if (state == GameState.GAMEOVER) { loadLevel(1); state = GameState.PLAYING; }
        }
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            if (state == GameState.PLAYING) state = GameState.PAUSED;
            else if (state == GameState.PAUSED) state = GameState.PLAYING;
        }
    }
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}
}