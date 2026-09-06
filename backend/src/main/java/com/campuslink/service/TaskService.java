package com.campuslink.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuslink.common.BusinessException;
import com.campuslink.common.Pages;
import com.campuslink.domain.*;
import com.campuslink.dto.Requests;
import com.campuslink.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.campuslink.domain.States.Task.*;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskMapper taskMapper;
    private final TaskApplicationMapper applicationMapper;
    private final TaskOrderMapper orderMapper;
    private final TaskCompletionMapper completionMapper;
    private final UserMapper userMapper;
    private final ReportMapper reportMapper;
    private final ReviewMapper reviewMapper;
    private final UserSkillMapper userSkillMapper;
    private final SkillMapper skillMapper;
    private final PointCreditService pointCreditService;
    private final AccountService accountService;
    private final WorkflowLocks locks;
    private final OrderAssignmentHistoryMapper historyMapper;
    private final StorageService storageService;

    @Value("${campuslink.rejection-limit:3}")
    private int rejectionLimit;
    private volatile long recruitingCount = -1;
    private volatile long recruitingCountExpiresAt;

    public Map<String, Object> list(int page, int size, String keyword, String category, String status,
                                    String location, Integer minReward, Integer maxReward,
                                    LocalDateTime startTime, LocalDateTime endTime, String sort) {
        var q = new LambdaQueryWrapper<CampusTask>().eq(CampusTask::getIsDelisted, false);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(CampusTask::getTitle, keyword)
                    .or().like(CampusTask::getDescription, keyword)
                    .or().like(CampusTask::getLocation, keyword));
        }
        if (category != null && !category.isBlank()) q.eq(CampusTask::getCategory, category);
        if (location != null && !location.isBlank()) q.like(CampusTask::getLocation, location);
        if (minReward != null) q.ge(CampusTask::getRewardPoints, minReward);
        if (maxReward != null) q.le(CampusTask::getRewardPoints, maxReward);
        if (minReward != null && maxReward != null && minReward > maxReward) {
            throw new BusinessException("最低积分不能大于最高积分");
        }
        if (startTime != null) q.ge(CampusTask::getTaskTime, startTime);
        if (endTime != null) q.le(CampusTask::getTaskTime, endTime);
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new BusinessException("开始时间不能晚于结束时间");
        }
        String effectiveStatus = status != null && !status.isBlank() ? status : RECRUITING.name();
        q.eq(CampusTask::getStatus, effectiveStatus);
        if (RECRUITING.name().equals(effectiveStatus)) {
            q.gt(CampusTask::getApplicationDeadline, LocalDateTime.now());
        }
        switch (sort == null ? "deadline" : sort) {
            case "rewardAsc" -> q.orderByAsc(CampusTask::getRewardPoints);
            case "rewardDesc" -> q.orderByDesc(CampusTask::getRewardPoints);
            case "newest" -> q.orderByDesc(CampusTask::getCreatedAt);
            case "deadline" -> q.orderByAsc(CampusTask::getApplicationDeadline);
            default -> throw new BusinessException("排序方式无效");
        }
        q.orderByDesc(CampusTask::getId);
        boolean cacheRecruitingCount = RECRUITING.name().equals(effectiveStatus)
                && blank(keyword) && blank(category) && blank(location)
                && minReward == null && maxReward == null && startTime == null && endTime == null;
        Page<CampusTask> pageRequest = Pages.request(page, size);
        pageRequest.setSearchCount(!cacheRecruitingCount);
        Page<CampusTask> result = taskMapper.selectPage(pageRequest, q);
        if (cacheRecruitingCount) result.setTotal(recruitingCount());
        return Pages.of(result, cardViews(result.getRecords()));
    }

    public Map<String, Object> detail(Long id) {
        CampusTask task = requiredTask(id);
        if (Boolean.TRUE.equals(task.getIsDelisted())) throw new BusinessException(404, "任务已下架");
        return taskView(task);
    }

    @Transactional
    public Map<String, Object> publish(Long userId, Requests.TaskCreate dto) {
        User user = userMapper.lockById(userId);
        if (user == null || !States.Account.NORMAL.name().equals(user.getStatus())) {
            throw new BusinessException(403, "账户不可用");
        }
        if (user.getCreditScore() < 60) throw new BusinessException("信用分低于60，暂不能发布");
        LocalDateTime now = LocalDateTime.now();
        if (!dto.taskTime().isAfter(now) || !dto.applicationDeadline().isAfter(now)
                || !dto.applicationDeadline().isBefore(dto.taskTime())) {
            throw new BusinessException("请检查任务和申请截止时间");
        }
        CampusTask task = new CampusTask();
        task.setPublisherId(userId);
        task.setTitle(dto.title());
        task.setCategory(dto.category());
        task.setDescription(dto.description());
        task.setLocation(dto.location());
        task.setTaskTime(dto.taskTime());
        task.setApplicationDeadline(dto.applicationDeadline());
        task.setEstimatedDurationMinutes(dto.estimatedDurationMinutes() == null ? 60 : dto.estimatedDurationMinutes());
        task.setRewardPoints(dto.rewardPoints());
        task.setMinCreditScore(dto.minCreditScore() == null ? 0 : dto.minCreditScore());
        task.setStatus(RECRUITING.name());
        task.setVersion(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        pointCreditService.freeze(user, dto.rewardPoints(), task.getId());
        return taskView(task, user, 0L);
    }

    @Transactional
    public TaskApplication apply(Long id, Long userId, Requests.TaskApply dto) {
        CampusTask task = locks.task(id);
        if (!RECRUITING.name().equals(task.getStatus()) || Boolean.TRUE.equals(task.getIsDelisted())
                || !task.getApplicationDeadline().isAfter(LocalDateTime.now())) {
            throw new BusinessException("任务已停止招募");
        }
        if (userId.equals(task.getPublisherId())) throw new BusinessException("不能申请自己的任务");
        User user = activeUser(userId);
        if (user.getCreditScore() < task.getMinCreditScore()) throw new BusinessException("信用分未达到要求");
        if (dto.expectedFinishTime() != null && !dto.expectedFinishTime().isAfter(task.getTaskTime())) {
            throw new BusinessException("预计完成时间必须晚于任务时间");
        }
        if (applicationMapper.selectCount(new LambdaQueryWrapper<TaskApplication>()
                .eq(TaskApplication::getTaskId, id).eq(TaskApplication::getApplicantId, userId)) > 0) {
            throw new BusinessException("已经申请过该任务");
        }
        TaskApplication application = new TaskApplication();
        application.setTaskId(id);
        application.setApplicantId(userId);
        application.setMessage(dto.message());
        application.setExpectedFinishTime(dto.expectedFinishTime());
        application.setStatus(States.Application.PENDING.name());
        application.setCreatedAt(LocalDateTime.now());
        applicationMapper.insert(application);
        accountService.notify(task.getPublisherId(), "TASK_APPLICATION", "收到新的任务申请",
                user.getNickname() + "申请了任务", id);
        return application;
    }

    @Transactional
    public void withdraw(Long id, Long userId) {
        TaskApplication application = applicationMapper.selectById(id);
        if (application == null) throw new BusinessException(404, "申请不存在");
        locks.task(application.getTaskId());
        if (!userId.equals(application.getApplicantId())) throw new BusinessException(403, "只能撤回本人申请");
        int changed = applicationMapper.update(null, new LambdaUpdateWrapper<TaskApplication>()
                .eq(TaskApplication::getId, id)
                .eq(TaskApplication::getStatus, States.Application.PENDING.name())
                .set(TaskApplication::getStatus, States.Application.WITHDRAWN.name()));
        if (changed != 1) throw new BusinessException("只能撤回待处理申请");
    }

    public Map<String, Object> applications(Long taskId, Long userId, int page, int size) {
        owner(requiredTask(taskId), userId);
        Page<TaskApplication> result = applicationMapper.selectApplicantPage(
                Pages.<TaskApplication>request(page, size), taskId);
        Set<Long> applicantIds = result.getRecords().stream()
                .map(TaskApplication::getApplicantId).collect(Collectors.toSet());
        Map<Long, User> users = usersById(applicantIds);
        Map<Long, Double> ratings = averageRatings(applicantIds);
        Map<Long, Long> completed = completedOrders(applicantIds);
        List<Map<String, Object>> records = result.getRecords().stream().map(application -> {
            Long applicantId = application.getApplicantId();
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("application", application);
            view.put("applicant", AuthService.publicUserView(users.get(applicantId)));
            view.put("completedOrders", completed.getOrDefault(applicantId, 0L));
            view.put("averageRating", roundedRating(ratings.get(applicantId)));
            return view;
        }).toList();
        return Pages.of(result, records);
    }

    public Map<String, Object> applicantProfile(Long taskId, Long applicantId, Long userId,
                                                int skillPage, int skillSize, int reviewPage, int reviewSize) {
        owner(requiredTask(taskId), userId);
        if (applicationMapper.selectCount(new LambdaQueryWrapper<TaskApplication>()
                .eq(TaskApplication::getTaskId, taskId)
                .eq(TaskApplication::getApplicantId, applicantId)) == 0) {
            throw new BusinessException(404, "申请者不存在");
        }
        User applicant = accountService.requiredUser(applicantId);
        Page<UserSkill> offers = userSkillMapper.selectPage(Pages.request(skillPage, skillSize),
                new LambdaQueryWrapper<UserSkill>().eq(UserSkill::getUserId, applicantId)
                        .orderByDesc(UserSkill::getId));
        Set<Long> skillIds = offers.getRecords().stream().map(UserSkill::getSkillId).collect(Collectors.toSet());
        Map<Long, Skill> skills = skillsById(skillIds);
        List<Map<String, Object>> skillViews = offers.getRecords().stream()
                .map(offer -> withSkill(offer, skills.get(offer.getSkillId()))).toList();
        Page<Review> reviews = reviewMapper.selectPage(Pages.request(reviewPage, reviewSize),
                new LambdaQueryWrapper<Review>().eq(Review::getRevieweeId, applicantId)
                        .orderByDesc(Review::getId));
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("applicant", AuthService.publicUserView(applicant));
        view.put("skills", skillViews);
        view.put("skillsTotal", offers.getTotal());
        view.put("skillPage", offers.getCurrent());
        view.put("skillSize", offers.getSize());
        view.put("recentReviews", reviews.getRecords());
        view.put("reviewsTotal", reviews.getTotal());
        view.put("reviewPage", reviews.getCurrent());
        view.put("reviewSize", reviews.getSize());
        view.put("completedOrders", completedOrders(Set.of(applicantId)).getOrDefault(applicantId, 0L));
        view.put("averageRating", roundedRating(averageRatings(Set.of(applicantId)).get(applicantId)));
        return view;
    }

    @Transactional
    public Map<String, Object> acceptApplication(Long applicationId, Long userId) {
        TaskApplication application = applicationMapper.selectById(applicationId);
        if (application == null) throw new BusinessException("申请不存在");
        CampusTask task = locks.task(application.getTaskId());
        owner(task, userId);
        application = applicationMapper.selectById(applicationId);
        if (!States.Application.PENDING.name().equals(application.getStatus())
                || !RECRUITING.name().equals(task.getStatus()) || Boolean.TRUE.equals(task.getIsDelisted())) {
            throw new BusinessException("任务或申请已变化");
        }
        if (activeUser(application.getApplicantId()).getCreditScore() < task.getMinCreditScore()) {
            throw new BusinessException("接取者信用不足");
        }
        choose(application);
        TaskOrder order = new TaskOrder();
        order.setTaskId(task.getId());
        order.setPublisherId(userId);
        order.setAccepterId(application.getApplicantId());
        order.setStatus(WAIT_EXECUTE.name());
        order.setVersion(0);
        order.setAcceptedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order.setDueAt(application.getExpectedFinishTime() != null
                ? application.getExpectedFinishTime()
                : task.getTaskTime().plusMinutes(task.getEstimatedDurationMinutes()));
        orderMapper.insert(order);
        task.setStatus(WAIT_EXECUTE.name());
        saveTask(task);
        notifyBoth(order, "申请已通过", "接取者已确定");
        return orderView(order, 1, 10);
    }

    private void choose(TaskApplication application) {
        applicationMapper.update(null, new LambdaUpdateWrapper<TaskApplication>()
                .eq(TaskApplication::getTaskId, application.getTaskId())
                .eq(TaskApplication::getStatus, States.Application.PENDING.name())
                .set(TaskApplication::getStatus, States.Application.REJECTED.name()));
        application.setStatus(States.Application.ACCEPTED.name());
        if (applicationMapper.updateById(application) != 1) throw new BusinessException("申请状态已变化");
    }

    public Map<String, Object> acceptApplication(Long taskId, Long applicationId, Long userId) {
        TaskApplication application = applicationMapper.selectById(applicationId);
        if (application == null || !taskId.equals(application.getTaskId())) {
            throw new BusinessException("申请不属于该任务");
        }
        return acceptApplication(applicationId, userId);
    }

    public Map<String, Object> mine(Long userId, String kind, int page, int size) {
        if ("published".equals(kind)) return publishedTasks(userId, page, size);
        if ("accepted".equals(kind)) return acceptedTasks(userId, page, size);
        if ("applied".equals(kind)) return appliedTasks(userId, page, size);
        throw new BusinessException("任务分类无效");
    }

    private Map<String, Object> publishedTasks(Long userId, int page, int size) {
        Page<CampusTask> result = taskMapper.selectPage(Pages.request(page, size),
                new LambdaQueryWrapper<CampusTask>().eq(CampusTask::getPublisherId, userId)
                        .orderByDesc(CampusTask::getId));
        Map<Long, TaskOrder> orders = ordersByTask(taskIds(result.getRecords()));
        Map<Long, Long> counts = applicationCounts(taskIds(result.getRecords()));
        User publisher = accountService.requiredUser(userId);
        List<Map<String, Object>> records = result.getRecords().stream().map(task -> {
            Map<String, Object> view = taskView(task, publisher, counts.getOrDefault(task.getId(), 0L));
            TaskOrder order = orders.get(task.getId());
            if (order != null) {
                view.put("orderId", order.getId());
                view.put("orderStatus", order.getStatus());
            }
            return view;
        }).toList();
        return Pages.of(result, records);
    }

    private Map<String, Object> acceptedTasks(Long userId, int page, int size) {
        LambdaQueryWrapper<TaskOrder> query = new LambdaQueryWrapper<>();
        query.and(w -> w.eq(TaskOrder::getAccepterId, userId).or().apply(
                "EXISTS (SELECT 1 FROM order_assignment_history h " +
                        "WHERE h.order_id = task_order.id AND h.previous_accepter_id = {0})", userId));
        query.orderByDesc(TaskOrder::getId);
        Page<TaskOrder> result = orderMapper.selectPage(Pages.request(page, size), query);
        Set<Long> taskIds = result.getRecords().stream().map(TaskOrder::getTaskId).collect(Collectors.toSet());
        Map<Long, CampusTask> tasks = tasksById(taskIds);
        Set<Long> publisherIds = result.getRecords().stream().map(TaskOrder::getPublisherId).collect(Collectors.toSet());
        Map<Long, User> publishers = usersById(publisherIds);
        Map<Long, Long> counts = applicationCounts(taskIds);
        List<Map<String, Object>> records = result.getRecords().stream().map(order -> {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("order", order);
            CampusTask task = tasks.get(order.getTaskId());
            view.put("task", taskView(task, publishers.get(order.getPublisherId()),
                    counts.getOrDefault(task.getId(), 0L)));
            view.put("publisher", AuthService.publicUserView(publishers.get(order.getPublisherId())));
            view.put("historical", !userId.equals(order.getAccepterId()));
            return view;
        }).toList();
        return Pages.of(result, records);
    }

    private Map<String, Object> appliedTasks(Long userId, int page, int size) {
        Page<TaskApplication> result = applicationMapper.selectPage(Pages.request(page, size),
                new LambdaQueryWrapper<TaskApplication>().eq(TaskApplication::getApplicantId, userId)
                        .orderByDesc(TaskApplication::getId));
        Set<Long> taskIds = result.getRecords().stream().map(TaskApplication::getTaskId).collect(Collectors.toSet());
        Map<Long, CampusTask> tasks = tasksById(taskIds);
        Set<Long> publisherIds = tasks.values().stream().map(CampusTask::getPublisherId).collect(Collectors.toSet());
        Map<Long, User> publishers = usersById(publisherIds);
        Map<Long, Long> counts = applicationCounts(taskIds);
        Map<Long, TaskOrder> orders = ordersByTask(taskIds);
        Set<Long> orderIds = orders.values().stream().map(TaskOrder::getId).collect(Collectors.toSet());
        Set<Long> historicalOrders = historicalOrders(orderIds, userId);
        List<Map<String, Object>> records = result.getRecords().stream().map(application -> {
            CampusTask task = tasks.get(application.getTaskId());
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("application", application);
            view.put("task", taskView(task, publishers.get(task.getPublisherId()),
                    counts.getOrDefault(task.getId(), 0L)));
            TaskOrder order = orders.get(task.getId());
            if (order != null && (userId.equals(order.getAccepterId()) || historicalOrders.contains(order.getId()))) {
                view.put("orderId", order.getId());
            }
            return view;
        }).toList();
        return Pages.of(result, records);
    }

    @Transactional
    public Map<String, Object> cancel(Long id, Long userId, String reason) {
        CampusTask task = locks.task(id);
        boolean recruiting = RECRUITING.name().equals(task.getStatus());
        TaskOrder order = orderMapper.selectOne(new LambdaQueryWrapper<TaskOrder>().eq(TaskOrder::getTaskId, id));
        if (recruiting) {
            owner(task, userId);
        } else {
            if (!WAIT_EXECUTE.name().equals(task.getStatus())) throw new BusinessException("当前阶段请发起异常处理");
            if (order == null || (!userId.equals(order.getPublisherId()) && !userId.equals(order.getAccepterId()))) {
                throw new BusinessException(403, "仅任务双方可以取消");
            }
            if (reason == null || reason.isBlank()) throw new BusinessException("取消必须填写原因");
            order = orderMapper.lockById(order.getId());
        }
        if (order != null) {
            userMapper.lockById(Math.min(order.getPublisherId(), order.getAccepterId()));
            userMapper.lockById(Math.max(order.getPublisherId(), order.getAccepterId()));
        }
        pointCreditService.refund(accountService.requiredUser(task.getPublisherId()), task.getRewardPoints(), id);
        if (!recruiting) {
            pointCreditService.changeCredit(accountService.requiredUser(userId), -5,
                    "TASK_CANCEL", id, reason);
        }
        task.setStatus(CANCELLED.name());
        task.setCancelReason(reason == null || reason.isBlank() ? "发布者取消招募" : reason);
        saveTask(task);
        applicationMapper.update(null, new LambdaUpdateWrapper<TaskApplication>()
                .eq(TaskApplication::getTaskId, id)
                .eq(TaskApplication::getStatus, States.Application.PENDING.name())
                .set(TaskApplication::getStatus, States.Application.REJECTED.name()));
        if (order != null) {
            order.setStatus(CANCELLED.name());
            saveOrder(order);
            notifyBoth(order, "任务已取消", task.getCancelReason());
        }
        return taskView(task);
    }

    @Transactional
    public Map<String, Object> startOrder(Long id, Long userId) {
        TaskOrder order = locks.order(id);
        accepter(order, userId);
        if (IN_PROGRESS.name().equals(order.getStatus())) return orderView(order, 1, 10);
        state(order, WAIT_EXECUTE.name());
        order.setStartedAt(LocalDateTime.now());
        transition(order, IN_PROGRESS.name());
        notifyBoth(order, "任务已开始", "接取者开始执行");
        return orderView(order, 1, 10);
    }

    @Transactional
    public TaskCompletion submitCompletion(Long id, Long userId, Requests.Completion dto) {
        TaskOrder order = locks.order(id);
        accepter(order, userId);
        state(order, IN_PROGRESS.name());
        storageService.requireProofReference(dto.proofUrl());
        TaskCompletion completion = new TaskCompletion();
        completion.setOrderId(id);
        completion.setSubmitterId(userId);
        completion.setAssignmentRound(order.getAssignmentRound());
        completion.setDescription(dto.description());
        completion.setProofUrl(dto.proofUrl());
        completion.setSubmitTime(LocalDateTime.now());
        completion.setReviewStatus(States.Completion.PENDING.name());
        completionMapper.insert(completion);
        transition(order, WAIT_ACCEPTANCE.name());
        notifyBoth(order, "任务待验收", "接取者已提交结果");
        return completion;
    }

    @Transactional
    public Map<String, Object> approve(Long id, Long userId) {
        TaskOrder order = locks.order(id);
        publisher(order, userId);
        state(order, WAIT_ACCEPTANCE.name());
        TaskCompletion completion = latestPending(id);
        completion.setReviewStatus(States.Completion.APPROVED.name());
        if (completionMapper.updateById(completion) != 1) throw new BusinessException("完成记录状态已变化");
        CampusTask task = requiredTask(order.getTaskId());
        pointCreditService.settle(accountService.requiredUser(order.getPublisherId()),
                accountService.requiredUser(order.getAccepterId()), task.getRewardPoints(), id);
        order.setCompletedAt(LocalDateTime.now());
        transition(order, COMPLETED.name());
        notifyBoth(order, "验收通过", "积分已经结算");
        return orderView(order, 1, 10);
    }

    @Transactional
    public Map<String, Object> reject(Long id, Long userId, Requests.Reject dto) {
        TaskOrder order = locks.order(id);
        publisher(order, userId);
        state(order, WAIT_ACCEPTANCE.name());
        TaskCompletion completion = latestPending(id);
        completion.setReviewStatus(States.Completion.REJECTED.name());
        completion.setRejectReason(dto.reason());
        if (completionMapper.updateById(completion) != 1) throw new BusinessException("完成记录状态已变化");
        long rejected = completionMapper.selectCount(new LambdaQueryWrapper<TaskCompletion>()
                .eq(TaskCompletion::getOrderId, id)
                .eq(TaskCompletion::getAssignmentRound, order.getAssignmentRound())
                .eq(TaskCompletion::getReviewStatus, States.Completion.REJECTED.name()));
        if (rejected >= rejectionLimit) {
            transition(order, ABNORMAL.name());
            CampusTask task = requiredTask(order.getTaskId());
            task.setAbnormalReason("REJECT_LIMIT");
            saveTask(task);
            Report report = new Report();
            report.setReporterId(userId);
            report.setTargetType("ORDER");
            report.setTargetId(id);
            report.setReasonType("REJECT_LIMIT");
            report.setDescription(dto.reason());
            report.setPreviousStatus(IN_PROGRESS.name());
            report.setStatus(States.Report.PROCESSING.name());
            report.setCreatedAt(LocalDateTime.now());
            reportMapper.insert(report);
        } else {
            transition(order, IN_PROGRESS.name());
        }
        notifyBoth(order, "结果被驳回", dto.reason());
        return orderView(order, 1, 10);
    }

    @Transactional
    public Map<String, Object> reassign(Long id, Long userId, Requests.Reassign dto) {
        CampusTask task = locks.task(id);
        owner(task, userId);
        if (!ABNORMAL.name().equals(task.getStatus()) || task.getAbnormalReason() == null
                || !Set.of("TIMEOUT", "REJECT_LIMIT").contains(task.getAbnormalReason())
                || Boolean.TRUE.equals(task.getIsDelisted())) {
            throw new BusinessException("当前异常不能重新分配");
        }
        TaskOrder order = orderMapper.selectOne(new LambdaQueryWrapper<TaskOrder>().eq(TaskOrder::getTaskId, id));
        order = orderMapper.lockById(order.getId());
        TaskApplication application = applicationMapper.selectById(dto.applicationId());
        if (application == null || !id.equals(application.getTaskId())
                || !Set.of(States.Application.PENDING.name(), States.Application.REJECTED.name())
                    .contains(application.getStatus())
                || application.getApplicantId().equals(order.getAccepterId())) {
            throw new BusinessException("请选择其他未选中的申请者");
        }
        User next = activeUser(application.getApplicantId());
        if (next.getCreditScore() < task.getMinCreditScore() || !dto.dueAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException("请检查接取者信用和新完成期限");
        }
        OrderAssignmentHistory history = new OrderAssignmentHistory();
        history.setOrderId(order.getId());
        history.setPreviousAccepterId(order.getAccepterId());
        history.setNewAccepterId(next.getId());
        history.setPreviousRound(order.getAssignmentRound());
        history.setPreviousStartedAt(order.getStartedAt());
        history.setPreviousDueAt(order.getDueAt());
        history.setReason(dto.reason());
        history.setCreatedAt(LocalDateTime.now());
        historyMapper.insert(history);
        accountService.notify(order.getAccepterId(), "ORDER_STATUS", "任务已重新分配", dto.reason(), order.getId());
        applicationMapper.update(null, new LambdaUpdateWrapper<TaskApplication>()
                .eq(TaskApplication::getTaskId, id)
                .eq(TaskApplication::getStatus, States.Application.ACCEPTED.name())
                .set(TaskApplication::getStatus, States.Application.REJECTED.name()));
        choose(application);
        int cleared = orderMapper.update(null, new LambdaUpdateWrapper<TaskOrder>()
                .eq(TaskOrder::getId, order.getId())
                .set(TaskOrder::getStartedAt, null).set(TaskOrder::getCompletedAt, null));
        if (cleared != 1) throw new BusinessException("订单状态已变化");
        order.setAccepterId(next.getId());
        order.setAssignmentRound(order.getAssignmentRound() + 1);
        order.setDueAt(dto.dueAt());
        order.setAcceptedAt(LocalDateTime.now());
        order.setStartedAt(null);
        order.setCompletedAt(null);
        transition(order, WAIT_EXECUTE.name());
        if (taskMapper.update(null, new LambdaUpdateWrapper<CampusTask>()
                .eq(CampusTask::getId, id).set(CampusTask::getAbnormalReason, null)) != 1) {
            throw new BusinessException("任务状态已变化");
        }
        reportMapper.update(null, new LambdaUpdateWrapper<Report>()
                .eq(Report::getTargetType, "ORDER").eq(Report::getTargetId, order.getId())
                .ne(Report::getStatus, States.Report.RESOLVED.name())
                .set(Report::getStatus, States.Report.RESOLVED.name())
                .set(Report::getHandleResult, "发布者重新分配：" + dto.reason())
                .set(Report::getHandledAt, LocalDateTime.now()));
        notifyBoth(order, "任务已重新分配", "已更新接取者及完成期限");
        return orderView(order, 1, 10);
    }

    public Map<String, Object> order(Long id, Long userId, int page, int size) {
        TaskOrder order = requiredOrder(id);
        if (userId.equals(order.getPublisherId()) || userId.equals(order.getAccepterId())) {
            return orderView(order, page, size);
        }
        long count = historyMapper.selectCount(new LambdaQueryWrapper<OrderAssignmentHistory>()
                .eq(OrderAssignmentHistory::getOrderId,id)
                .eq(OrderAssignmentHistory::getPreviousAccepterId,userId));
        if (count == 0) throw new BusinessException(403, "无权查看订单");
        Page<TaskCompletion> completions = completionMapper.selectPage(Pages.request(page, size),
                new LambdaQueryWrapper<TaskCompletion>().eq(TaskCompletion::getOrderId, id)
                        .eq(TaskCompletion::getSubmitterId, userId).orderByDesc(TaskCompletion::getId));
        return Map.of("historical", true, "task", taskView(requiredTask(order.getTaskId())),
                "completions", completions.getRecords(), "completionTotal", completions.getTotal());
    }

    private Map<String, Object> orderView(TaskOrder order, int page, int size) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("order", order);
        view.put("task", taskView(requiredTask(order.getTaskId())));
        view.put("publisher", AuthService.publicUserView(accountService.requiredUser(order.getPublisherId())));
        view.put("accepter", AuthService.publicUserView(accountService.requiredUser(order.getAccepterId())));
        Page<TaskCompletion> completions = completionMapper.selectPage(Pages.request(page, size),
                new LambdaQueryWrapper<TaskCompletion>().eq(TaskCompletion::getOrderId, order.getId())
                        .orderByDesc(TaskCompletion::getId));
        view.put("completions", completions.getRecords());
        view.put("completionTotal", completions.getTotal());
        return view;
    }

    private List<Map<String, Object>> cardViews(List<CampusTask> tasks) {
        Set<Long> publisherIds = tasks.stream().map(CampusTask::getPublisherId).collect(Collectors.toSet());
        Map<Long, User> publishers = usersById(publisherIds);
        Map<Long, Long> counts = applicationCounts(taskIds(tasks));
        return tasks.stream().map(task -> card(task, publishers.get(task.getPublisherId()),
                counts.getOrDefault(task.getId(), 0L))).toList();
    }

    private Map<String, Object> taskView(CampusTask task) {
        User publisher = userMapper.selectById(task.getPublisherId());
        long count = applicationCounts(Set.of(task.getId())).getOrDefault(task.getId(), 0L);
        return taskView(task, publisher, count);
    }

    private Map<String, Object> taskView(CampusTask task, User publisher, long applicationCount) {
        Map<String, Object> view = card(task, publisher, applicationCount);
        view.put("description", task.getDescription());
        view.put("minCreditScore", task.getMinCreditScore());
        view.put("abnormalReason", task.getAbnormalReason());
        view.put("cancelReason", task.getCancelReason());
        return view;
    }

    private Map<String, Object> card(CampusTask task, User publisher, long applicationCount) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", task.getId());
        view.put("publisherId", task.getPublisherId());
        view.put("title", task.getTitle());
        view.put("category", task.getCategory());
        String description = task.getDescription() == null ? "" : task.getDescription();
        view.put("descriptionSummary", description.substring(0, Math.min(120, description.length())));
        view.put("location", task.getLocation());
        view.put("taskTime", task.getTaskTime());
        view.put("estimatedDurationMinutes", task.getEstimatedDurationMinutes());
        view.put("applicationDeadline", task.getApplicationDeadline());
        view.put("applicationCount", applicationCount);
        view.put("rewardPoints", task.getRewardPoints());
        view.put("status", task.getStatus());
        view.put("isDelisted", task.getIsDelisted());
        view.put("createdAt", task.getCreatedAt());
        if (publisher != null) {
            view.put("publisherNickname", publisher.getNickname());
            view.put("publisherCredit", publisher.getCreditScore());
        }
        return view;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private long recruitingCount() {
        long now = System.currentTimeMillis();
        if (now < recruitingCountExpiresAt) return recruitingCount;
        synchronized (this) {
            now = System.currentTimeMillis();
            if (now >= recruitingCountExpiresAt) {
                recruitingCount = taskMapper.countVisibleRecruiting(RECRUITING.name(),LocalDateTime.now());
                recruitingCountExpiresAt = now + 1000;
            }
            return recruitingCount;
        }
    }

    private Map<Long, Long> applicationCounts(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return applicationMapper.countActiveByTaskIds(ids,States.Application.WITHDRAWN.name()).stream()
                .collect(Collectors.toMap(MapperRows.AggregateValue::getId,row->row.getMetricValue().longValue()));
    }

    private Map<Long, Double> averageRatings(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return reviewMapper.averageRatingsByIds(ids).stream()
                .collect(Collectors.toMap(MapperRows.AggregateValue::getId,MapperRows.AggregateValue::getMetricValue));
    }

    private Map<Long, Long> completedOrders(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return orderMapper.completedCountsByIds(ids,COMPLETED.name()).stream()
                .collect(Collectors.toMap(MapperRows.AggregateValue::getId,row->row.getMetricValue().longValue()));
    }

    private Set<Long> historicalOrders(Collection<Long> ids, Long userId) {
        if (ids.isEmpty()) return Set.of();
        return historyMapper.selectList(new LambdaQueryWrapper<OrderAssignmentHistory>()
                        .in(OrderAssignmentHistory::getOrderId,ids)
                        .eq(OrderAssignmentHistory::getPreviousAccepterId,userId)).stream()
                .map(OrderAssignmentHistory::getOrderId).collect(Collectors.toSet());
    }

    private Map<Long, User> usersById(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return userMapper.selectByIds(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Map<Long, Skill> skillsById(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return skillMapper.selectByIds(ids).stream().collect(Collectors.toMap(Skill::getId, Function.identity()));
    }

    private Map<Long, CampusTask> tasksById(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return taskMapper.selectByIds(ids).stream().collect(Collectors.toMap(CampusTask::getId, Function.identity()));
    }

    private Map<Long, TaskOrder> ordersByTask(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return orderMapper.selectList(new LambdaQueryWrapper<TaskOrder>().in(TaskOrder::getTaskId, ids)).stream()
                .collect(Collectors.toMap(TaskOrder::getTaskId, Function.identity()));
    }

    private Set<Long> taskIds(Collection<CampusTask> tasks) {
        return tasks.stream().map(CampusTask::getId).collect(Collectors.toSet());
    }

    private Map<String, Object> withSkill(UserSkill record, Skill skill) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("record", record);
        view.put("skill", skill);
        return view;
    }

    private double roundedRating(Double value) {
        return value == null ? 0 : Math.round(value * 10) / 10.0;
    }

    private CampusTask requiredTask(Long id) {
        CampusTask task = taskMapper.selectById(id);
        if (task == null) throw new BusinessException(404, "任务不存在");
        return task;
    }

    private TaskOrder requiredOrder(Long id) {
        TaskOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException(404, "订单不存在");
        return order;
    }

    private User activeUser(Long id) {
        User user = accountService.requiredUser(id);
        if (!States.Account.NORMAL.name().equals(user.getStatus())) throw new BusinessException(403, "账户已禁用");
        return user;
    }

    private void owner(CampusTask task, Long userId) {
        if (!userId.equals(task.getPublisherId())) throw new BusinessException(403, "仅发布者可操作");
    }

    private void publisher(TaskOrder order, Long userId) {
        if (!userId.equals(order.getPublisherId())) throw new BusinessException(403, "仅发布者可操作");
    }

    private void accepter(TaskOrder order, Long userId) {
        if (!userId.equals(order.getAccepterId())) throw new BusinessException(403, "仅接取者可操作");
    }

    private void state(TaskOrder order, String expected) {
        if (!expected.equals(order.getStatus())) throw new BusinessException("当前状态不能执行操作");
    }

    private void saveTask(CampusTask task) {
        task.setUpdatedAt(LocalDateTime.now());
        if (taskMapper.updateById(task) != 1) throw new BusinessException("任务状态已变化");
    }

    private void saveOrder(TaskOrder order) {
        order.setUpdatedAt(LocalDateTime.now());
        if (orderMapper.updateById(order) != 1) throw new BusinessException("订单状态已变化");
    }

    private void transition(TaskOrder order, String status) {
        order.setStatus(status);
        saveOrder(order);
        CampusTask task = requiredTask(order.getTaskId());
        task.setStatus(status);
        saveTask(task);
    }

    private void notifyBoth(TaskOrder order, String title, String text) {
        accountService.notify(order.getPublisherId(), "ORDER_STATUS", title, text, order.getId());
        accountService.notify(order.getAccepterId(), "ORDER_STATUS", title, text, order.getId());
    }

    private TaskCompletion latestPending(Long id) {
        TaskCompletion completion = completionMapper.selectOne(new LambdaQueryWrapper<TaskCompletion>()
                .eq(TaskCompletion::getOrderId, id)
                .eq(TaskCompletion::getReviewStatus, States.Completion.PENDING.name())
                .orderByDesc(TaskCompletion::getId).last("limit 1"));
        if (completion == null) throw new BusinessException("没有待验收结果");
        return completion;
    }
}
