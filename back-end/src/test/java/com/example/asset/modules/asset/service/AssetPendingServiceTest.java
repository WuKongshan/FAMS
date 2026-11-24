package com.example.asset.modules.asset.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.asset.modules.asset.entity.Asset;
import com.example.asset.modules.asset.mapper.AssetMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AssetPendingService} 的分支逻辑测试。
 *
 * <p>覆盖三种占用结果（直接成功 / 持有者在途 / 脏占位自愈）与释放的容错行为。
 * 不验证 SQL 本身，SQL 语义由端到端用例覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class AssetPendingServiceTest {

    @Mock
    private AssetMapper assetMapper;

    @InjectMocks
    private AssetPendingService assetPendingService;

    /**
     * MyBatis-Plus 的 LambdaUpdateWrapper 需要实体元数据才能把 Lambda 解析成列名，
     * 而这些元数据平时由 MyBatis-Plus 启动时注册。纯单元测试没有 Spring 上下文，
     * 因此在这里手动注册一次。
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Asset.class);
    }

    @Test
    @DisplayName("资产无占位 → 占用成功，且不会去清理脏占位")
    void claim_whenNoHolder_returnsTrue() {
        when(assetMapper.update(isNull(), any())).thenReturn(1);

        boolean claimed = assetPendingService.claim(100L, 1L);

        assertThat(claimed).isTrue();
        verify(assetMapper, never()).clearStaleClaim(anyLong());
    }

    @Test
    @DisplayName("持有者仍在途 → 占用失败，且不做清理（不会误伤正常占位）")
    void claim_whenHolderStillInFlight_returnsFalse() {
        when(assetMapper.update(isNull(), any())).thenReturn(0);
        when(assetMapper.clearStaleClaim(100L)).thenReturn(0);

        boolean claimed = assetPendingService.claim(100L, 2L);

        assertThat(claimed).isFalse();
        verify(assetMapper, times(1)).update(isNull(), any());   // 只尝试一次，不重试
        verify(assetMapper).clearStaleClaim(100L);
    }

    @Test
    @DisplayName("存在脏占位 → 清理后重试一次并占用成功")
    void claim_whenStalePlaceholder_clearsThenRetriesAndSucceeds() {
        when(assetMapper.update(isNull(), any())).thenReturn(0, 1);   // 首次失败、重试成功
        when(assetMapper.clearStaleClaim(100L)).thenReturn(1);

        boolean claimed = assetPendingService.claim(100L, 3L);

        assertThat(claimed).isTrue();
        verify(assetMapper, times(2)).update(isNull(), any());
        verify(assetMapper).clearStaleClaim(100L);
    }

    @Test
    @DisplayName("释放时占位已不在 → 只记日志，不抛异常（不能让合法审批因此失败）")
    void release_whenNothingMatched_doesNotThrow() {
        when(assetMapper.update(isNull(), any())).thenReturn(0);

        assertThatCode(() -> assetPendingService.release(100L, 4L)).doesNotThrowAnyException();

        verify(assetMapper, times(1)).update(isNull(), any());
    }
}
