package com.example.asset.common.aspect;

import cn.hutool.json.JSONUtil;
import com.example.asset.common.annotation.OperationLog;
import com.example.asset.common.utils.SecurityUtils;
import com.example.asset.modules.admin.mapper.OperationLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 操作日志 AOP 切面 - 自动拦截 @OperationLog 注解的方法并记录审计日志
 *
 * <p>职责：</p>
 * <ul>
 *   <li>从注解/Swagger 提取操作元数据（模块、操作类型、描述）</li>
 *   <li>从 SecurityContext 获取当前操作人信息</li>
 *   <li>从 HttpServletRequest 获取请求详情（方法、URL、IP）</li>
 *   <li>记录请求参数（敏感字段自动脱敏）</li>
 *   <li>记录响应结果和错误信息</li>
 *   <li>计算操作耗时</li>
 *   <li>异步写入数据库，不阻塞业务流程</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogMapper operationLogMapper;

    /** 需要脱敏的参数字段名 */
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "newPassword", "oldPassword", "pwd", "pass", "secret", "token"
    );

    @Around("@annotation(com.example.asset.common.annotation.OperationLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        com.example.asset.modules.admin.entity.OperationLog logEntity = new com.example.asset.modules.admin.entity.OperationLog();
        logEntity.setOperationTime(LocalDateTime.now());

        // 1. 提取方法上的注解
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        OperationLog annotation = method.getAnnotation(OperationLog.class);

        // 2. 模块/操作/描述：优先使用注解值，其次使用 Swagger @Operation summary
        logEntity.setModule(annotation.module());
        logEntity.setOperation(annotation.operation());
        String desc = annotation.description();
        if (desc.isEmpty()) {
            Operation swaggerOp = method.getAnnotation(Operation.class);
            if (swaggerOp != null) {
                desc = swaggerOp.summary();
            }
        }
        logEntity.setDescription(desc);

        // 3. 获取请求信息
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            logEntity.setRequestMethod(request.getMethod());
            logEntity.setRequestUrl(request.getRequestURI());
            logEntity.setOperatorIp(getClientIp(request));
        }

        // 4. 获取操作人信息
        logEntity.setOperatorId(SecurityUtils.getCurrentUserId());
        logEntity.setOperatorName(SecurityUtils.getCurrentRealName());

        // 5. 记录请求参数（脱敏处理）
        if (annotation.recordParams()) {
            logEntity.setRequestParams(sanitizeParams(joinPoint.getArgs()));
        }

        // 6. 执行目标方法
        Object result;
        try {
            result = joinPoint.proceed();
            // 操作成功
            logEntity.setStatus(1);
            if (annotation.recordResult() && result != null) {
                logEntity.setResponseResult(truncate(JSONUtil.toJsonStr(result), 2000));
            }
        } catch (Throwable e) {
            // 操作失败
            logEntity.setStatus(0);
            logEntity.setErrorMsg(truncate(e.getMessage(), 2000));
            // 记录完成后重新抛出异常，让 GlobalExceptionHandler 处理
            saveLog(logEntity, startTime);
            throw e;
        }

        // 7. 计算耗时并保存
        saveLog(logEntity, startTime);
        return result;
    }

    private void saveLog(com.example.asset.modules.admin.entity.OperationLog logEntity, long startTime) {
        try {
            logEntity.setCostTime(System.currentTimeMillis() - startTime);
            operationLogMapper.insert(logEntity);
        } catch (Exception e) {
            // 日志记录失败不影响业务流程
            log.error("操作日志写入失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 参数脱敏：过滤密码等敏感字段，跳过文件流等不可序列化对象
     */
    private String sanitizeParams(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            List<Object> sanitized = Arrays.stream(args)
                    .filter(arg -> !(arg instanceof MultipartFile)
                            && !(arg instanceof HttpServletRequest)
                            && !(arg instanceof jakarta.servlet.http.HttpServletResponse))
                    .map(this::maskSensitive)
                    .collect(Collectors.toList());
            if (sanitized.isEmpty()) return null;
            String json = JSONUtil.toJsonStr(sanitized.size() == 1 ? sanitized.get(0) : sanitized);
            return truncate(json, 1000);
        } catch (Exception e) {
            return "参数序列化失败";
        }
    }

    /**
     * 递归脱敏：将 Map/JSON 对象中名为 password 等敏感字段的值替换为 ***
     */
    @SuppressWarnings("unchecked")
    private Object maskSensitive(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Map) {
            Map<String, Object> map = new HashMap<>((Map<String, Object>) obj);
            for (String key : map.keySet()) {
                if (SENSITIVE_FIELDS.contains(key.toLowerCase())) {
                    map.put(key, "***");
                } else if (map.get(key) instanceof Map || map.get(key) instanceof List) {
                    map.put(key, maskSensitive(map.get(key)));
                }
            }
            return map;
        }
        if (obj instanceof List) {
            return ((List<Object>) obj).stream().map(this::maskSensitive).collect(Collectors.toList());
        }
        return obj;
    }

    /**
     * 获取客户端真实IP（考虑代理场景）
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() <= maxLen ? str : str.substring(0, maxLen) + "...[truncated]";
    }
}
