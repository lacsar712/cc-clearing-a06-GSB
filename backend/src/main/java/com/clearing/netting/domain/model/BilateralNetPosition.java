package com.clearing.netting.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Bilateral netting result for one member pair: payer owes payee netAmount.
 * netAmount >= 0; zero means the pair fully offset. Simulation only, never persisted.
 */
public class BilateralNetPosition {
    private final String payerMemberId;
    private final String payeeMemberId;
    private final String currency;
    private final BigDecimal netAmount;

    public BilateralNetPosition(String payerMemberId, String payeeMemberId, String currency, BigDecimal netAmount) {
        this.payerMemberId = Objects.requireNonNull(payerMemberId);
        this.payeeMemberId = Objects.requireNonNull(payeeMemberId);
        this.currency = Objects.requireNonNull(currency).toUpperCase();
        this.netAmount = Objects.requireNonNull(netAmount).setScale(8, RoundingMode.HALF_UP);
        if (this.netAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("bilateral netAmount must be >= 0");
        }
    }

    public String getPayerMemberId() {
        return payerMemberId;
    }

    public String getPayeeMemberId() {
        return payeeMemberId;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }
}
