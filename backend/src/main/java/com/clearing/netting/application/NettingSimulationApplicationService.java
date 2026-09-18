package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.BilateralNetPayment;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.NetPosition;
import com.clearing.netting.domain.model.TradeObligation;
import com.clearing.netting.domain.port.out.MemberRepositoryPort;
import com.clearing.netting.domain.port.out.ObligationRepositoryPort;
import com.clearing.netting.domain.service.BilateralNettingService;
import com.clearing.netting.domain.service.MultilateralNettingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only simulation: runs bilateral and multilateral netting over the same
 * batch of OPEN obligations and returns both results side by side.
 * Nothing is persisted — obligations stay OPEN after the simulation.
 */
@Service
public class NettingSimulationApplicationService {

    /** Synthetic run id used only to build transient multilateral positions; never persisted. */
    private static final String SIMULATION_RUN_ID = "SIMULATION";

    private final ObligationRepositoryPort obligationRepository;
    private final MemberRepositoryPort memberRepository;
    private final BilateralNettingService bilateralService;
    private final MultilateralNettingService multilateralService;

    public NettingSimulationApplicationService(
            ObligationRepositoryPort obligationRepository,
            MemberRepositoryPort memberRepository) {
        this.obligationRepository = obligationRepository;
        this.memberRepository = memberRepository;
        this.bilateralService = new BilateralNettingService();
        this.multilateralService = new MultilateralNettingService();
    }

    @Transactional(readOnly = true)
    public NettingCompareResult compare(LocalDate settleDate, String currency) {
        if (settleDate == null) {
            throw new DomainException("INVALID_DATE", "settleDate is required");
        }
        if (currency == null || currency.isBlank()) {
            throw new DomainException("INVALID_CURRENCY", "currency is required");
        }
        String ccy = currency.trim().toUpperCase();

        List<TradeObligation> opens = obligationRepository.findOpenBySettleDateAndCurrency(settleDate, ccy);

        Set<String> memberIds = new HashSet<>();
        for (TradeObligation o : opens) {
            memberIds.add(o.getPayerMemberId());
            memberIds.add(o.getPayeeMemberId());
        }
        Map<String, Member> members = new HashMap<>();
        for (Member m : memberRepository.findByIds(memberIds)) {
            members.put(m.getMemberId(), m);
        }

        List<BilateralNetPayment> bilateral = bilateralService.net(ccy, opens, members);
        List<NetPosition> multilateral = multilateralService.net(SIMULATION_RUN_ID, ccy, opens, members);

        return new NettingCompareResult(settleDate, ccy, opens.size(), bilateral, multilateral);
    }

    public record NettingCompareResult(
            LocalDate settleDate,
            String currency,
            int obligationCount,
            List<BilateralNetPayment> bilateralPayments,
            List<NetPosition> multilateralPositions) {
    }
}
