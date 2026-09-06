package com.campuslink.config;
import com.campuslink.common.BusinessException;
import com.campuslink.domain.States;
import com.campuslink.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;import java.time.Instant;import java.util.*;
@Service @RequiredArgsConstructor public class TokenService {
 private final ObjectMapper json;private final UserMapper users;
 @Value("${campuslink.token-secret}") private String secret;
 @Value("${campuslink.token-hours:2}") private long hours;
 private String enc(byte[] b){return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
 public String issue(Long uid,String role){try{long now=Instant.now().getEpochSecond();String h=enc(json.writeValueAsBytes(Map.of("alg","HS256","typ","JWT")));String p=enc(json.writeValueAsBytes(Map.of("sub",uid.toString(),"role",role,"iss","campuslink","aud","campuslink-client","iat",now,"exp",now+hours*3600,"ver",users.selectById(uid).getTokenVersion(),"jti",UUID.randomUUID().toString())));return h+"."+p+"."+enc(sign(h+"."+p));}catch(Exception e){throw new IllegalStateException(e);}}
 public TokenIdentity verify(String raw){try{
  String[] parts=raw.split("\\.",-1);if(parts.length!=3||raw.length()>4096)throw new IllegalArgumentException();
  var h=json.readTree(Base64.getUrlDecoder().decode(parts[0]));if(!"HS256".equals(h.path("alg").asText())||!"JWT".equals(h.path("typ").asText()))throw new IllegalArgumentException();
  if(!java.security.MessageDigest.isEqual(sign(parts[0]+"."+parts[1]),Base64.getUrlDecoder().decode(parts[2])))throw new IllegalArgumentException();
  var p=json.readTree(Base64.getUrlDecoder().decode(parts[1]));long now=Instant.now().getEpochSecond();
  if(!"campuslink".equals(p.path("iss").asText())||!"campuslink-client".equals(p.path("aud").asText())||!p.path("exp").isIntegralNumber()||p.path("exp").asLong()<=now||!p.path("iat").isIntegralNumber()||p.path("iat").asLong()>now+30||!p.path("ver").isIntegralNumber())throw new IllegalArgumentException();
  Long uid=Long.valueOf(p.path("sub").asText());var u=users.selectById(uid);if(u==null||!States.Account.NORMAL.name().equals(u.getStatus())||u.getTokenVersion()!=p.path("ver").asInt()||!u.getRole().equals(p.path("role").asText()))throw new IllegalArgumentException();return new TokenIdentity(uid,u.getRole());
 }catch(Exception e){throw new BusinessException(401,"登录凭证无效或已过期，请重新登录");}}
 private byte[] sign(String s)throws Exception{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return m.doFinal(s.getBytes(StandardCharsets.UTF_8));}
 public record TokenIdentity(Long userId,String role){}
}
