package petsa.ui;

import petsa.model.DailyLedger.RentBill;
import petsa.model.GameState.ApartmentType;
import petsa.ui.PixelKit.WoodButton;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class RentDayOverlay extends JPanel {

    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    private static final Rectangle DOOR = new Rectangle(171, 150, 282, 459);
    private static final Rectangle BILL = new Rectangle(501, 150, 612, 459);
    private static final Rectangle BUTTON = new Rectangle(597, 627, 420, 54);
    private static final int BILL_PAD = 30;
    private static final int LINE_COUNT = 5;
    private static final int FIRST_LINE_DELAY_MS = 700;
    private static final int LINE_DELAY_MS = 550;
    private static final int STAMP_MS = 180;

    private static final Color VEIL = new Color(20, 0, 0, 205);
    private static final Color TITLE_RED = new Color(0xE0, 0x5A, 0x4A);
    private static final Color CREAM = new Color(0xF0, 0xE6, 0xD2);
    private static final Color PAPER = new Color(0xF6, 0xF0, 0xE0);
    private static final Color INK = new Color(0x2E, 0x24, 0x1C);
    private static final Color DIM = new Color(0x7A, 0x6A, 0x58);
    private static final Color HEADER = new Color(0x7A, 0x22, 0x1C);
    private static final Color GOLD = new Color(0xE8, 0xC2, 0x4A);
    private static final Color PAID_GREEN = new Color(0x2E, 0x7A, 0x3A);
    private static final Color UNPAID_RED = new Color(0xB0, 0x2A, 0x22);

    private final RentBill bill;
    private final boolean canPay;
    private final WoodButton button;
    private final Timer revealTimer;
    private Timer stampTimer;
    private int revealed = 0;
    private boolean stamped = false;
    private float stampProgress = 0f;

    public RentDayOverlay(RentBill bill, Runnable onDone) {
        this.bill = bill;
        this.canPay = bill.getBalanceAfter() > 0;
        setLayout(null);
        setOpaque(false);
        setSize(WIDTH, HEIGHT);

        MouseAdapter clicks = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (revealed < LINE_COUNT) {
                    revealAll();
                }
            }
        };
        addMouseListener(clicks);
        addMouseMotionListener(new MouseAdapter() { });
        addMouseWheelListener(new MouseAdapter() { });

        button = new WoodButton(canPay ? "PAY " + PixelKit.pesoCents(bill.getTotal()) : "I CAN'T PAY...", null,
                canPay ? WoodButton.GREEN : WoodButton.RED);
        button.setBounds(BUTTON);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setVisible(false);
        button.addActionListener(e -> {
            if (!stamped) {
                slamStamp();
            } else {
                stopTimers();
                onDone.run();
            }
        });
        add(button);

        revealTimer = new Timer(LINE_DELAY_MS, e -> {
            revealed++;
            repaint();
            if (revealed >= LINE_COUNT) {
                ((Timer) e.getSource()).stop();
                button.setVisible(true);
            }
        });
        revealTimer.setInitialDelay(FIRST_LINE_DELAY_MS);
        revealTimer.start();
    }

    private void revealAll() {
        revealTimer.stop();
        revealed = LINE_COUNT;
        button.setVisible(true);
        repaint();
    }

    private void slamStamp() {
        stamped = true;
        button.setEnabled(false);
        long start = System.nanoTime();
        stampTimer = new Timer(15, e -> {
            stampProgress = Math.min(1f, (System.nanoTime() - start) / 1_000_000f / STAMP_MS);
            repaint();
            if (stampProgress >= 1f) {
                ((Timer) e.getSource()).stop();
                button.setTitleAndColor("CONTINUE", WoodButton.TAN);
                button.setEnabled(true);
            }
        });
        stampTimer.start();
    }

    private void stopTimers() {
        revealTimer.stop();
        if (stampTimer != null) {
            stampTimer.stop();
        }
    }

    @Override
    public void removeNotify() {
        stopTimers();
        super.removeNotify();
    }

    private static String apartmentName(ApartmentType apartment) {
        return apartment == ApartmentType.HIGH_END ? "High-End apartment" : "Standard apartment";
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(VEIL);
        g2.fillRect(0, 0, getWidth(), getHeight());
        PixelKit.paint(g2, 0, 0, getWidth(), getHeight(), pg -> {
            paintDoorway(pg);
            paintBillPaper(pg);
        });
        paintHeadings(g2);
        paintBillText(g2);
        if (stamped) {
            paintStamp(g2);
        }
        g2.dispose();
    }

    private void paintDoorway(Graphics2D pg) {
        int px = PixelKit.SCALE;
        Rectangle d = DOOR;
        pg.setColor(new Color(0x2A, 0x18, 0x0C));
        pg.fillRect(d.x, d.y, d.width, d.height);
        int inX = d.x + 21;
        int inY = d.y + 21;
        int inW = d.width - 42;
        int inH = d.height - 21;
        pg.setPaint(new GradientPaint(0, inY, new Color(0xF4, 0xCC, 0x78), 0, inY + inH, new Color(0x8A, 0x5A, 0x2A)));
        pg.fillRect(inX, inY, inW, inH);

        int cx = d.x + d.width / 2;
        Color shadow = new Color(0x16, 0x0E, 0x08);
        pg.setColor(shadow);
        pg.fillRect(cx - 54, inY + 51, 108, 12);
        pg.fillRect(cx - 33, inY + 18, 66, 36);
        pg.fillOval(cx - 36, inY + 54, 72, 78);
        pg.fillRect(cx - 15, inY + 126, 30, 18);
        pg.fillRoundRect(cx - 87, inY + 138, 174, inH - 138, 60, 60);
        pg.setColor(new Color(0xD8, 0xCC, 0xB0));
        pg.fillRect(cx + 12, inY + 186, 51, 69);
        pg.setColor(shadow);
        pg.fillRect(cx + 27, inY + 180, 21, 9);
        pg.fillRect(cx - 6, inY + 204, 36, 21);

        pg.setColor(new Color(0x5A, 0x3A, 0x1E));
        pg.fillRect(d.x, d.y, d.width, 21);
        pg.fillRect(d.x, d.y, 21, d.height);
        pg.fillRect(d.x + d.width - 21, d.y, 21, d.height);
        pg.setColor(new Color(0x2A, 0x18, 0x0C));
        pg.fillRect(d.x + 18, d.y + 18, px, d.height - 18);
        pg.fillRect(d.x + d.width - 21, d.y + 18, px, d.height - 18);
    }

    private void paintBillPaper(Graphics2D pg) {
        pg.setColor(new Color(0, 0, 0, 120));
        pg.fillRect(BILL.x + 12, BILL.y + 12, BILL.width, BILL.height);
        pg.setColor(PAPER);
        pg.fillRect(BILL.x, BILL.y, BILL.width, BILL.height);
        pg.setColor(HEADER);
        pg.fillRect(BILL.x, BILL.y, BILL.width, 48);
        if (revealed >= 4) {
            pg.setColor(INK);
            pg.fillRect(BILL.x + BILL_PAD, BILL.y + 240, BILL.width - BILL_PAD * 2, PixelKit.SCALE);
        }
    }

    private void paintHeadings(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        Font titleFont = PixelKit.font(40f);
        FontMetrics fm = g2.getFontMetrics(titleFont);
        String title = "RENT DAY";
        int x = (getWidth() - fm.stringWidth(title)) / 2;
        g2.setFont(titleFont);
        g2.setColor(new Color(0x40, 0x10, 0x0C));
        g2.drawString(title, x + 5, 97);
        g2.setColor(TITLE_RED);
        g2.drawString(title, x, 92);

        Font subFont = PixelKit.font(12f);
        String sub = "*KNOCK KNOCK KNOCK*  The landlord is at your door.";
        g2.setFont(subFont);
        g2.setColor(CREAM);
        g2.drawString(sub, (getWidth() - g2.getFontMetrics().stringWidth(sub)) / 2, 128);

        Font headerFont = PixelKit.font(16f);
        g2.setFont(headerFont);
        g2.setColor(GOLD);
        String header = "STATEMENT OF ACCOUNT";
        g2.drawString(header, BILL.x + (BILL.width - g2.getFontMetrics().stringWidth(header)) / 2, BILL.y + 32);
    }

    private void paintBillText(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int left = BILL.x + BILL_PAD;
        int right = BILL.x + BILL.width - BILL_PAD;
        Font small = PixelKit.font(9f);
        g2.setColor(DIM);
        PixelKit.drawText(g2, "Room rent and utilities for this month - due today.", small, left, BILL.y + 76);

        Font row = PixelKit.font(12f);
        if (revealed >= 1) {
            billRow(g2, "RENT", bill.getRent(), row, left, right, BILL.y + 118);
        }
        if (revealed >= 2) {
            billRow(g2, "ELECTRICITY", bill.getElectricity(), row, left, right, BILL.y + 160);
            g2.setColor(DIM);
            PixelKit.drawText(g2, apartmentName(bill.getApartment()) + " rate", small, left, BILL.y + 178);
        }
        if (revealed >= 3) {
            billRow(g2, "WATER", bill.getWater(), row, left, right, BILL.y + 208);
            g2.setColor(DIM);
            PixelKit.drawText(g2, "From your showers and laundry all month", small, left, BILL.y + 226);
        }
        if (revealed >= 4) {
            billRow(g2, "TOTAL DUE", bill.getTotal(), PixelKit.font(16f), left, right, BILL.y + 280);
        }
        if (revealed >= 5) {
            Font balanceFont = PixelKit.font(10f);
            String balance = "Your balance:  " + PixelKit.pesoCents(bill.getBalanceBefore()) + "  ->  " + PixelKit.pesoCents(bill.getBalanceAfter());
            g2.setColor(canPay ? INK : UNPAID_RED);
            PixelKit.drawText(g2, balance, balanceFont, left, BILL.y + 322);
        }
    }

    private void billRow(Graphics2D g2, String label, int amount, Font font, int left, int right, int baseline) {
        g2.setColor(INK);
        PixelKit.drawText(g2, label, font, left, baseline);
        String value = PixelKit.pesoCents(amount);
        PixelKit.drawText(g2, value, font, right - PixelKit.textWidth(value, font), baseline);
    }

    private void paintStamp(Graphics2D g2) {
        String word = canPay ? "PAID IN FULL" : "CAN'T PAY";
        Color color = canPay ? PAID_GREEN : UNPAID_RED;
        Font font = PixelKit.font(24f);
        FontMetrics fm = g2.getFontMetrics(font);
        int w = fm.stringWidth(word) + 36;
        int h = 54;
        int cx = BILL.x + BILL.width / 2;
        int cy = BILL.y + 395;
        double scale = 1 + (1 - stampProgress) * 0.8;

        Graphics2D s = (Graphics2D) g2.create();
        s.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0.05f, stampProgress) * 0.9f));
        s.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        s.translate(cx, cy);
        s.rotate(Math.toRadians(-10));
        s.scale(scale, scale);
        s.setColor(color);
        s.setStroke(new BasicStroke(6f));
        s.drawRect(-w / 2, -h / 2, w, h);
        s.setStroke(new BasicStroke(3f));
        s.drawRect(-w / 2 + 9, -h / 2 + 9, w - 18, h - 18);
        s.setFont(font);
        s.drawString(word, -fm.stringWidth(word) / 2, (fm.getAscent() - fm.getDescent()) / 2);
        s.dispose();
    }
}
