package com.atguigu.gulimall.search;


import com.alibaba.fastjson.JSON;
import com.atguigu.gulimall.search.config.GulimallElasticSearchConfig;
import lombok.*;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.Avg;
import org.elasticsearch.search.aggregations.metrics.AvgAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GulimallSearchApplicationTests {

    @Autowired
    private RestHighLevelClient client;

    @Test
    public void test2() {
        String[] str = {"100_500","_500","100_","_",""};
        List<String> list = new ArrayList<>(Arrays.asList(str));
        System.out.println(list);

        list.stream().forEach(e->{
            System.out.println("-------------------------");
            System.out.println(e);
            String[] split = e.split("_");
            ArrayList<String> splitList = new ArrayList<>(Arrays.asList(split));
            System.out.println(split.length);
            System.out.println(splitList.size());
            System.out.println(splitList);
            System.out.println("-------------------------");
        });
    }

    @Data
    static class Account {

        private int account_number;
        private int balance;
        private String firstname;
        private String lastname;
        private int age;
        private String gender;
        private String address;
        private String employer;
        private String email;
        private String city;
        private String state;
    }
    /**
     *  ????????????
     */
    @Test
    public void searchData() throws IOException {
        // 1?????????????????????
        SearchRequest searchRequest = new SearchRequest();
        // ??????????????????
        // SearchSourceBuilder
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        // 1.1 ??????????????????
//        sourceBuilder.query();        // ??????
//        sourceBuilder.from();         // ?????? ????????????
//        sourceBuilder.size();         // ?????????
//        sourceBuilder.aggregation();  // ????????????
        sourceBuilder.query(QueryBuilders.matchQuery("address", "mill"));
        // ????????????
        // ????????????????????????????????????
        TermsAggregationBuilder ageAgg = AggregationBuilders.terms("ageAgg").field("age").size(10);
        sourceBuilder.aggregation(ageAgg);
        // ??????????????????
        AvgAggregationBuilder balanceAvg = AggregationBuilders.avg("balanceAvg").field("balance");
        sourceBuilder.aggregation(balanceAvg);
        System.out.println("???????????????" + sourceBuilder);


        searchRequest.source(sourceBuilder);

        // 2???????????????
        SearchResponse searchResponse = client.search(searchRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);

        // 3???????????????
        System.out.println(searchResponse.toString());

        // 3.1 ??????????????????????????????
        SearchHits hits = searchResponse.getHits();
        SearchHit[] searchHits = hits.getHits();
        for (SearchHit hit : searchHits) {
            String source = hit.getSourceAsString();
            Account account = JSON.parseObject(source, Account.class);
            System.out.println("account: " + account);
        }
        System.out.println("------------------------------------------------------------");
        // 3.2??????????????????????????????
        Aggregations aggregations = searchResponse.getAggregations();
        Terms ageAgg1 = aggregations.get("ageAgg");
        ageAgg1.getBuckets().forEach(e -> System.out.println("?????????" + e.getKeyAsString()));
        Avg balanceAvg1 = aggregations.get("balanceAvg");
        System.out.println("???????????????" + balanceAvg1.getValue());


    }


    @Test
    public void contextLoads() {
        System.out.println(client);

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    class User {
        private String userName;
        private String gender;
        private Integer age;
    }

    /**
     *  ??????????????????
     *  ???????????????????????????  ??????????????????????????????
     */
    @Test
    public void test() throws IOException {
        IndexRequest indexRequest = new IndexRequest("users");
        indexRequest.id("1");
        User user = new User();
        user.setUserName("zhangsan");
        user.setAge(18);
        user.setGender("man");
        String jsonString = JSON.toJSONString(user);
        indexRequest.source(jsonString, XContentType.JSON);

        // ????????????
        IndexResponse index = client.index(indexRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);
        // ??????????????????
        System.out.println(index);

    }



}
