package com.campuslink.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campuslink.common.BusinessException;import com.campuslink.config.TokenService;
import com.campuslink.domain.*;import com.campuslink.mapper.*;import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;import java.security.*;import java.nio.charset.StandardCharsets;import java.util.*;
@Service @RequiredArgsConstructor public class SessionService {
 private final RefreshSessionMapper sessions;private final UserMapper users;private final TokenService tokens;
 private final SecureRandom random=new SecureRandom();
 @Transactional public Map<String,Object> issue(User u){byte[] b=new byte[32];random.nextBytes(b);String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(b);RefreshSession s=new RefreshSession();s.setUserId(u.getId());s.setTokenHash(hash(raw));s.setCreatedAt(LocalDateTime.now());s.setExpiresAt(LocalDateTime.now().plusDays(7));sessions.insert(s);return Map.of("token",tokens.issue(u.getId(),u.getRole()),"refreshToken",raw,"user",AuthService.userView(u));}
 @Transactional public Map<String,Object> refresh(String raw){RefreshSession old=find(raw);User u=users.lockById(old.getUserId());if(u==null||!States.Account.NORMAL.name().equals(u.getStatus())||!old.getExpiresAt().isAfter(LocalDateTime.now()))throw new BusinessException(401,"刷新凭证已失效");if(sessions.deleteById(old.getId())!=1)throw new BusinessException(401,"刷新凭证已被使用");return issue(u);}
 @Transactional public void logout(String raw){RefreshSession old=find(raw);User u=users.lockById(old.getUserId());users.update(null,new LambdaUpdateWrapper<User>().eq(User::getId,u.getId()).setSql("token_version=token_version+1"));sessions.delete(new LambdaQueryWrapper<RefreshSession>().eq(RefreshSession::getUserId,u.getId()));}
 @Scheduled(cron="${campuslink.refresh-cleanup-cron:0 17 3 * * *}") @Transactional public void cleanupExpired(){sessions.delete(new LambdaQueryWrapper<RefreshSession>().le(RefreshSession::getExpiresAt,LocalDateTime.now()));}
 private RefreshSession find(String raw){if(raw==null||raw.length()!=43)throw new BusinessException(401,"刷新凭证无效");var s=sessions.selectOne(new LambdaQueryWrapper<RefreshSession>().eq(RefreshSession::getTokenHash,hash(raw)));if(s==null)throw new BusinessException(401,"刷新凭证无效");return s;}
 private String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
