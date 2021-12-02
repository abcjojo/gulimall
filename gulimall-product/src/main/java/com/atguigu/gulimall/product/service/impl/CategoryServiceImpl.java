package com.atguigu.gulimall.product.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.gulimall.product.service.CategoryBrandRelationService;
import com.atguigu.gulimall.product.vo.Catalog2Vo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
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


    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryEntity> page = this.page(
                new Query<CategoryEntity>().getPage(params),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageUtils(page);
    }


    /**
     * 查询所有分类以及子分类，以树形结构组装起来
     */
    @Override
    public List<CategoryEntity> listWithTree() {

        // 1、查出所有分类
        List<CategoryEntity> categoryEntities = baseMapper.selectList(null);

        // 2、组装成父子的树形结构
        List<CategoryEntity> level1Menus = categoryEntities.stream()
                .filter(e -> e.getParentCid() == 0)
                .map(menu -> {
                    // 查询封装所有一级菜单的子菜单
                    menu.setChildren(getCateGoryChildrens(menu, categoryEntities));
                    return menu;
                })
                .sorted((menu1, menu2) -> (menu1.getSort() == null ? 0 : menu1.getSort()) - (menu2.getSort() == null ? 0 : menu2.getSort()))
                .collect(Collectors.toList());

        return categoryEntities;
    }


    // 传入当前菜单和所有菜单数据，返回当前菜单的所有子菜单
    private List<CategoryEntity> getCateGoryChildrens(CategoryEntity root, List<CategoryEntity> all) {
        List<CategoryEntity> children = all.stream()
                .filter(menu -> Objects.equals(menu.getParentCid(), root.getCatId()))
                .map(menu -> {
                    // 递归查询当前菜单的子菜单
                    menu.setChildren(getCateGoryChildrens(menu, all));
                    return menu;
                })
                .sorted((menu1, menu2) -> (menu1.getSort() == null ? 0 : menu1.getSort()) - (menu2.getSort() == null ? 0 : menu2.getSort()))
                .collect(Collectors.toList());
        return children;
    }

    @Override
    public void removeMenuByIds(List<Long> asList) {
        // TODO  1、检查当前删除的菜单，是否被别的地方引用

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
     * 级联更新所有数据
     *
     * @param category
     */
    @Transactional
    @Override
    public void updateCascade(CategoryEntity category) {
        this.updateById(category);
        categoryBrandRelationService.updateCategory(category.getCatId(), category.getName());
    }

    /**
     * 查询一级分类
     *
     * @return
     */
    @Override
    public List<CategoryEntity> getLevel1Categorys() {
        List<CategoryEntity> categoryEntities = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", 0));
        return categoryEntities;
    }

    /**
     *   TODO 产生堆外内存溢出 OutOfDirectMemoryError
     *   1、springboot2.0之后默认使用lettuce作为操作redis的客户端，他使用netty进行网络通信
     *   2、lettuce的bug导致netty堆外内存溢出  -Xmx300m； netty如果没有指定堆外内存，默认使用 -Xmx300m
     *      可以通过 -Dio.netty.maxDirectMemory进行设置
     *
     *   解决方案： 不能使用 -Dio.netty.maxDirectMemory 只去调节最大堆外内存
     *
     *   1、升级lettuce客户端， 2、使用jedis
     *
     *   lettuce 和 jedis 都是操作redis的底层客户端， Spring再次对两者进行封装redisTemplate，兼容lettuce和jedis，
     *
     */
    @Override
    public Map<String, List<Catalog2Vo>> getCatalogJson() {
        // 给缓存中放json字符串，拿出的json字符串，逆转为能用的队形类型【序列化与反序列化】
        // 加入缓存逻辑，先查缓存中是否命中，命中直接返回数据，未命中时从数据库查询数据，查出的数据放入redis中，然后返回数据
        // json跨语言，跨平台兼容 在redis中的对象存储一般为json格式的字符串
        String catalogJSON = redisTemplate.opsForValue().get("catalogJSON");
        if (StringUtils.isEmpty(catalogJSON)) {
            System.out.println("缓存未命中-----查询MySQL数据库");
            Map<String, List<Catalog2Vo>> catalogJsonFromDb = getCatalogJsonFromDb();
            return catalogJsonFromDb;
        }

        System.out.println("缓存命中-----查询redis");
        Map<String, List<Catalog2Vo>> result = JSON.parseObject(catalogJSON, new TypeReference<Map<String, List<Catalog2Vo>>>() {});
        return result;

    }

    // 从数据库查询并封装分类数据
    public Map<String, List<Catalog2Vo>> getCatalogJsonFromDb() {

        // 这种加锁方式适合单体应用  本地锁，在分布式环境中，只能锁本机的服务不能锁其他机器的服务，所以在分布式环境中应当使用分布式锁。
        synchronized(this) {

            // 加锁后，所有线程竞争锁，只有一个能拿到锁，其他线程排队等待，拿到锁的线程执行完后将结果放入缓存，剩下的线程依然会竞争拿到锁后执行同步代码块
            //   所以为了防止其他线程都去查库，所有线程进入代码块后先确认缓存中有没有数据，有就直接返回，没有再去查库
            String catalogJson = redisTemplate.opsForValue().get("catalogJSON");
            if (!StringUtils.isEmpty(catalogJson)) {
                Map<String, List<Catalog2Vo>> res = JSON.parseObject(catalogJson, new TypeReference<Map<String, List<Catalog2Vo>>>() {
                });
                return res;
            }

            System.out.println("查询了数据库。。。。。");

            /**
             * 循环查询优化为一次查询
             */
            List<CategoryEntity> selectList = baseMapper.selectList(null);

            // 1、查出所有1级分类
            List<CategoryEntity> level1Categorys = getParentCid(selectList, 0L);

            // 封装数据
            Map<String, List<Catalog2Vo>> parent_cid = level1Categorys.stream().collect(Collectors.toMap(k -> k.getCatId().toString(), v -> {
                List<CategoryEntity> categoryEntities = getParentCid(selectList, v.getCatId());
                List<Catalog2Vo> catalog2Vos = null;
                if (categoryEntities != null) {
                    catalog2Vos = categoryEntities.stream().map(item -> {
                        Catalog2Vo catalog2Vo = new Catalog2Vo(v.getCatId().toString(), null, item.getCatId().toString(), item.getName());

                        // 找到当前二级分类的三级分类封装成VO
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

            // 数据库查询结果集放入redis的过程应该和首次查库的操作放在同一把锁中，不然会产生锁的时序错乱问题：即第一个线程查完数据后释放锁，然后再往redis中添加缓存
            //   但是往中间件redis填充数据是需要网络交互的，会有时间间隙，这个时间间隙中，下一个线程已经拿到了锁，然后去查redis确实也没有对应的数据，就会产生第二个线程
            //   也去查库的情况，知道第一个线程将数据成功放入到redis后，之后的线程才不会查mysql。  所以应将查库和数据集放入redis放在同一个同步代码块中。
            String s = JSON.toJSONString(parent_cid);
            redisTemplate.opsForValue().set("catalogJSON", s);

            return parent_cid;
        }


    }

    private List<CategoryEntity> getParentCid(List<CategoryEntity> selectList, Long parent_cid) {
//        return baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", v.getCatId()));
        return selectList.stream().filter(e -> e.getParentCid().equals(parent_cid)).collect(Collectors.toList());
    }

    // 递归查找parentId
    private List<Long> findParentPath(Long catelogId, List<Long> paths) {

        // 收集当前节点id
        paths.add(catelogId);
        CategoryEntity byId = this.getById(catelogId);
        if (byId.getParentCid() != 0) {
            findParentPath(byId.getParentCid(), paths);
        }
        return paths;
    }

}