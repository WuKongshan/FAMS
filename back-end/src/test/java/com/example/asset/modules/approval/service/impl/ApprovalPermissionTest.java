package com.example.asset.modules.approval.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.asset.common.exception.BusinessException;
import com.example.asset.modules.approval.entity.ApplyOrder;
import com.example.asset.modules.approval.flow.ApplyWorkflowService;
import com.example.asset.modules.approval.mapper.ApplyOrderMapper;
import com.example.asset.modules.approval.mapper.ApprovalRecordMapper;
import com.example.asset.modules.asset.mapper.AssetMapper;
import com.example.asset.modules.asset.service.AssetPendingService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审批模块的数据范围（对象级授权）回归测试。
 *
 * <p>对应问题：普通师生可通过列表/详情接口读取他人单据。这里锁住三条不变量：
 * <ol>
 *   <li>role=3 查列表时，SQL 必须带 {@code apply_user_id = 本人}；</li>
 *   <li>role=3 传入他人的 applyUserId 必须被忽略；</li>
 *   <li>role=3 读取他人单据详情必须抛业务异常。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ApprovalPermissionTest {

    private static final int ROLE_ADMIN = 1;
    private static final int ROLE_DEPT_ADMIN = 2;
    private static final int ROLE_STUDENT = 3;

    @Mock private ApplyOrderMapper applyOrderMapper;
    @Mock private ApprovalRecordMapper approvalRecordMapper;
    @Mock private AssetMapper assetMapper;
    @Mock private ApplyWorkflowService applyWorkflowService;
    @Mock private AssetPendingService assetPendingService;

    @InjectMocks private ApprovalServiceImpl approvalService;

    /** LambdaQueryWrapper 需要实体元数据才能把 Lambda 解析成列名，单元测试里手动注册。 */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ApplyOrder.class);
    }

    /** 捕获 service 实际传给 mapper 的查询条件 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private LambdaQueryWrapper<ApplyOrder> captureWrapper() {
        ArgumentCaptor<LambdaQueryWrapper<ApplyOrder>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(applyOrderMapper).selectPage(any(), captor.capture());
        return captor.getValue();
    }

    private static String segment(LambdaQueryWrapper<ApplyOrder> wrapper) {
        String sql = wrapper.getSqlSegment();
        return sql == null ? "" : sql;
    }

    @Test
    @DisplayName("普通师生查列表：强制按本人过滤，且忽略前端传入的他人 applyUserId")
    void pageOrders_asStudent_forcesSelfFilterAndIgnoresForeignApplyUserId() {
        when(applyOrderMapper.selectPage(any(), any())).thenAnswer(inv -> inv.getArgument(0));

        // 攻击场景：前端传了别人的 id=999，登录身份是 userId=7
        approvalService.pageOrders(1, 10, null, null, null, 999L, 7L, 1L, ROLE_STUDENT);

        LambdaQueryWrapper<ApplyOrder> wrapper = captureWrapper();
        assertThat(segment(wrapper)).contains("apply_user_id");

        List<Object> params = List.copyOf(wrapper.getParamNameValuePairs().values());
        assertThat(params).contains(7L);          // 本人条件生效
        assertThat(params).doesNotContain(999L);   // 他人的 id 被丢弃
    }

    @Test
    @DisplayName("学院管理员查列表：按登录态院系过滤，忽略前端传入的 deptId")
    void pageOrders_asDeptAdmin_filtersByOwnDept() {
        when(applyOrderMapper.selectPage(any(), any())).thenAnswer(inv -> inv.getArgument(0));

        // 前端传 deptId=99（别人的院系），登录态院系是 3
        approvalService.pageOrders(1, 10, null, null, 99L, null, 5L, 3L, ROLE_DEPT_ADMIN);

        LambdaQueryWrapper<ApplyOrder> wrapper = captureWrapper();
        assertThat(segment(wrapper)).contains("dept_id");

        List<Object> params = List.copyOf(wrapper.getParamNameValuePairs().values());
        assertThat(params).contains(3L);
        assertThat(params).doesNotContain(99L);
    }

    @Test
    @DisplayName("超级管理员查列表：不强制加申请人或院系条件")
    void pageOrders_asAdmin_hasNoForcedScope() {
        when(applyOrderMapper.selectPage(any(), any())).thenAnswer(inv -> inv.getArgument(0));

        approvalService.pageOrders(1, 10, null, null, null, null, 1L, 6L, ROLE_ADMIN);

        String sql = segment(captureWrapper());
        assertThat(sql).doesNotContain("apply_user_id");
        assertThat(sql).doesNotContain("dept_id");
    }

    @Test
    @DisplayName("普通师生读取他人单据详情：抛业务异常")
    void getOrderDetail_asStudent_foreignOrder_isRejected() {
        ApplyOrder order = new ApplyOrder();
        order.setId(8L);
        order.setApplyUserId(1L);   // 单据属于 userId=1
        when(applyOrderMapper.selectById(8L)).thenReturn(order);

        assertThatThrownBy(() -> approvalService.getOrderDetail(8L, 7L, 1L, ROLE_STUDENT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权");
    }

    @Test
    @DisplayName("普通师生读取自己的单据详情：放行")
    void getOrderDetail_asStudent_ownOrder_isAllowed() {
        ApplyOrder order = new ApplyOrder();
        order.setId(9L);
        order.setApplyUserId(7L);
        when(applyOrderMapper.selectById(9L)).thenReturn(order);
        when(approvalRecordMapper.selectList(any())).thenReturn(List.of());

        assertThatCode(() -> approvalService.getOrderDetail(9L, 7L, 1L, ROLE_STUDENT))
                .doesNotThrowAnyException();
    }
}
