package com.termiapislabs.auth;

import com.termiapislabs.dto.auth.*;
import com.termiapislabs.dto.user.UserResponse;
import com.termiapislabs.exception.ApiException;
import com.termiapislabs.model.AuthCodeType;
import com.termiapislabs.model.AuthProvider;
import com.termiapislabs.model.User;
import com.termiapislabs.repository.UserRepository;
import com.termiapislabs.security.JwtService;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailCodeService emailCodeService;
    private final GoogleAuthService googleAuthService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, EmailCodeService emailCodeService,
                       GoogleAuthService googleAuthService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailCodeService = emailCodeService;
        this.googleAuthService = googleAuthService;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePasswordPolicy(String password) {
        if (password == null || password.length() < 8 || password.length() > 72
            || !password.matches(".*[A-Z].*") || !password.matches(".*[a-z].*")
            || !password.matches(".*\\d.*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                "A senha deve ter entre 8 e 72 caracteres e incluir maiúscula, minúscula e número.");
        }
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!request.senha().equals(request.confirmarSenha())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A confirmação de senha não coincide.");
        }
        validatePasswordPolicy(request.senha());
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Já existe uma conta com este e-mail.");
        }

        User user = new User();
        user.setNome(request.nome().trim());
        user.setEmail(email);
        user.setSenhaHash(passwordEncoder.encode(request.senha()));
        user.setProvedorAuth(AuthProvider.LOCAL);
        user.setEmailVerificado(false);
        userRepository.save(user);

        emailCodeService.issueAndSend(user, AuthCodeType.EMAIL_CONFIRMATION, false);
        return AuthResponse.bearer("", UserResponse.from(user));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email).orElseThrow(() ->
            new ApiException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos."));

        if (user.getProvedorAuth() == AuthProvider.GOOGLE || user.getSenhaHash() == null
            || !passwordEncoder.matches(request.senha(), user.getSenhaHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.");
        }
        if (!user.isEmailVerificado()) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                "Confirme seu e-mail antes de entrar. Você pode solicitar o reenvio do código.");
        }
        return AuthResponse.bearer(jwtService.generateToken(user), UserResponse.from(user));
    }

    @Transactional
    public UserResponse confirmEmail(ConfirmEmailRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email).orElseThrow(() ->
            new ApiException(HttpStatus.BAD_REQUEST, "Código inválido ou expirado."));
        emailCodeService.validateAndConsume(user, AuthCodeType.EMAIL_CONFIRMATION, request.codigo());
        user.setEmailVerificado(true);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void resendConfirmationCode(ResendCodeRequest request) {
        String email = normalizeEmail(request.email());
        userRepository.findByEmail(email).ifPresent(user -> {
            if (!user.isEmailVerificado()) {
                emailCodeService.issueAndSend(user, AuthCodeType.EMAIL_CONFIRMATION, true);
            }
        });
    }

    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        var payload = googleAuthService.verify(request.credential());
        String email = normalizeEmail(payload.getEmail());
        String name = payload.get("name") == null ? "Usuário Google" : payload.get("name").toString();

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User created = new User();
            created.setNome(name);
            created.setEmail(email);
            created.setSenhaHash(null);
            created.setProvedorAuth(AuthProvider.GOOGLE);
            created.setEmailVerificado(true);
            return userRepository.save(created);
        });

        user.setEmailVerificado(true);
        if (payload.get("name") != null && (user.getNome() == null || user.getNome().isBlank())) {
            user.setNome(name);
        }
        Object picture = payload.get("picture");

        if (picture != null) {
            user.setFotoUrl(picture.toString());
        }
        userRepository.save(user);
        return AuthResponse.bearer(jwtService.generateToken(user), UserResponse.from(user));
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getSenhaHash() != null) {
                emailCodeService.issueAndSend(user, AuthCodeType.PASSWORD_RESET, true);
            }
        });
    }

    @Transactional
    public AuthResponse resetPassword(ResetPasswordRequest request) {
        if (!request.novaSenha().equals(request.confirmarNovaSenha())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A confirmação da nova senha não coincide.");
        }
        validatePasswordPolicy(request.novaSenha());
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email).orElseThrow(() ->
            new ApiException(HttpStatus.BAD_REQUEST, "Código inválido ou expirado."));

        emailCodeService.validateAndConsume(user, AuthCodeType.PASSWORD_RESET, request.codigo());
        user.setSenhaHash(passwordEncoder.encode(request.novaSenha()));
        userRepository.save(user);
        return AuthResponse.bearer(jwtService.generateToken(user), UserResponse.from(user));
    }
}
