package com.atguigu.gulimall.product.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.utils.NetworkUtils;
import com.atguigu.gulimall.product.service.CategoryBrandRelationService;
import com.atguigu.gulimall.product.vo.Catalog2Vo;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.atguigu.gulimall.product.dao.CategoryDao;
import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;

import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service("categoryService")
@Slf4j
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, CategoryEntity> implements CategoryService {

    @Autowired
    private CategoryBrandRelationService categoryBrandRelationService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RedissonClient redisson;


    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryEntity> page = this.page(
                new Query<CategoryEntity>().getPage(params),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageUtils(page);
    }


    /**
     * ???????????????????????????????????????????????????????????????
     */
    @Override
    public List<CategoryEntity> listWithTree() {

        // 1?????????????????????
        List<CategoryEntity> categoryEntities = baseMapper.selectList(null);

        // 2?????????????????????????????????
        List<CategoryEntity> level1Menus = categoryEntities.stream()
                .filter(e -> e.getParentCid() == 0)
                .map(menu -> {
                    // ??????????????????????????????????????????
                    menu.setChildren(getCateGoryChildrens(menu, categoryEntities));
                    return menu;
                })
                .sorted((menu1, menu2) -> (menu1.getSort() == null ? 0 : menu1.getSort()) - (menu2.getSort() == null ? 0 : menu2.getSort()))
                .collect(Collectors.toList());

        return categoryEntities;
    }


    // ??????????????????????????????????????????????????????????????????????????????
    private List<CategoryEntity> getCateGoryChildrens(CategoryEntity root, List<CategoryEntity> all) {
        List<CategoryEntity> children = all.stream()
                .filter(menu -> Objects.equals(menu.getParentCid(), root.getCatId()))
                .map(menu -> {
                    // ????????????????????????????????????
                    menu.setChildren(getCateGoryChildrens(menu, all));
                    return menu;
                })
                .sorted((menu1, menu2) -> (menu1.getSort() == null ? 0 : menu1.getSort()) - (menu2.getSort() == null ? 0 : menu2.getSort()))
                .collect(Collectors.toList());
        return children;
    }

    @Override
    public void removeMenuByIds(List<Long> asList) {
        // TODO  1????????????????????????????????????????????????????????????

        baseMapper.deleteBatchIds(asList);
    }

    @Override
    public Long[] findCatelogPath(Long cateLogId) {

        List<Long> paths = new ArrayList<>();
        List<Long> parentPath = findParentPath(cateLogId, paths);
        Collections.reverse(parentPath);
        return parentPath.toArray(new Long[parentPath.size()]);
    }

    /**
     * ????????????????????????
     *
     * @param category
     */
    // ????????????????????? ???????????????  ???????????????????????????
//    @CacheEvict(value = "category", key = "'getLevel1Categorys'")
    // ??????????????????????????????
//    @Caching(evict = {
//            @CacheEvict(value = "category", key = "'getLevel1Categorys'"),
//            @CacheEvict(value = "category", key = "'getCatalogJson'")
//    })
    // ??????category????????????????????????
    @CacheEvict(value = "category", allEntries = true)      // ?????????????????? ??????????????? ????????????
//    @CachePut    // ?????????????????? ?????????????????????????????????????????????
    @Transactional
    @Override
    public void updateCascade(CategoryEntity category) {
        this.updateById(category);
        categoryBrandRelationService.updateCategory(category.getCatId(), category.getName());
    }

    /**
     *      ????????????????????????????????????????????????????????????????????????
     */
    /**
     * ??????????????????
     *
     * @return
     */
    // ????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
    // ????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
//    @Cacheable(value = {"category"}, key = "'level1Categorys'")
    @Cacheable(value = {"category"}, key = "#root.methodName")
    @Override
    public List<CategoryEntity>  getLevel1Categorys() {
        System.out.println("getLevel1Categorys....");
        List<CategoryEntity> categoryEntities = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", 0));
        return categoryEntities;
    }


    @Cacheable(value = "category", key = "#root.method.name")
    @Override
    public Map<String, List<Catalog2Vo>> getCatalogJson() {

        System.out.println("?????????????????????????????????");

        /**
         * ?????????????????????????????????
         */
        List<CategoryEntity> selectList = baseMapper.selectList(null);

        // 1???????????????1?????????
        List<CategoryEntity> level1Categorys = getParentCid(selectList, 0L);

        // ????????????
        Map<String, List<Catalog2Vo>> parent_cid = level1Categorys.stream().collect(Collectors.toMap(k -> k.getCatId().toString(), v -> {
            List<CategoryEntity> categoryEntities = getParentCid(selectList, v.getCatId());
            List<Catalog2Vo> catalog2Vos = null;
            if (categoryEntities != null) {
                catalog2Vos = categoryEntities.stream().map(item -> {
                    Catalog2Vo catalog2Vo = new Catalog2Vo(v.getCatId().toString(), null, item.getCatId().toString(), item.getName());

                    // ????????????????????????????????????????????????VO
                    List<CategoryEntity> level3Catalog = getParentCid(selectList, item.getCatId());
                    if (level3Catalog != null) {
                        List<Catalog2Vo.Catalog3Vo> collect = level3Catalog.stream().map(l3 -> {
                            Catalog2Vo.Catalog3Vo catalog3Vo = new Catalog2Vo.Catalog3Vo(item.getCatId().toString(), l3.getName(), l3.getCatId().toString());
                            return catalog3Vo;
                        }).collect(Collectors.toList());
                        catalog2Vo.setCatalog3List(collect);
                    }
                    return catalog2Vo;
                }).collect(Collectors.toList());
            }
            return catalog2Vos;
        }));

        return parent_cid;
    }

    @Override
    public Long[] findCatalogPath(Long catelogId) {
        List<Long> paths = new ArrayList<>();
        List<Long> parentPath = findParentPath(catelogId, paths);

        Collections.reverse(parentPath);
        return parentPath.toArray(new Long[parentPath.size()]);
    }

    /**
     * TODO ???????????????????????? OutOfDirectMemoryError
     * 1???springboot2.0??????????????????lettuce????????????redis????????????????????????netty??????????????????
     * 2???lettuce???bug??????netty??????????????????  -Xmx300m??? netty????????????????????????????????????????????? -Xmx300m
     * ???????????? -Dio.netty.maxDirectMemory????????????
     * <p>
     * ??????????????? ???????????? -Dio.netty.maxDirectMemory ??????????????????????????????
     * <p>
     * 1?????????lettuce???????????? 2?????????jedis
     * <p>
     * lettuce ??? jedis ????????????redis????????????????????? Spring???????????????????????????redisTemplate?????????lettuce???jedis???
     */

//    @Override
    public Map<String, List<Catalog2Vo>> getCatalogJson2() {
        String localPort = "";
        try {
            localPort = NetworkUtils.getLocalPort();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // ???????????????json?????????????????????json????????????????????????????????????????????????????????????????????????
        // ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????redis????????????????????????
        // json??????????????????????????? ???redis???????????????????????????json??????????????????
        String catalogJSON = redisTemplate.opsForValue().get("catalogJSON");
        if (StringUtils.isEmpty(catalogJSON)) {
            System.out.println("????????????" + localPort + "----???????????????-----??????MySQL?????????");
            Map<String, List<Catalog2Vo>> catalogJsonFromDb = getCatalogJsonFromDbWithRedisLock();
            return catalogJsonFromDb;
        }

        System.out.println("????????????" + localPort + "----????????????-----??????redis");
        Map<String, List<Catalog2Vo>> result = JSON.parseObject(catalogJSON, new TypeReference<Map<String, List<Catalog2Vo>>>() {
        });
        return result;

    }

    public Map<String, List<Catalog2Vo>> getCatalogJsonFromDbWithRedissonLock() {

        // ????????????  ?????????????????????????????????
        // ???????????????????????????????????????????????? 11????????????product-11-lock?????? 12?????? product-12-lock???????????????????????????12???????????????????????????11?????????
        RLock lock = redisson.getLock("catalogJson-lock");
        lock.lock();

        Map<String, List<Catalog2Vo>> dataFromDb;
        try {
            dataFromDb = getCatalogJsonFromDb();
        } finally {
            lock.unlock();
        }
        return dataFromDb;
    }


    // ??????redis?????????????????????????????????????????????????????????????????????
    // ??????redis????????????????????????????????????????????????????????????????????????????????????????????????
    public Map<String, List<Catalog2Vo>> getCatalogJsonFromDbWithRedisLock() {

        String uuid = UUID.randomUUID().toString().replace("-", "");
        // ?????????????????????????????????????????????????????????redis???set nx ????????????????????????
        // redis ????????? set lock 123 EX 300 NX   ?????????????????????300??????k-v?????????redis?????????key?????????false????????????????????????????????????key????????????k-v?????????true
        Boolean lock = redisTemplate.opsForValue().setIfAbsent("lock", uuid, 300, TimeUnit.SECONDS);
        if (lock) {
            System.out.println("????????????????????????");
            /**
             * TODO ??????????????????????????????
             *      1???getCatalogJsonFromDb() ??????????????????????????????????????????????????????redis???delete????????????????????????
             *      2??????????????????redis.delete ??????????????????
             *   ??????????????????lock?????????????????????, ?????????????????????????????????????????????????????????????????????????????????????????????
             */
            // ?????????????????????????????????????????????????????????????????????????????????????????????
//            // ????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
//            redisTemplate.expire("lock", 30, TimeUnit.SECONDS);
            // ??????????????? ??????mysql??????????????????
            Map<String, List<Catalog2Vo>> dataFromDb;
            try {
                dataFromDb = getCatalogJsonFromDb();
            } finally {
                /**
                 *  TODO  ????????????1???????????????????????????????????????????????????30s??????????????????40s???key???????????????????????????redis??????delete????????????????????????????????????????????????
                 *        ????????????2?????????????????????????????????key?????????????????????10s??????????????????????????????40s?????????1?????????10s????????????1???key???????????????????????????2????????????key??????????????????set key
                 *                ??????????????????20s?????????3????????????40s?????????4????????????????????????1???delete??????key?????????????????????????????????key????????????????????????????????????????????????????????????
                 *                ??????????????????????????????-?????????
                 *        ?????????????????????UUID??????value?????????key???????????????value?????????uuid????????????key???????????????????????????????????????????????????????????????????????????redis.get()????????????value???
                 *                ??????get???delete???????????????????????????????????????????????????????????????get??????value???????????????????????????set?????????value?????????delete????????????set????????????????????????????????????
                 *                ????????????????????????????????????redis??????????????????????????????lua?????????
                 */

                String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
                // ?????????????????????
                Long lock1 = redisTemplate.execute(new DefaultRedisScript<Long>(script, Long.class), Arrays.asList("lock"), uuid);

//                String lockValue = redisTemplate.opsForValue().get("lock");
//                if (uuid.equals(lockValue)){
//                    // ??????????????????????????????
//                    redisTemplate.delete("lock");
//                }
            }
            return dataFromDb;
        } else {
            // ????????????  ??????????????????
            // ??????100ms??????
            System.out.println("??????????????????????????????????????????");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return getCatalogJsonFromDbWithRedisLock();
        }
    }

    // ???mysql???
    private Map<String, List<Catalog2Vo>> getCatalogJsonFromDb() {
        // ???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
        //   ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
        String catalogJson = redisTemplate.opsForValue().get("catalogJSON");
        if (!StringUtils.isEmpty(catalogJson)) {
            Map<String, List<Catalog2Vo>> res = JSON.parseObject(catalogJson, new TypeReference<Map<String, List<Catalog2Vo>>>() {
            });
            return res;
        }

        System.out.println("?????????????????????????????????");

        /**
         * ?????????????????????????????????
         */
        List<CategoryEntity> selectList = baseMapper.selectList(null);

        // 1???????????????1?????????
        List<CategoryEntity> level1Categorys = getParentCid(selectList, 0L);

        // ????????????
        Map<String, List<Catalog2Vo>> parent_cid = level1Categorys.stream().collect(Collectors.toMap(k -> k.getCatId().toString(), v -> {
            List<CategoryEntity> categoryEntities = getParentCid(selectList, v.getCatId());
            List<Catalog2Vo> catalog2Vos = null;
            if (categoryEntities != null) {
                catalog2Vos = categoryEntities.stream().map(item -> {
                    Catalog2Vo catalog2Vo = new Catalog2Vo(v.getCatId().toString(), null, item.getCatId().toString(), item.getName());

                    // ????????????????????????????????????????????????VO
                    List<CategoryEntity> level3Catalog = getParentCid(selectList, item.getCatId());
                    if (level3Catalog != null) {
                        List<Catalog2Vo.Catalog3Vo> collect = level3Catalog.stream().map(l3 -> {
                            Catalog2Vo.Catalog3Vo catalog3Vo = new Catalog2Vo.Catalog3Vo(item.getCatId().toString(), l3.getName(), l3.getCatId().toString());
                            return catalog3Vo;
                        }).collect(Collectors.toList());
                        catalog2Vo.setCatalog3List(collect);
                    }
                    return catalog2Vo;
                }).collect(Collectors.toList());
            }
            return catalog2Vos;
        }));

        // ??????????????????????????????redis??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????redis???????????????
        //   ??????????????????redis????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????redis????????????????????????????????????????????????????????????
        //   ?????????????????????????????????????????????????????????????????????redis?????????????????????????????????mysql???  ????????????????????????????????????redis????????????????????????????????????
        String s = JSON.toJSONString(parent_cid);
        redisTemplate.opsForValue().set("catalogJSON", s);

        return parent_cid;
    }

    // ???????????????????????????????????????
    public Map<String, List<Catalog2Vo>> getCatalogJsonFromDbWithLocalLock() {

        // ????????????????????????????????????  ???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
        synchronized (this) {

            // ???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
            //   ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
            return getCatalogJsonFromDb();
        }


    }

    private List<CategoryEntity> getParentCid(List<CategoryEntity> selectList, Long parent_cid) {
//        return baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", v.getCatId()));
        return selectList.stream().filter(e -> e.getParentCid().equals(parent_cid)).collect(Collectors.toList());
    }

    // ????????????parentId
    private List<Long> findParentPath(Long catelogId, List<Long> paths) {

        // ??????????????????id
        paths.add(catelogId);
        CategoryEntity byId = this.getById(catelogId);
        if (byId.getParentCid() != 0) {
            findParentPath(byId.getParentCid(), paths);
        }
        return paths;
    }

}