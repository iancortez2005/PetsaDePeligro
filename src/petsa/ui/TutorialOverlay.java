package petsa.ui;

import petsa.ui.PixelKit.WoodButton;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.geom.Area;
import java.util.List;

public class TutorialOverlay extends JPanel {

    public enum BoxPlacement { CENTER, BOTTOM, BOTTOM_LEFT }

    public static class Page {
        final String title;
        final BoxPlacement placement;
        final Rectangle[] highlights;
        final String[] lines;

        public Page(String title, BoxPlacement placement, Rectangle[] highlights, String... lines) {
            this.title = title;
            this.placement = placement;
            this.highlights = highlights;
            this.lines = lines;
        }
    }

    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    private static final int LINE_HEIGHT = 24;
    private static final int PADDING = 30;
    private static final int BUTTON_HEIGHT = 42;
    private static final int HIGHLIGHT_MARGIN = 6;

    private static final Color VEIL = new Color(0, 0, 0, 165);
    private static final Color GOLD = new Color(0xE8, 0xC2, 0x4A);
    private static final Color BOX_EDGE = new Color(0x1A, 0x12, 0x0C);
    private static final Color BOX_TRIM = new Color(0xB8, 0x94, 0x5A);
    private static final Color BOX_FILL = new Color(0x3A, 0x2A, 0x1E);
    private static final Color TEXT = new Color(0xF4, 0xEA, 0xD6);
    private static final Color TEXT_DIM = new Color(0xB8, 0xA8, 0x8C);

    private final List<Page> pages;
    private final Runnable onFinished;
    private int index;
    private Rectangle box = new Rectangle();

    public TutorialOverlay(List<Page> pages, Runnable onFinished) {
        this.pages = pages;
        this.onFinished = onFinished;
        setLayout(null);
        setOpaque(false);
        setSize(WIDTH, HEIGHT);

        MouseAdapter swallowEverything = new MouseAdapter() { };
        addMouseListener(swallowEverything);
        addMouseMotionListener(swallowEverything);
        addMouseWheelListener(swallowEverything);

        showPage(0);
    }

    private void showPage(int pageIndex) {
        index = pageIndex;
        Page page = pages.get(pageIndex);
        box = boxBoundsFor(page);
        removeAll();

        JLabel title = new JLabel(page.title);
        title.setFont(PixelKit.font(16f));
        title.setForeground(GOLD);
        title.setBounds(box.x + PADDING, box.y + 24, box.width - PADDING * 2 - 110, 26);
        add(title);

        JLabel counter = new JLabel((pageIndex + 1) + " / " + pages.size(), SwingConstants.RIGHT);
        counter.setFont(PixelKit.font(10f));
        counter.setForeground(TEXT_DIM);
        counter.setBounds(box.x + box.width - PADDING - 110, box.y + 28, 110, 20);
        add(counter);

        int y = box.y + 66;
        for (String line : page.lines) {
            JLabel label = new JLabel(line);
            label.setFont(PixelKit.font(11f));
            label.setForeground(TEXT);
            label.setBounds(box.x + PADDING, y, box.width - PADDING * 2, 22);
            add(label);
            y += LINE_HEIGHT;
        }

        boolean first = pageIndex == 0;
        boolean last = pageIndex == pages.size() - 1;
        int buttonY = box.y + box.height - BUTTON_HEIGHT - 18;

        WoodButton next = new WoodButton(last ? "LET'S GO!" : "NEXT >", null, WoodButton.GREEN);
        next.setBounds(box.x + box.width - 24 - 168, buttonY, 168, BUTTON_HEIGHT);
        next.addActionListener(e -> {
            if (last) {
                onFinished.run();
            } else {
                showPage(index + 1);
            }
        });
        add(next);

        if (!first) {
            WoodButton back = new WoodButton("< BACK", null, WoodButton.TAN);
            back.setBounds(box.x + box.width - 24 - 168 - 12 - 138, buttonY, 138, BUTTON_HEIGHT);
            back.addActionListener(e -> showPage(index - 1));
            add(back);
        }
        if (!last) {
            WoodButton skip = new WoodButton("SKIP TUTORIAL", null, WoodButton.TAN);
            skip.setBounds(box.x + 24, buttonY, 204, BUTTON_HEIGHT);
            skip.addActionListener(e -> onFinished.run());
            add(skip);
        }

        revalidate();
        repaint();
        next.requestFocusInWindow();
    }

    private static Rectangle boxBoundsFor(Page page) {
        int height = PixelKit.snap(66 + page.lines.length * LINE_HEIGHT + BUTTON_HEIGHT + 40);
        switch (page.placement) {
            case BOTTOM:
                return new Rectangle(90, PixelKit.snap(HEIGHT - 15 - height), 1101, height);
            case BOTTOM_LEFT:
                return new Rectangle(36, PixelKit.snap(HEIGHT - 15 - height), 744, height);
            default:
                return new Rectangle(189, PixelKit.snap((HEIGHT - height) / 2), 903, height);
        }
    }

    private static Rectangle grow(Rectangle r, int amount) {
        return new Rectangle(r.x - amount, r.y - amount, r.width + amount * 2, r.height + amount * 2);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Page page = pages.get(index);

        Area veil = new Area(new Rectangle(0, 0, getWidth(), getHeight()));
        for (Rectangle highlight : page.highlights) {
            veil.subtract(new Area(grow(highlight, HIGHLIGHT_MARGIN)));
        }
        g2.setColor(VEIL);
        g2.fill(veil);

        PixelKit.paint(g2, 0, 0, getWidth(), getHeight(), pg -> {
            int px = PixelKit.SCALE;
            for (Rectangle highlight : page.highlights) {
                Rectangle frame = grow(highlight, HIGHLIGHT_MARGIN + px);
                pg.setColor(GOLD);
                pg.fillRect(frame.x, frame.y, frame.width, px);
                pg.fillRect(frame.x, frame.y + frame.height - px, frame.width, px);
                pg.fillRect(frame.x, frame.y, px, frame.height);
                pg.fillRect(frame.x + frame.width - px, frame.y, px, frame.height);
            }

            pg.setColor(new Color(0, 0, 0, 110));
            pg.fillRect(box.x + 9, box.y + 9, box.width, box.height);
            pg.setColor(BOX_EDGE);
            pg.fillRect(box.x, box.y, box.width, box.height);
            pg.setColor(BOX_TRIM);
            pg.fillRect(box.x + px, box.y + px, box.width - px * 2, box.height - px * 2);
            pg.setColor(BOX_FILL);
            pg.fillRect(box.x + px * 2, box.y + px * 2, box.width - px * 4, box.height - px * 4);
        });
        g2.dispose();
    }
}
