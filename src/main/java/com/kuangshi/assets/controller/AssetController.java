package com.kuangshi.assets.controller;

import com.kuangshi.assets.model.AssetDocument;
import com.kuangshi.assets.service.impl.AssetServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AssetController {

    private final AssetServiceImpl assetService;

    @GetMapping("/assets/{id}")
    @ResponseBody
    public ResponseEntity<?> getAssetById(
            @PathVariable String id,
            @RequestParam(required = false) String fields) {
        try {
            Map<String, Object> asset = assetService.getAssetById(id, fields);
            return ResponseEntity.ok(asset);
        } catch (Exception e) {
            log.error("查询素材失败", e);
            return ResponseEntity.internalServerError().body("查询失败");
        }
    }



    @GetMapping("/assets")
    public ResponseEntity<List<AssetDocument>> listAssets(@RequestParam Map<String, String> params) {
        try {
            List<AssetDocument> assets = assetService.searchAssets(params);
            return ResponseEntity.ok(assets);
        } catch (Exception e) {
            log.error("查询素材发生未预期错误, params: {}", params, e);
            return ResponseEntity.internalServerError().body(Lists.newArrayList());
        }
    }
}
