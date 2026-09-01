package com.campuslink.service;

import com.campuslink.common.BusinessException;
import com.campuslink.domain.*;
import com.campuslink.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service @RequiredArgsConstructor
public class PointCreditService {
    private final UserMapper userMapper; private final PointTransactionMapper pointMapper; private final CreditRecordMapper creditMapper;
    public void freeze(User u,int amount,Long businessId){if(u.getAvailablePoints()<amount)throw new BusinessException("可用积分不足");int before=u.getAvailablePoints();u.setAvailablePoints(before-amount);u.setFrozenPoints(u.getFrozenPoints()+amount);userMapper.updateById(u);recordPoint(u.getId(),"TASK_FREEZE",businessId,-amount,before,u.getAvailablePoints(),"发布任务冻结积分");}
    public void refund(User u,int amount,Long businessId){int before=u.getAvailablePoints();u.setFrozenPoints(Math.max(0,u.getFrozenPoints()-amount));u.setAvailablePoints(before+amount);userMapper.updateById(u);recordPoint(u.getId(),"TASK_REFUND",businessId,amount,before,u.getAvailablePoints(),"取消任务返还积分");}
    public void settle(User publisher,User accepter,int amount,Long businessId){if(publisher.getFrozenPoints()<amount)throw new BusinessException("冻结积分异常，无法结算");publisher.setFrozenPoints(publisher.getFrozenPoints()-amount);userMapper.updateById(publisher);recordPoint(publisher.getId(),"TASK_SETTLEMENT",businessId,0,publisher.getAvailablePoints(),publisher.getAvailablePoints(),"冻结积分已结算");int before=accepter.getAvailablePoints();accepter.setAvailablePoints(before+amount);userMapper.updateById(accepter);recordPoint(accepter.getId(),"TASK_SETTLEMENT",businessId,amount,before,accepter.getAvailablePoints(),"完成任务获得积分");changeCredit(accepter,5,"TASK_COMPLETION",businessId,"正常完成任务");}
    public void changeCredit(User user,int delta,String type,Long businessId,String reason){int before=user.getCreditScore(),after=Math.max(0,Math.min(100,before+delta));user.setCreditScore(after);userMapper.updateById(user);CreditRecord r=new CreditRecord();r.setUserId(user.getId());r.setBusinessType(type);r.setBusinessId(businessId);r.setChangeValue(after-before);r.setBeforeScore(before);r.setAfterScore(after);r.setReason(reason);r.setCreatedAt(LocalDateTime.now());creditMapper.insert(r);}
    private void recordPoint(Long uid,String type,Long bid,int delta,int before,int after,String remark){PointTransaction tx=new PointTransaction();tx.setUserId(uid);tx.setBusinessType(type);tx.setBusinessId(bid);tx.setChangeAmount(delta);tx.setBeforeBalance(before);tx.setAfterBalance(after);tx.setCreatedAt(LocalDateTime.now());tx.setRemark(remark);pointMapper.insert(tx);}
}
