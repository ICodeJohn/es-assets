package com.kuangshi.assets.excel.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelDataConvertException;
import com.kuangshi.assets.dao.AssetDAO;
import com.kuangshi.assets.excel.model.AssetExcelVersion2DTO;
import com.kuangshi.assets.model.AssetDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssetExcelVersion2Listener extends AnalysisEventListener<AssetExcelVersion2DTO> {

    private final AssetDAO assetDAO;


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
    public void invoke(AssetExcelVersion2DTO data, AnalysisContext context) {
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
        log.info("CSV文件解析完成，共处理数据");
    }

    /**
     * 读取表头
     */
    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        log.info("CSV表头信息: {}", headMap);
    }

    /**
     * 异常处理
     */
    @Override
    public void onException(Exception exception, AnalysisContext context) {
        log.error("解析CSV文件异常", exception);

        // 如果是数据转换异常，可以获取具体哪一行出错
        if (exception instanceof ExcelDataConvertException) {
            ExcelDataConvertException ex = (ExcelDataConvertException) exception;
            log.error("第{}行数据转换失败，列索引: {}",
                    ex.getRowIndex() + 1,
                    ex.getColumnIndex() + 1);
        }

        // 根据业务需求决定：抛出异常终止 或 继续解析
        throw new RuntimeException("CSV解析失败", exception);
    }

    /**
     * 业务处理方法：保存到数据库或进行其他操作
     */
    private void saveData() {
        log.info("批量处理 {} 条数据", dataList.size());
        assetDAO.batchSaveOrUpdateAssets(dataList);
    }

    /**
     * 将 V2 DTO 转换为 AssetDocument
     */
    public AssetDocument convertToAssetDocument(AssetExcelVersion2DTO dto) {
        if (dto == null) {
            return null;
        }

        AssetDocument doc = new AssetDocument();

        // 基础字段映射
        doc.setAssetId(dto.getAssetId());
        doc.setTitle(dto.getTitle());
        doc.setUploader(dto.getUploader());
        doc.setStatus(dto.getStatus().toLowerCase());  // status 映射到 reviewStatus
        doc.setCity(dto.getCity());

        // 处理时间戳（上传日期字符串转Long时间戳）
        // 格式：2024/5/1 19:00
        if (dto.getUploadedAt() != null && !dto.getUploadedAt().isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/M/d HH:mm");
                Date date = sdf.parse(dto.getUploadedAt());
                doc.setTimestamp(date.getTime());
            } catch (ParseException e) {
                log.warn("时间解析失败: {}", dto.getUploadedAt());
                doc.setTimestamp(System.currentTimeMillis());
            }
        } else {
            doc.setTimestamp(System.currentTimeMillis());
        }

        // 处理文件大小（已经是字节数，直接转换）
        if (dto.getFileSizeBytes() != null && !dto.getFileSizeBytes().isEmpty()) {
            try {
                doc.setSize(Long.parseLong(dto.getFileSizeBytes()));
            } catch (NumberFormatException e) {
                log.warn("文件大小解析失败: {}", dto.getFileSizeBytes());
                doc.setSize(0L);
            }
        } else {
            doc.setSize(0L);
        }

        // 处理标签（按分隔符拆分，常见分隔符：逗号、空格）
        if (dto.getTags() != null && !dto.getTags().isEmpty()) {
            List<String> tagList = Arrays.stream(dto.getTags().split("[，,、\\s]+"))
                    .map(String::trim)
                    .filter(tag -> !tag.isEmpty())
                    .collect(Collectors.toList());
            doc.setTags(tagList);
        } else {
            doc.setTags(new ArrayList<>());
        }

        // 构建扩展字段：存储 V2 特有的字段（spend, platform, resolution）
        Map<String, Object> extra = new HashMap<>();
        if (dto.getSpend() != null && !dto.getSpend().isEmpty() && !"N/A".equals(dto.getSpend())) {
            extra.put("spend", dto.getSpend());
        }
        if (dto.getPlatform() != null && !dto.getPlatform().isEmpty()) {
            extra.put("platform", dto.getPlatform());
        }
        if (dto.getResolution() != null && !dto.getResolution().isEmpty()) {
            extra.put("resolution", dto.getResolution());
        }
        doc.setExtra(extra);

        // 设置文件类型（标识为 version2）
        doc.setFileType("version2");

        // 存储完整源数据（用于追溯）
        Map<String, Object> source = new HashMap<>();
        source.put("asset_id", dto.getAssetId());
        source.put("title", dto.getTitle());
        source.put("uploader", dto.getUploader());
        source.put("uploaded_at", dto.getUploadedAt());
        source.put("file_size_bytes", dto.getFileSizeBytes());
        source.put("status", dto.getStatus());
        source.put("tags", dto.getTags());
        source.put("city", dto.getCity());
        source.put("spend", dto.getSpend());
        source.put("platform", dto.getPlatform());
        source.put("resolution", dto.getResolution());
        doc.setSource(source);

        return doc;
    }
}
