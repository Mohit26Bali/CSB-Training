package com.csb.banking.controller;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.csb.banking.dto.AmountRequest;
import com.csb.banking.dto.PageResponse;
import com.csb.banking.dto.TransactionResponse;
import com.csb.banking.dto.TransferRequest;
import com.csb.banking.dto.TransactionFilter;
import com.csb.banking.dto.TransferResponse;
import com.csb.banking.entity.TransactionDirection;
import com.csb.banking.entity.TransactionStatus;
import com.csb.banking.entity.TransactionType;
import com.csb.banking.service.TransactionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * The money endpoints from the project description (section 17).
 *
 * <pre>
 * POST /api/accounts/{accountNumber}/deposit        deposit  (CUSTOMER, owner)
 * POST /api/accounts/{accountNumber}/withdraw       withdraw (CUSTOMER, owner)
 * POST /api/transfers                               transfer (CUSTOMER, owner of the source account)
 * GET  /api/accounts/{accountNumber}/transactions   history, newest first (owner or ADMIN), with optional paging and
 *        filters: ?page=0&amp;size=20&amp;from=2026-10-01&amp;to=2026-10-31&amp;type=TRANSFER&amp;status=SUCCESS
 *        &amp;direction=CREDIT&amp;minAmount=100&amp;maxAmount=5000 (dates inclusive, all filters optional, combined with AND)
 * </pre>
 * The three POST endpoints accept an optional {@code Idempotency-Key} header (a UUID chosen by the client per user
 * action): a repeat of the same request returns the first response instead of moving money twice.
 *
 * <p>Kept apart from {@code AccountController} (account data) so account management and money movement evolve
 * separately. All logic is in {@code service/TransactionService}; bodies are {@code dto/AmountRequest} and
 * {@code dto/TransferRequest}. Business failures surface as 4xx through {@code exception/GlobalExceptionHandler}.
 */
@Tag(name = "Transactions", description = "Deposit, withdraw, transfer and transaction history")
@RestController
@RequestMapping("/api")
public class TransactionController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(summary = "Deposit money", description = "CUSTOMER, owner of the account. The account must be ACTIVE and the amount greater than zero.")
    @PostMapping("/accounts/{accountNumber}/deposit")
    public TransactionResponse deposit(@PathVariable String accountNumber,
                                       @Valid @RequestBody AmountRequest request,
                                       @Parameter(description = "Optional UUID chosen per user action. A repeat with the same key is not executed again; the first response is returned.") @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
                                       Authentication authentication) {
        return transactionService.deposit(accountNumber, request, idempotencyKey, authentication);
    }

    @Operation(summary = "Withdraw money", description = "CUSTOMER, owner of the account. Fails with 422 if the balance is insufficient; the balance is then unchanged.")
    @PostMapping("/accounts/{accountNumber}/withdraw")
    public TransactionResponse withdraw(@PathVariable String accountNumber,
                                        @Valid @RequestBody AmountRequest request,
                                        @Parameter(description = "Optional UUID chosen per user action. A repeat with the same key is not executed again; the first response is returned.") @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
                                        Authentication authentication) {
        return transactionService.withdraw(accountNumber, request, idempotencyKey, authentication);
    }

    @Operation(summary = "Transfer between accounts", description = "CUSTOMER, owner of the source account. Both accounts must exist and be ACTIVE, differ from each other, and the source needs enough balance. Writes a debit and a credit entry with one shared reference.")
    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request,
                                                     @Parameter(description = "Optional UUID chosen per user action. A repeat with the same key is not executed again; the first response is returned.") @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
                                                     Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.transfer(request, idempotencyKey, authentication));
    }

    @Operation(summary = "Transaction history", description = "Newest first, paged, with optional filters (all combined with AND): date range (`from`/`to`, inclusive), `type`, `status`, `direction`, and amount range (`minAmount`/`maxAmount`).")
    @GetMapping("/accounts/{accountNumber}/transactions")
    public PageResponse<TransactionResponse> history(
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) TransactionDirection direction,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            Authentication authentication) {
        TransactionFilter filter = new TransactionFilter(from, to, type, status, direction, minAmount, maxAmount);
        return transactionService.getHistory(accountNumber, page, size, filter, authentication);
    }
}
