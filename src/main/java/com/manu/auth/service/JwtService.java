package com.manu.auth.service;

import com.manu.auth.enums.ROLE;
import com.manu.auth.model.User;
import com.manu.auth.utils.KeyLoader;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final KeyLoader keyLoader;

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(60 * 15);

        return Jwts.builder()
                .claims(prepareClaims(user))
                .subject(String.valueOf(user.getId()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(keyLoader.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    private Map<String, Object> prepareClaims(User user) {
        return Map.of(
                "username", user.getUsername(),
                "roles", user.getRoles()
                        .stream()
                        .map(ROLE::getName)
                        .toList()
        );
    }
}
