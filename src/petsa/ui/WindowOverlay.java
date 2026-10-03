package petsa.ui;

import petsa.model.Timeline.TimeSlot;
import petsa.ui.PixelKit.WoodButton;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.util.Random;

public class WindowOverlay extends JPanel {

    public enum View { MORNING, MIDDAY, NIGHT, RAINY }

    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    private static final Rectangle FRAME = new Rectangle(345, 36, 591, 465);
    private static final Rectangle GLASS = new Rectangle(369, 60, 543, 417);
    private static final Rectangle NOTE = new Rectangle(345, 519, 591, 105);
    private static final Rectangle BACK_BUTTON = new Rectangle(540, 639, 198, 48);
    private static final int RAIN_TICK_MS = 70;
    private static final int CLOUD_TICK_MS = 150;
    private static final int LIGHTS_TICK_MS = 250;
    private static final int CLOUD_WIDTH = 159;

    private static final Color VEIL = new Color(0, 0, 0, 150);
    private static final Color WOOD = new Color(0x8C, 0x6A, 0x48);
    private static final Color WOOD_DARK = new Color(0x5A, 0x40, 0x28);
    private static final Color PAPER = new Color(0xF6, 0xF4, 0xEE);
    private static final Color INK = new Color(0x2E, 0x24, 0x1C);

    private final Runnable onClose;
    private final Timer animationTimer;
    private View view = View.MORNING;
    private int rainOffset = 0;
    private int cloudOffset = 0;
    private long viewShownAt;

    public WindowOverlay(Runnable onClose) {
        this.onClose = onClose;
        setLayout(null);
        setOpaque(false);
        setSize(WIDTH, HEIGHT);
        MouseAdapter swallowEverything = new MouseAdapter() { };
        addMouseListener(swallowEverything);
        addMouseMotionListener(swallowEverything);
        addMouseWheelListener(swallowEverything);

        WoodButton back = new WoodButton("< BACK", null, WoodButton.TAN);
        back.setBounds(BACK_BUTTON);
        back.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        back.addActionListener(e -> onClose.run());
        add(back);

        animationTimer = new Timer(RAIN_TICK_MS, e -> {
            if (view == View.RAINY) {
                rainOffset = (rainOffset + 12) % 120;
            } else if (view != View.NIGHT) {
                cloudOffset = (cloudOffset + PixelKit.SCALE) % (GLASS.width + CLOUD_WIDTH);
            }
            repaint(GLASS);
        });
    }

    public static View viewFor(TimeSlot slot, boolean rainy) {
        if (slot == TimeSlot.NIGHT) {
            return View.NIGHT;
        }
        if (rainy) {
            return View.RAINY;
        }
        return slot == TimeSlot.AFTERNOON ? View.MIDDAY : View.MORNING;
    }

    public void showView(View newView) {
        view = newView;
        viewShownAt = System.currentTimeMillis();
        switch (view) {
            case RAINY:
                animationTimer.setDelay(RAIN_TICK_MS);
                break;
            case NIGHT:
                animationTimer.setDelay(LIGHTS_TICK_MS);
                break;
            default:
                animationTimer.setDelay(CLOUD_TICK_MS);
                break;
        }
        animationTimer.restart();
        repaint();
    }

    public void stopAnimation() {
        animationTimer.stop();
    }

    @Override
    public void removeNotify() {
        animationTimer.stop();
        super.removeNotify();
    }

    private static String titleFor(View view) {
        switch (view) {
            case MIDDAY:
                return "MID-DAY - SUNNY";
            case NIGHT:
                return "NIGHT";
            case RAINY:
                return "RAINY DAY";
            default:
                return "MORNING - SUNNY";
        }
    }

    private static String descriptionFor(View view) {
        switch (view) {
            case MIDDAY:
                return "The sun is high and the sky is clear. A fine day for a walk if Stress is building up.";
            case NIGHT:
                return "The city lights are on. Do tonight's task, then sleep to end the day.";
            case RAINY:
                return "It's pouring! Going out today (walks, hangouts, work) means getting sick, and even "
                        + "staying in you might catch a cold. Showering means the laundromat today.";
            default:
                return "A clear, bright morning over the city. No rain today.";
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(VEIL);
        g2.fillRect(0, 0, getWidth(), getHeight());

        Image custom = PixelKit.loadImage(customImagePath(view, "png"));
        if (custom == null) {
            custom = PixelKit.loadImage(customImagePath(view, "jpeg"));
        }
        final Image customView = custom;
        PixelKit.paint(g2, 0, 0, getWidth(), getHeight(), pg -> {
            paintFrameShadow(pg);
            if (customView == null) {
                paintScene(pg);
            }
        });
        if (customView != null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.drawImage(customView, GLASS.x, GLASS.y, GLASS.width, GLASS.height, null);
        }
        PixelKit.paint(g2, 0, 0, getWidth(), getHeight(), this::paintFrameAndNote);
        paintNoteText(g2);
        g2.dispose();
    }

    private static String customImagePath(View view, String extension) {
        return "resources/window_view_" + view.name().toLowerCase() + "." + extension;
    }

    private void paintFrameShadow(Graphics2D pg) {
        pg.setColor(new Color(0, 0, 0, 110));
        pg.fillRect(FRAME.x + 12, FRAME.y + 12, FRAME.width, FRAME.height);
        pg.fillRect(NOTE.x + 9, NOTE.y + 9, NOTE.width, NOTE.height);
    }

    private void paintScene(Graphics2D pg) {
        Rectangle g = GLASS;
        Color skyTop;
        Color skyBottom;
        switch (view) {
            case MIDDAY:
                skyTop = new Color(0xE8, 0x9A, 0x48);
                skyBottom = new Color(0xFA, 0xE2, 0x9C);
                break;
            case NIGHT:
                skyTop = new Color(0x0C, 0x12, 0x2C);
                skyBottom = new Color(0x2A, 0x32, 0x5E);
                break;
            case RAINY:
                skyTop = new Color(0x5E, 0x68, 0x74);
                skyBottom = new Color(0x92, 0x9C, 0xA6);
                break;
            default:
                skyTop = new Color(0x86, 0xBE, 0xE6);
                skyBottom = new Color(0xF6, 0xD2, 0xA0);
                break;
        }
        Shape oldClip = pg.getClip();
        pg.clipRect(g.x, g.y, g.width, g.height);
        pg.setPaint(new GradientPaint(0, g.y, skyTop, 0, g.y + g.height, skyBottom));
        pg.fillRect(g.x, g.y, g.width, g.height);

        Random seeded = new Random(7);
        switch (view) {
            case NIGHT:
                pg.setColor(new Color(0xF2, 0xEE, 0xD0));
                for (int i = 0; i < 40; i++) {
                    pg.fillRect(g.x + seeded.nextInt(g.width / 3) * 3, g.y + seeded.nextInt(g.height / 6) * 3, 3, 3);
                }
                pg.fillOval(g.x + g.width - 165, g.y + 45, 66, 66);
                pg.setColor(skyTop);
                pg.fillOval(g.x + g.width - 147, g.y + 36, 66, 66);
                break;
            case MIDDAY:
                pg.setColor(new Color(0xFF, 0xF4, 0xC8));
                pg.fillOval(g.x + g.width - 165, g.y + 36, 75, 75);
                paintCloud(pg, driftingX(60), g.y + 75, new Color(0xFC, 0xE6, 0xBC));
                paintCloud(pg, driftingX(270), g.y + 135, new Color(0xFC, 0xE6, 0xBC));
                break;
            case RAINY:
                paintCloud(pg, g.x + 30, g.y + 30, new Color(0x6E, 0x76, 0x80));
                paintCloud(pg, g.x + 210, g.y + 15, new Color(0x5A, 0x62, 0x6C));
                paintCloud(pg, g.x + 360, g.y + 45, new Color(0x66, 0x6E, 0x78));
                break;
            default:
                pg.setColor(new Color(0xF4, 0xA8, 0x48));
                pg.fillOval(g.x + 90, g.y + g.height - 195, 90, 90);
                paintCloud(pg, driftingX(300), g.y + 60, new Color(0xFF, 0xF2, 0xE2));
                paintCloud(pg, driftingX(-30), g.y + 150, new Color(0xFF, 0xF2, 0xE2));
                break;
        }

        paintSkyline(pg, seeded);

        if (view == View.RAINY) {
            pg.setColor(new Color(0xD8, 0xE4, 0xF0, 170));
            Random drops = new Random(11);
            for (int i = 0; i < 90; i++) {
                int x = g.x + drops.nextInt(g.width / 3) * 3;
                int y = g.y + ((drops.nextInt(g.height) + rainOffset * 3) % g.height);
                pg.fillRect(x, y, 3, 15);
            }
        }

        pg.setColor(new Color(255, 255, 255, view == View.NIGHT ? 20 : 45));
        for (int i = 0; i < 6; i++) {
            pg.fillRect(g.x + 60 + i * 9, g.y + 30 + i * 18, 24, 18);
        }
        pg.setClip(oldClip);
    }

    private int driftingX(int startX) {
        int track = GLASS.width + CLOUD_WIDTH;
        int x = ((startX + CLOUD_WIDTH + cloudOffset) % track + track) % track;
        return GLASS.x - CLOUD_WIDTH + x;
    }

    private void paintCloud(Graphics2D pg, int x, int y, Color color) {
        pg.setColor(color);
        pg.fillOval(x, y + 15, 90, 45);
        pg.fillOval(x + 36, y, 81, 60);
        pg.fillOval(x + 87, y + 18, 72, 42);
    }

    private void paintSkyline(Graphics2D pg, Random seeded) {
        Rectangle g = GLASS;
        Color building;
        switch (view) {
            case NIGHT:
                building = new Color(0x16, 0x1A, 0x2C);
                break;
            case RAINY:
                building = new Color(0x52, 0x5A, 0x64);
                break;
            case MIDDAY:
                building = new Color(0x7A, 0x52, 0x3A);
                break;
            default:
                building = new Color(0x4A, 0x5C, 0x70);
                break;
        }
        Random blinking = new Random(13);
        long elapsed = System.currentTimeMillis() - viewShownAt;
        int x = g.x;
        while (x < g.x + g.width) {
            int w = 45 + seeded.nextInt(5) * 12;
            int h = 90 + seeded.nextInt(7) * 21;
            int top = g.y + g.height - h;
            pg.setColor(building);
            pg.fillRect(x, top, w, h);
            if (view == View.NIGHT) {
                for (int wy = top + 12; wy < g.y + g.height - 12; wy += 21) {
                    for (int wx = x + 9; wx < x + w - 9; wx += 15) {
                        if (seeded.nextInt(3) == 0 && isLightOn(blinking, elapsed)) {
                            pg.setColor(new Color(0xF2, 0xD0, 0x6A));
                            pg.fillRect(wx, wy, 6, 9);
                        }
                    }
                }
            }
            x += w + 3;
        }
    }

    private static boolean isLightOn(Random blinking, long elapsed) {
        boolean blinks = blinking.nextInt(4) != 0;
        int interval = 3000 + blinking.nextInt(1001);
        int start = blinking.nextInt(interval * 2);
        return !blinks || ((elapsed + start) / interval) % 2 == 0;
    }

    private void paintFrameAndNote(Graphics2D pg) {
        int px = PixelKit.SCALE;
        pg.setColor(WOOD_DARK);
        pg.fillRect(FRAME.x, FRAME.y, FRAME.width, GLASS.y - FRAME.y);
        pg.fillRect(FRAME.x, GLASS.y + GLASS.height, FRAME.width, FRAME.y + FRAME.height - GLASS.y - GLASS.height);
        pg.fillRect(FRAME.x, FRAME.y, GLASS.x - FRAME.x, FRAME.height);
        pg.fillRect(GLASS.x + GLASS.width, FRAME.y, FRAME.x + FRAME.width - GLASS.x - GLASS.width, FRAME.height);
        pg.setColor(WOOD);
        int inset = px * 2;
        pg.fillRect(FRAME.x + inset, FRAME.y + inset, FRAME.width - inset * 2, GLASS.y - FRAME.y - inset * 2);
        pg.fillRect(FRAME.x + inset, GLASS.y + GLASS.height + inset, FRAME.width - inset * 2,
                FRAME.y + FRAME.height - GLASS.y - GLASS.height - inset * 2);
        pg.fillRect(FRAME.x + inset, FRAME.y + inset, GLASS.x - FRAME.x - inset * 2, FRAME.height - inset * 2);
        pg.fillRect(GLASS.x + GLASS.width + inset, FRAME.y + inset,
                FRAME.x + FRAME.width - GLASS.x - GLASS.width - inset * 2, FRAME.height - inset * 2);
        int dividerX = GLASS.x + GLASS.width / 2 - 6;
        pg.setColor(WOOD_DARK);
        pg.fillRect(dividerX, GLASS.y, 12, GLASS.height);
        pg.setColor(WOOD);
        pg.fillRect(dividerX + px, GLASS.y, 12 - px * 2, GLASS.height);

        pg.setColor(PAPER);
        pg.fillRect(NOTE.x, NOTE.y, NOTE.width, NOTE.height);
        pg.setColor(new Color(0xC9, 0xB8, 0x8E));
        pg.fillRect(NOTE.x, NOTE.y + NOTE.height - px, NOTE.width, px);
        BoardOverlay.paintPin(pg, NOTE.x + NOTE.width / 2 - 6, NOTE.y - 3, new Color(0xB0, 0x3A, 0x2E));
    }

    private void paintNoteText(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        Font titleFont = PixelKit.font(16f);
        FontMetrics fm = g2.getFontMetrics(titleFont);
        String title = titleFor(view);
        g2.setFont(titleFont);
        g2.setColor(INK);
        g2.drawString(title, NOTE.x + (NOTE.width - fm.stringWidth(title)) / 2, NOTE.y + 32);

        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font bodyFont = PixelKit.font(9f);
        int y = NOTE.y + 58;
        for (String line : PixelKit.wrapText(descriptionFor(view), bodyFont, NOTE.width - 48)) {
            g2.setColor(INK);
            PixelKit.drawText(g2, line, bodyFont, NOTE.x + (NOTE.width - PixelKit.textWidth(line, bodyFont)) / 2, y);
            y += 15;
        }
    }
}
