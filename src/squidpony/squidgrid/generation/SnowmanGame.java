package squidpony.squidgrid.generation;

import java.awt.Font;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import squidpony.squidcolor.SColor;
import squidpony.squidgrid.fov.FOVTranslator;
import squidpony.squidgrid.fov.ShadowFOV;
import squidpony.squidgrid.gui.awt.TextCellFactory;
import squidpony.squidgrid.gui.awt.event.SGKeyListener;
import squidpony.squidgrid.gui.swing.SwingPane;
import squidpony.squidgrid.util.Direction;

/**
 * This class starts up the game.
 *
 * @author Eben Howard
 */
public class SnowmanGame {

    private static final int width = 50, height = 30, statWidth = 12, fontSize = 22, outputLines = 1;
    private static final int minimumRoomSize = 3;
    private static final char[] CHARS_USED = new char[]{'☃', '☺', '▒', '.', 'X', 'y'};
    private final FOVTranslator fov = new FOVTranslator(new ShadowFOV());
    private final Random rng = new squidpony.squidmath.RNG();
    private JFrame frame;
    private JPanel panel;
    private SwingPane mapPanel, statsPanel, outputPanel;
    private SGKeyListener keyListener;
    private Monster player;
    private int playerStrength = 7;
    private ArrayList<Monster> monsters = new ArrayList<>();
    private ArrayList<Treasure> treasuresFound = new ArrayList<>();
    private Tile[][] map;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        new SnowmanGame().go();
    }

    /**
     * Starts the game.
     */
    private void go() {
        initializeFrame();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        player = new Monster(Monster.PLAYER);

        createMap();
        updateMap();
        updateStats();
        printOut("Welcome to Final Lab");

        runTurn();
    }

    /**
     * This is the main game loop method that takes input and process the
     * results. Right now it doesn't loop!
     */
    private void runTurn() {
        int key = keyListener.next().getExtendedKeyCode();
        boolean success = false;
        if (key == KeyEvent.VK_RIGHT) {
            success = tryToMove(Direction.RIGHT);
        }

        //update all end of turn items
        if (success) {
            updateMap();
            moveAllMonsters();
            updateMap();
            player.causeDamage(1);//health drains each turn!
            updateStats();
        }
    }

    /**
     * Attempts to move in the given direction. If a monster is in that
     * direction then the player attacks the monster.
     *
     * Returns false if there was a wall in the direction and so no action was
     * taken.
     *
     * @param dir
     * @return
     */
    private boolean tryToMove(Direction dir) {
        Tile tile = map[player.x + dir.deltaX][player.y + dir.deltaY];
        if (tile.isWall()) {
            return false;
        }

        Monster monster = tile.getMonster();
        if (monster == null) {//move the player
            map[player.x][player.y].setMonster(null);
            mapPanel.slide(new Point(player.x, player.y), dir);
            mapPanel.waitForAnimations();
            player.x += dir.deltaX;
            player.y += dir.deltaY;
            map[player.x][player.y].setMonster(player);
            return true;
        } else {//attack!
            mapPanel.bump(new Point(player.x, player.y), dir);
            mapPanel.waitForAnimations();
            boolean dead = monster.causeDamage(playerStrength);
            if (dead) {
                monsters.remove(monster);
                map[player.x + dir.deltaX][player.y + dir.deltaY].setMonster(null);//no more monster
                printOut("Killed the " + monster.getName());
            }
            return true;
        }

    }

    /**
     * Updates the map display to show the current view
     */
    private void updateMap() {
        doFOV();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
//                map[x][y].setSeen(true);//uncomment this to see the fully generated map rather than the player's view
                mapPanel.placeCharacter(x, y, map[x][y].getSymbol(), map[x][y].getColor());
            }
        }

        mapPanel.refresh();
    }

    /**
     * Updates the stats display to show current values
     */
    private void updateStats() {
        int y = 0;
        String info = "STATS";
        statsPanel.placeHorizontalString((statWidth - info.length()) / 2, y, info);

        y += 2;
        info = "Health " + player.getHealth();
        statsPanel.placeHorizontalString((statWidth - info.length()) / 2, y, info);
        statsPanel.refresh();
    }

    /**
     * Sets the output panel to show the message.
     *
     * @param message
     */
    private void printOut(String message) {
        outputPanel.placeHorizontalString(0, 0, message);
        outputPanel.refresh();
    }

    /**
     * Calculates the Field of View and marks the maps spots seen appropriately.
     */
    private void doFOV() {
        boolean[][] walls = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                walls[x][y] = map[x][y].isWall();
            }
        }
        fov.calculateFOV(walls, player.x, player.y, width + height);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                map[x][y].setSeen(fov.isLit(x, y));
            }
        }
    }

    /**
     * Creates the map to contain random bits of wall
     */
    private void createMap() {
        map = new Tile[width][height];

        //make all the edges into walls
        for (int x = 0; x < width; x++) {
            map[x][0] = new Tile(true);
            map[x][height - 1] = new Tile(true);
        }
        for (int y = 0; y < height; y++) {
            map[0][y] = new Tile(true);
            map[width - 1][y] = new Tile(true);
        }

        //fill the rest in with floor
        for (int x = 1; x < width - 1; x++) {
            for (int y = 1; y < height - 1; y++) {
                map[x][y] = new Tile();
            }
        }

        //randomly place some chunks of wall
        placeWallChunk();
        placeWallChunk();
        placeWallChunk();
        placeWallChunk();
        placeWallChunk();
        placeWallChunk();
        placeWallChunk();
        placeWallChunk();
        placeWallChunk();

        //randomly place the player
        placeMonster(player);

        //randomly place some monsters
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));
        placeMonster(new Monster(Monster.SNOWMAN));

        placeTreasure(new Treasure("Chocolate Coin", 1));
        placeTreasure(new Treasure("Chocolate Coin", 1));
        placeTreasure(new Treasure("Chocolate Coin", 1));
        placeTreasure(new Treasure("Chocolate Coin", 1));
        placeTreasure(new Treasure("Chocolate Coin", 1));
        placeTreasure(new Treasure("Chocolate Coin", 1));
        placeTreasure(new Treasure("Chocolate Coin", 1));
        placeTreasure(new Treasure("Chocolate Coin", 1));
        placeTreasure(new Treasure("Chocolate Coin", 1));
        placeTreasure(new Treasure("Chocolate Coin", 1));
        placeTreasure(new Treasure("Chocolate Coin", 1));
        placeTreasure(new Treasure("Coal", 0));
        placeTreasure(new Treasure("Coal", 0));
        placeTreasure(new Treasure("Coal", 0));
        placeTreasure(new Treasure("Coal", 0));

    }

    /**
     * Randomly places a group of walls in the map. This replaces whatever was
     * in that location previously.
     */
    private void placeWallChunk() {
        int spread = 5;
        int centerX = rng.nextInt(width);
        int centerY = rng.nextInt(height);

        for (int placeX = centerX - spread; placeX < centerX + spread; placeX++) {
            for (int placeY = centerY - spread; placeY < centerY + spread; placeY++) {
                if (rng.nextDouble() < 0.2 && placeX > 0 && placeX < width - 1 && placeY > 0 && placeY < height - 1) {
                    map[placeX][placeY] = new Tile(true);
                }
            }
        }
    }

    /**
     * Places the provided monster into an open tile space.
     *
     * @param monster
     */
    private void placeMonster(Monster monster) {
        int x = rng.nextInt(width - 2) + 1;
        int y = rng.nextInt(height - 2) + 1;
        if (map[x][y].isWall() || map[x][y].getMonster() != null) {
            placeMonster(monster);//try again recursively
        } else {
            map[x][y].setMonster(monster);
            monster.x = x;
            monster.y = y;

            if (!monster.equals(Monster.PLAYER)) {
                monsters.add(monster);
            }
        }
    }

    /**
     * Places the provided monster into an open tile space.
     *
     * @param treasure
     */
    private void placeTreasure(Treasure treasure) {
        int x = rng.nextInt(width - 2) + 1;
        int y = rng.nextInt(height - 2) + 1;
        if (map[x][y].isWall() || map[x][y].getTreasure() != null) {
            placeTreasure(treasure);//try again recursively
        } else {
            map[x][y].setTreasure(treasure);
        }
    }

    /**
     * Moves the monster given if possible. Monsters will not move into walls,
     * other monsters, or the player.
     *
     * @param monster
     */
    private void moveMonster(Monster monster) {
        Direction dir = Direction.CARDINALS[rng.nextInt(Direction.CARDINALS.length)];//get a random direction
        Tile tile = map[monster.x + dir.deltaX][monster.y + dir.deltaY];
        if (!tile.isWall() && tile.getMonster() == null) {
            map[monster.x][monster.y].setMonster(null);
            if (tile.isSeen()) {//only show animation if within sight
                mapPanel.slide(new Point(monster.x, monster.y), dir);
                mapPanel.waitForAnimations();
            }
            monster.x += dir.deltaX;
            monster.y += dir.deltaY;
            map[monster.x][monster.y].setMonster(monster);
        } else if (tile.isSeen()) {//only show animation if within sight
            mapPanel.bump(new Point(monster.x, monster.y), dir);
            mapPanel.waitForAnimations();
        }
    }

    /**
     * Moves all the monsters, one at a time.
     */
    private void moveAllMonsters() {
        for (int i = 0; i < monsters.size(); i++) {
            moveMonster(monsters.get(i));
        }
    }

    /**
     * Sets up the frame for display and keyboard input.
     */
    private void initializeFrame() {
        frame = new JFrame("Final Lab - The Game");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        try {
            frame.setIconImage(ImageIO.read(new File("./icon.png")));
        } catch (IOException ex) {
            //don't do anything if it failed, the default Java icon will be used
        }

        Font font = new Font("Lucidia", Font.PLAIN, fontSize);

        keyListener = new SGKeyListener(true, SGKeyListener.CaptureType.DOWN);
        frame.addKeyListener(keyListener);

        panel = new JPanel();

        mapPanel = new SwingPane(width, height, font);

        TextCellFactory textFactory = mapPanel.getTextCellFactory();
        textFactory.setAntialias(true);
        textFactory.setFitCharacters(CHARS_USED);
        textFactory.initializeBySize(mapPanel.getCellWidth(), mapPanel.getCellHeight(), font);
        mapPanel.placeHorizontalString(width / 2 - 4, height / 2, "Loading");
        mapPanel.refresh();
        panel.add(mapPanel);

        statsPanel = new SwingPane(mapPanel.getCellWidth(), mapPanel.getCellHeight(), statWidth, mapPanel.getGridHeight(), font);
        statsPanel.setDefaultBackground(SColor.DARK_GRAY);
        statsPanel.setDefaultForeground(SColor.RUST);
        statsPanel.refresh();
        panel.add(statsPanel);

        outputPanel = new SwingPane(mapPanel.getGridWidth() + statsPanel.getGridWidth(), outputLines, font);
        outputPanel.setDefaultBackground(SColor.ALICE_BLUE);
        outputPanel.setDefaultForeground(SColor.BURNT_BAMBOO);
        outputPanel.refresh();
        panel.add(outputPanel);

        frame.add(panel);
        frame.pack();
    }

}
