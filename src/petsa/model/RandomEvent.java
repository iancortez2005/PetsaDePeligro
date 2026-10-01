package petsa.model;

import petsa.model.GameState.PendingEffect;
import petsa.model.GameState.Weather;
import petsa.model.Timeline.TimeSlot;

import java.util.Random;

/**
 * A Yes/No event that arrives as a phone message. Every event is a subclass
 * below that overrides onAccept() and onDecline() with its own effects, so
 * GameEngine and the phone handle all of them the same way (polymorphism).
 *
 * The first seven are drawn at random on the event days (see Timeline and
 * EventScheduler). The last two are never drawn - they happen only when
 * their condition is met: Rainy Day Laundry replaces Hygiene on a rainy
 * day, and Pickpocketed follows working a shift at 100% Stress.
 */
public abstract class RandomEvent {

    private final String name;
    private final String prompt;

    protected RandomEvent(String name, String prompt) {
        this.name = name;
        this.prompt = prompt;
    }

    public String getName() {
        return name;
    }

    public String getPrompt() {
        return prompt;
    }

    /**
     * True if this event can be drawn right now. Most events always can;
     * some override it (e.g. the Social event stops coming once Academics
     * is locked, the Discount only comes in Weeks 2 and 3).
     */
    public boolean isEligible(GameState state) {
        return true;
    }

    /**
     * False for events that can only happen once a month - the ones that pay
     * out or save money, so they can't be farmed (see
     * EventScheduler.pickEventFor()). Other events can repeat, but only after
     * every other event has had its turn.
     */
    public boolean isRepeatable() {
        return true;
    }

    /** True if accepting means going outside (which makes the player sick on a rainy day). */
    public boolean goesOutside() {
        return false;
    }

    /** Text for the "yes" button in the phone, e.g. "PAY UP". */
    public String getAcceptLabel() {
        return "ACCEPT";
    }

    /** Text for the "no" button, e.g. "DON'T PAY" - or null if the event has only one button (Pickpocketed). */
    public String getDeclineLabel() {
        return "DECLINE";
    }

    /**
     * What accepting does, in plain words - exact cash amounts, and the
     * direction of attribute changes (+/-, or ++ for a big jump). Shown on
     * the phone before the player chooses. Must match onAccept().
     */
    public abstract String getAcceptEffects();

    /** Same as getAcceptEffects(), for declining. Must match onDecline(). */
    public abstract String getDeclineEffects();

    public abstract void onAccept(Player player, GameState state);

    public abstract void onDecline(Player player, GameState state);

    /** Formats a peso amount for effect text, e.g. 1500 -> "₱1,500". */
    protected static String peso(int amount) {
        return "₱" + String.format("%,d", amount);
    }

    // ------------------------------------------------------------------
    // The seven events drawn on event days
    // ------------------------------------------------------------------

    /** Social event: hang out with friends (costs money, lowers Stress and Academics) or stay in. */
    public static class SocialEvent extends RandomEvent {

        private static final int COST = 100;
        private static final int HUNGER_GAIN = 15;
        private static final int STRESS_RELIEF = 15;
        private static final int ACADEMIC_LOSS = 10;

        public SocialEvent() {
            super("Social event",
                    "Your friends are asking you to hangout: 'Hey it's been a while, "
                            + "we're near your area. Let's meet up today.'");
        }

        /** Friends stop inviting once Academics is locked (65% or lower). */
        @Override
        public boolean isEligible(GameState state) {
            return !state.getPlayer().isPartTimeLocked();
        }

        @Override
        public boolean goesOutside() {
            return true;
        }

        @Override
        public void onAccept(Player player, GameState state) {
            player.applyCashDelta(-COST);
            player.getHunger().applyDelta(HUNGER_GAIN);
            player.getStress().applyDelta(-STRESS_RELIEF);
            player.getAcademic().applyDelta(-ACADEMIC_LOSS);
        }

        @Override
        public void onDecline(Player player, GameState state) {
            // No effect.
        }

        @Override
        public String getAcceptLabel() {
            return "HANG OUT";
        }

        @Override
        public String getDeclineLabel() {
            return "STAY IN";
        }

        @Override
        public String getAcceptEffects() {
            return "-" + peso(COST) + ", +Hunger, -Stress, -Academics";
        }

        @Override
        public String getDeclineEffects() {
            return "Nothing happens.";
        }
    }

    /** Discount notif (the cash-flow trap): 10 days of bulk food at half price. Days 8-21 only, once a month. */
    public static class DiscountEvent extends RandomEvent {

        private static final int COST = 350;
        private static final int FOOD_DAYS = 10;
        private static final int WEEK2_START_DAY = 8;
        private static final int WEEK3_END_DAY = 21;

        public DiscountEvent() {
            super("Discount notif", "50% off! Buy food by bulk NOW!");
        }

        @Override
        public boolean isEligible(GameState state) {
            int day = state.getDay();
            return day >= WEEK2_START_DAY && day <= WEEK3_END_DAY;
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public void onAccept(Player player, GameState state) {
            player.applyCashDelta(-COST);
            player.getInventory().addFoodStock(FOOD_DAYS);
        }

        @Override
        public void onDecline(Player player, GameState state) {
            // No effect.
        }

        @Override
        public String getAcceptLabel() {
            return "BUY IT";
        }

        @Override
        public String getDeclineLabel() {
            return "SKIP IT";
        }

        @Override
        public String getAcceptEffects() {
            return "-" + peso(COST) + " now, +" + FOOD_DAYS + " days of food on your shelf";
        }

        @Override
        public String getDeclineEffects() {
            return "Nothing happens.";
        }
    }

    /** Ambagan: pay the group-project contribution (Academics up) or don't (Academics down). */
    public static class AmbaganEvent extends RandomEvent {

        private static final int COST = 300;
        private static final int ACADEMIC_DELTA = 30;

        public AmbaganEvent() {
            super("Ambagan",
                    "Your groupmate asks you for your contribution: 'Bro, we need your P300 "
                            + "for the ambagan. We're buying the materials for our IoT project "
                            + "in ITS067 today.'");
        }

        @Override
        public void onAccept(Player player, GameState state) {
            player.applyCashDelta(-COST);
            player.getAcademic().applyDelta(ACADEMIC_DELTA);
        }

        @Override
        public void onDecline(Player player, GameState state) {
            player.getAcademic().applyDelta(-ACADEMIC_DELTA);
        }

        @Override
        public String getAcceptLabel() {
            return "PAY UP";
        }

        @Override
        public String getDeclineLabel() {
            return "DON'T PAY";
        }

        @Override
        public String getAcceptEffects() {
            return "-" + peso(COST) + ", +Academics";
        }

        @Override
        public String getDeclineEffects() {
            return "-Academics";
        }
    }

    /**
     * Utang (the gamble): lend Rene P300 now and he pays back on Day 28
     * morning - P310 (70% chance), P400 (20%) or P500 (10%). Refusing costs
     * Stress (guilt). Once a month.
     */
    public static class UtangEvent extends RandomEvent {

        private static final int LOAN_AMOUNT = 300;
        private static final int REPAYMENT_DAY = 28;
        private static final int PAYOUT_310 = 310;
        private static final int PAYOUT_400 = 400;
        private static final int PAYOUT_500 = 500;
        private static final int ODDS_310_PERCENT = 70; // rolls 0-69
        private static final int ODDS_400_PERCENT = 20; // rolls 70-89; the last 10% pays PAYOUT_500
        private static final int STRESS_GAIN_IF_DECLINED = 30;

        private final Random random;

        public UtangEvent() {
            this(new Random());
        }

        /** EventScheduler passes its own Random, so the payout comes from the same dice as everything else. */
        UtangEvent(Random random) {
            super("Utang", "Bro, can I borrow P300 for my rent? I promise to pay you back next week!");
            this.random = random;
        }

        private int rollPayout() {
            int roll = random.nextInt(100);
            if (roll < ODDS_310_PERCENT) {
                return PAYOUT_310;
            }
            if (roll < ODDS_310_PERCENT + ODDS_400_PERCENT) {
                return PAYOUT_400;
            }
            return PAYOUT_500;
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public void onAccept(Player player, GameState state) {
            player.applyCashDelta(-LOAN_AMOUNT);
            int payout = rollPayout();
            state.schedulePendingEffect(new PendingEffect(REPAYMENT_DAY, TimeSlot.MORNING,
                    "Utang repayment from Rene", payout));
        }

        @Override
        public void onDecline(Player player, GameState state) {
            player.getStress().applyDelta(STRESS_GAIN_IF_DECLINED);
        }

        @Override
        public String getAcceptLabel() {
            return "LEND IT";
        }

        @Override
        public String getDeclineLabel() {
            return "REFUSE";
        }

        @Override
        public String getAcceptEffects() {
            return "-" + peso(LOAN_AMOUNT) + " now. Rene promises to pay you back.";
        }

        @Override
        public String getDeclineEffects() {
            return "+Stress (guilt)";
        }
    }

    /**
     * Broken Phone: pay P400 to repair it, or leave it cracked (Academics and
     * Stress suffer from missed group chats). A cracked phone can be repaired
     * later in the Shop for the same price.
     */
    public static class BrokenPhoneEvent extends RandomEvent {

        /** Also what a later repair costs in the Shop (see GameEngine.repairPhone()). */
        public static final int REPAIR_COST = 400;
        private static final int ACADEMIC_LOSS = 20;
        private static final int STRESS_GAIN = 25;

        public BrokenPhoneEvent() {
            super("Broken Phone",
                    "Your phone screen just shattered after falling off your desk; "
                            + "will you pay P400 to get it fixed today?");
        }

        /** A phone that's already cracked can't shatter "again" - this event waits until it's repaired. */
        @Override
        public boolean isEligible(GameState state) {
            return !state.isPhoneBroken();
        }

        @Override
        public void onAccept(Player player, GameState state) {
            player.applyCashDelta(-REPAIR_COST);
            state.setPhoneBroken(false);
        }

        @Override
        public void onDecline(Player player, GameState state) {
            player.getAcademic().applyDelta(-ACADEMIC_LOSS);
            player.getStress().applyDelta(STRESS_GAIN);
            state.setPhoneBroken(true);
        }

        @Override
        public String getAcceptLabel() {
            return "REPAIR IT";
        }

        @Override
        public String getDeclineLabel() {
            return "LEAVE IT";
        }

        @Override
        public String getAcceptEffects() {
            return "-" + peso(REPAIR_COST);
        }

        @Override
        public String getDeclineEffects() {
            return "-Academics, +Stress (missed group chats). Your screen stays cracked.";
        }
    }

    /** Broken School Equipment: confess and pay the replacement fee, or hide it and feel the guilt (big Stress). */
    public static class BrokenSchoolEquipmentEvent extends RandomEvent {

        private static final int REPLACEMENT_FEE = 350;
        private static final int STRESS_GAIN = 45;

        public BrokenSchoolEquipmentEvent() {
            super("Broken School Equipment",
                    "You accidentally broke a comp. lab mouse while no one was looking; "
                            + "do you confess and pay the replacement fee?");
        }

        @Override
        public void onAccept(Player player, GameState state) {
            player.applyCashDelta(-REPLACEMENT_FEE);
        }

        @Override
        public void onDecline(Player player, GameState state) {
            player.getStress().applyDelta(STRESS_GAIN);
        }

        @Override
        public String getAcceptLabel() {
            return "CONFESS";
        }

        @Override
        public String getDeclineLabel() {
            return "HIDE IT";
        }

        @Override
        public String getAcceptEffects() {
            return "-" + peso(REPLACEMENT_FEE);
        }

        @Override
        public String getDeclineEffects() {
            return "++Stress (guilt)";
        }
    }

    /** Academic Commission: P400 for coding a classmate's project, paid for in Stress and Academics. Once a month. */
    public static class AcademicCommissionEvent extends RandomEvent {

        private static final int PAYOUT = 400;
        private static final int STRESS_GAIN = 25;
        private static final int ACADEMIC_LOSS = 30;

        public AcademicCommissionEvent() {
            super("Academic Commission", "A classmate is offering P400 if you code their Java project.");
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public void onAccept(Player player, GameState state) {
            player.applyCashDelta(PAYOUT);
            player.getStress().applyDelta(STRESS_GAIN);
            player.getAcademic().applyDelta(-ACADEMIC_LOSS);
        }

        @Override
        public void onDecline(Player player, GameState state) {
            // No effect.
        }

        @Override
        public String getAcceptLabel() {
            return "ACCEPT";
        }

        @Override
        public String getDeclineLabel() {
            return "DECLINE";
        }

        @Override
        public String getAcceptEffects() {
            return "+" + peso(PAYOUT) + ", ++Stress, -Academics";
        }

        @Override
        public String getDeclineEffects() {
            return "Nothing happens.";
        }
    }

    // ------------------------------------------------------------------
    // Conditional events (never drawn)
    // ------------------------------------------------------------------

    /**
     * Rainy Day Laundry: on a rainy day, Hygiene means the laundromat. Pay the
     * P150 rush fee, or wear dirty clothes - Sickness +30 and a cold (1-3
     * medicine pills, or a P500 Medical Bill if the shelf is short).
     */
    public static class RainyDayLaundryEvent extends RandomEvent {

        private static final int RUSH_FEE = 150;
        private static final int SICKNESS_GAIN = 30;
        private static final int MIN_PILLS_NEEDED = 1;
        private static final int MAX_PILLS_NEEDED = 3;

        private final Random random;

        public RainyDayLaundryEvent() {
            this(new Random());
        }

        /** Package-visible so a test can pass a seeded Random. */
        RainyDayLaundryEvent(Random random) {
            super("Rainy Day Laundry",
                    "It's pouring rain and you have no clean clothes left; "
                            + "will you pay the P150 rush laundromat fee?");
            this.random = random;
        }

        @Override
        public boolean isEligible(GameState state) {
            return state.getTodayWeather() == Weather.RAINY;
        }

        @Override
        public void onAccept(Player player, GameState state) {
            player.applyCashDelta(-RUSH_FEE);
        }

        @Override
        public void onDecline(Player player, GameState state) {
            player.getSickness().applyDelta(SICKNESS_GAIN);
            int pillsNeeded = MIN_PILLS_NEEDED + random.nextInt(MAX_PILLS_NEEDED - MIN_PILLS_NEEDED + 1);
            player.resolveSicknessEvent(pillsNeeded);
        }

        @Override
        public String getAcceptLabel() {
            return "PAY THE FEE";
        }

        @Override
        public String getDeclineLabel() {
            return "WEAR DIRTY";
        }

        @Override
        public String getAcceptEffects() {
            return "-" + peso(RUSH_FEE);
        }

        @Override
        public String getDeclineEffects() {
            return "+Sickness. Getting sick uses " + MIN_PILLS_NEEDED + "-" + MAX_PILLS_NEEDED
                    + " medicine pills, or a " + peso(Player.MEDICAL_BILL) + " bill if you have none.";
        }
    }

    /**
     * Pickpocketed: the player works a shift at 100% Stress, falls asleep on
     * the jeepney home and loses P300. There's no choice - it has already
     * happened when the phone shows it, so it has a single OKAY button.
     */
    public static class PickpocketedEvent extends RandomEvent {

        private static final int LOSS = 300;

        public PickpocketedEvent() {
            super("Pickpocketed",
                    "You were so exhausted from overworking that you fell asleep on the "
                            + "jeepney ride home and got pickpocketed.");
        }

        public void resolve(Player player) {
            player.applyCashDelta(-LOSS);
        }

        @Override
        public void onAccept(Player player, GameState state) {
            resolve(player);
        }

        @Override
        public void onDecline(Player player, GameState state) {
            resolve(player);
        }

        @Override
        public String getAcceptLabel() {
            return "OKAY";
        }

        @Override
        public String getDeclineLabel() {
            return null;
        }

        @Override
        public String getAcceptEffects() {
            return "-" + peso(LOSS);
        }

        @Override
        public String getDeclineEffects() {
            return "";
        }
    }
}
