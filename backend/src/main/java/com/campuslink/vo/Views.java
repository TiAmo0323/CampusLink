package com.campuslink.vo;

import com.campuslink.domain.*;
import com.fasterxml.jackson.annotation.JsonAnyGetter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stable API view models. Services may compose internal maps while controllers expose
 * named VO contracts instead of persistence entities or raw maps.
 */
public final class Views {
    private Views() {}

    public abstract static class MapVO {
        private final Map<String, Object> values;
        protected MapVO(Map<String, Object> values) { this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values)); }
        @JsonAnyGetter public Map<String, Object> values() { return values; }
    }

    public static final class LoginVO extends MapVO { public LoginVO(Map<String,Object> value){super(value);} }
    public static final class UserVO extends MapVO { public UserVO(Map<String,Object> value){super(value);} }
    public static final class TaskDetailVO extends MapVO { public TaskDetailVO(Map<String,Object> value){super(value);} }
    public static final class TaskOrderVO extends MapVO { public TaskOrderVO(Map<String,Object> value){super(value);} }
    public static final class ApplicantProfileVO extends MapVO { public ApplicantProfileVO(Map<String,Object> value){super(value);} }
    public static final class SkillProfileVO extends MapVO { public SkillProfileVO(Map<String,Object> value){super(value);} }
    public static final class WalletVO extends MapVO { public WalletVO(Map<String,Object> value){super(value);} }
    public static final class DashboardVO extends MapVO { public DashboardVO(Map<String,Object> value){super(value);} }
    public static final class ReportEvidenceVO extends MapVO { public ReportEvidenceVO(Map<String,Object> value){super(value);} }

    public record UploadVO(String url,String originalName,long size) {
        public static UploadVO from(Map<String,Object> value) {
            return new UploadVO((String)value.get("url"),(String)value.get("originalName"),((Number)value.get("size")).longValue());
        }
    }

    public record PageVO<T>(long page,long size,long total,List<T> records) {
        @SuppressWarnings("unchecked")
        public static PageVO<Object> from(Map<String,Object> value) {
            return new PageVO<>(((Number)value.get("page")).longValue(),
                    ((Number)value.get("size")).longValue(),
                    ((Number)value.get("total")).longValue(),
                    (List<Object>)value.get("records"));
        }
    }

    public record TaskApplicationVO(Long id,Long taskId,Long applicantId,String message,
                                    LocalDateTime expectedFinishTime,String status,LocalDateTime createdAt) {
        public static TaskApplicationVO from(TaskApplication value){return new TaskApplicationVO(value.getId(),value.getTaskId(),value.getApplicantId(),value.getMessage(),value.getExpectedFinishTime(),value.getStatus(),value.getCreatedAt());}
    }
    public record TaskCompletionVO(Long id,Long orderId,Long submitterId,Integer assignmentRound,
                                  String description,String proofUrl,LocalDateTime submitTime,
                                  String reviewStatus,String rejectReason) {
        public static TaskCompletionVO from(TaskCompletion value){return new TaskCompletionVO(value.getId(),value.getOrderId(),value.getSubmitterId(),value.getAssignmentRound(),value.getDescription(),value.getProofUrl(),value.getSubmitTime(),value.getReviewStatus(),value.getRejectReason());}
    }
    public record SkillExchangeVO(Long id,Long requesterId,Long providerId,Long requestSkillId,
                                  Long exchangeSkillId,String message,LocalDateTime scheduledTime,
                                  String status,LocalDateTime createdAt,LocalDateTime providerCompletedAt,
                                  LocalDateTime completedAt) {
        public static SkillExchangeVO from(SkillExchange value){return new SkillExchangeVO(value.getId(),value.getRequesterId(),value.getProviderId(),value.getRequestSkillId(),value.getExchangeSkillId(),value.getMessage(),value.getScheduledTime(),value.getStatus(),value.getCreatedAt(),value.getProviderCompletedAt(),value.getCompletedAt());}
    }
    public record ReviewVO(Long id,String businessType,Long businessId,Long reviewerId,Long revieweeId,
                           Integer rating,String content,LocalDateTime createdAt) {
        public static ReviewVO from(Review value){return new ReviewVO(value.getId(),value.getBusinessType(),value.getBusinessId(),value.getReviewerId(),value.getRevieweeId(),value.getRating(),value.getContent(),value.getCreatedAt());}
    }
    public record ReportVO(Long id,Long reporterId,String targetType,Long targetId,String reasonType,
                           String description,String previousStatus,String status,Long handlerId,
                           String handleResult,LocalDateTime createdAt,LocalDateTime handledAt) {
        public static ReportVO from(Report value){return new ReportVO(value.getId(),value.getReporterId(),value.getTargetType(),value.getTargetId(),value.getReasonType(),value.getDescription(),value.getPreviousStatus(),value.getStatus(),value.getHandlerId(),value.getHandleResult(),value.getCreatedAt(),value.getHandledAt());}
    }
    public record SkillVO(Long id,String name,String category,String status,LocalDateTime createdAt) {
        public static SkillVO from(Skill value){return new SkillVO(value.getId(),value.getName(),value.getCategory(),value.getStatus(),value.getCreatedAt());}
    }
}
