package com.example.asset.modules.approval.flow;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 审批工作流（Flowable）配置项。
 *
 * <p>对应 application.yml：</p>
 * <pre>
 * approval:
 *   workflow:
 *     process-key: assetApplyApproval
 *     enabled-apply-types: BORROW
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "approval.workflow")
public class ApplyWorkflowProperties {

    /**
     * BPMN 流程定义 Key。
     * 必须与 processes/asset-apply-approval.bpmn20.xml 中 {@code <process id="...">} 完全一致。
     */
    private String processKey = "assetApplyApproval";

    /**
     * 已接入 Flowable 的申请类型集合。
     * <p>BORROW=领用，RETURN=归还。把 RETURN 加进来即可让「归还」也走工作流，
     * 无需改动任何 Java 代码与 BPMN 文件（两条流程流转路径完全一致）。</p>
     */
    private Set<String> enabledApplyTypes = new LinkedHashSet<>(Set.of("BORROW"));
}
