package petsa.ui;

import petsa.ui.PixelKit.BackgroundPanel;
import petsa.ui.PixelKit.FadePane;
import petsa.ui.PixelKit.WoodButton;

import javax.swing.JLabel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Dimension;

/**
 * The main menu screen: title, New Game / Load Game / Exit buttons,
 * and a Settings gear, over the game's background art. Fixed at
 * 1280x720, matching the reference mockup's stated resolution.
 *
 * Expects the background image at:
 *   src/petsa/ui/resources/menu_background.jpeg
 * Falls back to a plain dark gradient (see BackgroundPanel) until
 * that file is added, so this screen is fully testable before the
 * final art is in place.
 */
public class MainMenuPanel extends BackgroundPanel {

    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    /** Callbacks for the menu's buttons - wired to the real game flow by whoever owns this panel (the eventual MainFrame). */
    public interface Listener {
        void onNewGame();
        void onLoadGame();
        void onExit();
        void onSettings();

        /** The Leaderboard button. Default: do nothing (so older listeners still compile). */
        default void onLeaderboard() {
        }
    }

    public MainMenuPanel(Listener listener) {
        super("resources/menu_background.jpeg");
        setPreferredSize(new Dimension(WIDTH, HEIGHT));

        JLabel title = new JLabel("PETSA DE PELIGRO", SwingConstants.CENTER);
        title.setFont(PixelKit.font(38f));
        title.setForeground(new Color(0xE8, 0xC9, 0x5A));
        title.setBounds(0, 130, WIDTH, 60);
        add(title);

        JLabel subtitle = new JLabel("SURVIVE THE 30-DAY COLLEGE STUDENT BUDGET SIMULATION", SwingConstants.CENTER);
        subtitle.setFont(PixelKit.font(11f));
        subtitle.setForeground(new Color(0xE8, 0xDD, 0xC0));
        subtitle.setBounds(0, 205, WIDTH, 30);
        add(subtitle);

        int buttonWidth = 260;
        int buttonHeight = 58;
        int buttonX = (WIDTH - buttonWidth) / 2;

        WoodButton newGameButton = new WoodButton("NEW GAME", null, WoodButton.GREEN);
        newGameButton.setBounds(buttonX, 297, buttonWidth, buttonHeight);
        newGameButton.addActionListener(e -> FadePane.run(this, listener::onNewGame));
        add(newGameButton);

        WoodButton loadGameButton = new WoodButton("LOAD GAME", null, WoodButton.BLUE);
        loadGameButton.setBounds(buttonX, 369, buttonWidth, buttonHeight);
        loadGameButton.addActionListener(e -> FadePane.run(this, listener::onLoadGame));
        add(loadGameButton);

        WoodButton leaderboardButton = new WoodButton("LEADERBOARD", null, WoodButton.YELLOW);
        leaderboardButton.setBounds(buttonX, 441, buttonWidth, buttonHeight);
        leaderboardButton.addActionListener(e -> FadePane.run(this, listener::onLeaderboard));
        add(leaderboardButton);

        WoodButton exitButton = new WoodButton("EXIT", null, WoodButton.RED);
        exitButton.setBounds(buttonX, 513, buttonWidth, buttonHeight);
        exitButton.addActionListener(e -> FadePane.run(this, listener::onExit));
        add(exitButton);

        // Labelled in words: the pixel font has no gear symbol, so the old "\u2699" showed as a blank box.
        WoodButton settingsButton = new WoodButton("SETTINGS", null, WoodButton.TAN);
        settingsButton.setBounds(30, HEIGHT - 78, 180, 48);
        settingsButton.addActionListener(e -> FadePane.run(this, listener::onSettings));
        add(settingsButton);

        JLabel versionLabel = new JLabel("v1.0 - Academic Term Project | Fixed 1280x720", SwingConstants.CENTER);
        versionLabel.setFont(PixelKit.font(9f));
        versionLabel.setForeground(new Color(0xB0, 0xA8, 0x98));
        versionLabel.setBounds(0, HEIGHT - 30, WIDTH, 20);
        add(versionLabel);
    }
}
