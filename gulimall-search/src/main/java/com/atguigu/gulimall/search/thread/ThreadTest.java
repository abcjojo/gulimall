package com.atguigu.gulimall.search.thread;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

public class ThreadTest {

    // 固定线程数为10的线程池
    public static ExecutorService execute = Executors.newFixedThreadPool(10);


    public static void main(String[] args) throws ExecutionException, InterruptedException {
        System.out.println("main---start");
        // 使用自定义的线程池，执行异步任务

//        // runAsync 无返回值的异步任务
//        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
//            // 异步执行的具体业务
//            System.out.println("Thread01当前异步线程：" + Thread.currentThread().getId());
//            System.out.println("Thread05:" + (10 / 5));
//        }, execute);

        // supplyAsync 有返回值的异步任务
        CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
            // 异步执行的具体业务
            int i;
            System.out.println("Thread01当前异步线程：" + Thread.currentThread().getId());
            System.out.println("Thread05:" + (i = (10 / 5)));
            return i;
        }, execute);

        System.out.println("mian---end" + future.get());
    }

    public static void thread(String[] args) throws ExecutionException, InterruptedException {
        /**
         *  实现多线程的4种方式 1-new Thread  2-Runnable  3-Callable   4-线程池
         *      区别：
         *          a、1,2方式没有返回值，3有返回值需要配合FutureTask.get() 方法获取返回值
         *          b、1,2,3,三种方式都不能控制资源， 第四种线程池可以控制资源（好处是：系统性能稳定，不会出现因为资源耗尽问题导致系统崩溃）
         *
         *      线程池的创建：
         *          原生线程池的创建：new ThreadPoolExecutor();
         *              参数：corePoolSize：核心线程数量【会一直存在，除非设置allowCoreThreadTimeOut】； 线程池创建好以后，就等待着接受异步任务然后去执行
         *                   maximumPoolSize：最大线程数量；用来控制资源
         *                   keepAliveTime：非核心空闲线程指定存活时间；  如果当前线程数量大于core线程数量，并且这些空闲的线程存活时间超过指定时间，就会释
         *                         放掉超出核心线程数量的线程
         *                   TimeUnit： 时间单位
         *                   BlockingQueue<Runnable>： 阻塞队列。如果任务有很多，就会将目前多的任务放在队列里面
         *                   ThreadFactory threadFactory： 线程的创建工厂
         *                   RejectedExecutionHandler handler： 如果队列满了，按照指定的拒绝策略拒绝执行任务
         *          工作顺序：
         *              准备阶段：线程池创建，准备好指定core数量的核心线程，准备接受任务  如果并发数大于指定的核心线程数量则会根据以下执行
         *              1、核心线程满了，就将再进来的任务放入阻塞队列中，空闲的核心线程会自己去阻塞队列中获取执行任务
         *              2、阻塞队列满了，就直接开启新的线程，最大只能开到指定的max数量；
         *              3、max满了就会根据 RejectedExecutionHandler 的类型执行相应的拒绝策略
         *              4、max执行完成，有了很多空闲，在指定的时间keepAliveTime后释放掉多余的线程（释放的线程数 = max - core）
         *
         *          面试题： 一个线程池，core：7， max：20， queue：50， 100个并发进来会怎么分配？
         *              解析：7个会得到立即执行，50个会进入队列，在开13个线程执行，线程数会扩充到20，然后此时70个任务得到处理活排队等待，剩下的30个使用拒绝策略
         *                   如果不想抛弃还要执行，可以使用CallerRunsPolicy策略，这个策略会将剩下的30个任务，每进来一个都会得到立即执行
         *
         */
        System.out.println("main---start");

        //  1、继承Thread类
        new Thread01().start();
        // 2、实现runnable
        new Thread(new Thread02()).start();
        // 3、实现callable
        FutureTask<Integer> futureTask = new FutureTask<>(new Thread03());
        new Thread(futureTask).start();
        // callable 线程执行完返回结果   阻塞等待执行结果
        System.out.println("thread03 执行结果：" + futureTask.get());

        // 以后再业务代码中，以上三种启动线程的方式都不用，将所有的多线程异步任务都交给线程池执行
        // 4、使用线程池，每个异步任务，都提交给线程池让他自己去执行
        ThreadPoolExecutor executor = new ThreadPoolExecutor(10,  // 核心线程数：10
                100, // 最大线程数：100
                10,   // 非核心空闲线程指定存活时间
                TimeUnit.SECONDS,   // 存活时间单位
                new LinkedBlockingDeque<>(10000),   // 阻塞队列：长度为10000   // 需要注意这里如果不指定长度，默认队列长度为 Integer.MAX_VALUE
                Executors.defaultThreadFactory(),       // 使用默认的线程创建工厂
                new ThreadPoolExecutor.AbortPolicy());  // 拒绝策略，新来的任务会直接丢弃掉

        // 常用的4种线程池
//        Executors.newCachedThreadPool();                // core数量为0，所有线程都可以回收
//        Executors.newFixedThreadPool(5);                // 固定大小，max = core 没有可缓冲的线程
//        Executors.newScheduledThreadPool(5);            // 定时任务的线程池
//        Executors.newSingleThreadExecutor();            // 单线程的线程池，core = max =1，后台从队列里获取任务，挨个执行

        System.out.println("mian---end");

//        testStringBuilder();
    }

    public static class Thread01 extends Thread {
        @Override
        public void run() {
            System.out.println("Thread01当前异步线程：" + Thread.currentThread().getId());
            System.out.println("Thread01:" + (10 / 5));
        }
    }

    public static class Thread02 implements Runnable {
        @Override
        public void run() {
            System.out.println("Thread02当前异步线程：" + Thread.currentThread().getId());
            System.out.println("Thread02:" + (10 / 5));
        }
    }

    public static class Thread03 implements Callable<Integer> {

        @Override
        public Integer call() throws Exception {
            System.out.println("Thread03当前异步线程：" + Thread.currentThread().getId());
            int i;
            System.out.println("Thread03:" + (i = 10 / 2));
            return i;
        }
    }


    public static void testStringBuilder() {
        System.out.println("-----------------------------------");
        Set<StringBuilder> set = new HashSet<>();
        StringBuilder sb1 = new StringBuilder("abc");
        StringBuilder sb2 = new StringBuilder("abc123");
        StringBuilder sb3 = sb1;
        set.add(sb1);
        set.add(sb2);
        sb3.append("123");
        System.out.println(set);
        System.out.println(sb1.toString() + sb2.toString());
        System.out.println(sb1.toString().equals(sb2.toString()));
        System.out.println(sb1.equals(sb2));
        System.out.println(sb1 == sb2);
    }
}
