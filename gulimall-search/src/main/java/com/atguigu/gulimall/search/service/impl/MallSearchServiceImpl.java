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

        // 1、动态构建出查询所需的DSL语句
        SearchResult result = null;

        // 准备检索请求
        SearchRequest searchRequest = buildSearchRequest(param);

        try {
            // 2、执行检所请求
            SearchResponse response = client.search(searchRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);

            // 3、分析相应数据封装成需要的格式
            result = buildSearchResult(response, param);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * 准备检索请求
     * 模糊匹配 过滤（按照属性，分类，品牌，价格区间，库存），排序，分页，高亮
     * dsl语句在dsl.json中  相当于要是java的api实现es查询语句的操作
     *
     * @return
     */
    private SearchRequest buildSearchRequest(SearchParam param) {

        // 这个builder用来构建DSL语句
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        /**
         *  模糊匹配 过滤（按照属性，分类，品牌，价格区间，库存）
         */
        // 1、构建query bool
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        // 构建 must 关键字模糊匹配语句
        if (!StringUtils.isEmpty(param.getKeywords())) {
            boolQuery.must(QueryBuilders.matchQuery("skuTitle", param.getKeywords()));
        }
        // 1.2、 bool- filter  三级分类id查询
        if (null != param.getCatalog3Id()) {
            boolQuery.filter(QueryBuilders.termQuery("catalogId", param.getCatalog3Id()));
        }
        // 1.3、品牌id查询
        if (!CollectionUtils.isEmpty(param.getBrandId())) {
            boolQuery.filter(QueryBuilders.termsQuery("brandId", param.getBrandId()));
        }
        // 1.4 是否有库存
        if (null != param.getHasStock()) {
            boolQuery.filter(QueryBuilders.termQuery("hasStock", param.getHasStock() == 1));
        }
        // 1.5 价格区间
        if (!StringUtils.isEmpty(param.getSkuPrice())) {
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery(param.getSkuPrice());
            String[] rangePrice = param.getSkuPrice().split("_");
            if (rangePrice.length == 2) {
                // gte 是 >=     lte 是 <=
                if (param.getSkuPrice().startsWith("_")) {
                    // 这种情况是 _500
                    rangeQuery.lte(rangePrice[1]);
                } else {
                    // 这种情况是 "100_500"
                    rangeQuery.lte(rangePrice[1]);
                    rangeQuery.gte(rangePrice[0]);
                }
            } else if (rangePrice.length == 1) {
                // 这种情况是 "100_"
                rangeQuery.gte(rangePrice[0]);
            }
        }

        // 1.6、 按照属性过滤
        if (!CollectionUtils.isEmpty(param.getAttrs())) {
            param.getAttrs().stream().forEach(attr -> {
                // attrs=1_5寸:8寸&attrs=2——16G:8G
                BoolQueryBuilder nestedboolQuery = QueryBuilders.boolQuery();
                // attrs=1_5寸:8寸
                String[] s = attr.split("_");
                String attrId = s[0];
                String[] attrValues = s[1].split(":");
                nestedboolQuery.must(QueryBuilders.termQuery("attrs.attrId", attrId));
                nestedboolQuery.must(QueryBuilders.termQuery("attrs.attrValue", attrValues));
                // 每一个必须都生成nested查询
                NestedQueryBuilder attrs = QueryBuilders.nestedQuery("attrs", nestedboolQuery, ScoreMode.None);
                boolQuery.filter(nestedboolQuery);
            });
        }
        // 把所有的条件都拿到进行封装检索
        sourceBuilder.query(boolQuery);

        /**
         *  排序，分页，高亮
         */
        // 排序
        if (!StringUtils.isEmpty(param.getSort())) {
            String[] sortParam = param.getSort().split("_");
            SortOrder order = sortParam[1].equalsIgnoreCase("asc") ? SortOrder.ASC : SortOrder.DESC;
            sourceBuilder.sort(sortParam[0], order);
        }
        // 分页
        if (param.getPageNum() == null) {
            param.setPageNum(1);
        }
        sourceBuilder.from((param.getPageNum() - 1) * EsConstant.PAGE_SIZE);
        sourceBuilder.size(EsConstant.PAGE_SIZE);
        // 高亮
        if (!StringUtils.isEmpty(param.getKeywords())) {
            HighlightBuilder highlightBuilder = new HighlightBuilder();
            highlightBuilder.field("skuTitle");
            highlightBuilder.preTags("<b style='color:red'>");
            highlightBuilder.postTags("</b>");
            sourceBuilder.highlighter(highlightBuilder);
        }

        System.out.println("构建的DSL语句： " + sourceBuilder.toString());

        /**
         *  聚合分析
         */
        // 品牌聚合
        TermsAggregationBuilder brand_agg = AggregationBuilders.terms("brand_agg");
        brand_agg.field("brandId").size(50);
        brand_agg.subAggregation(AggregationBuilders.terms("brand_name_agg").field("brandName").size(1));
        brand_agg.subAggregation(AggregationBuilders.terms("brand_img_agg").field("brandImg").size(1));
        sourceBuilder.aggregation(brand_agg);
        // 分类聚合
        TermsAggregationBuilder catalog_agg = AggregationBuilders.terms("catalog_agg");
        catalog_agg.field("catalogId").size(50);
        catalog_agg.subAggregation(AggregationBuilders.terms("catalog_name_agg").field("catalogName").size(1));
        sourceBuilder.aggregation(catalog_agg);
        // 属性聚合
        NestedAggregationBuilder attr_agg = AggregationBuilders.nested("attr_agg", "attrs");
        NestedAggregationBuilder attr_id_agg = attr_agg.subAggregation(AggregationBuilders.terms("attr_id_agg").field("attrs.attrId").size(1));
        attr_id_agg.subAggregation(AggregationBuilders.terms("attr_name_agg").field("attrs.attrName").size(1));
        attr_id_agg.subAggregation(AggregationBuilders.terms("attr_value_agg").field("attrs.attrName").size(50));
        sourceBuilder.aggregation(attr_agg);

        SearchRequest searchRequest = new SearchRequest(new String[]{EsConstant.PRODUCT_INDEX}, sourceBuilder);
        return searchRequest;
    }

    /**
     * 构建结果数据
     *
     * @param response
     * @return
     */
    private SearchResult buildSearchResult(SearchResponse response, SearchParam param) {

        SearchResult searchResult = new SearchResult();

        SearchHits hits = response.getHits();
        Aggregations aggregations = response.getAggregations();

        // 封装查询到的商品
        List<SkuEsModel> esModelList = new ArrayList<>();
        if (hits.getHits() != null && hits.getHits().length > 0) {
            for (SearchHit hit : hits.getHits()) {
                String sourceAsString = hit.getSourceAsString();
                SkuEsModel skuEsModel = JSON.parseObject(sourceAsString, SkuEsModel.class);
                // 设置关键字高亮
                if (!StringUtils.isEmpty(param.getKeywords())) {
                    HighlightField skuTitle = hit.getHighlightFields().get("skuTitle");
                    String highLightKeyWords = skuTitle.getFragments()[0].string();
                    skuEsModel.setSkuTitle(highLightKeyWords);
                }
                esModelList.add(skuEsModel);
            }
        }
        searchResult.setProducts(esModelList);

        // 封装查询商品结果集聚合结果：涉及到的所有的属性信息
        ParsedLongTerms catalog_agg = aggregations.get("catalog_agg");
        List<SearchResult.CatalogVo> catalogs = new ArrayList<>();
        catalog_agg.getBuckets().forEach(e -> {
            SearchResult.CatalogVo catalogVo = new SearchResult.CatalogVo();
            // 三级分类id
            String catalogId = e.getKeyAsString();
            catalogVo.setCatalogId(Long.parseLong(catalogId));
            // 由于分类名是根据分类id再次聚合的结果，也就是分类id一级的子聚合，需要再次get
            // 分类名
            ParsedStringTerms catalog_name_agg = e.getAggregations().get("catalog_name_agg");
            String catalogName = catalog_name_agg.getBuckets().get(0).getKeyAsString();
            catalogVo.setCatalogName(catalogName);
            catalogs.add(catalogVo);
        });
        searchResult.setCatalogs(catalogs);

        // 封装所有商品聚合到的品牌信息
        List<SearchResult.BrandVo> brands = new ArrayList<>();
        ParsedLongTerms brand_agg = aggregations.get("brand_agg");
        brand_agg.getBuckets().forEach(e -> {
            SearchResult.BrandVo brandVo = new SearchResult.BrandVo();
            // 品牌id
            String brandId = e.getKeyAsString();
            brandVo.setBrandId(Long.parseLong(brandId));
            // 品牌名称
            ParsedStringTerms brand_name_agg = e.getAggregations().get("brand_name_agg");
            String brandName = brand_name_agg.getBuckets().get(0).getKeyAsString();
            brandVo.setBrandName(brandName);
            // 品牌图片
            ParsedStringTerms brand_img_agg = e.getAggregations().get("brand_img_agg");
            String brandImg = brand_img_agg.getBuckets().get(0).getKeyAsString();
            brandVo.setBrandImg(brandImg);
            brands.add(brandVo);
        });
        searchResult.setBrands(brands);

        // 封装所有商品聚合到的属性信息
        List<SearchResult.AttrVo> attrVoList = new ArrayList<>();
        ParsedNested attr_agg = aggregations.get("attr_agg");
        ParsedLongTerms attr_id_agg = attr_agg.getAggregations().get("attr_id_agg");
        attr_id_agg.getBuckets().forEach(e -> {
            SearchResult.AttrVo attrVo = new SearchResult.AttrVo();
            // 属性id
            String attrId = e.getKeyAsString();
            attrVo.setAttrId(Long.parseLong(attrId));
            // 属性名称
            ParsedStringTerms attr_name_agg = e.getAggregations().get("attr_name_agg");
            attrVo.setAttrName(attr_name_agg.getBuckets().get(0).getKeyAsString());
            // 属性值
            ParsedStringTerms attr_value_agg = e.getAggregations().get("attr_value_agg");
//            List<String> attrValueList = new ArrayList<>();
//            attr_value_agg.getBuckets().forEach(v -> attrValueList.add(v.getKeyAsString()));
            List<String> attrValueList = attr_value_agg.getBuckets().stream().map(v -> v.getKeyAsString()).collect(Collectors.toList());
            attrVo.setAttrValue(attrValueList);
            searchResult.getAttrIds().add(Long.parseLong(attrId));
            attrVoList.add(attrVo);
        });
        searchResult.setAttrs(attrVoList);

        // 封装所有商品聚合到的分页信息
        // 当前页
        searchResult.setPageNum(param.getPageNum());
        // 页大小
        long total = hits.getTotalHits().value;
        searchResult.setTotal(total);
        // 总页数
        Integer totalPage = Math.toIntExact(total % EsConstant.PAGE_SIZE == 0 ? total / EsConstant.PAGE_SIZE : total / EsConstant.PAGE_SIZE + 1);
        // 页码列表
        List<Integer> pageNavs = new ArrayList<>();
        for (Integer i = 1; i <= totalPage; i++) {
            pageNavs.add(i);
        }
        searchResult.setPageNavs(pageNavs);

        // 构建面包屑导航功能
        // 属性的面包屑导航
        if (CollectionUtils.isEmpty(param.getAttrs())) {
            List<SearchResult.NavVo> navs = param.getAttrs().stream().map(attr -> {
                SearchResult.NavVo navVo = new SearchResult.NavVo();
                // attrs = 2_5寸:6寸
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
        // 品牌
        if (!CollectionUtils.isEmpty(param.getBrandId())) {
            List<SearchResult.NavVo> navs = searchResult.getNavs();
            SearchResult.NavVo navVo = new SearchResult.NavVo();
            navVo.setNavName("品牌");
            // TODO 远程查询所有品牌
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
        // 设置编码
        String encode = null;
        try {
            encode = URLEncoder.encode(value, "UTF-8");
            encode.replace("+", "");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        // 取消面包屑以后，要跳转到之前的地方
        // 拿到所有的查询条件，去掉当前
        String replace = param.get_queryString().replace("&" + key + "=" + encode, "");
        return replace;
    }
}
