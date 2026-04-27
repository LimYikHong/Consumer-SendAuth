package com.worldline.mock.controller;

import com.worldline.mock.entity.AuthorizationResult;
import com.worldline.mock.entity.BatchJob;
import com.worldline.mock.entity.BatchStatus;
import com.worldline.mock.entity.KeyExchangeAudit;
import com.worldline.mock.entity.TransactionRecord;
import com.worldline.mock.repository.BatchJobRepository;
import com.worldline.mock.repository.KeyExchangeAuditRepository;
import com.worldline.mock.repository.TransactionRecordRepository;
import com.worldline.mock.service.KeyPairService;
import com.worldline.mock.service.ProducerKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Dashboard UI controller for tracking batch jobs, authorization results, and
 * key exchange audit logs. Accessible at https://localhost:8881/
 */
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final BatchJobRepository batchJobRepository;
    private final TransactionRecordRepository transactionRecordRepository;
    private final KeyExchangeAuditRepository keyExchangeAuditRepository;
    private final KeyPairService keyPairService;
    private final ProducerKeyService producerKeyService;

    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * Dashboard home — list all batch jobs with pagination & status filter.
     */
    @GetMapping("/")
    public String dashboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String status,
            Model model) {

        Pageable pageable = PageRequest.of(page, DEFAULT_PAGE_SIZE);
        Page<BatchJob> batchPage;

        if (status != null && !status.isBlank()) {
            try {
                BatchStatus batchStatus = BatchStatus.valueOf(status.toUpperCase());
                batchPage = batchJobRepository.findByStatusOrderByReceivedAtDesc(batchStatus, pageable);
            } catch (IllegalArgumentException e) {
                batchPage = batchJobRepository.findAllByOrderByReceivedAtDesc(pageable);
            }
        } else {
            batchPage = batchJobRepository.findAllByOrderByReceivedAtDesc(pageable);
        }

        model.addAttribute("batches", batchPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("statusFilter", status);
        model.addAttribute("statuses", BatchStatus.values());

        // Producer RSA key status for the dashboard button
        ProducerKeyService.KeyStatus keyStatus = producerKeyService.getKeyStatus();
        model.addAttribute("keyStatus", keyStatus);

        return "dashboard";
    }

    /**
     * Batch detail — show all transactions for a specific batch with filtering.
     */
    @GetMapping("/batch/{batchId}")
    public String batchDetail(
            @PathVariable String batchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String result,
            Model model) {

        BatchJob job = batchJobRepository.findByBatchId(batchId).orElse(null);
        if (job == null) {
            model.addAttribute("error", "Batch not found: " + batchId);
            return "error";
        }

        Pageable pageable = PageRequest.of(page, 50);
        Page<TransactionRecord> txnPage;

        if (result != null && !result.isBlank()) {
            try {
                AuthorizationResult authResult = AuthorizationResult.valueOf(result.toUpperCase());
                txnPage = transactionRecordRepository.findByBatchIdAndAuthResultOrderByIdAsc(
                        batchId, authResult, pageable);
            } catch (IllegalArgumentException e) {
                txnPage = transactionRecordRepository.findByBatchIdOrderByIdAsc(batchId, pageable);
            }
        } else {
            txnPage = transactionRecordRepository.findByBatchIdOrderByIdAsc(batchId, pageable);
        }

        model.addAttribute("batch", job);
        model.addAttribute("transactions", txnPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("resultFilter", result);
        model.addAttribute("results", AuthorizationResult.values());
        return "batch-detail";
    }

    /**
     * Key Exchange Audit page — track all public key requests.
     */
    @GetMapping("/key-exchange")
    public String keyExchangeAudit(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String status,
            Model model) {

        Pageable pageable = PageRequest.of(page, DEFAULT_PAGE_SIZE);
        Page<KeyExchangeAudit> auditPage;

        if (status != null && !status.isBlank()) {
            auditPage = keyExchangeAuditRepository.findByStatusOrderByRequestedAtDesc(
                    status.toUpperCase(), pageable);
        } else {
            auditPage = keyExchangeAuditRepository.findAllByOrderByRequestedAtDesc(pageable);
        }

        long totalRequests = keyExchangeAuditRepository.countTotal();
        long successCount = keyExchangeAuditRepository.countByStatus("SUCCESS");
        long failedCount = totalRequests - successCount;

        model.addAttribute("audits", auditPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("statusFilter", status);
        model.addAttribute("totalRequests", totalRequests);
        model.addAttribute("successCount", successCount);
        model.addAttribute("failedCount", failedCount);
        model.addAttribute("keyFingerprint", keyPairService.getPublicKeyFingerprint());
        return "key-exchange";
    }

    /**
     * Handle the "Request/Renew RSA Key" button click. Fetches the producer's
     * RSA public key via internal API.
     */
    @PostMapping("/request-producer-key")
    public String requestProducerKey(RedirectAttributes redirectAttributes) {
        try {
            producerKeyService.fetchProducerKey("dashboard-user");
            redirectAttributes.addFlashAttribute("keyMessage", "✅ Producer RSA key fetched successfully!");
            redirectAttributes.addFlashAttribute("keyMessageType", "success");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("keyMessage", "❌ Failed to fetch producer RSA key: " + e.getMessage());
            redirectAttributes.addFlashAttribute("keyMessageType", "error");
        }
        return "redirect:/";
    }
}
