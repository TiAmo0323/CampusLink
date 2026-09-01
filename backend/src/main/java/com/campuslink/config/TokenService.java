package com.campuslink.config;

import com.campuslink.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class TokenService {
    @Value("${campuslink.token-secret}") private String secret;
    @Value("${campuslink.token-hours:24}") private long tokenHours;

    public String issue(Long userId, String role) {
        String payload = userId + ":" + role + ":" + Instant.now().plusSeconds(tokenHours * 3600).getEpochSecond();
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + sign(encoded);
    }

    public TokenIdentity verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2 || !constantEquals(sign(parts[0]), parts[1])) throw new IllegalArgumentException();
            String[] payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split(":");
            if (Long.parseLong(payload[2]) < Instant.now().getEpochSecond()) throw new BusinessException(401, "登录已过期");
            return new TokenIdentity(Long.parseLong(payload[0]), payload[1]);
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException(401, "登录凭证无效"); }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private boolean constantEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
    public record TokenIdentity(Long userId, String role) {}
}
