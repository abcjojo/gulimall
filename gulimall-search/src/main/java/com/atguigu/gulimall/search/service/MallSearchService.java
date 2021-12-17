package com.atguigu.gulimall.search.service;

import com.atguigu.gulimall.search.vo.SearchParam;
import com.atguigu.gulimall.search.vo.SearchResult;

public interface MallSearchService {

    /**
     *   检索商品
     * @param param
     * @return
     */
    SearchResult search(SearchParam param);
}
