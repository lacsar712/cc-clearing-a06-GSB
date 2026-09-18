package com.clearing.netting.domain.service;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.BilateralNetPosition;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.MemberStatus;
import com.clearing.netting.domain.model.TradeObligation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Pure domain service: bilateral netting for a single currency.
 * Each member pair is netted independently; the result is one net payment
 * direction per pair (netAmount >= 0, zero = fully offset).
 */
public class BilateralNettingService {

    public List<BilateralNetPosition> net(
            String currency,
            List<TradeObligation> openObligations,
            Map<String, Member> membersById) {

        if (openObligations == null || openObligations.isEmpty()) {
            throw new DomainException("NO_OBLIGATIONS", "no OPEN obligations for settleDate/currency");
        }

        Set<String> currencies = new HashSet<>();
        for (TradeObligation o : openObligations) {
            currencies.add(o.getCurrency());
            if (!currency.equalsIgnoreCase(o.getCurrency())) {
                throw new DomainException("MIXED_CURRENCY", "mixed currency obligations are not allowed");
            }
            if (o.getAmount() == null || o.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new DomainException("INVALID_AMOUNT", "invalid amount on obligation " + o.getObligationId());
            }
        }
        if (currencies.size() > 1) {
            throw new DomainException("MIXED_CURRENCY", "mixed currency obligations are not allowed");
        }

        Set<String> involved = new HashSet<>();
        for (TradeObligation o : openObligations) {
            involved.add(o.getPayerMemberId());
            involved.add(o.getPayeeMemberId());
        }

        for (String memberId : involved) {
            Member member = membersById.get(memberId);
            if (member == null) {
                throw new DomainException("MEMBER_NOT_FOUND", "member not found: " + memberId);
            }
            if (member.getStatus() == MemberStatus.SUSPENDED) {
                throw new DomainException("SUSPENDED_MEMBER", "suspended member rejected: " + memberId);
            }
        }

        // net each unordered pair: key = "lowId|highId", value = low->high net (negative = high->low)
        Map<String, BigDecimal> pairNets = new TreeMap<>();
        for (TradeObligation o : openObligations) {
            String payer = o.getPayerMemberId();
            String payee = o.getPayeeMemberId();
            String low = payer.compareTo(payee) < 0 ? payer : payee;
            String high = payer.compareTo(payee) < 0 ? payee : payer;
            String key = low + "|" + high;
            BigDecimal signed = payer.equals(low) ? o.getAmount() : o.getAmount().negate();
            pairNets.merge(key, signed, BigDecimal::add);
        }

        List<BilateralNetPosition> positions = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : pairNets.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            BigDecimal net = e.getValue().setScale(8, RoundingMode.HALF_UP);
            String payer = net.signum() >= 0 ? parts[0] : parts[1];
            String payee = net.signum() >= 0 ? parts[1] : parts[0];
            positions.add(new BilateralNetPosition(payer, payee, currency.toUpperCase(), net.abs()));
        }
        return positions;
    }
}
