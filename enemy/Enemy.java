package enemy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

public class Enemy {

    // Position
    public int x;
    public int y;

    // Size
    public int width = 32;
    public int height = 46;

    // Movement
    public int speed = 2;
    public int direction = 1; // 1 = Right, -1 = Left

    // Patrol limits
    public int leftLimit;
    public int rightLimit;

    public Enemy(int x, int y, int leftLimit, int rightLimit) {
        this.x = x;
        this.y = y;
        this.leftLimit = leftLimit;
        this.rightLimit = rightLimit;
    }

    // Enemy movement
    public void update() {

        x += speed * direction;

        if (x <= leftLimit) {
            x = leftLimit;
            direction = 1;
        }

        if (x + width >= rightLimit) {
            x = rightLimit - width;
            direction = -1;
        }
    }

    // Collision box
    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }

    // Draw enemy
    public void draw(Graphics2D g2) {

        
        // Body
        g2.setColor(new Color(40, 40, 55));
        g2.fillRoundRect(x, y, width, height, 8, 8);

        // Hood
        g2.setColor(new Color(70, 70, 95));
        g2.fillOval(x + 5, y - 8, 22, 20);

        // Eyes
        g2.setColor(new Color(180, 120, 255));

        if (direction == 1) {
            g2.fillOval(x + 16, y + 1, 5, 4);
        } else {
            g2.fillOval(x + 10, y + 1, 5, 4);
        }
    }
}