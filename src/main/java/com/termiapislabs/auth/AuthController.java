package com.termiapislabs.auth;

import com.termiapislabs.dto.auth.*;
import com.termiapislabs.dto.user.UserResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request).user());
    }

    @PostMapping("/confirm-email")
    public ResponseEntity<UserResponse> confirmEmail(@Valid @RequestBody ConfirmEmailRequest request) {
        return ResponseEntity.ok(authService.confirmEmail(request));
    }

    @PostMapping("/resend-code")
    public ResponseEntity<Map<String, String>> resendCode(@Valid @RequestBody ResendCodeRequest request) {
        authService.resendConfirmationCode(request);
        return ResponseEntity.ok(Map.of("message",
            "Se a conta existir e estiver pendente, um novo código será enviado."));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authService.googleLogin(request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(Map.of("message",
            "Se o e-mail estiver cadastrado, você receberá as instruções."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        return ResponseEntity.ok(Map.of("message", "Logout realizado. Descarte o token no cliente."));
    }
}
