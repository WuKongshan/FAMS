package com.example.asset.modules.check.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.asset.common.exception.BusinessException;
import com.example.asset.common.utils.SecurityUtils;
import com.example.asset.modules.admin.entity.User;
import com.example.asset.modules.admin.mapper.DepartmentMapper;
import com.example.asset.modules.admin.mapper.UserMapper;
import com.example.asset.modules.asset.entity.Asset;
import com.example.asset.modules.asset.mapper.AssetMapper;
import com.example.asset.modules.check.client.CheckAnalysisClient;
import com.example.asset.modules.check.dto.CheckDiffResult;
import com.example.asset.modules.check.dto.LLMAnalysisItem;
import com.example.asset.modules.check.entity.CheckRecord;
import com.example.asset.modules.check.entity.CheckTask;
import com.example.asset.modules.check.mapper.CheckRecordMapper;
import com.example.asset.modules.check.mapper.CheckTaskMapper;
import com.example.asset.modules.check.service.CheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckServiceImpl implements CheckService {

    private final CheckTaskMapper checkTaskMapper;
    private final CheckRecordMapper checkRecordMapper;
    private final AssetMapper assetMapper;
    private final CheckAnalysisClient analysisClient;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;

    @Override
    @Transactional
    public void createTask(CheckTask task) {
        task.setStatus("PENDING");
        task.setPublisherId(SecurityUtils.getCurrentUserId());
        task.setTaskNo(generateTaskNo());

        // 根据 ID 解析部门名称（deptIds 格式："1,2"）
        if (StrUtil.isNotBlank(task.getDeptIds())) {
            task.setDeptNames(resolveDeptNames(task.getDeptIds()));
        }
        // 根据 ID 解析参与人员姓名（participantIds 格式："2,3"）
        if (StrUtil.isNotBlank(task.getParticipantIds())) {
            task.setParticipantNames(resolveUserNames(task.getParticipantIds()));
        }

        checkTaskMapper.insert(task);
    }

    /**
     * 生成任务编号：PD-yyyyMMdd-序号（如 PD-20260109-001）
     */
    private String generateTaskNo() {
        String prefix = "PD-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        LambdaQueryWrapper<CheckTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(CheckTask::getTaskNo, prefix)
               .orderByDesc(CheckTask::getTaskNo)
               .last("LIMIT 1");
        CheckTask last = checkTaskMapper.selectOne(wrapper);
        int seq = 1;
        if (last != null && last.getTaskNo() != null) {
            String lastSeq = last.getTaskNo().substring(last.getTaskNo().lastIndexOf('-') + 1);
            try {
                seq = Integer.parseInt(lastSeq) + 1;
            } catch (NumberFormatException ignored) {
                // keep seq = 1
            }
        }
        return prefix + String.format("%03d", seq);
    }

    /**
     * 根据逗号分隔的部门ID解析部门名称
     */
    private String resolveDeptNames(String deptIds) {
        return Arrays.stream(deptIds.split(","))
                .map(String::trim)
                .map(id -> {
                    try {
                        var dept = departmentMapper.selectById(Long.valueOf(id));
                        return dept != null ? dept.getDeptName() : "未知部门#" + id;
                    } catch (NumberFormatException e) {
                        return "未知部门#" + id;
                    }
                })
                .collect(Collectors.joining(","));
    }

    /**
     * 根据逗号分隔的用户ID解析用户真实姓名
     */
    private String resolveUserNames(String userIds) {
        return Arrays.stream(userIds.split(","))
                .map(String::trim)
                .map(id -> {
                    try {
                        User user = userMapper.selectById(Long.valueOf(id));
                        return user != null ? user.getRealName() : "未知用户#" + id;
                    } catch (NumberFormatException e) {
                        return "未知用户#" + id;
                    }
                })
                .collect(Collectors.joining(","));
    }

    @Override
    public IPage<CheckTask> pageTasks(int current, int size, String status) {
        LambdaQueryWrapper<CheckTask> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(CheckTask::getStatus, status);
        }
        wrapper.orderByDesc(CheckTask::getCreateTime);
        return checkTaskMapper.selectPage(new Page<>(current, size), wrapper);
    }

    @Override
    @Transactional
    public void saveCheckRecord(CheckRecord record) {
        // 如果填写了资产编号，自动查询资产信息填充 assetId、assetName、bookQuantity
        if (StrUtil.isNotBlank(record.getAssetCode())) {
            Asset asset = assetMapper.selectOne(
                    new LambdaQueryWrapper<Asset>().eq(Asset::getAssetCode, record.getAssetCode()));
            if (asset != null) {
                record.setAssetId(asset.getId());
                record.setAssetName(asset.getAssetName());
                record.setBookQuantity(asset.getQuantity() != null ? asset.getQuantity() : 1);
            }
        }

        record.setDiffQuantity(record.getActualQuantity() - record.getBookQuantity());
        if (record.getDiffQuantity() == 0) {
            record.setResult("MATCH");
        } else if (record.getDiffQuantity() > 0) {
            record.setResult("SURPLUS");
        } else {
            record.setResult("SHORTAGE");
        }

        // 如果记录已有 ID，执行更新；否则执行新增（支持协同编辑时的增量保存）
        if (record.getId() != null) {
            checkRecordMapper.updateById(record);
        } else {
            try {
                checkRecordMapper.insert(record);
            } catch (DuplicateKeyException e) {
                // 唯一索引 uk_check_record_task_asset 拦下了并发重复插入：
                // 说明另一个请求已经为本任务下的同一资产建好了记录，本次填报应合并进那一行，
                // 而不是让接口 500。这里只捕获 DuplicateKeyException（唯一冲突这一种确定语义），
                // 其他数据访问异常照常上抛，避免掩盖真实故障。
                mergeIntoExistingRecord(record);
            }
        }

        // 首次录入盘点数据时，将任务状态从 PENDING 改为 IN_PROGRESS
        CheckTask task = checkTaskMapper.selectById(record.getTaskId());
        if (task != null && "PENDING".equals(task.getStatus())) {
            task.setStatus("IN_PROGRESS");
            checkTaskMapper.updateById(task);
        }
    }

    /**
     * 并发重复插入的兜底合并：把本次填报内容写到已存在的那条记录上。
     *
     * <p>触发场景：两个盘点人（或一次误双击）同时对同一 (task_id, asset_id) 走"查无记录 → insert"，
     * 后到者在唯一索引上抛 DuplicateKeyException。此时以库中已存在的行 id 为准执行 updateById：
     * 复用那一行已有的主键（前端手上的 id 不会悬空），本次填报成为最终值（后写覆盖），
     * 与前端"协同编辑、增量保存"的既有语义保持一致。
     *
     * <p><b>创建审计字段不会被自动保护，必须显式清空。</b>
     * MetaObjectHandlerConfig.insertFill 用的是 strictInsertFill，它在那次「先填充、后失败」的 insert
     * 尝试时就已经把 createBy/createTime 写到了实体上。也就是说进入本方法时这两个字段已非 null，
     * MyBatis-Plus 的 NOT_NULL 策略会把它们照常拼进 updateById 的 SET，
     * 先到者那一行的创建人/创建时间就被后来者与当前时间覆盖了（不是策略失效，是字段已被污染）。
     * 因此"保留先到者的创建审计"是靠下面的显式清空换来的，不是框架默认行为。
     * updateBy/updateTime 相反不需要干预：它们的语义就是"最后写入者"，
     * 合并时被本次请求刷新正是期望结果，且 insertFill 写入的本来就是本次请求的用户与时刻。
     *
     * <p>覆盖范围：实盘数量 / 差异数量 / 盘点结果 / 备注 / 盘点人 / 资产快照。
     * 其中 diffQuantity、result 已在调用插入之前按最新台账算好，直接复用 record 上的值，
     * 不在本方法内重算，避免两条写入路径的差异判定逻辑分叉。
     *
     * @param record 本次待保存的盘点记录（insert 失败，id 仍为 null）
     */
    private void mergeIntoExistingRecord(CheckRecord record) {
        CheckRecord existing = checkRecordMapper.selectOne(new LambdaQueryWrapper<CheckRecord>()
                .eq(CheckRecord::getTaskId, record.getTaskId())
                .eq(CheckRecord::getAssetId, record.getAssetId())
                .last("LIMIT 1"));
        if (existing == null) {
            // 理论上不可达：刚刚才撞了唯一索引，行必然存在。
            // 但如果极端情况下窗口内先到者又被物理删除，这里绝不能静默吞掉本次填报，
            // 直接上抛让事务回滚，由调用方看到明确错误。
            // 已知边界：若那一行是被「逻辑删除」而不是物理删除，MyBatis-Plus 的 @TableLogic 会给上面的
            // selectOne 自动补 deleted = 0，从而查不到，同样落到这里抛错（接口 500）。
            // 目前 check_record 没有任何删除入口，故该路径不可达；一旦以后新增"撤销盘点记录"，
            // 需要把唯一索引 uk_check_record_task_asset 放宽成 (task_id, asset_id, deleted)
            // （代价是同一条记录只能被逻辑删除一次，符合业务预期），或让这里的查询绕过逻辑删除。
            // 这段边界说明之所以写在 Java 里而不是 V10 迁移注释里：迁移文件一旦被 apply 过就不可再改，
            // 连注释改一个字节都会让 Flyway 的 checksum 校验失败、应用启动即挂。
            throw new IllegalStateException(
                    "盘点记录唯一冲突后未查到已存在记录, taskId=" + record.getTaskId()
                            + ", assetId=" + record.getAssetId());
        }
        // 清空被失败的 insert 污染过的创建审计字段，让 NOT_NULL 策略把它们排除在 SET 之外。
        // 原因见方法注释：insertFill 在 insert 组装 SQL 之前就填好了它们，非 null 即会被拼进 UPDATE。
        // updateBy/updateTime 刻意不动 —— 它们本就该被本次合并请求刷新。
        record.setCreateBy(null);
        record.setCreateTime(null);
        record.setId(existing.getId());
        checkRecordMapper.updateById(record);
        log.info("盘点记录并发重复插入已合并, taskId={}, assetId={}, recordId={}",
                record.getTaskId(), record.getAssetId(), existing.getId());
    }

    @Override
    public List<CheckRecord> getRecordsByTaskId(Long taskId) {
        LambdaQueryWrapper<CheckRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CheckRecord::getTaskId, taskId);
        return checkRecordMapper.selectList(wrapper);
    }

    /**
     * LLM 智能分析盘点差异
     * 调用 Python 分析服务对差异记录进行专业分析并给出处理建议；服务不可用时回退模板
     */
    @Override
    public List<CheckDiffResult> analyzeDiff(Long taskId) {
        List<CheckRecord> records = getRecordsByTaskId(taskId);
        if (CollUtil.isEmpty(records)) {
            return Collections.emptyList();
        }

        // 筛选有差异的记录，相符的直接跳过远程调用
        List<CheckRecord> diffRecords = records.stream()
                .filter(r -> !"MATCH".equals(r.getResult()))
                .collect(Collectors.toList());

        // 用于存放分析结果的映射：recordId -> LLMAnalysisItem
        Map<Long, LLMAnalysisItem> llmMap = Collections.emptyMap();

        if (CollUtil.isNotEmpty(diffRecords)) {
            CheckTask task = checkTaskMapper.selectById(taskId);

            try {
                // 分片、重试、prompt 都在 Python 侧，这里只发一次请求
                var outcome = analysisClient.analyze(task, diffRecords);
                llmMap = outcome.items();

                log.info("差异分析完成, 任务ID: {}, 差异条数: {}, 引擎: {}",
                        taskId, diffRecords.size(), outcome.engine());

            } catch (Exception e) {
                log.error("分析服务调用失败, 任务ID: {}, 回退到模板分析", taskId, e);
                // 降级：使用模板分析（不回抛异常，保证功能可用）
                return fallbackAnalyze(records);
            }
        }

        // 将分析结果与全部记录合并
        return mergeResults(records, llmMap);
    }

    /**
     * 将 LLM 分析结果与盘点记录合并为完整的 CheckDiffResult 列表
     */
    private List<CheckDiffResult> mergeResults(List<CheckRecord> records, Map<Long, LLMAnalysisItem> llmMap) {
        return records.stream().map(r -> {
            CheckDiffResult result = new CheckDiffResult();
            result.setRecordId(r.getId());
            result.setAssetCode(r.getAssetCode());
            result.setAssetName(r.getAssetName());
            result.setBookQuantity(r.getBookQuantity());
            result.setActualQuantity(r.getActualQuantity());
            result.setDiffQuantity(r.getDiffQuantity());

            LLMAnalysisItem ai = llmMap.get(r.getId());
            if (ai != null) {
                result.setAnalysis(ai.getAnalysis());
                result.setSuggestion(ai.getSuggestion());
            } else if ("MATCH".equals(r.getResult())) {
                result.setAnalysis("资产【" + r.getAssetName() + "】账面与实物相符。");
                result.setSuggestion("无需处理。");
            } else {
                // LLM 未返回该记录的分析结果
                result.setAnalysis("分析失败，请重试");
                result.setSuggestion("请联系管理员或重新执行分析");
            }
            return result;
        }).collect(Collectors.toList());
    }

    /**
     * 降级方案：当 LLM 不可用时，使用模板化分析
     */
    private List<CheckDiffResult> fallbackAnalyze(List<CheckRecord> records) {
        List<CheckDiffResult> results = new ArrayList<>();
        for (CheckRecord record : records) {
            CheckDiffResult diff = new CheckDiffResult();
            diff.setRecordId(record.getId());
            diff.setAssetCode(record.getAssetCode());
            diff.setAssetName(record.getAssetName());
            diff.setBookQuantity(record.getBookQuantity());
            diff.setActualQuantity(record.getActualQuantity());
            diff.setDiffQuantity(record.getDiffQuantity());

            if (record.getDiffQuantity() > 0) {
                diff.setAnalysis("系统检测到资产【" + record.getAssetName() + "】盘盈 " + record.getDiffQuantity() + " 件，建议核实是否存在未入账资产。");
                diff.setSuggestion("请确认是否有新增资产未及时录入系统，如确认则更新台账增加该资产数量。");
            } else if (record.getDiffQuantity() < 0) {
                diff.setAnalysis("系统检测到资产【" + record.getAssetName() + "】盘亏 " + Math.abs(record.getDiffQuantity()) + " 件，建议核实是否存在资产丢失或误登记。");
                diff.setSuggestion("请核实资产去向，如确认丢失需按相关规定处理。");
            } else {
                diff.setAnalysis("资产【" + record.getAssetName() + "】账面与实物相符。");
                diff.setSuggestion("无需处理。");
            }
            results.add(diff);
        }
        return results;
    }

    /**
     * 确认盘点结果。
     *
     * <p><b>本方法不改动资产台账。</b>历史实现在这里把盘亏资产置为「闲置」、盘盈置为「在用」，
     * 这两个映射在业务上是错的：
     * <ul>
     *   <li>盘亏 = 账上有、实物缺失；而「闲置」在系统里表示"在库、可借出"。
     *       置为闲置会让一件已丢失的资产重新进入可领用池
     *       （{@code submitApply} 对领用的校验只是 {@code "闲置".equals(status)}）。</li>
     *   <li>盘盈 = 实物有、账上无。改某条既有资产的状态解决不了"账上缺记录"，
     *       真正该做的是新增资产登记。</li>
     *   <li>直接 set 状态还会与在途审批打架：归还终审以 {@code eq(status,"在用")} 做条件更新，
     *       若盘点先把状态改成闲置，终审会因匹配不到而抛"状态不一致"异常，单据卡死。</li>
     * </ul>
     *
     * <p>因此确认动作现在只做两件事：统计差异、把任务置为已完成。差异的处置走既有业务流程 ——
     * 盘亏到「报废管理」提交报废单，盘盈到「资产台账」新增登记，
     * 资产状态仍由审批流统一变更，保证只有一条写路径。
     *
     * @return 本次确认的统计（confirmed / shortage / surplus / matched），供前端提示处置方向
     */
    @Override
    @Transactional
    public Map<String, Object> confirmDiff(Long taskId, List<Long> recordIds) {
        int confirmed = 0;
        int shortage = 0;
        int surplus = 0;
        int matched = 0;

        for (Long recordId : recordIds) {
            if (recordId == null) {
                continue;
            }
            CheckRecord record = checkRecordMapper.selectById(recordId);
            if (record == null) {
                continue;
            }
            // 只统计属于本任务的记录，避免调用方误传 id 把别的任务的记录算进来
            if (!Objects.equals(taskId, record.getTaskId())) {
                log.warn("盘点确认跳过跨任务记录: taskId={}, recordId={}, recordTaskId={}",
                        taskId, recordId, record.getTaskId());
                continue;
            }
            confirmed++;
            if ("SHORTAGE".equals(record.getResult())) {
                shortage++;
            } else if ("SURPLUS".equals(record.getResult())) {
                surplus++;
            } else {
                matched++;
            }
        }

        // 更新任务状态为已完成
        CheckTask task = checkTaskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus("COMPLETED");
            checkTaskMapper.updateById(task);
        }

        log.info("盘点确认完成: taskId={}, 记录 {} 条（盘亏 {} / 盘盈 {} / 相符 {}），资产台账未变更",
                taskId, confirmed, shortage, surplus, matched);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("confirmed", confirmed);
        result.put("shortage", shortage);
        result.put("surplus", surplus);
        result.put("matched", matched);
        return result;
    }
}
