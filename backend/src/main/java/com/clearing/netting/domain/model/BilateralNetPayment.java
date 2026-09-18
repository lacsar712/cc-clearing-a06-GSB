package com.clearing.netting.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Bilateral net payment between two members: fromMemberId pays toMemberId.
 * netAmount is always strictly positive; direction is carried by from/to.
 */
public class BilateralNetPayment {
    private final String fromMemberId;
    private final String toMemberId;
    private final String currency;
    private final BigDecimal netAmount;

    public BilateralNetPayment(String fromMemberId, String toMemberId, String currency, BigDecimal netAmount) {
        this.fromMemberId = Objects.requireNonNull(fromMemberId);
        this.toMemberId = Objects.requireNonNull(toMemberId);
        this.currency = Objects.requireNonNull(currency).toUpperCase();
        this.netAmount = Objects.requireNonNull(netAmount).setScale(8, RoundingMode.HALF_UP);
        if (fromMemberId.equals(toMemberId)) {
            throw new IllegalArgumentException("from and to member must differ");
        }
        if (this.netAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("netAmount must be positive");
        }
    }

    public String getFromMemberId() {
        return fromMemberId;
    }

    public String getToMemberId() {
        return toMemberId;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }
}
