package petsa.ui;

import petsa.model.LeaderboardManager;
import petsa.ui.PixelKit.BackgroundPanel;
import petsa.ui.PixelKit.FadePane;
import petsa.ui.PixelKit.MoneyLabel;
import petsa.ui.PixelKit.PaperCard;
import petsa.ui.PixelKit.WoodButton;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The leaderboard: the top 10 months by money left after the fare home,
 * on a paper card - rank, name, savings and date. The top three ranks are gold, silver and bronze.
 *
 * Opened from the main menu, and right after finishing a month, when the
 * new result is highlighted (and, if it didn't make the top 10, its rank
 * is shown underneath).
 */
public class LeaderboardPanel extends BackgroundPanel {

    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    private static final int SHOWN = 10;
    private static final int CARD_X = 219;
    private static final int CARD_Y = 144;
    private static final int CARD_W = 843;
    private static final int CARD_H = 438;
    private static final int ROW_H = 36;
    private static final int FIRST_ROW_Y = 66;

    private static final int RANK_X = 30;
    private static final int NAME_X = 96;
    private static final int SAVINGS_RIGHT = 610;
    private static final int DATE_X = 650;

    private static final Color INK = new Color(0x3B, 0x2A, 0x1C);
    private static final Color DIM = new Color(0x7A, 0x6A, 0x58);
    private static final Color GOLD_TEXT = new Color(0xE8, 0xC9, 0x5A);
    private static final Color CREAM_TEXT = new Color(0xE8, 0xDD, 0xC0);
    private static final Color[] MEDALS = {
            new Color(0xC8, 0x96, 0x1E), new Color(0x8A, 0x8C, 0x94), new Color(0xA8, 0x62, 0x36)
    };
    private static final Color SAVINGS_GREEN = new Color(0x3E, 0x7A, 0x3A);

    /**
     * entries: every recorded result (any order). highlight: the result to
     * highlight (the run just finished), or null.
     */
    public LeaderboardPanel(List<LeaderboardManager.Entry> entries, LeaderboardManager.Entry highlight,
            Runnable onBack) {
        super("resources/menu_background.jpeg", "resources/menu_background.png");
        setPreferredSize(new Dimension(WIDTH, HEIGHT));

        List<LeaderboardManager.Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingInt(LeaderboardManager.Entry::getFinalSavings).reversed());

        JLabel title = new JLabel("LEADERBOARD", SwingConstants.CENTER);
        title.setFont(PixelKit.font(40f));
        title.setForeground(GOLD_TEXT);
        title.setBounds(0, 36, WIDTH, 56);
        add(title);

        JLabel subtitle = new JLabel("MOST MONEY SAVED BY THE END OF THE MONTH", SwingConstants.CENTER);
        subtitle.setFont(PixelKit.font(12f));
        subtitle.setForeground(CREAM_TEXT);
        subtitle.setBounds(0, 104, WIDTH, 22);
        add(subtitle);

        PaperCard card = new PaperCard(PaperCard.CREAM, false);
        card.setBounds(CARD_X, CARD_Y, CARD_W, CARD_H);
        add(card);

        if (sorted.isEmpty()) {
            addText(card, "NO SCORES YET", 16f, INK, 0, 170, CARD_W - 6, SwingConstants.CENTER);
            addText(card, "Survive all 30 days to get your name on the board!", 10f, DIM, 0, 206, CARD_W - 6,
                    SwingConstants.CENTER);
        } else {
            addText(card, "#", 10f, DIM, RANK_X, 22, 40, SwingConstants.LEFT);
            addText(card, "NAME", 10f, DIM, NAME_X, 22, 200, SwingConstants.LEFT);
            addText(card, "SAVINGS", 10f, DIM, SAVINGS_RIGHT - 200, 22, 200, SwingConstants.RIGHT);
            addText(card, "DATE", 10f, DIM, DATE_X, 22, 150, SwingConstants.LEFT);
            card.add(new Rule(RANK_X, 50, CARD_W - 6 - RANK_X * 2));

            for (int i = 0; i < Math.min(SHOWN, sorted.size()); i++) {
                LeaderboardManager.Entry entry = sorted.get(i);
                int y = FIRST_ROW_Y + i * ROW_H;
                Color rankColor = i < MEDALS.length ? MEDALS[i] : INK;
                addText(card, String.valueOf(i + 1), 12f, rankColor, RANK_X, y, 50, SwingConstants.LEFT);
                addText(card, entry.getPlayerName(), 12f, INK, NAME_X, y, 300, SwingConstants.LEFT);
                MoneyLabel savings = new MoneyLabel(12f, SAVINGS_GREEN, MoneyLabel.Align.RIGHT);
                savings.setAmount(entry.getFinalSavings());
                savings.setBounds(SAVINGS_RIGHT - 220, y, 220, ROW_H);
                card.add(savings);
                String date = entry.getRecordedAt().length() >= 10 ? entry.getRecordedAt().substring(0, 10) : entry.getRecordedAt();
                addText(card, date, 9f, DIM, DATE_X, y, 160, SwingConstants.LEFT);
                if (entry.sameAs(highlight)) {
                    card.add(new Highlight(RANK_X - 12, y, CARD_W - 6 - (RANK_X - 12) * 2, ROW_H));
                }
            }

            int rank = rankOf(sorted, highlight);
            if (rank > SHOWN) {
                MoneyLabel yours = new MoneyLabel(12f, GOLD_TEXT, MoneyLabel.Align.LEFT);
                yours.setAmount(highlight.getFinalSavings());
                JLabel yourRank = new JLabel("YOUR RUN RANKS #" + rank + ":", SwingConstants.RIGHT);
                yourRank.setFont(PixelKit.font(12f));
                yourRank.setForeground(GOLD_TEXT);
                yourRank.setBounds(0, CARD_Y + CARD_H + 6, WIDTH / 2 + 60, 28);
                yours.setBounds(WIDTH / 2 + 72, CARD_Y + CARD_H + 6, 300, 28);
                add(yourRank);
                add(yours);
            }
        }

        WoodButton back = new WoodButton("< BACK TO MAIN MENU", null, WoodButton.BLUE);
        back.setBounds(440, 627, 400, 60);
        back.addActionListener(e -> FadePane.run(this, onBack));
        add(back);
    }

    /** The 1-based rank of entry in the sorted list, or 0 if it isn't there. */
    private static int rankOf(List<LeaderboardManager.Entry> sorted, LeaderboardManager.Entry entry) {
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).sameAs(entry)) {
                return i + 1;
            }
        }
        return 0;
    }

    private static void addText(JComponent parent, String text, float size, Color color, int x, int y, int w, int align) {
        JLabel label = new JLabel(text, align);
        label.setFont(PixelKit.font(size));
        label.setForeground(color);
        label.setBounds(x, y, w, ROW_H);
        parent.add(label);
    }

    /** A thin ink line under the column headings. */
    private static class Rule extends JComponent {
        Rule(int x, int y, int w) {
            setBounds(x, y, w, 3);
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(INK);
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    /** A soft yellow band behind the newest result. Added after the row's text, so it's drawn underneath it. */
    private static class Highlight extends JComponent {
        Highlight(int x, int y, int w, int h) {
            setBounds(x, y, w, h);
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(new Color(0xF2, 0xD2, 0x6A, 150));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }
}
