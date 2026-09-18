package com.clearing.netting.domain.service;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.BilateralNetPayment;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.MemberStatus;
import com.clearing.netting.domain.model.TradeObligation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Pure domain service: bilateral netting per member pair for a single currency.
 * Each unordered pair settles at most one net payment (from debtor to creditor).
 */
public class BilateralNettingService {

    public List<BilateralNetPayment> net(
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

        // net per unordered pair, signed from the lexicographically smaller member's view
        Map<String, BigDecimal> netByPair = new TreeMap<>();
        for (TradeObligation o : openObligations) {
            String payer = o.getPayerMemberId();
            String payee = o.getPayeeMemberId();
            String first = payer.compareTo(payee) < 0 ? payer : payee;
            String second = payer.compareTo(payee) < 0 ? payee : payer;
            String key = first + "|" + second;
            BigDecimal signed = payer.equals(first) ? o.getAmount().negate() : o.getAmount();
            netByPair.merge(key, signed, BigDecimal::add);
        }

        List<BilateralNetPayment> payments = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : netByPair.entrySet()) {
            BigDecimal net = e.getValue().setScale(8, RoundingMode.HALF_UP);
            if (net.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            String first = e.getKey().substring(0, e.getKey().indexOf('|'));
            String second = e.getKey().substring(e.getKey().indexOf('|') + 1);
            if (net.signum() < 0) {
                payments.add(new BilateralNetPayment(first, second, currency.toUpperCase(), net.negate()));
            } else {
                payments.add(new BilateralNetPayment(second, first, currency.toUpperCase(), net));
            }
        }

        return payments;
    }
}
