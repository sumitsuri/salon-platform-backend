package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SlowDayAction {
    private String day;
    private String dayLabel;
    private String severity;
    private String headline;
    private String insight;
    private String metricLabel;
    private String metricValue;
    private List<String> actions;
}
