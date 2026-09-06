package com.campuslink.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campuslink.common.BusinessException;
import com.campuslink.domain.*;
import com.campuslink.dto.Requests;
import com.campuslink.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service @RequiredArgsConstructor
public class AbnormalTaskService {
    private final WorkflowLocks locks;private final TaskMapper taskMapper;private final TaskOrderMapper orderMapper;private final TaskCompletionMapper completionMapper;private final ReportMapper reportMapper;private final UserMapper userMapper;private final PointCreditService pointCreditService;private final AccountService accountService;

    @Transactional public Report openOrder(Long orderId,Long userId,Requests.AbnormalCreate dto){TaskOrder order=orderMapper.selectById(orderId);if(order==null)throw new BusinessException(404,"任务订单不存在");return open(order.getTaskId(),userId,dto);}
    @Transactional public Report open(Long taskId,Long userId,Requests.AbnormalCreate dto){
        if(!java.util.Set.of("NO_CONTACT","DISPUTE","OTHER").contains(dto.reasonType()))throw new BusinessException("异常原因无效");CampusTask task=locks.task(taskId);TaskOrder order=requiredOrder(taskId);order=orderMapper.lockById(order.getId());if(!userId.equals(order.getPublisherId())&&!userId.equals(order.getAccepterId()))throw new BusinessException(403,"仅任务双方可以发起异常处理");if(!List.of(States.Task.IN_PROGRESS.name(),States.Task.WAIT_ACCEPTANCE.name()).contains(order.getStatus()))throw new BusinessException("当前任务阶段不能发起异常处理");String previous=order.getStatus();
        int claimed=orderMapper.update(null,new LambdaUpdateWrapper<TaskOrder>().eq(TaskOrder::getId,order.getId()).eq(TaskOrder::getStatus,previous).set(TaskOrder::getStatus,States.Task.ABNORMAL.name()).set(TaskOrder::getUpdatedAt,LocalDateTime.now()).setSql("version = version + 1"));if(claimed!=1)throw new BusinessException("任务状态已变化，请刷新后重试");task.setStatus(States.Task.ABNORMAL.name());task.setAbnormalReason(dto.reasonType());task.setUpdatedAt(LocalDateTime.now());taskMapper.updateById(task);
        Report report=new Report();report.setReporterId(userId);report.setTargetType("ORDER");report.setTargetId(order.getId());report.setReasonType(dto.reasonType());report.setDescription(dto.description());report.setPreviousStatus(previous);report.setStatus(States.Report.PROCESSING.name());report.setCreatedAt(LocalDateTime.now());reportMapper.insert(report);notifyBoth(order,"任务进入异常处理","管理员将根据双方信息进行裁决",report.getId());return report;
    }

    @Transactional public Report resolve(Long reportId,Long adminId,Requests.AbnormalResolve dto){
        Report report=reportMapper.selectById(reportId);if(report==null||!java.util.Set.of("TASK","ORDER").contains(report.getTargetType())||report.getPreviousStatus()==null)throw new BusinessException("异常任务记录不存在");Long resolvedTaskId="ORDER".equals(report.getTargetType())?orderMapper.selectById(report.getTargetId()).getTaskId():report.getTargetId();CampusTask lockedTask=locks.task(resolvedTaskId);
        int claimed=reportMapper.update(null,new LambdaUpdateWrapper<Report>().eq(Report::getId,reportId).eq(Report::getStatus,States.Report.PROCESSING.name()).set(Report::getHandlerId,adminId));if(claimed!=1)throw new BusinessException("该异常任务已被处理");CampusTask task=lockedTask;TaskOrder order=requiredOrder(task.getId());order=orderMapper.lockById(order.getId());if(!States.Task.ABNORMAL.name().equals(task.getStatus())||!States.Task.ABNORMAL.name().equals(order.getStatus()))throw new BusinessException("异常任务状态不一致");
        userMapper.lockById(Math.min(order.getPublisherId(),order.getAccepterId()));userMapper.lockById(Math.max(order.getPublisherId(),order.getAccepterId()));
        switch(dto.decision()){
            case "REFUND"->{pointCreditService.refund(accountService.requiredUser(order.getPublisherId()),task.getRewardPoints(),task.getId());setStatus(task,order,States.Task.CANCELLED.name());}
            case "SETTLE"->{TaskCompletion pending=completionMapper.selectOne(new LambdaQueryWrapper<TaskCompletion>().eq(TaskCompletion::getOrderId,order.getId()).eq(TaskCompletion::getReviewStatus,States.Completion.PENDING.name()).orderByDesc(TaskCompletion::getSubmitTime).last("limit 1"));if(pending!=null){pending.setReviewStatus(States.Completion.APPROVED.name());completionMapper.updateById(pending);}pointCreditService.settle(accountService.requiredUser(order.getPublisherId()),accountService.requiredUser(order.getAccepterId()),task.getRewardPoints(),order.getId());setStatus(task,order,States.Task.COMPLETED.name());order.setCompletedAt(LocalDateTime.now());orderMapper.updateById(order);}
            case "RESUME"->{if("TIMEOUT".equals(report.getReasonType()))throw new BusinessException("超时任务请重新选择并设置新期限，或执行退款/结算");setStatus(task,order,report.getPreviousStatus());}
            default->throw new BusinessException("裁决结果无效");
        }
        if(dto.creditDelta()!=null&&dto.creditDelta()!=0){if(dto.liableUserId()==null||(!dto.liableUserId().equals(order.getPublisherId())&&!dto.liableUserId().equals(order.getAccepterId())))throw new BusinessException("信用调整对象必须是任务参与者");pointCreditService.changeCredit(accountService.requiredUser(dto.liableUserId()),dto.creditDelta(),"ABNORMAL_HANDLE",order.getId(),dto.result());}
        report.setStatus(States.Report.RESOLVED.name());report.setHandlerId(adminId);report.setHandleResult(dto.decision()+"："+dto.result());report.setHandledAt(LocalDateTime.now());reportMapper.updateById(report);notifyBoth(order,"异常任务已裁决",dto.result(),report.getId());return report;
    }
    private void setStatus(CampusTask task,TaskOrder order,String status){LocalDateTime now=LocalDateTime.now();task.setStatus(status);task.setUpdatedAt(now);taskMapper.updateById(task);order.setStatus(status);order.setUpdatedAt(now);orderMapper.updateById(order);}private CampusTask requiredTask(Long id){CampusTask t=taskMapper.selectById(id);if(t==null)throw new BusinessException("任务不存在");return t;}private TaskOrder requiredOrder(Long taskId){TaskOrder o=orderMapper.selectOne(new LambdaQueryWrapper<TaskOrder>().eq(TaskOrder::getTaskId,taskId));if(o==null)throw new BusinessException("任务订单不存在");return o;}private void notifyBoth(TaskOrder o,String title,String content,Long id){accountService.notify(o.getPublisherId(),"ORDER_STATUS",title,content,id);accountService.notify(o.getAccepterId(),"ORDER_STATUS",title,content,id);}
}
