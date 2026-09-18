package com.clearing.netting.application;

import com.clearing.netting.domain.model.BilateralNetPosition;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.NetPosition;
import com.clearing.netting.domain.model.ObligationStatus;
import com.clearing.netting.domain.model.TradeObligation;
import com.clearing.netting.domain.port.out.MemberRepositoryPort;
import com.clearing.netting.domain.port.out.NettingRunRepositoryPort;
import com.clearing.netting.domain.port.out.ObligationRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boots the full context on H2 and verifies the compare simulation:
 * same OPEN batch yields both bilateral and multilateral results,
 * and nothing is persisted — obligations stay OPEN, no runs created.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:simtest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class NettingSimulationApplicationServiceTest {

    @Autowired
    private NettingSimulationApplicationService simulationService;
    @Autowired
    private ObligationRepositoryPort obligationRepository;
    @Autowired
    private MemberRepositoryPort memberRepository;
    @Autowired
    private NettingRunRepositoryPort runRepository;

    private final LocalDate settleDate = LocalDate.of(2026, 9, 18);
    private Member a;
    private Member b;
    private Member c;

    @BeforeEach
    void setUp() {
        a = memberRepository.save(Member.create("Alpha Bank"));
        b = memberRepository.save(Member.create("Beta Securities"));
        c = memberRepository.save(Member.create("Gamma Clearing"));
        // mirrors the seed batch
        obligationRepository.save(TradeObligation.open(
                a.getMemberId(), b.getMemberId(), "USD", new BigDecimal("100000"), settleDate.minusDays(1), settleDate));
        obligationRepository.save(TradeObligation.open(
                b.getMemberId(), c.getMemberId(), "USD", new BigDecimal("60000"), settleDate.minusDays(1), settleDate));
        obligationRepository.save(TradeObligation.open(
                c.getMemberId(), a.getMemberId(), "USD", new BigDecimal("40000"), settleDate.minusDays(1), settleDate));
        obligationRepository.save(TradeObligation.open(
                a.getMemberId(), c.getMemberId(), "USD", new BigDecimal("25000"), settleDate.minusDays(1), settleDate));
    }

    @Test
    void sameBatchYieldsBothSidesAndKeepsStateUntouched() {
        NettingSimulationApplicationService.NettingComparisonResult result =
                simulationService.compare(settleDate, "USD");

        // bilateral side: A->B 100000, B->C 60000, C->A 15000 (40000 - 25000)
        assertEquals(4, result.obligations().size());
        assertEquals(3, result.bilateralPositions().size());
        Map<String, BigDecimal> bilateralByPair = result.bilateralPositions().stream()
                .collect(Collectors.toMap(
                        p -> p.getPayerMemberId() + "->" + p.getPayeeMemberId(),
                        BilateralNetPosition::getNetAmount));
        assertEquals(0, bilateralByPair.get(a.getMemberId() + "->" + b.getMemberId())
                .compareTo(new BigDecimal("100000.00000000")));
        assertEquals(0, bilateralByPair.get(b.getMemberId() + "->" + c.getMemberId())
                .compareTo(new BigDecimal("60000.00000000")));
        assertEquals(0, bilateralByPair.get(c.getMemberId() + "->" + a.getMemberId())
                .compareTo(new BigDecimal("15000.00000000")));
        assertEquals(0, result.bilateralTotalNet().compareTo(new BigDecimal("175000.00000000")));

        // multilateral side: A=-85000, B=+40000, C=+45000, Σnet=0
        Map<String, BigDecimal> multiByMember = result.multilateralPositions().stream()
                .collect(Collectors.toMap(NetPosition::getMemberId, NetPosition::getNetAmount));
        assertEquals(0, multiByMember.get(a.getMemberId()).compareTo(new BigDecimal("-85000.00000000")));
        assertEquals(0, multiByMember.get(b.getMemberId()).compareTo(new BigDecimal("40000.00000000")));
        assertEquals(0, multiByMember.get(c.getMemberId()).compareTo(new BigDecimal("45000.00000000")));
        assertEquals(0, result.multilateralSumNet().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.multilateralTotalNet().compareTo(new BigDecimal("85000.00000000")));

        // simulation only: obligations remain OPEN, no netting run persisted
        List<TradeObligation> after = obligationRepository.findByFilters("USD", settleDate, null);
        assertEquals(4, after.size());
        assertTrue(after.stream().allMatch(o -> o.getStatus() == ObligationStatus.OPEN));
        assertTrue(after.stream().allMatch(o -> o.getNettingRunId() == null));
        assertTrue(runRepository.findAllOrderByCreatedAtDesc().isEmpty());
    }
}
