package com.salonplatform.dto.demand;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** One piece of advice for a branch, tied to the days and hours it is about so the screen can highlight them. */
@Data
@Builder
public class DemandRecommendation {
    /** Stable id within the branch, e.g. "RUSH-6-17-20". */
    private String id;
    /** RUSH | QUIET | OVERSTAFFED | PREMIUM_HOURS | DAY_MIX | SEASON | CANCELLATIONS | OFF_HOURS | EDGE_HOUR | TREND */
    private String type;
    /** HIGH | MEDIUM | LOW */
    private String priority;
    private String title;
    /** One-line instruction for the action board, e.g. "Start 2 people at 12 pm instead of 10 am on weekdays". */
    private String action;
    /** What the numbers show, in plain words. */
    private String whatWeSee;
    /** What the owner can do about it. */
    private String whatToDo;
    /** The likely payoff, with its assumptions stated. Null when there is nothing honest to estimate. */
    private String impact;
    /** Estimated ₹ per month behind {@link #impact}; null when not estimated. */
    private Double impactAmount;
    /** ISO weekdays this applies to (empty = not day-specific). */
    private List<Integer> days;
    /** Hour range [startHour, endHour) to highlight, or -1 when not hour-specific. */
    private int startHour;
    private int endHour;
}
