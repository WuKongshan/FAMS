package com.example.asset.modules.asset.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.asset.modules.asset.entity.Asset;

import java.util.List;

public interface AssetService extends IService<Asset> {

    IPage<Asset> pageAssets(int current, int size, String keyword, Long categoryId, String status, Long deptId);

    void addAsset(Asset asset);

    void updateAsset(Asset asset);

    void deleteAsset(Long id);

    List<Asset> exportAssets(String keyword, Long categoryId, String status, Long deptId);
}
