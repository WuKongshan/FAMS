package com.example.asset.modules.asset.controller;

import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.example.asset.common.annotation.OperationLog;
import com.example.asset.common.result.R;
import com.example.asset.modules.asset.entity.Asset;
import com.example.asset.modules.asset.service.AssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "资产台账管理")
@RestController
@RequestMapping("/api/asset")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @Operation(summary = "分页查询资产")
    @GetMapping("/page")
    public R<?> page(@RequestParam(defaultValue = "1") int current,
                     @RequestParam(defaultValue = "10") int size,
                     @RequestParam(required = false) String keyword,
                     @RequestParam(required = false) Long categoryId,
                     @RequestParam(required = false) String status,
                     @RequestParam(required = false) Long deptId) {
        return R.ok(assetService.pageAssets(current, size, keyword, categoryId, status, deptId));
    }

    @Operation(summary = "新增资产")
    @OperationLog(module = "资产管理", operation = "新增", description = "录入资产")
    @PostMapping
    public R<?> add(@RequestBody Asset asset) {
        assetService.addAsset(asset);
        return R.ok("资产录入成功");
    }

    @Operation(summary = "修改资产")
    @OperationLog(module = "资产管理", operation = "修改", description = "更新资产信息")
    @PutMapping
    public R<?> update(@RequestBody Asset asset) {
        assetService.updateAsset(asset);
        return R.ok("资产更新成功");
    }

    @Operation(summary = "删除资产")
    @OperationLog(module = "资产管理", operation = "删除", description = "删除资产")
    @DeleteMapping("/{id}")
    public R<?> delete(@PathVariable Long id) {
        assetService.deleteAsset(id);
        return R.ok("资产删除成功");
    }

    @Operation(summary = "导出资产台账（Excel）")
    @OperationLog(module = "资产管理", operation = "导出", description = "导出资产台账")
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) Long deptId,
                       HttpServletResponse response) throws Exception {
        List<Asset> assets = assetService.exportAssets(keyword, categoryId, status, deptId);

        // 使用 Hutool ExcelWriter 生成 .xlsx 文件
        ExcelWriter writer = ExcelUtil.getWriter(true);
        writer.addHeaderAlias("assetCode", "资产编号");
        writer.addHeaderAlias("assetName", "资产名称");
        writer.addHeaderAlias("quantity", "数量");
        writer.addHeaderAlias("categoryName", "资产分类");
        writer.addHeaderAlias("model", "规格型号");
        writer.addHeaderAlias("originalValue", "原值(元)");
        writer.addHeaderAlias("purchaseDate", "购置时间");
        writer.addHeaderAlias("location", "存放地点");
        writer.addHeaderAlias("deptName", "归属部门");
        writer.addHeaderAlias("userName", "使用人");
        writer.addHeaderAlias("status", "状态");

        // 只写出指定的列
        writer.setOnlyAlias(true);
        writer.write(assets, true);

        // 设置响应头
        String fileName = URLEncoder.encode("资产台账导出.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");

        writer.flush(response.getOutputStream());
        writer.close();
    }
}
