package com.atguigu.gulimall.product.web;


import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;
import com.atguigu.gulimall.product.vo.Catalog2Vo;
import org.redisson.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Controller
public class IndexController {

    @Autowired
    CategoryService categoryService;
    @Autowired
    RedissonClient redisson;
    @Autowired
    StringRedisTemplate redisTemplate;

    @GetMapping({"/", "/index.html"})
    public String indexPage(Model model) {

        List<CategoryEntity> categoryEntities = categoryService.getLevel1Categorys();
        model.addAttribute("categorys", categoryEntities);

        // 视图解析器进行拼串
        // 默认前缀 classpath:/templates     默认后缀  .html
        return "index";
    }

    /**
     * @return
     */
    @ResponseBody
    @GetMapping("/index/catalog.json")
    public Map<String, List<Catalog2Vo>> getCatalogJson() {
        Map<String, List<Catalog2Vo>> map = categoryService.getCatalogJson();
        return map;
    }

    @ResponseBody
    @GetMapping("/hello")
    public String hello() {
        RLock lock = redisson.getLock("my-lock");
        /**
         *    redisson 有一个看门狗：即锁的自动续期，如果业务超长，运行期间自动给锁续上新的30s，不用担心业务时间长，锁自动过期被删除
         *      加锁业务只要完成，就不会给当前锁续期，即使不手动解锁，锁也默认在30s以后自动删除（该观点待定）
         */

        /**
         *      lock() 和 lock(long var1, TimeUnit var3) 区别：手动指定过期时间的lock()没有看门狗机制，不指定过期时间的lock方法有看门狗机制
         *          1、如果传递了锁的超时时间，就发送给redis执行脚本，进行抢占锁操作，默认的超时时间就是我们指定的时间
         *          2、如果我们没有指定锁的超时时间，就是用 30 * 1000【lockedWatchDogTimeout看门狗的默认时间】，然后也是去抢占锁；
         *              只要抢占锁成功，就会启动一个定时任务【重新给锁设置过期时间，新的过期时间就是看门狗的默认时间】，每隔10s就会自动续期，续成30s
         */

        // TODO 最佳实战  推荐使用 lock.lock(30, TimeUnit.SECONDS); 这种方式 指定默认时间，省掉了自动续期的过程，而且业务执行完后手动解锁，设置一个稍大一点的过期时间如果
        //      这个过期时间到期后业务还没执行完，说明业务需要优化

//        lock.lock();       // 阻塞式等待 默认加锁时间30s
        // 设置自动解锁时间，10s过后自动解锁不再自动续期，  问题：如果自动解锁时间到了之后，业务没有执行完，下个线程进来后就会加锁，当前线程执行完后手动解锁会产生解不到锁的异常
        lock.lock(10, TimeUnit.SECONDS);        // 10s后自动解锁，自动解锁时间一定要大于业务的执行时间
        try {
            System.out.println("加锁成功，执行业务------" + Thread.currentThread().getId());
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {

            //
            System.out.println("释放锁-----" + Thread.currentThread().getId());
            lock.unlock();
        }
        return "hello";
    }

    /**
     * 读写锁： 保证一定能读到最新数据，修改数据期间，写锁是一个排它锁（互斥锁）； 读锁是共享锁
     * 写锁没释放就必须等待
     * <p>
     * 读 + 读 ： 相当于无锁，所有线程都可以访问资源执行业务；并发读，只会在redis中记录好所有的读锁，他们都会同时加锁成功
     * 写 + 读 ： 写线程先拿到读写锁，读线程需要等到写线程释放锁后才能获取锁
     * 写 + 写 ： 阻塞模式，其他写线程需要等到锁释放才能抢占锁
     * 读 + 写 ： 有读锁，写也需要等待，写线程需要等待读线程释放完读写锁才能去拿锁，
     * 只要有写的存在都会存在等待
     */
    @ResponseBody
    @GetMapping("/write")
    public String writeValue() {

        // 读写锁的使用场景 读数据加读锁，写数据加写锁
        RReadWriteLock lock = redisson.getReadWriteLock("rw-lock");
        RLock rLock = lock.writeLock();
        rLock.lock();
        String s = "";
        try {
            System.out.println("写锁加锁成功" + Thread.currentThread().getId());
            s = UUID.randomUUID().toString();
            Thread.sleep(15000);
            redisTemplate.opsForValue().set("writeValue", s);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            System.out.println("写锁释放成功" + Thread.currentThread().getId());
            rLock.unlock();
        }
        return s;
    }

    @ResponseBody
    @GetMapping("/read")
    public String readValue() {

        RReadWriteLock lock = redisson.getReadWriteLock("rw-lock");
        RLock rLock = lock.readLock();
        rLock.lock();
        String s = "";
        try {
            System.out.println("读锁加锁成功" + Thread.currentThread().getId());
            Thread.sleep(10000);
            s = redisTemplate.opsForValue().get("writeValue");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("读锁释放成功" + Thread.currentThread().getId());
            rLock.unlock();
        }
        return s;
    }


    /**
     * redisson 信号量demo
     * 车库停车 共有3个车位
     *
     * 信号量可以用来作分布式系统限流
     */
    @ResponseBody
    @GetMapping("/park")
    public String park() throws InterruptedException {
        RSemaphore park = redisson.getSemaphore("park");       //
//        park.acquire();     // 获取一个信号 ，获取一个值，， 占一个车位
        boolean b = park.tryAcquire();
        return "ok => " + b;
    }

    @ResponseBody
    @GetMapping("/go")
    public String go() throws InterruptedException {
        RSemaphore park = redisson.getSemaphore("park");
        park.release();

        return "ok";
    }

    /**
     *    闭锁demo
     *      放假了，锁校门，只有所有班级人都走完了，才能缩校门去 演示5个班级
     */
    @ResponseBody
    @GetMapping("/lockDoor")
    public String lockDoor() throws InterruptedException {
        RCountDownLatch door = redisson.getCountDownLatch("door");
        door.trySetCount(5);   // 设置需要等待的班级数量为5
        door.await();    // 等待闭锁都完成
        return "放假了。。。";
    }

    @ResponseBody
    @GetMapping("/gogogo/{id}")
    public String gogogo(@PathVariable("id") Long id) {
        RCountDownLatch door = redisson.getCountDownLatch("door");
        door.countDown();  // 计数减一
        return id + "班级的人都走了。。。";
    }

}
