package com.manu.auth.dto.request;

import lombok.*;

@AllArgsConstructor
@Builder
@Getter
@Setter
public class LoginRequest {
    private String username;
    private String password;
}
