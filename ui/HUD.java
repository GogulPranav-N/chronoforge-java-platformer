package ui;

import engine.GameState;
import java.awt.*;

public class HUD {

    public void draw(Graphics2D g2, GameState state, String phase,
                     boolean canDash, int jumpCount, float timeOfDay,
                     int health, int maxHealth, int screenW, int screenH) {

        if (state == GameState.MENU) {
            drawMenu(g2, screenW, screenH);
        } else if (state == GameState.PLAYING || state == GameState.PAUSED) {
            drawPlayingHUD(g2, phase, canDash, jumpCount, timeOfDay,
                           health, maxHealth, screenW, screenH);
            if (state == GameState.PAUSED) drawPause(g2, screenW, screenH);
        } else if (state == GameState.GAMEOVER) {
            drawGameOver(g2, screenW, screenH);
        } else if (state == GameState.WIN) {
            drawWin(g2, screenW, screenH);
        }
    }

    void drawPlayingHUD(Graphics2D g2, String phase, boolean canDash,
                        int jumpCount, float timeOfDay, int health,
                        int maxHealth, int screenW, int screenH) {
        // HUD panel
        g2.setColor(new Color(0,0,0,140));
        g2.fillRoundRect(8, 8, 240, 85, 12, 12);
        g2.setColor(new Color(130,100,200));
        g2.drawRoundRect(8, 8, 240, 85, 12, 12);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 13));
        g2.drawString("Phase: " + phase, 18, 28);
        g2.drawString("Power: " + (phase.equals("NIGHT") ? "FULL ⚡" :
                       phase.equals("DUSK") ? "HALF" : "NONE"), 18, 46);
        g2.setColor(canDash && phase.equals("NIGHT") ? new Color(150,255,150) :
                    !phase.equals("NIGHT") ? new Color(255,150,150) : new Color(255,200,100));
        g2.drawString("Dash [SHIFT]: " + (canDash && phase.equals("NIGHT") ? "READY" :
                       !phase.equals("NIGHT") ? "NIGHT ONLY" : "COOLDOWN"), 18, 64);
        g2.setColor(Color.WHITE);
        g2.drawString(phase.equals("NIGHT") ? "Reach the Moon!" : "Wait for nightfall...", 18, 82);

        // Health bar
        g2.setColor(new Color(0,0,0,140));
        g2.fillRoundRect(8, 100, 160, 22, 8, 8);
        for (int i = 0; i < maxHealth; i++) {
            g2.setColor(i < health ? new Color(255,80,80) : new Color(60,40,40));
            g2.fillOval(14 + i * 28, 105, 18, 12);
        }

        // Jump dots
        g2.setColor(new Color(0,0,0,140));
        g2.fillRoundRect(8, 128, 90, 22, 8, 8);
        for (int i = 0; i < 2; i++) {
            g2.setColor(i < (2 - jumpCount) ? new Color(150,200,255) : new Color(60,60,80));
            g2.fillOval(14 + i * 35, 133, 16, 12);
        }

        // Day cycle bar
        g2.setColor(new Color(0,0,0,130));
        g2.fillRoundRect(screenW-168, 8, 158, 28, 8, 8);
        g2.setColor(new Color(255,255,255,30));
        g2.fillRoundRect(screenW-160, 13, 142, 14, 6, 6);
        g2.setColor(phase.equals("NIGHT") ? new Color(100,100,255) :
                    phase.equals("DUSK")  ? new Color(255,120,50) : new Color(255,220,50));
        g2.fillRoundRect(screenW-160, 13, (int)(timeOfDay*142), 14, 6, 6);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.PLAIN, 10));
        g2.drawString("Day Cycle", screenW-148, 34);

        // Controls hint
        g2.setColor(new Color(255,255,255,100));
        g2.setFont(new Font("Arial", Font.PLAIN, 11));
        g2.drawString("A/D: Move  SPACE: Jump  SHIFT+A/D: Dash  ESC: Pause", 15, screenH-10);
    }

    void drawMenu(Graphics2D g2, int w, int h) {
        g2.setColor(new Color(0,0,0,200));
        g2.fillRect(0, 0, w, h);
        g2.setColor(new Color(200,200,255));
        g2.setFont(new Font("Arial", Font.BOLD, 60));
        g2.drawString("CHRONOFORGE", 120, 200);
        g2.setFont(new Font("Arial", Font.BOLD, 22));
        g2.setColor(Color.WHITE);
        g2.drawString("Press  ENTER  to Start", 280, 300);
        g2.setFont(new Font("Arial", Font.PLAIN, 15));
        g2.setColor(new Color(180,180,255));
        g2.drawString("A moon knight cursed warrior — survive the eclipse", 220, 360);
    }

    void drawPause(Graphics2D g2, int w, int h) {
        g2.setColor(new Color(0,0,0,150));
        g2.fillRect(0, 0, w, h);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 50));
        g2.drawString("PAUSED", 300, 260);
        g2.setFont(new Font("Arial", Font.BOLD, 20));
        g2.drawString("Press ESC to Resume", 295, 310);
    }

    void drawGameOver(Graphics2D g2, int w, int h) {
        g2.setColor(new Color(0,0,0,180));
        g2.fillRect(0, 0, w, h);
        g2.setColor(new Color(255,80,80));
        g2.setFont(new Font("Arial", Font.BOLD, 60));
        g2.drawString("GAME OVER", 210, 260);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 20));
        g2.drawString("Press  ENTER  to Retry", 285, 320);
    }

    void drawWin(Graphics2D g2, int w, int h) {
        g2.setColor(new Color(0,0,0,170));
        g2.fillRect(0, 0, w, h);
        g2.setColor(new Color(200,200,255));
        g2.setFont(new Font("Arial", Font.BOLD, 52));
        g2.drawString("LEVEL COMPLETE!", 140, 260);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 20));
        g2.drawString("Press  ENTER  to Continue", 265, 320);
    }
}