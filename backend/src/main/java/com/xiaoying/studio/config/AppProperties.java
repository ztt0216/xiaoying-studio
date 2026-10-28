package com.xiaoying.studio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String storageDir, Files files, Worker worker, Simulation simulation) {

    /** /files/** 签名链接：HMAC 密钥与有效期（秒）。 */
    public record Files(String signingSecret, long urlTtlSeconds) {
    }

    public record Worker(int threads, int queueCapacity) {
    }

    /** failStage 取值 NONE 或某个 {@code TaskStage} 名称。 */
    public record Simulation(long stepDelayMs, String failStage, boolean devApiEnabled) {
    }
}
