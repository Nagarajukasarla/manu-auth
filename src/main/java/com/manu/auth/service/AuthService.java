package com.manu.auth.service;

import com.manu.auth.dto.request.LoginRequest;
import com.manu.auth.dto.request.SignupRequest;
import com.manu.auth.dto.response.AuthResponse;
import org.springframework.http.ResponseEntity;

public interface AuthService {
    ResponseEntity<AuthResponse> login(LoginRequest request);
    ResponseEntity<AuthResponse> signup(SignupRequest request);
}
