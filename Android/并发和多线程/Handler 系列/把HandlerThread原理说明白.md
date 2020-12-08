



# 把 HandlerThread 原理说明白





对 Handler 及 ThreadLocal 不熟悉的小伙伴请先阅读 [把Handler原理说明白.md](https://github.com/fanshanhong/note/blob/master/Android/%E5%B9%B6%E5%8F%91%E5%92%8C%E5%A4%9A%E7%BA%BF%E7%A8%8B/Handler%20%E7%B3%BB%E5%88%97/%E6%8A%8AHandler%E5%8E%9F%E7%90%86%E8%AF%B4%E6%98%8E%E7%99%BD.md) 和  [把ThreadLocal原理说明白.md](https://github.com/fanshanhong/note/blob/master/Android/%E5%B9%B6%E5%8F%91%E5%92%8C%E5%A4%9A%E7%BA%BF%E7%A8%8B/Handler%20%E7%B3%BB%E5%88%97/%E6%8A%8AThreadLocal%E5%8E%9F%E7%90%86%E8%AF%B4%E6%98%8E%E7%99%BD.md)

对线程同步不清楚的小伙伴，请先阅读[线程同步](https://github.com/fanshanhong/note/blob/master/Android/%E5%B9%B6%E5%8F%91%E5%92%8C%E5%A4%9A%E7%BA%BF%E7%A8%8B/%E7%BA%BF%E7%A8%8B%E7%B3%BB%E5%88%97/%E7%BA%BF%E7%A8%8B%E5%90%8C%E6%AD%A5.md)



## HandlerThread 是什么



```java
/**
 * Handy class for starting a new thread that has a looper. The looper can then be 
 * used to create handler classes. Note that start() must still be called.
 */
```



简单说来， HandlerThread 是 Android 系统为我们提供的一个便捷类（线程类，继承自 Thread）， 它可以帮助我们开启一个含有 Looper 的新线程。那， 这个 Looper 就可以用于创建 Handler。



我们都知道， 如果我们想在子线程中使用 Handler来处理消息， 一般用法如下：

```java
class LooperThread extends Thread {
       public Handler mHandler;
       
       public void run() {
  
           Looper.prepare();
           
           mHandler = new Handler() {
               public void handleMessage(Message msg) {
                   // process incoming messages here
               }
           };
           
           Looper.loop();
       }
   }
```



在子线程中， 我们需要手动进行 `Looper.prepare()`和 `Looper.loop()`方法的调用。

HandlerThread 就是为了让我们在子线程中使用 Handler 的时候更简便，不需要手动调用 `Looper.prepare()`和 `Looper.loop()`方法， 我们一起来看看它是怎么做的。



## HandlerThread 基本用法



```java
public class MainActivity extends AppCompatActivity {

    Button button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        button = findViewById(R.id.click);
        HandlerThread handlerThread = new HandlerThread("my-handler-thread1");
        handlerThread.start();

        final Handler handler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
            }
        };

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handler.sendEmptyMessage(133);
            }
        });
    }
}
```



首先，我们要创建一个 HandlerThread，并 start()。 之后，我们就可以用这个 HandlerThread 中的 Looper 对象作为参数来构造一个 Handler 对象， 然后通过 Handler 发送消息之后，其消息的处理就是在 HandlerThread 这个线程上执行的。

## HandlerThread 源码分析



以下源码分析基于 Android 2.3.5



```java
public class HandlerThread extends Thread {
    private int mPriority; // 优先级
    private int mTid = -1; // 线程 tid
    private Looper mLooper;

    // 构造方法，指定线程名称
    public HandlerThread(String name) {
        super(name);
        mPriority = Process.THREAD_PRIORITY_DEFAULT;// 指定优先级为默认优先级
    }
    
    // 构造方法，同时指定线程名称和线程优先级
    public HandlerThread(String name, int priority) {
        super(name);
        mPriority = priority;
    }
}
```



HandlerThread 直接继承自 Thread 类，因此他是一个线程类，具有线程类的所有特征和方法。

HandlerThread 为我们提供了两个构造。



既然它继承自Thread 类，我们来看看它的 run() 方法



```java
    public void run() {
        mTid = Process.myTid();
        Looper.prepare();
        synchronized (this) {
            mLooper = Looper.myLooper();
            notifyAll();
        }
        Process.setThreadPriority(mPriority);
        onLooperPrepared();
        Looper.loop();
        mTid = -1;
    }
```



首先获取到当前所在线程的 tid。

然后调用 Looper的 prepare()。在 [把Handler原理说明白](https://github.com/fanshanhong/note/blob/master/Android/%E5%B9%B6%E5%8F%91%E5%92%8C%E5%A4%9A%E7%BA%BF%E7%A8%8B/Handler%20%E7%B3%BB%E5%88%97/%E6%8A%8AHandler%E5%8E%9F%E7%90%86%E8%AF%B4%E6%98%8E%E7%99%BD.md) 中我们知道，这个 prepare() 内部就是创建了一个 Looper 对象并存入 ThreadLocal 中  `sThreadLocal.set(new Looper());` 。说的更深入一些，就是将<ThreadLocal, Looper> 作为键值对， 放入当前线程的 ThreadLocalMap 中。即：将 Looper 对象与当前线程进行关联。

之后，上锁，再从 sThreadLocal 中将 Looper 对象取出，此时，我们的 Looper 已经准备好了，可以通知其他线程。(关于线程同步，请参考[线程同步](../线程系列/线程同步.md))

之后设置了线程的优先级。

然后开始 Looper.loop() 进行循环，不断从 MessageQueue 中取出 Message。刚开始肯定是阻塞的，因为我们还没给 MessageQueue 中丢入 Message， MessageQueue 其实是空的。我们在 [把Handler原理说明白](https://github.com/fanshanhong/note/blob/master/Android/%E5%B9%B6%E5%8F%91%E5%92%8C%E5%A4%9A%E7%BA%BF%E7%A8%8B/Handler%20%E7%B3%BB%E5%88%97/%E6%8A%8AHandler%E5%8E%9F%E7%90%86%E8%AF%B4%E6%98%8E%E7%99%BD.md) 中说过，Handler 处理消息是在哪个线程执行的， 就看 Handler 中的 Looper 是在哪里执行 loop()方法。显然，如果我们用这个 Looper 对象来创建 Handler，那么 Handler 处理消息，肯定就是在当前这个 HandlerThread 线程中执行了。



执行了Looper.loop() 会一直循环在这里，当Looper 的循环退出，才可以执行到 `mTid = -1;` 这里。



这里可以看到， 在 run 方法中， 帮我们完成了 `Looper.prepare()`和 `Looper.loop()`方法的调用。



除此之外，它还向外暴露了 Looper 对象。



```java
    
    /**
     * This method returns the Looper associated with this thread. If this thread not been started
     * or for any reason is isAlive() returns false, this method will return null. If this thread 
     * has been started, this method will block until the looper has been initialized.  
     * @return The looper.
     */
     public Looper getLooper() {
        if (!isAlive()) {
            return null;
        }
        
        // If the thread has been started, wait until the looper has been created.
        synchronized (this) {
            while (isAlive() && mLooper == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
        }
        return mLooper;
    }
```



先看下注释：这个方法将返回与当前线程关联的的Looper对象。如果线程还没有启动，或者其他原因导致isAlive()方法返回false，那该方法就返回null。如果线程已经启动，这个方法将会阻塞直到 Looper 对象被正确初始化。

分析代码：

如果线程未启动 `if (!isAlive())`，就返回null；

`while (isAlive() && mLooper == null) {}` 防止在使用的时候线程已经启动但是 mLooper 还未创建好；因为创建对象也是需要时间的。 比如， 在我们上面的代码中

```java
        handlerThread.start();

        final Handler handler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
            }
        };
```



线程 `start()` 之后， 就立马使用 `handlerThread.getLooper()` ， 这个时候mLooper对象有可能没有创建好。如果此时Looper对象没有创建好，就wait()， 直到Looper对象被正确初始化之后，返回这个Looper对象。这里为什么使用while()， 而不使用if, 请参考： [线程同步](../线程系列/线程同步.md)

只要我们拿到这个 Looper， 并将其作为参数用于构造 Handler， 那么 Handler 处理消息就都是在这个 HandlerThread 线程中执行啦。





这就是HandlerThread的核心原理，它也是遵循Handler 的机制，帮我们创建了一个子线程，并且在子线程内帮我们进行了`Looper.prepare() ` 和 `Looper.loop()` 操作，并对外暴露了这个与当前子线程关联的Looper对象。之后我们可以直接使用这个Looper进行Handler的创建，并在该子线程上处理消息，不再需要自己手动调用`Looper.prepare() ` 和 `Looper.loop()` 。这也是它便捷的地方。





我们这里要考虑两个问题：

Q1：这里为啥要进行线程同步



这个问题，首先我们要明确，`getLooper()`  和  `run()`这两个方法分别是在哪个线程执行的。

  

`run()`  当子线程start() 之后，其run() 方法必定是执行在子线程上的， 也就是HandlerThread线程。在文章最开始的例子中， 它是运行在 名称为  my-handler-thread1  的线程上的。

那 `getLooper()`  呢？ 显然，这个方法是Android 向开发者暴露的接口，那么，开发者在哪里调用该方法，就决定了该方法在哪个线程上执行。在文章开始的例子中，它是跑在主线程的。因为是在onCreate里的。

两个不同的线程，就涉及到线程同步的问题。



可以这里想：HandlerThread 所在的线程其实是作为生产者，生产什么呢？生产Looper对象。即：在run方法中其实就是在创建Looper对象了。`getLooper` 所在线程其实是作为消费者，我们要拿到HandlerThread生产的Looper来使用。



当消费者想要去消费的时候（即：使用HandlerThread中的Looper对象的时候）， 发现该对象并未创建好，那么就等待（`wait()`）;

当生产者创建好Looper对象的时候（即：`mLooper = Looper.myLooper();` 调用完成的时候），需要通知（唤起）所有阻塞在该生产者线程的其他消费者线程，告诉他们东西已经生产好啦，你们可以使用啦。（`notifyAll()`）.





Q2：这里为啥要加synchronized

1.共享资源

2.几条语句不想被打断，打断会有问题；



```java
    synchronized (this) {
        mLooper = Looper.myLooper();
        notifyAll();
    }
```



如果这里不加synchronized， 在第一条语句  `mLooper = Looper.myLooper();` 执行之后， 如果此时CPU时间片分给其他线程执行。如果此时其他线程此时调用了`quit()`方法，那Looper循环就结束了。这个时候再回到我们这里执行 `notifyAll() `去通知其他消费者来使用我们的Looper，会发现使用Handler发了消息根本收不到了，因为你的Looper已经不转了，它已经退出循环，不再从MessageQueue中取Message了。。

```java
    public boolean quit() {
        Looper looper = getLooper();
        if (looper != null) {
            looper.quit();
            return true;
        }
        return false;
    }
```



```java
        // If the thread has been started, wait until the looper has been created.
        synchronized (this) {
            while (isAlive() && mLooper == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
        }
```

这里如果不加 synchronized，也会有类似的问题；

比如 `isAlive() && mLooper == null` 判断为true（代表线程已经启动，但是Looper对象未创建好）之后，被打断，CPU转而执行其他线程的操作。那其他线程有可能就把Looper对象给创建好了。。这时候再回到我们的代码，你并不知道Looper已经好了，又跑去wait()。。。那要等到什么时候去了。。人家已经创建好了。不会再创建了，也就不会再去notify了。你就只能一直等着了。。。



更通俗的讲解线程同步的问题，请参考：[线程同步](https://github.com/fanshanhong/note/blob/master/Android/%E5%B9%B6%E5%8F%91%E5%92%8C%E5%A4%9A%E7%BA%BF%E7%A8%8B/%E7%BA%BF%E7%A8%8B%E7%B3%BB%E5%88%97/%E7%BA%BF%E7%A8%8B%E5%90%8C%E6%AD%A5.md)