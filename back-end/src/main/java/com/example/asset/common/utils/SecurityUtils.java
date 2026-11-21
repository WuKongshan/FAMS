package com.example.asset.common.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

/**
 * 安全上下文工具类 - 从 SecurityContext 获取当前登录用户信息
 */
public class SecurityUtils {

    /**
     * 获取当前用户ID
     */
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof String userIdStr) {
            try {
                return Long.valueOf(userIdStr);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 获取当前用户名
     */
    public static String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        if (auth.getDetails() instanceof Map<?, ?> details) {
            return (String) details.get("username");
        }
        return null;
    }

    /**
     * 获取当前用户真实姓名
     */
    public static String getCurrentRealName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        if (auth.getDetails() instanceof Map<?, ?> details) {
            return (String) details.get("realName");
        }
        return null;
    }

    /**
     * 获取当前用户部门ID
     */
    public static Integer getCurrentDeptId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        if (auth.getDetails() instanceof Map<?, ?> details) {
            return (Integer) details.get("deptId");
        }
        return null;
    }

    /**
     * 获取当前用户角色ID
     */
    public static Integer getCurrentRoleId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        if (auth.getDetails() instanceof Map<?, ?> details) {
            return (Integer) details.get("roleId");
        }
        return null;
    }

    /**
     * 获取当前用户详细信息（从JWT claims放入的details）
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getCurrentUserDetails() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        if (auth.getDetails() instanceof Map) {
            return (Map<String, Object>) auth.getDetails();
        }
        return null;
    }
}
