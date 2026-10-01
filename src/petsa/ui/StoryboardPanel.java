package petsa.ui;

import petsa.model.DailyLedger.RentBill;
import petsa.model.GameState;
import petsa.model.Player;
import petsa.ui.PixelKit.FadePane;
import petsa.ui.PixelKit.WoodButton;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class StoryboardPanel extends JPanel {

    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    private static final Rectangle PICTURE = new Rectangle(339, 42, 603, 339);
    private static final int TEXT_X = 290;
    private static final int TEXT_W = 700;
    private static final int TICK_MS = 30;
    private static final int PICTURE_FADE_MS = 900;
    private static final int PARAGRAPH_DELAY_MS = 1100;

    private static final Color CREAM = new Color(0xF0, 0xE6, 0xD2);
    private static final Color SOFT = new Color(0xC8, 0xBC, 0xA8);
    private static final Color DOT_OFF = new Color(0x4A, 0x40, 0x36);

    private static final class Scene {
        final Image picture;
        final String[] paragraphs;

        Scene(String imageName, String... paragraphs) {
            this.picture = findImage(imageName);
            this.paragraphs = paragraphs;
        }
    }

    private final List<Scene> scenes;
    private final String lastButtonLabel;
    private final Color lastButtonColor;
    private final Runnable onDone;
    private final WoodButton nextButton;
    private final WoodButton skipButton;
    private final Timer timer;
    private int scene = 0;
    private long sceneStart = System.nanoTime();
    private boolean skipped = false;
    private boolean done = false;

    public static StoryboardPanel opening(Runnable onDone) {
        String allowance = PixelKit.peso(Player.STARTING_CASH);
        String rent = PixelKit.peso(GameState.RENT_AMOUNT);
        String fare = PixelKit.peso(GameState.TRANSPORT_AMOUNT);
        List<Scene> scenes = new ArrayList<>();
        scenes.add(new Scene("opening_scene_1",
                "Leaving home.",
                "You passed the entrance exam to a university in the big city. Before dawn, your parents hand you "
                        + allowance + " - all the money you'll get for the whole month.",
                "\"Budget it well, anak. Pay your bills well, and keep enough for the way home.\""));
        scenes.add(new Scene("opening_scene_2",
                "Welcome to the city.",
                "Hours later, the bus rolls into a busy street. Jeepneys honk, vendors shout, and everything "
                        + "costs more than it did back home.",
                "Food, school, surprise expenses, " + rent + " rent on Day 29 and a " + fare
                        + " fare home on Day 30. From here on, every peso counts."));
        scenes.add(new Scene("opening_scene_3",
                "Finding a place.",
                "Near campus, two apartments have a room for rent. One is plain and cheap to run; the other is "
                        + "quieter and more comfortable, but its electricity bill is much higher.",
                "Choose carefully as you'll be living with this choice for the next 30 days."));
        return new StoryboardPanel(scenes, "CHOOSE APARTMENT >", WoodButton.GREEN, 330, true, onDone);
    }

    public static StoryboardPanel ending(int finalSavings, RentBill bill, Runnable onFinish) {
        String rent = PixelKit.peso(GameState.RENT_AMOUNT);
        String bills = bill != null ? " (" + PixelKit.peso(bill.getTotal()) + " with the bills)" : "";
        String fare = PixelKit.peso(GameState.TRANSPORT_AMOUNT);
        List<Scene> scenes = new ArrayList<>();
        scenes.add(new Scene("ending_scene",
                "Day 30: Finally going home.",
                "Having settled the " + rent + " rent on Day 29" + bills + " and paid the " + fare
                        + " fare for the bus ride back to your hometown, your survival challenge is over.",
                finalSavings > 0
                        ? "You survived the 30-day college student budget simulation with "
                                + PixelKit.pesoCents(finalSavings) + " to spare."
                        : "You survived the 30-day college student budget simulation - down to your very last peso!"));
        return new StoryboardPanel(scenes, "FINISH >", WoodButton.TAN, 210, false, onFinish);
    }

    private StoryboardPanel(List<Scene> scenes, String lastButtonLabel, Color lastButtonColor, int buttonWidth,
            boolean skippable, Runnable onDone) {
        this.scenes = scenes;
        this.lastButtonLabel = lastButtonLabel;
        this.lastButtonColor = lastButtonColor;
        this.onDone = onDone;
        setLayout(null);
        setOpaque(true);
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(WIDTH, HEIGHT));

        boolean single = scenes.size() == 1;
        nextButton = new WoodButton(single ? lastButtonLabel : "NEXT >", null,
                single ? lastButtonColor : WoodButton.TAN);
        nextButton.setBounds((WIDTH - buttonWidth) / 2, 630, buttonWidth, 51);
        nextButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        nextButton.setVisible(false);
        nextButton.addActionListener(e -> nextScene());
        add(nextButton);

        if (skippable) {
            skipButton = new WoodButton("SKIP >>", null, WoodButton.TAN);
            skipButton.setBounds(WIDTH - 180, 639, 150, 42);
            skipButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            skipButton.addActionListener(e -> finish());
            add(skipButton);
        } else {
            skipButton = null;
        }

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                skipped = true;
                repaint();
            }
        });

        timer = new Timer(TICK_MS, e -> {
            repaint();
            if (paragraphsShown() >= scenes.get(scene).paragraphs.length) {
                nextButton.setVisible(true);
            }
        });
        timer.start();
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    private boolean onLastScene() {
        return scene == scenes.size() - 1;
    }

    private void nextScene() {
        if (onLastScene()) {
            finish();
            return;
        }
        FadePane.run(this, () -> {
            scene++;
            sceneStart = System.nanoTime();
            skipped = false;
            nextButton.setVisible(false);
            if (onLastScene()) {
                nextButton.setTitleAndColor(lastButtonLabel, lastButtonColor);
                if (skipButton != null) {
                    skipButton.setVisible(false);
                }
            }
            repaint();
        });
    }

    private void finish() {
        if (done) {
            return;
        }
        done = true;
        nextButton.setEnabled(false);
        if (skipButton != null) {
            skipButton.setEnabled(false);
        }
        timer.stop();
        onDone.run();
    }

    private long elapsedMs() {
        return (System.nanoTime() - sceneStart) / 1_000_000L;
    }

    private float pictureAlpha() {
        return skipped ? 1f : Math.min(1f, elapsedMs() / (float) PICTURE_FADE_MS);
    }

    private int paragraphsShown() {
        int total = scenes.get(scene).paragraphs.length;
        if (skipped) {
            return total;
        }
        long afterPicture = elapsedMs() - PICTURE_FADE_MS;
        return afterPicture < 0 ? 0 : (int) Math.min(total, afterPicture / PARAGRAPH_DELAY_MS + 1);
    }

    private static Image findImage(String name) {
        for (String ext : new String[] {"png", "jpeg", "jpg"}) {
            Image image = PixelKit.loadImage("resources/" + name + "." + ext);
            if (image != null && image.getWidth(null) > 0) {
                return image;
            }
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        Scene current = scenes.get(scene);

        Graphics2D picture = (Graphics2D) g2.create();
        picture.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pictureAlpha()));
        PixelKit.paint(picture, 0, 0, WIDTH, HEIGHT, this::paintFrame);
        if (current.picture != null) {
            picture.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            picture.drawImage(current.picture, PICTURE.x, PICTURE.y, PICTURE.width, PICTURE.height, null);
        }
        picture.dispose();

        int y = 432;
        if (scenes.size() > 1) {
            int dotsX = WIDTH / 2 - (scenes.size() * 21 - 9) / 2;
            for (int i = 0; i < scenes.size(); i++) {
                g2.setColor(i == scene ? CREAM : DOT_OFF);
                g2.fillRect(dotsX + i * 21, 399, 12, 12);
            }
            y = 450;
        }

        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int shown = paragraphsShown();
        for (int i = 0; i < shown; i++) {
            Font font = PixelKit.font(i == 0 ? 16f : 10f);
            int lineH = i == 0 ? 24 : 18;
            for (String line : PixelKit.wrapText(current.paragraphs[i], font, TEXT_W)) {
                g2.setColor(i == 0 ? CREAM : SOFT);
                PixelKit.drawText(g2, line, font, TEXT_X, y);
                y += lineH;
            }
            y += 14;
        }
        g2.dispose();
    }

    private void paintFrame(Graphics2D pg) {
        int b = 12;
        pg.setColor(new Color(0x22, 0x1C, 0x18));
        pg.fillRoundRect(PICTURE.x - b, PICTURE.y - b, PICTURE.width + b * 2, PICTURE.height + b * 2, 30, 30);
        pg.setColor(new Color(0x3A, 0x30, 0x28));
        pg.fillRoundRect(PICTURE.x - 6, PICTURE.y - 6, PICTURE.width + 12, PICTURE.height + 12, 21, 21);
    }
}
