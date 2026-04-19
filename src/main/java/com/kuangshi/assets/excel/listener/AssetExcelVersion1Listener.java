package com.kuangshi.assets.excel.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelDataConvertException;
import com.kuangshi.assets.dao.AssetDAO;
import com.kuangshi.assets.enums.ReviewStatusEnum;
import com.kuangshi.assets.excel.model.AssetExcelVersion1DTO;
import com.kuangshi.assets.model.AssetDocument;
import com.kuangshi.assets.util.AssetSizeUtil;
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
public class AssetExcelVersion1Listener extends AnalysisEventListener<AssetExcelVersion1DTO> {

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
    public void invoke(AssetExcelVersion1DTO data, AnalysisContext context) {
        // 数据校验（可选）
        if (data == null) {
            log.warn("读取到空数据，跳过");
            return;
        }

        dataList.add(convertToAssetDocument(data));

        // 达到批量阈值，立即处理一批数据，释放内存
        if (dataList.size() >= BATCH_COUNT) {
            saveData();  // 保存到数据库或业务处理
            dataList.clear();  // 清空缓存
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
                    ex.getRowIndex() + 1,  // 行号从1开始
                    ex.getColumnIndex() + 1);  // 列索引从1开始
        }

        // 根据业务需求决定：抛出异常终止 或 继续解析
        throw new RuntimeException("CSV解析失败", exception);
    }

    /**
     * 业务处理方法：保存到数据库或进行其他操作
     */
    private void saveData() {
        log.info("批量处理 {} 条数据", dataList.size());
        // 这里实现具体的业务逻辑，如批量插入数据库
        assetDAO.batchSaveOrUpdateAssets(dataList);
    }


    public AssetDocument convertToAssetDocument(AssetExcelVersion1DTO dto) {
        if (dto == null) {
            return null;
        }

        AssetDocument doc = new AssetDocument();

        // 基础字段映射
        doc.setAssetId(dto.getAssetId());
        doc.setTitle(dto.getTitle());
        doc.setUploader(dto.getUploader());
        ReviewStatusEnum reviewStatusEnum = ReviewStatusEnum.fromDescription(dto.getReviewStatus());
        if(Objects.nonNull(reviewStatusEnum)){
            doc.setStatus(reviewStatusEnum.getCode());
        }
        doc.setCity(dto.getCity());

        // 处理时间戳（上传日期字符串转Long时间戳）
        if (dto.getUploadDate() != null && !dto.getUploadDate().isEmpty()) {
            try {
                // 根据实际日期格式调整，这里假设格式为 yyyy-MM-dd
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                Date date = sdf.parse(dto.getUploadDate());
                doc.setTimestamp(date.getTime());
            } catch (ParseException e) {
                // 如果解析失败，设置为当前时间戳
                doc.setTimestamp(System.currentTimeMillis());
            }
        } else {
            doc.setTimestamp(System.currentTimeMillis());
        }

        // 处理文件大小（字符串转Long，单位MB转字节）
        if (dto.getSize() != null && !dto.getSize().isEmpty()) {
            // 提取数字部分（支持小数）
            String numberStr = dto.getSize().replaceAll("[^0-9.]", "").trim();
            // 提取单位部分（字母）
            String unit = dto.getSize().replaceAll("[0-9.]", "").trim().toUpperCase();
            Long sizeInBytes = AssetSizeUtil.convertSizeToBytes(Double.parseDouble(numberStr), unit);
            doc.setSize(sizeInBytes);
        } else {
            doc.setSize(0L);
        }



        // 处理标签（按分隔符拆分，常见分隔符：逗号、分号、空格）
        if (dto.getTags() != null && !dto.getTags().isEmpty()) {
            List<String> tagList = Arrays.stream(dto.getTags().split("[，,;；、\\s]+"))
                    .map(String::trim)
                    .filter(tag -> !tag.isEmpty())
                    .collect(Collectors.toList());
            doc.setTags(tagList);
        } else {
            doc.setTags(new ArrayList<>());
        }

        // 构建扩展字段：存储审核人、备注等差异字段
        Map<String, Object> extra = new HashMap<>();
        if (dto.getReviewer() != null && !dto.getReviewer().isEmpty()) {
            extra.put("reviewer", dto.getReviewer());
        }
        if (dto.getRemark() != null && !dto.getRemark().isEmpty()) {
            extra.put("remark", dto.getRemark());
        }
        doc.setExtra(extra);

        // 设置文件类型（可根据文件名后缀或素材编号规则推断，这里默认未识别）
        doc.setFileType("version1");

        // 存储完整源数据（用于追溯）
        Map<String, Object> source = new HashMap<>();
        source.put("素材编号", dto.getAssetId());
        source.put("标题", dto.getTitle());
        source.put("上传人",dto.getUploader());
        source.put("上传日期", dto.getUploadDate());
        source.put("文件大小(MB)", dto.getSize());
        source.put("审核状态", dto.getReviewStatus());
        source.put("标签", dto.getTags());
        source.put("审核人", dto.getReviewer());
        source.put("所在城市", dto.getCity());
        source.put("备注", dto.getRemark());
        doc.setSource(source);

        return doc;
    }
}
