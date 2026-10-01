package petsa.ui;

import petsa.model.DailyLedger.DaySummary;
import petsa.model.DailyLedger.RentBill;
import petsa.model.GameEngine;
import petsa.model.GameState;
import petsa.model.PartTimeJob.ShiftType;
import petsa.model.Player;
import petsa.model.Player.Attribute;
import petsa.model.RandomEvent.PickpocketedEvent;
import petsa.model.RandomEvent.RainyDayLaundryEvent;
import petsa.model.SaveManager;
import petsa.model.Task;
import petsa.model.Task.EatTask;
import petsa.model.Task.HygieneTask;
import petsa.model.Task.StudyTask;
import petsa.model.Task.WalkTask;
import petsa.model.Timeline.TimeSlot;
import petsa.ui.NotebookOverlay.GameOverOverlay;
import petsa.ui.NotebookOverlay.PauseOverlay;
import petsa.ui.PixelKit.BackgroundPanel;
import petsa.ui.PixelKit.FadePane;
import petsa.ui.PixelKit.MoneyLabel;
import petsa.ui.PixelKit.WoodButton;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.util.Locale;

/**
 * The Room screen: the HUD (day, cash, time of day, attributes) plus the
 * six clickable objects - Door, Window, Board, Shelf, Bed, Phone - laid
 * over the room background, all backed by the GameEngine. Every object is
 * a real WoodButton, never an invisible click zone over the artwork. The
 * signs are small plaques beside or on top of the furniture they name, so
 * the room art stays visible.
 *
 * All object positions are the constants just below, so they're easy to
 * nudge if they don't line up with the background art. The two HUD pieces
 * (InfoBox, TimeSlotBar) are small classes at the end of this file.
 *
 * Pictures (in src/petsa/ui/resources/):
 *   room_background_morning / _afternoon / _night / _rainy (.jpeg or .png)
 *   phone_sprite.png (and phone_sprite_cracked.png) - the phone beside the
 *       PHONE sign, clickable too
 *
 * Interaction rules implemented here:
 *  - Board: performs the current slot's task (Eat/Hygiene/Study).
 *    Day 1 is a guided day that assigns a fixed task per slot. Choosing
 *    Hygiene on a rainy day presents the Rainy Day Laundry choice
 *    instead. Also available on Part-Time nights (studying instead of
 *    working is a valid choice).
 *  - Door: a Walk (always available), or on the Night of a part-time
 *    day, a Standard or Overtime shift (or still a walk) - unless
 *    Academics is 65% or lower, which locks the job until grades
 *    recover (only the walk is offered then).
 *  - A new game opens with a welcome tutorial (startWelcomeTutorial())
 *    that explains the HUD, attributes, tasks and room objects.
 *  - Morning and Afternoon actions advance to the next slot
 *    automatically. Night allows ONE action, then waits for the Bed.
 *  - Phone (sign or sprite) opens the PhoneOverlay: Mail & Alerts and
 *    Shop. On a random-event day the day's event is delivered there,
 *    with what each choice does, and must be answered before any task -
 *    trying to act first simply opens the phone. Rainy Day Laundry and
 *    Pickpocketed are shown on the phone too.
 *  - Bed (at Night, once tonight's task is done) ends the day and hands
 *    the DaySummary to the Listener, which shows the End of Day screen.
 *    On Day 30 there is no next day - the summary is marked final.
 *  - Every object click runs inside a quick FadePane fade, except the
 *    Bed, whose screen change already has its own fade.
 */
public class RoomPanel extends BackgroundPanel {

    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    // Object sign positions (x, y, width, height) at 1280x720 - tweak to match the background art.
    private static final Rectangle DOOR_SIGN = new Rectangle(35, 245, 145, 62);
    private static final Rectangle WINDOW_SIGN = new Rectangle(305, 222, 160, 62);
    private static final Rectangle BOARD_SIGN = new Rectangle(618, 112, 136, 60);
    private static final Rectangle SHELF_SIGN = new Rectangle(928, 108, 200, 60);
    private static final Rectangle BED_SIGN = new Rectangle(820, 440, 230, 66);
    private static final Rectangle PHONE_SPRITE = new Rectangle(1020, 596, 56, 90);
    private static final Rectangle PHONE_SIGN = new Rectangle(1085, 616, 150, 58);

    // HUD positions (also used by the tutorial to highlight them).
    private static final Rectangle DAY_CASH_BOX = new Rectangle(20, 15, 260, 80);
    private static final Rectangle TIME_BAR = new Rectangle(340, 22, 520, 50);
    private static final Rectangle ATTRIBUTE_BOX = new Rectangle(890, 15, 285, 80);
    /** The Board sign plus the corkboard in the background art, highlighted together by the tutorial. */
    private static final Rectangle BOARD_AREA = new Rectangle(540, 108, 294, 258);

    /** File names tried for the phone sprite (any of these, with any of the extensions below). */
    private static final String[] PHONE_SPRITE_NAMES = {"phone_sprite", "phone", "Phone", "Phone_Sprite", "PhoneSprite"};
    private static final String[] SPRITE_EXTENSIONS = {"png", "gif", "jpeg", "jpg"};

    /** Screen changes this panel can't do itself - the owner of the window decides what comes next. */
    public interface Listener {
        /** The player went to bed. Show the End of Day screen, then call returnFromEndOfDay() (unless it was the final day). */
        void onDayEnded(DaySummary summary);

        /** RESTART on the Game Over screen. Default: do nothing. */
        default void onRestart() {
        }

        /** EXIT GAME on the Game Over screen. Default: quit the program. */
        default void onQuitGame() {
            System.exit(0);
        }

        /** The player chose Save & Exit (or Exit) from the pause screen. Default: do nothing. */
        default void onExitToTitle() {
        }
    }

    private final GameEngine engine;
    private final Listener listener;

    private final SaveManager saveManager;   // null = saving not available (the pause screen then just exits)
    private final PauseOverlay pauseOverlay;
    private boolean paused = false;
    private final PhoneOverlay phone;          // created once, so today's mail log survives closing the phone
    private JButton phoneSprite;               // null if no phone sprite image was added
    private ImageIcon phoneIcon;               // the sprite as normal ...
    private ImageIcon crackedPhoneIcon;        // ... and with a cracked screen (after declining the Broken Phone repair)
    private WindowOverlay windowView;          // non-null while the window view is showing
    private ShelfOverlay shelfView;            // non-null while the shelf view is showing
    private RentDayOverlay rentDay;            // non-null while the landlord's visit is showing
    private boolean rentDayShown = false;      // the Rent Day scene plays once
    private final BoardOverlay board;
    private boolean boardOpen = false;
    private GameOverOverlay gameOverOverlay;   // non-null once the Game Over screen is showing
    private int alertsSeenDay = -1;            // day whose phone alerts (rent reminder, part-time notice) have been seen
    private boolean phoneOpen = false;
    private boolean gameOverHandled = false;   // the Game Over dialog is shown only once
    private TutorialOverlay tutorialOverlay;  // non-null while the welcome tutorial is showing

    private final JLabel dayLabel = new JLabel();
    private final MoneyLabel cashLabel = new MoneyLabel(20f, new Color(0xF2, 0xC8, 0x4A), MoneyLabel.Align.LEFT);
    private final TimeSlotBar timeSlotBar = new TimeSlotBar();
    private final JLabel hungerLabel = new JLabel();
    private final JLabel stressLabel = new JLabel();
    private final JLabel academicLabel = new JLabel();
    private final JLabel sicknessLabel = new JLabel();

    private final WoodButton doorButton = new WoodButton("DOOR", "Walk", WoodButton.DARK_WOOD);
    private final WoodButton windowButton = new WoodButton("WINDOW", "Sunny Day", WoodButton.DARK_WOOD);
    private final WoodButton boardButton = new WoodButton("BOARD", "Daily Tasks", WoodButton.DARK_WOOD);
    private final WoodButton shelfButton = new WoodButton("SHELF", "Storage & Inventory", WoodButton.DARK_WOOD);
    private final WoodButton bedButton = new WoodButton("BED", "Sleep (Advance Day)", WoodButton.DARK_WOOD);
    private final WoodButton phoneButton = new WoodButton("PHONE", "Apps & Mail", WoodButton.DARK_WOOD);

    /** A room with no save file - the pause screen's second button just exits. */
    public RoomPanel(GameEngine engine, Listener listener) {
        this(engine, listener, null);
    }

    /** A room whose pause screen can Save & Exit into saveManager's file. */
    public RoomPanel(GameEngine engine, Listener listener, SaveManager saveManager) {
        super(backgroundFor(TimeSlot.MORNING, false));
        this.engine = engine;
        this.listener = listener;
        this.saveManager = saveManager;
        setPreferredSize(new Dimension(WIDTH, HEIGHT));

        pauseOverlay = new PauseOverlay(new PauseOverlay.Actions() {
            @Override
            public void onResume() {
                FadePane.run(RoomPanel.this, RoomPanel.this::hidePause);
            }

            @Override
            public void onSaveAndExit() {
                saveAndExit();
            }
        }, saveManager != null);
        phone = new PhoneOverlay(engine, new PhoneOverlay.Host() {
            @Override
            public void onStateChanged() {
                refreshHud();
                if (engine.isGameOver()) {
                    closePhone();
                }
                checkGameOver();
            }

            @Override
            public void onClose() {
                FadePane.run(RoomPanel.this, RoomPanel.this::closePhone);
            }
        });
        board = new BoardOverlay(engine, new BoardOverlay.Actions() {
            @Override
            public void onChoose(BoardOverlay.Choice choice) {
                FadePane.run(RoomPanel.this, () -> onBoardTaskChosen(choice));
            }

            @Override
            public void onClose() {
                FadePane.run(RoomPanel.this, RoomPanel.this::closeBoard);
            }
        });
        installEscapeKey();

        buildHud();
        buildObjects();
        refreshHud();
    }

    /** Candidate background images for a time slot, best match first. */
    /**
     * Candidate background images for a time slot and weather, best match first.
     * Rain has its own art in the Morning and Mid-day (room_background_rainy_morning /
     * _rainy_afternoon, or one room_background_rainy for both); at Night, and when no
     * rainy art has been added, the normal image for the time of day is used.
     */
    private static String[] backgroundFor(TimeSlot slot, boolean rainy) {
        String[] normal = backgroundFor(slot);
        if (!rainy || slot == TimeSlot.NIGHT) {
            return normal;
        }
        String name = slot == TimeSlot.AFTERNOON ? "afternoon" : "morning";
        String[] rainyFirst = {
                "resources/room_background_rainy_" + name + ".jpeg",
                "resources/room_background_rainy_" + name + ".png",
                "resources/room_background_rainy.jpeg",
                "resources/room_background_rainy.png"
        };
        String[] all = new String[rainyFirst.length + normal.length];
        System.arraycopy(rainyFirst, 0, all, 0, rainyFirst.length);
        System.arraycopy(normal, 0, all, rainyFirst.length, normal.length);
        return all;
    }

    private static String[] backgroundFor(TimeSlot slot) {
        String name;
        switch (slot) {
            case AFTERNOON:
                name = "afternoon";
                break;
            case NIGHT:
                name = "night";
                break;
            default:
                name = "morning";
                break;
        }
        return new String[] {
                "resources/room_background_" + name + ".jpeg",
                "resources/room_background_" + name + ".png",
                "resources/room_background.jpeg",
                "resources/room_background.png"
        };
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private void buildHud() {
        InfoBox dayCashBox = new InfoBox();
        dayCashBox.setBounds(DAY_CASH_BOX);
        dayLabel.setFont(PixelKit.font(16f));
        dayLabel.setForeground(new Color(0xF0, 0xE6, 0xD2));
        dayLabel.setBounds(14, 12, 240, 24);
        dayCashBox.add(dayLabel);
        cashLabel.setBounds(14, 42, 240, 30);
        dayCashBox.add(cashLabel);
        add(dayCashBox);

        timeSlotBar.setBounds(TIME_BAR);
        add(timeSlotBar);

        InfoBox attributeBox = new InfoBox();
        attributeBox.setBounds(ATTRIBUTE_BOX);
        styleAttributeLabel(hungerLabel, 12, 12);
        styleAttributeLabel(stressLabel, 150, 12);
        styleAttributeLabel(academicLabel, 12, 44);
        styleAttributeLabel(sicknessLabel, 150, 44);
        attributeBox.add(hungerLabel);
        attributeBox.add(stressLabel);
        attributeBox.add(academicLabel);
        attributeBox.add(sicknessLabel);
        add(attributeBox);

        WoodButton pauseButton = new WoodButton("PAUSE", null, WoodButton.DARK_WOOD);
        pauseButton.setBounds(1185, 28, 82, 42);
        pauseButton.addActionListener(e -> FadePane.run(this, this::showPause));
        makeClickable(pauseButton);
        add(pauseButton);
    }

    private void styleAttributeLabel(JLabel label, int x, int y) {
        label.setFont(PixelKit.font(9f));
        label.setBounds(x, y, 136, 24);
    }

    private void buildObjects() {
        placeObject(doorButton, DOOR_SIGN, this::onDoor);
        placeObject(windowButton, WINDOW_SIGN, this::onWindow);
        placeObject(boardButton, BOARD_SIGN, this::onBoard);
        placeObject(shelfButton, SHELF_SIGN, this::onShelf);
        placeObject(phoneButton, PHONE_SIGN, this::onPhone);

        // The Bed changes screens, which has its own fade - so it isn't wrapped in the quick button fade.
        bedButton.setBounds(BED_SIGN);
        bedButton.addActionListener(e -> onBed());
        makeClickable(bedButton);
        add(bedButton);

        phoneSprite = buildPhoneSprite();
        if (phoneSprite != null) {
            add(phoneSprite);
        }

        JLabel tip = new JLabel("Tip: Use the Board or Door for each part of the day, then sleep at Night.");
        tip.setFont(PixelKit.font(9f));
        tip.setForeground(new Color(0xF2, 0xD8, 0x8A));
        tip.setBounds(20, HEIGHT - 30, 900, 22);
        add(tip);
    }

    private void placeObject(WoodButton button, Rectangle bounds, Runnable action) {
        button.setBounds(bounds);
        button.addActionListener(e -> FadePane.run(this, action));
        makeClickable(button);
        add(button);
    }

    private void makeClickable(AbstractButton button) {
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /**
     * The phone sprite beside the PHONE sign: a real JButton showing the
     * sprite image (scaled with nearest-neighbor so pixel art stays
     * crisp), which opens the phone just like the sign does. Returns
     * null if phone_sprite.png hasn't been added yet.
     */
    private JButton buildPhoneSprite() {
        Image image = findPhoneSprite();
        if (image == null) {
            return null;
        }
        double scale = Math.min((double) PHONE_SPRITE.width / image.getWidth(null),
                (double) PHONE_SPRITE.height / image.getHeight(null));
        int w = Math.max(1, (int) (image.getWidth(null) * scale));
        int h = Math.max(1, (int) (image.getHeight(null) * scale));
        Image scaled = image.getScaledInstance(w, h, Image.SCALE_REPLICATE);
        phoneIcon = new ImageIcon(scaled);
        Image cracked = findCrackedPhoneSprite();
        crackedPhoneIcon = new ImageIcon(cracked != null
                ? cracked.getScaledInstance(w, h, Image.SCALE_REPLICATE)
                : drawCracksOn(phoneIcon.getImage(), w, h));

        JButton sprite = new JButton(phoneIcon);
        sprite.setContentAreaFilled(false);
        sprite.setBorderPainted(false);
        sprite.setFocusPainted(false);
        sprite.setOpaque(false);
        sprite.setToolTipText("Phone");
        sprite.setBounds(PHONE_SPRITE.x + (PHONE_SPRITE.width - w) / 2,
                PHONE_SPRITE.y + (PHONE_SPRITE.height - h), w, h);
        sprite.addActionListener(e -> FadePane.run(this, this::onPhone));
        makeClickable(sprite);
        return sprite;
    }

    /** Looks for a cracked-screen version of the phone sprite; null if none was added. */
    private Image findCrackedPhoneSprite() {
        for (String name : new String[] {"phone_sprite_cracked", "phone_cracked", "Phone_Sprite_Cracked"}) {
            for (String extension : SPRITE_EXTENSIONS) {
                Image image = PixelKit.loadImage("resources/" + name + "." + extension);
                if (image != null && image.getWidth(null) > 0) {
                    return image;
                }
            }
        }
        return null;
    }

    /**
     * If no cracked sprite image was added, makes one by drawing a few
     * pixel-style crack lines over the normal sprite's screen area.
     */
    private static Image drawCracksOn(Image sprite, int w, int h) {
        java.awt.image.BufferedImage copy = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = copy.createGraphics();
        g.drawImage(sprite, 0, 0, null);
        int ix = w * 3 / 4;
        int iy = h / 5;
        int[][] cracks = {
                {0, 0, -w / 5, h / 8, -w / 3, h / 4, -w / 2, h / 3},
                {0, 0, -w / 12, h / 5, -w / 20, h / 2 - h / 8},
                {0, 0, w / 10, h / 7},
                {0, 0, -w / 4, -h / 30, -w / 2, h / 40},
        };
        g.setColor(new Color(255, 255, 255, 210));
        for (int[] crack : cracks) {
            for (int i = 0; i + 3 < crack.length; i += 2) {
                g.drawLine(ix + crack[i], iy + crack[i + 1], ix + crack[i + 2], iy + crack[i + 3]);
            }
        }
        g.fillRect(ix - 1, iy - 1, 3, 3);
        g.dispose();
        return copy;
    }

    /** Looks for the phone sprite under the accepted names/extensions, and reports clearly if it can't be used. */
    private Image findPhoneSprite() {
        for (String name : PHONE_SPRITE_NAMES) {
            for (String extension : SPRITE_EXTENSIONS) {
                String path = "resources/" + name + "." + extension;
                Image image = PixelKit.loadImage(path);
                if (image == null) {
                    continue;
                }
                if (image.getWidth(null) > 0 && image.getHeight(null) > 0) {
                    return image;
                }
                System.err.println("RoomPanel: found " + path + " but Java couldn't read it. "
                        + "Re-export it as a real PNG (a renamed WebP file won't load).");
            }
        }
        System.err.println("RoomPanel: no phone sprite found. Save it as src/petsa/ui/resources/phone_sprite.png "
                + "(also accepted: phone.png, .gif, .jpeg, .jpg), then Clean and Build so it gets copied.");
        return null;
    }

    // ------------------------------------------------------------------
    // HUD refresh
    // ------------------------------------------------------------------

    /** Re-reads the engine and updates the HUD, object subtitles and background. Call after any action that changes state. */
    private void refreshHud() {
        GameState state = engine.getState();
        Player player = state.getPlayer();

        dayLabel.setText("DAY " + state.getDay() + " / " + GameEngine.FINAL_DAY);
        cashLabel.setAmount(player.getCash());
        timeSlotBar.setCurrentSlot(state.getTimeSlot());
        setBackgroundCandidates(backgroundFor(state.getTimeSlot(), engine.isTodayRainy()));

        updateAttributeLabel(hungerLabel, "Hunger", player.getHunger());
        updateAttributeLabel(stressLabel, "Stress", player.getStress());
        updateAttributeLabel(academicLabel, "Academics", player.getAcademic());
        updateAttributeLabel(sicknessLabel, "Sickness", player.getSickness());

        if (engine.isPartTimeSlotNow()) {
            doorButton.setSubtitle(player.isPartTimeLocked() ? "Job locked" : "Part-Time Job");
        } else {
            doorButton.setSubtitle("Walk");
        }
        windowButton.setSubtitle(engine.isTodayRainy() ? "Rainy Day" : "Sunny Day");
        boolean reminderUnread = hasPhoneAlertsToday() && alertsSeenDay != state.getDay();
        String phoneStatus = engine.isPhoneBroken() ? "Cracked screen" : "Apps & Mail";
        phoneButton.setSubtitle(hasUnreadMail() ? "1 new mail!" : (reminderUnread ? "1 new alert" : phoneStatus));
        if (phoneSprite != null) {
            phoneSprite.setIcon(engine.isPhoneBroken() ? crackedPhoneIcon : phoneIcon);
        }
    }

    private void updateAttributeLabel(JLabel label, String name, Attribute attribute) {
        label.setText(name + ": " + attribute.getValue() + "%");
        label.setForeground(zoneColor(attribute.getZone()));
    }

    private Color zoneColor(Attribute.Zone zone) {
        switch (zone) {
            case GREEN:
                return new Color(0x6C, 0xC6, 0x6C);
            case YELLOW:
                return new Color(0xE0, 0xC5, 0x4A);
            default:
                return new Color(0xE0, 0x62, 0x62);
        }
    }

    // ------------------------------------------------------------------
    // Object interactions
    // ------------------------------------------------------------------

    /** Opens the view out the window: Morning, Mid-day, Night or Rainy, depending on the time and weather. */
    private void onWindow() {
        if (windowView != null) {
            return;
        }
        WindowOverlay view = new WindowOverlay(() -> FadePane.run(this, this::closeWindow));
        view.showView(WindowOverlay.viewFor(engine.getState().getTimeSlot(), engine.isTodayRainy()));
        if (attachOverlay(view)) {
            windowView = view;
        }
    }

    private void closeWindow() {
        if (windowView != null) {
            windowView.stopAnimation();
            detachOverlay(windowView);
            windowView = null;
        }
    }

    /** Opens the shelf view: food and medicine stock, with a shortcut to the phone's Shop. */
    private void onShelf() {
        if (shelfView != null) {
            return;
        }
        ShelfOverlay view = new ShelfOverlay(engine.getState().getPlayer(), new ShelfOverlay.Actions() {
            @Override
            public void onBuySupplies() {
                FadePane.run(RoomPanel.this, () -> {
                    closeShelf();
                    if (showPhoneOverlay()) {
                        phone.openShop();
                    }
                });
            }

            @Override
            public void onClose() {
                FadePane.run(RoomPanel.this, RoomPanel.this::closeShelf);
            }
        });
        if (attachOverlay(view)) {
            shelfView = view;
        }
    }

    private void closeShelf() {
        if (shelfView != null) {
            detachOverlay(shelfView);
            shelfView = null;
        }
    }

    private void onBoard() {
        if (!canActNow()) {
            return;
        }
        if (attachOverlay(board)) {
            board.refresh();
            boardOpen = true;
        }
    }

    private void closeBoard() {
        if (boardOpen) {
            detachOverlay(board);
            boardOpen = false;
        }
    }

    /** A note was picked on the Board: do that task for the current part of the day. */
    private void onBoardTaskChosen(BoardOverlay.Choice choice) {
        closeBoard();
        TimeSlot slot = engine.getState().getTimeSlot();
        if (choice == BoardOverlay.Choice.HYGIENE && engine.isTodayRainy()) {
            // On a rainy day, showering is replaced by the Rainy Day Laundry event, shown on the phone.
            RainyDayLaundryEvent laundry = new RainyDayLaundryEvent();
            openPhoneAlert(PhoneOverlay.Message.fromEvent(laundry, "ALERT", null, paid -> {
                engine.performRainyDayLaundry(laundry, paid);
                finishSlot(slot);
            }));
            return;
        }
        Task task;
        switch (choice) {
            case EAT:
                task = new EatTask();
                break;
            case HYGIENE:
                task = new HygieneTask();
                break;
            default:
                task = new StudyTask();
                break;
        }
        engine.performTask(task);
        warnIfBlockedByStress(task);
        finishSlot(slot);
    }

    private void onDoor() {
        if (!canActNow()) {
            return;
        }
        TimeSlot slot = engine.getState().getTimeSlot();

        if (engine.isPartTimeSlotNow() && engine.getState().getPlayer().isPartTimeLocked()) {
            boolean walk = GameDialog.confirm(this, "Job Locked",
                    "Your Academic Performance is 65% or lower, so the part-time job won't take you tonight. "
                            + "Study to bring it back up. Take a walk instead?",
                    "TAKE A WALK", "STAY IN", GameDialog.Tone.INFO);
            if (!walk) {
                return; // nothing happens - the Night slot is still free
            }
            engine.performTask(new WalkTask());
        } else if (engine.isPartTimeSlotNow()) {
            // A walk is offered here too: walks are always available, and at 100% Stress it's the only
            // thing a player can do tonight (the Board's tasks are blocked).
            int choice = GameDialog.choose(this, "Part-Time Job",
                    "Work a shift tonight? It uses up your Night.\n"
                            + "STANDARD: +" + PixelKit.peso(ShiftType.STANDARD.getEarnings()) + ", some Stress.\n"
                            + "OVERTIME: +" + PixelKit.peso(ShiftType.OVERTIME.getEarnings()) + ", a LOT of Stress.\n"
                            + "Or take a walk instead to calm down.",
                    new String[] {"STANDARD +" + PixelKit.peso(ShiftType.STANDARD.getEarnings()),
                            "OVERTIME +" + PixelKit.peso(ShiftType.OVERTIME.getEarnings()), "TAKE A WALK"},
                    new Color[] {WoodButton.GREEN, WoodButton.ORANGE, WoodButton.TAN}, "NOT TONIGHT");
            if (choice == 2) {
                engine.performTask(new WalkTask());
                finishSlot(slot);
                return;
            }
            if (choice != 0 && choice != 1) {
                return; // dialog dismissed - the Night is still free
            }
            ShiftType shift = (choice == 0) ? ShiftType.STANDARD : ShiftType.OVERTIME;
            PickpocketedEvent pickpocketed = engine.workPartTime(shift);
            finishSlot(slot);
            if (pickpocketed != null && !engine.isGameOver()) {
                // It has already happened - the phone just shows what it cost, with an OKAY button.
                openPhoneAlert(PhoneOverlay.Message.fromEvent(pickpocketed, "ALERT", null, ok -> { }));
            }
            return;
        } else {
            engine.performTask(new WalkTask());
        }
        finishSlot(slot);
    }

    private void onBed() {
        if (engine.isGameOver()) {
            return;
        }
        if (engine.getState().getTimeSlot() != TimeSlot.NIGHT) {
            GameDialog.showMessage(this, "Bed",
                    "Finish your Morning and Mid-day first (Board or Door) before going to bed.");
            return;
        }
        if (hasUnreadMail()) {
            openPhone(); // today's message must be answered before anything else
            return;
        }
        if (!engine.isNightActionUsed()) {
            // The Bed only opens once tonight's task is done. There's always one to do: at least one
            // Board task is left at Night (three tasks, two earlier slots), and the Door always offers a walk.
            GameDialog.showMessage(this, "Bed",
                    "Do tonight's task first (Board or Door) before going to bed.");
            return;
        }
        DaySummary summary = engine.endDay();
        if (listener != null) {
            listener.onDayEnded(summary);
        }
    }

    /**
     * Call when the player leaves the End of Day screen and comes back
     * to this room: refreshes everything for the new day and shows the
     * Game Over dialog if the overnight charges (e.g. Day 29's rent)
     * pushed cash below zero.
     */
    public void returnFromEndOfDay() {
        RentBill bill = engine.getRentBill();
        if (bill != null && !rentDayShown && engine.getState().getDay() == GameState.RENT_DUE_DAY) {
            showRentDay(bill);
            return;
        }
        checkStatus();
    }

    /**
     * The landlord's visit, first thing on Day 29 (the rent and bills were
     * charged as the day began). Only once it's dismissed does the room
     * check for Game Over - so a player the rent leaves broke sees the bill
     * that did it first.
     */
    private void showRentDay(RentBill bill) {
        rentDayShown = true;
        refreshHud();
        rentDay = new RentDayOverlay(bill, () -> FadePane.run(this, () -> {
            if (rentDay != null) {
                detachOverlay(rentDay);
                rentDay = null;
            }
            checkStatus();
        }));
        if (!attachOverlay(rentDay)) {
            rentDay = null;
            checkStatus();
        }
    }

    /**
     * Refreshes the HUD and shows the Game Over screen if the player is
     * broke. Called when a loaded game's room first appears, so a save
     * made with no money left ends properly instead of leaving the player
     * stuck in the room.
     */
    public void checkStatus() {
        refreshHud();
        checkGameOver();
    }

    private void onPhone() {
        openPhone();
    }

    /** Shows the phone on its Mail tab (delivering today's event if it hasn't been read yet). */
    private void openPhone() {
        if (!showPhoneOverlay()) {
            return;
        }
        if (hasPhoneAlertsToday()) {
            alertsSeenDay = engine.getState().getDay(); // the alerts are on the Mail tab
        }
        phone.openHome();
    }

    /** True on days the Mail tab has an alert: the end-of-week rent reminder or the part-time job notice. */
    private boolean hasPhoneAlertsToday() {
        return engine.isRentReminderDay() || engine.isPartTimeDay();
    }

    /** Shows the phone with an alert that must be answered (Rainy Day Laundry, Pickpocketed). */
    private void openPhoneAlert(PhoneOverlay.Message alert) {
        if (!showPhoneOverlay()) {
            alert.onAnswer.accept(true); // no window to show it in - never leave the game stuck
            return;
        }
        phone.openAlert(alert);
    }

    /** Adds the phone to the window's layered pane, above the room. Returns false if it can't be shown. */
    private boolean showPhoneOverlay() {
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root == null || engine.isGameOver()) {
            return false;
        }
        if (!phoneOpen) {
            JLayeredPane layers = root.getLayeredPane();
            phone.setBounds(SwingUtilities.convertRectangle(this, new Rectangle(0, 0, getWidth(), getHeight()), layers));
            layers.add(phone, JLayeredPane.MODAL_LAYER);
            layers.revalidate();
            layers.repaint();
            phoneOpen = true;
        }
        return true;
    }

    private void closePhone() {
        if (!phoneOpen) {
            return;
        }
        JLayeredPane layers = (JLayeredPane) phone.getParent();
        if (layers != null) {
            layers.remove(phone);
            layers.repaint();
        }
        phoneOpen = false;
        refreshHud();
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /** True on a random-event day whose mail hasn't been resolved yet. */
    private boolean hasUnreadMail() {
        return engine.isRandomEventPendingToday();
    }

    /**
     * Guards Board/Door: no actions after game over, unread mail must be
     * read first, and the Night slot allows only one action.
     */
    private boolean canActNow() {
        if (engine.isGameOver()) {
            return false;
        }
        if (hasUnreadMail()) {
            openPhone(); // today's message must be answered before anything else
            return false;
        }
        if (engine.isNightActionUsed()) {
            GameDialog.showMessage(this, "Night", "You've already used tonight. Click the Bed to sleep.");
            return false;
        }
        return true;
    }

    private void warnIfBlockedByStress(Task task) {
        Player player = engine.getState().getPlayer();
        if (!(task instanceof WalkTask) && player.isIncapacitatedByStress()) {
            GameDialog.showMessage(this, "Stress",
                    "You're too stressed to function - the " + task.getName() + " didn't happen.",
                    GameDialog.Tone.WARNING);
        }
    }

    /** After a slot's action: Morning/Afternoon advance automatically; Night is marked used and waits for the Bed. */
    private void finishSlot(TimeSlot slotJustCompleted) {
        if (slotJustCompleted == TimeSlot.NIGHT) {
            engine.markNightActionUsed();
        } else {
            engine.advanceTimeSlot();
        }
        refreshHud();
        checkGameOver();
    }

    // ------------------------------------------------------------------
    // Welcome tutorial (new games only)
    // ------------------------------------------------------------------

    /**
     * Shows the welcome tutorial over the room: a greeting, then what the
     * HUD and the four attributes mean, then which task looks after which
     * attribute, then the Door, Phone, Shelf and Bed. Called by the window
     * right after a new game's room appears; loaded games skip it.
     */
    public void startWelcomeTutorial() {
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root == null || tutorialOverlay != null) {
            return;
        }
        tutorialOverlay = new TutorialOverlay(buildTutorialPages(),
                () -> FadePane.run(this, this::closeTutorial));
        JLayeredPane layers = root.getLayeredPane();
        tutorialOverlay.setBounds(SwingUtilities.convertRectangle(this,
                new Rectangle(0, 0, getWidth(), getHeight()), layers));
        layers.add(tutorialOverlay, JLayeredPane.MODAL_LAYER);
        layers.revalidate();
        layers.repaint();
    }

    private void closeTutorial() {
        if (tutorialOverlay == null) {
            return;
        }
        JLayeredPane layers = (JLayeredPane) tutorialOverlay.getParent();
        if (layers != null) {
            layers.remove(tutorialOverlay);
            layers.repaint();
        }
        tutorialOverlay = null;
    }

    /**
     * The tutorial's pages. Every rule stated here matches what the game
     * actually does - if a rule or number changes, update the text too.
     */
    private java.util.List<TutorialOverlay.Page> buildTutorialPages() {
        java.util.List<TutorialOverlay.Page> pages = new java.util.ArrayList<>();
        Rectangle[] none = {};

        pages.add(new TutorialOverlay.Page("WELCOME TO PETSA DE PELIGRO!", TutorialOverlay.BoxPlacement.CENTER, none,
                "You're a college student living in the city, and your",
                "5,000-peso allowance has to last the whole month: 30 days.",
                "On Day 29 the landlord collects rent plus the electricity",
                "and water bills. On Day 30 you need 500 pesos to get home.",
                "Run out of money and it's game over. Let's look around!"));

        pages.add(new TutorialOverlay.Page("YOUR DAY AND YOUR MONEY", TutorialOverlay.BoxPlacement.BOTTOM,
                new Rectangle[] {DAY_CASH_BOX, TIME_BAR},
                "Top left: today's date and the cash you have left.",
                "Top middle: the time of day. Every day has three parts - Morning, Mid-day and Night -",
                "and you can do ONE thing in each part. Choose wisely!"));

        pages.add(new TutorialOverlay.Page("YOUR ATTRIBUTES", TutorialOverlay.BoxPlacement.BOTTOM,
                new Rectangle[] {ATTRIBUTE_BOX},
                "Top right: your four attributes. Green is fine, yellow is a warning, red is danger.",
                "HUNGER    - Eat to keep it up. If it hits 0%, you collapse: a 500-peso medical bill.",
                "STRESS    - Hard work and bad luck raise it. At 100% you can only walk it off that day.",
                "ACADEMICS - Study to keep it up. At 65% or lower, the part-time job won't take you.",
                "SICKNESS  - Showers keep it down; skipping them or going out in the rain pushes it up.",
                "            At 100% you fall ill: 1-3 medicine pills, or a 500-peso bill if you have none."));

        pages.add(new TutorialOverlay.Page("THE BOARD: YOUR DAILY TASKS", TutorialOverlay.BoxPlacement.BOTTOM,
                new Rectangle[] {BOARD_AREA},
                "Each part of the day, pick one task from the Board to look after an attribute:",
                "EAT     - Refills Hunger. Costs 70 pesos (or one day of bulk food from your shelf).",
                "HYGIENE - A shower lowers Sickness, but adds to the water bill you pay on Day 29.",
                "STUDY   - Raises Academics, but adds a little Stress. It's free.",
                "Skip a task for a whole day and its attribute slips overnight: no food lowers Hunger,",
                "no study lowers Academics, and no shower raises Sickness. Balance needs and budget!"));

        pages.add(new TutorialOverlay.Page("THE DOOR", TutorialOverlay.BoxPlacement.BOTTOM,
                new Rectangle[] {DOOR_SIGN},
                "WALK - Head outside to lower Stress. It uses up that part of the day - and in the rain,",
                "  going out makes you sick.",
                "PART-TIME JOB - On the Nights of Days 7, 14, 21, 24 and 28 the Door leads to work:",
                "  Standard shift: +500 pesos, some stress.   Overtime: +750 pesos, a LOT of stress.",
                "Working with Stress at 100% is a bad idea - you might get pickpocketed on the way home."));

        pages.add(new TutorialOverlay.Page("PHONE, SHELF AND BED", TutorialOverlay.BoxPlacement.BOTTOM_LEFT,
                new Rectangle[] {PHONE_SPRITE.union(PHONE_SIGN), SHELF_SIGN, BED_SIGN},
                "PHONE: Mail brings surprise events - when it says",
                "'1 new mail!', read it before doing anything else.",
                "Shopping sells medicine and bulk food.",
                "SHELF: shows the medicine and food you've stocked.",
                "BED: after tonight's task, sleep to end the day.",
                "PAUSE (or Esc) lets you save and quit at any time."));

        pages.add(new TutorialOverlay.Page("YOUR FIRST DAY", TutorialOverlay.BoxPlacement.CENTER, none,
                "Today is a guided day: the Board will hand you a set task",
                "for each part of the day - Hygiene, then Eat, then Study.",
                "From tomorrow on, every choice is yours. Good luck!"));
        return pages;
    }

    // ------------------------------------------------------------------
    // Pause screen
    // ------------------------------------------------------------------

    /** Esc toggles the pause screen while this room is the screen being shown. */
    private void installEscapeKey() {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "togglePause");
        getActionMap().put("togglePause", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (tutorialOverlay != null || gameOverOverlay != null || rentDay != null) {
                    return; // Esc does nothing during the tutorial or after Game Over
                }
                if (boardOpen) {
                    FadePane.run(RoomPanel.this, RoomPanel.this::closeBoard);
                    return;
                }
                if (windowView != null) {
                    FadePane.run(RoomPanel.this, RoomPanel.this::closeWindow);
                    return;
                }
                if (shelfView != null) {
                    FadePane.run(RoomPanel.this, RoomPanel.this::closeShelf);
                    return;
                }
                if (phoneOpen) {
                    if (phone.isClosable()) {
                        FadePane.run(RoomPanel.this, RoomPanel.this::closePhone);
                    }
                    return; // a message waiting for an answer keeps the phone open
                }
                FadePane.run(RoomPanel.this, paused ? RoomPanel.this::hidePause : RoomPanel.this::showPause);
            }
        });
    }

    /**
     * Shows the pause screen on the window's layered pane, above the room
     * and below the fade layer - so it covers every room button and the
     * room underneath can't be clicked.
     */
    private void showPause() {
        JRootPane root = SwingUtilities.getRootPane(this);
        if (paused || tutorialOverlay != null || phoneOpen || boardOpen || windowView != null || shelfView != null
                || rentDay != null || root == null || engine.isGameOver()) {
            return;
        }
        GameState state = engine.getState();
        pauseOverlay.update(state.getDay(), GameEngine.FINAL_DAY, state.getTimeSlot(), state.getPlayer().getCash());
        pauseOverlay.setButtonsEnabled(true);

        JLayeredPane layers = root.getLayeredPane();
        pauseOverlay.setBounds(SwingUtilities.convertRectangle(this, new Rectangle(0, 0, getWidth(), getHeight()), layers));
        layers.add(pauseOverlay, JLayeredPane.MODAL_LAYER);
        layers.revalidate();
        layers.repaint();
        paused = true;
        pauseOverlay.focusResume();
    }

    private void hidePause() {
        if (!paused) {
            return;
        }
        JLayeredPane layers = (JLayeredPane) pauseOverlay.getParent();
        if (layers != null) {
            layers.remove(pauseOverlay);
            layers.repaint();
        }
        paused = false;
    }

    /**
     * Writes a checkpoint for the current day and time of day (overwriting
     * any older checkpoint for that same point), then returns to the
     * title screen. If saving fails, the player stays paused and is told
     * why, so no progress is lost silently.
     */
    private void saveAndExit() {
        pauseOverlay.setButtonsEnabled(false);
        if (!saveNow()) {
            pauseOverlay.setButtonsEnabled(true);
            return;
        }
        FadePane.run(this, () -> {
            hidePause();
            if (listener != null) {
                listener.onExitToTitle();
            }
        });
    }

    /**
     * Writes a checkpoint for the current day and time of day (weather
     * included). Returns true if it was saved (or there's no save file to
     * write to); on failure the player is told why and it returns false.
     * Also used by GameApp when the window is closed mid-game.
     */
    public boolean saveNow() {
        if (saveManager == null) {
            return true;
        }
        try {
            saveManager.saveCheckpoint(engine.captureSnapshot());
            return true;
        } catch (IOException ex) {
            GameDialog.showMessage(this, "Save Failed", "Couldn't save your game:\n" + ex.getMessage(),
                    GameDialog.Tone.ERROR);
            return false;
        }
    }

    /** If the player has gone broke, closes anything open and shows the Game Over screen (once). */
    private void checkGameOver() {
        if (!engine.isGameOver() || gameOverHandled) {
            return;
        }
        gameOverHandled = true;
        closeBoard();
        closePhone();
        hidePause();
        gameOverOverlay = new GameOverOverlay(new GameOverOverlay.Actions() {
            @Override
            public void onRestart() {
                if (listener != null) {
                    listener.onRestart();
                }
            }

            @Override
            public void onExitGame() {
                saveLostDay();
                if (listener != null) {
                    listener.onQuitGame();
                } else {
                    System.exit(0);
                }
            }
        });
        GameState state = engine.getState();
        gameOverOverlay.update(state.getDay(), GameEngine.FINAL_DAY, state.getPlayer().getCash(), engine.isStranded());
        if (attachOverlay(gameOverOverlay)) {
            gameOverOverlay.focusRestart();
        }
    }

    /**
     * Before quitting from Game Over, saves the day and time the player went
     * broke as a checkpoint, so the Load Game screen shows when the run was
     * lost (loading it shows the Game Over screen again). If saving fails,
     * the game still quits - there's no progress to lose at this point.
     */
    private void saveLostDay() {
        if (saveManager == null) {
            return;
        }
        try {
            saveManager.saveCheckpoint(engine.captureSnapshot());
        } catch (IOException ex) {
            System.err.println("Couldn't save the Game Over checkpoint: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Overlay plumbing
    // ------------------------------------------------------------------

    /** Shows a full-screen overlay on the window's layered pane, above the room. Returns false if there's no window. */
    private boolean attachOverlay(JComponent overlay) {
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root == null) {
            return false;
        }
        JLayeredPane layers = root.getLayeredPane();
        overlay.setBounds(SwingUtilities.convertRectangle(this, new Rectangle(0, 0, getWidth(), getHeight()), layers));
        layers.add(overlay, JLayeredPane.MODAL_LAYER);
        layers.revalidate();
        layers.repaint();
        return true;
    }

    private void detachOverlay(JComponent overlay) {
        java.awt.Container parent = overlay.getParent();
        if (parent != null) {
            parent.remove(overlay);
            parent.repaint();
        }
    }

    /**
     * When the room leaves the window (another screen is shown), forget any
     * overlay that was open. GameApp.show() has already taken the overlays
     * off the window before swapping screens.
     *
     * Deliberately does NOT remove anything from the window's layered pane:
     * Swing calls this in the middle of removing the room from that same
     * layered pane, and changing it here crashed the screen swap (and froze
     * the game) when restarting from the Game Over screen.
     */
    @Override
    public void removeNotify() {
        boardOpen = false;
        phoneOpen = false;
        paused = false;
        tutorialOverlay = null;
        if (windowView != null) {
            windowView.stopAnimation();
            windowView = null;
        }
        shelfView = null;
        rentDay = null;
        super.removeNotify();
    }

    // ------------------------------------------------------------------
    // HUD pieces
    // ------------------------------------------------------------------

    /**
     * A small rounded, see-through dark panel that groups HUD text (the
     * Day/Cash cluster, the attribute readout) over the room background.
     * Only a background - the labels are added on top with setBounds.
     */
    private static class InfoBox extends JPanel {

        private static final Color FILL = new Color(20, 16, 14, 190);
        private static final Color BORDER = new Color(60, 50, 40, 220);
        private static final int ARC = 15; // a multiple of PixelKit.SCALE keeps the corners symmetric

        InfoBox() {
            setLayout(null);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth();
            int h = getHeight();
            int px = PixelKit.SCALE;
            int artW = PixelKit.snap(w);
            int artH = PixelKit.snap(h);
            PixelKit.paint(g2, 0, 0, w, h, pg -> {
                pg.setColor(BORDER);
                pg.fill(new RoundRectangle2D.Float(0, 0, artW, artH, ARC, ARC));
                // Src replaces the border pixels underneath instead of blending the see-through fill over them.
                pg.setComposite(AlphaComposite.Src);
                pg.setColor(FILL);
                pg.fill(new RoundRectangle2D.Float(px, px, artW - px * 2, artH - px * 2, ARC - px * 2, ARC - px * 2));
            });
            g2.dispose();
        }
    }

    /**
     * The "MORNING -> MID-DAY -> NIGHT" strip at the top: three labelled
     * segments, the current one lit. Only a display - the slot moves on when
     * the player acts (or sleeps at Night).
     */
    private static class TimeSlotBar extends JPanel {

        private static final Color INACTIVE_FILL = new Color(40, 35, 30);
        private static final Color MORNING_FILL = new Color(0xD8, 0x8A, 0x4A);
        private static final Color AFTERNOON_FILL = new Color(0xC9, 0xA8, 0x3A);
        private static final Color NIGHT_FILL = new Color(0x3A, 0x3A, 0x7A);
        private static final Color BORDER = new Color(60, 50, 40);
        private static final Color LABEL_COLOR = new Color(235, 225, 210);

        private TimeSlot currentSlot = TimeSlot.MORNING;

        TimeSlotBar() {
            setOpaque(false);
        }

        void setCurrentSlot(TimeSlot slot) {
            this.currentSlot = slot;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth();
            int h = getHeight();
            int gap = 9; // a multiple of PixelKit.SCALE keeps the three segments evenly spaced
            int segmentWidth = PixelKit.snap((w - gap * 2) / 3);
            int artH = PixelKit.snap(h);
            int px = PixelKit.SCALE;
            int[] xs = {0, segmentWidth + gap, (segmentWidth + gap) * 2};
            TimeSlot[] slots = TimeSlot.values();

            // Segment shapes are pixelated; labels are drawn afterwards at full size so they stay sharp.
            PixelKit.paint(g2, 0, 0, w, h, pg -> {
                for (int i = 0; i < slots.length; i++) {
                    pg.setColor(BORDER);
                    pg.fill(new RoundRectangle2D.Float(xs[i], 0, segmentWidth, artH, 12, 12));
                    pg.setColor(fillFor(slots[i]));
                    pg.fill(new RoundRectangle2D.Float(xs[i] + px, px, segmentWidth - px * 2, artH - px * 2, 6, 6));
                }
            });

            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(PixelKit.font(9f));
            g2.setColor(LABEL_COLOR);
            FontMetrics fm = g2.getFontMetrics();
            for (int i = 0; i < slots.length; i++) {
                String label = slots[i].getLabel().toUpperCase(Locale.ROOT);
                int textWidth = fm.stringWidth(label);
                g2.drawString(label, xs[i] + (segmentWidth - textWidth) / 2, h / 2 + fm.getAscent() / 2 - 2);
            }
            g2.dispose();
        }

        private Color fillFor(TimeSlot slot) {
            if (slot != currentSlot) {
                return INACTIVE_FILL;
            }
            switch (slot) {
                case MORNING:
                    return MORNING_FILL;
                case AFTERNOON:
                    return AFTERNOON_FILL;
                default:
                    return NIGHT_FILL;
            }
        }
    }
}
