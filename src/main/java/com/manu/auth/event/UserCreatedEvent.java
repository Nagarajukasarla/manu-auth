package com.manu.auth.event;

import lombok.*;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class UserCreatedEvent {
    private String userId;
    private String email;
    private String name;
//    private LocalDateTime createdAt;
}
