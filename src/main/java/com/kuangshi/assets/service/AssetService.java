package com.kuangshi.assets.service;

import com.kuangshi.assets.model.AssetDocument;
import com.kuangshi.assets.model.TagStatsDTO;
import com.kuangshi.assets.model.UploaderAvgSizeDTO;
import com.kuangshi.assets.model.UploaderQualityDTO;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface AssetService {

    List<UploaderAvgSizeDTO> avgSizeByUploader();

    List<TagStatsDTO> getTop5Tags();

    List<UploaderQualityDTO> analyzeUploaderQuality() throws IOException;

    List<AssetDocument> searchAssets(Map<String, String> params) throws IOException;

    Map<String, Object> getAssetById(String id, String fields) throws IOException;
}
