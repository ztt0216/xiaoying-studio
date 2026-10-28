package com.xiaoying.studio.asset;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class FileUrlSignerTest {

    private static final String PATH = "/files/results/0b7c.png";

    private static FileUrlSigner at(long epochSecond, String secret) {
        return new FileUrlSigner(secret, 3600, Clock.fixed(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC));
    }

    private static String param(String url, String name) {
        for (String kv : url.substring(url.indexOf('?') + 1).split("&")) {
            if (kv.startsWith(name + "=")) {
                return kv.substring(name.length() + 1);
            }
        }
        return null;
    }

    @Test
    void signedUrlVerifiesAndIsStableWithinBucket() {
        FileUrlSigner signer = at(10_000, "s");
        String url = signer.sign(PATH);
        assertThat(signer.verify(PATH, param(url, "exp"), param(url, "sig"))).isTrue();
        // 同一时间桶内签名不变，轮询时图片地址不抖动
        assertThat(at(10_500, "s").sign(PATH)).isEqualTo(url);
    }

    @Test
    void rejectsTamperedExpiredOrForeignSignatures() {
        String url = at(10_000, "s").sign(PATH);
        String exp = param(url, "exp");
        String sig = param(url, "sig");

        assertThat(at(10_000, "s").verify("/files/results/other.png", exp, sig)).isFalse();
        assertThat(at(10_000, "s").verify(PATH, String.valueOf(Long.parseLong(exp) + 3600), sig)).isFalse();
        assertThat(at(10_000, "other-secret").verify(PATH, exp, sig)).isFalse();
        assertThat(at(Long.parseLong(exp) + 1, "s").verify(PATH, exp, sig)).isFalse();
        assertThat(at(10_000, "s").verify(PATH, null, null)).isFalse();
    }
}
