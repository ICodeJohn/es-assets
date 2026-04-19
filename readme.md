
1.修改配置文件中es相关配置:
spring.elasticsearch.uris
spring.elasticsearch.username
spring.elasticsearch.password

2.数据初始化注意数据
默认已经将作业中的数据放在了如下位置，如需更改请在启动项目前更改
es-assets\src\main\resources\cvs


3.ES相关注意事项:
ES本地环境使用的单节点，创建索引相关配置会在项目启动后自行删除重新创建

    AssetServiceImpl 类初始化方法

    @PostConstruct
    public void initIndex() {
    recreateIndex();
    }

    /**
     * 重建索引
     */
    public void recreateIndex() {
        try {

            String indexName = "assets";

            // 1. 判断索引是否存在
            boolean exists = elasticsearchClient.indices()
                    .exists(e -> e.index(indexName))
                    .value();

            if (exists) {
                log.info("删除旧索引 {}", indexName);
                elasticsearchClient.indices().delete(d -> d.index(indexName));
            }

            // 2. 创建索引（基础 settings）
            log.info("创建索引 {}", indexName);

            elasticsearchClient.indices().create(c -> c
                    .index(indexName)
                    .settings(s -> s
                            .numberOfShards("1")
                            .numberOfReplicas("0")
                    )
                    .mappings(m -> m
                            .dynamic(DynamicMapping.False)  // 禁用动态映射：忽略未知字段
                            .properties("assetId", p -> p.keyword(k -> k))
                            .properties("title", p -> p.text(t -> t))
                            .properties("uploader", p -> p.keyword(k -> k))
                            .properties("timestamp", p -> p.long_(l -> l))
                            .properties("status", p -> p.keyword(k -> k))
                            .properties("tags", p -> p.keyword(k -> k))
                            .properties("city", p -> p.keyword(k -> k))
                            .properties("size", p -> p.long_(l -> l))
                            .properties("fileType", p -> p.keyword(k -> k))
                            .properties("extra", p -> p.object(k -> k))
                            .properties("source", p -> p.object(k -> k))
                    )
            );

            log.info("索引创建成功");

            log.info("初始化数据assets-1.csv开始");
            String filepath1 = "src/main/resources/cvs/assets-1.csv";
            assetExcelReader.readCsvFileWithListenerVersion1(filepath1);
            log.info("初始化数据assets-1.csv完成");

            log.info("初始化数据assets-2.csv开始");
            String filepath2 = "src/main/resources/cvs/assets-2.csv";
            assetExcelReader.readCsvFileWithListenerVersion2(filepath2);
            log.info("初始化数据assets-2.csv完成");

            log.info("初始化数据assets-3.csv开始");
            String filepath3 = "src/main/resources/cvs/assets-3.csv";
            assetExcelReader.readCsvFileWithListenerVersion3(filepath3);
            log.info("初始化数据assets-3.csv完成");

            ObjectMapper objectMapper = new ObjectMapper();

            List<UploaderAvgSizeDTO> avgSizeByUploaderResult = assetDAO.avgSizeByUploader();
            log.info("统计审核状态为已通过的素材中，各上传人的平均文件大小:{}", objectMapper.writeValueAsString(avgSizeByUploaderResult));

            List<TagStatsDTO> top5Tags = assetDAO.getTop5Tags();
            log.info("按标签统计素材数量，列出数量最多的前 5 个标签:{}", objectMapper.writeValueAsString(top5Tags));

            List<UploaderQualityDTO> uploaderQualityDTOS = assetDAO.analyzeUploaderQuality();
            log.info("分析各上传人的素材质量和审核通过率，识别优质内容生产者，要求审核通过数量大于0:{}", objectMapper.writeValueAsString(uploaderQualityDTOS));

        } catch (Exception e) {
            log.error("索引创建失败", e);
        }
    }


4.由于index字段可能和题目中不一致，请在测试api时注意字段差异，另外特殊字符需要先转义再请求:

http://localhost:8080/assets?status=approved&uploader=%E5%BC%A0%E4%B8%89


[
{
"assetId": "A0004",
"title": "素材_004_新品",
"uploader": "张三",
"timestamp": 1776606744979,
"status": "approved",
"tags": [
"促销",
"品牌"
],
"city": "西安",
"size": 111379742,
"extra": {
"remark": "背景音乐问题",
"reviewer": "刘八"
},
"fileType": "version1",
"source": {
"文件大小(MB)": "106.22MB",
"素材编号": "A0004",
"审核人": "刘八",
"所在城市": "西安",
"上传人": "张三",
"标签": "促销;品牌",
"备注": "背景音乐问题",
"标题": "素材_004_新品",
"审核状态": "已通过",
"上传日期": "2024/10/23"
}
},
{
"assetId": "A0005",
"title": "素材_005_夏日",
"uploader": "张三",
"timestamp": 1776606744980,
"status": "approved",
"tags": [
"剧情",
"搞笑"
],
"city": "深圳",
"size": 97874083,
"extra": {
"reviewer": "赵六"
},
"fileType": "version1",
"source": {
"文件大小(MB)": "93.34MB",
"素材编号": "A0005",
"审核人": "赵六",
"所在城市": "深圳",
"上传人": "张三",
"标签": "剧情;搞笑",
"标题": "素材_005_夏日",
"审核状态": "已通过",
"上传日期": "2024/2/18"
}
}
]


http://localhost:8080/assets?size%5Blte%5D=524288000

[
{
"assetId": "A0001",
"title": "素材_001_冬季",
"uploader": "李四",
"timestamp": 1776606744969,
"status": "pending",
"tags": [
"节日",
"促销"
],
"city": "上海",
"size": 66857205,
"extra": {
"remark": "背景音乐问题"
},
"fileType": "version1",
"source": {
"文件大小(MB)": "63.76MB",
"素材编号": "A0001",
"所在城市": "上海",
"上传人": "李四",
"标签": "节日;促销",
"备注": "背景音乐问题",
"标题": "素材_001_冬季",
"审核状态": "待审核",
"上传日期": "2024/9/5"
}
},
{
"assetId": "A0002",
"title": "素材_002_夏日",
"uploader": "赵六",
"timestamp": 1776606744977,
"status": "pending",
"tags": [
"节日"
],
"city": "武汉",
"size": 69614960,
"extra": {

    },
    "fileType": "version1",
    "source": {
      "文件大小(MB)": "66.39MB",
      "素材编号": "A0002",
      "所在城市": "武汉",
      "上传人": "赵六",
      "标签": "节日",
      "标题": "素材_002_夏日",
      "审核状态": "待审核",
      "上传日期": "2024/6/12"
    }
},
{
"assetId": "A0003",
"title": "素材_003_新品",
"uploader": "陈七",
"timestamp": 1776606744978,
"status": "rejected",
"tags": [
"品牌"
],
"city": "广州",
"size": 212860928,
"extra": {
"reviewer": "周九"
},
"fileType": "version1",
"source": {
"文件大小(MB)": "203.0MB",
"素材编号": "A0003",
"审核人": "周九",
"所在城市": "广州",
"上传人": "陈七",
"标签": "品牌",
"标题": "素材_003_新品",
"审核状态": "已拒绝",
"上传日期": "2024/6/23"
}
},
{
"assetId": "A0004",
"title": "素材_004_新品",
"uploader": "张三",
"timestamp": 1776606744979,
"status": "approved",
"tags": [
"促销",
"品牌"
],
"city": "西安",
"size": 111379742,
"extra": {
"remark": "背景音乐问题",
"reviewer": "刘八"
},
"fileType": "version1",
"source": {
"文件大小(MB)": "106.22MB",
"素材编号": "A0004",
"审核人": "刘八",
"所在城市": "西安",
"上传人": "张三",
"标签": "促销;品牌",
"备注": "背景音乐问题",
"标题": "素材_004_新品",
"审核状态": "已通过",
"上传日期": "2024/10/23"
}
},
{
"assetId": "A0005",
"title": "素材_005_夏日",
"uploader": "张三",
"timestamp": 1776606744980,
"status": "approved",
"tags": [
"剧情",
"搞笑"
],
"city": "深圳",
"size": 97874083,
"extra": {
"reviewer": "赵六"
},
"fileType": "version1",
"source": {
"文件大小(MB)": "93.34MB",
"素材编号": "A0005",
"审核人": "赵六",
"所在城市": "深圳",
"上传人": "张三",
"标签": "剧情;搞笑",
"标题": "素材_005_夏日",
"审核状态": "已通过",
"上传日期": "2024/2/18"
}
},
{
"assetId": "A0007",
"title": "素材_007_冬季",
"uploader": "王五",
"timestamp": 1776606744982,
"status": "rejected",
"tags": [
"促销",
"搞笑",
"剧情"
],
"city": "西安",
"size": 206401699,
"extra": {
"remark": "重新剪辑",
"reviewer": "王五"
},
"fileType": "version1",
"source": {
"文件大小(MB)": "196.84MB",
"素材编号": "A0007",
"审核人": "王五",
"所在城市": "西安",
"上传人": "王五",
"标签": "促销;搞笑;剧情",
"备注": "重新剪辑",
"标题": "素材_007_冬季",
"审核状态": "已拒绝",
"上传日期": "2024/3/22"
}
},
{
"assetId": "A0008",
"title": "素材_008_冬季",
"uploader": "张三",
"timestamp": 1776606744984,
"status": "rejected",
"tags": [
"剧情"
],
"city": "成都",
"size": 449304330,
"extra": {
"remark": "重新剪辑",
"reviewer": "张三"
},
"fileType": "version1",
"source": {
"文件大小(MB)": "428.49MB",
"素材编号": "A0008",
"审核人": "张三",
"所在城市": "成都",
"上传人": "张三",
"标签": "剧情",
"备注": "重新剪辑",
"标题": "素材_008_冬季",
"审核状态": "已拒绝",
"上传日期": "2024/4/14"
}
},
{
"assetId": "A0009",
"title": "素材_009_限定",
"uploader": "周九",
"timestamp": 1776606744986,
"status": "pending",
"tags": [
"剧情",
"促销",
"生活"
],
"city": "西安",
"size": 174105559,
"extra": {

    },
    "fileType": "version1",
    "source": {
      "文件大小(MB)": "166.04MB",
      "素材编号": "A0009",
      "所在城市": "西安",
      "上传人": "周九",
      "标签": "剧情;促销;生活",
      "标题": "素材_009_限定",
      "审核状态": "待审核",
      "上传日期": "2024/4/13"
    }
},
{
"assetId": "A0010",
"title": "素材_010_限定",
"uploader": "周九",
"timestamp": 1776606744988,
"status": "pending",
"tags": [
"搞笑",
"剧情",
"生活"
],
"city": "成都",
"size": 194668134,
"extra": {

    },
    "fileType": "version1",
    "source": {
      "文件大小(MB)": "185.65MB",
      "素材编号": "A0010",
      "所在城市": "成都",
      "上传人": "周九",
      "标签": "搞笑;剧情;生活",
      "标题": "素材_010_限定",
      "审核状态": "待审核",
      "上传日期": "2024/4/12"
    }
},
{
"assetId": "A0011",
"title": "素材_011_冬季",
"uploader": "周九",
"timestamp": 1776606744989,
"status": "rejected",
"tags": [
"促销"
],
"city": "上海",
"size": 337043783,
"extra": {
"remark": "重新剪辑",
"reviewer": "王五"
},
"fileType": "version1",
"source": {
"文件大小(MB)": "321.43MB",
"素材编号": "A0011",
"审核人": "王五",
"所在城市": "上海",
"上传人": "周九",
"标签": "促销",
"备注": "重新剪辑",
"标题": "素材_011_冬季",
"审核状态": "已拒绝",
"上传日期": "2024/2/23"
}
},
{
"assetId": "A0013",
"title": "素材_013_夏日",
"uploader": "陈七",
"timestamp": 1776606744992,
"status": "pending",
"tags": [
"搞笑",
"生活",
"促销"
],
"city": "深圳",
"size": 314100940,
"extra": {

    },
    "fileType": "version1",
    "source": {
      "文件大小(MB)": "299.55MB",
      "素材编号": "A0013",
      "所在城市": "深圳",
      "上传人": "陈七",
      "标签": "搞笑;生活;促销",
      "标题": "素材_013_夏日",
      "审核状态": "待审核",
      "上传日期": "2024/6/17"
    }
},
{
"assetId": "A0014",
"title": "素材_014_限定",
"uploader": "张三",
"timestamp": 1776606744993,
"status": "pending",
"tags": [
"种草",
"搞笑",
"品牌"
],
"city": "上海",
"size": 363499356,
"extra": {

    },
    "fileType": "version1",
    "source": {
      "文件大小(MB)": "346.66MB",
      "素材编号": "A0014",
      "所在城市": "上海",
      "上传人": "张三",
      "标签": "种草;搞笑;品牌",
      "标题": "素材_014_限定",
      "审核状态": "待审核",
      "上传日期": "2024/5/25"
    }
},
{
"assetId": "A0015",
"title": "素材_015_限定",
"uploader": "李四",
"timestamp": 1776606744995,
"status": "approved",
"tags": [
"促销"
],
"city": "广州",
"size": 190526259,
"extra": {
"reviewer": "李四"
},
"fileType": "version1",
"source": {
"文件大小(MB)": "181.7MB",
"素材编号": "A0015",
"审核人": "李四",
"所在城市": "广州",
"上传人": "李四",
"标签": "促销",
"标题": "素材_015_限定",
"审核状态": "已通过",
"上传日期": "2024/12/7"
}
},
{
"assetId": "A0017",
"title": "素材_017_夏日",
"uploader": "刘八",
"timestamp": 1776606744999,
"status": "rejected",
"tags": [
"生活",
"种草",
"促销"
],
"city": "北京",
"size": 267785338,
"extra": {
"remark": "背景音乐问题",
"reviewer": "赵六"
},
"fileType": "version1",
"source": {
"文件大小(MB)": "255.38MB",
"素材编号": "A0017",
"审核人": "赵六",
"所在城市": "北京",
"上传人": "刘八",
"标签": "生活;种草;促销",
"备注": "背景音乐问题",
"标题": "素材_017_夏日",
"审核状态": "已拒绝",
"上传日期": "2024/9/17"
}
},
{
"assetId": "A0018",
"title": "素材_018_夏日",
"uploader": "赵六",
"timestamp": 1776606745000,
"status": "pending",
"tags": [
"促销"
],
"city": "上海",
"size": 391789936,
"extra": {

    },
    "fileType": "version1",
    "source": {
      "文件大小(MB)": "373.64MB",
      "素材编号": "A0018",
      "所在城市": "上海",
      "上传人": "赵六",
      "标签": "促销",
      "标题": "素材_018_夏日",
      "审核状态": "待审核",
      "上传日期": "2024/8/3"
    }
},
{
"assetId": "A0019",
"title": "素材_019_冬季",
"uploader": "王五",
"timestamp": 1776606745002,
"status": "approved",
"tags": [
"搞笑"
],
"city": "西安",
"size": 93291806,
"extra": {
"reviewer": "吴十"
},
"fileType": "version1",
"source": {
"文件大小(MB)": "88.97MB",
"素材编号": "A0019",
"审核人": "吴十",
"所在城市": "西安",
"上传人": "王五",
"标签": "搞笑",
"标题": "素材_019_冬季",
"审核状态": "已通过",
"上传日期": "2024/11/29"
}
},
{
"assetId": "A0020",
"title": "素材_020_限定",
"uploader": "刘八",
"timestamp": 1776606745003,
"status": "approved",
"tags": [
"促销"
],
"city": "武汉",
"size": 518122373,
"extra": {
"remark": "重新剪辑",
"reviewer": "李四"
},
"fileType": "version1",
"source": {
"文件大小(MB)": "494.12MB",
"素材编号": "A0020",
"审核人": "李四",
"所在城市": "武汉",
"上传人": "刘八",
"标签": "促销",
"备注": "重新剪辑",
"标题": "素材_020_限定",
"审核状态": "已通过",
"上传日期": "2024/10/31"
}
},
{
"assetId": "A0021",
"title": "素材_021_夏日",
"uploader": "赵六",
"timestamp": 1776606745005,
"status": "rejected",
"tags": [
"促销",
"种草",
"节日"
],
"city": "深圳",
"size": 83665879,
"extra": {
"reviewer": "刘八"
},
"fileType": "version1",
"source": {
"文件大小(MB)": "79.79MB",
"素材编号": "A0021",
"审核人": "刘八",
"所在城市": "深圳",
"上传人": "赵六",
"标签": "促销;种草;节日",
"标题": "素材_021_夏日",
"审核状态": "已拒绝",
"上传日期": "2024/6/30"
}
},
{
"assetId": "A0022",
"title": "素材_022_冬季",
"uploader": "李四",
"timestamp": 1776606745006,
"status": "approved",
"tags": [
"搞笑"
],
"city": "西安",
"size": 133274009,
"extra": {
"remark": "背景音乐问题",
"reviewer": "吴十"
},
"fileType": "version1",
"source": {
"文件大小(MB)": "127.1MB",
"素材编号": "A0022",
"审核人": "吴十",
"所在城市": "西安",
"上传人": "李四",
"标签": "搞笑",
"备注": "背景音乐问题",
"标题": "素材_022_冬季",
"审核状态": "已通过",
"上传日期": "2024/7/27"
}
},
{
"assetId": "A0023",
"title": "素材_023_冬季",
"uploader": "周九",
"timestamp": 1776606745008,
"status": "pending",
"tags": [
"种草",
"生活",
"促销"
],
"city": "西安",
"size": 428532039,
"extra": {
"remark": "重新剪辑"
},
"fileType": "version1",
"source": {
"文件大小(MB)": "408.68MB",
"素材编号": "A0023",
"所在城市": "西安",
"上传人": "周九",
"标签": "种草;生活;促销",
"备注": "重新剪辑",
"标题": "素材_023_冬季",
"审核状态": "待审核",
"上传日期": "2024/2/8"
}
},
{
"assetId": "asset_001",
"title": "creative_001",
"uploader": "王五",
"timestamp": 1714561200000,
"status": "pending",
"tags": [
"生活",
"搞笑",
"测评"
],
"city": "北京",
"size": 106268720,
"extra": {
"resolution": "720x1280",
"platform": "千川"
},
"fileType": "version2",
"source": {
"uploaded_at": "2024/5/1 19:00",
"city": "北京",
"uploader": "王五",
"spend": "N/A",
"asset_id": "asset_001",
"title": "creative_001",
"file_size_bytes": "106268720",
"resolution": "720x1280",
"platform": "千川",
"status": "pending",
"tags": "生活,搞笑,测评"
}
},
{
"assetId": "asset_002",
"title": "creative_002",
"uploader": "赵六",
"timestamp": 1706337540000,
"status": "rejected",
"tags": [
"节日",
"生活",
"种草"
],
"city": "北京",
"size": 125401220,
"extra": {
"resolution": "1080x1920",
"platform": "千川"
},
"fileType": "version2",
"source": {
"uploaded_at": "2024/1/27 14:39",
"city": "北京",
"uploader": "赵六",
"spend": "N/A",
"asset_id": "asset_002",
"title": "creative_002",
"file_size_bytes": "125401220",
"resolution": "1080x1920",
"platform": "千川",
"status": "rejected",
"tags": "节日,生活,种草"
}
},
{
"assetId": "asset_004",
"title": "creative_004",
"uploader": "李四",
"timestamp": 1708429920000,
"status": "rejected",
"tags": [
"剧情",
"测评"
],
"city": "北京",
"size": 374548208,
"extra": {
"resolution": "1080x1920",
"platform": "千川"
},
"fileType": "version2",
"source": {
"uploaded_at": "2024/2/20 19:52",
"city": "北京",
"uploader": "李四",
"spend": "N/A",
"asset_id": "asset_004",
"title": "creative_004",
"file_size_bytes": "374548208",
"resolution": "1080x1920",
"platform": "千川",
"status": "rejected",
"tags": "剧情,测评"
}
},
{
"assetId": "asset_005",
"title": "creative_005",
"uploader": "李四",
"timestamp": 1706496600000,
"status": "rejected",
"tags": [
"搞笑",
"促销",
"品牌"
],
"city": "深圳",
"size": 281301498,
"extra": {
"resolution": "NULL",
"platform": "千川"
},
"fileType": "version2",
"source": {
"uploaded_at": "2024/1/29 10:50",
"city": "深圳",
"uploader": "李四",
"spend": "N/A",
"asset_id": "asset_005",
"title": "creative_005",
"file_size_bytes": "281301498",
"resolution": "NULL",
"platform": "千川",
"status": "rejected",
"tags": "搞笑,促销,品牌"
}
},
{
"assetId": "asset_007",
"title": "creative_007",
"uploader": "赵六",
"timestamp": 1733892180000,
"status": "pending",
"tags": [
"搞笑",
"生活"
],
"city": "成都",
"size": 219339722,
"extra": {
"resolution": "NULL",
"platform": "巨量引擎"
},
"fileType": "version2",
"source": {
"uploaded_at": "2024/12/11 12:43",
"city": "成都",
"uploader": "赵六",
"spend": "N/A",
"asset_id": "asset_007",
"title": "creative_007",
"file_size_bytes": "219339722",
"resolution": "NULL",
"platform": "巨量引擎",
"status": "pending",
"tags": "搞笑,生活"
}
},
{
"assetId": "asset_008",
"title": "creative_008",
"uploader": "陈七",
"timestamp": 1720997100000,
"status": "approved",
"tags": [
"促销"
],
"city": "北京",
"size": 322068188,
"extra": {
"spend": "31714.22",
"resolution": "NULL",
"platform": "千川"
},
"fileType": "version2",
"source": {
"uploaded_at": "2024/7/15 6:45",
"city": "北京",
"uploader": "陈七",
"spend": "31714.22",
"asset_id": "asset_008",
"title": "creative_008",
"file_size_bytes": "322068188",
"resolution": "NULL",
"platform": "千川",
"status": "approved",
"tags": "促销"
}
},
{
"assetId": "asset_009",
"title": "creative_009",
"uploader": "周九",
"timestamp": 1729912860000,
"status": "pending",
"tags": [
"生活"
],
"city": "北京",
"size": 333701127,
"extra": {
"resolution": "1080x1920",
"platform": "千川"
},
"fileType": "version2",
"source": {
"uploaded_at": "2024/10/26 11:21",
"city": "北京",
"uploader": "周九",
"spend": "N/A",
"asset_id": "asset_009",
"title": "creative_009",
"file_size_bytes": "333701127",
"resolution": "1080x1920",
"platform": "千川",
"status": "pending",
"tags": "生活"
}
},
{
"assetId": "asset_010",
"title": "creative_010",
"uploader": "王五",
"timestamp": 1733670480000,
"status": "rejected",
"tags": [
"种草",
"测评",
"品牌"
],
"city": "武汉",
"size": 212474622,
"extra": {
"resolution": "1080x1920",
"platform": "巨量引擎"
},
"fileType": "version2",
"source": {
"uploaded_at": "2024/12/8 23:08",
"city": "武汉",
"uploader": "王五",
"spend": "N/A",
"asset_id": "asset_010",
"title": "creative_010",
"file_size_bytes": "212474622",
"resolution": "1080x1920",
"platform": "巨量引擎",
"status": "rejected",
"tags": "种草,测评,品牌"
}
},
{
"assetId": "asset_006",
"title": "creative_011",
"uploader": "李四",
"timestamp": 1714381800000,
"status": "approved",
"tags": [
"节日",
"剧情"
],
"city": "成都",
"size": 95265593,
"extra": {
"spend": "12476.49",
"resolution": "720x1280",
"platform": "千川"
},
"fileType": "version2",
"source": {
"uploaded_at": "2024/4/29 17:10",
"city": "成都",
"uploader": "李四",
"spend": "12476.49",
"asset_id": "asset_006",
"title": "creative_011",
"file_size_bytes": "95265593",
"resolution": "720x1280",
"platform": "千川",
"status": "approved",
"tags": "节日,剧情"
}
},
{
"assetId": "asset_012",
"title": "creative_012",
"uploader": "张三",
"timestamp": 1735101840000,
"status": "pending",
"tags": [
"测评"
],
"city": "广州",
"size": 226913741,
"extra": {
"resolution": "1080x1920",
"platform": "千川"
},
"fileType": "version2",
"source": {
"uploaded_at": "2024/12/25 12:44",
"city": "广州",
"uploader": "张三",
"spend": "N/A",
"asset_id": "asset_012",
"title": "creative_012",
"file_size_bytes": "226913741",
"resolution": "1080x1920",
"platform": "千川",
"status": "pending",
"tags": "测评"
}
},
{
"assetId": "asset_013",
"title": "creative_013",
"uploader": "李四",
"timestamp": 1730956920000,
"status": "rejected",
"tags": [
"品牌",
"测评"
],
"city": "武汉",
"size": 318896344,
"extra": {
"resolution": "1080x1920",
"platform": "千川"
},
"fileType": "version2",
"source": {
"uploaded_at": "2024/11/7 13:22",
"city": "武汉",
"uploader": "李四",
"spend": "N/A",
"asset_id": "asset_013",
"title": "creative_013",
"file_size_bytes": "318896344",
"resolution": "1080x1920",
"platform": "千川",
"status": "rejected",
"tags": "品牌,测评"
}
},
{
"assetId": "asset_015",
"title": "creative_015",
"uploader": "周九",
"timestamp": 1717408680000,
"status": "approved",
"tags": [
"搞笑"
],
"city": "成都",
"size": 351576554,
"extra": {
"spend": "17557.31",
"resolution": "1080x1920",
"platform": "千川"
},
"fileType": "version2",
"source": {
"uploaded_at": "2024/6/3 17:58",
"city": "成都",
"uploader": "周九",
"spend": "17557.31",
"asset_id": "asset_015",
"title": "creative_015",
"file_size_bytes": "351576554",
"resolution": "1080x1920",
"platform": "千川",
"status": "approved",
"tags": "搞笑"
}
},
{
"assetId": "asset_017",
"title": "creative_017",
"uploader": "周九",
"timestamp": 1716963360000,
"status": "rejected",
"tags": [
"种草",
"剧情"
],
"city": "北京",
"size": 256524331,
"extra": {
"resolution": "NULL",
"platform": "巨量引擎"
},
"fileType": "version2",
"source": {
"uploaded_at": "2024/5/29 14:16",
"city": "北京",
"uploader": "周九",
"spend": "N/A",
"asset_id": "asset_017",
"title": "creative_017",
"file_size_bytes": "256524331",
"resolution": "NULL",
"platform": "巨量引擎",
"status": "rejected",
"tags": "种草,剧情"
}
},
{
"assetId": "asset_018",
"title": "creative_018",
"uploader": "陈七",
"timestamp": 1718510280000,
"status": "pending",
"tags": [
"剧情",
"生活",
"种草"
],
"city": "杭州",
"size": 406978387,
"extra": {
"resolution": "NULL",
"platform": "千川"
},
"fileType": "version2",
"source": {
"uploaded_at": "2024/6/16 11:58",
"city": "杭州",
"uploader": "陈七",
"spend": "N/A",
"asset_id": "asset_018",
"title": "creative_018",
"file_size_bytes": "406978387",
"resolution": "NULL",
"platform": "千川",
"status": "pending",
"tags": "剧情,生活,种草"
}
},
{
"assetId": "asset_019",
"title": "creative_019",
"uploader": "周九",
"timestamp": 1717540680000,
"status": "rejected",
"tags": [
"品牌",
"促销",
"节日"
],
"city": "广州",
"size": 369886152,
"extra": {
"resolution": "1080x1920",
"platform": "千川"
},
"fileType": "version2",
"source": {
"uploaded_at": "2024/6/5 6:38",
"city": "广州",
"uploader": "周九",
"spend": "N/A",
"asset_id": "asset_019",
"title": "creative_019",
"file_size_bytes": "369886152",
"resolution": "1080x1920",
"platform": "千川",
"status": "rejected",
"tags": "品牌,促销,节日"
}
},
{
"assetId": "asset_020",
"title": "creative_020",
"uploader": "刘八",
"timestamp": 1722424080000,
"status": "pending",
"tags": [
"节日",
"生活"
],
"city": "西安",
"size": 378712045,
"extra": {
"resolution": "1080x1920",
"platform": "千川"
},
"fileType": "version2",
"source": {
"uploaded_at": "2024/7/31 19:08",
"city": "西安",
"uploader": "刘八",
"spend": "N/A",
"asset_id": "asset_020",
"title": "creative_020",
"file_size_bytes": "378712045",
"resolution": "1080x1920",
"platform": "千川",
"status": "pending",
"tags": "节日,生活"
}
},
{
"assetId": "asset_022",
"title": "creative_022",
"uploader": "王五",
"timestamp": 1731500100000,
"status": "pending",
"tags": [
"节日"
],
"city": "北京",
"size": 385739161,
"extra": {
"resolution": "720x1280",
"platform": "千川"
},
"fileType": "version2",
"source": {
"uploaded_at": "2024/11/13 20:15",
"city": "北京",
"uploader": "王五",
"spend": "N/A",
"asset_id": "asset_022",
"title": "creative_022",
"file_size_bytes": "385739161",
"resolution": "720x1280",
"platform": "千川",
"status": "pending",
"tags": "节日"
}
},
{
"assetId": "asset_023",
"title": "creative_023",
"uploader": "赵六",
"timestamp": 1719981300000,
"status": "rejected",
"tags": [
"测评",
"剧情"
],
"city": "武汉",
"size": 130627516,
"extra": {
"resolution": "1080x1920",
"platform": "千川"
},
"fileType": "version2",
"source": {
"uploaded_at": "2024/7/3 12:35",
"city": "武汉",
"uploader": "赵六",
"spend": "N/A",
"asset_id": "asset_023",
"title": "creative_023",
"file_size_bytes": "130627516",
"resolution": "1080x1920",
"platform": "千川",
"status": "rejected",
"tags": "测评,剧情"
}
},
{
"assetId": "asset_024",
"title": "creative_024",
"uploader": "赵六",
"timestamp": 1712225100000,
"status": "pending",
"tags": [
"测评"
],
"city": "广州",
"size": 58382497,
"extra": {
"resolution": "1080x1920",
"platform": "千川"
},
"fileType": "version2",
"source": {
"uploaded_at": "2024/4/4 18:05",
"city": "广州",
"uploader": "赵六",
"spend": "N/A",
"asset_id": "asset_024",
"title": "creative_024",
"file_size_bytes": "58382497",
"resolution": "1080x1920",
"platform": "千川",
"status": "pending",
"tags": "测评"
}
},
{
"assetId": "asset_025",
"title": "creative_025",
"uploader": "吴十",
"timestamp": 1722741780000,
"status": "pending",
"tags": [
"品牌",
"测评"
],
"city": "成都",
"size": 182736605,
"extra": {
"resolution": "1080x1920",
"platform": "千川"
},
"fileType": "version2",
"source": {
"uploaded_at": "2024/8/4 11:23",
"city": "成都",
"uploader": "吴十",
"spend": "N/A",
"asset_id": "asset_025",
"title": "creative_025",
"file_size_bytes": "182736605",
"resolution": "1080x1920",
"platform": "千川",
"status": "pending",
"tags": "品牌,测评"
}
},
{
"assetId": "vid0002",
"title": "投放素材002",
"uploader": "周九",
"timestamp": 1717743965000,
"status": "pending",
"tags": [
"节日"
],
"city": "北京",
"size": 365323878,
"extra": {
"durationSec": 157,
"platform": "qianchuan"
},
"fileType": "version3",
"source": {
"asset_title": "投放素材002",
"size": 348.4,
"city": "北京",
"uploader": "周九",
"id": "vid0002",
"review_status": "pending",
"size_unit": "MB",
"duration_sec": 157,
"platform": "qianchuan",
"timestamp": 1717743965,
"tags": "['节日']"
}
},
{
"assetId": "vid0004",
"title": "投放素材004",
"uploader": "王五",
"timestamp": 1715345835000,
"status": null,
"tags": [
"品牌",
"测评",
"节日"
],
"city": "武汉",
"size": 444071936,
"extra": {
"durationSec": 161,
"platform": "千川"
},
"fileType": "version3",
"source": {
"asset_title": "投放素材004",
"size": 423.5,
"city": "武汉",
"uploader": "王五",
"id": "vid0004",
"review_status": "通过",
"size_unit": "MB",
"duration_sec": 161,
"platform": "千川",
"timestamp": 1715345835,
"tags": "['品牌', '测评', '节日']"
}
},
{
"assetId": "vid0005",
"title": "投放素材005",
"uploader": "陈七",
"timestamp": 1706884009000,
"status": null,
"tags": [
"种草"
],
"city": "成都",
"size": 299578163,
"extra": {
"durationSec": 96,
"platform": "qianchuan"
},
"fileType": "version3",
"source": {
"asset_title": "投放素材005",
"size": 285.7,
"city": "成都",
"uploader": "陈七",
"id": "vid0005",
"review_status": "通过",
"size_unit": "MB",
"duration_sec": 96,
"platform": "qianchuan",
"timestamp": 1706884009,
"tags": "['种草']"
}
},
{
"assetId": "vid0006",
"title": "投放素材006",
"uploader": "张三",
"timestamp": 1713403942000,
"status": "pending",
"tags": [
"种草",
"剧情"
],
"city": "上海",
"size": 197866291,
"extra": {
"durationSec": 176,
"platform": "千川"
},
"fileType": "version3",
"source": {
"asset_title": "投放素材006",
"size": 188.7,
"city": "上海",
"uploader": "张三",
"id": "vid0006",
"review_status": "PENDING",
"size_unit": "MB",
"duration_sec": 176,
"platform": "千川",
"timestamp": 1713403942,
"tags": "['种草', '剧情']"
}
},
{
"assetId": "vid0008",
"title": null,
"uploader": "陈七",
"timestamp": 1727537349000,
"status": "reject",
"tags": [
"促销",
"测评",
"生活"
],
"city": "杭州",
"size": 494718156,
"extra": {
"durationSec": 157,
"platform": "qianchuan"
},
"fileType": "version3",
"source": {
"size": 471.8,
"city": "杭州",
"uploader": "陈七",
"id": "vid0008",
"review_status": "Reject",
"size_unit": "MB",
"duration_sec": 157,
"platform": "qianchuan",
"timestamp": 1727537349,
"tags": "['促销', '测评', '生活']"
}
},
{
"assetId": "vid0009",
"title": "投放素材009",
"uploader": "李四",
"timestamp": 1717376714000,
"status": "rejected",
"tags": [
"搞笑"
],
"city": "北京",
"size": 96259276,
"extra": {
"durationSec": 98,
"platform": "qianchuan"
},
"fileType": "version3",
"source": {
"asset_title": "投放素材009",
"size": 91.8,
"city": "北京",
"uploader": "李四",
"id": "vid0009",
"review_status": "rejected",
"size_unit": "MB",
"duration_sec": 98,
"platform": "qianchuan",
"timestamp": 1717376714,
"tags": "['搞笑']"
}
},
{
"assetId": "vid0010",
"title": "投放素材010",
"uploader": "吴十",
"timestamp": 1726264172000,
"status": "pending",
"tags": [
"测评"
],
"city": "西安",
"size": 92169830,
"extra": {
"durationSec": 49,
"platform": "qianchuan"
},
"fileType": "version3",
"source": {
"asset_title": "投放素材010",
"size": 90009.6,
"city": "西安",
"uploader": "吴十",
"id": "vid0010",
"review_status": "pending",
"size_unit": "KB",
"duration_sec": 49,
"platform": "qianchuan",
"timestamp": 1726264172,
"tags": "['测评']"
}
},
{
"assetId": "vid0012",
"title": "投放素材012",
"uploader": "刘八",
"timestamp": 1712249899000,
"status": "pending",
"tags": [
"种草",
"节日"
],
"city": "杭州",
"size": 381052518,
"extra": {
"durationSec": 97
},
"fileType": "version3",
"source": {
"asset_title": "投放素材012",
"size": 363.4,
"city": "杭州",
"uploader": "刘八",
"id": "vid0012",
"review_status": "待审核",
"size_unit": "MB",
"duration_sec": 97,
"timestamp": 1712249899,
"tags": "['种草', '节日']"
}
},
{
"assetId": "vid0013",
"title": "投放素材013",
"uploader": "李四",
"timestamp": 1733605836000,
"status": "rejected",
"tags": [
"种草",
"搞笑",
"促销"
],
"city": null,
"size": 456864563,
"extra": {
"durationSec": 71,
"platform": "qianchuan"
},
"fileType": "version3",
"source": {
"asset_title": "投放素材013",
"size": 446156.8,
"uploader": "李四",
"id": "vid0013",
"review_status": "已拒绝",
"size_unit": "KB",
"duration_sec": 71,
"platform": "qianchuan",
"timestamp": 1733605836,
"tags": "['种草', '搞笑', '促销']"
}
},
{
"assetId": "vid0014",
"title": "投放素材014",
"uploader": "赵六",
"timestamp": 1725740104000,
"status": "pending",
"tags": [
"搞笑"
],
"city": "未知",
"size": 310902784,
"extra": {
"durationSec": 104,
"platform": "qianchuan"
},
"fileType": "version3",
"source": {
"asset_title": "投放素材014",
"size": 296.5,
"city": "未知",
"uploader": "赵六",
"id": "vid0014",
"review_status": "待审核",
"size_unit": "MB",
"duration_sec": 104,
"platform": "qianchuan",
"timestamp": 1725740104,
"tags": "['搞笑']"
}
},
{
"assetId": "vid0015",
"title": null,
"uploader": "陈七",
"timestamp": 1722638670000,
"status": "approved",
"tags": [
"生活",
"品牌"
],
"city": "深圳",
"size": 250714521,
"extra": {
"durationSec": 90,
"platform": "千川"
},
"fileType": "version3",
"source": {
"size": 244838.4,
"city": "深圳",
"uploader": "陈七",
"id": "vid0015",
"review_status": "已通过",
"size_unit": "KB",
"duration_sec": 90,
"platform": "千川",
"timestamp": 1722638670,
"tags": "['生活', '品牌']"
}
},
{
"assetId": "vid0017",
"title": null,
"uploader": "张三",
"timestamp": 1707440296000,
"status": "rejected",
"tags": [
"搞笑"
],
"city": "北京",
"size": 223241830,
"extra": {
"durationSec": 23
},
"fileType": "version3",
"source": {
"size": 218009.6,
"city": "北京",
"uploader": "张三",
"id": "vid0017",
"review_status": "rejected",
"size_unit": "KB",
"duration_sec": 23,
"timestamp": 1707440296,
"tags": "['搞笑']"
}
},
{
"assetId": "vid0018",
"title": "投放素材018",
"uploader": "陈七",
"timestamp": 1727468644000,
"status": "approved",
"tags": [
"种草"
],
"city": "广州",
"size": 420269260,
"extra": {
"durationSec": 23
},
"fileType": "version3",
"source": {
"asset_title": "投放素材018",
"size": 400.8,
"city": "广州",
"uploader": "陈七",
"id": "vid0018",
"review_status": "APPROVED",
"size_unit": "MB",
"duration_sec": 23,
"timestamp": 1727468644,
"tags": "['种草']"
}
},
{
"assetId": "vid0019",
"title": "投放素材019",
"uploader": "张三",
"timestamp": 1732981824000,
"status": "pending",
"tags": [
"生活",
"种草"
],
"city": "杭州",
"size": 118174515,
"extra": {
"durationSec": 31,
"platform": "巨量引擎"
},
"fileType": "version3",
"source": {
"asset_title": "投放素材019",
"size": 112.7,
"city": "杭州",
"uploader": "张三",
"id": "vid0019",
"review_status": "待审核",
"size_unit": "MB",
"duration_sec": 31,
"platform": "巨量引擎",
"timestamp": 1732981824,
"tags": "['生活', '种草']"
}
},
{
"assetId": "vid0020",
"title": "投放素材020",
"uploader": "陈七",
"timestamp": 1708041774000,
"status": "approved",
"tags": [
"测评",
"节日",
"生活"
],
"city": "杭州",
"size": 493250150,
"extra": {
"durationSec": 155,
"platform": "千川"
},
"fileType": "version3",
"source": {
"asset_title": "投放素材020",
"size": 470.4,
"city": "杭州",
"uploader": "陈七",
"id": "vid0020",
"review_status": "已通过",
"size_unit": "MB",
"duration_sec": 155,
"platform": "千川",
"timestamp": 1708041774,
"tags": "['测评', '节日', '生活']"
}
},
{
"assetId": "vid0022",
"title": "投放素材022",
"uploader": "陈七",
"timestamp": 1717774775000,
"status": "pending",
"tags": [
"种草",
"促销"
],
"city": "西安",
"size": 450048819,
"extra": {
"durationSec": 28,
"platform": "巨量引擎"
},
"fileType": "version3",
"source": {
"asset_title": "投放素材022",
"size": 429.2,
"city": "西安",
"uploader": "陈七",
"id": "vid0022",
"review_status": "待审核",
"size_unit": "MB",
"duration_sec": 28,
"platform": "巨量引擎",
"timestamp": 1717774775,
"tags": "['种草', '促销']"
}
}
]


http://localhost:8080/assets/A0002?fields=title,status,uploader

{
"title": "素材_002_夏日",
"uploader": "赵六",
"status": "pending"
}