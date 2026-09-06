package com.campuslink.service;
import com.campuslink.common.BusinessException;import com.campuslink.domain.*;import com.campuslink.mapper.*;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Component;
/** All task writers lock task then order; account locks follow in ascending id order. */
@Component @RequiredArgsConstructor public class WorkflowLocks {
 private final TaskMapper tasks;private final TaskOrderMapper orders;
 public CampusTask task(Long id){CampusTask t=tasks.lockById(id);if(t==null)throw new BusinessException(404,"任务不存在");return t;}
 public TaskOrder order(Long id){TaskOrder found=orders.selectById(id);if(found==null)throw new BusinessException(404,"订单不存在");task(found.getTaskId());return orders.lockById(id);}
}
