package petsa.ui;

import petsa.model.DailyLedger;
import petsa.model.DailyLedger.CashCategory;
import petsa.model.DailyLedger.DaySummary;
import petsa.model.Player.Attribute;
import petsa.ui.PixelKit.BackgroundPanel;
import petsa.ui.PixelKit.MoneyLabel;
import petsa.ui.PixelKit.PaperCard;
import petsa.ui.PixelKit.WoodButton;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;

/**
 * The End of Day screen shown after the player clicks the Bed: a title,
 * a short note about how the budget is doing, a Financial Ledger card
 * (starting balance, money in and out by category, closing balance),
 * a Stats & Inventory card, and a SLEEP button that continues to the
 * next day (or FINISH on Day 30).
 *
 * Every ledger row comes straight from a DaySummary, and the rows
 * always add up: starting balance + every row = closing balance.
 * Rows for categories with no activity that day are hidden, except
 * Salary Earned and Random Events, which always show (as in the
 * reference mockup). Random Events is split into one line per event
 * that moved money that day, named after the event.
 *
 * Optional background image: src/petsa/ui/resources/end_of_day_background.jpeg
 * (falls back to a dark gradient until it is added).
 */
public class EndOfDayPanel extends BackgroundPanel {

    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    private static final Color INK = new Color(0x3B, 0x2A, 0x1C);
    private static final Color POSITIVE = new Color(0x4E, 0x8A, 0x4A);
    private static final Color NEGATIVE = new Color(0xB0, 0x44, 0x3C);
    private static final Color TITLE_GOLD = new Color(0xE8, 0xC2, 0x4A);

    private static final int ROW_HEIGHT = 34;
    private static final int CARD_PADDING = 24;
    private static final int CARD_WIDTH = 470;
    private static final int CARD_INNER = CARD_WIDTH - 6; // PaperCard reserves 6px for its drop shadow

    public EndOfDayPanel(DaySummary summary, Runnable onContinue) {
        super("resources/end_of_day_background.jpeg", "resources/end_of_day_background.png");
        setPreferredSize(new Dimension(WIDTH, HEIGHT));

        JLabel title = new JLabel("END OF DAY " + summary.getDay(), SwingConstants.CENTER);
        title.setFont(PixelKit.font(36f));
        title.setForeground(TITLE_GOLD);
        title.setBounds(0, 35, WIDTH, 50);
        add(title);

        add(buildNote(summary));
        add(buildLedgerCard(summary));
        add(buildStatsCard(summary));

        WoodButton continueButton = new WoodButton(summary.isFinalDay() ? "FINISH >" : "SLEEP >", null, WoodButton.BLUE);
        continueButton.setBounds(1010, 615, 230, 64);
        continueButton.addActionListener(e -> onContinue.run());
        add(continueButton);
    }

    private JPanel buildNote(DaySummary summary) {
        PaperCard note = new PaperCard(PaperCard.WHITE, false);
        note.setBounds(290, 105, 700, 86);
        addNoteLine(note, "Day " + summary.getDay() + " is in the books.", 16);
        addNoteLine(note, moodLine(summary), 46);
        return note;
    }

    private void addNoteLine(JPanel note, String text, int y) {
        JLabel line = new JLabel(text, SwingConstants.CENTER);
        line.setFont(PixelKit.font(12f));
        line.setForeground(INK);
        line.setBounds(0, y, 694, 22);
        note.add(line);
    }

    /** A one-line verdict on the day. The thresholds are simple placeholders - tune freely. */
    private String moodLine(DaySummary summary) {
        int closing = summary.getClosingBalance();
        if (summary.isFinalDay()) {
            return closing >= 0 ? "You made it through the month." : "The month ended in the red.";
        }
        if (closing < 0) {
            return "You're in the red. Something has to give.";
        }
        if (summary.getNetChange() > 0) {
            return "You came out ahead today.";
        }
        if (summary.getDay() < 29 && closing < 2500) {
            return "Money is getting tight - rent is still coming.";
        }
        return "Budget remains stable for now.";
    }

    private JPanel buildLedgerCard(DaySummary summary) {
        PaperCard card = new PaperCard(PaperCard.CREAM, false);
        card.setBounds(150, 215, CARD_WIDTH, 390);
        addCardHeader(card, "FINANCIAL LEDGER", CARD_INNER);

        int y = 70;
        y = addMoneyRow(card, "Starting Balance:", summary.getStartingBalance(), false, INK, y);
        for (CashCategory category : CashCategory.values()) {
            if (category == CashCategory.EVENTS && !summary.getEventEntries().isEmpty()) {
                // One line per event that moved money today, named after the event (e.g. "Ambagan: -P200.00").
                for (DailyLedger.EventEntry event : summary.getEventEntries()) {
                    Color color = event.getAmount() > 0 ? POSITIVE : NEGATIVE;
                    y = addMoneyRow(card, event.getLabel() + ":", event.getAmount(), true, color, y);
                }
                continue;
            }
            int total = summary.getTotal(category);
            boolean alwaysShown = category == CashCategory.SALARY || category == CashCategory.EVENTS;
            if (total == 0 && !alwaysShown) {
                continue;
            }
            Color color = total > 0 ? POSITIVE : (total < 0 ? NEGATIVE : INK);
            y = addMoneyRow(card, category.getLabel() + ":", total, true, color, y);
        }

        card.add(new Divider(CARD_PADDING, y + 4, CARD_INNER - CARD_PADDING * 2));
        Color closingColor = summary.getClosingBalance() < 0 ? NEGATIVE : INK;
        addMoneyRow(card, "Closing Balance:", summary.getClosingBalance(), false, closingColor, y + 14);
        return card;
    }

    private JPanel buildStatsCard(DaySummary summary) {
        PaperCard card = new PaperCard(PaperCard.WHITE, true);
        card.setBounds(660, 215, CARD_WIDTH, 390);
        addCardHeader(card, "STATS & INVENTORY", CARD_INNER);

        int y = 70;
        y = addTextRow(card, "Food Stock:", summary.getFoodStock() + " meals", INK, y);
        y = addTextRow(card, "Medicine Stock:", summary.getMedicineStock() + " pills", INK, y);
        for (DaySummary.StatReading stat : summary.getStats()) {
            y = addTextRow(card, stat.getName() + ":", stat.getValue() + "%", zoneColor(stat.getZone()), y);
        }
        return card;
    }

    private void addCardHeader(JPanel card, String text, int innerWidth) {
        JLabel header = new JLabel(text);
        header.setFont(PixelKit.font(15f));
        header.setForeground(INK);
        header.setBounds(CARD_PADDING, 22, innerWidth - CARD_PADDING * 2, 26);
        card.add(header);
        card.add(new Divider(CARD_PADDING, 52, innerWidth - CARD_PADDING * 2));
    }

    /** Adds "label ........ amount" and returns the y for the next row. */
    private int addMoneyRow(JPanel card, String label, int amount, boolean signed, Color color, int y) {
        addRowLabel(card, label, y);
        MoneyLabel money = new MoneyLabel(11f, color, MoneyLabel.Align.RIGHT);
        money.setAmount(amount, signed);
        money.setBounds(230, y, CARD_INNER - 230 - CARD_PADDING, ROW_HEIGHT);
        card.add(money);
        return y + ROW_HEIGHT;
    }

    private int addTextRow(JPanel card, String label, String value, Color valueColor, int y) {
        addRowLabel(card, label, y);
        JLabel valueLabel = new JLabel(value, SwingConstants.RIGHT);
        valueLabel.setFont(PixelKit.font(11f));
        valueLabel.setForeground(valueColor);
        valueLabel.setBounds(300, y, CARD_INNER - 300 - CARD_PADDING, ROW_HEIGHT);
        card.add(valueLabel);
        return y + ROW_HEIGHT;
    }

    private void addRowLabel(JPanel card, String text, int y) {
        JLabel label = new JLabel(text);
        label.setFont(PixelKit.font(10f));
        label.setForeground(INK);
        label.setBounds(CARD_PADDING, y, 300, ROW_HEIGHT);
        card.add(label);
    }

    private Color zoneColor(Attribute.Zone zone) {
        switch (zone) {
            case GREEN:
                return POSITIVE;
            case YELLOW:
                return new Color(0xB0, 0x86, 0x20);
            default:
                return NEGATIVE;
        }
    }

    /** A thin ink line used under headers and above the closing balance. */
    private static class Divider extends JPanel {
        Divider(int x, int y, int width) {
            setOpaque(false);
            setBounds(x, y, width, 3);
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(INK);
            g.fillRect(0, 1, getWidth(), 2);
        }
    }
}
