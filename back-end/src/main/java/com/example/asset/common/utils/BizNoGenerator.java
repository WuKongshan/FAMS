package com.example.asset.common.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 业务单号生成器。
 *
 * <p>格式：{前缀}{yyyyMMdd}-{自增ID补齐 6 位}，例如 {@code LY20261124-000024}。</p>
 *
 * <p><b>为什么用自增 ID 做流水号</b>：不需要额外的序列表、不加锁、不额外查库，
 * 且自增 ID 天然唯一，单号必然不重复。
 * 若改成「按天从 1 计数」，就必须 SELECT MAX + 1 再插入，并发下要么加锁要么靠唯一索引冲突重试，
 * 复杂度和故障面都更大；单号里带日期已经足够表达「哪天提的」。</p>
 *
 * <p>调用时机：必须先 insert 拿到主键，再生成单号并 update。</p>
 */
public final class BizNoGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 领用 */
    private static final String PREFIX_BORROW = "LY";
    /** 归还 */
    private static final String PREFIX_RETURN = "GH";
    /** 报废 */
    private static final String PREFIX_SCRAP = "BF";
    /** 报修工单 */
    private static final String PREFIX_REPAIR = "WX";

    private BizNoGenerator() {
    }

    /** 领用 / 归还申请单号 */
    public static String applyNo(String applyType, Long id) {
        return build("RETURN".equals(applyType) ? PREFIX_RETURN : PREFIX_BORROW, id);
    }

    /** 报废申请单号 */
    public static String scrapNo(Long id) {
        return build(PREFIX_SCRAP, id);
    }

    /** 报修工单号 */
    public static String repairNo(Long id) {
        return build(PREFIX_REPAIR, id);
    }

    private static String build(String prefix, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("生成单号需要已持久化的主键 id");
        }
        return prefix + LocalDate.now().format(DAY) + "-" + String.format("%06d", id);
    }
}
