package com.clearing.netting.adapter.in.web;

import com.clearing.netting.adapter.in.web.auth.AuthContext;
import com.clearing.netting.application.NettingSimulationApplicationService;
import com.clearing.netting.domain.model.BilateralNetPosition;
import com.clearing.netting.domain.model.NetPosition;
import com.clearing.netting.domain.model.ObligationStatus;
import com.clearing.netting.domain.model.TradeObligation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Simulation-only endpoints: compute netting outcomes without changing any state.
 */
@RestController
@RequestMapping("/api/netting-simulations")
public class NettingSimulationController {

    private final NettingSimulationApplicationService simulationService;

    public NettingSimulationController(NettingSimulationApplicationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/compare")
    public CompareResponse compare(@Valid @RequestBody CompareRequest request) {
        AuthContext.requireOperator();
        NettingSimulationApplicationService.NettingComparisonResult result =
                simulationService.compare(request.settleDate(), request.currency());
        return new CompareResponse(
                result.settleDate(),
                result.currency(),
                result.obligations().stream().map(ObligationBrief::from).collect(Collectors.toList()),
                new BilateralSide(
                        result.bilateralPositions().stream()
                                .map(BilateralPositionResponse::from)
                                .collect(Collectors.toList()),
                        result.bilateralTotalNet()),
                new MultilateralSide(
                        result.multilateralPositions().stream()
                                .map(MultilateralPositionResponse::from)
                                .collect(Collectors.toList()),
                        result.multilateralTotalNet(),
                        result.multilateralSumNet()),
                result.grossAmount());
    }

    public record CompareRequest(@NotNull LocalDate settleDate, @NotBlank String currency) {
    }

    public record ObligationBrief(
            String obligationId,
            String payerMemberId,
            String payeeMemberId,
            String currency,
            BigDecimal amount,
            ObligationStatus status) {
        static ObligationBrief from(TradeObligation o) {
            return new ObligationBrief(
                    o.getObligationId(),
                    o.getPayerMemberId(),
                    o.getPayeeMemberId(),
                    o.getCurrency(),
                    o.getAmount(),
                    o.getStatus());
        }
    }

    public record BilateralPositionResponse(
            String payerMemberId,
            String payeeMemberId,
            String currency,
            BigDecimal netAmount) {
        static BilateralPositionResponse from(BilateralNetPosition p) {
            return new BilateralPositionResponse(
                    p.getPayerMemberId(),
                    p.getPayeeMemberId(),
                    p.getCurrency(),
                    p.getNetAmount());
        }
    }

    public record MultilateralPositionResponse(
            String memberId,
            String currency,
            BigDecimal netAmount) {
        static MultilateralPositionResponse from(NetPosition p) {
            return new MultilateralPositionResponse(
                    p.getMemberId(),
                    p.getCurrency(),
                    p.getNetAmount());
        }
    }

    public record BilateralSide(List<BilateralPositionResponse> positions, BigDecimal totalNetAmount) {
    }

    public record MultilateralSide(
            List<MultilateralPositionResponse> positions,
            BigDecimal totalNetAmount,
            BigDecimal sumNetAmount) {
    }

    public record CompareResponse(
            LocalDate settleDate,
            String currency,
            List<ObligationBrief> obligations,
            BilateralSide bilateral,
            MultilateralSide multilateral,
            BigDecimal grossAmount) {
    }
}
