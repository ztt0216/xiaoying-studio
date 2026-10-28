package com.xiaoying.studio.asset;

import com.xiaoying.studio.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * /files/** 的签名链接。&lt;img&gt; 无法携带 Authorization 头，所以由已鉴权的接口
 * （任务查询、上传）返回带 exp/sig 的地址，FileAccessFilter 校验后才放行。
 *
 * <p>过期时间按 ttl 对齐到时间桶：同一桶内对同一路径签出的 URL 完全相同，
 * 前端轮询时图片地址不会每次变化导致重复加载。实际有效期在 ttl 到 2×ttl 之间。
 */
@Component
public class FileUrlSigner {

    private final byte[] key;
    private final long ttlSeconds;
    private final Clock clock;

    @Autowired
    public FileUrlSigner(AppProperties props) {
        this(props.files().signingSecret(), props.files().urlTtlSeconds(), Clock.systemUTC());
    }

    public FileUrlSigner(String secret, long ttlSeconds, Clock clock) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.files.signing-secret 不能为空");
        }
        this.key = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = Math.max(60, ttlSeconds);
        this.clock = clock;
    }

    /** 对 /files/... 路径签名；null 原样返回。 */
    public String sign(String path) {
        if (path == null) {
            return null;
        }
        long now = clock.instant().getEpochSecond();
        long exp = (now / ttlSeconds + 2) * ttlSeconds;
        return path + "?exp=" + exp + "&sig=" + hmac(path, exp);
    }

    public boolean verify(String path, String exp, String sig) {
        if (path == null || exp == null || sig == null) {
            return false;
        }
        long expiresAt;
        try {
            expiresAt = Long.parseLong(exp);
        } catch (NumberFormatException e) {
            return false;
        }
        if (expiresAt < clock.instant().getEpochSecond()) {
            return false;
        }
        return MessageDigest.isEqual(hmac(path, expiresAt).getBytes(StandardCharsets.US_ASCII),
                sig.getBytes(StandardCharsets.US_ASCII));
    }

    private String hmac(String path, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal((path + "|" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
