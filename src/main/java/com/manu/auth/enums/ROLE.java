package com.manu.auth.enums;

import lombok.Getter;

@Getter
public enum ROLE {
    USER("ROLE_USER"),
    ADMIN("ROLE_ADMIN"),
    MANAGER("ROLE_MANAGER");

    private final String name;

    ROLE(String name) {
        this.name = name;
    }
}
