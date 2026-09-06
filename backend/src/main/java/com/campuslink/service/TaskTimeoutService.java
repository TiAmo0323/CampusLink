package com.campuslink.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campuslink.domain.*;import com.campuslink.mapper.*;import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;import java.time.LocalDateTime;import java.util.*;
@Service @RequiredArgsConstructor public class TaskTimeoutService {
 private final TaskOrderMapper orders;private final TaskMapper tasks;private final ReportMapper reports;private final PointCreditService credit;private final AccountService accounts;private final WorkflowLocks locks;private final TransactionTemplate transactions;
 @Value("${campuslink.task-timeout-grace-hours:0}") private long graceHours;
 @Scheduled(cron="${campuslink.task-timeout-cron:0 */10 * * * *}") public void scheduledScan(){processOverdue(LocalDateTime.now());}
 public int processOverdue(LocalDateTime now){int count=0;long cursor=0;
  while(true){var batch=orders.selectList(new LambdaQueryWrapper<TaskOrder>().in(TaskOrder::getStatus,List.of(States.Task.WAIT_EXECUTE.name(),States.Task.IN_PROGRESS.name())).gt(TaskOrder::getId,cursor).orderByAsc(TaskOrder::getId).last("limit 200"));if(batch.isEmpty())break;
   for(TaskOrder candidate:batch){cursor=candidate.getId();Boolean changed=transactions.execute(tx->mark(candidate.getId(),now));if(Boolean.TRUE.equals(changed))count++;}
  }return count;
 }
 private boolean mark(Long id,LocalDateTime now){
  TaskOrder o=locks.order(id);if(!List.of(States.Task.WAIT_EXECUTE.name(),States.Task.IN_PROGRESS.name()).contains(o.getStatus()))return false;
  CampusTask t=tasks.selectById(o.getTaskId());LocalDateTime due=o.getDueAt()!=null?o.getDueAt():t.getTaskTime().plusMinutes(t.getEstimatedDurationMinutes());
  if(!due.plusHours(graceHours).isBefore(now))return false;
  String previous=o.getStatus();o.setStatus(States.Task.ABNORMAL.name());o.setUpdatedAt(now);if(orders.updateById(o)!=1)throw new com.campuslink.common.BusinessException("订单状态已变化");t.setStatus(States.Task.ABNORMAL.name());t.setAbnormalReason("TIMEOUT");t.setUpdatedAt(now);if(tasks.updateById(t)!=1)throw new com.campuslink.common.BusinessException("任务状态已变化");
  credit.changeCredit(accounts.requiredUser(o.getAccepterId()),-2,"TASK_TIMEOUT",t.getId(),"任务超时未完成");
  Report r=new Report();r.setReporterId(o.getPublisherId());r.setTargetType("ORDER");r.setTargetId(o.getId());r.setReasonType("TIMEOUT");r.setDescription("系统检测到本轮约定完成期限已过");r.setPreviousStatus(previous);r.setStatus(States.Report.PROCESSING.name());r.setCreatedAt(now);reports.insert(r);
  accounts.notify(o.getPublisherId(),"ORDER_STATUS","任务已超时","可以重新选择接取者并设置新期限，或由管理员裁决",o.getId());accounts.notify(o.getAccepterId(),"ORDER_STATUS","任务已超时","信用按超时规则扣减",o.getId());return true;
 }
}
