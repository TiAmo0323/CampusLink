package com.campuslink.config;

import com.campuslink.common.BusinessException;

public final class AuthContext {
    private static final ThreadLocal<Identity> HOLDER = new ThreadLocal<>();
    private AuthContext() {}
    public static void set(Long userId, String role) { HOLDER.set(new Identity(userId, role)); }
    public static Long userId() { if (HOLDER.get() == null) throw new BusinessException(401, "请先登录"); return HOLDER.get().userId(); }
    public static String role() { if (HOLDER.get() == null) throw new BusinessException(401, "请先登录"); return HOLDER.get().role(); }
    public static void requireAdmin() { if (!"ADMIN".equals(role())) throw new BusinessException(403, "需要管理员权限"); }
    public static void clear() { HOLDER.remove(); }
    private record Identity(Long userId, String role) {}
}
