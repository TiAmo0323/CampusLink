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
    private final TaskMapper taskMapper;private final TaskOrderMapper orderMapper;private final TaskCompletionMapper completionMapper;private final ReportMapper reportMapper;private final UserMapper userMapper;private final PointCreditService pointCreditService;private final AccountService accountService;

    @Transactional public Report open(Long taskId,Long userId,Requests.AbnormalCreate dto){
        CampusTask task=requiredTask(taskId);TaskOrder order=requiredOrder(taskId);if(!userId.equals(order.getPublisherId())&&!userId.equals(order.getAccepterId()))throw new BusinessException(403,"仅任务双方可以发起异常处理");if(!List.of("IN_PROGRESS","WAIT_ACCEPTANCE").contains(order.getStatus()))throw new BusinessException("当前任务阶段不能发起异常处理");String previous=order.getStatus();
        int claimed=orderMapper.update(null,new LambdaUpdateWrapper<TaskOrder>().eq(TaskOrder::getId,order.getId()).eq(TaskOrder::getStatus,previous).set(TaskOrder::getStatus,"ABNORMAL").set(TaskOrder::getUpdatedAt,LocalDateTime.now()).setSql("version = version + 1"));if(claimed!=1)throw new BusinessException("任务状态已变化，请刷新后重试");task.setStatus("ABNORMAL");task.setUpdatedAt(LocalDateTime.now());taskMapper.updateById(task);
        Report report=new Report();report.setReporterId(userId);report.setTargetType("TASK");report.setTargetId(taskId);report.setReasonType(dto.reasonType());report.setDescription(dto.description());report.setPreviousStatus(previous);report.setStatus("PROCESSING");report.setCreatedAt(LocalDateTime.now());reportMapper.insert(report);notifyBoth(order,"任务进入异常处理","管理员将根据双方信息进行裁决",report.getId());return report;
    }

    @Transactional public Report resolve(Long reportId,Long adminId,Requests.AbnormalResolve dto){
        Report report=reportMapper.selectById(reportId);if(report==null||!"TASK".equals(report.getTargetType())||report.getPreviousStatus()==null)throw new BusinessException("异常任务记录不存在");int claimed=reportMapper.update(null,new LambdaUpdateWrapper<Report>().eq(Report::getId,reportId).eq(Report::getStatus,"PROCESSING").set(Report::getStatus,"HANDLING"));if(claimed!=1)throw new BusinessException("该异常任务已被处理");CampusTask task=requiredTask(report.getTargetId());TaskOrder order=requiredOrder(task.getId());if(!"ABNORMAL".equals(task.getStatus())||!"ABNORMAL".equals(order.getStatus()))throw new BusinessException("异常任务状态不一致");
        switch(dto.decision()){
            case "REFUND"->{pointCreditService.refund(accountService.requiredUser(order.getPublisherId()),task.getRewardPoints(),task.getId());setStatus(task,order,"CANCELLED");}
            case "SETTLE"->{TaskCompletion pending=completionMapper.selectOne(new LambdaQueryWrapper<TaskCompletion>().eq(TaskCompletion::getOrderId,order.getId()).eq(TaskCompletion::getReviewStatus,"PENDING").orderByDesc(TaskCompletion::getSubmitTime).last("limit 1"));if(pending!=null){pending.setReviewStatus("APPROVED");completionMapper.updateById(pending);}pointCreditService.settle(accountService.requiredUser(order.getPublisherId()),accountService.requiredUser(order.getAccepterId()),task.getRewardPoints(),order.getId());setStatus(task,order,"COMPLETED");order.setCompletedAt(LocalDateTime.now());orderMapper.updateById(order);}
            case "RESUME"->setStatus(task,order,report.getPreviousStatus());
            default->throw new BusinessException("裁决结果无效");
        }
        if(dto.creditDelta()!=null&&dto.creditDelta()!=0){if(dto.liableUserId()==null||(!dto.liableUserId().equals(order.getPublisherId())&&!dto.liableUserId().equals(order.getAccepterId())))throw new BusinessException("信用调整对象必须是任务参与者");pointCreditService.changeCredit(accountService.requiredUser(dto.liableUserId()),dto.creditDelta(),"ABNORMAL_HANDLE",order.getId(),dto.result());}
        report.setStatus("RESOLVED");report.setHandlerId(adminId);report.setHandleResult(dto.decision()+"："+dto.result());report.setHandledAt(LocalDateTime.now());reportMapper.updateById(report);notifyBoth(order,"异常任务已裁决",dto.result(),report.getId());return report;
    }
    private void setStatus(CampusTask task,TaskOrder order,String status){LocalDateTime now=LocalDateTime.now();task.setStatus(status);task.setUpdatedAt(now);taskMapper.updateById(task);order.setStatus(status);order.setUpdatedAt(now);orderMapper.updateById(order);}private CampusTask requiredTask(Long id){CampusTask t=taskMapper.selectById(id);if(t==null)throw new BusinessException("任务不存在");return t;}private TaskOrder requiredOrder(Long taskId){TaskOrder o=orderMapper.selectOne(new LambdaQueryWrapper<TaskOrder>().eq(TaskOrder::getTaskId,taskId));if(o==null)throw new BusinessException("任务订单不存在");return o;}private void notifyBoth(TaskOrder o,String title,String content,Long id){accountService.notify(o.getPublisherId(),"ORDER_STATUS",title,content,id);accountService.notify(o.getAccepterId(),"ORDER_STATUS",title,content,id);}
}
