import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.prefs.Preferences;

public class DistroInvaders extends JPanel implements ActionListener, KeyListener {
    
    private static final int LOGICAL_WIDTH = 800;
    private static final int LOGICAL_HEIGHT = 600;
    private static final int PLAYER_SIZE = 48;

    enum GameState { MENU, SELECT_DISTRO, PLAYING, HOW_TO, CREATORS }
    private GameState currentState = GameState.MENU;

    enum Language { TR, EN }
    private Language currentLang = Language.EN;

    private int menuSelection = 0;
    private int selectedPlayerIndex = 0;

    private boolean leftPressed = false;
    private boolean rightPressed = false;
    private boolean spacePressed = false;

    enum PlayerDistro {
        ARCH("Arch Linux", "arch.png", new Color(23, 147, 209), "Rolling Strafe: Hareketli Atış", "Rolling Strafe: Shoot While Moving", 100, 7, 1, false),
        DEBIAN("Debian", "debian.png", new Color(215, 7, 81), "Rock Solid: 2 Can (Yavaş Atış: 280ms)", "Rock Solid: 2 HP (Slower Fire: 280ms)", 280, 7, 2, false),
        FEDORA("Fedora", "fedora.png", new Color(60, 110, 180), "Cutting Edge: Çift Lazer (240ms)", "Cutting Edge: Dual Laser (240ms)", 240, 7, 1, true),
        GENTOO("Gentoo", "gentoo.png", new Color(53, 191, 92), "Source Compiled: +50% Hız (160ms)", "Source Compiled: +50% Speed (160ms)", 160, 11, 1, false);

        final String label;
        final String fileName;
        final Color color;
        final String perkTR;
        final String perkEN;
        final long shotCooldown;
        final int moveSpeed;
        final int maxHp;
        final boolean dualLaser;

        PlayerDistro(String label, String fileName, Color color, String perkTR, String perkEN, long shotCooldown, int moveSpeed, int maxHp, boolean dualLaser) {
            this.label = label;
            this.fileName = fileName;
            this.color = color;
            this.perkTR = perkTR;
            this.perkEN = perkEN;
            this.shotCooldown = shotCooldown;
            this.moveSpeed = moveSpeed;
            this.maxHp = maxHp;
            this.dualLaser = dualLaser;
        }
    }

    enum DistroType {
        UBUNTU("Ubuntu", new Color(233, 84, 32), "ubuntu.png"),
        FEDORA("Fedora", new Color(60, 110, 180), "fedora.png"),
        DEBIAN("Debian", new Color(215, 7, 81), "debian.png"),
        GENTOO("Gentoo", new Color(53, 191, 92), "gentoo.png"), 
        WINDOWS("Windows", new Color(0, 164, 239), "windows.png"),
        ARCH("Arch Linux", new Color(23, 147, 209), "arch.png");

        final String label;
        final Color color;
        final String fileName;

        DistroType(String label, Color color, String fileName) {
            this.label = label;
            this.color = color;
            this.fileName = fileName;
        }
    }

    static class Entity {
        double x, y;
        int width, height;

        Entity(double x, double y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
        }

        Rectangle getBounds() {
            return new Rectangle((int) x, (int) y, width, height);
        }
    }

    static class Enemy extends Entity {
        DistroType type;
        double speed;

        Enemy(double x, double y, DistroType type, double speed) {
            super(x, y, 46, 46);
            this.type = type;
            this.speed = speed;
        }

        @Override
        Rectangle getBounds() {
            return new Rectangle((int) x + 5, (int) y + 5, width - 10, height - 10);
        }
    }

    static class Star {
        int x, y, speed, size;
        Star(int x, int y, int speed, int size) {
            this.x = x; this.y = y; this.speed = speed; this.size = size;
        }
    }

    private int playerX = 376;
    private final int playerY = 500;
    private int playerHP = 1;
    private PlayerDistro activePlayerDistro = PlayerDistro.ARCH;
    
    private int score = 0;
    private int highScore = 0;
    private Preferences prefs;

    private int purgedCount = 0;
    private boolean gameOver = false;
    private long lastShotTime = 0;
    private long gameStartTime = 0;
    private long elapsedTimeSeconds = 0;

    private final Timer gameLoop;
    private final Random random = new Random();
    private final Map<String, BufferedImage> sprites = new HashMap<>();
    private BufferedImage crtOverlay;
    private Clip bgmClip;

    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Entity> lasers = new ArrayList<>();
    private final List<Star> stars = new ArrayList<>();

    public DistroInvaders() {
        setBackground(new Color(8, 10, 15));
        setFocusable(true);
        addKeyListener(this);

        prefs = Preferences.userNodeForPackage(DistroInvaders.class);
        highScore = prefs.getInt("highscore", 0);

        loadSprites();
        initStars();
        initCRTOverlay();
        playMusic("bgm.wav");

        gameLoop = new Timer(16, this);
        gameLoop.start();
    }

    private String getText(String tr, String en) {
        return currentLang == Language.TR ? tr : en;
    }

    private String[] getMenuOptions() {
        if (currentLang == Language.TR) {
            return new String[] {
                "[ KOK_ERISIMI ] - OYUNU BASLAT",
                "[ MAN_SAYFASI ] - NASIL OYNANIR",
                "[ SIS_YONETICILERI ] - GELISTIRICILER",
                "[ KAPANIS ] - SISTEMDEN CIKIS"
            };
        } else {
            return new String[] {
                "[ ROOT_ACCESS ] - INITIALIZE PLAY",
                "[ MAN_PAGE ] - HOW TO PLAY",
                "[ SYS_ADMINS ] - CREATORS",
                "[ SHUTDOWN ] - EXIT SYSTEM"
            };
        }
    }

    private void playMusic(String filename) {
        try {
            File file = new File(filename);
            if (!file.exists()) return;
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(file);
            bgmClip = AudioSystem.getClip();
            bgmClip.open(audioStream);

            if (bgmClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl volumeControl = (FloatControl) bgmClip.getControl(FloatControl.Type.MASTER_GAIN);
                volumeControl.setValue(-8.0f);
            }
            bgmClip.loop(Clip.LOOP_CONTINUOUSLY);
            bgmClip.start();
        } catch (Exception ignored) {}
    }

    private void loadSprites() {
        for (PlayerDistro pd : PlayerDistro.values()) {
            loadTexture(pd.name().toLowerCase(), pd.fileName);
        }
        for (DistroType dt : DistroType.values()) {
            loadTexture(dt.name().toLowerCase(), dt.fileName);
        }
    }

    private void loadTexture(String key, String filename) {
        try {
            File file = new File(filename);
            if (file.exists()) sprites.put(key, ImageIO.read(file));
        } catch (IOException ignored) {}
    }

    private void initStars() {
        for (int i = 0; i < 90; i++) {
            stars.add(new Star(random.nextInt(LOGICAL_WIDTH), random.nextInt(LOGICAL_HEIGHT), random.nextInt(4) + 1, random.nextInt(2) + 1));
        }
    }

    private void initCRTOverlay() {
        crtOverlay = new BufferedImage(LOGICAL_WIDTH, LOGICAL_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = crtOverlay.createGraphics();
        
        g2.setColor(new Color(0, 0, 0, 45));
        for (int i = 0; i < LOGICAL_HEIGHT; i += 3) g2.drawLine(0, i, LOGICAL_WIDTH, i);
        
        RadialGradientPaint vignette = new RadialGradientPaint(
            new Point(LOGICAL_WIDTH / 2, LOGICAL_HEIGHT / 2),
            LOGICAL_WIDTH - 50,
            new float[] {0.0f, 1.0f},
            new Color[] {new Color(0, 0, 0, 0), new Color(0, 0, 0, 190)}
        );
        g2.setPaint(vignette);
        g2.fillRect(0, 0, LOGICAL_WIDTH, LOGICAL_HEIGHT);
        g2.dispose();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double scaleX = (double) getWidth() / LOGICAL_WIDTH;
        double scaleY = (double) getHeight() / LOGICAL_HEIGHT;
        g2.scale(scaleX, scaleY);

        g2.setColor(new Color(255, 255, 255, 120));
        for (Star s : stars) {
            g2.fillRect(s.x, s.y, s.size, s.size);
        }

        if (currentState == GameState.MENU) {
            drawMenu(g2);
        } else if (currentState == GameState.SELECT_DISTRO) {
            drawSelectDistro(g2);
        } else if (currentState == GameState.HOW_TO) {
            drawHowTo(g2);
        } else if (currentState == GameState.CREATORS) {
            drawCreators(g2);
        } else if (currentState == GameState.PLAYING) {
            drawGame(g2);
        }

        if (crtOverlay != null) {
            g2.drawImage(crtOverlay, 0, 0, null);
        }
        
        Toolkit.getDefaultToolkit().sync();
    }

    private void drawMenu(Graphics2D g2) {
        g2.setFont(new Font("Monospaced", Font.BOLD, 52));
        g2.setColor(new Color(0, 255, 200));
        String title = "DISTRO INVADERS";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(title, (LOGICAL_WIDTH - fm.stringWidth(title)) / 2, 180);

        g2.setFont(new Font("Monospaced", Font.BOLD, 20));
        String[] menuOptions = getMenuOptions();
        for (int i = 0; i < menuOptions.length; i++) {
            if (i == menuSelection) {
                g2.setColor(new Color(255, 60, 60));
                g2.drawString("> " + menuOptions[i] + " <", 200, 300 + (i * 45));
            } else {
                g2.setColor(new Color(0, 255, 128));
                g2.drawString("  " + menuOptions[i], 200, 300 + (i * 45));
            }
        }

        g2.setFont(new Font("Monospaced", Font.BOLD, 16));
        g2.setColor(new Color(255, 215, 0)); 
        String hsText = getText("EN YUKSEK SKOR: ", "HIGH SCORE: ") + highScore;
        g2.drawString(hsText, (LOGICAL_WIDTH - g2.getFontMetrics().stringWidth(hsText)) / 2, 240);

        g2.setFont(new Font("Monospaced", Font.PLAIN, 14));
        g2.setColor(new Color(180, 180, 180));
        String langHint = getText("[L] Dili Degistir (TR/EN)", "[L] Change Language (TR/EN)");
        g2.drawString(langHint, (LOGICAL_WIDTH - g2.getFontMetrics().stringWidth(langHint)) / 2, 520);
    }

    private void drawSelectDistro(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 200));
        g2.fillRect(0, 0, LOGICAL_WIDTH, LOGICAL_HEIGHT);

        g2.setFont(new Font("Monospaced", Font.BOLD, 36));
        g2.setColor(new Color(0, 255, 200));
        String title = getText("DAGITIM SECIMI", "DISTRO SELECTION");
        g2.drawString(title, (LOGICAL_WIDTH - g2.getFontMetrics().stringWidth(title)) / 2, 120);

        PlayerDistro selected = PlayerDistro.values()[selectedPlayerIndex];

        g2.setColor(selected.color);
        g2.drawRect(200, 180, 400, 260);
        g2.fillRect(200, 180, 400, 35);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 20));
        g2.drawString("<  " + selected.label.toUpperCase() + "  >", (LOGICAL_WIDTH - g2.getFontMetrics().stringWidth("<  " + selected.label.toUpperCase() + "  >")) / 2, 205);

        BufferedImage img = sprites.get(selected.name().toLowerCase());
        if (img != null) {
            g2.drawImage(img, 360, 235, 80, 80, null);
        } else {
            drawFallbackArch(g2, 376, 250);
        }

        g2.setFont(new Font("Monospaced", Font.BOLD, 13));
        g2.setColor(new Color(0, 255, 128));
        String perkStr = getText("OZELLIK: ", "PERK: ") + (currentLang == Language.TR ? selected.perkTR : selected.perkEN);
        g2.drawString(perkStr, (LOGICAL_WIDTH - g2.getFontMetrics().stringWidth(perkStr)) / 2, 360);

        g2.setColor(new Color(200, 200, 200));
        g2.setFont(new Font("Monospaced", Font.PLAIN, 14));
        g2.drawString(getText("SOL/SAG Tuşları ile Değiştir", "Use LEFT/RIGHT Keys to Select"), (LOGICAL_WIDTH - g2.getFontMetrics().stringWidth(getText("SOL/SAG Tuşları ile Değiştir", "Use LEFT/RIGHT Keys to Select"))) / 2, 400);

        g2.setColor(new Color(255, 60, 60));
        g2.setFont(new Font("Monospaced", Font.BOLD, 18));
        String confirmStr = getText("[ ENTER / SPACE ] - SISTEMI BASLAT", "[ ENTER / SPACE ] - INITIALIZE SYSTEM");
        g2.drawString(confirmStr, (LOGICAL_WIDTH - g2.getFontMetrics().stringWidth(confirmStr)) / 2, 490);
    }

    private void drawHowTo(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRect(0, 0, LOGICAL_WIDTH, LOGICAL_HEIGHT);

        g2.setFont(new Font("Monospaced", Font.BOLD, 36));
        g2.setColor(new Color(0, 255, 200));
        g2.drawString(getText("MAN_PAGE: KONTROLLER VE AMAC", "MAN_PAGE: CONTROLS AND OBJECTIVE"), 80, 120);

        g2.setFont(new Font("Monospaced", Font.PLAIN, 18));
        g2.setColor(new Color(0, 255, 128));
        g2.drawString(getText(">> YON TUSLARI (SOL/SAG) : Secilen Distro'yu hareket ettir.", 
                              ">> ARROW KEYS (LEFT/RIGHT) : Move selected distro."), 80, 210);
        g2.drawString(getText(">> BOSLUK (SPACE)        : Terminal komutu (Lazer) atesle.", 
                              ">> SPACEBAR                : Fire terminal command (Laser)."), 80, 260);
        g2.drawString(getText(">> SISTEMIN AMACI        : Gelen diger distrolari yok et.", 
                              ">> SYSTEM OBJECTIVE        : Destroy incoming distros."), 80, 310);

        g2.setColor(new Color(180, 180, 180));
        g2.drawString(getText("[ ESC ] -> GERI DON", "[ ESC ] -> GO BACK"), 80, 500);
    }

    private void drawCreators(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRect(0, 0, LOGICAL_WIDTH, LOGICAL_HEIGHT);

        g2.setFont(new Font("Monospaced", Font.BOLD, 36));
        g2.setColor(new Color(0, 255, 200));
        g2.drawString(getText("SYS_ADMINS: GELISTIRICILER", "SYS_ADMINS: DEVELOPERS"), 80, 120);

        g2.setFont(new Font("Monospaced", Font.PLAIN, 20));
        g2.setColor(new Color(0, 255, 128));
        g2.drawString(getText(">> Lider Gelistirici & Mimar : Siratilotty", 
                              ">> Lead Developer & Architect: SirAtilotty"), 80, 220);
        
        g2.setColor(new Color(180, 180, 180));
        g2.drawString(getText("[ ESC ] -> GERI DON", "[ ESC ] -> GO BACK"), 80, 500);
    }

    private void drawGame(Graphics2D g2) {
        if (!gameOver) {
            g2.setColor(new Color(0, 255, 200));
            for (Entity laser : lasers) {
                g2.fillRect((int) laser.x, (int) laser.y, laser.width, laser.height);
            }

            BufferedImage pImg = sprites.get(activePlayerDistro.name().toLowerCase());
            if (pImg != null) g2.drawImage(pImg, playerX, playerY, PLAYER_SIZE, PLAYER_SIZE, null);
            else drawFallbackArch(g2, playerX, playerY);

            for (Enemy e : enemies) {
                BufferedImage sprite = sprites.get(e.type.name().toLowerCase());
                if (sprite != null) g2.drawImage(sprite, (int) e.x, (int) e.y, e.width, e.height, null);
                else drawFallbackDistro(g2, e);
            }

            g2.setFont(new Font("Monospaced", Font.BOLD, 14));
            g2.setColor(new Color(0, 255, 128));
            
            String hudFormat = getText("SKOR: %05d | REKOR: %05d | CAN: %d | TEMIZLENEN: %03d | SURE: %02ds", 
                                       "SCORE: %05d | HIGH: %05d | HP: %d | PURGED: %03d | TIME: %02ds");
            g2.drawString(String.format(hudFormat, score, highScore, playerHP, purgedCount, elapsedTimeSeconds), 10, 25);

        } else {
            g2.setColor(new Color(0, 0, 0, 220));
            g2.fillRect(0, 0, LOGICAL_WIDTH, LOGICAL_HEIGHT);
            
            g2.setFont(new Font("Monospaced", Font.BOLD, 22));
            g2.setColor(new Color(255, 60, 60));
            g2.drawString(getText("[!] KERNEL PANIC: SISTEM COKTU", "[!] KERNEL PANIC: SYSTEM OVERWHELMED"), 100, 220);
            
            g2.setColor(new Color(0, 255, 128));
            g2.setFont(new Font("Monospaced", Font.PLAIN, 16));
            g2.drawString(getText("Secilen Dagitim            : ", "Selected Distro            : ") + activePlayerDistro.label, 100, 270);
            g2.drawString(getText("Final Skoru                : ", "Final Score                : ") + score, 100, 295);
            
            g2.setColor(new Color(255, 215, 0));
            g2.drawString(getText("En Yuksek Skor (Rekor)     : ", "High Score                 : ") + Math.max(score, highScore), 100, 320);
            
            if (score > 0 && score >= highScore) {
                g2.setFont(new Font("Monospaced", Font.BOLD, 18));
                g2.drawString(getText("*** YENI REKOR! ***", "*** NEW HIGH SCORE! ***"), 100, 350);
                g2.setFont(new Font("Monospaced", Font.PLAIN, 16));
            }

            g2.setColor(new Color(0, 255, 128));
            g2.drawString(getText("Temizlenen Distro Sayisi   : ", "Unsupported Distros Purged : ") + purgedCount, 100, 380);
            g2.drawString(getText("Calisma Suresi (Uptime)    : ", "Uptime                     : ") + elapsedTimeSeconds + "s", 100, 405);
            
            g2.setColor(new Color(180, 180, 180));
            g2.drawString(getText("> Hard Reboot Icin 'R'ye Bas", "> Press 'R' to Hard Reboot System"), 100, 470);
            g2.drawString(getText("> Ana Menu Icin 'ESC'ye Bas", "> Press 'ESC' to Main Menu"), 100, 500);
        }
    }

    private void drawFallbackArch(Graphics2D g2, int x, int y) {
        int[] px = {x + 24, x + 48, x};
        int[] py = {y, y + 48, y + 48};
        g2.setColor(new Color(23, 147, 209));
        g2.fillPolygon(px, py, 3);
    }

    private void drawFallbackDistro(Graphics2D g2, Enemy e) {
        g2.setColor(e.type.color);
        g2.fillRect((int) e.x, (int) e.y, e.width, e.height);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        for (Star s : stars) {
            s.y += s.speed;
            if (s.y > LOGICAL_HEIGHT) {
                s.y = 0;
                s.x = random.nextInt(LOGICAL_WIDTH);
            }
        }

        if (currentState == GameState.PLAYING && !gameOver) {
            boolean isMoving = false;
            if (leftPressed && playerX > 5) {
                playerX -= activePlayerDistro.moveSpeed;
                isMoving = true;
            }
            if (rightPressed && playerX < LOGICAL_WIDTH - PLAYER_SIZE - 5) {
                playerX += activePlayerDistro.moveSpeed;
                isMoving = true;
            }

            boolean canShoot = true;
            long currentCooldown = activePlayerDistro.shotCooldown;
            
            if (isMoving) {
                if (activePlayerDistro == PlayerDistro.ARCH) {
                    currentCooldown = 60;
                } else {
                    canShoot = false;
                }
            }

            if (spacePressed && canShoot) {
                long now = System.currentTimeMillis();
                if (now - lastShotTime >= currentCooldown) {
                    if (activePlayerDistro.dualLaser) {
                        lasers.add(new Entity(playerX + 8, playerY - 10, 4, 20));
                        lasers.add(new Entity(playerX + PLAYER_SIZE - 12, playerY - 10, 4, 20));
                    } else {
                        lasers.add(new Entity(playerX + PLAYER_SIZE / 2.0 - 2, playerY - 10, 4, 20));
                    }
                    lastShotTime = now;
                }
            }

            elapsedTimeSeconds = (System.currentTimeMillis() - gameStartTime) / 1000;
            
            double speedMultiplier = Math.min(2.0, 1.0 + (elapsedTimeSeconds * 0.02)); 
            
            double baseSpeed = 1.2 + random.nextDouble() * 1.0; 
            if (random.nextInt(100) < 2) {
                baseSpeed = 5.5 + random.nextDouble() * 2.5;
            }
            
            double currentEnemySpeed = baseSpeed * speedMultiplier;

            int spawnRate = Math.min(42, 6 + (int)(elapsedTimeSeconds / 3));
            if (random.nextInt(100) < spawnRate) {
                DistroType[] types = DistroType.values();
                DistroType type;
                
                do {
                    type = types[random.nextInt(types.length)];
                } while (type.name().equals(activePlayerDistro.name()));
                
                enemies.add(new Enemy(random.nextInt(LOGICAL_WIDTH - 50), -50, type, currentEnemySpeed));
            }

            for (int i = lasers.size() - 1; i >= 0; i--) {
                Entity l = lasers.get(i);
                l.y -= 16;
                if (l.y < -20) lasers.remove(i);
            }

            Rectangle playerBox = new Rectangle(playerX + 5, playerY + 5, PLAYER_SIZE - 10, PLAYER_SIZE - 10);

            for (int i = enemies.size() - 1; i >= 0; i--) {
                Enemy enemy = enemies.get(i);
                enemy.y += enemy.speed;
                Rectangle enemyBox = enemy.getBounds();
                boolean hit = false;
                for (int j = lasers.size() - 1; j >= 0; j--) {
                    if (enemyBox.intersects(lasers.get(j).getBounds())) {
                        lasers.remove(j);
                        hit = true;
                        break;
                    }
                }

                if (hit) {
                    enemies.remove(i);
                    score += 25;
                    purgedCount++;
                    continue;
                }

                if (enemy.y > LOGICAL_HEIGHT) {
                    enemies.remove(i);
                    score += 5;
                } else if (enemyBox.intersects(playerBox)) {
                    enemies.remove(i);
                    playerHP--;
                    if (playerHP <= 0) {
                        triggerGameOver();
                    }
                }
            }
        }

        repaint();
    }

    private void triggerGameOver() {
        gameOver = true;
        if (score > highScore) {
            highScore = score;
            prefs.putInt("highscore", highScore);
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();

        if (currentState == GameState.MENU) {
            if (key == KeyEvent.VK_UP) {
                menuSelection--;
                if (menuSelection < 0) menuSelection = getMenuOptions().length - 1;
            } else if (key == KeyEvent.VK_DOWN) {
                menuSelection++;
                if (menuSelection >= getMenuOptions().length) menuSelection = 0;
            } else if (key == KeyEvent.VK_L) {
                currentLang = (currentLang == Language.TR) ? Language.EN : Language.TR;
            } else if (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE) {
                if (menuSelection == 0) currentState = GameState.SELECT_DISTRO;
                else if (menuSelection == 1) currentState = GameState.HOW_TO;
                else if (menuSelection == 2) currentState = GameState.CREATORS;
                else if (menuSelection == 3) System.exit(0);
            }
        } else if (currentState == GameState.SELECT_DISTRO) {
            if (key == KeyEvent.VK_LEFT) {
                selectedPlayerIndex--;
                if (selectedPlayerIndex < 0) selectedPlayerIndex = PlayerDistro.values().length - 1;
            } else if (key == KeyEvent.VK_RIGHT) {
                selectedPlayerIndex++;
                if (selectedPlayerIndex >= PlayerDistro.values().length) selectedPlayerIndex = 0;
            } else if (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE) {
                activePlayerDistro = PlayerDistro.values()[selectedPlayerIndex];
                resetGame();
            } else if (key == KeyEvent.VK_ESCAPE) {
                currentState = GameState.MENU;
            }
        } else if (currentState == GameState.HOW_TO || currentState == GameState.CREATORS) {
            if (key == KeyEvent.VK_ESCAPE) {
                currentState = GameState.MENU;
            }
        } else if (currentState == GameState.PLAYING) {
            if (key == KeyEvent.VK_LEFT) leftPressed = true;
            if (key == KeyEvent.VK_RIGHT) rightPressed = true;
            if (key == KeyEvent.VK_SPACE) spacePressed = true;

            if (key == KeyEvent.VK_ESCAPE) {
                if (gameOver) currentState = GameState.MENU;
                else {
                    triggerGameOver();
                    System.exit(0);
                }
            }
            if (gameOver && key == KeyEvent.VK_R) {
                resetGame();
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_LEFT) leftPressed = false;
        if (key == KeyEvent.VK_RIGHT) rightPressed = false;
        if (key == KeyEvent.VK_SPACE) spacePressed = false;
    }

    private void resetGame() {
        playerX = 376;
        score = 0;
        purgedCount = 0;
        playerHP = activePlayerDistro.maxHp;
        enemies.clear();
        lasers.clear();
        leftPressed = false;
        rightPressed = false;
        spacePressed = false;
        gameOver = false;
        gameStartTime = System.currentTimeMillis();
        elapsedTimeSeconds = 0;
        currentState = GameState.PLAYING;
    }

    @Override public void keyTyped(KeyEvent e) {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Distro Invaders");
            frame.setUndecorated(true); 
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            DistroInvaders game = new DistroInvaders();
            frame.add(game);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);
        });
    }
}