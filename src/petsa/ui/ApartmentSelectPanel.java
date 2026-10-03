package petsa.ui;

import petsa.model.GameState.ApartmentType;
import petsa.ui.PixelKit.BackgroundPanel;
import petsa.ui.PixelKit.FadePane;
import petsa.ui.PixelKit.PaperCard;
import petsa.ui.PixelKit.WoodButton;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.util.function.Consumer;

public class ApartmentSelectPanel extends BackgroundPanel {

    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    private static final Color INK = new Color(0x3B, 0x2A, 0x1C);
    private static final Color GOOD = new Color(0x4E, 0x8A, 0x4A);
    private static final Color BAD = new Color(0xB0, 0x44, 0x3C);
    private static final int CARD_WIDTH = 470;
    private static final int CARD_HEIGHT = 380;
    private static final int LINE_HEIGHT = 30;

    private final WoodButton standardButton;
    private final WoodButton highEndButton;

    public ApartmentSelectPanel(Consumer<ApartmentType> onChosen, Runnable onBack) {
        super("resources/apartment_background.jpeg", "resources/apartment_background.png",
                "resources/menu_background.jpeg", "resources/menu_background.png");
        setPreferredSize(new Dimension(WIDTH, HEIGHT));

        JLabel title = new JLabel("CHOOSE YOUR APARTMENT", SwingConstants.CENTER);
        title.setFont(PixelKit.font(34f));
        title.setForeground(new Color(0xE8, 0xC9, 0x5A));
        title.setBounds(0, 60, WIDTH, 50);
        add(title);

        JLabel subtitle = new JLabel("This choice lasts the whole month.", SwingConstants.CENTER);
        subtitle.setFont(PixelKit.font(12f));
        subtitle.setForeground(new Color(0xE8, 0xDD, 0xC0));
        subtitle.setBounds(0, 122, WIDTH, 24);
        add(subtitle);

        standardButton = new WoodButton("CHOOSE STANDARD", null, WoodButton.GREEN);
        highEndButton = new WoodButton("CHOOSE HIGH-END", null, WoodButton.BLUE);

        add(buildCard(150, "STANDARD", "The default choice", new String[][] {
                {"=", "Standard electricity rate"},
                {"=", "Normal stress build-up"},
                {" ", ""},
                {" ", "A safe, ordinary start."}
        }, standardButton, ApartmentType.STANDARD, onChosen));

        add(buildCard(660, "HIGH-END", "Comfort has a price", new String[][] {
                {"+", "Stress builds up more slowly"},
                {" ", "(except at your part-time job)"},
                {"-", "Much higher electricity bill"},
                {" ", "(you'll only see it on Day 29)"}
        }, highEndButton, ApartmentType.HIGH_END, onChosen));

        WoodButton backButton = new WoodButton("< BACK", null, WoodButton.TAN);
        backButton.setBounds(30, HEIGHT - 80, 130, 50);
        backButton.addActionListener(e -> FadePane.run(this, onBack));
        add(backButton);
    }

    private JPanel buildCard(int x, String name, String tagline, String[][] lines, WoodButton button,
            ApartmentType type, Consumer<ApartmentType> onChosen) {
        PaperCard card = new PaperCard(PaperCard.CREAM, false);
        card.setBounds(x, 175, CARD_WIDTH, CARD_HEIGHT);
        int innerWidth = CARD_WIDTH - 6;

        JLabel header = new JLabel(name, SwingConstants.CENTER);
        header.setFont(PixelKit.font(24f));
        header.setForeground(INK);
        header.setBounds(0, 26, innerWidth, 34);
        card.add(header);

        JLabel sub = new JLabel(tagline, SwingConstants.CENTER);
        sub.setFont(PixelKit.font(10f));
        sub.setForeground(new Color(0x7A, 0x62, 0x48));
        sub.setBounds(0, 68, innerWidth, 20);
        card.add(sub);

        int y = 112;
        for (String[] line : lines) {
            JLabel label = new JLabel(line[0] + " " + line[1]);
            label.setFont(PixelKit.font(10f));
            label.setForeground(line[0].equals("+") ? GOOD : (line[0].equals("-") ? BAD : INK));
            label.setBounds(28, y, innerWidth - 56, LINE_HEIGHT);
            card.add(label);
            y += LINE_HEIGHT;
        }

        button.setBounds(60, CARD_HEIGHT - 110, innerWidth - 120, 60);
        button.addActionListener(e -> choose(type, onChosen));
        card.add(button);
        return card;
    }

    private void choose(ApartmentType type, Consumer<ApartmentType> onChosen) {
        standardButton.setEnabled(false);
        highEndButton.setEnabled(false);
        FadePane.run(this, () -> onChosen.accept(type));
    }
}
