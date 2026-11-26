package com.example.asset.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分页参数收敛的回归测试。
 *
 * <p>锁住三条不变量：超上限截断、非法值回落、页码不小于 1。
 * 这三条一旦被改松，分页接口就重新变成可被放大的资源入口。
 */
class PageUtilsTest {

    @Test
    @DisplayName("超过上限的 size 被截断到上限")
    void size_overLimit_isClamped() {
        assertThat(PageUtils.clampSize(100000)).isEqualTo(PageUtils.MAX_PAGE_SIZE);
        assertThat(PageUtils.clampSize(PageUtils.MAX_PAGE_SIZE + 1)).isEqualTo(PageUtils.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("等于上限的 size 原样保留")
    void size_atLimit_isKept() {
        assertThat(PageUtils.clampSize(PageUtils.MAX_PAGE_SIZE)).isEqualTo(PageUtils.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("正常范围内的 size 原样保留")
    void size_withinRange_isKept() {
        assertThat(PageUtils.clampSize(20)).isEqualTo(20);
        assertThat(PageUtils.clampSize(1)).isEqualTo(1);
    }

    @Test
    @DisplayName("null / 0 / 负数 回落到默认 10")
    void size_invalid_fallsBackToDefault() {
        assertThat(PageUtils.clampSize(null)).isEqualTo(10);
        assertThat(PageUtils.clampSize(0)).isEqualTo(10);
        assertThat(PageUtils.clampSize(-1)).isEqualTo(10);
    }

    @Test
    @DisplayName("页码为 null / 0 / 负数 时回落到第 1 页")
    void current_invalid_fallsBackToFirstPage() {
        assertThat(PageUtils.clampCurrent(null)).isEqualTo(1);
        assertThat(PageUtils.clampCurrent(0)).isEqualTo(1);
        assertThat(PageUtils.clampCurrent(-5)).isEqualTo(1);
    }

    @Test
    @DisplayName("正常页码原样保留")
    void current_valid_isKept() {
        assertThat(PageUtils.clampCurrent(1)).isEqualTo(1);
        assertThat(PageUtils.clampCurrent(37)).isEqualTo(37);
    }
}
