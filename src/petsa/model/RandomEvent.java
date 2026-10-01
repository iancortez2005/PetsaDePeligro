package petsa.model;

import petsa.model.GameState.PendingEffect;
import petsa.model.GameState.Weather;
import petsa.model.Timeline.TimeSlot;

import java.util.Random;

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

    public boolean isEligible(GameState state) {
        return true;
    }

    public boolean isRepeatable() {
        return true;
    }

    public boolean goesOutside() {
        return false;
    }

    public String getAcceptLabel() {
        return "ACCEPT";
    }

    public String getDeclineLabel() {
        return "DECLINE";
    }

    public abstract String getAcceptEffects();

    public abstract String getDeclineEffects();

    public abstract void onAccept(Player player, GameState state);

    public abstract void onDecline(Player player, GameState state);

    protected static String peso(int amount) {
        return "₱" + String.format("%,d", amount);
    }

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

    public static class UtangEvent extends RandomEvent {

        private static final int LOAN_AMOUNT = 300;
        private static final int REPAYMENT_DAY = 28;
        private static final int PAYOUT_310 = 310;
        private static final int PAYOUT_400 = 400;
        private static final int PAYOUT_500 = 500;
        private static final int ODDS_310_PERCENT = 70;
        private static final int ODDS_400_PERCENT = 20;
        private static final int STRESS_GAIN_IF_DECLINED = 30;

        private final Random random;

        public UtangEvent() {
            this(new Random());
        }

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

    public static class BrokenPhoneEvent extends RandomEvent {

        public static final int REPAIR_COST = 400;
        private static final int ACADEMIC_LOSS = 20;
        private static final int STRESS_GAIN = 25;

        public BrokenPhoneEvent() {
            super("Broken Phone",
                    "Your phone screen just shattered after falling off your desk; "
                            + "will you pay P400 to get it fixed today?");
        }

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

    public static class RainyDayLaundryEvent extends RandomEvent {

        private static final int RUSH_FEE = 150;
        private static final int SICKNESS_GAIN = 30;
        private static final int MIN_PILLS_NEEDED = 1;
        private static final int MAX_PILLS_NEEDED = 3;

        private final Random random;

        public RainyDayLaundryEvent() {
            this(new Random());
        }

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
