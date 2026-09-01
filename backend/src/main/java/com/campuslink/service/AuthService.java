package com.campuslink.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campuslink.common.BusinessException;
import com.campuslink.config.TokenService;
import com.campuslink.domain.PointTransaction;
import com.campuslink.domain.User;
import com.campuslink.dto.Requests;
import com.campuslink.mapper.PointTransactionMapper;
import com.campuslink.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service @RequiredArgsConstructor
public class AuthService {
    private final UserMapper userMapper; private final PointTransactionMapper pointMapper; private final TokenService tokenService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    @Value("${campuslink.initial-points:200}") private int initialPoints;

    @Transactional public Map<String,Object> register(Requests.Register dto) {
        if ((dto.studentNo()==null || dto.studentNo().isBlank()) && (dto.email()==null || dto.email().isBlank())) throw new BusinessException("学号或校园邮箱至少填写一项");
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername,dto.username()))>0) throw new BusinessException("用户名已存在");
        if (dto.studentNo()!=null && !dto.studentNo().isBlank() && userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getStudentNo,dto.studentNo()))>0) throw new BusinessException("学号已注册");
        if (dto.email()!=null && !dto.email().isBlank() && userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail,dto.email()))>0) throw new BusinessException("邮箱已注册");
        LocalDateTime now=LocalDateTime.now(); User user=new User(); user.setUsername(dto.username()); user.setPasswordHash(encoder.encode(dto.password())); user.setNickname(dto.nickname());
        user.setStudentNo(blankToNull(dto.studentNo())); user.setEmail(blankToNull(dto.email())); user.setRole("STUDENT"); user.setCreditScore(100); user.setAvailablePoints(initialPoints); user.setFrozenPoints(0); user.setStatus("NORMAL"); user.setCreatedAt(now); user.setUpdatedAt(now); userMapper.insert(user);
        PointTransaction tx=new PointTransaction(); tx.setUserId(user.getId()); tx.setBusinessType("REGISTER_REWARD"); tx.setChangeAmount(initialPoints); tx.setBeforeBalance(0); tx.setAfterBalance(initialPoints); tx.setRemark("新用户注册奖励"); tx.setCreatedAt(now); pointMapper.insert(tx);
        return loginResult(user);
    }
    public Map<String,Object> login(Requests.Login dto) {
        User user=userMapper.selectOne(new LambdaQueryWrapper<User>().and(q->q.eq(User::getUsername,dto.account()).or().eq(User::getEmail,dto.account())));
        if(user==null || !encoder.matches(dto.password(),user.getPasswordHash())) throw new BusinessException(401,"账号或密码错误");
        if(!"NORMAL".equals(user.getStatus())) throw new BusinessException(403,"账户已被禁用");
        user.setLastLoginAt(LocalDateTime.now()); user.setUpdatedAt(LocalDateTime.now()); userMapper.updateById(user); return loginResult(user);
    }
    public Map<String,Object> loginResult(User user){ Map<String,Object> result=new LinkedHashMap<>(); result.put("token",tokenService.issue(user.getId(),user.getRole())); result.put("user",userView(user)); return result; }
    public static Map<String,Object> userView(User u){ Map<String,Object> m=new LinkedHashMap<>(); m.put("id",u.getId());m.put("username",u.getUsername());m.put("nickname",u.getNickname());m.put("studentNo",u.getStudentNo());m.put("email",u.getEmail());m.put("avatarUrl",u.getAvatarUrl());m.put("bio",u.getBio());m.put("role",u.getRole());m.put("creditScore",u.getCreditScore());m.put("availablePoints",u.getAvailablePoints());m.put("frozenPoints",u.getFrozenPoints());m.put("status",u.getStatus()); return m; }
    private String blankToNull(String v){return v==null||v.isBlank()?null:v.trim();}
    public String encode(String raw){ return encoder.encode(raw); }
}
