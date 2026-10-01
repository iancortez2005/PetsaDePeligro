package petsa.model;

import petsa.model.DailyLedger.CashCategory;
import petsa.model.DailyLedger.DaySummary;
import petsa.model.DailyLedger.RentBill;
import petsa.model.GameState.ApartmentType;
import petsa.model.GameState.Weather;
import petsa.model.PartTimeJob.ShiftType;
import petsa.model.Player.Inventory;
import petsa.model.RandomEvent.BrokenPhoneEvent;
import petsa.model.RandomEvent.PickpocketedEvent;
import petsa.model.RandomEvent.RainyDayLaundryEvent;
import petsa.model.SaveManager.GameSnapshot;
import petsa.model.Task.EatTask;
import petsa.model.Task.HygieneTask;
import petsa.model.Task.StudyTask;
import petsa.model.Task.WalkTask;
import petsa.model.Timeline.DayType;
import petsa.model.Timeline.TimeSlot;

import java.util.Random;
import java.util.Set;

public class GameEngine {

    public static final int FINAL_DAY = GameState.TRANSPORT_DUE_DAY;

    private static final String PENDING_EFFECT_LABEL = "Utang repaid";

    private static final int HUNGER_DECAY_IF_SKIPPED = 40;
    private static final int ACADEMIC_DECAY_IF_NO_STUDY = 10;
    private static final int SICKNESS_GAIN_IF_NO_HYGIENE = 10;
    private static final int SICKNESS_AFTER_FALLING_ILL = 50;
    private static final int MIN_PILLS_WHEN_ILL = 1;
    private static final int MAX_PILLS_WHEN_ILL = 3;
    private static final int HUNGER_RECOVERY_AFTER_COLLAPSE = 50;

    private final GameState state;
    private final EventScheduler eventScheduler;

    private RandomEvent activeRandomEvent;
    private boolean wentOutToday;
    private boolean ateToday;
    private boolean studiedToday;
    private boolean showeredToday;
    private final Random random = new Random();
    private RentBill rentBill;
    private DailyLedger ledger;
    private boolean eventHandledToday;
    private boolean nightActionUsed;

    public GameEngine() {
        this(new GameState(), new EventScheduler());
        state.setRainyDays(eventScheduler.planRainyDays());
        eventScheduler.applyWeatherFor(state);
    }

    GameEngine(GameState state, EventScheduler eventScheduler) {
        this.state = state;
        this.eventScheduler = eventScheduler;
        this.wentOutToday = false;
        this.ateToday = false;
        this.ledger = new DailyLedger(state.getPlayer().getCash());
    }

    public GameState getState() {
        return state;
    }

    public DayType getCurrentDayType() {
        return Timeline.getDayType(state.getDay());
    }

    private void track(CashCategory category, Runnable operation) {
        track(category, null, operation);
    }

    private void track(CashCategory category, String eventLabel, Runnable operation) {
        Player player = state.getPlayer();
        int cashBefore = player.getCash();
        int billsBefore = player.getMedicalBillCount();
        operation.run();
        int medical = -Player.MEDICAL_BILL * (player.getMedicalBillCount() - billsBefore);
        int rest = (player.getCash() - cashBefore) - medical;
        ledger.record(CashCategory.MEDICAL, medical);
        if (category == CashCategory.EVENTS && eventLabel != null) {
            ledger.recordEvent(eventLabel, rest);
        } else {
            ledger.record(category, rest);
        }
    }

    public GameSnapshot captureSnapshot() {
        Player player = state.getPlayer();
        return new GameSnapshot(
                state.getDay(),
                state.getTimeSlot(),
                state.getApartment(),
                state.getTodayWeather(),
                state.getAccumulatedWaterBill(),
                player.getCash(),
                player.getHunger().getValue(),
                player.getStress().getValue(),
                player.getAcademic().getValue(),
                player.getSickness().getValue(),
                player.getInventory().getMedicineStock(),
                player.getInventory().getFoodStock(),
                player.isIncapacitatedToday(),
                ateToday,
                wentOutToday,
                player.getMedicalBillCount(),
                player.getLastMedicalBillReason(),
                state.getPendingEffects(),
                eventHandledToday,
                nightActionUsed,
                ledger.getStartingBalance(),
                ledger.getTotals(),
                ledger.getEventEntries(),
                state.isPhoneBroken(),
                studiedToday,
                showeredToday,
                state.getRainyDays(),
                state.getEventHistory());
    }

    public static GameEngine fromSnapshot(GameSnapshot snapshot) {
        GameState state = new GameState();
        state.restoreDayAndSlot(snapshot.getDay(), snapshot.getTimeSlot());
        if (snapshot.getApartment() != null) {
            state.chooseApartment(snapshot.getApartment());
        }
        state.restoreWaterBill(snapshot.getAccumulatedWaterBill());
        state.restorePendingEffects(snapshot.getPendingEffects());
        state.restoreEventHistory(snapshot.getEventHistory());
        EventScheduler scheduler = new EventScheduler();
        Set<Integer> rainyDays = snapshot.getRainyDays();
        if (rainyDays.isEmpty()) {
            rainyDays = scheduler.planRainyDays();
            if (snapshot.getTodayWeather() == Weather.RAINY) {
                rainyDays.add(snapshot.getDay());
            } else {
                rainyDays.remove(snapshot.getDay());
            }
        }
        state.setRainyDays(rainyDays);
        state.setTodayWeather(snapshot.getTodayWeather());

        Player player = state.getPlayer();
        player.restoreCash(snapshot.getCash());
        player.getHunger().restoreValue(snapshot.getHunger());
        player.getStress().restoreValue(snapshot.getStress());
        player.getAcademic().restoreValue(snapshot.getAcademic());
        player.getSickness().restoreValue(snapshot.getSickness());
        player.getInventory().restoreStocks(snapshot.getMedicineStock(), snapshot.getFoodStock());
        player.setIncapacitatedToday(snapshot.isIncapacitatedToday());
        player.restoreMedicalBillInfo(snapshot.getMedicalBillCount(), snapshot.getLastMedicalBillReason());

        GameEngine engine = new GameEngine(state, scheduler);
        engine.ateToday = snapshot.isAteToday();
        engine.wentOutToday = snapshot.isWentOutToday();
        engine.eventHandledToday = snapshot.isEventHandledToday();
        engine.nightActionUsed = snapshot.isNightActionUsed();
        engine.studiedToday = snapshot.isStudiedToday();
        engine.showeredToday = snapshot.isShoweredToday();
        engine.ledger = new DailyLedger(snapshot.getLedgerStartingBalance(), snapshot.getLedgerTotals(),
                snapshot.getLedgerEvents());
        state.setPhoneBroken(snapshot.isPhoneBroken());
        return engine;
    }

    public void chooseApartment(ApartmentType apartment) {
        state.chooseApartment(apartment);
    }

    public Task getTutorialTaskFor(TimeSlot slot) {
        switch (slot) {
            case MORNING:   return new HygieneTask();
            case AFTERNOON: return new EatTask();
            default:        return new StudyTask();
        }
    }

    public RandomEvent triggerRandomEventIfDue() {
        if (getCurrentDayType() != DayType.RANDOM_EVENT || activeRandomEvent != null) {
            return null;
        }
        activeRandomEvent = eventScheduler.pickEventFor(state);
        return activeRandomEvent;
    }

    public RandomEvent getActiveRandomEvent() {
        return activeRandomEvent;
    }

    public void resolveActiveRandomEvent(boolean accepted) {
        RandomEvent event = activeRandomEvent;
        if (event != null) {
            eventHandledToday = true;
            if (accepted) {
                track(CashCategory.EVENTS, event.getName(), () -> {
                    event.onAccept(state.getPlayer(), state);
                    if (event.goesOutside()) {
                        goOut();
                    }
                    checkSicknessCollapse();
                });
            } else {
                track(CashCategory.EVENTS, event.getName(), () -> event.onDecline(state.getPlayer(), state));
            }
        }
        activeRandomEvent = null;
    }

    public boolean isRandomEventPendingToday() {
        return getCurrentDayType() == DayType.RANDOM_EVENT && !eventHandledToday;
    }

    public boolean isNightActionUsed() {
        return state.getTimeSlot() == TimeSlot.NIGHT && nightActionUsed;
    }

    public void markNightActionUsed() {
        nightActionUsed = true;
    }

    public boolean isRentReminderDay() {
        return Timeline.isRentReminderDay(state.getDay());
    }

    public int getDaysUntilRent() {
        return GameState.RENT_DUE_DAY - state.getDay();
    }

    public RentBill getRentBill() {
        return rentBill;
    }

    public boolean isMonthComplete() {
        return state.isMonthCompleted();
    }

    public boolean isStranded() {
        return isGameOver() && state.getDay() >= GameState.TRANSPORT_DUE_DAY && state.getPlayer().getCash() > 0;
    }

    public boolean isPhoneBroken() {
        return state.isPhoneBroken();
    }

    public boolean repairPhone() {
        if (!state.isPhoneBroken() || !leavesMoneyAfter(BrokenPhoneEvent.REPAIR_COST)) {
            return false;
        }
        Player player = state.getPlayer();
        track(CashCategory.EVENTS, "Phone repair", () -> player.applyCashDelta(-BrokenPhoneEvent.REPAIR_COST));
        state.setPhoneBroken(false);
        return true;
    }

    private boolean leavesMoneyAfter(int cost) {
        return state.getPlayer().getCash() - cost > 0;
    }

    public boolean buyMedicine(int pills) {
        Player player = state.getPlayer();
        int cost = pills * Inventory.MEDICINE_PRICE;
        if (!leavesMoneyAfter(cost)) {
            return false;
        }
        track(CashCategory.FOOD_AND_SUPPLIES, () -> player.applyCashDelta(-cost));
        player.getInventory().addMedicine(pills);
        return true;
    }

    public boolean buyFoodStock(int days) {
        Player player = state.getPlayer();
        int cost = days * Inventory.MEAL_PRICE;
        if (!leavesMoneyAfter(cost)) {
            return false;
        }
        track(CashCategory.FOOD_AND_SUPPLIES, () -> player.applyCashDelta(-cost));
        player.getInventory().addFoodStock(days);
        return true;
    }

    public boolean isTodayRainy() {
        return state.getTodayWeather() == Weather.RAINY;
    }

    public boolean performTask(Task task) {
        if (isTaskDoneToday(task)) {
            return false;
        }
        boolean blockedByStress = !(task instanceof WalkTask) && state.getPlayer().isIncapacitatedByStress();
        if (blockedByStress) {
            state.getPlayer().setIncapacitatedToday(true);
            return false;
        }
        if (task instanceof EatTask) {
            ateToday = true;
        } else if (task instanceof StudyTask) {
            studiedToday = true;
        } else if (task instanceof HygieneTask) {
            showeredToday = true;
        }
        track(CashCategory.FOOD_AND_SUPPLIES, () -> {
            task.execute(state.getPlayer(), state);
            if (task instanceof WalkTask) {
                goOut();
            }
            checkHungerCollapse();
            checkSicknessCollapse();
        });
        return true;
    }

    public boolean isTaskDoneToday(Task task) {
        if (task instanceof EatTask) {
            return ateToday;
        }
        if (task instanceof StudyTask) {
            return studiedToday;
        }
        if (task instanceof HygieneTask) {
            return showeredToday;
        }
        return false;
    }

    public boolean hasEatenToday() {
        return ateToday;
    }

    public boolean hasStudiedToday() {
        return studiedToday;
    }

    public boolean hasDoneHygieneToday() {
        return showeredToday;
    }

    public boolean isPartTimeDay() {
        return PartTimeJob.isAvailableOn(state.getDay());
    }

    private void goOut() {
        boolean firstOuting = !wentOutToday;
        wentOutToday = true;
        if (isTodayRainy()) {
            eventScheduler.goOutInRain(state.getPlayer(), firstOuting);
            checkSicknessCollapse();
        }
    }

    private void checkSicknessCollapse() {
        Player player = state.getPlayer();
        if (player.getSickness().hasCollapsed()) {
            int pills = MIN_PILLS_WHEN_ILL + random.nextInt(MAX_PILLS_WHEN_ILL - MIN_PILLS_WHEN_ILL + 1);
            player.resolveSicknessEvent(pills);
            player.getSickness().restoreValue(SICKNESS_AFTER_FALLING_ILL);
        }
    }

    private void checkHungerCollapse() {
        Player player = state.getPlayer();
        if (player.getHunger().hasCollapsed()) {
            player.triggerMedicalBill("Hunger collapsed to 0%");
            player.getHunger().applyDelta(HUNGER_RECOVERY_AFTER_COLLAPSE);
        }
    }

    public void performRainyDayLaundry(RainyDayLaundryEvent event, boolean paidRushFee) {
        if (state.getPlayer().isIncapacitatedByStress()) {
            state.getPlayer().setIncapacitatedToday(true);
            return;
        }
        if (showeredToday) {
            return;
        }
        showeredToday = true;
        if (paidRushFee) {
            track(CashCategory.EVENTS, event.getName(), () -> event.onAccept(state.getPlayer(), state));
        } else {
            track(CashCategory.EVENTS, event.getName(), () -> {
                event.onDecline(state.getPlayer(), state);
                checkSicknessCollapse();
            });
        }
    }

    public boolean isPartTimeSlotNow() {
        return getCurrentDayType() == DayType.PART_TIME_AVAILABLE && state.getTimeSlot() == TimeSlot.NIGHT;
    }

    public PickpocketedEvent workPartTime(ShiftType shiftType) {
        Player player = state.getPlayer();
        if (player.isIncapacitatedByStress()) {
            PickpocketedEvent pickpocketed = new PickpocketedEvent();
            track(CashCategory.EVENTS, pickpocketed.getName(), () -> {
                goOut();
                pickpocketed.resolve(player);
            });
            return pickpocketed;
        }
        track(CashCategory.SALARY, () -> {
            goOut();
            shiftType.work(player);
        });
        return null;
    }

    public void advanceTimeSlot() {
        if (state.getTimeSlot() == TimeSlot.NIGHT) {
            endDay();
            return;
        }
        track(CashCategory.EVENTS, PENDING_EFFECT_LABEL, state::advanceTimeSlot);
    }

    public DaySummary endDay() {
        if (state.getTimeSlot() != TimeSlot.NIGHT) {
            throw new IllegalStateException("endDay() can only be called during the NIGHT slot");
        }
        Player player = state.getPlayer();
        boolean finalDay = state.getDay() >= FINAL_DAY;

        if (!finalDay) {
            boolean wasRainyToday = isTodayRainy();
            boolean wentOut = wentOutToday;
            boolean ate = ateToday;
            boolean studied = studiedToday;
            boolean showered = showeredToday;
            track(CashCategory.OTHER, () -> {
                if (!ate) {
                    player.getHunger().applyDelta(-HUNGER_DECAY_IF_SKIPPED);
                    checkHungerCollapse();
                }
                if (!studied) {
                    player.getAcademic().applyDelta(-ACADEMIC_DECAY_IF_NO_STUDY);
                }
                if (!showered) {
                    player.getSickness().applyDelta(SICKNESS_GAIN_IF_NO_HYGIENE);
                }
                if (wasRainyToday && !wentOut) {
                    eventScheduler.rollStayedInRisk(player);
                }
                checkSicknessCollapse();
            });
        }

        DaySummary summary = ledger.close(state.getDay(), finalDay, player);
        if (finalDay) {
            return summary;
        }

        ledger = new DailyLedger(player.getCash());
        track(CashCategory.EVENTS, PENDING_EFFECT_LABEL, state::advanceTimeSlot);
        wentOutToday = false;
        ateToday = false;
        eventHandledToday = false;
        nightActionUsed = false;
        studiedToday = false;
        showeredToday = false;
        activeRandomEvent = null;
        eventScheduler.applyWeatherFor(state);

        if (state.getDay() == GameState.RENT_DUE_DAY) {
            rentBill = new RentBill(GameState.RENT_AMOUNT, state.getElectricityBill(), state.getAccumulatedWaterBill(),
                    player.getCash(), state.getApartment());
            track(CashCategory.BILLS, state::applyEndOfMonthBills);
        } else if (state.getDay() == GameState.TRANSPORT_DUE_DAY) {
            if (player.getCash() >= GameState.TRANSPORT_AMOUNT) {
                track(CashCategory.BILLS, state::applyTransportCost);
                state.setMonthCompleted(true);
            }
        }
        return summary;
    }

    public boolean isGameOver() {
        return state.isGameOver();
    }
}
