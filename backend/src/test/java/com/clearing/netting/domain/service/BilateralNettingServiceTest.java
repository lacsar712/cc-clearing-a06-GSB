package com.clearing.netting.domain.service;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.BilateralNetPosition;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.MemberStatus;
import com.clearing.netting.domain.model.ObligationStatus;
import com.clearing.netting.domain.model.TradeObligation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BilateralNettingServiceTest {

    private BilateralNettingService service;
    private Member a;
    private Member b;
    private Member c;
    private LocalDate settleDate;

    @BeforeEach
    void setUp() {
        service = new BilateralNettingService();
        a = new Member("A", "Bank A", MemberStatus.ACTIVE);
        b = new Member("B", "Bank B", MemberStatus.ACTIVE);
        c = new Member("C", "Bank C", MemberStatus.ACTIVE);
        settleDate = LocalDate.of(2026, 9, 10);
    }

    @Test
    void netsEachPairIndependently() {
        List<TradeObligation> opens = List.of(
                obligation("A", "B", "100"),
                obligation("B", "A", "30"),
                obligation("B", "C", "60"),
                obligation("C", "A", "40")
        );
        List<BilateralNetPosition> positions = service.net("USD", opens, Map.of("A", a, "B", b, "C", c));

        assertEquals(3, positions.size());
        Map<String, BilateralNetPosition> byPair = positions.stream()
                .collect(Collectors.toMap(p -> p.getPayerMemberId() + "->" + p.getPayeeMemberId(), p -> p));
        // A<->B: 100 - 30 = 70 A pays B; B<->C: 60 B pays C; A<->C: 40 C pays A
        assertEquals(0, byPair.get("A->B").getNetAmount().compareTo(new BigDecimal("70.00000000")));
        assertEquals(0, byPair.get("B->C").getNetAmount().compareTo(new BigDecimal("60.00000000")));
        assertEquals(0, byPair.get("C->A").getNetAmount().compareTo(new BigDecimal("40.00000000")));
    }

    @Test
    void fullyOffsetPairYieldsZero() {
        List<TradeObligation> opens = List.of(
                obligation("A", "B", "50"),
                obligation("B", "A", "50")
        );
        List<BilateralNetPosition> positions = service.net("USD", opens, Map.of("A", a, "B", b));

        assertEquals(1, positions.size());
        assertEquals(0, positions.get(0).getNetAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    void rejectsEmptyBatch() {
        DomainException ex = assertThrows(DomainException.class, () ->
                service.net("USD", List.of(), Map.of()));
        assertEquals("NO_OBLIGATIONS", ex.getCode());
    }

    @Test
    void rejectsSuspendedMember() {
        Member suspended = new Member("B", "Bank B", MemberStatus.SUSPENDED);
        List<TradeObligation> opens = List.of(obligation("A", "B", "10"));

        DomainException ex = assertThrows(DomainException.class, () ->
                service.net("USD", opens, Map.of("A", a, "B", suspended)));
        assertEquals("SUSPENDED_MEMBER", ex.getCode());
        assertTrue(ex.getMessage().contains("B"));
    }

    @Test
    void rejectsMixedCurrency() {
        TradeObligation usd = obligation("A", "B", "10");
        TradeObligation eur = new TradeObligation(
                "o2", "B", "A", "EUR", new BigDecimal("5"),
                settleDate.minusDays(1), settleDate, ObligationStatus.OPEN, null);

        DomainException ex = assertThrows(DomainException.class, () ->
                service.net("USD", List.of(usd, eur), Map.of("A", a, "B", b)));
        assertEquals("MIXED_CURRENCY", ex.getCode());
    }

    private TradeObligation obligation(String payer, String payee, String amount) {
        return new TradeObligation(
                java.util.UUID.randomUUID().toString(),
                payer,
                payee,
                "USD",
                new BigDecimal(amount),
                settleDate.minusDays(1),
                settleDate,
                ObligationStatus.OPEN,
                null);
    }
}
