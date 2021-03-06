package com.atguigu.gulimall.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.to.es.SkuEsModel;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.search.config.GulimallElasticSearchConfig;
import com.atguigu.gulimall.search.constant.EsConstant;
import com.atguigu.gulimall.search.feign.ProductFeignService;
import com.atguigu.gulimall.search.service.MallSearchService;
import com.atguigu.gulimall.search.vo.AttrResponseVo;
import com.atguigu.gulimall.search.vo.SearchParam;
import com.atguigu.gulimall.search.vo.SearchResult;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MallSearchServiceImpl implements MallSearchService {

    @Autowired
    private RestHighLevelClient client;
    @Autowired
    private ProductFeignService productFeignService;

    /**
     * @param param
     * @return
     */
    @Override
    public SearchResult search(SearchParam param) {

        // 1?????????????????????????????????DSL??????
        SearchResult result = null;

        // ??????????????????
        SearchRequest searchRequest = buildSearchRequest(param);

        try {
            // 2?????????????????????
            SearchResponse response = client.search(searchRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);

            // 3?????????????????????????????????????????????
            result = buildSearchResult(response, param);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * ??????????????????
     * ???????????? ?????????????????????????????????????????????????????????????????????????????????????????????
     * dsl?????????dsl.json???  ???????????????java???api??????es?????????????????????
     *
     * @return
     */
    private SearchRequest buildSearchRequest(SearchParam param) {

        // ??????builder????????????DSL??????
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        /**
         *  ???????????? ??????????????????????????????????????????????????????????????????
         */
        // 1?????????query bool
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        // ?????? must ???????????????????????????
        if (!StringUtils.isEmpty(param.getKeywords())) {
            boolQuery.must(QueryBuilders.matchQuery("skuTitle", param.getKeywords()));
        }
        // 1.2??? bool- filter  ????????????id??????
        if (null != param.getCatalog3Id()) {
            boolQuery.filter(QueryBuilders.termQuery("catalogId", param.getCatalog3Id()));
        }
        // 1.3?????????id??????
        if (!CollectionUtils.isEmpty(param.getBrandId())) {
            boolQuery.filter(QueryBuilders.termsQuery("brandId", param.getBrandId()));
        }
        // 1.4 ???????????????
        if (null != param.getHasStock()) {
            boolQuery.filter(QueryBuilders.termQuery("hasStock", param.getHasStock() == 1));
        }
        // 1.5 ????????????
        if (!StringUtils.isEmpty(param.getSkuPrice())) {
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery(param.getSkuPrice());
            String[] rangePrice = param.getSkuPrice().split("_");
            if (rangePrice.length == 2) {
                // gte ??? >=     lte ??? <=
                if (param.getSkuPrice().startsWith("_")) {
                    // ??????????????? _500
                    rangeQuery.lte(rangePrice[1]);
                } else {
                    // ??????????????? "100_500"
                    rangeQuery.lte(rangePrice[1]);
                    rangeQuery.gte(rangePrice[0]);
                }
            } else if (rangePrice.length == 1) {
                // ??????????????? "100_"
                rangeQuery.gte(rangePrice[0]);
            }
        }

        // 1.6??? ??????????????????
        if (!CollectionUtils.isEmpty(param.getAttrs())) {
            param.getAttrs().stream().forEach(attr -> {
                // attrs=1_5???:8???&attrs=2??????16G:8G
                BoolQueryBuilder nestedboolQuery = QueryBuilders.boolQuery();
                // attrs=1_5???:8???
                String[] s = attr.split("_");
                String attrId = s[0];
                String[] attrValues = s[1].split(":");
                nestedboolQuery.must(QueryBuilders.termQuery("attrs.attrId", attrId));
                nestedboolQuery.must(QueryBuilders.termQuery("attrs.attrValue", attrValues));
                // ????????????????????????nested??????
                NestedQueryBuilder attrs = QueryBuilders.nestedQuery("attrs", nestedboolQuery, ScoreMode.None);
                boolQuery.filter(nestedboolQuery);
            });
        }
        // ?????????????????????????????????????????????
        sourceBuilder.query(boolQuery);

        /**
         *  ????????????????????????
         */
        // ??????
        if (!StringUtils.isEmpty(param.getSort())) {
            String[] sortParam = param.getSort().split("_");
            SortOrder order = sortParam[1].equalsIgnoreCase("asc") ? SortOrder.ASC : SortOrder.DESC;
            sourceBuilder.sort(sortParam[0], order);
        }
        // ??????
        if (param.getPageNum() == null) {
            param.setPageNum(1);
        }
        sourceBuilder.from((param.getPageNum() - 1) * EsConstant.PAGE_SIZE);
        sourceBuilder.size(EsConstant.PAGE_SIZE);
        // ??????
        if (!StringUtils.isEmpty(param.getKeywords())) {
            HighlightBuilder highlightBuilder = new HighlightBuilder();
            highlightBuilder.field("skuTitle");
            highlightBuilder.preTags("<b style='color:red'>");
            highlightBuilder.postTags("</b>");
            sourceBuilder.highlighter(highlightBuilder);
        }

        System.out.println("?????????DSL????????? " + sourceBuilder.toString());

        /**
         *  ????????????
         */
        // ????????????
        TermsAggregationBuilder brand_agg = AggregationBuilders.terms("brand_agg");
        brand_agg.field("brandId").size(50);
        brand_agg.subAggregation(AggregationBuilders.terms("brand_name_agg").field("brandName").size(1));
        brand_agg.subAggregation(AggregationBuilders.terms("brand_img_agg").field("brandImg").size(1));
        sourceBuilder.aggregation(brand_agg);
        // ????????????
        TermsAggregationBuilder catalog_agg = AggregationBuilders.terms("catalog_agg");
        catalog_agg.field("catalogId").size(50);
        catalog_agg.subAggregation(AggregationBuilders.terms("catalog_name_agg").field("catalogName").size(1));
        sourceBuilder.aggregation(catalog_agg);
        // ????????????
        NestedAggregationBuilder attr_agg = AggregationBuilders.nested("attr_agg", "attrs");
        NestedAggregationBuilder attr_id_agg = attr_agg.subAggregation(AggregationBuilders.terms("attr_id_agg").field("attrs.attrId").size(1));
        attr_id_agg.subAggregation(AggregationBuilders.terms("attr_name_agg").field("attrs.attrName").size(1));
        attr_id_agg.subAggregation(AggregationBuilders.terms("attr_value_agg").field("attrs.attrName").size(50));
        sourceBuilder.aggregation(attr_agg);

        SearchRequest searchRequest = new SearchRequest(new String[]{EsConstant.PRODUCT_INDEX}, sourceBuilder);
        return searchRequest;
    }

    /**
     * ??????????????????
     *
     * @param response
     * @return
     */
    private SearchResult buildSearchResult(SearchResponse response, SearchParam param) {

        SearchResult searchResult = new SearchResult();

        SearchHits hits = response.getHits();
        Aggregations aggregations = response.getAggregations();

        // ????????????????????????
        List<SkuEsModel> esModelList = new ArrayList<>();
        if (hits.getHits() != null && hits.getHits().length > 0) {
            for (SearchHit hit : hits.getHits()) {
                String sourceAsString = hit.getSourceAsString();
                SkuEsModel skuEsModel = JSON.parseObject(sourceAsString, SkuEsModel.class);
                // ?????????????????????
                if (!StringUtils.isEmpty(param.getKeywords())) {
                    HighlightField skuTitle = hit.getHighlightFields().get("skuTitle");
                    String highLightKeyWords = skuTitle.getFragments()[0].string();
                    skuEsModel.setSkuTitle(highLightKeyWords);
                }
                esModelList.add(skuEsModel);
            }
        }
        searchResult.setProducts(esModelList);

        // ???????????????????????????????????????????????????????????????????????????
        ParsedLongTerms catalog_agg = aggregations.get("catalog_agg");
        List<SearchResult.CatalogVo> catalogs = new ArrayList<>();
        catalog_agg.getBuckets().forEach(e -> {
            SearchResult.CatalogVo catalogVo = new SearchResult.CatalogVo();
            // ????????????id
            String catalogId = e.getKeyAsString();
            catalogVo.setCatalogId(Long.parseLong(catalogId));
            // ??????????????????????????????id???????????????????????????????????????id?????????????????????????????????get
            // ?????????
            ParsedStringTerms catalog_name_agg = e.getAggregations().get("catalog_name_agg");
            String catalogName = catalog_name_agg.getBuckets().get(0).getKeyAsString();
            catalogVo.setCatalogName(catalogName);
            catalogs.add(catalogVo);
        });
        searchResult.setCatalogs(catalogs);

        // ??????????????????????????????????????????
        List<SearchResult.BrandVo> brands = new ArrayList<>();
        ParsedLongTerms brand_agg = aggregations.get("brand_agg");
        brand_agg.getBuckets().forEach(e -> {
            SearchResult.BrandVo brandVo = new SearchResult.BrandVo();
            // ??????id
            String brandId = e.getKeyAsString();
            brandVo.setBrandId(Long.parseLong(brandId));
            // ????????????
            ParsedStringTerms brand_name_agg = e.getAggregations().get("brand_name_agg");
            String brandName = brand_name_agg.getBuckets().get(0).getKeyAsString();
            brandVo.setBrandName(brandName);
            // ????????????
            ParsedStringTerms brand_img_agg = e.getAggregations().get("brand_img_agg");
            String brandImg = brand_img_agg.getBuckets().get(0).getKeyAsString();
            brandVo.setBrandImg(brandImg);
            brands.add(brandVo);
        });
        searchResult.setBrands(brands);

        // ??????????????????????????????????????????
        List<SearchResult.AttrVo> attrVoList = new ArrayList<>();
        ParsedNested attr_agg = aggregations.get("attr_agg");
        ParsedLongTerms attr_id_agg = attr_agg.getAggregations().get("attr_id_agg");
        attr_id_agg.getBuckets().forEach(e -> {
            SearchResult.AttrVo attrVo = new SearchResult.AttrVo();
            // ??????id
            String attrId = e.getKeyAsString();
            attrVo.setAttrId(Long.parseLong(attrId));
            // ????????????
            ParsedStringTerms attr_name_agg = e.getAggregations().get("attr_name_agg");
            attrVo.setAttrName(attr_name_agg.getBuckets().get(0).getKeyAsString());
            // ?????????
            ParsedStringTerms attr_value_agg = e.getAggregations().get("attr_value_agg");
//            List<String> attrValueList = new ArrayList<>();
//            attr_value_agg.getBuckets().forEach(v -> attrValueList.add(v.getKeyAsString()));
            List<String> attrValueList = attr_value_agg.getBuckets().stream().map(v -> v.getKeyAsString()).collect(Collectors.toList());
            attrVo.setAttrValue(attrValueList);
            searchResult.getAttrIds().add(Long.parseLong(attrId));
            attrVoList.add(attrVo);
        });
        searchResult.setAttrs(attrVoList);

        // ??????????????????????????????????????????
        // ?????????
        searchResult.setPageNum(param.getPageNum());
        // ?????????
        long total = hits.getTotalHits().value;
        searchResult.setTotal(total);
        // ?????????
        Integer totalPage = Math.toIntExact(total % EsConstant.PAGE_SIZE == 0 ? total / EsConstant.PAGE_SIZE : total / EsConstant.PAGE_SIZE + 1);
        // ????????????
        List<Integer> pageNavs = new ArrayList<>();
        for (Integer i = 1; i <= totalPage; i++) {
            pageNavs.add(i);
        }
        searchResult.setPageNavs(pageNavs);

        // ???????????????????????????
        // ????????????????????????
        if (CollectionUtils.isEmpty(param.getAttrs())) {
            List<SearchResult.NavVo> navs = param.getAttrs().stream().map(attr -> {
                SearchResult.NavVo navVo = new SearchResult.NavVo();
                // attrs = 2_5???:6???
                String[] s = attr.split("_");
                navVo.setNavValue(s[1]);
                R r = productFeignService.attrInfo(Long.parseLong(s[0]));
                searchResult.getAttrIds().add(Long.parseLong(s[0]));
                if (r.getCode() == 0) {
                    AttrResponseVo data = r.getData("attr", new TypeReference<AttrResponseVo>() {
                    });
                    navVo.setNavName(data.getAttrName());
                }
                String replace = replaceQueryString(param, attr, "attrs");
                navVo.setLink("http://search.gulimall.com/list.html?" + replace);
                return navVo;
            }).collect(Collectors.toList());
            searchResult.setNavs(navs);
        }
        // ??????
        if (!CollectionUtils.isEmpty(param.getBrandId())) {
            List<SearchResult.NavVo> navs = searchResult.getNavs();
            SearchResult.NavVo navVo = new SearchResult.NavVo();
            navVo.setNavName("??????");
            // TODO ????????????????????????
            R r = productFeignService.brandsInfos(param.getBrandId());
            if (r.getCode() == 0) {
                List<SearchResult.BrandVo> brand = r.getData("brands", new TypeReference<List<SearchResult.BrandVo>>() {
                });
                StringBuffer buffer = new StringBuffer();
                String replace = "";
                for (SearchResult.BrandVo brandVo : brand) {
                    buffer.append(brandVo.getBrandName() + ";");
                    replace = replaceQueryString(param, brandVo.getBrandId() + "", "attrs");
                }
                navVo.setNavValue(buffer.toString());
                navVo.setLink("http://search.gulimall.com/list.html?" + replace);
            }
        }

        return searchResult;
    }

    private String replaceQueryString(SearchParam param, String value, String key) {
        // ????????????
        String encode = null;
        try {
            encode = URLEncoder.encode(value, "UTF-8");
            encode.replace("+", "");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        // ???????????????????????????????????????????????????
        // ??????????????????????????????????????????
        String replace = param.get_queryString().replace("&" + key + "=" + encode, "");
        return replace;
    }
}
