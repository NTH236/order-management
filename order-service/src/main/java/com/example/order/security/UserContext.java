package com.example.order.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

public class UserContext {

    private static final ThreadLocal<UserInfo> CURRENT_USER = new ThreadLocal<>();

    @Getter
    @AllArgsConstructor
    public static class UserInfo {
        private final String username;
        private final List<String> roles;
    }

    public static void setCurrentUser(UserInfo userInfo) {
        CURRENT_USER.set(userInfo);
    }

    public static UserInfo getCurrentUser() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}

