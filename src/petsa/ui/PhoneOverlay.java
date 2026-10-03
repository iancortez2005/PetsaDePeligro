package petsa.ui;

import petsa.model.GameEngine;
import petsa.model.GameState;
import petsa.model.PartTimeJob.ShiftType;
import petsa.model.Player;
import petsa.model.Player.Inventory;
import petsa.model.RandomEvent;
import petsa.model.RandomEvent.BrokenPhoneEvent;
import petsa.ui.PixelKit.TextBlock;
import petsa.ui.PixelKit.WoodButton;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class PhoneOverlay extends JPanel {

    public interface Host {
        void onStateChanged();

        void onClose();
    }

    public static class Message {
        final String header;
        final String title;
        final String body;
        final String note;
        final String acceptLabel;
        final String acceptEffects;
        final String declineLabel;
        final String declineEffects;
        final Consumer<Boolean> onAnswer;

        public Message(String header, String title, String body, String note, String acceptLabel,
                String acceptEffects, String declineLabel, String declineEffects, Consumer<Boolean> onAnswer) {
            this.header = header;
            this.title = title;
            this.body = body;
            this.note = note;
            this.acceptLabel = acceptLabel;
            this.acceptEffects = acceptEffects;
            this.declineLabel = declineLabel;
            this.declineEffects = declineEffects;
            this.onAnswer = onAnswer;
        }

        public static Message fromEvent(RandomEvent event, String header, String note, Consumer<Boolean> onAnswer) {
            return new Message(header, event.getName(), withPesoSigns(event.getPrompt()), note,
                    event.getAcceptLabel(), event.getAcceptEffects(),
                    event.getDeclineLabel(), event.getDeclineEffects(), onAnswer);
        }
    }

    private enum Tab { MAIL, SHOP }

    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    private static final Rectangle PHONE = new Rectangle(441, 30, 399, 660);
    private static final Rectangle SCREEN = new Rectangle(465, 99, 351, 534);
    private static final int CONTENT_X = SCREEN.x + 12;
    private static final int CONTENT_W = SCREEN.width - 24;
    private static final int STATUS_H = 24;
    private static final int TOP = SCREEN.y + STATUS_H + 3;
    private static final int BOTTOM_BUTTON_Y = SCREEN.y + SCREEN.height - 54;
    private static final int BUTTON_H = 42;

    private static final Color VEIL = new Color(0, 0, 0, 150);
    private static final Color PHONE_EDGE = new Color(0x15, 0x15, 0x1A);
    private static final Color PHONE_BODY = new Color(0x2C, 0x2C, 0x36);
    private static final Color PHONE_RIM = new Color(0x4A, 0x4A, 0x58);
    private static final Color SCREEN_BG = new Color(0xEF, 0xE6, 0xD2);
    private static final Color STATUS_BG = new Color(0x3A, 0x3A, 0x46);
    private static final Color STATUS_TEXT = new Color(0xD8, 0xD8, 0xE0);
    private static final Color INK = new Color(0x2E, 0x24, 0x1C);
    private static final Color DIM = new Color(0x6E, 0x60, 0x50);
    private static final Color TITLE_RED = new Color(0x8A, 0x2A, 0x20);
    private static final Color GOOD = new Color(0x2E, 0x6A, 0x2E);
    private static final Color BAD = new Color(0x8A, 0x32, 0x28);
    private static final Color RAIN_BLUE = new Color(0x2C, 0x5A, 0x8A);
    private static final Color GOLD = new Color(0xE8, 0xC2, 0x4A);

    private static final int MAX_MEDICINE_QTY = 30;
    private static final int MAX_FOOD_QTY = 15;

    private final GameEngine engine;
    private final Host host;

    private Tab tab = Tab.MAIL;
    private Message pendingMessage;
    private boolean pendingIsAlert;
    private final List<String> mailLog = new ArrayList<>();
    private int mailLogDay = -1;
    private int medicineQty = 5;
    private int foodQty = 3;
    private String shopNotice;
    private boolean shopNoticeGood;

    public PhoneOverlay(GameEngine engine, Host host) {
        this.engine = engine;
        this.host = host;
        setLayout(null);
        setOpaque(false);
        setSize(WIDTH, HEIGHT);
        MouseAdapter swallowEverything = new MouseAdapter() { };
        addMouseListener(swallowEverything);
        addMouseMotionListener(swallowEverything);
        addMouseWheelListener(swallowEverything);
    }

    public void openHome() {
        tab = Tab.MAIL;
        shopNotice = null;
        pendingMessage = null;
        if (engine.isRandomEventPendingToday()) {
            RandomEvent event = engine.getActiveRandomEvent();
            if (event == null) {
                event = engine.triggerRandomEventIfDue();
                host.onStateChanged();
            }
            if (event != null) {
                showMailEvent(event);
                return;
            }
        }
        rebuild();
    }

    public void openShop() {
        if (engine.isRandomEventPendingToday()) {
            openHome();
            return;
        }
        pendingMessage = null;
        tab = Tab.SHOP;
        shopNotice = null;
        rebuild();
    }

    public void openAlert(Message alert) {
        pendingMessage = alert;
        pendingIsAlert = true;
        rebuild();
    }

    public boolean isClosable() {
        return pendingMessage == null;
    }

    private void showMailEvent(RandomEvent event) {
        String note = engine.isTodayRainy()
                ? "It's raining today! Going out (walks, hangouts, work) means getting sick."
                : null;
        String name = event.getName();
        pendingMessage = Message.fromEvent(event, "NEW MESSAGE", note, accepted -> {
            String choice = accepted ? event.getAcceptLabel() : event.getDeclineLabel();
            String effects = accepted ? event.getAcceptEffects() : event.getDeclineEffects();
            engine.resolveActiveRandomEvent(accepted);
            logMail(name + " - you chose " + choice + ". (" + effects + ")");
        });
        pendingIsAlert = false;
        rebuild();
    }

    private void logMail(String line) {
        int today = engine.getState().getDay();
        if (mailLogDay != today) {
            mailLog.clear();
            mailLogDay = today;
        }
        mailLog.add(line);
    }

    private void answer(boolean accepted) {
        Message message = pendingMessage;
        boolean alert = pendingIsAlert;
        pendingMessage = null;
        message.onAnswer.accept(accepted);
        host.onStateChanged();
        if (alert) {
            host.onClose();
        } else {
            tab = Tab.MAIL;
            rebuild();
        }
    }

    private void rebuild() {
        removeAll();
        if (pendingMessage != null) {
            buildMessage(pendingMessage);
        } else {
            buildTabs();
            if (tab == Tab.MAIL) {
                buildInbox();
            } else {
                buildShop();
            }
            WoodButton close = button("CLOSE PHONE", WoodButton.TAN,
                    new Rectangle(CONTENT_X, BOTTOM_BUTTON_Y, CONTENT_W, BUTTON_H));
            close.addActionListener(e -> host.onClose());
        }
        revalidate();
        repaint();
    }

    private void buildTabs() {
        int tabW = (SCREEN.width - 9) / 2;
        WoodButton mail = button("MAIL & ALERTS", tab == Tab.MAIL ? WoodButton.TAN : WoodButton.DARK_WOOD,
                new Rectangle(SCREEN.x + 3, TOP, tabW, BUTTON_H));
        mail.addActionListener(e -> {
            tab = Tab.MAIL;
            rebuild();
        });
        WoodButton shop = button("SHOP", tab == Tab.SHOP ? WoodButton.TAN : WoodButton.DARK_WOOD,
                new Rectangle(SCREEN.x + 6 + tabW, TOP, tabW, BUTTON_H));
        shop.addActionListener(e -> {
            tab = Tab.SHOP;
            shopNotice = null;
            rebuild();
        });
    }

    private void buildInbox() {
        int y = TOP + BUTTON_H + 12;
        y = addText("MESSAGES", 10f, INK, y, 18) + 4;
        boolean haveLog = mailLogDay == engine.getState().getDay() && !mailLog.isEmpty();
        if (engine.isPartTimeDay()) {
            y = addText("PART-TIME JOB TONIGHT", 10f, GOOD, y, 18) + 2;
            y = addText(partTimeNoticeText(), 9f, INK, y, 15) + 10;
        }
        if (engine.isRentReminderDay()) {
            y = addText("RENT REMINDER", 10f, TITLE_RED, y, 18) + 2;
            y = addText(rentReminderText(), 9f, INK, y, 15) + 10;
        }
        if (haveLog) {
            for (String line : mailLog) {
                y = addText(line, 9f, INK, y, 15) + 8;
            }
        } else if (!engine.isRentReminderDay() && !engine.isPartTimeDay()) {
            y = addText("No new messages.", 9f, DIM, y, 15) + 8;
        }
        y += 10;
        y = addText("WEATHER: " + (engine.isTodayRainy() ? "RAINY" : "SUNNY"), 10f,
                engine.isTodayRainy() ? RAIN_BLUE : INK, y, 18) + 4;
        if (engine.isTodayRainy()) {
            addText("Going out today (walks, hangouts, work) means getting sick. Even staying in, "
                    + "you might catch a cold. Showering is replaced by a trip to the laundromat.",
                    9f, RAIN_BLUE, y, 15);
        }
    }

    private String partTimeNoticeText() {
        String text = "A shift is open tonight - go to the Door at Night. Standard: +"
                + PixelKit.peso(ShiftType.STANDARD.getEarnings()) + ", some Stress. Overtime: +"
                + PixelKit.peso(ShiftType.OVERTIME.getEarnings()) + ", a LOT of Stress.";
        if (engine.getState().getPlayer().isPartTimeLocked()) {
            text += " Right now your Academics are 65% or lower, so they won't hire you - study before tonight!";
        }
        return text;
    }

    private String rentReminderText() {
        int days = engine.getDaysUntilRent();
        String when = days == 1
                ? "Rent is due TOMORROW (Day " + GameState.RENT_DUE_DAY + ")!"
                : "Rent is due on Day " + GameState.RENT_DUE_DAY + ", that is " + days + " days from now.";
        return when + " The landlord collects " + PixelKit.peso(GameState.RENT_AMOUNT)
                + " rent plus the electricity and water bills, and on Day " + GameState.TRANSPORT_DUE_DAY
                + " you'll need " + PixelKit.peso(GameState.TRANSPORT_AMOUNT) + " for the ride home. Budget for it!";
    }

    private void buildShop() {
        Player player = engine.getState().getPlayer();
        int y = TOP + BUTTON_H + 12;
        y = addText("Cash: " + PixelKit.pesoCents(player.getCash()), 10f, INK, y, 18) + 12;

        y = addText("MEDICINE - " + PixelKit.peso(Inventory.MEDICINE_PRICE) + " a pill", 10f, INK, y, 18);
        y = addText("On your shelf: " + player.getInventory().getMedicineStock() + " pills", 9f, DIM, y, 15) + 6;
        y = addQuantityRow(y, medicineQty, MAX_MEDICINE_QTY, Inventory.MEDICINE_PRICE,
                qty -> medicineQty = qty, () -> buy(true)) + 18;

        y = addText("BULK FOOD - " + PixelKit.peso(Inventory.MEAL_PRICE) + " a day", 10f, INK, y, 18);
        y = addText("On your shelf: " + player.getInventory().getFoodStock() + " days", 9f, DIM, y, 15) + 6;
        y = addQuantityRow(y, foodQty, MAX_FOOD_QTY, Inventory.MEAL_PRICE,
                qty -> foodQty = qty, () -> buy(false)) + 14;

        if (engine.isPhoneBroken()) {
            y = addText("PHONE REPAIR - " + PixelKit.peso(BrokenPhoneEvent.REPAIR_COST), 10f, INK, y, 18);
            y = addText("Your screen is cracked.", 9f, DIM, y, 15) + 6;
            WoodButton repair = button("REPAIR SCREEN " + PixelKit.peso(BrokenPhoneEvent.REPAIR_COST), WoodButton.GREEN,
                    new Rectangle(CONTENT_X, y, CONTENT_W, 39));
            repair.addActionListener(e -> {
                boolean repaired = engine.repairPhone();
                shopNotice = repaired ? "Screen repaired - good as new!" : "You can't afford that without going broke.";
                shopNoticeGood = repaired;
                host.onStateChanged();
                rebuild();
            });
            y += 39 + 14;
        }

        if (shopNotice != null) {
            addText(shopNotice, 9f, shopNoticeGood ? GOOD : BAD, y, 15);
        }
    }

    private int addQuantityRow(int y, int qty, int max, int unitPrice, Consumer<Integer> setQty, Runnable buy) {
        int h = 39;
        WoodButton minus = button("-", WoodButton.DARK_WOOD, new Rectangle(CONTENT_X, y, 42, h));
        minus.setEnabled(qty > 1);
        minus.addActionListener(e -> {
            setQty.accept(qty - 1);
            shopNotice = null;
            rebuild();
        });

        TextBlock count = new TextBlock(String.valueOf(qty), 12f, INK, 48, h, true);
        count.setLocation(CONTENT_X + 45, y);
        add(count);

        WoodButton plus = button("+", WoodButton.DARK_WOOD, new Rectangle(CONTENT_X + 96, y, 42, h));
        plus.setEnabled(qty < max);
        plus.addActionListener(e -> {
            setQty.accept(qty + 1);
            shopNotice = null;
            rebuild();
        });

        int buyX = CONTENT_X + 150;
        WoodButton buyButton = button("BUY " + PixelKit.peso(qty * unitPrice), WoodButton.GREEN,
                new Rectangle(buyX, y, CONTENT_X + CONTENT_W - buyX, h));
        buyButton.addActionListener(e -> buy.run());
        return y + h;
    }

    private void buy(boolean medicine) {
        boolean bought = medicine ? engine.buyMedicine(medicineQty) : engine.buyFoodStock(foodQty);
        if (bought) {
            shopNotice = medicine ? "Bought " + medicineQty + " pills." : "Bought " + foodQty + " days of food.";
        } else {
            shopNotice = "You can't afford that without going broke.";
        }
        shopNoticeGood = bought;
        host.onStateChanged();
        rebuild();
    }

    private void buildMessage(Message message) {
        int y = TOP + 30;
        y = addText(message.title.toUpperCase(), 12f, TITLE_RED, y, 20) + 8;
        if (message.note != null) {
            y = addText(message.note, 9f, RAIN_BLUE, y, 15) + 8;
        }
        y = addText(message.body, 9f, INK, y, 15) + 12;
        if (message.declineLabel == null) {
            addText("WHAT HAPPENED: " + message.acceptEffects, 9f, BAD, y, 15);
        } else {
            y = addEffects("IF YOU " + message.acceptLabel + ":", message.acceptEffects, GOOD, y) + 6;
            addEffects("IF YOU " + message.declineLabel + ":", message.declineEffects, BAD, y);
        }

        if (message.declineLabel == null) {
            WoodButton ok = button(message.acceptLabel, WoodButton.TAN,
                    new Rectangle(CONTENT_X, BOTTOM_BUTTON_Y, CONTENT_W, BUTTON_H));
            ok.addActionListener(e -> answer(true));
        } else {
            WoodButton accept = button(message.acceptLabel, WoodButton.GREEN,
                    new Rectangle(CONTENT_X, BOTTOM_BUTTON_Y - BUTTON_H - 6, CONTENT_W, BUTTON_H));
            accept.addActionListener(e -> answer(true));
            WoodButton decline = button(message.declineLabel, WoodButton.RED,
                    new Rectangle(CONTENT_X, BOTTOM_BUTTON_Y, CONTENT_W, BUTTON_H));
            decline.addActionListener(e -> answer(false));
        }
    }

    private int addText(String text, float size, Color color, int y, int lineHeight) {
        TextBlock block = new TextBlock(text, size, color, CONTENT_W, lineHeight, false);
        block.setLocation(CONTENT_X, y);
        add(block);
        return y + block.getBlockHeight();
    }

    private int addEffects(String heading, String effects, Color headingColor, int y) {
        List<String> pieces = new ArrayList<>();
        List<Color> colors = new ArrayList<>();
        pieces.add(heading);
        colors.add(headingColor);
        String[] parts = effects.split(", ");
        for (int i = 0; i < parts.length; i++) {
            pieces.add(i < parts.length - 1 ? parts[i] + "," : parts[i]);
            colors.add(effectColor(parts[i], headingColor));
        }
        TextBlock block = new TextBlock(pieces, colors, 9f, CONTENT_W, 15);
        block.setLocation(CONTENT_X, y);
        add(block);
        return y + block.getBlockHeight();
    }

    static Color effectColor(String effect, Color fallback) {
        String text = effect.trim();
        if (text.isEmpty() || (text.charAt(0) != '+' && text.charAt(0) != '-')) {
            return fallback;
        }
        boolean goesUp = text.charAt(0) == '+';
        String lower = text.toLowerCase(Locale.ROOT);
        boolean moreIsWorse = lower.contains("stress") || lower.contains("sickness");
        return goesUp != moreIsWorse ? GOOD : BAD;
    }

    private WoodButton button(String title, Color color, Rectangle bounds) {
        WoodButton button = new WoodButton(title, null, color);
        button.setBounds(bounds);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        add(button);
        return button;
    }

    static String withPesoSigns(String text) {
        return text.replaceAll("\\bP(\\d)", "\u20B1$1");
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        if (engine.isPhoneBroken()) {
            Graphics2D g2 = (Graphics2D) g.create();
            PixelKit.paint(g2, SCREEN.x, SCREEN.y, SCREEN.width, SCREEN.height, this::paintCracks);
            g2.dispose();
        }
    }

    private void paintCracks(Graphics2D pg) {
        int ix = SCREEN.x + SCREEN.width - 48;
        int iy = SCREEN.y + 60;
        int[][] cracks = {
                {0, 0, -45, 36, -81, 90, -126, 117, -168, 180, -222, 204},
                {0, 0, -18, 54, -9, 111, -33, 174, -24, 243, -45, 300},
                {0, 0, -60, 6, -111, -9, -165, 12, -216, 3},
                {0, 0, 21, 33, 27, 72, 39, 126},
                {0, 0, 9, -30, 30, -54},
                {-27, 21, -12, 30, 3, 18},
                {-54, 51, -18, 69, 12, 48},
        };
        pg.setStroke(new BasicStroke(PixelKit.SCALE));
        for (int[] crack : cracks) {
            for (int i = 0; i + 3 < crack.length; i += 2) {
                pg.setColor(new Color(0x2A, 0x2A, 0x33, 110));
                pg.drawLine(ix + crack[i] + 3, iy + crack[i + 1] + 3, ix + crack[i + 2] + 3, iy + crack[i + 3] + 3);
                pg.setColor(new Color(255, 255, 255, 190));
                pg.drawLine(ix + crack[i], iy + crack[i + 1], ix + crack[i + 2], iy + crack[i + 3]);
            }
        }
        pg.setColor(new Color(255, 255, 255, 220));
        pg.fillRect(ix - 3, iy - 3, 9, 9);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(VEIL);
        g2.fillRect(0, 0, getWidth(), getHeight());
        PixelKit.paint(g2, 0, 0, getWidth(), getHeight(), this::paintPhone);
        paintStatusText(g2);
        if (pendingMessage != null) {
            paintMessageHeader(g2);
        }
        g2.dispose();
    }

    private void paintPhone(Graphics2D pg) {
        int px = PixelKit.SCALE;
        pg.setColor(new Color(0, 0, 0, 110));
        pg.fillRoundRect(PHONE.x + 12, PHONE.y + 12, PHONE.width, PHONE.height, 60, 60);
        pg.setColor(PHONE_EDGE);
        pg.fillRoundRect(PHONE.x, PHONE.y, PHONE.width, PHONE.height, 60, 60);
        pg.setColor(PHONE_RIM);
        pg.fillRoundRect(PHONE.x + px * 2, PHONE.y + px * 2, PHONE.width - px * 4, PHONE.height - px * 4, 48, 48);
        pg.setColor(PHONE_BODY);
        pg.fillRoundRect(PHONE.x + px * 3, PHONE.y + px * 3, PHONE.width - px * 6, PHONE.height - px * 6, 42, 42);

        pg.setColor(PHONE_EDGE);
        pg.fillRect(PHONE.x + 150, PHONE.y + 21, 99, 9);
        pg.fillOval(PHONE.x + PHONE.width - 72, PHONE.y + 18, 15, 15);

        pg.setColor(PHONE_EDGE);
        pg.fillRect(SCREEN.x - px, SCREEN.y - px, SCREEN.width + px * 2, SCREEN.height + px * 2);
        pg.setColor(SCREEN_BG);
        pg.fillRect(SCREEN.x, SCREEN.y, SCREEN.width, SCREEN.height);

        pg.setColor(STATUS_BG);
        pg.fillRect(SCREEN.x, SCREEN.y, SCREEN.width, STATUS_H);
        pg.setColor(STATUS_TEXT);
        for (int i = 0; i < 4; i++) {
            int barH = 6 + i * 3;
            pg.fillRect(SCREEN.x + 9 + i * 6, SCREEN.y + STATUS_H - 6 - barH, 3, barH);
        }
        int batteryX = SCREEN.x + SCREEN.width - 39;
        pg.fillRect(batteryX, SCREEN.y + 6, 27, 12);
        pg.fillRect(batteryX + 27, SCREEN.y + 9, 3, 6);
        pg.setColor(STATUS_BG);
        pg.fillRect(batteryX + 3, SCREEN.y + 9, 21, 6);
        pg.setColor(new Color(0x6C, 0xC6, 0x6C));
        pg.fillRect(batteryX + 3, SCREEN.y + 9, 15, 6);

        if (pendingMessage != null) {
            pg.setColor(TITLE_RED);
            pg.fillRect(SCREEN.x, TOP, SCREEN.width, 24);
        }

        pg.setColor(PHONE_RIM);
        pg.fillOval(PHONE.x + PHONE.width / 2 - 18, SCREEN.y + SCREEN.height + 15, 36, 36);
        pg.setColor(PHONE_EDGE);
        pg.fillOval(PHONE.x + PHONE.width / 2 - 12, SCREEN.y + SCREEN.height + 21, 24, 24);
    }

    private void paintStatusText(Graphics2D g2) {
        g2.setFont(PixelKit.font(12f));
        g2.setColor(new Color(0xC8, 0xC8, 0xD4));
        String brand = "MyPhone";
        g2.drawString(brand, PHONE.x + (PHONE.width - g2.getFontMetrics().stringWidth(brand)) / 2, PHONE.y + 66);

        g2.setFont(PixelKit.font(8f));
        g2.setColor(STATUS_TEXT);
        String status = "DAY " + engine.getState().getDay() + " - "
                + engine.getState().getTimeSlot().getLabel().toUpperCase(Locale.ROOT);
        g2.drawString(status, SCREEN.x + (SCREEN.width - g2.getFontMetrics().stringWidth(status)) / 2,
                SCREEN.y + 16);
    }

    private void paintMessageHeader(Graphics2D g2) {
        g2.setFont(PixelKit.font(10f));
        g2.setColor(GOLD);
        String header = pendingMessage.header;
        g2.drawString(header, SCREEN.x + (SCREEN.width - g2.getFontMetrics().stringWidth(header)) / 2, TOP + 17);
    }

}
