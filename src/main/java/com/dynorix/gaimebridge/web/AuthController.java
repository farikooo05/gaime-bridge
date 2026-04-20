package com.dynorix.gaimebridge.web;

import com.dynorix.gaimebridge.application.port.TaxPortalClient;
import com.dynorix.gaimebridge.dto.AuthTaxpayerSelectionRequest;
import com.dynorix.gaimebridge.dto.AuthVerificationRequest;
import com.dynorix.gaimebridge.dto.AuthVerificationResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final TaxPortalClient taxPortalClient;

    public AuthController(TaxPortalClient taxPortalClient) {
        this.taxPortalClient = taxPortalClient;
    }

    @PostMapping("/start-verification")
    public AuthVerificationResponse startVerification(@Valid @RequestBody AuthVerificationRequest request) {
        return taxPortalClient.startVerification(request.portalPhone(), request.portalUserId());
    }

    @PostMapping("/confirm-taxpayer")
    public void confirmTaxpayer(@Valid @RequestBody AuthTaxpayerSelectionRequest request) {
        taxPortalClient.confirmTaxpayer(request.sessionId(), request.legalTin());
    }

    @PostMapping("/cancel")
    public void cancel() {
        taxPortalClient.cancelVerification();
    }

    @GetMapping("/status")
    public com.dynorix.gaimebridge.dto.AuthStatusResponse getAuthStatus() {
        return taxPortalClient.checkSession();
    }

    @PostMapping("/logout")
    public void logout() {
        taxPortalClient.logout();
    }
}
