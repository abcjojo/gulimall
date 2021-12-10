package com.atguigu.gulimall.product.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 *  二级分类Vo
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Catalog2Vo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String catalog1Id;
    private List<Catalog3Vo> catalog3List;
    private String id;
    private String name;

    /**
     *  3级分类
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Catalog3Vo{
        private String id;
        private String name;
        private String catalog2Id;
    }
}
