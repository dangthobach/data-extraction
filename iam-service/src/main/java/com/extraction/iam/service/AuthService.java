package com.extraction.iam.service;

import com.extraction.iam.dto.ValidateRequest;
import com.extraction.iam.dto.ValidateResponse;
import com.extraction.iam.entity.SystemClient;
import com.extraction.iam.entity.SystemClientStatus;
import com.extraction.iam.repository.SystemClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final SystemClientRepository systemClientRepository;

    public ValidateResponse validate(ValidateRequest request) {
        return systemClientRepository.findByClientId(request.getClientId())
                .map(client -> {
                    if (client.getStatus() != SystemClientStatus.ACTIVE) {
                        return ValidateResponse.builder()
                                .valid(false)
                                .message("Client is not active")
                                .build();
                    }

                    if (verifySecret(request.getClientSecret(), client.getClientSecretHash())) {
                        return ValidateResponse.builder()
                                .valid(true)
                                .clientId(client.getClientId())
                                .clientName(client.getClientName())
                                .scopes(client.getScopes())
                                .dailyLimit(client.getDailyLimit())
                                .build();
                    } else {
                        return ValidateResponse.builder()
                                .valid(false)
                                .message("Invalid secret")
                                .build();
                    }
                })
                .orElse(ValidateResponse.builder()
                        .valid(false)
                        .message("Client not found")
                        .build());
    }

    private boolean verifySecret(String rawSecret, String storedHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(rawSecret.getBytes(StandardCharsets.UTF_8));
            String computedHash = HexFormat.of().formatHex(encodedhash);
            return computedHash.equals(storedHash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not found", e);
            return false;
        }
    }
}
