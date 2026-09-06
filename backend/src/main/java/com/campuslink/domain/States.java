package com.campuslink.domain;
public final class States {
 private States() {}
 public enum Task { RECRUITING, WAIT_EXECUTE, IN_PROGRESS, WAIT_ACCEPTANCE, COMPLETED, CANCELLED, ABNORMAL }
 public enum Application { PENDING, ACCEPTED, REJECTED, WITHDRAWN }
 public enum Completion { PENDING, APPROVED, REJECTED }
 public enum Exchange { PENDING, ACCEPTED, IN_PROGRESS, COMPLETED, REJECTED, CANCELLED }
 public enum Report { PENDING, PROCESSING, RESOLVED }
 public enum Account { NORMAL, BANNED }
 public enum Role { STUDENT, ADMIN }
 public enum Skill { ENABLED, DISABLED }
}
