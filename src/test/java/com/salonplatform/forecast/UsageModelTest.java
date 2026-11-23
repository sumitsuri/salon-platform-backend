package com.salonplatform.forecast;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class UsageModelTest {

    /** 40 weeks of services in 3 categories, and a product that facials use heavily and haircuts barely at all. */
    private static double[][] services(Random rnd, int weeks) {
        double[][] s = new double[weeks][3];
        for (int i = 0; i < weeks; i++) {
            s[i][0] = 80 + rnd.nextInt(40);  // haircuts
            s[i][1] = 20 + rnd.nextInt(30);  // facials
            s[i][2] = 5 + rnd.nextInt(15);   // colour
        }
        return s;
    }

    @Test
    void recoversWhatEachKindOfServiceUses() {
        Random rnd = new Random(4);
        double[][] s = services(rnd, 40);
        double[] u = new double[40];
        for (int i = 0; i < 40; i++) u[i] = 1 * s[i][0] + 15 * s[i][1] + 40 * s[i][2] + rnd.nextGaussian() * 20;
        UsageModel m = UsageModel.fit(s, u);
        assertThat(m.mode()).isEqualTo(UsageModel.Mode.BY_CATEGORY);
        assertThat(m.rates()[0]).isCloseTo(1, offset(0.4));
        assertThat(m.rates()[1]).isCloseTo(15, offset(1.5));
        assertThat(m.rates()[2]).isCloseTo(40, offset(3.0));
        assertThat(m.confidence()).isIn(ForecastEngine.Confidence.HIGH, ForecastEngine.Confidence.MEDIUM);
        // a week with lots of facials needs far more than a week of haircuts
        assertThat(m.need(new double[]{0, 50, 0}, 7)).isGreaterThan(m.need(new double[]{50, 0, 0}, 7) * 5);
    }

    @Test
    void aProductUsedPerServiceRegardlessOfKindFallsBackToOneAverageRate() {
        Random rnd = new Random(8);
        double[][] s = services(rnd, 40);
        double[] u = new double[40];
        for (int i = 0; i < 40; i++) u[i] = 2.5 * (s[i][0] + s[i][1] + s[i][2]) + rnd.nextGaussian() * 15;
        UsageModel m = UsageModel.fit(s, u);
        assertThat(m.need(new double[]{100, 30, 10}, 7)).isCloseTo(2.5 * 140, offset(25.0));
    }

    @Test
    void tooLittleHistoryOrNoUsageIsUnknownRatherThanGuessed() {
        Random rnd = new Random(1);
        assertThat(UsageModel.fit(services(rnd, 5), new double[]{1, 2, 3, 4, 5}).mode()).isEqualTo(UsageModel.Mode.NONE);
        assertThat(UsageModel.fit(services(rnd, 20), new double[20]).mode()).isEqualTo(UsageModel.Mode.NONE);
        assertThat(UsageModel.none(0).confidence()).isEqualTo(ForecastEngine.Confidence.INSUFFICIENT);
    }

    @Test
    void usageIsNeverNegativeAndRatesAreNeverNegative() {
        Random rnd = new Random(2);
        double[][] s = services(rnd, 30);
        double[] u = new double[30];
        for (int i = 0; i < 30; i++) u[i] = Math.max(0, 5 * s[i][1] + rnd.nextGaussian() * 80);
        UsageModel m = UsageModel.fit(s, u);
        for (double r : m.rates()) assertThat(r).isGreaterThanOrEqualTo(0);
        assertThat(m.need(new double[]{0, 0, 0}, 7)).isGreaterThanOrEqualTo(0);
    }
}
