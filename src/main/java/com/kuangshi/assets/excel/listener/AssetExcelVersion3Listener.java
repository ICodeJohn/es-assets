package com.kuangshi.assets.excel.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelDataConvertException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuangshi.assets.dao.AssetDAO;
import com.kuangshi.assets.enums.ReviewStatusEnum;
import com.kuangshi.assets.excel.model.AssetExcelVersion3DTO;
import com.kuangshi.assets.model.AssetDocument;
import com.kuangshi.assets.util.AssetSizeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssetExcelVersion3Listener extends AnalysisEventListener<AssetExcelVersion3DTO> {

    private final AssetDAO assetDAO;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 批量处理阈值：每积累1000条就处理一次，防止内存溢出
     */
    private static final int BATCH_COUNT = 1000;

    /**
     * 缓存的数据列表
     */
    private final List<AssetDocument> dataList = new ArrayList<>();

    /**
     * 每解析一行数据会调用一次
     */
    @Override
    public void invoke(AssetExcelVersion3DTO data, AnalysisContext context) {
        // 数据校验（可选）
        if (data == null) {
            log.warn("读取到空数据，跳过");
            return;
        }

        dataList.add(convertToAssetDocument(data));

        // 达到批量阈值，立即处理一批数据，释放内存
        if (dataList.size() >= BATCH_COUNT) {
            saveData();
            dataList.clear();
        }
    }

    /**
     * 所有数据解析完成后调用
     */
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // 处理最后一批剩余数据
        if (!dataList.isEmpty()) {
            saveData();
            dataList.clear();
        }
        log.info("Excel文件解析完成，共处理数据");
    }

    /**
     * 读取表头
     */
    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        log.info("Excel表头信息: {}", headMap);
    }

    /**
     * 异常处理
     */
    @Override
    public void onException(Exception exception, AnalysisContext context) {
        log.error("解析Excel文件异常", exception);

        // 如果是数据转换异常，可以获取具体哪一行出错
        if (exception instanceof ExcelDataConvertException) {
            ExcelDataConvertException ex = (ExcelDataConvertException) exception;
            log.error("第{}行数据转换失败，列索引: {}",
                    ex.getRowIndex() + 1,
                    ex.getColumnIndex() + 1);
        }

        // 根据业务需求决定：抛出异常终止 或 继续解析
        throw new RuntimeException("Excel解析失败", exception);
    }

    /**
     * 业务处理方法：保存到数据库或进行其他操作
     */
    private void saveData() {
        log.info("批量处理 {} 条数据", dataList.size());
        assetDAO.batchSaveOrUpdateAssets(dataList);
    }

    /**
     * 将 V3 DTO 转换为 AssetDocument
     */
    public AssetDocument convertToAssetDocument(AssetExcelVersion3DTO dto) {
        if (dto == null) {
            return null;
        }

        AssetDocument doc = new AssetDocument();

        // 基础字段映射
        doc.setAssetId(dto.getId());  // ID
        doc.setTitle(dto.getAssetTitle());// 素材title
        doc.setUploader(dto.getUploader());// 上传者
        if(isAllEnglishLetters(dto.getReviewStatus())){
            doc.setStatus(dto.getReviewStatus().toLowerCase());
        }else {
            ReviewStatusEnum reviewStatusEnum = ReviewStatusEnum.fromDescription(dto.getReviewStatus());
            if(Objects.nonNull(reviewStatusEnum)){
                doc.setStatus(reviewStatusEnum.getCode());
            }
        }
        doc.setCity(dto.getCity()); // 城市

        // 处理时间戳（直接使用timestamp字段）
        if (dto.getTimestamp() != null && dto.getTimestamp() > 0) {
            doc.setTimestamp(dto.getTimestamp() * 1000);
        } else {
            doc.setTimestamp(System.currentTimeMillis());
        }

        // 处理文件大小（需要结合size和size_unit）
        if (dto.getSize() != null && dto.getSize() > 0) {
            Long sizeInBytes = AssetSizeUtil.convertSizeToBytes(dto.getSize(), dto.getSizeUnit());
            doc.setSize(sizeInBytes);
        } else {
            doc.setSize(0L);
        }

        // 处理标签（格式：['品牌'] 或 ['节日']）
        if (dto.getTags() != null && !dto.getTags().isEmpty()) {
            List<String> tagList = parseTags(dto.getTags());
            doc.setTags(tagList);
        } else {
            doc.setTags(new ArrayList<>());
        }

        // 构建扩展字段：存储 V3 特有的字段（platform, size_unit）
        Map<String, Object> extra = new HashMap<>();
        if (dto.getPlatform() != null && !dto.getPlatform().isEmpty()) {
            extra.put("platform", dto.getPlatform());
        }
        if (dto.getDurationSec() != null ) {
            extra.put("durationSec", dto.getDurationSec());
        }
        doc.setExtra(extra);

        // 设置文件类型（标识为 version3）
        doc.setFileType("version3");

        // 存储完整源数据（用于追溯）
        Map<String, Object> source = new HashMap<>();
        source.put("id", dto.getId());
        source.put("asset_title", dto.getAssetTitle());
        source.put("uploader", dto.getUploader());
        source.put("timestamp", dto.getTimestamp());
        source.put("size", dto.getSize());
        source.put("size_unit", dto.getSizeUnit());
        source.put("review_status", dto.getReviewStatus());
        source.put("tags", dto.getTags());
        source.put("city", dto.getCity());
        source.put("duration_sec", dto.getDurationSec());
        source.put("platform", dto.getPlatform());
        doc.setSource(source);

        return doc;
    }



    /**
     * 解析tags字段
     * 支持格式：['品牌']、['节日']、["品牌"]、["品牌","节日"]、['品牌','节日']
     * @param tagsStr tags字符串
     * @return 标签列表
     */
    private List<String> parseTags(String tagsStr) {
        if (tagsStr == null || tagsStr.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // 将Python风格的列表字符串转换为JSON格式
            String jsonStr = tagsStr.trim();
            // 替换单引号为双引号
            jsonStr = jsonStr.replace("'", "\"");
            // 解析为List
            return objectMapper.readValue(jsonStr, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("解析tags字段失败: {}, 原始值: {}", e.getMessage(), tagsStr);

            // 降级处理：尝试按逗号分隔
            String cleaned = tagsStr.replace("[", "").replace("]", "")
                    .replace("'", "").replace("\"", "");
            if (cleaned.contains(",")) {
                return Arrays.asList(cleaned.split(","));
            } else if (!cleaned.isEmpty()) {
                return Collections.singletonList(cleaned);
            }
            return new ArrayList<>();
        }
    }

    /**
     * 判断字符串是否全部为英文字母
     * @param str 字符串
     * @return true-全部是英文字母，false-包含非英文字母
     */
    public static boolean isAllEnglishLetters(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        // 只允许大小写英文字母
        return Pattern.matches("^[a-zA-Z]+$", str);
    }
}