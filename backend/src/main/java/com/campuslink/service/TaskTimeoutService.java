package com.campuslink.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campuslink.domain.*;
import com.campuslink.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service @RequiredArgsConstructor
public class TaskTimeoutService {
    private final TaskOrderMapper orderMapper;private final TaskMapper taskMapper;private final ReportMapper reportMapper;private final UserMapper userMapper;private final PointCreditService pointCreditService;private final AccountService accountService;
    @Value("${campuslink.task-timeout-grace-hours:24}") private long graceHours;
    @Scheduled(cron="${campuslink.task-timeout-cron:0 */10 * * * *}") public void scheduledScan(){processOverdue(LocalDateTime.now());}
    @Transactional public int processOverdue(LocalDateTime now){int count=0;for(TaskOrder order:orderMapper.selectList(new LambdaQueryWrapper<TaskOrder>().in(TaskOrder::getStatus,List.of("WAIT_EXECUTE","IN_PROGRESS")))){CampusTask task=taskMapper.selectById(order.getTaskId());if(task!=null&&task.getTaskTime().plusHours(graceHours).isBefore(now)&&markOverdue(order,task))count++;}return count;}
    private boolean markOverdue(TaskOrder order,CampusTask task){String previous=order.getStatus();int changed=orderMapper.update(null,new LambdaUpdateWrapper<TaskOrder>().eq(TaskOrder::getId,order.getId()).eq(TaskOrder::getStatus,previous).set(TaskOrder::getStatus,"ABNORMAL").set(TaskOrder::getUpdatedAt,LocalDateTime.now()).setSql("version = version + 1"));if(changed!=1)return false;task.setStatus("ABNORMAL");task.setUpdatedAt(LocalDateTime.now());taskMapper.updateById(task);User accepter=accountService.requiredUser(order.getAccepterId());pointCreditService.changeCredit(accepter,-2,"TASK_TIMEOUT",task.getId(),"任务超时未完成");User admin=userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getRole,"ADMIN").last("limit 1"));Report report=new Report();report.setReporterId(admin==null?order.getPublisherId():admin.getId());report.setTargetType("TASK");report.setTargetId(task.getId());report.setReasonType("TIMEOUT");report.setDescription("系统检测到任务超过约定时间 "+graceHours+" 小时仍未完成");report.setPreviousStatus(previous);report.setStatus("PROCESSING");report.setCreatedAt(LocalDateTime.now());reportMapper.insert(report);accountService.notify(order.getPublisherId(),"ORDER_STATUS","任务已标记超时","请等待管理员处理",report.getId());accountService.notify(order.getAccepterId(),"ORDER_STATUS","任务已标记超时","信用分已扣除2分",report.getId());return true;}
}
