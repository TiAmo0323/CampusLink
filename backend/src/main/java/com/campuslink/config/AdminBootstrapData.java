package com.campuslink.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campuslink.domain.User;import com.campuslink.domain.States;
import com.campuslink.mapper.UserMapper;
import com.campuslink.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Component @RequiredArgsConstructor
public class AdminBootstrapData implements CommandLineRunner {
    private static final Logger log= LoggerFactory.getLogger(AdminBootstrapData.class);
    private final UserMapper userMapper;private final AuthService authService;
    @Value("${campuslink.seed-demo-data:true}") private boolean seedDemoData;
    @Value("${campuslink.bootstrap-admin-username:}") private String username;
    @Value("${campuslink.bootstrap-admin-password:}") private String password;
    @Override @Transactional public void run(String... args){if(seedDemoData)return;if(username==null||username.isBlank()||password==null||password.length()<8){if(userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole,States.Role.ADMIN.name()))==0)log.warn("演示数据已关闭且未配置有效的首次管理员账号/密码");return;}if(userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername,username))>0)return;LocalDateTime now=LocalDateTime.now();User admin=new User();admin.setUsername(username.trim());admin.setPasswordHash(authService.encode(password));admin.setNickname("系统管理员");admin.setRole(States.Role.ADMIN.name());admin.setCreditScore(100);admin.setAvailablePoints(0);admin.setFrozenPoints(0);admin.setStatus(States.Account.NORMAL.name());admin.setCreatedAt(now);admin.setUpdatedAt(now);userMapper.insert(admin);log.info("已创建首次管理员账号 {}，请在登录后妥善管理凭据",username);}
}
