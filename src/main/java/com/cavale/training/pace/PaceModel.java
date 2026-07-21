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
 * {@code personal} tells the caller whether the numbers were fitted on the
 * athlete's own history or are the conservative defaults for a cold start.
 */
public record PaceModel(Map<Allure, Integer> flatSecPerKm, double climbSecPerMeter,
                        int sampleSize, boolean personal) {

    /** Cold-start defaults: 6:30/km EF and a classic-GAP-ish climb cost. */
    static final int DEFAULT_EF_SEC_PER_KM = 390;
    static final double DEFAULT_CLIMB_SEC_PER_METER = 4.0;

    /**
     * Speed multipliers vs EF. Quality blocks are a small share of weekly time,
     * so ratios (validated against the athlete's best-effort curve: EF ~5:50,
     * threshold ~4:57, VMA ~4:10) beat carrying a second fitted model.
     */
    private static final Map<Allure, Double> SPEED_VS_EF = Map.of(
            Allure.LENTE, 0.85,
            Allure.EF, 1.0,
            Allure.COURSE, 1.08,
            Allure.SEUIL60, 1.14,
            Allure.SEUIL30, 1.18,
            Allure.VMA, 1.32,
            Allure.SPRINT, 1.5);

    public static PaceModel of(int efSecPerKm, double climbSecPerMeter, int sampleSize, boolean personal) {
        Map<Allure, Integer> paces = new EnumMap<>(Allure.class);
        for (Allure allure : Allure.values()) {
            paces.put(allure, (int) Math.round(efSecPerKm / SPEED_VS_EF.get(allure)));
        }
        return new PaceModel(Map.copyOf(paces), climbSecPerMeter, sampleSize, personal);
    }

    public static PaceModel fallback() {
        return of(DEFAULT_EF_SEC_PER_KM, DEFAULT_CLIMB_SEC_PER_METER, 0, false);
    }

    public int secPerKm(Allure allure) {
        return flatSecPerKm.get(allure == null ? Allure.EF : allure);
    }
}
