



# 把 HandlerThread 原理说明白





对 Handler 及 ThreadLocal 不熟悉的小伙伴请先阅读 [把Handler原理说明白.md](./把Handler原理说明白.md) 和  [把ThreadLocal原理说明白.md](./把ThreadLocal原理说明白.md)





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

然后调用 Looper的 prepare()。在 [把Handler原理说明白](./把Handler原理说明白.md) 中我们知道，这个 prepare() 内部就是创建了一个 Looper 对象并存入 ThreadLocal 中  `sThreadLocal.set(new Looper());` 。说的更深入一些，就是将<ThreadLocal, Looper> 作为键值对， 放入当前线程的 ThreadLocalMap 中。即：将 Looper 对象与当前线程进行关联。

之后，上锁，再从 sThreadLocal 中将 Looper 对象取出，此时，我们的 Looper 已经准备好了，可以通知其他线程。(关于线程同步，请参考[线程同步](../线程系列/线程同步.md))

之后设置了线程的优先级。

然后开始 Looper.loop() 进行循环，不断从 MessageQueue 中取出 Message。刚开始肯定是阻塞的，因为我还没给 MessageQueue 中丢入 Message， MessageQueue 其实是空的。我们在 [把Handler原理说明白](./把Handler原理说明白.md) 中说过，Handler 处理消息是在哪个线程执行的， 就看 Handler 中的 Looper 是在哪里执行 loop()方法。显然，如果我们用这个 Looper 对象来创建 Handler，那么 Handler 处理消息，肯定就是在当前这个 HandlerThread 线程中执行了。



执行了Looper.loop() 会一直循环在这里，当Looper 的循环退出，才可以执行到 `mTid = -1;` 这里。



这里可以看到， 在 run 方法中， 帮我们完成了 `Looper.prepare()`和 `Looper.loop()`方法的调用。



除此之外，它还向外暴露了 Looper



```java
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



只要我们拿到这个 Looper， 并将其作为参数用于构造 Handler， 那么 Handler 处理消息就都是在这个 HandlerThread 线程中执行啦。



`while (isAlive() && mLooper == null) {}` 防止在使用的时候线程还未启动或者 mLooper 还未创建好；因为线程启动也是需要时间的。 比如， 在我们上面的代码中

```java
        handlerThread.start();

        final Handler handler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
            }
        };
```



线程 start 之后， 就立马使用 `handlerThread.getLooper()` ， 这个时候线程很可能未初始化完成，或者 mLooper 没有创建好。因此， 要使用 while()循环，不断轮询。



`getLooper`  不是在当前的 HandlerThread 线程的。



`run()` 是在当前的 HandlerThread 线程的， 二者要通信一波。就用了  wait 和 notify