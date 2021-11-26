package com.atguigu.gulimall.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.common.to.es.SkuEsModel;
import com.atguigu.gulimall.search.config.GulimallElasticSearchConfig;
import com.atguigu.gulimall.search.constant.EsConstant;
import com.atguigu.gulimall.search.service.ProductSaveService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@Service
public class ProductSaveServiceImpl implements ProductSaveService {

    @Autowired
    RestHighLevelClient restHighLevelClient;


    @Override
    public Boolean productStatusUp(List<SkuEsModel> skuEsModels) throws IOException {
        // 数据保存到es
        // 1、es中建立索引 product    ----手动在kibana中添加mapping映射

        // 2、给es保存数据
        BulkRequest bulkRequest = new BulkRequest();
        skuEsModels.forEach(model -> {
            // 构造保存请求
            IndexRequest indexRequest = new IndexRequest(EsConstant.PRODUCT_INDEX);
            indexRequest.id(model.getSkuId().toString());
            String s = JSON.toJSONString(model);
            // 将数据循环保存在indexRequest中
            indexRequest.source(s, XContentType.JSON);
            // bulk批量请求添加请求数据
            bulkRequest.add(indexRequest);
        });
        // 发送批量请求
        BulkResponse bulk = restHighLevelClient.bulk(bulkRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);

        // TODO 如果批量保存数据到es出现错误
        boolean hasFailures = bulk.hasFailures();  // 是否出现错误

        List<String> sukIdList = Arrays.stream(bulk.getItems()).map(item -> item.getId()).collect(Collectors.toList());
        log.info("商品上架完成：{}; 返回数据：{}", sukIdList, bulk.toString());

        return hasFailures;
    }
}
