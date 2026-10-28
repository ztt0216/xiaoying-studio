package com.xiaoying.studio.task;

import java.time.Instant;

public record TaskStepRow(Long id, Long taskId, String stage, String status, String message,
                          Instant startedAt, Instant finishedAt) {
}
