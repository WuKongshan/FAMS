package com.example.asset.integration;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.asset.common.exception.BusinessException;
import com.example.asset.modules.admin.entity.Department;
import com.example.asset.modules.admin.mapper.DepartmentMapper;
import com.example.asset.modules.approval.entity.ApplyOrder;
import com.example.asset.modules.approval.mapper.ApplyOrderMapper;
import com.example.asset.modules.approval.service.ApprovalService;
import com.example.asset.modules.asset.entity.Asset;
import com.example.asset.modules.asset.mapper.AssetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 审批模块数据范围（对象级授权）的真实 SQL 回归测试。
 *
 * <p>单测只能证明"Wrapper 上拼了 apply_user_id"；本类证明的是这条条件真的进了 SQL、
 * 真的在 MySQL 上把别人的行过滤掉了 —— 越权修复如果被后续重构改坏（比如误删那个 eq），
 * 单测可能仍然绿，这里会红。</p>
 */
@Transactional
@DisplayName("审批权限：真实 SQL 下的数据范围")
class ApprovalPermissionIntegrationTest extends AbstractIntegrationTest {

    private static final int ROLE_DEPT_ADMIN = 2;
    private static final int ROLE_STUDENT = 3;

    private static final long OWNER_ID = 900001L;
    private static final long OTHER_ID = 900002L;

    @Autowired
    private ApprovalService approvalService;
    @Autowired
    private ApplyOrderMapper applyOrderMapper;
    @Autowired
    private DepartmentMapper departmentMapper;
    @Autowired
    private AssetMapper assetMapper;

    private Long deptA;
    private Long deptB;
    private Long ownOrderId;
    private Long foreignOrderId;

    @BeforeEach
    void setUp() {
        deptA = insertDepartment("IT-学院A");
        deptB = insertDepartment("IT-学院B");

        Long ownAsset = insertAsset("IT-APP-A-" + UUID.randomUUID(), deptA);
        Long foreignAsset = insertAsset("IT-APP-B-" + UUID.randomUUID(), deptB);

        ownOrderId = insertOrder(OWNER_ID, "本人", deptA, ownAsset);
        foreignOrderId = insertOrder(OTHER_ID, "他人", deptB, foreignAsset);
    }

    @Test
    @DisplayName("普通师生查列表：前端传他人的 applyUserId 被忽略，结果只含本人单据")
    void pageOrders_asStudent_ignoresForeignApplyUserId() {
        // 攻击场景：登录用户 900001，前端把 applyUserId 改成 900002
        IPage<ApplyOrder> page = approvalService.pageOrders(
                1, 10, null, null, null, OTHER_ID, OWNER_ID, deptA, ROLE_STUDENT);

        assertThat(page.getRecords())
                .as("普通师生即使传了他人的 applyUserId，也只能看到自己的单")
                .hasSize(1);
        assertThat(page.getRecords())
                .allSatisfy(order -> assertThat(order.getApplyUserId()).isEqualTo(OWNER_ID));
        assertThat(page.getRecords())
                .extracting(ApplyOrder::getId)
                .containsExactly(ownOrderId)
                .doesNotContain(foreignOrderId);
    }

    @Test
    @DisplayName("学院管理员查列表：以登录态院系为准，前端传别院 deptId 无效")
    void pageOrders_asDeptAdmin_filtersByOwnDept() {
        // 攻击场景：A 院管理员，前端传 deptId=B 院
        IPage<ApplyOrder> page = approvalService.pageOrders(
                1, 10, null, null, deptB, null, 900010L, deptA, ROLE_DEPT_ADMIN);

        assertThat(page.getRecords())
                .as("学院管理员按登录态院系必须至少看到本院的单")
                .isNotEmpty();
        assertThat(page.getRecords())
                .allSatisfy(order -> assertThat(order.getDeptId()).isEqualTo(deptA));
        assertThat(page.getRecords())
                .extracting(ApplyOrder::getId)
                .contains(ownOrderId)
                .doesNotContain(foreignOrderId);
    }

    @Test
    @DisplayName("普通师生按 id 读他人单据详情：抛 BusinessException")
    void getOrderDetail_asStudent_foreignOrder_isRejected() {
        assertThatThrownBy(() -> approvalService.getOrderDetail(
                foreignOrderId, OWNER_ID, deptA, ROLE_STUDENT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权");
    }

    @Test
    @DisplayName("普通师生读本人单据详情：放行，且返回的单据就是自己的")
    void getOrderDetail_asStudent_ownOrder_isAllowed() {
        Map<String, Object> detail = approvalService.getOrderDetail(
                ownOrderId, OWNER_ID, deptA, ROLE_STUDENT);

        ApplyOrder order = (ApplyOrder) detail.get("order");
        assertThat(order.getId()).isEqualTo(ownOrderId);
        assertThat(order.getApplyUserId()).isEqualTo(OWNER_ID);
    }

    private Long insertDepartment(String name) {
        Department dept = new Department();
        dept.setDeptName(name);
        dept.setParentId(0L);
        dept.setSort(1);
        dept.setStatus(1);
        departmentMapper.insert(dept);
        return dept.getId();
    }

    private Long insertAsset(String code, Long deptId) {
        Asset asset = new Asset();
        asset.setAssetCode(code);
        asset.setAssetName("测试资产-" + code);
        asset.setDeptId(deptId);
        asset.setDeptName("IT");
        asset.setQuantity(1);
        asset.setStatus("闲置");
        assetMapper.insert(asset);
        return asset.getId();
    }

    private Long insertOrder(long applyUserId, String applyUserName, Long deptId, Long assetId) {
        ApplyOrder order = new ApplyOrder();
        order.setApplyType("BORROW");
        order.setAssetId(assetId);
        order.setAssetCode("A-" + applyUserId);
        order.setAssetName("测试资产");
        order.setApplyUserId(applyUserId);
        order.setApplyUserName(applyUserName);
        order.setDeptId(deptId);
        order.setDeptName("IT");
        order.setStatus("PENDING_FIRST");
        applyOrderMapper.insert(order);
        return order.getId();
    }
}
