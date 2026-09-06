package com.campuslink.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuslink.common.BusinessException;
import com.campuslink.domain.*;
import com.campuslink.dto.Requests;
import com.campuslink.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class GovernanceService {
    private static final String ADMIN_ACTION_PREFIX = "ADMIN_";
    private static final String ADMIN_USER_STATUS = ADMIN_ACTION_PREFIX + "USER_STATUS";
    private static final String ADMIN_SKILL_STATUS = ADMIN_ACTION_PREFIX + "SKILL_STATUS";
    private final WorkflowLocks locks;private final RefreshSessionMapper refreshSessions;private final ReviewMapper reviewMapper;private final ReportMapper reportMapper;private final TaskOrderMapper orderMapper;private final TaskCompletionMapper completionMapper;private final SkillExchangeMapper exchangeMapper;private final UserMapper userMapper;private final TaskMapper taskMapper;private final SkillMapper skillMapper;private final OrderAssignmentHistoryMapper historyMapper;private final AccountService accountService;private final PointCreditService pointCreditService;
    @Transactional public Review review(Long reviewerId,Requests.ReviewCreate dto){
        if(reviewerId.equals(dto.revieweeId()))throw new BusinessException("不能评价自己");boolean participant=false;
        if("TASK".equals(dto.businessType())){TaskOrder o=orderMapper.selectById(dto.businessId());participant=o!=null&&States.Task.COMPLETED.name().equals(o.getStatus())&&((reviewerId.equals(o.getPublisherId())&&dto.revieweeId().equals(o.getAccepterId()))||(reviewerId.equals(o.getAccepterId())&&dto.revieweeId().equals(o.getPublisherId())));}else if("SKILL_EXCHANGE".equals(dto.businessType())){SkillExchange e=exchangeMapper.selectById(dto.businessId());participant=e!=null&&States.Exchange.COMPLETED.name().equals(e.getStatus())&&((reviewerId.equals(e.getRequesterId())&&dto.revieweeId().equals(e.getProviderId()))||(reviewerId.equals(e.getProviderId())&&dto.revieweeId().equals(e.getRequesterId())));}if(!participant)throw new BusinessException("仅已完成业务的参与者可以评价");
        userMapper.lockById(Math.min(reviewerId,dto.revieweeId()));userMapper.lockById(Math.max(reviewerId,dto.revieweeId()));
        if(reviewMapper.selectCount(new LambdaQueryWrapper<Review>().eq(Review::getBusinessType,dto.businessType()).eq(Review::getBusinessId,dto.businessId()).eq(Review::getReviewerId,reviewerId))>0)throw new BusinessException("该业务已评价");Review r=new Review();r.setBusinessType(dto.businessType());r.setBusinessId(dto.businessId());r.setReviewerId(reviewerId);r.setRevieweeId(dto.revieweeId());r.setRating(dto.rating());r.setContent(dto.content());r.setCreatedAt(LocalDateTime.now());reviewMapper.insert(r);if(dto.rating()>=4)pointCreditService.changeCredit(accountService.requiredUser(dto.revieweeId()),2,"HIGH_RATING",dto.businessId(),"获得4~5星评价");else if(dto.rating()<=2)pointCreditService.changeCredit(accountService.requiredUser(dto.revieweeId()),-3,"LOW_RATING",dto.businessId(),"获得1~2星评价");return r;
    }
    public Map<String,Object> reviews(Long userId,int page,int size){Page<Review> p=reviewMapper.selectPage(com.campuslink.common.Pages.request(page,size),new LambdaQueryWrapper<Review>().eq(Review::getRevieweeId,userId).orderByDesc(Review::getId));return com.campuslink.common.Pages.of(p,p.getRecords());}
    public Report report(Long reporterId,Requests.ReportCreate dto){
        boolean exists=switch(dto.targetType()){case "USER"->userMapper.selectById(dto.targetId())!=null;case "TASK"->taskMapper.selectById(dto.targetId())!=null;case "SKILL"->skillMapper.selectById(dto.targetId())!=null;case "REVIEW"->reviewMapper.selectById(dto.targetId())!=null;default->false;};
        if(!exists)throw new BusinessException("举报对象无效；履约申诉请使用异常处理入口");Report r=new Report();r.setReporterId(reporterId);r.setTargetType(dto.targetType());r.setTargetId(dto.targetId());r.setReasonType(dto.reasonType());r.setDescription(dto.description());r.setStatus(States.Report.PENDING.name());r.setCreatedAt(LocalDateTime.now());reportMapper.insert(r);return r;}
    public Map<String,Object> myReports(Long userId,int page,int size){Page<Report> p=reportMapper.selectPage(com.campuslink.common.Pages.request(page,size),new LambdaQueryWrapper<Report>().eq(Report::getReporterId,userId).orderByDesc(Report::getId));return com.campuslink.common.Pages.of(p,p.getRecords());}
    public Map<String,Object> dashboard(){
        LocalDateTime now=LocalDateTime.now(),since=now.minusDays(6).toLocalDate().atStartOfDay(),previous=since.minusDays(7);
        List<User> recentUsers=userMapper.selectList(new LambdaQueryWrapper<User>().eq(User::getRole,States.Role.STUDENT.name()).ge(User::getCreatedAt,previous));
        List<CampusTask> recentTasks=taskMapper.selectList(new LambdaQueryWrapper<CampusTask>().ge(CampusTask::getCreatedAt,previous));
        List<TaskOrder> recentOrders=orderMapper.selectList(new LambdaQueryWrapper<TaskOrder>().ge(TaskOrder::getCompletedAt,previous));
        List<Report> recentReports=reportMapper.selectList(new LambdaQueryWrapper<Report>()
                .notLikeRight(Report::getReasonType,ADMIN_ACTION_PREFIX).ge(Report::getCreatedAt,previous));
        long currentUsers=recentUsers.stream().filter(u->!u.getCreatedAt().isBefore(since)).count(),previousUsers=recentUsers.size()-currentUsers;
        long currentTasks=recentTasks.stream().filter(t->!t.getCreatedAt().isBefore(since)).count(),previousTasks=recentTasks.size()-currentTasks;
        long currentCompleted=recentOrders.stream().filter(o->o.getCompletedAt()!=null&&!o.getCompletedAt().isBefore(since)).count(),previousCompleted=recentOrders.stream().filter(o->o.getCompletedAt()!=null&&o.getCompletedAt().isBefore(since)).count();
        long currentReports=recentReports.stream().filter(r->!r.getCreatedAt().isBefore(since)).count(),previousReports=recentReports.size()-currentReports;
        Map<String,Object>m=new LinkedHashMap<>();
        m.put("users",userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole,States.Role.STUDENT.name())));
        m.put("userGrowthPercent",percent(currentUsers,previousUsers));
        m.put("taskGrowthPercent",percent(currentTasks,previousTasks));
        m.put("completedGrowthPercent",percent(currentCompleted,previousCompleted));
        m.put("reportGrowthPercent",percent(currentReports,previousReports));
        m.put("recruitingTasks",taskMapper.selectCount(new LambdaQueryWrapper<CampusTask>().eq(CampusTask::getStatus,States.Task.RECRUITING.name())));
        m.put("activeTasks",taskMapper.selectCount(new LambdaQueryWrapper<CampusTask>().in(CampusTask::getStatus,List.of(States.Task.WAIT_EXECUTE.name(),States.Task.IN_PROGRESS.name(),States.Task.WAIT_ACCEPTANCE.name()))));
        long completed=taskMapper.selectCount(new LambdaQueryWrapper<CampusTask>().eq(CampusTask::getStatus,States.Task.COMPLETED.name())),all=taskMapper.selectCount(null);m.put("completedTasks",completed);m.put("completionRate",all==0?0:Math.round(completed*1000.0/all)/10.0);
        m.put("pendingReports",reportMapper.selectCount(new LambdaQueryWrapper<Report>().ne(Report::getStatus,States.Report.RESOLVED.name())));
        m.put("registrationTrend",trend(since,recentUsers.stream().filter(u->!u.getCreatedAt().isBefore(since)).map(User::getCreatedAt).toList()));
        m.put("taskTrend",trend(since,recentTasks.stream().filter(t->!t.getCreatedAt().isBefore(since)).map(CampusTask::getCreatedAt).toList()));
        m.put("categoryDistribution",taskMapper.categoryCounts().stream().map(row->Map.<String,Object>of("name",row.getName(),"value",row.getCountValue())).toList());
        m.put("activityTrend",activityTrend(since,recentTasks,recentOrders));return m;
    }
    public Map<String,Object> users(int page,int size,String status){LambdaQueryWrapper<User> q=new LambdaQueryWrapper<User>().orderByDesc(User::getCreatedAt);if(status!=null&&!status.isBlank())q.eq(User::getStatus,status);Page<User> p=userMapper.selectPage(Page.of(Math.max(1,page),bounded(size)),q);return pageData(p,p.getRecords().stream().map(AuthService::userView).toList());}
    public Map<String,Object> tasks(int page,int size,String status){LambdaQueryWrapper<CampusTask> q=new LambdaQueryWrapper<CampusTask>().orderByDesc(CampusTask::getCreatedAt);if(status!=null&&!status.isBlank())q.eq(CampusTask::getStatus,status);Page<CampusTask> p=taskMapper.selectPage(Page.of(Math.max(1,page),bounded(size)),q);return pageData(p,p.getRecords());}
    public Map<String,Object> reports(int page,int size,String status){LambdaQueryWrapper<Report> q=new LambdaQueryWrapper<Report>().notLikeRight(Report::getReasonType,ADMIN_ACTION_PREFIX).orderByAsc(Report::getStatus).orderByDesc(Report::getCreatedAt);if(status!=null&&!status.isBlank())q.eq(Report::getStatus,status);Page<Report> p=reportMapper.selectPage(Page.of(Math.max(1,page),bounded(size)),q);return pageData(p,p.getRecords());}
    public Map<String,Object> adminActions(int page,int size){Page<Report> p=reportMapper.selectPage(Page.of(Math.max(1,page),bounded(size)),new LambdaQueryWrapper<Report>().likeRight(Report::getReasonType,ADMIN_ACTION_PREFIX).orderByDesc(Report::getCreatedAt));return pageData(p,p.getRecords());}
    @Transactional public Report handleReport(Long adminId,Long id,Requests.ReportHandle dto){
        Report r=reportMapper.selectOne(new LambdaQueryWrapper<Report>().eq(Report::getId,id).last("FOR UPDATE"));if(r==null)throw new BusinessException(404,"举报不存在");if(r.getPreviousStatus()!=null)throw new BusinessException("异常任务请使用异常裁决接口处理");if(States.Report.RESOLVED.name().equals(r.getStatus()))throw new BusinessException("举报已处理");
        String decision=dto.decision()==null?"REJECT_REPORT":dto.decision();Long userId=dto.liableUserId()!=null?dto.liableUserId():("USER".equals(r.getTargetType())?r.getTargetId():null);
        switch(decision){
            case "REJECT_REPORT"->{ }
            case "WARN_USER"->{User u=requiredStudent(userId);accountService.notify(u.getId(),"SYSTEM_NOTICE","平台治理提醒",dto.result(),r.getId());}
            case "CONFIRM_VIOLATION"->{User u=requiredStudent(userId);pointCreditService.changeCredit(u,-10,"VIOLATION_CONFIRMED",r.getId(),dto.result());}
            case "BAN_USER"->updateUserStatus(userId,States.Account.BANNED.name());
            case "DELIST_TASK"->{if(!"TASK".equals(r.getTargetType()))throw new BusinessException("只有任务举报可以执行下架");delistTask(r.getTargetId(),dto.result());}
            default->throw new BusinessException("处理动作无效");
        }
        r.setStatus(States.Report.RESOLVED.name());r.setHandlerId(adminId);String liable=userId==null?r.getTargetType()+"#"+r.getTargetId():"USER#"+userId;r.setHandleResult(decision+"["+liable+"]："+dto.result());r.setHandledAt(LocalDateTime.now());reportMapper.updateById(r);accountService.notify(r.getReporterId(),"SYSTEM_NOTICE","举报处理完成",dto.result(),r.getId());return r;
    }
    @Transactional public Map<String,Object> setUserStatus(Long adminId,Long id,String status,String reason){String previous=updateUserStatus(id,status);recordAdminAction(adminId,"USER",id,ADMIN_USER_STATUS,reason,previous,status);return AuthService.adminUserView(accountService.requiredUser(id));}
    @Transactional public void delist(Long adminId,Long id,String reason){delistTask(id,reason);}
    public Map<String,Object> evidence(Long reportId,int page,int size){
        Report r=reportMapper.selectById(reportId);if(r==null)throw new BusinessException(404,"举报不存在");Map<String,Object> m=new LinkedHashMap<>();m.put("report",r);
        if("ORDER".equals(r.getTargetType())){
            TaskOrder o=orderMapper.selectById(r.getTargetId());if(o==null)throw new BusinessException(404,"订单不存在");m.put("order",o);m.put("task",taskMapper.selectById(o.getTaskId()));m.put("publisher",AuthService.adminUserView(accountService.requiredUser(o.getPublisherId())));m.put("accepter",AuthService.adminUserView(accountService.requiredUser(o.getAccepterId())));
            Page<TaskCompletion> p=completionMapper.selectPage(com.campuslink.common.Pages.request(page,size),new LambdaQueryWrapper<TaskCompletion>().eq(TaskCompletion::getOrderId,o.getId()).orderByDesc(TaskCompletion::getId));m.put("completions",p.getRecords());m.put("completionTotal",p.getTotal());m.put("assignmentHistory",historyMapper.selectList(new LambdaQueryWrapper<OrderAssignmentHistory>().eq(OrderAssignmentHistory::getOrderId,o.getId()).orderByAsc(OrderAssignmentHistory::getPreviousRound)));
        }else if("USER".equals(r.getTargetType()))m.put("target",AuthService.adminUserView(accountService.requiredUser(r.getTargetId())));
        else if("TASK".equals(r.getTargetType()))m.put("target",taskMapper.selectById(r.getTargetId()));
        else if("SKILL".equals(r.getTargetType()))m.put("target",skillMapper.selectById(r.getTargetId()));
        else if("REVIEW".equals(r.getTargetType()))m.put("target",reviewMapper.selectById(r.getTargetId()));
        return m;
    }
    @Transactional public Report claimReport(Long id,Long adminId){Report r=reportMapper.selectById(id);if(r==null||!States.Report.PENDING.name().equals(r.getStatus()))throw new BusinessException("举报已被领取或处理");int n=reportMapper.update(null,new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Report>().eq(Report::getId,id).eq(Report::getStatus,States.Report.PENDING.name()).set(Report::getStatus,States.Report.PROCESSING.name()).set(Report::getHandlerId,adminId));if(n!=1)throw new BusinessException("举报已被领取");return reportMapper.selectById(id);}
    public Skill addSkill(String name,String category){if(name==null||name.isBlank()||category==null||category.isBlank())throw new BusinessException("技能名称和分类不能为空");Skill existing=skillMapper.selectOne(new LambdaQueryWrapper<Skill>().eq(Skill::getName,name));if(existing!=null){existing.setStatus(States.Skill.ENABLED.name());existing.setCategory(category);skillMapper.updateById(existing);return existing;}Skill s=new Skill();s.setName(name);s.setCategory(category);s.setStatus(States.Skill.ENABLED.name());s.setCreatedAt(LocalDateTime.now());skillMapper.insert(s);return s;}
    private SkillStatusChange updateSkillStatus(Long id,String status){if(!Set.of(States.Skill.ENABLED.name(),States.Skill.DISABLED.name()).contains(status))throw new BusinessException("状态值无效");Skill s=skillMapper.lockById(id);if(s==null)throw new BusinessException("技能不存在");String previous=s.getStatus();s.setStatus(status);if(skillMapper.updateById(s)!=1)throw new BusinessException("技能状态已变化");return new SkillStatusChange(s,previous);}
    public Map<String,Object> allSkills(int page,int size){Page<Skill> p=skillMapper.selectPage(com.campuslink.common.Pages.request(page,size),new LambdaQueryWrapper<Skill>().orderByAsc(Skill::getId));return com.campuslink.common.Pages.of(p,p.getRecords());}
    private User requiredStudent(Long id){if(id==null)throw new BusinessException("请选择责任用户");User u=accountService.requiredUser(id);if(States.Role.ADMIN.name().equals(u.getRole()))throw new BusinessException("不能对管理员执行该操作");return u;}
    private String updateUserStatus(Long id,String status){if(!Set.of(States.Account.NORMAL.name(),States.Account.BANNED.name()).contains(status))throw new BusinessException("状态值无效");User u=userMapper.lockById(id);if(u==null)throw new BusinessException(404,"用户不存在");if(States.Role.ADMIN.name().equals(u.getRole()))throw new BusinessException("不能禁用管理员");String previous=u.getStatus();u.setStatus(status);u.setTokenVersion(u.getTokenVersion()+1);refreshSessions.delete(new LambdaQueryWrapper<RefreshSession>().eq(RefreshSession::getUserId,id));u.setUpdatedAt(LocalDateTime.now());if(userMapper.updateById(u)!=1)throw new BusinessException("用户状态已变化");return previous;}
    private void delistTask(Long id,String reason){if(reason==null||reason.isBlank())throw new BusinessException("请填写下架原因");CampusTask task=locks.task(id);task.setIsDelisted(true);task.setDelistReason(reason);task.setUpdatedAt(LocalDateTime.now());taskMapper.updateById(task);accountService.notify(task.getPublisherId(),"SYSTEM_NOTICE","任务已下架",reason,id);}
    private double percent(long current,long previous){if(previous==0)return current==0?0:100;return Math.round((current-previous)*1000.0/previous)/10.0;}
    private List<Map<String,Object>> trend(LocalDateTime since,List<LocalDateTime> events){Map<LocalDate,Long> counts=new LinkedHashMap<>();for(int i=0;i<7;i++)counts.put(since.toLocalDate().plusDays(i),0L);for(LocalDateTime e:events)if(e!=null&&counts.containsKey(e.toLocalDate()))counts.merge(e.toLocalDate(),1L,Long::sum);return counts.entrySet().stream().map(e->Map.<String,Object>of("date",e.getKey().toString(),"value",e.getValue())).toList();}
    private List<Map<String,Object>> activityTrend(LocalDateTime since,List<CampusTask> tasks,List<TaskOrder> orders){Map<LocalDate,Set<Long>> users=new LinkedHashMap<>();for(int i=0;i<7;i++)users.put(since.toLocalDate().plusDays(i),new HashSet<>());for(CampusTask t:tasks)if(t.getCreatedAt()!=null&&users.containsKey(t.getCreatedAt().toLocalDate()))users.get(t.getCreatedAt().toLocalDate()).add(t.getPublisherId());for(TaskOrder o:orders)if(o.getCompletedAt()!=null&&users.containsKey(o.getCompletedAt().toLocalDate())){users.get(o.getCompletedAt().toLocalDate()).add(o.getPublisherId());users.get(o.getCompletedAt().toLocalDate()).add(o.getAccepterId());}return users.entrySet().stream().map(e->Map.<String,Object>of("date",e.getKey().toString(),"value",e.getValue().size())).toList();}
    @Transactional public Skill setSkillStatus(Long adminId,Long id,String status,String reason){SkillStatusChange change=updateSkillStatus(id,status);recordAdminAction(adminId,"SKILL",id,ADMIN_SKILL_STATUS,reason,change.previousStatus(),status);return change.skill();}

    private void recordAdminAction(Long adminId,String targetType,Long targetId,String actionType,String reason,String previousStatus,String nextStatus){
        if(reason==null||reason.isBlank())throw new BusinessException("请填写操作原因");
        LocalDateTime now=LocalDateTime.now();Report action=new Report();action.setReporterId(adminId);action.setTargetType(targetType);action.setTargetId(targetId);action.setReasonType(actionType);action.setDescription(reason.trim());action.setStatus(States.Report.RESOLVED.name());action.setHandlerId(adminId);action.setHandleResult(previousStatus+" -> "+nextStatus);action.setCreatedAt(now);action.setHandledAt(now);reportMapper.insert(action);
    }

    private record SkillStatusChange(Skill skill,String previousStatus){}

    private int bounded(int size){return Math.min(50,Math.max(1,size));}private Map<String,Object> pageData(Page<?> page,Object records){Map<String,Object> m=new LinkedHashMap<>();m.put("records",records);m.put("total",page.getTotal());m.put("page",page.getCurrent());m.put("size",page.getSize());m.put("pages",page.getPages());return m;}
}
