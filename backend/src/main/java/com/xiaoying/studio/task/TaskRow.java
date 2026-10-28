package com.xiaoying.studio.task;

import java.time.Instant;

public record TaskRow(Long id, Long userId, String templateId, String templateTitle, String templateCoverUrl,
                      String status, String stage, String failedStage, String errorMessage, String resultUrl,
                      Instant createdAt, Instant updatedAt, Instant startedAt, Instant finishedAt) {
}
