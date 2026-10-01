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

/**
 * The rules of the month in one place: it ties the calendar (Timeline),
 * the month's luck (EventScheduler) and the state (GameState, Player)
 * into one playable loop. It has no Swing code - the ui package calls it
 * for every action the player takes.
 *
 * Besides running each action, it applies the overnight rules when a day
 * ends: a whole day without eating costs Hunger, without studying costs
 * Academics, and without a shower adds Sickness; a rainy day spent indoors
 * may still end in a cold. Hitting 0% Hunger or 100% Sickness costs
 * medicine or a Medical Bill, then puts the meter back to 50%.
 *
 * Every cash change is filed in the day's DailyLedger (see track()), which
 * becomes the End of Day summary at bedtime.
 */
public class GameEngine {

    /** The last day of the month - its Night ends the game instead of rolling into a Day 31. */
    public static final int FINAL_DAY = GameState.TRANSPORT_DUE_DAY;

    /** Ledger name for cash from delayed effects - currently only the Utang repayment on Day 28. */
    private static final String PENDING_EFFECT_LABEL = "Utang repaid";

    private static final int HUNGER_DECAY_IF_SKIPPED = 40;
    /** Academics lost on a day with no Study at all. */
    private static final int ACADEMIC_DECAY_IF_NO_STUDY = 10;
    /** Sickness gained on a day with no Hygiene at all (paying the rainy-day laundromat counts as washing). */
    private static final int SICKNESS_GAIN_IF_NO_HYGIENE = 10;
    /** Where Sickness drops back to after the player falls ill at 100% (like Hunger's recovery after a collapse). */
    private static final int SICKNESS_AFTER_FALLING_ILL = 50;
    private static final int MIN_PILLS_WHEN_ILL = 1;
    private static final int MAX_PILLS_WHEN_ILL = 3;
    private static final int HUNGER_RECOVERY_AFTER_COLLAPSE = 50;

    private final GameState state;
    private final EventScheduler eventScheduler;

    private RandomEvent activeRandomEvent; // set on a RANDOM_EVENT day until resolved
    private boolean wentOutToday;          // a walk, hangout or shift today - for the rain's sickness rules
    private boolean ateToday;              // for the overnight Hunger loss
    private boolean studiedToday;          // for the overnight Academics loss
    private boolean showeredToday;         // for the overnight Sickness gain (the rainy-day laundromat counts too)
    private final Random random = new Random(); // how many pills a player falls ill for
    private RentBill rentBill;             // what the landlord charged on Day 29 (this session), or null
    private DailyLedger ledger;            // today's cash flow by category, for the End of Day summary
    private boolean eventHandledToday;     // today's random event was already delivered and answered
    private boolean nightActionUsed;       // the single Night action was already used today

    /** A brand-new game: the month's rainy days are drawn now, once. */
    public GameEngine() {
        this(new GameState(), new EventScheduler());
        state.setRainyDays(eventScheduler.planRainyDays());
        eventScheduler.applyWeatherFor(state);
    }

    /** Package-visible constructor so tests can inject a fixed GameState/EventScheduler. */
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

    // --- Daily ledger ---

    /**
     * Runs a cash-affecting operation and files the resulting cash
     * change in today's ledger. Any Medical Bills fired during the
     * operation are counted via Player.getMedicalBillCount() and filed
     * under MEDICAL; the rest of the change goes to the given category.
     */
    private void track(CashCategory category, Runnable operation) {
        track(category, null, operation);
    }

    /**
     * Like track(category, operation), but for Random Events: the cash
     * change is also recorded under eventLabel (e.g. "Ambagan"), so the
     * End of Day ledger can say which event it came from.
     */
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

    // --- Save / Load ---

    /**
     * Captures everything needed to fully resume from the current
     * (day, TimeSlot) via fromSnapshot(). Intended to be called
     * between slots (after any random event for the current slot has
     * already been resolved) - see GameSnapshot's class-level note.
     */
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

    /**
     * Rebuilds a fresh GameEngine from a previously captured snapshot.
     * The EventScheduler is newly created (with a fresh Random) rather
     * than restored - the exact sequence of future random draws isn't
     * something the player could observe or rely on anyway, only the
     * game state that already happened matters for a faithful resume.
     */
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
            // A save from before the weather plan was saved: draw one now, keeping the saved day's own weather.
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

    // --- Day 1: apartment selection ---

    public void chooseApartment(ApartmentType apartment) {
        state.chooseApartment(apartment);
    }

    // --- Day 1: guided tutorial day ---

    /** For STANDARD_TUTORIAL (Day 1): the fixed task the tutorial assigns to a given slot (Hygiene morning, Eat afternoon, Study night). */
    public Task getTutorialTaskFor(TimeSlot slot) {
        switch (slot) {
            case MORNING:   return new HygieneTask();
            case AFTERNOON: return new EatTask();
            default:        return new StudyTask();
        }
    }

    // --- Random events ---

    /**
     * On a RANDOM_EVENT day, if nothing has been pulled yet: independently
     * rolls whether it starts raining (may or may not trigger, and either
     * way does not block the main event), then pulls one eligible event
     * from the 7-event pool. Returns the main event so the caller (UI) can
     * display its prompt; returns null if today isn't a random-event day
     * or an event was already pulled. Call isTodayRainy() afterward to
     * check whether the independent rain roll also succeeded, so the UI
     * can show that notification alongside the main event's.
     */
    public RandomEvent triggerRandomEventIfDue() {
        if (getCurrentDayType() != DayType.RANDOM_EVENT || activeRandomEvent != null) {
            return null;
        }
        // Rain was already decided at the start of the day (see endDay()); the event is picked independently.
        activeRandomEvent = eventScheduler.pickEventFor(state);
        return activeRandomEvent;
    }

    /** The event delivered today but not answered yet, or null. Lets the phone reopen an unanswered message. */
    public RandomEvent getActiveRandomEvent() {
        return activeRandomEvent;
    }

    /**
     * Resolves today's event with the player's Yes/No choice (no-op if
     * nothing is active). An event that goes outside (the Social event's
     * hangout) counts as going out, so on a rainy day it makes the player
     * sick like a walk would.
     */
    public void resolveActiveRandomEvent(boolean accepted) {
        RandomEvent event = activeRandomEvent;
        if (event != null) {
            eventHandledToday = true;
            if (accepted) {
                track(CashCategory.EVENTS, event.getName(), () -> {
                    event.onAccept(state.getPlayer(), state);
                    if (event.goesOutside()) {
                        goOut(); // hanging out means going outside
                    }
                    checkSicknessCollapse();
                });
            } else {
                track(CashCategory.EVENTS, event.getName(), () -> event.onDecline(state.getPlayer(), state));
            }
        }
        activeRandomEvent = null;
    }

    /** True on a random-event day whose event hasn't been delivered and answered yet. */
    public boolean isRandomEventPendingToday() {
        return getCurrentDayType() == DayType.RANDOM_EVENT && !eventHandledToday;
    }

    /** True if it's Night and tonight's single action has already been used. */
    public boolean isNightActionUsed() {
        return state.getTimeSlot() == TimeSlot.NIGHT && nightActionUsed;
    }

    /** Records that tonight's single action was used (the UI calls this after a Night task or shift). */
    public void markNightActionUsed() {
        nightActionUsed = true;
    }

    // --- Rent reminders ---

    /** True at the end of each week (Days 7, 14, 21, 28), when the phone reminds the player about rent. */
    public boolean isRentReminderDay() {
        return Timeline.isRentReminderDay(state.getDay());
    }

    /** Days left until the landlord collects rent (Day 29). 0 on Day 29, negative after it. */
    public int getDaysUntilRent() {
        return GameState.RENT_DUE_DAY - state.getDay();
    }

    // --- The end of the month ---

    /** The itemized Day 29 bill, if the landlord came during this session; null otherwise. */
    public RentBill getRentBill() {
        return rentBill;
    }

    /** True once the Day 30 fare home has been paid - the player has won the month. */
    public boolean isMonthComplete() {
        return state.isMonthCompleted();
    }

    /** True if the game ended on Day 30 because the player couldn't afford the fare home (as opposed to going broke). */
    public boolean isStranded() {
        return isGameOver() && state.getDay() >= GameState.TRANSPORT_DUE_DAY && state.getPlayer().getCash() > 0;
    }

    // --- Cracked phone ---

    public boolean isPhoneBroken() {
        return state.isPhoneBroken();
    }

    /**
     * Repairs a cracked phone from the Shop, for the same price as the
     * Broken Phone event's repair. Filed in the ledger as "Phone repair".
     * Returns false (with no state change) if the phone isn't cracked or
     * the repair would leave the player with no money.
     */
    public boolean repairPhone() {
        if (!state.isPhoneBroken() || !leavesMoneyAfter(BrokenPhoneEvent.REPAIR_COST)) {
            return false;
        }
        Player player = state.getPlayer();
        track(CashCategory.EVENTS, "Phone repair", () -> player.applyCashDelta(-BrokenPhoneEvent.REPAIR_COST));
        state.setPhoneBroken(false);
        return true;
    }

    // --- Shopping (Phone app) ---

    /**
     * True if the player can pay cost and still have money left. Zero
     * cash means game over, so the shop never lets a purchase take the
     * player to zero or below.
     */
    private boolean leavesMoneyAfter(int cost) {
        return state.getPlayer().getCash() - cost > 0;
    }

    /**
     * Buys medicine pills in the phone's Shop, at P20 each. Shopping doesn't
     * use up a time slot. Returns false (with no change) if it would leave
     * the player with no money.
     */
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

    /**
     * Buys days of bulk food in the phone's Shop, at P70 a day - the same as
     * paying per meal, just stocked in advance (EatTask uses the stock
     * first). Shopping doesn't use up a time slot. Returns false (with no
     * change) if it would leave the player with no money.
     */
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

    // --- Standard tasks ---

    /** True if today's Hygiene slot should be replaced by RainyDayLaundryEvent instead of the normal HygieneTask. */
    public boolean isTodayRainy() {
        return state.getTodayWeather() == Weather.RAINY;
    }

    /**
     * Does a task (Eat, Hygiene, Study or Walk) in the current slot. Returns
     * false, with no effect, if the task was already done today, or if
     * Stress is at 100% - then Eat, Hygiene and Study fail (and
     * Player.incapacitatedToday is set for the UI). Walk always works: it is
     * the only way to bring Stress back down.
     */
    public boolean performTask(Task task) {
        if (isTaskDoneToday(task)) {
            return false; // Eat, Hygiene and Study can each be done once a day; Walk as often as you like
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

    /**
     * True if this task has already been done today. Eat, Hygiene and Study
     * can each be done once per day (a rainy-day laundromat trip counts as
     * Hygiene, paid or not); Walk is never "done" - it can be repeated.
     */
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

    /** True on the part-time job days (7, 14, 21, 24, 28) - all day, not only at Night when the shift happens. */
    public boolean isPartTimeDay() {
        return PartTimeJob.isAvailableOn(state.getDay());
    }

    /**
     * The player goes outside (a walk, a hangout, a work shift). On a rainy
     * day that takes its toll right away: +30 Sickness each time, and the
     * first outing of the day also catches a cold (medicine or a bill).
     */
    private void goOut() {
        boolean firstOuting = !wentOutToday;
        wentOutToday = true;
        if (isTodayRainy()) {
            eventScheduler.goOutInRain(state.getPlayer(), firstOuting);
            checkSicknessCollapse();
        }
    }

    /**
     * If Sickness has reached 100%, the player falls ill: 1-3 medicine pills
     * are used up (or a P500 Medical Bill if there aren't enough), and
     * Sickness drops back to 50% - the counterpart of a Hunger collapse.
     */
    private void checkSicknessCollapse() {
        Player player = state.getPlayer();
        if (player.getSickness().hasCollapsed()) {
            int pills = MIN_PILLS_WHEN_ILL + random.nextInt(MAX_PILLS_WHEN_ILL - MIN_PILLS_WHEN_ILL + 1);
            player.resolveSicknessEvent(pills);
            player.getSickness().restoreValue(SICKNESS_AFTER_FALLING_ILL);
        }
    }

    /** If Hunger has collapsed to 0%, charges the P500 Medical Bill and puts Hunger back up to 50%. */
    private void checkHungerCollapse() {
        Player player = state.getPlayer();
        if (player.getHunger().hasCollapsed()) {
            player.triggerMedicalBill("Hunger collapsed to 0%");
            player.getHunger().applyDelta(HUNGER_RECOVERY_AFTER_COLLAPSE);
        }
    }

    /**
     * Resolves the Rainy Day Laundry substitution for the Hygiene slot
     * (see isTodayRainy()). Same Stress-incapacitation rule as
     * performTask(): fails silently if Stress is maxed out.
     */
    public void performRainyDayLaundry(RainyDayLaundryEvent event, boolean paidRushFee) {
        if (state.getPlayer().isIncapacitatedByStress()) {
            state.getPlayer().setIncapacitatedToday(true);
            return;
        }
        if (showeredToday) {
            return; // today's Hygiene has already been done
        }
        // The laundromat trip counts as today's Hygiene either way. Declining (wearing dirty clothes)
        // already costs Sickness right away, so it isn't also charged the overnight "no hygiene" gain.
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

    // --- Part-Time job ---

    /** True if the current slot is the fixed Part-Time slot: NIGHT on one of PartTimeJob.AVAILABLE_DAYS. Morning/Afternoon on those days are still normal task slots. */
    public boolean isPartTimeSlotNow() {
        return getCurrentDayType() == DayType.PART_TIME_AVAILABLE && state.getTimeSlot() == TimeSlot.NIGHT;
    }

    /**
     * Works a part-time shift. At 100% Stress the player is pickpocketed on
     * the way home instead of paid: the loss is applied and the
     * PickpocketedEvent is returned so the phone can show it. Returns null
     * after a normal shift.
     */
    public PickpocketedEvent workPartTime(ShiftType shiftType) {
        Player player = state.getPlayer();
        if (player.isIncapacitatedByStress()) {
            PickpocketedEvent pickpocketed = new PickpocketedEvent();
            track(CashCategory.EVENTS, pickpocketed.getName(), () -> {
                goOut(); // commuting to/from work counts as going out
                pickpocketed.resolve(player);
            });
            return pickpocketed;
        }
        track(CashCategory.SALARY, () -> {
            goOut(); // commuting to/from work counts as going out
            shiftType.work(player);
        });
        return null;
    }

    // --- Day/slot progression ---

    /**
     * Advances to the next time slot. From NIGHT this ends the day
     * (see endDay()); from Morning or Afternoon it simply moves to the
     * next slot, resolving any pending effect due there.
     */
    public void advanceTimeSlot() {
        if (state.getTimeSlot() == TimeSlot.NIGHT) {
            endDay();
            return;
        }
        track(CashCategory.EVENTS, PENDING_EFFECT_LABEL, state::advanceTimeSlot);
    }

    /**
     * Ends the current day. Must be called during the NIGHT slot.
     *
     * On Days 1-29: applies the overnight consequences of the day just
     * finished (passive Hunger decay if the player never ate, and the
     * Rainy Weather sickness roll), closes today's ledger into a
     * DaySummary, then rolls into the next day's Morning - resetting
     * the per-day trackers and weather, resolving pending effects (such
     * as the Day 28 Utang repayment) and applying Day 29's rent and
     * utilities or Day 30's transport cost. Those morning charges land
     * in the NEW day's ledger, since they happen at the start of it.
     *
     * On FINAL_DAY (30): only closes the ledger - there is no Day 31.
     *
     * Returns the summary of the day that just ended.
     */
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
                // Neglecting a task for a whole day lets its attribute slip overnight.
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
                // Going out in the rain already took its toll at the time (see goOut());
                // a rainy day spent indoors still carries a 50% risk of a cold.
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
        track(CashCategory.EVENTS, PENDING_EFFECT_LABEL, state::advanceTimeSlot); // NIGHT -> next day's MORNING
        wentOutToday = false;
        ateToday = false;
        eventHandledToday = false;
        nightActionUsed = false;
        studiedToday = false;
        showeredToday = false;
        activeRandomEvent = null;
        // Today's weather comes from the month's plan, set at the start of the day so the window,
        // room and phone all show it from the morning on.
        eventScheduler.applyWeatherFor(state);

        if (state.getDay() == GameState.RENT_DUE_DAY) {
            // The landlord's visit: itemize the bill before charging it, for the Rent Day screen.
            rentBill = new RentBill(GameState.RENT_AMOUNT, state.getElectricityBill(), state.getAccumulatedWaterBill(),
                    player.getCash(), state.getApartment());
            track(CashCategory.BILLS, state::applyEndOfMonthBills);
        } else if (state.getDay() == GameState.TRANSPORT_DUE_DAY) {
            // Day 30 has no gameplay: the fare home is paid if the player can afford it (winning the
            // month, even if that leaves exactly P0), otherwise they're stranded - see isGameOver().
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
