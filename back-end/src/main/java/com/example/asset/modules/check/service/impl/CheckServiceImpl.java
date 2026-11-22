package com.example.asset.modules.check.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.asset.common.exception.BusinessException;
import com.example.asset.common.llm.LLMService;
import com.example.asset.common.utils.SecurityUtils;
import com.example.asset.modules.admin.entity.User;
import com.example.asset.modules.admin.mapper.DepartmentMapper;
import com.example.asset.modules.admin.mapper.UserMapper;
import com.example.asset.modules.asset.entity.Asset;
import com.example.asset.modules.asset.mapper.AssetMapper;
import com.example.asset.modules.check.dto.CheckDiffResult;
import com.example.asset.modules.check.dto.LLMAnalysisItem;
import com.example.asset.modules.check.entity.CheckRecord;
import com.example.asset.modules.check.entity.CheckTask;
import com.example.asset.modules.check.mapper.CheckRecordMapper;
import com.example.asset.modules.check.mapper.CheckTaskMapper;
import com.example.asset.modules.check.prompt.CheckAnalysisPrompt;
import com.example.asset.modules.check.service.CheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final LLMService llmService;
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
            checkRecordMapper.insert(record);
        }

        // 首次录入盘点数据时，将任务状态从 PENDING 改为 IN_PROGRESS
        CheckTask task = checkTaskMapper.selectById(record.getTaskId());
        if (task != null && "PENDING".equals(task.getStatus())) {
            task.setStatus("IN_PROGRESS");
            checkTaskMapper.updateById(task);
        }
    }

    @Override
    public List<CheckRecord> getRecordsByTaskId(Long taskId) {
        LambdaQueryWrapper<CheckRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CheckRecord::getTaskId, taskId);
        return checkRecordMapper.selectList(wrapper);
    }

    /**
     * LLM 智能分析盘点差异
     * 调用 DeepSeek API 对差异记录进行专业分析并给出处理建议
     */
    @Override
    public List<CheckDiffResult> analyzeDiff(Long taskId) {
        List<CheckRecord> records = getRecordsByTaskId(taskId);
        if (CollUtil.isEmpty(records)) {
            return Collections.emptyList();
        }

        // 筛选有差异的记录，相符的直接跳过 LLM 调用
        List<CheckRecord> diffRecords = records.stream()
                .filter(r -> !"MATCH".equals(r.getResult()))
                .collect(Collectors.toList());

        // 用于存放 LLM 分析结果的映射：recordId -> LLMAnalysisItem
        Map<Long, LLMAnalysisItem> llmMap = Collections.emptyMap();

        if (CollUtil.isNotEmpty(diffRecords)) {
            CheckTask task = checkTaskMapper.selectById(taskId);
            String prompt = CheckAnalysisPrompt.build(task, diffRecords);

            try {
                // 调用 DeepSeek API 进行批量分析
                List<LLMAnalysisItem> llmResults = llmService.chatForList(
                        CheckAnalysisPrompt.SYSTEM_PROMPT,
                        prompt,
                        LLMAnalysisItem.class);

                if (CollUtil.isNotEmpty(llmResults)) {
                    llmMap = llmResults.stream()
                            .filter(item -> item.getRecordId() != null)
                            .collect(Collectors.toMap(LLMAnalysisItem::getRecordId, item -> item));
                }
                log.info("LLM 差异分析完成, 任务ID: {}, 差异条数: {}, 分析结果条数: {}",
                        taskId, diffRecords.size(), llmMap.size());

            } catch (Exception e) {
                log.error("LLM 差异分析调用失败, 任务ID: {}, 回退到模板分析", taskId, e);
                // 降级：使用模板分析（不回抛异常，保证功能可用）
                return fallbackAnalyze(records);
            }
        }

        // 将 LLM 结果与全部记录合并
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

    @Override
    @Transactional
    public void confirmDiff(Long taskId, List<Long> recordIds) {
        for (Long recordId : recordIds) {
            CheckRecord record = checkRecordMapper.selectById(recordId);
            if (record == null) continue;

            Asset asset = assetMapper.selectById(record.getAssetId());
            if (asset == null) continue;

            // 根据盘点结果更新资产状态（跳过特殊状态的资产，避免覆盖工作流驱动的状态）
            if ("SHORTAGE".equals(record.getResult())) {
                int rows = assetMapper.update(null, new LambdaUpdateWrapper<Asset>()
                                .eq(Asset::getId, asset.getId())
                                .notIn(Asset::getStatus, "维修中", "已报废")
                                .set(Asset::getStatus, "闲置"));
                if (rows == 0) {
                    log.warn("盘点差异确认跳过: 资产[{}]状态不允许自动变更", asset.getAssetCode());
                }
            } else if ("SURPLUS".equals(record.getResult())) {
                int rows = assetMapper.update(null, new LambdaUpdateWrapper<Asset>()
                        .eq(Asset::getId, asset.getId())
                        .ne(Asset::getStatus, "已报废")
                        .set(Asset::getStatus, "在用"));
                if (rows == 0) {
                    log.warn("盘点差异确认跳过: 资产[{}]状态不允许自动变更", asset.getAssetCode());
                }
            }
        }

        // 更新任务状态为已完成
        CheckTask task = checkTaskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus("COMPLETED");
            checkTaskMapper.updateById(task);
        }
    }
}
