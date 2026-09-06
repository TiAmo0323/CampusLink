package com.campuslink.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuslink.common.BusinessException;
import com.campuslink.domain.*;
import com.campuslink.dto.Requests;
import com.campuslink.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class AccountService {
    private final UserMapper userMapper; private final PointTransactionMapper pointMapper; private final CreditRecordMapper creditMapper; private final NotificationMapper notificationMapper; private final StorageService storageService;
    public Map<String,Object> profile(Long id){ return AuthService.userView(requiredUser(id)); }
    @org.springframework.transaction.annotation.Transactional public Map<String,Object> update(Long id, Requests.Profile dto){storageService.requireImageReference(dto.avatarUrl());User u=userMapper.lockById(id);u.setNickname(dto.nickname());u.setAvatarUrl(dto.avatarUrl());u.setBio(dto.bio());u.setUpdatedAt(LocalDateTime.now());userMapper.updateById(u);return AuthService.userView(u); }
    public Map<String,Object> pointTransactions(Long id,int page,int size){Page<PointTransaction> points=pointMapper.selectPage(Page.of(Math.max(1,page),Math.min(50,Math.max(1,size))),new LambdaQueryWrapper<PointTransaction>().eq(PointTransaction::getUserId,id).orderByDesc(PointTransaction::getCreatedAt));return pageData(points);}
    public Map<String,Object> wallet(Long id,int page,int size){ User u=requiredUser(id);int bounded=Math.min(50,Math.max(1,size));Page<PointTransaction> points=pointMapper.selectPage(Page.of(Math.max(1,page),bounded),new LambdaQueryWrapper<PointTransaction>().eq(PointTransaction::getUserId,id).orderByDesc(PointTransaction::getCreatedAt));Page<CreditRecord> credits=creditMapper.selectPage(Page.of(Math.max(1,page),bounded),new LambdaQueryWrapper<CreditRecord>().eq(CreditRecord::getUserId,id).orderByDesc(CreditRecord::getCreatedAt));Map<String,Object> m=new LinkedHashMap<>();m.put("availablePoints",u.getAvailablePoints());m.put("frozenPoints",u.getFrozenPoints());m.put("creditScore",u.getCreditScore());m.put("pointTransactions",pageData(points));m.put("creditRecords",pageData(credits));return m; }
    public Map<String,Object> notifications(Long id,int page,int size){Page<Notification> result=notificationMapper.selectPage(Page.of(Math.max(1,page),Math.min(50,Math.max(1,size))),new LambdaQueryWrapper<Notification>().eq(Notification::getUserId,id).orderByDesc(Notification::getCreatedAt));return pageData(result);}
    public void readNotification(Long id,Long notificationId){Notification n=notificationMapper.selectById(notificationId);if(n==null||!id.equals(n.getUserId()))throw new BusinessException("通知不存在");n.setIsRead(true);notificationMapper.updateById(n);}
    public User requiredUser(Long id){User u=userMapper.selectById(id);if(u==null)throw new BusinessException("用户不存在");return u;}
    public void notify(Long userId,String type,String title,String content,Long businessId){Notification n=new Notification();n.setUserId(userId);n.setType(type);n.setTitle(title);n.setContent(content);n.setRelatedBusinessId(businessId);n.setIsRead(false);n.setCreatedAt(LocalDateTime.now());notificationMapper.insert(n);}
    private Map<String,Object> pageData(Page<?> p){Map<String,Object> m=new LinkedHashMap<>();m.put("records",p.getRecords());m.put("total",p.getTotal());m.put("page",p.getCurrent());m.put("size",p.getSize());m.put("pages",p.getPages());return m;}
}
