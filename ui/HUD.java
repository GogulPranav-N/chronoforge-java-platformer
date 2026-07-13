package ui;

import engine.GameState;
import java.awt.*;
import java.awt.geom.*;

/**
 * Combat-Mode HUD
 * Dark, angular UI with crimson/orange combat colour palette.
 */
public class HUD {

    // ── Palette ──────────────────────────────────────────────────────────────
    private static final Color C_BG = new Color(8, 4, 4, 210);
    private static final Color C_BORDER = new Color(200, 40, 40, 220);
    private static final Color C_BORDER2 = new Color(255, 100, 30, 180);
    private static final Color C_HP_FULL = new Color(220, 30, 30);
    private static final Color C_HP_MID = new Color(230, 120, 10);
    private static final Color C_HP_LOW = new Color(255, 30, 10);
    private static final Color C_HP_EMPTY = new Color(50, 15, 15);
    private static final Color C_POWER = new Color(255, 160, 0);
    private static final Color C_POWER_BG = new Color(40, 20, 0);
    private static final Color C_NIGHT = new Color(80, 80, 230);
    private static final Color C_DUSK = new Color(230, 100, 30);
    private static final Color C_DAY = new Color(240, 210, 50);
    private static final Color C_WHITE = new Color(235, 220, 210);
    private static final Color C_DIM = new Color(160, 130, 110, 180);
    private static final Color C_DASH_ON = new Color(60, 255, 140);
    private static final Color C_DASH_OFF = new Color(60, 60, 60);

    // ── Entry point ──────────────────────────────────────────────────────────
    public void draw(Graphics2D g2, GameState state, String phase,
            boolean canDash, int jumpCount, float timeOfDay,
            int health, int maxHealth, int screenW, int screenH, int killCount,
            int healChargesLeft, int bossHealth, int bossMaxHealth, boolean showBossBar,
            boolean isInvisible, int invisibleTimer, int invisibleMax,
            boolean isTimeWarp, int timeWarpTimer, int timeWarpMax,
            boolean isShadowClone, int shadowCloneTimer, int shadowCloneMax,
            int timeWarpCooldown, int timeWarpCooldownMax) {

        enableAA(g2);

        if (state == GameState.MENU) {
            drawMenu(g2, screenW, screenH);
        } else if (state == GameState.PLAYING || state == GameState.PAUSED || state == GameState.BOSS_FIGHT) {
            drawCombatHUD(g2, phase, canDash, jumpCount, timeOfDay,
                    health, maxHealth, screenW, screenH, killCount,
                    healChargesLeft, bossHealth, bossMaxHealth, showBossBar);
            drawAbilityPanel(g2, phase,
                    isInvisible, invisibleTimer, invisibleMax,
                    isTimeWarp, timeWarpTimer, timeWarpMax,
                    isShadowClone, shadowCloneTimer, shadowCloneMax,
                    timeWarpCooldown, timeWarpCooldownMax,
                    healChargesLeft, screenW, screenH);
            if (state == GameState.PAUSED)
                drawPause(g2, screenW, screenH);
        } else if (state == GameState.GAMEOVER) {
            drawGameOver(g2, screenW, screenH);
        } else if (state == GameState.WIN) {
            drawWin(g2, screenW, screenH);
        }
    }

    // ── Playing HUD ──────────────────────────────────────────────────────────
    private void drawCombatHUD(Graphics2D g2, String phase, boolean canDash,
            int jumpCount, float timeOfDay,
            int health, int maxHealth,
            int screenW, int screenH, int killCount,
            int healChargesLeft, int bossHealth, int bossMaxHealth, boolean showBossBar) {

        long now = System.currentTimeMillis();

        // ── TOP-LEFT panel ──────────────────────────────────────────────────
        drawAngularPanel(g2, 10, 10, 230, 110);

        // Phase icon + label
        Color phaseCol = phaseCols(phase);
        g2.setColor(phaseCol);
        g2.setFont(combatFont(Font.BOLD, 11));
        String phaseIcon = phase.equals("NIGHT") ? "☽ NIGHT" : phase.equals("DUSK") ? "◑ DUSK" : "☀ DAY";
        g2.drawString(phaseIcon, 22, 30);

        // Divider
        g2.setColor(C_BORDER);
        g2.drawLine(22, 35, 225, 35);

        // Dash status
        boolean dashReady = canDash && phase.equals("NIGHT");
        boolean dashNA = !phase.equals("NIGHT");
        String dashLabel = dashNA ? "DASH  OFF-CYCLE" : dashReady ? "DASH  ▶ READY" : "DASH  ◌ CD";
        Color dashCol = dashNA ? C_DASH_OFF : dashReady ? C_DASH_ON : C_DUSK;

        // Pulsing glow when ready
        if (dashReady) {
            float pulse = 0.55f + 0.45f * (float) Math.sin(now * 0.006);
            g2.setColor(new Color(60, 255, 140, (int) (pulse * 90)));
            g2.fillRoundRect(18, 39, 214, 18, 6, 6);
        }
        g2.setColor(dashCol);
        g2.setFont(combatFont(Font.BOLD, 12));
        g2.drawString(dashLabel, 22, 53);

        // Jump charges
        g2.setColor(C_DIM);
        g2.setFont(combatFont(Font.PLAIN, 11));
        g2.drawString("JUMP CHARGES", 22, 72);
        for (int i = 0; i < 2; i++) {
            boolean charged = i < (2 - jumpCount);
            g2.setColor(charged ? new Color(130, 180, 255) : new Color(35, 35, 55));
            g2.fillRoundRect(22 + i * 34, 76, 28, 12, 5, 5);
            if (charged) {
                g2.setColor(new Color(180, 215, 255, 120));
                g2.fillRoundRect(23 + i * 34, 77, 28, 5, 3, 3);
            }
        }

        // Power label
        String powerTxt = phase.equals("NIGHT") ? "PWR: MAX" : phase.equals("DUSK") ? "PWR: 50%" : "PWR: 0%";
        g2.setColor(phaseCol);
        g2.setFont(combatFont(Font.BOLD, 11));
        g2.drawString(powerTxt, 100, 88);

        // ── HEALTH BAR (bottom-left) ─────────────────────────────────────────
        int hbX = 10, hbY = screenH - 46, hbW = 260, hbH = 22;
        drawAngularPanel(g2, hbX, hbY - 16, hbW + 10, 38);

        g2.setColor(C_DIM);
        g2.setFont(combatFont(Font.BOLD, 10));
        g2.drawString("VITALITY", hbX + 8, hbY - 2);

        // Track
        g2.setColor(C_HP_EMPTY);
        g2.fillRoundRect(hbX + 6, hbY + 4, hbW - 4, hbH - 10, 4, 4);

        // Fill
        float ratio = maxHealth > 0 ? (float) health / maxHealth : 0f;
        int fillW = (int) ((hbW - 4) * ratio);
        Color hpCol = ratio > 0.5f ? C_HP_FULL : ratio > 0.25f ? C_HP_MID : C_HP_LOW;

        if (fillW > 0) {
            // Low-hp flicker
            if (ratio <= 0.25f) {
                float flicker = 0.7f + 0.3f * (float) Math.sin(now * 0.012);
                hpCol = new Color(
                        (int) (hpCol.getRed() * flicker),
                        (int) (hpCol.getGreen() * flicker),
                        (int) (hpCol.getBlue() * flicker));
            }
            GradientPaint gp = new GradientPaint(
                    hbX + 6, hbY + 4, hpCol,
                    hbX + 6, hbY + hbH - 10, hpCol.darker());
            g2.setPaint(gp);
            g2.fillRoundRect(hbX + 6, hbY + 4, fillW, hbH - 10, 4, 4);

            // Shine
            g2.setColor(new Color(255, 255, 255, 50));
            g2.fillRoundRect(hbX + 6, hbY + 4, fillW, (hbH - 10) / 2, 4, 4);
        }

        // HP numbers
        g2.setColor(C_WHITE);
        g2.setFont(combatFont(Font.BOLD, 10));
        g2.drawString(health + " / " + maxHealth, hbX + fillW / 2 - 12, hbY + 14);

        // ── POWER / RAGE meter (bottom-left, above health) ───────────────────
        int pmX = 10, pmY = screenH - 72, pmW = 140, pmH = 12;
        g2.setColor(C_DIM);
        g2.setFont(combatFont(Font.PLAIN, 10));
        g2.drawString("COMBAT POWER", pmX, pmY - 2);

        g2.setColor(C_POWER_BG);
        g2.fillRoundRect(pmX, pmY, pmW, pmH, 4, 4);
        float pwrRatio = phase.equals("NIGHT") ? 1f : phase.equals("DUSK") ? 0.5f : 0.05f;
        int pwrFill = (int) (pmW * pwrRatio);
        GradientPaint gp2 = new GradientPaint(pmX, pmY, C_DUSK, pmX + pwrFill, pmY, C_POWER);
        g2.setPaint(gp2);
        g2.fillRoundRect(pmX, pmY, pwrFill, pmH, 4, 4);
        g2.setColor(new Color(255, 255, 200, 60));
        g2.fillRoundRect(pmX, pmY, pwrFill, pmH / 2, 4, 4);

        // ── DAY-CYCLE bar (top-right) ─────────────────────────────────────────
        int barX = screenW - 180, barY = 10, barW = 170, barH = 30;
        drawAngularPanel(g2, barX, barY, barW, barH);

        g2.setColor(new Color(255, 255, 255, 20));
        g2.fillRoundRect(barX + 8, barY + 8, barW - 16, barH - 16, 4, 4);

        Color cycleCol = phaseCol;
        float dw = (barW - 16) * timeOfDay;
        GradientPaint gpCycle = new GradientPaint(
                barX + 8, 0, cycleCol.darker(), barX + 8 + (int) dw, 0, cycleCol);
        g2.setPaint(gpCycle);
        g2.fillRoundRect(barX + 8, barY + 8, (int) dw, barH - 16, 4, 4);

        g2.setColor(C_WHITE);
        g2.setFont(combatFont(Font.BOLD, 10));
        g2.drawString("TIME CYCLE", barX + 8, barY + barH - 6);

        // ── Kill counter (top-right, below time cycle bar) ───────────────────
        int kcX = screenW - 180, kcY = 48;
        drawAngularPanel(g2, kcX, kcY, 170, 28);
        g2.setColor(C_DIM);
        g2.setFont(combatFont(Font.PLAIN, 10));
        g2.drawString("SLAIN", kcX + 10, kcY + 12);
        long now2 = System.currentTimeMillis();
        float killPulse = killCount > 0 ? 0.7f + 0.3f * (float)Math.sin(now2 * 0.005) : 1f;
        g2.setColor(new Color(220, 50, 50, (int)(killPulse * 220)));
        g2.setFont(combatFont(Font.BOLD, 15));
        g2.drawString(String.valueOf(killCount), kcX + 60, kcY + 20);
        // Skull icon
        g2.setColor(new Color(200, 160, 160, 180));
        g2.setFont(combatFont(Font.BOLD, 14));
        g2.drawString("☠", kcX + 130, kcY + 20);

        // ── Heal charges ─────────────────────────────────────────────────────
        int hcX = kcX - 120;
        drawAngularPanel(g2, hcX, kcY, 110, 28);
        g2.setColor(C_DIM);
        g2.setFont(combatFont(Font.PLAIN, 10));
        g2.drawString("HEALS", hcX + 10, kcY + 12);
        for (int i = 0; i < 2; i++) {
            g2.setColor(i < healChargesLeft ? new Color(100, 255, 100) : new Color(60, 60, 60));
            g2.fillOval(hcX + 60 + i * 20, kcY + 8, 12, 12);
        }

        // ── Boss Bar ─────────────────────────────────────────────────────────
        if (showBossBar) {
            int bbW = 400, bbH = 12;
            int bbX = screenW / 2 - bbW / 2, bbY = screenH - 30;
            g2.setColor(new Color(20, 10, 10, 200));
            g2.fillRoundRect(bbX, bbY, bbW, bbH, 4, 4);
            float bRatio = (float)bossHealth / bossMaxHealth;
            if (bRatio > 0) {
                g2.setColor(new Color(220, 40, 40));
                g2.fillRoundRect(bbX, bbY, (int)(bbW * bRatio), bbH, 4, 4);
            }
            g2.setColor(new Color(150, 40, 40));
            g2.drawRoundRect(bbX, bbY, bbW, bbH, 4, 4);
            g2.setFont(combatFont(Font.BOLD, 12));
            g2.setColor(Color.WHITE);
            String bName = "SHADOW COMMANDER";
            int nw = g2.getFontMetrics().stringWidth(bName);
            g2.drawString(bName, screenW / 2 - nw / 2, bbY - 6);
        }

        // ── Controls hint (bottom-right) ─────────────────────────────────────
        g2.setColor(new Color(180, 140, 130, 140));
        g2.setFont(combatFont(Font.PLAIN, 10));
        String ctrl = "A/D: Move   SPACE: Jump   SHIFT: Dash   J: Attack   ESC: Pause";
        int ctrlW = g2.getFontMetrics().stringWidth(ctrl);
        g2.drawString(ctrl, screenW - ctrlW - 10, screenH - 6);
    }

    // ── Ability Panel (bottom-left) ───────────────────────────────────────────
    private void drawAbilityPanel(Graphics2D g2, String phase,
            boolean isInvisible, int invisibleTimer, int invisibleMax,
            boolean isTimeWarp, int timeWarpTimer, int timeWarpMax,
            boolean isShadowClone, int shadowCloneTimer, int shadowCloneMax,
            int timeWarpCooldown, int timeWarpCooldownMax,
            int heals, int screenW, int screenH) {

        int panX = 12, panY = screenH - 80;
        int panW = 160, panH = 68;

        // Panel background
        drawAngularPanel(g2, panX, panY, panW, panH);

        // Determine ability state
        String abilityName;
        String icon;
        Color abilityColor;
        float activeRatio  = -1f; // -1 = not active
        float cooldownRatio = -1f;
        boolean canUse;

        switch (phase) {
            case "DAY" -> {
                abilityName = "Invisibility";
                icon        = "◈";
                abilityColor = new Color(220, 235, 255);
                canUse      = heals >= 2 && !isInvisible;
                if (isInvisible) activeRatio = (float) invisibleTimer / invisibleMax;
            }
            case "DUSK" -> {
                abilityName = "Time Warp";
                icon        = "◎";
                abilityColor = new Color(255, 180, 80);
                canUse      = timeWarpCooldown == 0 && !isTimeWarp;
                if (isTimeWarp) activeRatio = (float) timeWarpTimer / timeWarpMax;
                if (timeWarpCooldown > 0) cooldownRatio = 1f - (float) timeWarpCooldown / timeWarpCooldownMax;
            }
            default -> { // NIGHT
                abilityName = "Shadow Clone";
                icon        = "❖";
                abilityColor = new Color(180, 130, 255);
                canUse      = heals >= 1 && !isShadowClone;
                if (isShadowClone) activeRatio = (float) shadowCloneTimer / shadowCloneMax;
            }
        }

        long now = System.currentTimeMillis();

        // Icon circle
        int icX = panX + 14, icY = panY + 18, icR = 18;
        g2.setColor(new Color(abilityColor.getRed(), abilityColor.getGreen(),
                abilityColor.getBlue(), canUse ? 80 : 35));
        g2.fillOval(icX - icR / 2, icY - icR / 2, icR * 2, icR * 2);
        g2.setColor(abilityColor.darker());
        g2.drawOval(icX - icR / 2, icY - icR / 2, icR * 2, icR * 2);

        // Active timer arc (green progress ring)
        if (activeRatio >= 0) {
            g2.setColor(new Color(100, 255, 150, 200));
            g2.setStroke(new BasicStroke(3f));
            int arc = (int)(activeRatio * 360);
            g2.drawArc(icX - icR / 2 - 2, icY - icR / 2 - 2, icR * 2 + 4, icR * 2 + 4, 90, arc);
            g2.setStroke(new BasicStroke(1f));
        }

        // Cooldown arc (dark overlay sweep)
        if (cooldownRatio >= 0) {
            g2.setColor(new Color(50, 50, 50, 180));
            int arc = (int)((1f - cooldownRatio) * 360);
            g2.fillArc(icX - icR / 2, icY - icR / 2, icR * 2, icR * 2, 90, arc);
        }

        // Icon text
        float pulse = 0.7f + 0.3f * (float)Math.sin(now * 0.006);
        int iconAlpha = canUse ? (int)(180 + 75 * pulse) : 80;
        g2.setColor(new Color(abilityColor.getRed(), abilityColor.getGreen(),
                abilityColor.getBlue(), iconAlpha));
        g2.setFont(combatFont(Font.BOLD, 16));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(icon, icX - fm.stringWidth(icon) / 2, icY + fm.getAscent() / 3);

        // Ability name
        g2.setFont(combatFont(Font.BOLD, 11));
        g2.setColor(canUse ? abilityColor : C_DIM);
        g2.drawString(abilityName, panX + 46, panY + 20);

        // Status text
        g2.setFont(combatFont(Font.PLAIN, 10));
        if (activeRatio >= 0) {
            int secsLeft = (int)Math.ceil(activeRatio * (activeRatio == (float)invisibleTimer/invisibleMax ? 5
                    : activeRatio == (float)timeWarpTimer/timeWarpMax ? 4 : 5));
            g2.setColor(new Color(100, 255, 150, 200));
            g2.drawString("ACTIVE  " + secsLeft + "s", panX + 46, panY + 35);
        } else if (cooldownRatio >= 0) {
            int cdSecs = (int)Math.ceil((float) timeWarpCooldown / 60);
            g2.setColor(C_DIM);
            g2.drawString("COOLDOWN " + cdSecs + "s", panX + 46, panY + 35);
        } else {
            String cost = switch (phase) {
                case "DAY"  -> "Cost: 2 Heals";
                case "DUSK" -> "Free  (15s CD)";
                default     -> "Cost: 1 Heal";
            };
            g2.setColor(canUse ? new Color(abilityColor.getRed(), abilityColor.getGreen(),
                    abilityColor.getBlue(), 160) : C_DIM);
            g2.drawString(cost, panX + 46, panY + 35);
        }

        // [Q] key hint
        g2.setFont(combatFont(Font.BOLD, 10));
        g2.setColor(canUse ? new Color(255, 230, 100, 200) : C_DIM);
        g2.drawString("[Q] ACTIVATE", panX + 18, panY + 55);
    }

    // ── MENU ─────────────────────────────────────────────────────────────────
    void drawMenu(Graphics2D g2, int w, int h) {
        // Full dark overlay with vignette
        g2.setColor(new Color(0, 0, 0, 210));
        g2.fillRect(0, 0, w, h);

        // Vignette
        RadialGradientPaint vignette = new RadialGradientPaint(
                w / 2f, h / 2f, Math.max(w, h) * 0.7f,
                new float[] { 0f, 1f },
                new Color[] { new Color(0, 0, 0, 0), new Color(0, 0, 0, 180) });
        g2.setPaint(vignette);
        g2.fillRect(0, 0, w, h);

        // Title bar decoration
        g2.setColor(C_BORDER);
        g2.fillRect(0, h / 2 - 95, w, 3);
        g2.fillRect(0, h / 2 + 10, w, 3);

        // Title
        g2.setFont(combatFont(Font.BOLD, 64));
        String title = "CHRONOFORGE";
        int tw = g2.getFontMetrics().stringWidth(title);
        // Shadow
        g2.setColor(new Color(120, 20, 20, 160));
        g2.drawString(title, (w - tw) / 2 + 4, h / 2 - 28 + 4);
        // Gradient fill via clip trick
        g2.setColor(new Color(230, 60, 40));
        g2.drawString(title, (w - tw) / 2, h / 2 - 28);

        // Subtitle
        g2.setFont(combatFont(Font.PLAIN, 16));
        String sub = "A cursed warrior — survive the endless eclipse";
        int sw = g2.getFontMetrics().stringWidth(sub);
        g2.setColor(new Color(200, 150, 130));
        g2.drawString(sub, (w - sw) / 2, h / 2 + 5);

        // Pulsing ENTER prompt
        long now = System.currentTimeMillis();
        float pulse = 0.5f + 0.5f * (float) Math.sin(now * 0.004);
        g2.setColor(new Color(230, 80, 60, (int) (140 + pulse * 115)));
        g2.setFont(combatFont(Font.BOLD, 22));
        String enter = "▶  PRESS  ENTER  TO  FIGHT";
        int ew = g2.getFontMetrics().stringWidth(enter);
        g2.drawString(enter, (w - ew) / 2, h / 2 + 80);

        // Decorative corners
        drawCornerAccents(g2, w, h);
    }

    // ── PAUSE ────────────────────────────────────────────────────────────────
    void drawPause(Graphics2D g2, int w, int h) {
        g2.setColor(new Color(0, 0, 0, 170));
        g2.fillRect(0, 0, w, h);

        // Panel
        int bx = w / 2 - 160, by = h / 2 - 70;
        drawAngularPanel(g2, bx, by, 320, 140);

        // Header bar
        g2.setColor(C_BORDER);
        g2.fillRect(bx, by, 320, 3);

        g2.setFont(combatFont(Font.BOLD, 42));
        String paused = "PAUSED";
        int pw = g2.getFontMetrics().stringWidth(paused);
        g2.setColor(new Color(230, 80, 60));
        g2.drawString(paused, w / 2 - pw / 2, h / 2 - 10);

        g2.setFont(combatFont(Font.PLAIN, 16));
        String resume = "ESC — Resume Combat";
        int rw = g2.getFontMetrics().stringWidth(resume);
        g2.setColor(C_DIM);
        g2.drawString(resume, w / 2 - rw / 2, h / 2 + 30);
    }

    // ── GAME OVER ────────────────────────────────────────────────────────────
    void drawGameOver(Graphics2D g2, int w, int h) {
        // Blood-red vignette
        RadialGradientPaint rg = new RadialGradientPaint(
                w / 2f, h / 2f, Math.max(w, h) * 0.6f,
                new float[] { 0f, 1f },
                new Color[] { new Color(80, 0, 0, 80), new Color(0, 0, 0, 220) });
        g2.setPaint(rg);
        g2.fillRect(0, 0, w, h);

        g2.setColor(new Color(0, 0, 0, 120));
        g2.fillRect(0, 0, w, h);

        // Horizontal crimson lines
        g2.setColor(new Color(180, 20, 20, 160));
        g2.fillRect(0, h / 2 - 85, w, 2);
        g2.fillRect(0, h / 2 + 30, w, 2);

        g2.setFont(combatFont(Font.BOLD, 66));
        String go = "YOU DIED";
        int gw = g2.getFontMetrics().stringWidth(go);
        // Drop shadow
        g2.setColor(new Color(80, 0, 0, 200));
        g2.drawString(go, w / 2 - gw / 2 + 5, h / 2 - 20 + 5);
        g2.setColor(new Color(230, 30, 30));
        g2.drawString(go, w / 2 - gw / 2, h / 2 - 20);

        long now = System.currentTimeMillis();
        float pulse = 0.5f + 0.5f * (float) Math.sin(now * 0.005);
        g2.setColor(new Color(200, 80, 60, (int) (130 + pulse * 125)));
        g2.setFont(combatFont(Font.BOLD, 18));
        String retry = "▶  ENTER  TO  TRY  AGAIN";
        int rrw = g2.getFontMetrics().stringWidth(retry);
        g2.drawString(retry, w / 2 - rrw / 2, h / 2 + 55);

        drawCornerAccents(g2, w, h);
    }

    // ── WIN ──────────────────────────────────────────────────────────────────
    void drawWin(Graphics2D g2, int w, int h) {
        g2.setColor(new Color(0, 0, 0, 185));
        g2.fillRect(0, 0, w, h);

        // Golden glow centre
        RadialGradientPaint glow = new RadialGradientPaint(
                w / 2f, h / 2f, 260f,
                new float[] { 0f, 1f },
                new Color[] { new Color(255, 180, 0, 60), new Color(0, 0, 0, 0) });
        g2.setPaint(glow);
        g2.fillRect(0, 0, w, h);

        g2.setColor(new Color(220, 160, 0, 160));
        g2.fillRect(0, h / 2 - 85, w, 2);
        g2.fillRect(0, h / 2 + 30, w, 2);

        g2.setFont(combatFont(Font.BOLD, 54));
        String vic = "VICTORY";
        int vw = g2.getFontMetrics().stringWidth(vic);
        g2.setColor(new Color(100, 60, 0, 180));
        g2.drawString(vic, w / 2 - vw / 2 + 4, h / 2 - 20 + 4);
        g2.setColor(new Color(255, 200, 30));
        g2.drawString(vic, w / 2 - vw / 2, h / 2 - 20);

        long now = System.currentTimeMillis();
        float pulse = 0.5f + 0.5f * (float) Math.sin(now * 0.004);
        g2.setColor(new Color(255, 180, 60, (int) (130 + pulse * 125)));
        g2.setFont(combatFont(Font.BOLD, 18));
        String cont = "▶  ENTER  TO  CONTINUE";
        int cw = g2.getFontMetrics().stringWidth(cont);
        g2.drawString(cont, w / 2 - cw / 2, h / 2 + 55);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    /** Draws a combat-style angular dark panel with crimson border. */
    private void drawAngularPanel(Graphics2D g2, int x, int y, int w, int h) {
        // Shadow
        g2.setColor(new Color(0, 0, 0, 120));
        g2.fillRoundRect(x + 3, y + 4, w, h, 6, 6);
        // Background
        g2.setColor(C_BG);
        g2.fillRoundRect(x, y, w, h, 6, 6);
        // Border
        g2.setColor(C_BORDER);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(x, y, w, h, 6, 6);
        // Inner accent line (top)
        g2.setColor(C_BORDER2);
        g2.setStroke(new BasicStroke(1f));
        g2.drawLine(x + 10, y + 1, x + w - 10, y + 1);
        g2.setStroke(new BasicStroke(1f));
    }

    /** Corner accent cross-hairs drawn on all 4 corners of screen. */
    private void drawCornerAccents(Graphics2D g2, int w, int h) {
        g2.setColor(new Color(200, 40, 40, 180));
        g2.setStroke(new BasicStroke(2f));
        int s = 22, g = 4;
        // TL
        g2.drawLine(g, g, g + s, g);
        g2.drawLine(g, g, g, g + s);
        // TR
        g2.drawLine(w - g, g, w - g - s, g);
        g2.drawLine(w - g, g, w - g, g + s);
        // BL
        g2.drawLine(g, h - g, g + s, h - g);
        g2.drawLine(g, h - g, g, h - g - s);
        // BR
        g2.drawLine(w - g, h - g, w - g - s, h - g);
        g2.drawLine(w - g, h - g, w - g, h - g - s);
        g2.setStroke(new BasicStroke(1f));
    }

    private Color phaseCols(String phase) {
        return phase.equals("NIGHT") ? C_NIGHT : phase.equals("DUSK") ? C_DUSK : C_DAY;
    }

    private Font combatFont(int style, int size) {
        return new Font("Arial Narrow", style, size);
    }

    private void enableAA(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
}