package com.manu.auth.service.implementation;

import com.manu.auth.dto.request.LoginRequest;
import com.manu.auth.dto.request.SignupRequest;
import com.manu.auth.dto.response.AuthResponse;
import com.manu.auth.enums.ROLE;
import com.manu.auth.model.User;
import com.manu.auth.model.UserPrincipal;
import com.manu.auth.repository.UserRepository;
import com.manu.auth.service.AuthService;
import com.manu.auth.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Override
    public ResponseEntity<AuthResponse> signup(SignupRequest request) {
        var user = User.builder()
                        .username(request.getEmail())
                        .password(passwordEncoder.encode(request.getPassword()))
                        .roles(List.of(ROLE.USER))
                        .build();

        var response = userRepository.save(user);

        if (response.getId() != null) {
            return ResponseEntity.ok(AuthResponse.builder()
                            .token(jwtService.generateToken(user))
                            .build());
        }
        return ResponseEntity.badRequest().body(null);
    }

    @Override
    public ResponseEntity<AuthResponse> login(LoginRequest request) {

        // Authenticate user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        // Get Authenticated user
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        assert userPrincipal != null;
        User user = userPrincipal.getUser();

        // Generate JWT
        String token = jwtService.generateToken(user);

        return ResponseEntity.ok(
                AuthResponse.builder()
                        .token(token)
                        .build()
        );
    }
}
