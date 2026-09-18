package com.clearing.netting.adapter.in.web;

import com.clearing.netting.adapter.in.web.auth.AuthContext;
import com.clearing.netting.application.NettingSimulationApplicationService;
import com.clearing.netting.domain.model.BilateralNetPayment;
import com.clearing.netting.domain.model.NetPosition;
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
 * Simulation-only endpoint: compares bilateral vs multilateral netting for the
 * same batch of OPEN obligations. Persists nothing; obligations remain OPEN.
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
        NettingSimulationApplicationService.NettingCompareResult result =
                simulationService.compare(request.settleDate(), request.currency());

        List<BilateralPaymentResponse> payments = result.bilateralPayments().stream()
                .map(BilateralPaymentResponse::from)
                .collect(Collectors.toList());
        List<PositionResponse> positions = result.multilateralPositions().stream()
                .map(PositionResponse::from)
                .collect(Collectors.toList());

        BigDecimal bilateralGross = payments.stream()
                .map(BilateralPaymentResponse::netAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal multilateralGross = positions.stream()
                .map(p -> p.netAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumNet = positions.stream()
                .map(PositionResponse::netAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CompareResponse(
                result.settleDate(),
                result.currency(),
                result.obligationCount(),
                new BilateralSide(payments, payments.size(), bilateralGross),
                new MultilateralSide(positions, positions.size(), multilateralGross, sumNet));
    }

    public record CompareRequest(@NotNull LocalDate settleDate, @NotBlank String currency) {
    }

    public record BilateralPaymentResponse(
            String fromMemberId,
            String toMemberId,
            String currency,
            BigDecimal netAmount) {
        static BilateralPaymentResponse from(BilateralNetPayment p) {
            return new BilateralPaymentResponse(
                    p.getFromMemberId(), p.getToMemberId(), p.getCurrency(), p.getNetAmount());
        }
    }

    public record PositionResponse(
            String memberId,
            String currency,
            BigDecimal netAmount) {
        static PositionResponse from(NetPosition p) {
            return new PositionResponse(p.getMemberId(), p.getCurrency(), p.getNetAmount());
        }
    }

    public record BilateralSide(
            List<BilateralPaymentResponse> payments,
            int paymentCount,
            BigDecimal grossAmount) {
    }

    public record MultilateralSide(
            List<PositionResponse> positions,
            int positionCount,
            BigDecimal grossAmount,
            BigDecimal sumNetAmount) {
    }

    public record CompareResponse(
            LocalDate settleDate,
            String currency,
            int obligationCount,
            BilateralSide bilateral,
            MultilateralSide multilateral) {
    }
}
