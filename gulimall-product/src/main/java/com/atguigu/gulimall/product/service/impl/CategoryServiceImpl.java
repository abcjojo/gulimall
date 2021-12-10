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
    // 数据一旦被修改 就删除缓存  缓存失效模式的使用
//    @CacheEvict(value = "category", key = "'getLevel1Categorys'")
    // 同时进行多种缓存操作
//    @Caching(evict = {
//            @CacheEvict(value = "category", key = "'getLevel1Categorys'"),
//            @CacheEvict(value = "category", key = "'getCatalogJson'")
//    })
    // 删除category分区下所有的缓存
    @CacheEvict(value = "category", allEntries = true)      // 失效模式使用 数据修改后 删除缓存
//    @CachePut    // 双写模式使用 将修改后数据的返回值放进缓存中
    @Transactional
    @Override
    public void updateCascade(CategoryEntity category) {
        this.updateById(category);
        categoryBrandRelationService.updateCategory(category.getCatId(), category.getName());
    }

    /**
     *      一般约定：缓存同一类型的数据，都指定在同一分区下
     */
    /**
     * 查询一级分类
     *
     * @return
     */
    // 每一个需要缓存的数据，我们都来指定要放在哪个名字的缓存【就是指定缓存放在哪个分区下（一般分区按照业务类型划分）】
    // 代表当前方法的结果需要缓存，如果缓存中有，方法不需要调用，如果没有，调用方法，最后将结果放入缓存
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

        return parent_cid;
    }

    /**
     * TODO 产生堆外内存溢出 OutOfDirectMemoryError
     * 1、springboot2.0之后默认使用lettuce作为操作redis的客户端，他使用netty进行网络通信
     * 2、lettuce的bug导致netty堆外内存溢出  -Xmx300m； netty如果没有指定堆外内存，默认使用 -Xmx300m
     * 可以通过 -Dio.netty.maxDirectMemory进行设置
     * <p>
     * 解决方案： 不能使用 -Dio.netty.maxDirectMemory 只去调节最大堆外内存
     * <p>
     * 1、升级lettuce客户端， 2、使用jedis
     * <p>
     * lettuce 和 jedis 都是操作redis的底层客户端， Spring再次对两者进行封装redisTemplate，兼容lettuce和jedis，
     */

//    @Override
    public Map<String, List<Catalog2Vo>> getCatalogJson2() {
        String localPort = "";
        try {
            localPort = NetworkUtils.getLocalPort();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 给缓存中放json字符串，拿出的json字符串，逆转为能用的队形类型【序列化与反序列化】
        // 加入缓存逻辑，先查缓存中是否命中，命中直接返回数据，未命中时从数据库查询数据，查出的数据放入redis中，然后返回数据
        // json跨语言，跨平台兼容 在redis中的对象存储一般为json格式的字符串
        String catalogJSON = redisTemplate.opsForValue().get("catalogJSON");
        if (StringUtils.isEmpty(catalogJSON)) {
            System.out.println("端口号：" + localPort + "----缓存未命中-----查询MySQL数据库");
            Map<String, List<Catalog2Vo>> catalogJsonFromDb = getCatalogJsonFromDbWithRedisLock();
            return catalogJsonFromDb;
        }

        System.out.println("端口号：" + localPort + "----缓存命中-----查询redis");
        Map<String, List<Catalog2Vo>> result = JSON.parseObject(catalogJSON, new TypeReference<Map<String, List<Catalog2Vo>>>() {
        });
        return result;

    }

    public Map<String, List<Catalog2Vo>> getCatalogJsonFromDbWithRedissonLock() {

        // 锁的名字  锁的粒度越细，运行越快
        // 说的粒度：具体缓存的是某个数据， 11号商品：product-11-lock，， 12号： product-12-lock；这样设计就会避免12号商品失效，而影响11号商品
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


    // 使用redis分布式锁保证只有一个线程能够拿到锁并操作数据库
    // 使用redis做分布式锁的两个核心，加锁的过程保证原子性，解锁的过程保证原子性
    public Map<String, List<Catalog2Vo>> getCatalogJsonFromDbWithRedisLock() {

        String uuid = UUID.randomUUID().toString().replace("-", "");
        // 使用分布式锁可以避免本地锁出现的问题，redis的set nx 可以实现分布式锁
        // redis 命令： set lock 123 EX 300 NX   设置过期时间为300秒的k-v，如果redis中存在key，返回false，不进行操作，如果没有该key，就添加k-v，返回true
        Boolean lock = redisTemplate.opsForValue().setIfAbsent("lock", uuid, 300, TimeUnit.SECONDS);
        if (lock) {
            System.out.println("获取分布式锁成功");
            /**
             * TODO 死锁问题：发生场景：
             *      1、getCatalogJsonFromDb() 里的代码抛出了异常导致程序终止执行，redis的delete操作没有被执行；
             *      2、程序执行到redis.delete 之前机器宕机
             *   各种原因导致lock键值没有被删除, 其他线程依然会停止并等待，不断地去抢占这个锁，就会产生死锁问题
             */
            // 为了防止死锁的发生，需要在执行业务代码之前设置一个自动过期时间
//            // 加锁和设置过期时间不是原子操作，中间还是有时间间隙，在这个间隙出现宕机还是会出现死锁，所以设置过期时间应该和加锁放在同一个操作中
//            redisTemplate.expire("lock", 30, TimeUnit.SECONDS);
            // 抢占锁成功 查询mysql库，执行业务
            Map<String, List<Catalog2Vo>> dataFromDb;
            try {
                dataFromDb = getCatalogJsonFromDb();
            } finally {
                /**
                 *  TODO  问题场景1：业务代码执行超时，比如过期时间为30s，代码执行了40s，key已经自动过期，等到redis调用delete方法时，会删除一个空值（小问题）
                 *        问题场景2：依然是业务代码超时，key自动过期时间为10s，业务代码执行时间为40s，线程1执行到10s时候线程1的key自动过期，这时线程2进来发现key不存在，成功set key
                 *                并拿到锁，在20s时线程3拿到锁，40s时线程4拿到锁，这时线程1来delete这个key，就会删掉不属于自己的key，释放掉所有的锁，导致其余线程来抢占锁，
                 *                从而导致锁失效的问题-没锁住
                 *        解决方式：使用UUID作为value，删除key之前先拿到value，根据uuid判断这个key是否属于当前的线程所有，但是又会产生新的问题，如果redis.get()方法获取value的
                 *                时候get和delete不是原子操作，会有时隙，这个时隙就可能产生get到的value过期了，其他线程又set了新的value，所以delete也应当和set的操作一样，保证原子性，
                 *                保证原子性的操作可以使用redis官方提供的方法：使用lua脚本。
                 */

                String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
                // 调用脚本删除锁
                Long lock1 = redisTemplate.execute(new DefaultRedisScript<Long>(script, Long.class), Arrays.asList("lock"), uuid);

//                String lockValue = redisTemplate.opsForValue().get("lock");
//                if (uuid.equals(lockValue)){
//                    // 删除当前线程自己的锁
//                    redisTemplate.delete("lock");
//                }
            }
            return dataFromDb;
        } else {
            // 加锁失败  重试（自旋）
            // 休眠100ms重试
            System.out.println("获取分布式锁失败。。等待重试");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return getCatalogJsonFromDbWithRedisLock();
        }
    }

    // 查mysql库
    private Map<String, List<Catalog2Vo>> getCatalogJsonFromDb() {
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

    // 从数据库查询并封装分类数据
    public Map<String, List<Catalog2Vo>> getCatalogJsonFromDbWithLocalLock() {

        // 这种加锁方式适合单体应用  本地锁，在分布式环境中，只能锁本机的服务不能锁其他机器的服务，所以在分布式环境中应当使用分布式锁。
        synchronized (this) {

            // 加锁后，所有线程竞争锁，只有一个能拿到锁，其他线程排队等待，拿到锁的线程执行完后将结果放入缓存，剩下的线程依然会竞争拿到锁后执行同步代码块
            //   所以为了防止其他线程都去查库，所有线程进入代码块后先确认缓存中有没有数据，有就直接返回，没有再去查库
            return getCatalogJsonFromDb();
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