package com.worldline.mock.controller;

import com.worldline.mock.service.BatchUploadService;
import com.worldline.mock.service.BatchUploadService.BatchUploadResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * POST /api/internal/batch-upload
 *
 * Accepts multipart upload from the RTA Bank producer system: - file: encrypted
 * CSV file (binary) - encryptedAesKey: RSA-encrypted AES session key (Base64
 * string) - iv: AES initialization vector (Base64 string) - batchId: unique
 * batch identifier
 *
 * Returns JSON with the encrypted result CSV + encrypted AES key + IV, so the
 * producer can decrypt the authorization results.
 *
 * Secured by InternalApiSecurityFilter (X-API-Key + IP whitelist).
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@Slf4j
public class BatchUploadController {

    private final BatchUploadService batchUploadService;

    @PostMapping(value = "/batch-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> receiveBatchUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("encryptedAesKey") String encryptedAesKey,
            @RequestParam("iv") String iv,
            @RequestParam("batchId") String batchId,
            @RequestParam(value = "producerPublicKey", required = false) String producerPublicKey,
            HttpServletRequest request) {

        log.info("📥 Batch upload received [{}] from IP {} — file size: {} bytes",
                batchId, getClientIp(request), file.getSize());

        try {
            // Determine if the file content is raw encrypted bytes or already Base64-encoded text
            byte[] fileBytes = file.getBytes();
            String encryptedContent;

            // Check if the file content looks like Base64 text (all printable ASCII)
            if (isBase64Text(fileBytes)) {
                // Producer sent Base64-encoded encrypted content as the file body
                encryptedContent = new String(fileBytes, StandardCharsets.UTF_8).trim();
                log.debug("  File content detected as Base64 text ({} chars)", encryptedContent.length());
            } else {
                // Producer sent raw encrypted bytes — encode to Base64 for our decrypt service
                encryptedContent = Base64.getEncoder().encodeToString(fileBytes);
                log.debug("  File content detected as raw bytes, Base64-encoded ({} chars)", encryptedContent.length());
            }

            BatchUploadResult result = batchUploadService.process(
                    batchId, encryptedAesKey, encryptedContent, iv, producerPublicKey);

            if ("FAILED".equals(result.status())) {
                return ResponseEntity.internalServerError().body(Map.of(
                        "batchId", result.batchId(),
                        "status", "FAILED",
                        "errorMessage", result.errorMessage() != null ? result.errorMessage() : "Unknown error"
                ));
            }

            // Return encrypted result so producer can decrypt with its private key
            return ResponseEntity.ok(Map.of(
                    "batchId", result.batchId(),
                    "status", result.status(),
                    "totalRecords", result.totalRecords(),
                    "approvedCount", result.approvedCount(),
                    "declinedCount", result.declinedCount(),
                    "encryptedAesKey", result.encryptedAesKey(),
                    "encryptedContent", result.encryptedContent(),
                    "iv", result.iv()
            ));

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "batchId", batchId,
                    "status", "DUPLICATE",
                    "errorMessage", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("❌ Batch upload [{}] error: {}", batchId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "batchId", batchId,
                    "status", "FAILED",
                    "errorMessage", e.getMessage()
            ));
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Heuristic: check if byte array looks like Base64 text.
     */
    private boolean isBase64Text(byte[] data) {
        if (data.length == 0) {
            return false;
        }
        int checkLen = Math.min(data.length, 200);
        for (int i = 0; i < checkLen; i++) {
            byte b = data[i];
            if (!((b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z')
                    || (b >= '0' && b <= '9') || b == '+' || b == '/'
                    || b == '=' || b == '\n' || b == '\r' || b == ' ')) {
                return false;
            }
        }
        return true;
    }
}
