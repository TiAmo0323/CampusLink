package com.campuslink.service;
import com.campuslink.common.BusinessException;import com.campuslink.domain.*;import com.campuslink.mapper.*;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
@Service @RequiredArgsConstructor public class PointCreditService {
 private final UserMapper users;private final PointTransactionMapper points;private final CreditRecordMapper credits;private final NotificationMapper notifications;
 @Transactional public void freeze(User user,int amount,Long id){User u=users.lockById(user.getId());if(amount<=0||u.getAvailablePoints()<amount)throw new BusinessException("可用积分不足");change(u,-amount,amount,"TASK_FREEZE",id,"发布任务冻结积分");}
 @Transactional public void refund(User user,int amount,Long id){User u=users.lockById(user.getId());if(amount<=0||u.getFrozenPoints()<amount)throw new BusinessException("冻结积分异常，无法返还");change(u,amount,-amount,"TASK_REFUND",id,"取消任务返还积分");}
 @Transactional public void settle(User publisher,User accepter,int amount,Long id){
  User lower=users.lockById(Math.min(publisher.getId(),accepter.getId())),higher=users.lockById(Math.max(publisher.getId(),accepter.getId()));
  User p=publisher.getId().equals(lower.getId())?lower:higher,a=accepter.getId().equals(lower.getId())?lower:higher;if(amount<=0||p.getFrozenPoints()<amount)throw new BusinessException("冻结积分异常，无法结算");
  change(p,0,-amount,"TASK_SETTLEMENT",id,"冻结积分结算支出");change(a,amount,0,"TASK_SETTLEMENT",id,"完成任务获得积分");changeCredit(a,5,"TASK_COMPLETION",id,"正常完成任务");
 }
 @Transactional public void changeCredit(User user,int delta,String type,Long id,String reason){User u=users.lockById(user.getId());int before=u.getCreditScore(),after=Math.max(0,Math.min(100,before+delta));u.setCreditScore(after);users.updateById(u);CreditRecord r=new CreditRecord();r.setUserId(u.getId());r.setBusinessType(type);r.setBusinessId(id);r.setChangeValue(after-before);r.setBeforeScore(before);r.setAfterScore(after);r.setReason(reason);r.setCreatedAt(LocalDateTime.now());credits.insert(r);
  if(before>=60&&after<60){Notification n=new Notification();n.setUserId(u.getId());n.setType("SYSTEM_NOTICE");n.setTitle("信用降级提醒");n.setContent("信用已低于60，暂不能发布任务，请按时履约改善信用");n.setRelatedBusinessId(id);n.setIsRead(false);n.setCreatedAt(LocalDateTime.now());notifications.insert(n);}
 }
 private void change(User u,int available,int frozen,String type,Long id,String remark){PointTransaction t=new PointTransaction();t.setUserId(u.getId());t.setBusinessType(type);t.setBusinessId(id);t.setChangeAmount(available);t.setBeforeBalance(u.getAvailablePoints());t.setBeforeFrozen(u.getFrozenPoints());u.setAvailablePoints(u.getAvailablePoints()+available);u.setFrozenPoints(u.getFrozenPoints()+frozen);users.updateById(u);t.setAfterBalance(u.getAvailablePoints());t.setAfterFrozen(u.getFrozenPoints());t.setRemark(remark);t.setCreatedAt(LocalDateTime.now());points.insert(t);}
}
