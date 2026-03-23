package com.manu.auth.dto.request;

import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class SignupRequest {
    private String name;
    private String email;
    private String password;
}
