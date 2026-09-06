package com.campuslink.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campuslink.common.BusinessException;
import com.campuslink.config.TokenService;
import com.campuslink.domain.CreditRecord;
import com.campuslink.domain.PointTransaction;
import com.campuslink.domain.User;import com.campuslink.domain.States;
import com.campuslink.dto.Requests;
import com.campuslink.mapper.CreditRecordMapper;
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
    private final UserMapper userMapper; private final PointTransactionMapper pointMapper; private final CreditRecordMapper creditMapper; private final TokenService tokenService; private final SessionService sessionService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    @Value("${campuslink.initial-points:200}") private int initialPoints;

    @Transactional public Map<String,Object> register(Requests.Register dto) {
        if ((dto.studentNo()==null || dto.studentNo().isBlank()) && (dto.email()==null || dto.email().isBlank())) throw new BusinessException("学号或校园邮箱至少填写一项");
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername,dto.username()))>0) throw new BusinessException("用户名已存在");
        if (dto.studentNo()!=null && !dto.studentNo().isBlank() && userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getStudentNo,dto.studentNo()))>0) throw new BusinessException("学号已注册");
        if (dto.email()!=null && !dto.email().isBlank() && userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail,dto.email()))>0) throw new BusinessException("邮箱已注册");
        LocalDateTime now=LocalDateTime.now(); User user=new User(); user.setUsername(dto.username()); user.setPasswordHash(encoder.encode(dto.password())); user.setNickname(dto.nickname());
        user.setStudentNo(blankToNull(dto.studentNo())); user.setEmail(blankToNull(dto.email())); user.setRole(States.Role.STUDENT.name()); user.setCreditScore(100); user.setAvailablePoints(initialPoints); user.setFrozenPoints(0); user.setStatus(States.Account.NORMAL.name()); user.setCreatedAt(now); user.setUpdatedAt(now); userMapper.insert(user);
        PointTransaction tx=new PointTransaction(); tx.setUserId(user.getId()); tx.setBusinessType("REGISTER_REWARD"); tx.setChangeAmount(initialPoints); tx.setBeforeFrozen(0); tx.setAfterFrozen(0); tx.setBeforeBalance(0); tx.setAfterBalance(initialPoints); tx.setRemark("新用户注册奖励"); tx.setCreatedAt(now); pointMapper.insert(tx);
        CreditRecord credit=new CreditRecord();credit.setUserId(user.getId());credit.setBusinessType("REGISTER_INIT");credit.setChangeValue(100);credit.setBeforeScore(0);credit.setAfterScore(100);credit.setReason("新用户信用初始化");credit.setCreatedAt(now);creditMapper.insert(credit);
        return loginResult(user);
    }
    @Transactional(noRollbackFor=BusinessException.class) public Map<String,Object> login(Requests.Login dto) {
        User user=userMapper.selectOne(new LambdaQueryWrapper<User>().and(q->q.eq(User::getUsername,dto.account()).or().eq(User::getEmail,dto.account())));
        if(user==null) throw new BusinessException(401,"账号或密码错误");
        user=userMapper.lockById(user.getId());
        if(user.getLockedUntil()!=null && user.getLockedUntil().isAfter(LocalDateTime.now())) throw new BusinessException(429,"登录失败次数过多，请10分钟后重试");
        if(!encoder.matches(dto.password(),user.getPasswordHash())) {
            int failures=user.getLockedUntil()!=null ? 1 : user.getFailedLoginAttempts()+1;
            user.setFailedLoginAttempts(failures);
            user.setLockedUntil(null);
            if(failures>=5) user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
            else userMapper.update(null,new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>().eq(User::getId,user.getId()).set(User::getLockedUntil,null));
            userMapper.updateById(user);throw new BusinessException(401,"账号或密码错误");
        }
        user.setFailedLoginAttempts(0);user.setLockedUntil(null);
        userMapper.update(null,new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>().eq(User::getId,user.getId()).set(User::getLockedUntil,null));
        if(!States.Account.NORMAL.name().equals(user.getStatus())) throw new BusinessException(403,"账户已被禁用");
        user.setLastLoginAt(LocalDateTime.now()); user.setUpdatedAt(LocalDateTime.now()); userMapper.updateById(user); return loginResult(user);
    }
    public Map<String,Object> loginResult(User user){ return sessionService.issue(user); }
    public static Map<String,Object> userView(User u){return selfView(u);}
    public static Map<String,Object> selfView(User u){Map<String,Object> m=new LinkedHashMap<>(publicUserView(u));m.put("studentNo",u.getStudentNo());m.put("email",u.getEmail());m.put("availablePoints",u.getAvailablePoints());m.put("frozenPoints",u.getFrozenPoints());return m;}
    public static Map<String,Object> publicUserView(User u){Map<String,Object> m=new LinkedHashMap<>();m.put("id",u.getId());m.put("username",u.getUsername());m.put("nickname",u.getNickname());m.put("avatarUrl",u.getAvatarUrl());m.put("bio",u.getBio());m.put("role",u.getRole());m.put("creditScore",u.getCreditScore());m.put("status",u.getStatus());return m;}
    public static Map<String,Object> adminUserView(User u){return selfView(u);}
    private String blankToNull(String v){return v==null||v.isBlank()?null:v.trim();}
    public String encode(String raw){ return encoder.encode(raw); }
}
