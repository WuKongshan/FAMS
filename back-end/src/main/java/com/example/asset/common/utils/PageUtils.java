package com.example.asset.common.utils;

/**
 * 分页参数收敛。
 *
 * <p><b>为什么需要</b>：分页接口的 {@code size} 直接来自请求参数，调用方传 100000
 * 就会让 MyBatis-Plus 拼出一条近乎全表的结果集，内存占用与响应体都可能被打爆，
 * 是典型的资源耗尽入口（也常被用来做慢查询压测）。这里统一设上限。
 *
 * <p><b>为什么超出时截断而不是报错</b>：截断对前端更友好 —— 分页元数据里的
 * {@code total} 仍然是真实总数，前端照常翻页即可；而返回 400 会让一个"参数没校验好"
 * 的调用方直接不可用。
 *
 * <p><b>为什么放在 Controller 边界</b>：这是外部输入进入系统的位置，把校验放在这里，
 * Service 层不必重复防御，也不会出现"某个 Service 忘了校验"的漏洞。
 */
public final class PageUtils {

    /**
     * 单页最大条数。100 足以覆盖任何正常表格展示，
     * 需要更多数据的场景（导出、全量统计）应当走专用接口而不是放大 size。
     */
    public static final int MAX_PAGE_SIZE = 100;

    /** 页码兜底：默认第 1 页 */
    private static final int DEFAULT_CURRENT = 1;

    /** 每页条数兜底：与各接口原有的 defaultValue="10" 保持一致 */
    private static final int DEFAULT_SIZE = 10;

    private PageUtils() {
    }

    /**
     * 收敛每页条数：非法（null / ≤0）回落到默认值，超过上限则截断到上限。
     */
    public static int clampSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * 收敛页码：非法（null / <1）回落到第 1 页。
     * 负数或 0 会算出负的 offset，在 SQL 层表现为语法错误，必须在这里挡掉。
     */
    public static int clampCurrent(Integer current) {
        if (current == null || current < DEFAULT_CURRENT) {
            return DEFAULT_CURRENT;
        }
        return current;
    }
}
