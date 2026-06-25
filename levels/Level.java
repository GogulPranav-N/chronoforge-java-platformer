package levels;

import java.awt.Rectangle;

public class Level {

    public Rectangle[] platforms;
    public int portalX, portalY;
    public String name;

    public Level(String name, Rectangle[] platforms, int portalX, int portalY) {
        this.name = name;
        this.platforms = platforms;
        this.portalX = portalX;
        this.portalY = portalY;
    }

    // Factory — add more levels here later
    public static Level getLevel(int num) {
        return switch (num) {
            case 1 -> new Level("The Awakening",
                new Rectangle[]{
                    new Rectangle(0,   470, 180, 18),
                    new Rectangle(230, 470, 340, 18),
                    new Rectangle(630, 470, 170, 18),
                    new Rectangle(100, 360, 110, 15),
                    new Rectangle(310, 290, 120, 15),
                    new Rectangle(500, 220, 100, 15),
                    new Rectangle(150, 190, 100, 15),
                    new Rectangle(560, 130, 130, 15),
                }, 630, 60);
            default -> getLevel(1);
        };
    }
}