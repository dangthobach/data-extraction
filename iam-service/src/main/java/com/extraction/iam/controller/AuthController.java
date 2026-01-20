package com.extraction.iam.controller;

import com.extraction.iam.dto.ValidateRequest;
import com.extraction.iam.dto.ValidateResponse;
import com.extraction.iam.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/validate")
    public ResponseEntity<ValidateResponse> validate(@RequestBody @Valid ValidateRequest request) {
        ValidateResponse response = authService.validate(request);
        if (response.isValid()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(401).body(response);
        }
    }
}
