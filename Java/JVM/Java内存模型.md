---
title: 分代垃圾回收机制

date: 2021-03-16

categories: 
   - JVM

tags: 
   - JVM 

description: 
---


# Java内存模型介绍

Java虚拟机规范中定义了Java内存模型（Java Memory Model，JMM），用于屏蔽掉各种硬件和操作系统的内存访问差异，以实现让Java程序在各种平台下都能达到一致的并发效果.

JMM规范了Java虚拟机与计算机内存是如何协同工作的：规定了一个线程如何和何时可以看到由其他线程修改过后的共享变量的值，以及在必须时如何同步的访问共享变量。

简单理解: Java内存模式是一种虚拟机规范, 什么规范呢? 它定义了一套 多线程对共享数据读写时, 对数据的可见性 / 有序性 / 原子性 的规则和保障, 就是这样的一个规范.


# 主内存与工作内存

Java内存模型规定了所有的变量都存储在主内存, 每个线程还有自己的工作内存.

线程的工作内存保存了被该线程使用到的变量的主内存副本拷贝,线程对变量的所有操作(赋值,读取)等都必须在工作内存进行, 不能直接读写主内存..

不同线程之间也无法直接访问对象工作内存中的变量, 线程间的变量值传递, 必须通过主内存完成


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/JMM_memory.jpg)



# volitale


* volitale 体现可见性, 保证多个线程之间, 一个线程对volitale变量的修改, 对另外一个线程是可见的. 
> 普普通变量无法做到这一点, 普通变量的值在线程间传递, 均需要通过主内存来完成. 比如, 线程 A 修改一个普通变量的值, 然后向主内存进行回写, 另外一个线程 B 在线程 A 回写完了之后, 再从主内存进行读取操作, 新变量值才对 B 可见.

* 但是volitale 不保证原子性, 因此只适用于一个线程写, 多个线程读的情况.

* synchronized既能保证原子性, 也能保证可见性. 但是 synchronized 是重量级操作. 性能更低.

* volitale修饰变量, 禁止指令重排序优化



# 原子性


基本数据类型的访问读写是具备原子性的.(例外是 long 和 double 的非原子协定)

如果需要更大范围的原子性保障, Java 内存模型提供了 unlock 和 unlock 操作来满足这种需求.

尽管虚拟机未把 lock 和 unlock 操作直接开放给用户使用, 但是提供了更高层次的字节码指令  Monitorenter 和 MonitorExit 字节码指令来隐式使用者两个操作. 这两个字节码指令 就是 Java 代码: synchronized 关键字.

因此 synchronized 代码块中的操作是原子性的.



例子:
```java
static int a=0;
public static void main(){
        Thread t1 = new  Thread(new Runnable() {
            @Override
            public void run() {
                for (int i=0;i<500000;i++) {
                    a++;
                }
            }
        });
        t1.start();

        Thread t2 = new  Thread(new Runnable() {
            @Override
            public void run() {
                for (int j=0;j<500000;j++) {
                    a--;
                }
            }
        });
        t2.start();


        t1.join();
        t2.join();

        System.out.println(a);
}
```

结果不为 0

原因:
对变量的 自增 子减 不是原子操作. 因而在多线程下, 可能被 CPU 打断, 交错执行.

例如对于 i++ 而言（i 为静态变量），实际会产生如下的 JVM 字节码指令：

```java
getstatic       i   // 获取静态变量i的值
iconst_1            // 准备常量1
iadd                // 加法
putstatic       i   // 将修改后的值存入静态变量i
```

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/i++_code.png)


解决方法: synchronized 关键字


补充: 
1. 使用synchronized的时候, 注意锁的粒度, 减少加锁解锁的次数
2. t1 t2必须锁同一个对象



## synchronized
利用synchronized 实现同步的原基础:Java 中的每一个对象都可以作为锁. 具体表现:
1. 对普通方法加 synchronized,  锁是当前实例对象, 也就是 this
2. 对静态同步方法, 锁是当前类的 CLass 对象
3. 对于同步方法块, 锁是 synchronized 括号里配置的对象

当一个线程访问同步代码块的时候, 必须先得到锁, 退出或者抛出异常必须释放锁. 那锁存在哪里呢. 锁里面错处什么信息呢.



synchronized 在 JVM 里的实现, 主要基于 monitorenter 指令和  monitorexit 两个指令来实现.


monitorenter指令是在编译后插入到同步代码块开始的位置, monitorexit 是插入到方法结束处和异常处. JVM 要保证每个 monitorenter 都有对应的 monitorexit 与之配对.

任何一个对象都有一个 monitor 与之关联, 当一个 monitor 被持有后, 它将处于锁定状态.线程执行到monitorenter指令的时候, 会尝试获取对象对应的 monitor 所有权, 即尝试获得对象的锁

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/Monitor.png)


## 对象头

synchronized 用的锁是存在 Java 对象头里的.


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/object_header1.png)

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/object_header2.png)


# 可见性


可见性说的是当一个线程修改了共享变量的值, 其他线程能够立即得知这个修改.



Java 内存模型是通过在变量修改后将新值同步到主存, 在变量读取前从主存刷新变量值, 这种依赖主存作为传递媒介的方式实现可见性.无论是普通变量还是 volitale 都是这样.

区别:
volitale 保证新值能够立即同步到主存, 并且每次使用前立即从主存刷新.这样就保证了多线程操作的时候变量的可见性.

除了 volitale, java 还有两个关键字保证可见性: synchronized 和 final


1. synchronized保证可见性:synchronized强制当前线程从主存中读取, 并且对变量的修改,  必须把此变量同步到主存中.

2. final保证可见性:final 修饰的字段在构造中一旦初始化完成, 并且构造器没有把 this 传递出去, 那其他线程就能看见 final 字段的值.


例子:退不出的循环
```java
   static   boolean run = true;

    public static void main(String[] args) throws InterruptedException {

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (run) {
                    // 这里加一句, 就可见了.  为啥呢.
                    // 因为底层 println 方法, 底层加了 synchronized 关键字, 要对 PrintStream 加锁
                    // synchronized强制当前线程从主存中读取, 破坏了 JIT 的优化
                    // System.out.println("in visiable" + run);
                }
            }
        }).start();

        Thread.sleep(500);

        run = false;
        System.out.println("run:::" + run);

    }
```
退不出的循环



解决方案:volitale


# 有序性

指令重排序 : JIT 编译器的优化

指令重排序是什么:JVM 在不影响正确性的前提下, 可以调整语句的执行顺序.  

比如:指令1 把地址 A中的值+10. 指令 2 把地址 A 中的值*2. 指定 3 把地址 B 中的值-3.
这时, 指令 1 和指令2 是有依赖的., 他们的顺序不能重排.
但是 指令 3 与指令 12 无关, 可以重排到 12 之前或者 12 之间都行, 只要保证后面依赖 AB 值的操作时能拿到正确的值即可.

这样的话, 同一线程, 没问题. 多线程, 就可能有问题.


例子:
```java
int num = 0;
boolean ready = false;

// 线程1 执行此方法
public void actor1(I_Result r) {
    if(ready) {
        r.r1 = num + num;
    } else {
        r.r1 = 1;
    }
}

 // 线程2 执行此方法
public void actor2(I_Result r) {
    num=2;
    ready=true;
}
```

I_Result 是一个对象，有一个属性 r1 用来保存结果，问，可能的结果有几种？
有同学这么分析
情况1：线程1 先执行，这时 ready = false，所以进入 else 分支结果为 1
情况2：线程2 先执行 num = 2，但没来得及执行 ready = true，线程1 执行，还是进入 else 分支，结 果为1
情况3：线程2 执行到 ready = true，线程1 执行，这回进入 if 分支，结果为 4（因为 num 已经执行过 了）

还可能是 0.

这种情况下是：线程2 执行 ready = true，切换到线程1，进入 if 分支，相加为 0, 赋给 r.r1，再切回线程2 执行 num = 2. 最终 r.r1=0

通过压测工具工具 jcstress 查看, 确认会出现指令重排序


解决:volitale修饰变量, 防止指令重排序

除此之外, 指令重排的案例

单例模式 Double Check Locking (DCL)



# happens before


**可见性和有序性的规则, 规定哪些写操作可以对其他的线程的读操作可见!**


* 一个线程对 volatile 变量的写操作, 对接下来其他线程对该变量的读可见

```java
    volatile static int x;
    new Thread(()->{
        x = 10;
    },"t1").start();

    new Thread(()->{
        System.out.println(x); 
    },"t2").start();
```

* 线程解锁之前对共享变量的写，接下来对相同对象加锁的其它线程, 对该共享变量的读可见
```java
static int x;
static Object m = new Object();
new Thread(()->{
    synchronized(m) {
    x = 10;
 }
},"t1").start();
new Thread(()->{
    synchronized(m) {
    System.out.println(x);
 }
},"t2").start();
```


* 线程 start 前对变量的写, 对线程开始后对该变量的读可见
```java
static int x;
x = 10;
new Thread(()->{
    System.out.println(x); 
 },"t2").start();
```
* 线程结束前对变量的写, 对其他线程得知那个线程结束后的读, 是可见的,  比如其他线程调用了 t.isAlive  t.join 之类的方法, 等待这个线程结束
```java
static int x;
Thread t1 = new Thread(()->{
    x = 10;
},"t1");
t1.start();
t1.join();
System.out.println(x);
```

* interrupt 之前对变量的写, 在其他线程知道那个线程被打断后, 对变量的读, 可见

* 对默认变量的写, 对其他线程对该变量的读可见

* 具有传递性



注意: 变量都是指成员变量或者静态变量


# CAS与原子类


CAS Compare and Swap  乐观锁的思想, 也称为无锁并发


比如多个线程要对一个共享的整型变量执行 +1 操作：
```java
// 需要不断尝试 
while(true) { 
    int 旧值 = 共享变量 ; // 比如拿到了当前值 0 , 是从主存读进来的共享变量
    int 结果 = 旧值 + 1; // 在旧值 0 的基础上增加 1 ，正确结果是 1 
    /*compareAndSwap在把新的结果写回主存之前, 先判断: 共享变量当前的值还跟旧值相同么?
    如果不相同, 就表示刚刚被其他线程改过了, compareAndSwap 返回 false，重新尝试，
    如果相同, 就表示共享变量没有被改过,  compareAndSwap 返回 true，表示我本线程做修改的同时，别的线程没有干扰, 我可以成功把结果写回主存. 成功后, 就退出循环*/
    if( compareAndSwap ( 旧值, 结果 )) { 
        // 成功，退出循环 
    } 
}
```
获取共享变量时，为了保证该变量的可见性，需要使用 volatile 修饰。结合 CAS 和 volatile 可以实现无
锁并发，适用于竞争不激烈、多核 CPU 的场景下。


* 适用于 竞争不激烈, 多核 CPU 的情况下

* 如果竞争激烈, 导致重试频繁发生

* 必须多核 CPU

* 不使用 sync, 因此不会阻塞, 不涉及线程上下文切换, 因此效率有所提升


## CAS 底层实现


依赖 Unsafe, 直接调用操作系统底层的 CAS 指令.


## 乐观锁与悲观锁
CAS 是基于乐观锁的思想：最乐观的估计，不怕别的线程来修改共享变量，就算改了也没关系，我吃亏点再重试呗。
synchronized 是基于悲观锁的思想：最悲观的估计，得防着其它线程来修改共享变量，我上了锁
你们都别想改，我改完了解开锁，你们才有机会。
