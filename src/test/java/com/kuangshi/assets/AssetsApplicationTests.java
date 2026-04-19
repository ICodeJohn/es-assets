package com.kuangshi.assets;

import com.kuangshi.assets.excel.reader.AssetExcelReader;
import com.kuangshi.assets.model.TagStatsDTO;
import com.kuangshi.assets.model.UploaderAvgSizeDTO;
import com.kuangshi.assets.service.impl.AssetServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;

@Slf4j
@SpringBootTest
class AssetsApplicationTests {

    @Autowired
    private AssetExcelReader assetExcelReader;

    @Autowired
    private AssetServiceImpl assetService;

    @Test
    void readCsvFileWithListenerVersion1() throws IOException {
        String filepath1 = "src/main/resources/cvs/assets-1.csv";
        assetExcelReader.readCsvFileWithListenerVersion1(filepath1);
        String filepath2 = "src/main/resources/cvs/assets-2.csv";
        assetExcelReader.readCsvFileWithListenerVersion2(filepath2);
        String filepath3 = "src/main/resources/cvs/assets-3.csv";
        assetExcelReader.readCsvFileWithListenerVersion3(filepath3);
        List<UploaderAvgSizeDTO> uploaderAvgSizeDTOS = assetService.avgSizeByUploader();
        log.info("uploaderAvgSizeDTOS:{}", uploaderAvgSizeDTOS);
        List<TagStatsDTO> top5Tags = assetService.getTop5Tags();
        log.info("top5Tags:{}", top5Tags);
    }

}
