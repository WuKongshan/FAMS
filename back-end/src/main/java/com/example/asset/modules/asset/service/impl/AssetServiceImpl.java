package com.example.asset.modules.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.asset.common.exception.BusinessException;
import com.example.asset.modules.admin.mapper.DepartmentMapper;
import com.example.asset.modules.asset.entity.Asset;
import com.example.asset.modules.asset.mapper.AssetCategoryMapper;
import com.example.asset.modules.asset.mapper.AssetMapper;
import com.example.asset.modules.asset.service.AssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetServiceImpl extends ServiceImpl<AssetMapper, Asset> implements AssetService {

    private final DepartmentMapper departmentMapper;
    private final AssetCategoryMapper assetCategoryMapper;

    @Override
    public IPage<Asset> pageAssets(int current, int size, String keyword, Long categoryId, String status, Long deptId) {
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Asset::getAssetCode, keyword)
                    .or().like(Asset::getAssetName, keyword)
                    .or().like(Asset::getUserName, keyword));
        }
        if (categoryId != null) {
            wrapper.eq(Asset::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Asset::getStatus, status);
        }
        if (deptId != null) {
            wrapper.eq(Asset::getDeptId, deptId);
        }
        wrapper.orderByDesc(Asset::getCreateTime);
        return page(new Page<>(current, size), wrapper);
    }

    @Override
    public void addAsset(Asset asset) {
        // 检查资产编号唯一性（仅查未删除记录）
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Asset::getAssetCode, asset.getAssetCode());
        if (count(wrapper) > 0) {
            throw new BusinessException("资产编号已存在: " + asset.getAssetCode());
        }
        // 根据ID自动填充部门名称和分类名称
        fillNamesFromIds(asset);
        try {
            save(asset);
        } catch (DataIntegrityViolationException e) {
            // 软删除记录导致 uk_asset_code 冲突
            log.error("资产保存违反唯一约束, assetCode={}", asset.getAssetCode());
            throw new BusinessException("资产编号已存在（含已删除记录）: " + asset.getAssetCode());
        }
    }

    @Override
    public void updateAsset(Asset asset) {
        Asset exist = getById(asset.getId());
        if (exist == null) {
            throw new BusinessException("资产不存在");
        }
        // 检查资产编号唯一性（排除自身）
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Asset::getAssetCode, asset.getAssetCode())
               .ne(Asset::getId, asset.getId());
        if (count(wrapper) > 0) {
            throw new BusinessException("资产编号已存在: " + asset.getAssetCode());
        }
        // 根据ID自动填充部门名称和分类名称
        fillNamesFromIds(asset);
        try {
            updateById(asset);
        } catch (DataIntegrityViolationException e) {
            log.error("资产更新违反唯一约束, assetCode={}", asset.getAssetCode());
            throw new BusinessException("资产编号已存在（含已删除记录）: " + asset.getAssetCode());
        }
    }

    /**
     * 根据 deptId 和 categoryId 自动填充对应的名称字段
     */
    private void fillNamesFromIds(Asset asset) {
        if (asset.getDeptId() != null) {
            var dept = departmentMapper.selectById(asset.getDeptId());
            if (dept != null && org.springframework.util.StringUtils.hasText(dept.getDeptName())) {
                asset.setDeptName(dept.getDeptName());
            }
        }
        if (asset.getCategoryId() != null) {
            var category = assetCategoryMapper.selectById(asset.getCategoryId());
            if (category != null && org.springframework.util.StringUtils.hasText(category.getCategoryName())) {
                asset.setCategoryName(category.getCategoryName());
            }
        }
    }

    @Override
    public void deleteAsset(Long id) {
        removeById(id);
    }

    @Override
    public List<Asset> exportAssets(String keyword, Long categoryId, String status, Long deptId) {
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Asset::getAssetCode, keyword)
                    .or().like(Asset::getAssetName, keyword));
        }
        if (categoryId != null) {
            wrapper.eq(Asset::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Asset::getStatus, status);
        }
        if (deptId != null) {
            wrapper.eq(Asset::getDeptId, deptId);
        }
        return list(wrapper);
    }
}
