package com.xiaoying.studio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String storageDir, Worker worker, Simulation simulation) {

    public record Worker(int threads, int queueCapacity) {
    }

    /** failStage 取值 NONE 或某个 {@code TaskStage} 名称。 */
    public record Simulation(long stepDelayMs, String failStage, boolean devApiEnabled) {
    }
}
