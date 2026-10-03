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
import javax.swing.BorderFactory;
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

public class RoomPanel extends BackgroundPanel {

    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    private static final Rectangle DOOR_SIGN = new Rectangle(35, 245, 145, 62);
    private static final Rectangle WINDOW_SIGN = new Rectangle(305, 222, 160, 62);
    private static final Rectangle BOARD_SIGN = new Rectangle(618, 112, 136, 60);
    private static final Rectangle SHELF_SIGN = new Rectangle(928, 108, 200, 60);
    private static final Rectangle BED_SIGN = new Rectangle(820, 440, 230, 66);
    private static final Rectangle PHONE_SPRITE = new Rectangle(1020, 596, 56, 90);
    private static final Rectangle PHONE_SIGN = new Rectangle(1085, 616, 150, 58);

    private static final Rectangle DAY_CASH_BOX = new Rectangle(20, 15, 260, 80);
    private static final Rectangle TIME_BAR = new Rectangle(340, 22, 520, 50);
    private static final Rectangle ATTRIBUTE_BOX = new Rectangle(890, 15, 285, 80);
    private static final Rectangle PAUSE_BUTTON = new Rectangle(1185, 28, 82, 42);
    private static final Rectangle BOARD_AREA = new Rectangle(540, 108, 294, 258);
    private static final Color TIP_BACKING = new Color(0, 0, 0, 160);

    private static final String[] PHONE_SPRITE_NAMES = {"phone_sprite", "phone", "Phone", "Phone_Sprite", "PhoneSprite"};
    private static final String[] SPRITE_EXTENSIONS = {"png", "gif", "jpeg", "jpg"};

    public interface Listener {
        void onDayEnded(DaySummary summary);

        default void onRestart() {
        }

        default void onQuitGame() {
            System.exit(0);
        }

        default void onExitToTitle() {
        }
    }

    private final GameEngine engine;
    private final Listener listener;

    private final SaveManager saveManager;
    private final PauseOverlay pauseOverlay;
    private boolean paused = false;
    private final PhoneOverlay phone;
    private JButton phoneSprite;
    private ImageIcon phoneIcon;
    private ImageIcon crackedPhoneIcon;
    private WindowOverlay windowView;
    private ShelfOverlay shelfView;
    private RentDayOverlay rentDay;
    private boolean rentDayShown = false;
    private final BoardOverlay board;
    private boolean boardOpen = false;
    private GameOverOverlay gameOverOverlay;
    private int alertsSeenDay = -1;
    private boolean phoneOpen = false;
    private boolean gameOverHandled = false;
    private TutorialOverlay tutorialOverlay;

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

    public RoomPanel(GameEngine engine, Listener listener) {
        this(engine, listener, null);
    }

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
        pauseButton.setBounds(PAUSE_BUTTON);
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

        bedButton.setBounds(BED_SIGN);
        bedButton.addActionListener(e -> onBed());
        makeClickable(bedButton);
        add(bedButton);

        phoneSprite = buildPhoneSprite();
        if (phoneSprite != null) {
            add(phoneSprite);
        }

        JLabel tip = new JLabel("Tip: Use the Board or Door for each part of the day, then sleep at Night. Don't skip a part-time job.") {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(TIP_BACKING);
                g.fillRect(0, 0, getWidth(), getHeight());
                super.paintComponent(g);
            }
        };
        tip.setFont(PixelKit.font(9f));
        tip.setForeground(new Color(0xF2, 0xD8, 0x8A));
        tip.setBorder(BorderFactory.createEmptyBorder(3, 9, 3, 9));
        Dimension tipSize = tip.getPreferredSize();
        tip.setBounds(11, HEIGHT - 19 - tipSize.height / 2, tipSize.width, tipSize.height);
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

    private void onBoardTaskChosen(BoardOverlay.Choice choice) {
        closeBoard();
        TimeSlot slot = engine.getState().getTimeSlot();
        if (choice == BoardOverlay.Choice.HYGIENE && engine.isTodayRainy()) {
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
                return;
            }
            engine.performTask(new WalkTask());
        } else if (engine.isPartTimeSlotNow()) {
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
                return;
            }
            ShiftType shift = (choice == 0) ? ShiftType.STANDARD : ShiftType.OVERTIME;
            PickpocketedEvent pickpocketed = engine.workPartTime(shift);
            finishSlot(slot);
            if (pickpocketed != null && !engine.isGameOver()) {
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
            openPhone();
            return;
        }
        if (!engine.isNightActionUsed()) {
            GameDialog.showMessage(this, "Bed",
                    "Do tonight's task first (Board or Door) before going to bed.");
            return;
        }
        DaySummary summary = engine.endDay();
        if (listener != null) {
            listener.onDayEnded(summary);
        }
    }

    public void returnFromEndOfDay() {
        RentBill bill = engine.getRentBill();
        if (bill != null && !rentDayShown && engine.getState().getDay() == GameState.RENT_DUE_DAY) {
            showRentDay(bill);
            return;
        }
        checkStatus();
    }

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

    public void checkStatus() {
        refreshHud();
        checkGameOver();
    }

    private void onPhone() {
        openPhone();
    }

    private void openPhone() {
        if (!showPhoneOverlay()) {
            return;
        }
        if (hasPhoneAlertsToday()) {
            alertsSeenDay = engine.getState().getDay();
        }
        phone.openHome();
    }

    private boolean hasPhoneAlertsToday() {
        return engine.isRentReminderDay() || engine.isPartTimeDay();
    }

    private void openPhoneAlert(PhoneOverlay.Message alert) {
        if (!showPhoneOverlay()) {
            alert.onAnswer.accept(true);
            return;
        }
        phone.openAlert(alert);
    }

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

    private boolean hasUnreadMail() {
        return engine.isRandomEventPendingToday();
    }

    private boolean canActNow() {
        if (engine.isGameOver()) {
            return false;
        }
        if (hasUnreadMail()) {
            openPhone();
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

    private void finishSlot(TimeSlot slotJustCompleted) {
        if (slotJustCompleted == TimeSlot.NIGHT) {
            engine.markNightActionUsed();
        } else {
            engine.advanceTimeSlot();
        }
        refreshHud();
        checkGameOver();
    }

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
                new Rectangle[] {PHONE_SPRITE.union(PHONE_SIGN), SHELF_SIGN, BED_SIGN, PAUSE_BUTTON},
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

    private void installEscapeKey() {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "togglePause");
        getActionMap().put("togglePause", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (tutorialOverlay != null || gameOverOverlay != null || rentDay != null) {
                    return;
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
                    return;
                }
                FadePane.run(RoomPanel.this, paused ? RoomPanel.this::hidePause : RoomPanel.this::showPause);
            }
        });
    }

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

    private static class InfoBox extends JPanel {

        private static final Color FILL = new Color(20, 16, 14, 190);
        private static final Color BORDER = new Color(60, 50, 40, 220);
        private static final int ARC = 15;

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
                pg.setComposite(AlphaComposite.Src);
                pg.setColor(FILL);
                pg.fill(new RoundRectangle2D.Float(px, px, artW - px * 2, artH - px * 2, ARC - px * 2, ARC - px * 2));
            });
            g2.dispose();
        }
    }

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
            int gap = 9;
            int segmentWidth = PixelKit.snap((w - gap * 2) / 3);
            int artH = PixelKit.snap(h);
            int px = PixelKit.SCALE;
            int[] xs = {0, segmentWidth + gap, (segmentWidth + gap) * 2};
            TimeSlot[] slots = TimeSlot.values();

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
