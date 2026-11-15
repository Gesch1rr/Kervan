package com.eyup.kervan.auth.service;

import com.eyup.kervan.auth.dto.AuthResponse;
import com.eyup.kervan.auth.dto.LoginRequest;
import com.eyup.kervan.auth.dto.RefreshTokenRequest;
import com.eyup.kervan.auth.dto.RegisterRequest;
import com.eyup.kervan.auth.exception.InvalidCredentialsException;
import com.eyup.kervan.auth.exception.InvalidRefreshTokenException;
import com.eyup.kervan.auth.exception.PasswordTooLongException;
import com.eyup.kervan.user.dto.UserCredentials;
import com.eyup.kervan.user.dto.UserResponse;
import com.eyup.kervan.user.entity.Role;
import com.eyup.kervan.user.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
public class AuthService {

    // BCrypt rejects longer input; Turkish letters take 2 bytes in UTF-8, so a char-count check is not enough
    static final int BCRYPT_MAX_BYTES = 72;

    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public AuthService(UserService userService,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    public UserResponse register(RegisterRequest request) {
        if (tooLong(request.password())) {
            throw new PasswordTooLongException();
        }
        return userService.create(request.email(), request.password(), request.fullName(), Role.USER);
    }

    public AuthResponse login(LoginRequest request) {
        if (tooLong(request.password())) {
            throw new InvalidCredentialsException();
        }
        Optional<UserCredentials> credentials = userService.findCredentialsByEmail(request.email());

        // always run BCrypt, even for unknown emails, so response time does not reveal which emails are registered
        String hash = credentials.map(UserCredentials::passwordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        if (credentials.isEmpty() || !passwordMatches || !credentials.get().enabled()) {
            throw new InvalidCredentialsException();
        }
        return issueTokens(credentials.get());
    }

    // the new access token uses the user's current role, so role changes apply at the next refresh
    public AuthResponse refresh(RefreshTokenRequest request) {
        Long userId = refreshTokenService.rotate(request.refreshToken());
        UserCredentials user = userService.findCredentialsById(userId)
                .filter(UserCredentials::enabled)
                .orElseThrow(InvalidRefreshTokenException::new);
        return issueTokens(user);
    }

    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private AuthResponse issueTokens(UserCredentials user) {
        return new AuthResponse(
                jwtService.createAccessToken(user.id(), user.role()),
                refreshTokenService.issue(user.id()),
                "Bearer",
                jwtService.accessTokenTtlSeconds()
        );
    }

    private static boolean tooLong(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
    }
}
