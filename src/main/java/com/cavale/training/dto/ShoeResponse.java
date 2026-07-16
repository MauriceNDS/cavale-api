package com.cavale.training.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import com.cavale.training.domain.Shoe;
import com.cavale.training.domain.ShoePurpose;

/**
 * A shoe with its accrued mileage. needsRetirement flags a still-active pair
 * that has reached its retirement threshold — time to replace it.
 */
public record ShoeResponse(
        UUID id,
        String name,
        String brand,
        String color,
        ShoePurpose purpose,
        Integer retirementKm,
        boolean retired,
        boolean isDefault,
        BigDecimal mileageKm,
        boolean needsRetirement) {

    public static ShoeResponse from(Shoe shoe, BigDecimal mileageKm) {
        BigDecimal km = (mileageKm != null ? mileageKm : BigDecimal.ZERO).setScale(1, RoundingMode.HALF_UP);
        boolean needsRetirement = !shoe.isRetired() && shoe.getRetirementKm() != null
                && km.doubleValue() >= shoe.getRetirementKm();
        return new ShoeResponse(shoe.getId(), shoe.getName(), shoe.getBrand(), shoe.getColor(),
                shoe.getPurpose(), shoe.getRetirementKm(), shoe.isRetired(), shoe.isDefault(),
                km, needsRetirement);
    }
}
