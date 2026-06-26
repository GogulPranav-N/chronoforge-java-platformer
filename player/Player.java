package player;

import java.awt.*;
import java.util.ArrayList;

public class Player {

    // Position & physics
    public int x, y;
    public int velX, velY;
    public boolean facingRight = true;
    public boolean onGround = false;

    // Jump
    public int jumpCount = 0;
    public boolean jumpWasPressed = false;

    // Dash
    public boolean canDash = true;
    public boolean isDashing = false;
    public int dashTimer = 0;
    public int dashCooldown = 0;
    public boolean dashWasPressed = false;
    final int DASH_FRAMES = 14;
    final int DASH_SPEED = 24;
    final int DASH_COOLDOWN_MAX = 35;

    // Wall jump
    public boolean touchingWallLeft = false;
    public boolean touchingWallRight = false;
    public int wallJumpLock = 0;

    // Effects
    public ArrayList<int[]> trail = new ArrayList<>();
    public ArrayList<int[]> particles = new ArrayList<>();

    // Health
    public int maxHealth = 5;
    public int health = 5;
    public int invincibleTimer = 0;
        // Animation
    public String animState = "idle";
    public int animFrame = 0;
    public int animTick = 0;
    public String prevAnimState = "";
    public boolean isMoving = false;

    public Player(int startX, int startY) {
        this.x = startX;
        this.y = startY;
    }

    public void update(KeyHandler key, int speed, String phase,
                       Rectangle[] platforms, int wallThickness,
                       int screenWidth, int screenHeight) {

        // Invincibility frames
        if (invincibleTimer > 0) invincibleTimer--;

        // Dash cooldown
        if (dashCooldown > 0) dashCooldown--;

        // Trigger dash — SHIFT + direction, night only
        boolean dashNow = key.dashPressed && !dashWasPressed;
        if (dashNow && canDash && !isDashing && dashCooldown == 0
                && phase.equals("NIGHT")
                && (key.leftPressed || key.rightPressed)) {
            isDashing = true;
            dashTimer = DASH_FRAMES;
            dashCooldown = DASH_COOLDOWN_MAX;
            canDash = false;
            velY = 0;
            spawnDashParticles();
        }
        dashWasPressed = key.dashPressed;

        // Dashing movement
        if (isDashing) {
            dashTimer--;
            x += facingRight ? DASH_SPEED : -DASH_SPEED;
            trail.add(new int[]{x, y, 180});
            if (dashTimer <= 0) isDashing = false;
        }

        // Fade trail
        trail.removeIf(t -> { t[2] -= 25; return t[2] <= 0; });

        // Horizontal movement
                // Horizontal movement
        isMoving = false;

        if (!isDashing) {
            if (wallJumpLock > 0) {
                wallJumpLock--;
            } else {
                if (key.leftPressed)  {
                    x -= speed;
                    facingRight = false;
                    isMoving = true;
                }
                if (key.rightPressed) {
                    x += speed;
                    facingRight = true;
                    isMoving = true;
                }
            }
        }

        // Gravity — floatier feel
        if (!isDashing) {
            velY += key.downPressed ? 4 : 1;
            if (velY > 15) velY = 15; // lower terminal velocity
        }
        y += velY;

        // Platform collision
        onGround = false;
        Rectangle pr = getRect();
        for (Rectangle p : platforms) {
            if (pr.intersects(p)) {
                if (velY >= 0 && y + 50 <= p.y + p.height + velY + 2) {
                    y = p.y - 50;
                    velY = 0;
                    onGround = true;
                    jumpCount = 0;
                    canDash = true;
                } else if (velY < 0) {
                    y = p.y + p.height;
                    velY = 2;
                }
            }
        }

        // Wall detection
        touchingWallLeft  = (x <= wallThickness + 2);
        touchingWallRight = (x + 35 >= screenWidth - wallThickness - 2);
        boolean onWall = (touchingWallLeft || touchingWallRight) && !onGround;

        // Wall slide — slow fall when pressing into wall
        if (onWall) {
            boolean pressingWall = (touchingWallLeft  && key.leftPressed)
                                 || (touchingWallRight && key.rightPressed);
            if (pressingWall && velY > 4) velY = 4;
        }

        // Clamp to walls
        if (x < wallThickness)                   x = wallThickness;
        if (x + 35 > screenWidth - wallThickness) x = screenWidth - wallThickness - 35;

        // Jump
        boolean jumpNow = key.jumpPressed && !jumpWasPressed;
        if (jumpNow) {
            if (onWall && !onGround) {
                // Wall jump — stronger bounce
                velY = -18;
                int bounceX = touchingWallRight ? -15 : 15;
                velX = bounceX;
                wallJumpLock = 14;
                jumpCount = 1;
                spawnJumpParticles();
            } else if (jumpCount < 2) {
                // Normal + double jump — both stronger
                velY = (jumpCount == 0) ? -20 : -17;
                jumpCount++;
                if (jumpCount == 2) spawnJumpParticles();
            }
        }
        jumpWasPressed = key.jumpPressed;

        // Wall jump momentum — decays slower for better control
        if (velX != 0) {
            velX = (int)(velX * 0.88f);
            x += velX;
        }

        // Fell off screen — take damage and respawn
        if (y > screenHeight + 50) {
            takeDamage();
            respawn();
        }

        // Update particles
        particles.removeIf(p -> {
            p[4]--; p[0] += p[2]; p[1] += p[3]; p[3]++;
            return p[4] <= 0;
        });
        updateAnimationState();
    }

    void updateAnimationState() {

        if (isDashing) {
            animState = "dash";
        }
        else if (!onGround) {
            if (velY < 0) {
                animState = "jump";
            } else {
                animState = "fall";
            }
        }
        else if (touchingWallLeft || touchingWallRight) {
            animState = "wall";
        }
        else if (isMoving) {
            animState = "run";
        }
        else if (velX != 0 || Math.abs(velX) > 0 || false) {
            animState = "run";
        }
        else {
            animState = "idle";
        }

        // Reset frame if animation changed
        if (!animState.equals(prevAnimState)) {
            animFrame = 0;
            animTick = 0;
            prevAnimState = animState;
        }

        // Advance frame every few ticks
        animTick++;
        if (animTick >= 8) {
            animTick = 0;
            animFrame++;

            int maxFrames = switch (animState) {
                case "run"  -> 6;
                case "idle" -> 4;
                case "jump" -> 1;
                case "fall" -> 1;
                case "dash" -> 2;
                case "wall" -> 1;
                default -> 1;
            };

            if (animFrame >= maxFrames) {
                animFrame = 0;
            }
        }
    }

    
    public void takeDamage() {
        if (invincibleTimer > 0) return;
        health--;
        invincibleTimer = 90;
    }

    public void respawn() {
        x = 100; y = 380;
        velX = 0; velY = 0;
        jumpCount = 0; canDash = true;
    }

    public boolean isDead() { return health <= 0; }

    public Rectangle getRect() { return new Rectangle(x, y, 35, 50); }

    void spawnJumpParticles() {
        for (int i = 0; i < 8; i++)
            particles.add(new int[]{x+17, y+50,
                (int)(Math.random()*10-5),
                (int)(Math.random()*-5-1),
                15, 200, 200, 255});
    }

    void spawnDashParticles() {
        for (int i = 0; i < 12; i++)
            particles.add(new int[]{x+17, y+25,
                facingRight ? (int)(Math.random()*-8-2) : (int)(Math.random()*8+2),
                (int)(Math.random()*6-3),
                20, 180, 180, 255});
    }

    public void draw(Graphics2D g2, String phase) {
        // Trail
        for (int[] t : trail) {
            g2.setColor(new Color(180, 180, 255, t[2]));
            g2.fillRect(t[0], t[1], 35, 50);
        }

        // Particles
        for (int[] p : particles) {
            g2.setColor(new Color(p[5], p[6], p[7], Math.min(255, p[4]*15)));
            g2.fillOval(p[0]-3, p[1]-3, 7, 7);
        }

        // Flicker when invincible
        if (invincibleTimer > 0 && (invincibleTimer % 8 < 4)) return;

        // Cape
        g2.setColor(phase.equals("NIGHT") ? new Color(160,160,255) : new Color(80,80,80));
        int[] cx = {x+(facingRight?0:35), x+(facingRight?-14:49), x+(facingRight?5:30)};
        int[] cy = {y+12, y+48, y+50};
        g2.fillPolygon(cx, cy, 3);

        // Body
        Color body = phase.equals("NIGHT") ? Color.WHITE :
                     phase.equals("DUSK")  ? new Color(190,150,90) : new Color(130,130,130);
        g2.setColor(body);
        g2.fillRoundRect(x, y, 35, 50, 6, 6);

        // Hood
        g2.setColor(phase.equals("NIGHT") ? new Color(220,220,255) : new Color(110,110,110));
        g2.fillOval(x+5, y-10, 25, 22);

        // Eye glow
        g2.setColor(phase.equals("NIGHT") ? new Color(200,220,255) : Color.GRAY);
        g2.fillOval(x+(facingRight?18:6), y-4, 7, 5);
        g2.setColor(Color.YELLOW);
        g2.setFont(new Font("Arial", Font.BOLD, 12));
        // g2.drawString(animState + " [" + animFrame + "]", x - 5, y - 18);
    }
}