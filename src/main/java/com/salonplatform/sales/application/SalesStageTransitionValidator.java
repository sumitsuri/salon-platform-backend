package com.salonplatform.sales.application;

import com.salonplatform.sales.domain.enums.LeadStage;
import com.salonplatform.exception.BadRequestException;

import java.util.EnumSet;
import java.util.Set;

public final class SalesStageTransitionValidator {

    private static final Set<LeadStage> TERMINAL = EnumSet.of(LeadStage.WON, LeadStage.LOST);

    private SalesStageTransitionValidator() {}

    public static void validateTransition(LeadStage from, LeadStage to) {
        if (from == to) {
            throw new BadRequestException("Lead is already in stage " + to);
        }
        if (TERMINAL.contains(from)) {
            throw new BadRequestException("Cannot change stage from terminal state " + from);
        }
        if (to == LeadStage.WON) {
            if (from != LeadStage.FREE_TRIAL && from != LeadStage.INTERESTED) {
                throw new BadRequestException("Lead must reach Free Trial or Interested before marking as Won");
            }
            return;
        }
        if (to == LeadStage.LOST && from == LeadStage.WON) {
            throw new BadRequestException("Invalid transition");
        }
        if (ordinal(to) <= ordinal(from) && to != LeadStage.LOST) {
            throw new BadRequestException("Can only move forward in pipeline or mark as LOST");
        }
        // Allow skipping CONTACTED (NEW -> QUALIFIED or beyond)
        if (from == LeadStage.NEW && to == LeadStage.CONTACTED) {
            return;
        }
        if (ordinal(to) - ordinal(from) > 2 && to != LeadStage.LOST) {
            throw new BadRequestException("Cannot skip more than one stage (except CONTACTED)");
        }
    }

    public static void validateQualifiedFields(String localityName, String useCase, com.salonplatform.sales.domain.enums.LeadType leadType) {
        if (localityName == null || localityName.isBlank()) {
            throw new BadRequestException("Locality is required for QUALIFIED stage");
        }
        if (useCase == null || useCase.isBlank()) {
            throw new BadRequestException("Use case is required for QUALIFIED stage");
        }
        if (leadType == null) {
            throw new BadRequestException("Lead type is required for QUALIFIED stage");
        }
    }

    private static int ordinal(LeadStage stage) {
        return switch (stage) {
            case NEW -> 0;
            case CONTACTED -> 1;
            case QUALIFIED -> 2;
            case PITCHED -> 3;
            case INTERESTED -> 4;
            case FREE_TRIAL -> 5;
            case WON -> 6;
            case LOST -> 7;
        };
    }
}
