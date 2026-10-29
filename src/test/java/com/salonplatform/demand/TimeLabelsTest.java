package com.salonplatform.demand;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeLabelsTest {

    @Test
    void hours() {
        assertEquals("12 am", TimeLabels.hour(0));
        assertEquals("9 am", TimeLabels.hour(9));
        assertEquals("12 pm", TimeLabels.hour(12));
        assertEquals("1 pm", TimeLabels.hour(13));
        assertEquals("8 pm", TimeLabels.hour(20));
        assertEquals("12 am", TimeLabels.hour(24));
    }

    @Test
    void rangesSayAmPmOnceWhenTheyShareAHalfOfTheDay() {
        assertEquals("5-8 pm", TimeLabels.range(17, 20));
        assertEquals("9-11 am", TimeLabels.range(9, 11));
        assertEquals("11 am-2 pm", TimeLabels.range(11, 14));
        assertEquals("10 am-12 pm", TimeLabels.range(10, 12));
        assertEquals("6-7 pm", TimeLabels.range(18, 19));
    }

    @Test
    void days() {
        assertEquals("every day", TimeLabels.days(List.of(1, 2, 3, 4, 5, 6, 7)));
        assertEquals("weekdays", TimeLabels.days(List.of(1, 2, 3, 4, 5)));
        assertEquals("weekends", TimeLabels.days(List.of(7, 6)));
        assertEquals("Tue-Thu", TimeLabels.days(List.of(2, 3, 4)));
        assertEquals("Mon, Wed", TimeLabels.days(List.of(1, 3)));
        assertEquals("Mon, Wed-Fri", TimeLabels.days(List.of(1, 3, 4, 5)));
        assertEquals("Saturdays", TimeLabels.days(List.of(6)));
        assertEquals("Tue, Wed", TimeLabels.days(List.of(2, 3)));
    }
}
