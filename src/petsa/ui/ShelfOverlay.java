package petsa.ui;

import petsa.model.Player;
import petsa.model.Task.EatTask;
import petsa.ui.PixelKit.WoodButton;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;

public class ShelfOverlay extends JPanel {

    public interface Actions {
        void onBuySupplies();

        void onClose();
    }

    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    private static final Rectangle FRAME = new Rectangle(255, 36, 771, 522);
    private static final Rectangle TITLE = new Rectangle(435, 54, 411, 51);
    private static final Rectangle FOOD_ROW = new Rectangle(282, 123, 717, 174);
    private static final Rectangle MEDICINE_ROW = new Rectangle(282, 333, 717, 174);
    private static final int PLANK_H = 18;
    private static final int ITEMS_W = 384;
    private static final int CARD_X_OFFSET = 411;
    private static final int CARD_W = 294;
    private static final int CARD_H = 150;
    private static final Rectangle BUY_BUTTON = new Rectangle(363, 576, 273, 48);
    private static final Rectangle BACK_BUTTON = new Rectangle(645, 576, 273, 48);

    private static final int MAX_CANS_SHOWN = 16;
    private static final int MAX_PILLS_SHOWN = 30;
    private static final int LOW_MEDICINE = 3;

    private static final Color VEIL = new Color(0, 0, 0, 150);
    private static final Color WOOD_EDGE = new Color(0x3A, 0x24, 0x12);
    private static final Color WOOD = new Color(0x7A, 0x54, 0x32);
    private static final Color BACK_PANEL = new Color(0x4A, 0x32, 0x20);
    private static final Color PLANK = new Color(0x9A, 0x72, 0x4A);
    private static final Color PAPER = new Color(0xF6, 0xF4, 0xEE);
    private static final Color INK = new Color(0x2E, 0x24, 0x1C);
    private static final Color DIM = new Color(0x6E, 0x60, 0x50);
    private static final Color WARNING = new Color(0xB0, 0x3A, 0x2E);
    private static final Color[] CAN_COLORS = {
            new Color(0xB0, 0x3A, 0x2E), new Color(0x3A, 0x6E, 0xB0), new Color(0x4E, 0x8A, 0x4A), new Color(0xD0, 0x86, 0x36)
    };

    private final Player player;

    public ShelfOverlay(Player player, Actions actions) {
        this.player = player;
        setLayout(null);
        setOpaque(false);
        setSize(WIDTH, HEIGHT);
        MouseAdapter swallowEverything = new MouseAdapter() { };
        addMouseListener(swallowEverything);
        addMouseMotionListener(swallowEverything);
        addMouseWheelListener(swallowEverything);

        WoodButton buy = new WoodButton("BUY SUPPLIES", null, WoodButton.GREEN);
        buy.setBounds(BUY_BUTTON);
        buy.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        buy.addActionListener(e -> actions.onBuySupplies());
        add(buy);

        WoodButton back = new WoodButton("< BACK", null, WoodButton.TAN);
        back.setBounds(BACK_BUTTON);
        back.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        back.addActionListener(e -> actions.onClose());
        add(back);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(VEIL);
        g2.fillRect(0, 0, getWidth(), getHeight());
        int food = player.getInventory().getFoodStock();
        int medicine = player.getInventory().getMedicineStock();

        PixelKit.paint(g2, 0, 0, getWidth(), getHeight(), pg -> {
            paintCabinet(pg);
            paintCans(pg, food);
            paintPills(pg, medicine);
            paintCard(pg, FOOD_ROW);
            paintCard(pg, MEDICINE_ROW);
        });

        paintTitle(g2);
        paintItemCaption(g2, FOOD_ROW, food, MAX_CANS_SHOWN);
        paintItemCaption(g2, MEDICINE_ROW, medicine, MAX_PILLS_SHOWN);
        paintCardText(g2, FOOD_ROW, "BULK FOOD", food + (food == 1 ? " day" : " days"),
                "Each Eat uses 1 day of food instead of paying \u20B1" + EatTask.MEAL_COST + ".",
                food == 0 ? "None left - meals cost cash." : null);
        paintCardText(g2, MEDICINE_ROW, "MEDICINE", medicine + (medicine == 1 ? " pill" : " pills"),
                "Getting sick uses 1-3 pills. Without enough, it's a \u20B1" + Player.MEDICAL_BILL + " bill.",
                medicine == 0 ? "Out of medicine!" : (medicine < LOW_MEDICINE ? "Running low!" : null));
        g2.dispose();
    }

    private void paintCabinet(Graphics2D pg) {
        int px = PixelKit.SCALE;
        pg.setColor(new Color(0, 0, 0, 110));
        pg.fillRect(FRAME.x + 12, FRAME.y + 12, FRAME.width, FRAME.height);
        pg.setColor(WOOD_EDGE);
        pg.fillRect(FRAME.x, FRAME.y, FRAME.width, FRAME.height);
        pg.setColor(WOOD);
        pg.fillRect(FRAME.x + px, FRAME.y + px, FRAME.width - px * 2, FRAME.height - px * 2);
        pg.setColor(BACK_PANEL);
        pg.fillRect(FRAME.x + 24, TITLE.y + TITLE.height + 9, FRAME.width - 48,
                FRAME.y + FRAME.height - 24 - (TITLE.y + TITLE.height + 9));

        for (Rectangle row : new Rectangle[] {FOOD_ROW, MEDICINE_ROW}) {
            int plankY = row.y + row.height;
            pg.setColor(WOOD_EDGE);
            pg.fillRect(FRAME.x + 18, plankY, FRAME.width - 36, PLANK_H);
            pg.setColor(PLANK);
            pg.fillRect(FRAME.x + 18, plankY, FRAME.width - 36, PLANK_H - px);
        }

        pg.setColor(new Color(0, 0, 0, 90));
        pg.fillRect(TITLE.x + 6, TITLE.y + 6, TITLE.width, TITLE.height);
        pg.setColor(PAPER);
        pg.fillRect(TITLE.x, TITLE.y, TITLE.width, TITLE.height);
    }

    private void paintCans(Graphics2D pg, int food) {
        int shown = Math.min(food, MAX_CANS_SHOWN);
        for (int i = 0; i < shown; i++) {
            int x = FOOD_ROW.x + 18 + (i % 8) * 45;
            int y = FOOD_ROW.y + 24 + (i / 8) * 66;
            Color body = CAN_COLORS[i % CAN_COLORS.length];
            pg.setColor(WOOD_EDGE);
            pg.fillRect(x - 3, y - 3, 42, 57);
            pg.setColor(body);
            pg.fillRect(x, y, 36, 51);
            pg.setColor(new Color(0xC8, 0xC8, 0xC8));
            pg.fillRect(x, y, 36, 6);
            pg.setColor(new Color(0xF0, 0xE6, 0xD0));
            pg.fillRect(x, y + 18, 36, 15);
            pg.setColor(body.darker());
            pg.fillRect(x + 12, y + 21, 12, 9);
        }
    }

    private void paintPills(Graphics2D pg, int medicine) {
        int shown = Math.min(medicine, MAX_PILLS_SHOWN);
        for (int i = 0; i < shown; i++) {
            int x = MEDICINE_ROW.x + 18 + (i % 10) * 36;
            int y = MEDICINE_ROW.y + 36 + (i / 10) * 36;
            pg.setColor(WOOD_EDGE);
            pg.fillRect(x - 3, y - 3, 33, 18);
            pg.setColor(new Color(0xF4, 0xF2, 0xEA));
            pg.fillRect(x, y, 15, 12);
            pg.setColor(new Color(0xC8, 0x40, 0x3A));
            pg.fillRect(x + 15, y, 12, 12);
        }
    }

    private void paintCard(Graphics2D pg, Rectangle row) {
        int x = row.x + CARD_X_OFFSET;
        int y = row.y + 12;
        pg.setColor(new Color(0, 0, 0, 90));
        pg.fillRect(x + 6, y + 6, CARD_W, CARD_H);
        pg.setColor(PAPER);
        pg.fillRect(x, y, CARD_W, CARD_H);
        BoardOverlay.paintPin(pg, x + CARD_W / 2 - 6, y - 3, new Color(0xB0, 0x3A, 0x2E));
    }

    private void paintTitle(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        Font font = PixelKit.font(16f);
        FontMetrics fm = g2.getFontMetrics(font);
        String title = "STORAGE & INVENTORY";
        g2.setFont(font);
        g2.setColor(INK);
        g2.drawString(title, TITLE.x + (TITLE.width - fm.stringWidth(title)) / 2,
                TITLE.y + (TITLE.height + fm.getAscent() - fm.getDescent()) / 2);
    }

    private void paintItemCaption(Graphics2D g2, Rectangle row, int count, int maxShown) {
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        String caption = count == 0 ? "EMPTY" : (count > maxShown ? "+" + (count - maxShown) + " more" : null);
        if (caption == null) {
            return;
        }
        Font font = PixelKit.font(count == 0 ? 16f : 10f);
        FontMetrics fm = g2.getFontMetrics(font);
        g2.setFont(font);
        g2.setColor(new Color(0xD8, 0xC8, 0xA8));
        int areaX = row.x + 12;
        int x = count == 0 ? areaX + (ITEMS_W - fm.stringWidth(caption)) / 2 : areaX + ITEMS_W - fm.stringWidth(caption);
        int y = count == 0 ? row.y + row.height / 2 + 6 : row.y + row.height - 9;
        g2.drawString(caption, x, y);
    }

    private void paintCardText(Graphics2D g2, Rectangle row, String name, String amount, String description,
            String warning) {
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int x = row.x + CARD_X_OFFSET + 15;
        int width = CARD_W - 30;
        int y = row.y + 12 + 30;

        Font titleFont = PixelKit.font(12f);
        g2.setColor(INK);
        PixelKit.drawText(g2, name, titleFont, x, y);
        y += 26;
        Font amountFont = PixelKit.font(16f);
        g2.setColor(INK);
        PixelKit.drawText(g2, amount, amountFont, x, y);
        y += 22;
        Font bodyFont = PixelKit.font(9f);
        for (String line : PixelKit.wrapText(description, bodyFont, width)) {
            g2.setColor(DIM);
            PixelKit.drawText(g2, line, bodyFont, x, y);
            y += 15;
        }
        if (warning != null) {
            g2.setColor(WARNING);
            PixelKit.drawText(g2, warning, PixelKit.font(10f), x, y + 4);
        }
    }
}
