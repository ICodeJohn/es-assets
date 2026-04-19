package com.kuangshi.assets.excel.reader;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.kuangshi.assets.excel.listener.AssetExcelVersion1Listener;
import com.kuangshi.assets.excel.listener.AssetExcelVersion2Listener;
import com.kuangshi.assets.excel.listener.AssetExcelVersion3Listener;
import com.kuangshi.assets.excel.model.AssetExcelVersion1DTO;
import com.kuangshi.assets.excel.model.AssetExcelVersion2DTO;
import com.kuangshi.assets.excel.model.AssetExcelVersion3DTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssetExcelReader {

    private final AssetExcelVersion1Listener assetExcelVersion1Listener;
    private final AssetExcelVersion2Listener assetExcelVersion2Listener;
    private final AssetExcelVersion3Listener assetExcelVersion3Listener;

    /**
     * 使用监听器（推荐，适合大文件）
     */
    public void readCsvFileWithListenerVersion1(String filePath) throws IOException {
        try (InputStream inputStream = new FileInputStream(filePath)) {

            EasyExcel.read(inputStream, AssetExcelVersion1DTO.class, assetExcelVersion1Listener)
                    .excelType(ExcelTypeEnum.CSV)
                    .charset(StandardCharsets.UTF_8)  // 根据文件实际编码调整
                    .sheet()
                    .doRead();
        }
    }

    /**
     * 使用监听器（推荐，适合大文件）
     */
    public void readCsvFileWithListenerVersion2(String filePath) throws IOException {
        try (InputStream inputStream = new FileInputStream(filePath)) {

            EasyExcel.read(inputStream, AssetExcelVersion2DTO.class, assetExcelVersion2Listener)
                    .excelType(ExcelTypeEnum.CSV)
                    .charset(StandardCharsets.UTF_8)  // 根据文件实际编码调整
                    .sheet()
                    .doRead();
        }
    }

    /**
     * 使用监听器（推荐，适合大文件）
     */
    public void readCsvFileWithListenerVersion3(String filePath) throws IOException {
        try (InputStream inputStream = new FileInputStream(filePath)) {

            EasyExcel.read(inputStream, AssetExcelVersion3DTO.class, assetExcelVersion3Listener)
                    .excelType(ExcelTypeEnum.CSV)
                    .charset(StandardCharsets.UTF_8)  // 根据文件实际编码调整
                    .sheet()
                    .doRead();
        }
    }

}
