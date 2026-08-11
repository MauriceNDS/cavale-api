package com.cavale.training.pace;

import java.util.EnumMap;
import java.util.Map;

import com.cavale.training.workout.WorkoutStructure.Allure;

/**
 * The athlete's personal time→distance conversion: flat pace per allure plus
 * the cost of climbing. {@code climbSecPerMeter} is literally the seconds one
 * vertical metre adds on top of the flat pace (pace_hilly = pace_flat +
 * climbSecPerMeter × D+/km, so the s/km-per-(m/km) slope IS s per metre).
 *
 * Two anchors when the data allows it: the easy end comes from the fitted EF
 * pace, the hard end from the critical speed fitted on the best-effort curve
 * — so threshold paces move with quality fitness, not just with easy-run
 * drift. {@code thresholdAnchored} says whether the hard end is personal;
 * without it the quality paces fall back to fixed ratios off EF.
 *
 * {@code personal} tells the caller whether the numbers were fitted on the
 * athlete's own history or are the conservative defaults for a cold start.
 */
public record PaceModel(Map<Allure, Integer> flatSecPerKm, double climbSecPerMeter,
                        int sampleSize, boolean personal, boolean thresholdAnchored,
                        Integer cpSecPerKm) {

    /** Cold-start defaults: 6:30/km EF and a classic-GAP-ish climb cost. */
    static final int DEFAULT_EF_SEC_PER_KM = 390;
    static final double DEFAULT_CLIMB_SEC_PER_METER = 4.0;

    /**
     * Speed multipliers vs EF — the fallback when no critical speed is
     * available. Ratios validated against the athlete's best-effort curve
     * (EF ~5:50, threshold ~4:57, VMA ~4:10).
     */
    private static final Map<Allure, Double> SPEED_VS_EF = Map.of(
            Allure.LENTE, 0.85,
            Allure.EF, 1.0,
            Allure.COURSE, 1.08,
            Allure.SEUIL60, 1.14,
            Allure.SEUIL30, 1.18,
            Allure.VMA, 1.32,
            Allure.SPRINT, 1.5);

    /** COURSE sits at this fraction of the speed gap between EF and Seuil 60. */
    private static final double COURSE_SPEED_FRACTION = 0.57;

    public static PaceModel of(int efSecPerKm, double climbSecPerMeter, int sampleSize, boolean personal) {
        Map<Allure, Integer> paces = new EnumMap<>(Allure.class);
        for (Allure allure : Allure.values()) {
            paces.put(allure, (int) Math.round(efSecPerKm / SPEED_VS_EF.get(allure)));
        }
        return new PaceModel(Map.copyOf(paces), climbSecPerMeter, sampleSize, personal, false, null);
    }

    /**
     * Both anchors personal: EF from the easy-run fit, the quality end from
     * critical speed. Seuil 30 IS the critical pace (~30-40′ sustainable),
     * Seuil 60 sits ~4% slower, VMA comes from the 2-parameter model at six
     * minutes to exhaustion (v6 = CS + D′/360) when D′ is credible.
     */
    public static PaceModel anchored(int efSecPerKm, int cpSecPerKm, Double dPrimeM,
                                     double climbSecPerMeter, int sampleSize) {
        double vEf = 1000.0 / efSecPerKm;
        double vCp = 1000.0 / cpSecPerKm;

        int seuil60 = (int) Math.round(cpSecPerKm * 1.04);
        double vS60 = 1000.0 / seuil60;
        int course = (int) Math.round(1000.0 / (vEf + COURSE_SPEED_FRACTION * (vS60 - vEf)));

        double v6 = dPrimeM != null && dPrimeM >= 80 && dPrimeM <= 600
                ? vCp + dPrimeM / 360.0
                : vCp * 1.12;
        // Meaningfully faster than threshold, but a noisy D' (the 2-parameter
        // fit over few distances inflates it) must not produce fantasy
        // interval paces: physiological vVO2max sits ~5-15% above CS.
        v6 = Math.clamp(v6, vCp * 1.05, vCp * 1.15);
        int vma = (int) Math.round(1000.0 / v6);

        Map<Allure, Integer> paces = new EnumMap<>(Allure.class);
        paces.put(Allure.LENTE, (int) Math.round(efSecPerKm / 0.85));
        paces.put(Allure.EF, efSecPerKm);
        paces.put(Allure.COURSE, course);
        paces.put(Allure.SEUIL60, seuil60);
        paces.put(Allure.SEUIL30, cpSecPerKm);
        paces.put(Allure.VMA, vma);
        paces.put(Allure.SPRINT, (int) Math.round(vma / 1.14));
        return new PaceModel(Map.copyOf(paces), climbSecPerMeter, sampleSize, true, true,
                cpSecPerKm);
    }

    public static PaceModel fallback() {
        return of(DEFAULT_EF_SEC_PER_KM, DEFAULT_CLIMB_SEC_PER_METER, 0, false);
    }

    public int secPerKm(Allure allure) {
        return flatSecPerKm.get(allure == null ? Allure.EF : allure);
    }
}
