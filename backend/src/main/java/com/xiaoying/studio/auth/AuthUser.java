package com.xiaoying.studio.auth;

import java.time.Instant;

public record AuthUser(Long id, String nickname, Instant createdAt) {
}
