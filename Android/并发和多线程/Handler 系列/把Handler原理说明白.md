---

title: 把Handler原理说明白

date: 2018-08-10 

categories: 
   - Android
   - Handler 

tags: 
   - Android 
   - Handler 

description: ​

---


# 把Handler原理说明白

<!-- TOC -->

- [把Handler原理说明白](#把handler原理说明白)
    - [Handler是什么](#handler是什么)
    - [Handler使用简单介绍](#handler使用简单介绍)
    - [Handler](#handler)
    - [MessageQueue](#messagequeue)
    - [Looper](#looper)
    - [Message](#message)
- [总结](#总结)

<!-- /TOC -->

## Handler是什么


在安卓当中提供了两种方式来解决线程之间的通信，一种是AsynchTask，另外一种就是现在我们主要分析的Handler。

Handler是Android类库提供的用于接收、传递和处理消息或Runnable对象的处理类，它结合Message、MessageQueue和Looper类以及当前线程实现了一个消息循环机制，用于实现任务的异步加载和处理。



简单说来，就是Handler将Message丢到MessageQueue中，Looper不断从MessageQueue中取出Message，取出之后，由Handler进行处理。这是最主要的流程，我们要带着这个思路，进行接下来的分析。




## Handler使用简单介绍

1. 在 主线程中使用（请忽略内存泄漏的问题， 后续再说）

    ```java
    public class MainActivity extends AppCompatActivity {

            Handler handler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                }
            };

            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_main);

                handler.sendEmptyMessage(1);
                handler.postDelayed(task1, 10000);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        handler.sendEmptyMessage(2);
                        handler.postDelayed(task2, 20000);
                    }
                }).start();

            }
        }
    ```
    
    


2. 在子线程中使用。 从`Looper.java`中copy过来的。

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

   

下面我们提出几个问题：

Q1：为什么在主线程使用的时候不需要`Looper.prepare();` 和 `Looper.loop();`？

Q2：为什么在子线程中这样用？

Q3：为什么在主线程 调用`handler.sendEmptyMessage(1);` `handler.postDelayed(task1, 10000);`  之后， 最后的处理在主线程中进行的？

Q4：为什么在子线程调用`handler.sendEmptyMessage(2);` `handler.postDelayed(task2, 20000);`之后，最后的处理也是在主线程中进行的？



why？？

慢慢分析

以下分析基于 android 2.3.5 源码。




## Handler


Handler主要完成Message的入队（MessageQueue）和处理，下面将通过Handler的源码分析其消息分发、处理流程。来看下Handler类的方法列表：

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/Handler_method_list.png)

首先来看Handler的几个构造方法：


```java
    /**
     * Default constructor associates this handler with the queue for the
     * current thread.
     *
     * If there isn't one, this handler won't be able to receive messages.
     */
    public Handler() {
        if (FIND_POTENTIAL_LEAKS) {
            final Class<? extends Handler> klass = getClass();
            if ((klass.isAnonymousClass() || klass.isMemberClass() || klass.isLocalClass()) &&
                    (klass.getModifiers() & Modifier.STATIC) == 0) {
                Log.w(TAG, "The following Handler class should be static or leaks might occur: " +
                    klass.getCanonicalName());
            }
        }


        mLooper = Looper.myLooper();
        if (mLooper == null) {
            throw new RuntimeException(
                "Can't create handler inside thread that has not called Looper.prepare()");
        }
        mQueue = mLooper.mQueue;
        // callback默认为null
        mCallback = null;
    }

```

无参构造， 主要是给mLooper、mQueue、mCallback几个成员变量赋值。

```java
    /**
     * Constructor associates this handler with the queue for the
     * current thread and takes a callback interface in which you can handle
     * messages.
     */
    public Handler(Callback callback) {
        if (FIND_POTENTIAL_LEAKS) {
            final Class<? extends Handler> klass = getClass();
            if ((klass.isAnonymousClass() || klass.isMemberClass() || klass.isLocalClass()) &&
                    (klass.getModifiers() & Modifier.STATIC) == 0) {
                Log.w(TAG, "The following Handler class should be static or leaks might occur: " +
                    klass.getCanonicalName());
            }
        }

        mLooper = Looper.myLooper();
        if (mLooper == null) {
            throw new RuntimeException(
                "Can't create handler inside thread that has not called Looper.prepare()");
        }
        mQueue = mLooper.mQueue;
        mCallback = callback;
    }

 
```

带一个参数Callback的构造， 一样是给mLooper、mQueue、mCallback几个成员变量赋值。


```java
    /**
     * Use the provided queue instead of the default one.
     */
     // 提供looper的构造
    public Handler(Looper looper) {
        mLooper = looper;
        mQueue = looper.mQueue;
        mCallback = null;
    }
```

带一个参数Looper的构造。

可以看到， 几个构造都是在给mLooper、mQueue、mCallback几个成员变量赋值。这几个变量定义在 Handler.java 文件的最后。稍后， 我们将看到它们是如何被使用的。

```java
    final MessageQueue mQueue;

    final Looper mLooper;

    final Callback mCallback;
```



显然，Handler 就是把消息发送到这个 MessageQueue 中了， 然后再由这个 Looper 将消息从这个 MessageQueue 中取出来，他们之间具体是如何协作的，并且如何实现线程切换的，我们在讲 Looper 的时候再说。



下来我们看一下Handler 是如何创建Message的。
在使用过程中， 我们可能会直接调用Message的构造方法来创建一个新的Message。但是， 这并不是一个好的习惯。在Handler 和 Message中都给我们提供了一系列obtain方法， 我们应该使用obtain方法从消息池中获取对象来使用。因为通过obtain方法获取到的消息对象， 在使用完之后， 消息池会自动帮我们回收（是在Looper.loop()的最后调用了`msg.recycle();`回收资源）。

Handler的obtainMessage系列方法

```java

    public final Message obtainMessage()
    {
        return Message.obtain(this);
    }

    public final Message obtainMessage(int what)
    {
        return Message.obtain(this, what);
    }

    public final Message obtainMessage(int what, Object obj)
    {
        return Message.obtain(this, what, obj);
    }

    public final Message obtainMessage(int what, int arg1, int arg2)
    {
        return Message.obtain(this, what, arg1, arg2);
    }

    public final Message obtainMessage(int what, int arg1, int arg2, Object obj)
    {
        return Message.obtain(this, what, arg1, arg2, obj);
    }

```

可以看到全部都是调用了`Message.obtain()`方法构造一个Message对象出来。第一个参数传入this， 是指定 Message的 target 为当前的Handler。



拿到了Message之后， 就可以通过sendMessage系列方法， 将Message发送到消息队列（MessageQueue）了。


```java
   public final boolean sendMessage(Message msg)
    {
        return sendMessageDelayed(msg, 0);
    }

    public final boolean sendEmptyMessage(int what)
    {
        return sendEmptyMessageDelayed(what, 0);
    }

    public final boolean sendEmptyMessageDelayed(int what, long delayMillis) {
        Message msg = Message.obtain();
        msg.what = what;
        return sendMessageDelayed(msg, delayMillis);
    }

    public final boolean sendEmptyMessageAtTime(int what, long uptimeMillis) {
        Message msg = Message.obtain();
        msg.what = what;
        return sendMessageAtTime(msg, uptimeMillis);
    }

    public final boolean sendMessageDelayed(Message msg, long delayMillis)
    {
        if (delayMillis < 0) {
            delayMillis = 0;
        }
        return sendMessageAtTime(msg, SystemClock.uptimeMillis() + delayMillis);
    }

    // 所有的post方法， send方法， 都间接调用这里
    public boolean sendMessageAtTime(Message msg, long uptimeMillis)
    {
        boolean sent = false;// 返回值, 是否enqueue成功
        MessageQueue queue = mQueue;
        if (queue != null) {
            msg.target = this;// 将msg的target属性设置成当前handler
            // 在 Message.obtain()中已经指定 target 为 this 了，这里为啥还指定？
            // 因为有些人创建Message的时候不用 Message.obtain()，自己 new Message()就没指定 target
            sent = queue.enqueueMessage(msg, uptimeMillis);// 入队
        }
        else {
            RuntimeException e = new RuntimeException(
                this + " sendMessageAtTime() called with no mQueue");
            Log.w("Looper", e.getMessage(), e);
        }
        return sent;
    }
```

透过源码， 我们可以发现， 在sendMessage方法内部， 创建Message对象的时候也是使用`Message.obtain()`方法来创建消息的。而且， 所有的sendMessage方法内部最后都是调用到了`sendMessageAtTime(Message msg, long uptimeMillis)`

在`sendMessageAtTime(Message msg, long uptimeMillis)`中， 首先拿到当前关联的MessageQueue， 然后给Message的target指定为当前Handler（后续将Message从queue中取出后， 就是用这个target来处理的）， 然后调用enqueueMessage方法， 将Message加入到queue中， 并且返回是否加入成功。

下面我们再看一下post系列方法

```java
    /**
     * Causes the Runnable r to be added to the message queue.
     * The runnable will be run on the thread to which this handler is 
     * attached. 
     *  
     * 将runnable 加入message queue， 这个runnable将在与handler关联的线程中被执行
     * @param r The Runnable that will be executed.
     * 
     * @return Returns true if the Runnable was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.
     *  如果这个runnable被成功放在messagequeue中， 则返回true 
     *  出错的话就返回false(那在什么情况下会出错呢，一般是正在退出的情况， 也就是在messagequeue中加
     了一个target=null的message， looper死循环要停止了， 就返回 false）
     */
    public final boolean post(Runnable r)
    {
       // 将runnable封装成message对象发出
       return  sendMessageDelayed(getPostMessage(r), 0);
    }
    
    public final boolean postAtTime(Runnable r, long uptimeMillis)
    {
        return sendMessageAtTime(getPostMessage(r), uptimeMillis);
    }
    
    public final boolean postAtTime(Runnable r, Object token, long uptimeMillis)
    {
        return sendMessageAtTime(getPostMessage(r, token), uptimeMillis);
    }
   
    public final boolean postDelayed(Runnable r, long delayMillis)
    {
        return sendMessageDelayed(getPostMessage(r), delayMillis);
    }
```

透过源码， 我们可以看到， 所有传入的Runnable对象， 都用`getPostMessage()`包装成一个 Message 对象， 然后调用了sendMessage相关的方法， 通过上面的分析我们知道， sendMessage相关方法， 最终都间接调用了`sendMessageAtTime(Message msg, long uptimeMillis)`。

我们看一下 `getPostMessage()`是如何将Runnable包装成Message的。

```java
    /**
    * 将runnable对象封装成message对象
    */
    private final Message getPostMessage(Runnable r) {
        Message m = Message.obtain();
        m.callback = r;// 对message中的callback赋值为runnable
        return m;
    }
```

很简单， 就是先调用`Message.obtain()`拿到空消息对象， 然后给消息的callback属性赋值为Runnable。



到这里，  我们的Message已经被创建好啦。我们下面看一下这个 Message 是如何被丢在MessageQueue中的。



## MessageQueue

MessageQueue 的底层实现全部放在的native层。 我们主要分析 `boolean enqueueMessage(Message msg, long when)` 和 `Message next()`这两个方法， 便可大概了解MessageQueue 内部的实现逻辑。


```java
    // 将 msg 加入到 MessageQueue 中
    final boolean enqueueMessage(Message msg, long when) {
        // 在调用enqueueMessage()之前,     Message对象的when属性是不支持设置的.
        // Message的when属性要在下面设置.
        // 如果when != 0, 则认为这个Message当前正在被使用.
        if (msg.when != 0) {
            throw new AndroidRuntimeException(msg
                    + " This message is already in use.");
        }
      
      	// msg.target == null 是指定当前的循环器 Looper 退出循环的标志。在 Looper 的源码中可以看到。
      	// 只有main线程 mQuitAllowed 是 false， 即 main 线程的 Looper 循环不可以退出；
        if (msg.target == null && !mQuitAllowed) {
            throw new RuntimeException("Main thread not allowed to quit");
        }

        final boolean needWake; // 是否需要唤醒
        synchronized (this) {
            if (mQuiting) { // 正在退出
                RuntimeException e = new RuntimeException(
                    msg.target + " sending message to a Handler on a dead thread");
                Log.w("MessageQueue", e.getMessage(), e);
                return false;
            } else if (msg.target == null) { // target == null, 表示将要退出Loop的循环了.
                mQuiting = true;
            }

            // 如果 mQuiting == false && msg.target != null 才会走到这里

            // 这里给msg.when赋值, 也就是何时处理这个Message的时间
            msg.when = when;
            //Log.d("MessageQueue", "Enqueing: " + msg);
            // 这个mMessages是队列的头
            Message p = mMessages;

            // 如果队列是空的, 或者指定 的 当前正在入队的这个Message的执行时间是比队头的Message执行时间早, 那么当前的这个Message应该放在队头, 第一个被处理.
            if (p == null || when == 0 || when < p.when) {
                msg.next = p; // 把之前的队列接在当前的这个msg的屁股后面
                mMessages = msg; // 更新头指针, 头指针指向当前的这个Message
                needWake = mBlocked; // new head, might need to wake up 
              	// 队列里有消息了, 或许需要唤醒一下.
                // 他这里   用 是否阻塞的值   赋给了  是否需要唤醒. 合理的, 阻塞了才需要唤醒嘛. 
            } else {
                // 队列不是空, 或者当前入队的这个msg不是放在队头的, 需要另外处理一下.
                Message prev = null;

                // 遍历链表, 找到当前msg应该插入的合适的位置.也是根据时间when来判断的.
                while (p != null && p.when <= when) {
                    prev = p;
                    p = p.next;
                }
                msg.next = prev.next;
                prev.next = msg;
                needWake = false; // still waiting on head, no need to wake up  不需要唤醒
            }
        }
        if (needWake) { // 如果需要唤醒, 调用native层实现去唤醒.
            nativeWake(mPtr);
        }
        // 执行到这里, 表示入队成功, 返回true.
        return true;
    }

```

注释已经写了，首先进行异常判断，然后给msg.when赋值，  这个when属性是说这个Message 应该在什么时间被处理， 比如我们一般写 `sendEmptyMessageDelay()`延时5秒后处理， 其实就是给这个when设置值。
之后分两种情况， 第一种是当前这个msg要放在队头的情况(当队列为空或者当这个msg要最早被执行的情况，msg要放在队头)， 第二种情况， 是要把msg插入到队列的合适位置。比如队列中目前有两个Message, 一个是1秒后执行， 一个是5秒后执行，我们enqueue的这个msg要在3秒后执行， 那么就插入到  1秒  和  5秒之前的位置。其实他这个队列是按照执行时间的优先级来排序的， 需要最早处理的放在最前面，对吧。

加入之后, 判断是否需要唤醒, 如需要, 调用底层代码唤醒.

最后返回的boolean 表示enqueue 是否成功了.


下面我们再看一下 是如何从队列中取出的

```java
    // 从队列中取出Message对象
    final Message next() {
        int pendingIdleHandlerCount = -1; // -1 only during first iteration
        int nextPollTimeoutMillis = 0;

        // 死循环
        // 这里没有用while(flag)的形式， 因为 while(flag)的形式，可以通过反射设置 flag 的值，进行控制循环，所以这里用了 for(;;)
        for (;;) {
            if (nextPollTimeoutMillis != 0) {
                Binder.flushPendingCommands();
            }
            nativePollOnce(mPtr, nextPollTimeoutMillis);

            synchronized (this) {
                // Try to retrieve the next message.  Return if found.
                final long now = SystemClock.uptimeMillis();
                final Message msg = mMessages; // 拿到队头的Message对象, 用msg记录一下.
                if (msg != null) { // 判空
                    final long when = msg.when; // 拿到队头的Message对象的执行时间
                    if (now >= when) { // 如果当前时间 >= 执行时间 , 也就是说已经到了或者过了执行时间啦.赶紧取出执行吧
                        mBlocked = false; // 不阻塞
                        mMessages = msg.next; // 头指针后移一个.  这个msg就是要取出的了.
                        msg.next = null; // 
                        if (Config.LOGV) Log.v("MessageQueue", "Returning message: " + msg);
                        return msg; // 返回取出的msg
                    } else { // 还没到执行时间
                    	// nextPollTimeoutMillis指的是还要多久才执行
                        nextPollTimeoutMillis = (int) Math.min(when - now, Integer.MAX_VALUE);
                    }
                } else {
                    nextPollTimeoutMillis = -1;
                }

                // If first time, then get the number of idlers to run.
                if (pendingIdleHandlerCount < 0) {
                    pendingIdleHandlerCount = mIdleHandlers.size();
                }
                if (pendingIdleHandlerCount == 0) {
                    // No idle handlers to run.  Loop and wait some more.
                    mBlocked = true;
                    continue;
                }

                if (mPendingIdleHandlers == null) {
                    mPendingIdleHandlers = new IdleHandler[Math.max(pendingIdleHandlerCount, 4)];
                }
                mPendingIdleHandlers = mIdleHandlers.toArray(mPendingIdleHandlers);
            }

            // Run the idle handlers.
            // We only ever reach this code block during the first iteration.
            for (int i = 0; i < pendingIdleHandlerCount; i++) {
                final IdleHandler idler = mPendingIdleHandlers[i];
                mPendingIdleHandlers[i] = null; // release the reference to the handler

                boolean keep = false;
                try {
                    keep = idler.queueIdle();
                } catch (Throwable t) {
                    Log.wtf("MessageQueue", "IdleHandler threw exception", t);
                }

                if (!keep) {
                    synchronized (this) {
                        mIdleHandlers.remove(idler);
                    }
                }
            }

            // Reset the idle handler count to 0 so we do not run them again.
            pendingIdleHandlerCount = 0;

            // While calling an idle handler, a new message could have been delivered
            // so go back and look again for a pending message without waiting.
            nextPollTimeoutMillis = 0;
        }
    }
```

最主要的都注释了, 没注释的就是我没看懂的....


其中有个变量 `mAllowQuit`  默认是true, 表示当前你能否退出。 只有主线程的是不可以退出的，其他子线程都是可以退出的。

何为退出？  就是 往 MessageQueue里面丢一个target == null 的 Message， 就代表说要退出啦，其实就是告诉 Looper不要再从MessageQueue里面取Message处理了。在Loop中我们会看到具体的实现。

```java
    boolean mQuitAllowed = true;
```



终于轮到 Looper 出场了。

## Looper

先看看类注释哈。

```java
/**
  * 该类用于在线程中运行一个消息轮询器.
  * 线程在默认情况下是没有消息轮询器和它关联的.
  * 可以在线程中调用prepare()方法去创建一个消息轮询器并运行起来， 然后调用loop()方法去处理message， 直到loop停止.
  * Class used to run a message loop for a thread.  Threads by default do
  * not have a message loop associated with them; to create one, call
  * {@link #prepare} in the thread that is to run the loop, and then
  * {@link #loop} to have it process messages until the loop is stopped.
  * 
    大多数情况下, 与消息轮询器相互作用的都是通过handler. 也就是Handler把Message丢在MessageQueue中, 然后消息轮询器去取出消息, 然后再交给Handler处理.
  * <p>Most interaction with a message loop is through the
  * {@link Handler} class.
  * 
    下面是个典型列子， 实现looper的thread
  * <p>This is a typical example of the implementation of a Looper thread,
  * using the separation of {@link #prepare} and {@link #loop} to create an
  * initial Handler to communicate with the Looper.
  * 
  * <pre>
  *  class LooperThread extends Thread {
  *      public Handler mHandler;
  *      
  *      public void run() {
  *          Looper.prepare();
  *          
  *          mHandler = new Handler() {
  *              public void handleMessage(Message msg) {
  *                  // process incoming messages here
  *              }
  *          };
  *          
  *          Looper.loop();
  *      }
  *  }</pre>
  */
```

注释里面说的很清楚，Looper类是用于在线程中运行一个消息轮询器。线程在默认情况下是没有消息轮询器和它关联的，可以在线程中调用prepare()方法去创建一个消息轮询器并运行起来， 然后调用loop()方法去处理message， 直到loop停止。



那我们来看看prepare()方法做了什么。

```java
    /** Initialize the current thread as a looper.
      * This gives you a chance to create handlers that then reference
      * this looper, before actually starting the loop. Be sure to call
      * {@link #loop()} after calling this method, and end it by calling
      * {@link #quit()}.
      */
      /** fan:
        * 将当前线程初始化成为 一个 Looper线程 
        * 在真正开始轮训之前， 给你提供一个机会, 让你创建handlers并且引用looper
        * 
        */
    public static final void prepare() {
        // 如果没有调用 过prepare就返回null
        // 每个线程只能有一个looper
        if (sThreadLocal.get() != null) {
            throw new RuntimeException("Only one Looper may be created per thread");
        }
        // 调用Looper的私有构造， 创建一个Looper对象， 放到threadlocal中
        // 这里之所以要做成ThreadLocal是因为，prepare方法可能在不同的线程中调用，比如main线程，比如需要loop能力的其他子线程， 不管哪个线程使用的时候，比如get set，都只是针对自己线程里面的变量操作，不影响其他线程
        sThreadLocal.set(new Looper());
    }
```

Looper中有个变量 sThreadLocal, 我们看看他是干嘛.

```java
    private static final ThreadLocal sThreadLocal = new ThreadLocal();
```

参考这篇文章，一定要弄明白 ThreadLocal 是干嘛的， 否则一直云里雾里，弄不清线程是如何切换的。

[把ThreadLocal原理说明白](./把ThreadLocal原理说明白.md)

这个ThreadLocal 就是个在线程作用域内的变量。



每个线程只能关联一个Loop， 如果已经关联过了， 再调用prepare()方法， 就会抛出异常。
后面调用了Looper的私有构造方法， 给其内部的 `mQueue  mRun  mThread`几个变量赋值。
构造好了Looper对象之后， 调用了 threadLocal  的 set()方法， 其内部就是拿到当前线程（currentThread）中的那个map， 然后把 ThreadLocal对象作为key， Looper对象作为value的键值对， 存储在了当前线程中的那个map中。 之后再get()， 就是从当前线程的那个map中， 根据 ThreadLoca对象， 拿到Looper对象。

那为啥每个线程只能关联一个Looper对象？ 因为Looper类中的这个ThreadLocal对象是static的。。。而且他是  饿加载， 提前new好了的。

因此当你在不同的线程中调用prepare()的时候， 就是给线程中的那个 ThreadLocalMap 中放了 （sThreadLocal， new Looper()） 这样一个键值对。 然后你在不同的线程中再get()， 拿到就是刚set进去的Looper对象。prepare()之后，sThreadLocal 就有值了。



```java
    // 私有的构造方法
    // 因为Looper的创建时在prepare中， 所以这里private
    // 在 Looper 内部持有对 MessageQueue 的引用
    private Looper() {
        // 创建MessageQueue
        mQueue = new MessageQueue();
        mRun = true;
        // 赋值当前线程对象
        mThread = Thread.currentThread();
    }

```



按照注释中给定的代码示例，prepare()之后， 创建一个 Handler 出来，我们回过头来看看前面 Handler 的构造。

```java
    /**
     * Constructor associates this handler with the queue for the
     * current thread and takes a callback interface in which you can handle
     * messages.
     */
    public Handler(Callback callback) {
        if (FIND_POTENTIAL_LEAKS) {
            final Class<? extends Handler> klass = getClass();
            if ((klass.isAnonymousClass() || klass.isMemberClass() || klass.isLocalClass()) &&
                    (klass.getModifiers() & Modifier.STATIC) == 0) {
                Log.w(TAG, "The following Handler class should be static or leaks might occur: " +
                    klass.getCanonicalName());
            }
        }

        mLooper = Looper.myLooper();
        if (mLooper == null) {
            throw new RuntimeException(
                "Can't create handler inside thread that has not called Looper.prepare()");
        }
        mQueue = mLooper.mQueue;
        mCallback = callback;
    }
```

构造方法中调用了`Looper.myLooper();`，就是拿到在 prepare()中 set 进去的那个 Looper 对象，为 Handler 中的 mLooper 、mQueue 赋值。



比如：我们在子线程 t1 中调用了 prepare()方法， 那么就给 t1 线程中的 sThreadLocal 设置了一个 Looper 对象（其实质是给当前线程 t1 中的那个 map 中存放了一个<sThread,  looper>的键值对）。然后在Handler 的构造中拿到这个 Looper 对象，并给 Handler 中的相关属性赋值(主要是 mMessageQueue)。之后，Handler 发送消息，就发在了这个 MessageQueue 中，也就是说，这个 MessageQueue 也是在 t1 线程中的。



```java
		public static final Looper myLooper() {
        return (Looper)sThreadLocal.get();
    }
```



之后， 我们就要loop()了， 取出消息并处理它。

```java
    /**
     *  Run the message queue in this thread. Be sure to call
     * {@link #quit()} to end the loop.
     */
    public static final void loop() {
        // 拿到当前线程关联的looper， 这个 looper 还是之前 set 的那个Looper对象
        Looper me = myLooper();
				// 封装在looper中的MessageQueue, 因为looper和当前线程是关联的， 所以messagequeue也是和线程相关联的
        MessageQueue queue = me.mQueue;
        
        // Make sure the identity of this thread is that of the local process,
        // and keep track of what that identity token actually is.
        Binder.clearCallingIdentity();
        final long ident = Binder.clearCallingIdentity();
        
        while (true) {
            Message msg = queue.next(); // might block 调用 queue的 next 方法，从MessageQueue中取出， 可能会阻塞
            // 如果当前的 MessageQueue 是空的， 或者 MessageQueue 中的第一个 Message 还没到执行时间，就阻塞了。
            //if (!me.mRun) {
            //    break;
            //}
            if (msg != null) {
                if (msg.target == null) {
                    // No target is a magic identifier for the quit message.
                    // message没有target为结束信号，退出循环
                    return;
                }

                // 打印日志
                if (me.mLogging!= null) me.mLogging.println(
                        ">>>>> Dispatching to " + msg.target + " "
                        + msg.callback + ": " + msg.what
                        );
                // 将消息分发给target，target就是handler，  如果走到这里， target肯定不是null
                msg.target.dispatchMessage(msg);
                if (me.mLogging!= null) me.mLogging.println(
                        "<<<<< Finished to    " + msg.target + " "
                        + msg.callback);
                
                // Make sure that during the course of dispatching the
                // identity of the thread wasn't corrupted.
                final long newIdent = Binder.clearCallingIdentity();
                if (ident != newIdent) {
                    Log.wtf("Looper", "Thread identity changed from 0x"
                            + Long.toHexString(ident) + " to 0x"
                            + Long.toHexString(newIdent) + " while dispatching to "
                            + msg.target.getClass().getName() + " "
                            + msg.callback + " what=" + msg.what);
                }
                // 回收message资源
                msg.recycle();
            }
        }
    }
```

我们一点一点分析。

首先调用`myLooper()` 拿到当前线程关联的looper。 直接调用ThreadLocal的get()方法， 那么就是从当前线程 中的 ThreadLocalMap 中， 以sThreadLocal为key， 获取到对应的value， 其实就是在同一线程中， 之前在 prepare()中set进去的那个Looper对象。注意是同一线程中啊， 不可能是其他线程的， 因为ThreadLocal作用域就是线程内的。

拿到Looper之后， Looper中的MessageQueue也拿到了， 然后就调用`next()`开始取消息。`next()`之前分析过了，  就是死循环， 一直判断队头的Message对象 不是 null， 并且到了它的执行时间（`if (now >= when) { // 如果当前时间 >= 执行时间 , 也就是说已经到了或者过了执行时间啦.赶紧取出执行吧`）， 就把队头的Message对象取出来， 也就是这里的msg啦。

如果msg的target属性是null， 就直接return， 是return， 就退出循环了， 那么这个消息轮询器就不再轮询了， 以后就不从MessageQueue里面取消息处理了。 因此， msg.target == null 是 整个消息轮询器结束的标志。

之后打印日志。

之后， 调用`msg.target.dispatchMessage(msg)`将消息分发给target对Message进行处理，target就是handler。  如果走到这里， target肯定不是null。

我们看一下dispatch方法

```java

    /**
     * Handle system messages here.
     */
     // 处理消息，该方法由looper调用, 在looper的loop()方法中, msg.target.dispatchMessage(msg)
    public void dispatchMessage(Message msg) {
        // 先判断msg中的callback, 优先调用
        // 如果message设置了callback，即runnable消息，处理callback！
        if (msg.callback != null) {
            handleCallback(msg);
        } else {
            // 然后这里判断的是, handler的callback
            // 这种方法允许让activity等来实现Handler.Callback接口，避免了自己编写handler重写handleMessage方法。见http://alex-yang-xiansoftware-com.iteye.com/blog/850865
            if (mCallback != null) {
                if (mCallback.handleMessage(msg)) {
                    return;
                }
            }
            // 最后回调hanler的handleMessage方法
            // 该方法必须由子类进行重写
            handleMessage(msg);
        }
    }
```

判断msg.callback 不为空的话， 就调用 `handleCallback(msg)`。 这个msg.callback 是啥？ 就是调用`handle.post(Runnable)`时候传入的那个runnable对象啊。还记得不， 他把传入的Runnable 对象包装了一下， 包装成Message对象之后， 调用了sendMessageAtTIme方法，  而这个Runnable对象是成为了Message对象的一个属性。 `handleCallback(msg)`就是直接run了一下， 没啥

```java
    // 如果messge指定了callback属性, 就调用这个方法
    private final void handleCallback(Message message) {
        // message.callback就是个runnable
        // 直接调用runnable的run方法
        message.callback.run();
    }
```

如果没有设置 msg.callback， 走到else， 判断 mCallback是否null， 不为null，就回调mCallback的handleMessage()方法。这个mCallback是啥？是否还记得在创建Handler时候， 有多个构造方法， 其中就有指定Callback参数的。指定的就是这个mCallback。

如果mCallback 也是null， 那么就回调`handleMessage(msg)`。 这个是啥？  这个就是子类必须重写的那个handleMessage啦。


处理完之后， 调用了 `msg.recycle();`, 这是把Message资源释放， 把Message对象回收到那个全局的消息池， 方便下次使用啦。


说到这里， 整个Handler的流程就完啦。

最后， 我们再回答一下开篇说的几个问题：

Q1：为什么在主线程使用的时候不需要`Looper.prepare();` 和 `Looper.loop();`？

> 这里我们要先看ActivityThread类的源码。在该类的最后， 有个main()方法， 这个main()方法， 是整个app程序的入口。

```java
   public static final void main(String[] args) {
        SamplingProfilerIntegration.start();

        Process.setArgV0("<pre-initialized>");

        Looper.prepareMainLooper();
        if (sMainThreadHandler == null) {
            sMainThreadHandler = new Handler();
        }

        ActivityThread thread = new ActivityThread();
        thread.attach(false);

        if (false) {
            Looper.myLooper().setMessageLogging(new
                    LogPrinter(Log.DEBUG, "ActivityThread"));
        }

        Looper.loop();

        if (Process.supportsProcesses()) {
            throw new RuntimeException("Main thread loop unexpectedly exited");
        }

        thread.detach();
        String name = (thread.mInitialApplication != null)
            ? thread.mInitialApplication.getPackageName()
            : "<unknown>";
        Slog.i(TAG, "Main thread of " + name + " is now exiting");
    }
}

```

第三行， 他调用了 `Looper.prepareMainLooper();`  随后， 又调用了`Looper.loop();` 。 真相大白


Q2：为什么在子线程中这样用？
> 先prepare是给当前的线程关联一个Looper对象。Looper对象中持有MessageQueue的引用。
> 然后再Looper.loop() 其实就是在当前线程关联的Looper对象上的MessageQueue上不断轮询， 不断取出Message去处理。



Q3：为什么在主线程 调用`handler.sendEmptyMessage(1);` `handler.postDelayed(task1, 10000);`  之后， 最后的处理在主线程中进行的？

Q4：为什么在子线程调用`handler.sendEmptyMessage(2);` `handler.postDelayed(task2, 20000);`之后，最后的处理也是在主线程中进行的？

最后的处理是在哪个线程中执行， 就是看Looper.loop()方法中， 从MessageQueue中拿到Message之后， `msg.target.dispatchMessage(msg)`这个方法在哪个线程调用， 就是在哪个线程处理的， 对吧？  那么， 这个方法究竟是在哪个线程执行的？`msg.target.dispatchMessage(msg)`这个方法是在Looper.loop() 方法中调用的，那就看是在哪个线程上调用 loop()方法的，也就是看Handler 中的Looper对象是和哪个线程关联的。



比如：主线程调用默认的构造方法创建Handler， 那么Handler中 Looper 对象就是主线程上的那个 Looper 对象，MessageQueue 也是主线程上的（MessageQueue 是 Looper 的成员变量）。Handler 每次都是往主线程的 MessageQueue 丢消息。然后主线程的这个 Looper 对象调用 loop()方法是在 ActivityThread 中的 main()方法中调用的， 即主线程调用的，它从 MessageQueue 中取出 Message ，  再交给 Handler 处理，处理Message就是在主线程执行的。



比如， 我们在子线程中

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

这样写，显然，这个 Handler 中的 Looper 对象是与当前子线程关联的（在 prepare()中创建好的），然后这个 Looper 对象的 loop()方法跑在子线程， 那么最后的handleMessage肯定是在子线程中执行。

比如我们在子线程中
```java

    class LooperThread extends Thread {
        
        public void run() {
  
           Handler mHandler = new Handler(getMainLooper()) {
               @Override
               public void handleMessage(Message msg) {
                   Log.i("sss", Thread.currentThread().getName());  // main
               }
           };
  
           mHandler.sendEmptyMessage();
  
        }
    }


```

最后的处理是在主线程的。因为 MainLooper 的 loop()方法是跑在主线程上滴。





前面为了思路的流畅，Message 类没有详细说，这里说一下。

## Message

Message有如下的成员变量

```java
    /**
     * User-defined message code so that the recipient can identify 
     * what this message is about. Each {@link Handler} has its own name-space
     * for message codes, so you do not need to worry about yours conflicting
     * with other handlers.
     */
    // 翻译一下:用户定义的Message Code, 用于让接收者可以识别到底是哪个Message.
    // 每一个Handler 对于MessageCode 都有他自己的命名空间, 所以你不必担心你的MessageCode 会和其他的Handler的MessageCode 冲突
    public int what;

    /**
     * arg1 and arg2 are lower-cost alternatives to using
     * {@link #setData(Bundle) setData()} if you only need to store a
     * few integer values.
     */
     // 如果你只需要存储少量的int 值， 那么 相当于setData()而言， arg1 和 arg2 是一种低成本的备选方案。因为setData()是用Bundle的。
    public int arg1; 
    public int arg2;

    // 这个obj 是发送给接收者的任意对象
    public Object obj;

    public Messenger replyTo;
    
    // 这个when 是Message加入到MessageQueue的时间， 主要用于MessageQueue对Message进行排序的。比如我们sendMessage的时候， 可以设置delay值， 这些都是依赖这个when来实现的。
    /*package*/ long when;
    
    // 消息携带的数据， 可以使用setData()方法来设置。
    // 接收者可以使用 getData()来拿到Message 携带的数据。
    /*package*/ Bundle data;
    
    // 这个target 其实是指定了接收者， 也就是这个Message交给谁来处理， 即最后会调用target.handleMessage 来处理这个Message
    /*package*/ Handler target;     
    
    // 这个callback 就是我们在调用  handle.post(Runnable) 的时候， 把runnable对象赋给了这个callback， 接收者拿到这个Message之后会回调这个callback。
    /*package*/ Runnable callback;   
    

    // ---------------------- 消息池相关 ---------------

    // sometimes we store linked lists of these things
    /*package*/ Message next;

    private static Object mPoolSync = new Object();
    private static Message mPool;
    private static int mPoolSize = 0;

    private static final int MAX_POOL_SIZE = 10;
```

下面的几个变量， 主要用于实现了一个消息池。他是用链表的形式做了一个消息池， 里面的Message对象可以重复利用。

next是指向下一个对象的指针。

mPool是这个链表的头指针。

mPoolSize是当前消息池的大小， 也就是链表中有几个对象（元素）

MAX_POOL_SIZE常量， 表示消息池最多能有几个对象。

之后每次obtain都是从消息池中获取Message对象， 使用完之后， 回收到消息池中。具体如何实现， 我们来看obtain()方法。

```java
    /**
     * Same as {@link #obtain()}, but sets the value for the <em>target</em> member on the Message returned.
     * @param h  Handler to assign to the returned Message object's <em>target</em> member.
     * @return A Message object from the global pool.
     */
    public static Message obtain(Handler h) {
        Message m = obtain();
        m.target = h;

        return m;
    }

    /**
     * Same as {@link #obtain(Handler)}, but assigns a callback Runnable on
     * the Message that is returned.
     * @param h  Handler to assign to the returned Message object's <em>target</em> member.
     * @param callback Runnable that will execute when the message is handled.
     * @return A Message object from the global pool.
     */
    public static Message obtain(Handler h, Runnable callback) {
        Message m = obtain();
        m.target = h;
        m.callback = callback;

        return m;
    }

    /**
     * Same as {@link #obtain()}, but sets the values for both <em>target</em> and
     * <em>what</em> members on the Message.
     * @param h  Value to assign to the <em>target</em> member.
     * @param what  Value to assign to the <em>what</em> member.
     * @return A Message object from the global pool.
     */
    public static Message obtain(Handler h, int what) {
        Message m = obtain();
        m.target = h;
        m.what = what;

        return m;
    }

    /**
     * Same as {@link #obtain()}, but sets the values of the <em>target</em>, <em>what</em>, and <em>obj</em>
     * members.
     * @param h  The <em>target</em> value to set.
     * @param what  The <em>what</em> value to set.
     * @param obj  The <em>object</em> method to set.
     * @return  A Message object from the global pool.
     */
    public static Message obtain(Handler h, int what, Object obj) {
        Message m = obtain();
        m.target = h;
        m.what = what;
        m.obj = obj;

        return m;
    }

    /**
     * Same as {@link #obtain()}, but sets the values of the <em>target</em>, <em>what</em>, 
     * <em>arg1</em>, and <em>arg2</em> members.
     * 
     * @param h  The <em>target</em> value to set.
     * @param what  The <em>what</em> value to set.
     * @param arg1  The <em>arg1</em> value to set.
     * @param arg2  The <em>arg2</em> value to set.
     * @return  A Message object from the global pool.
     */
    public static Message obtain(Handler h, int what, int arg1, int arg2) {
        Message m = obtain();
        m.target = h;
        m.what = what;
        m.arg1 = arg1;
        m.arg2 = arg2;

        return m;
    }

    /**
     * Same as {@link #obtain()}, but sets the values of the <em>target</em>, <em>what</em>, 
     * <em>arg1</em>, <em>arg2</em>, and <em>obj</em> members.
     * 
     * @param h  The <em>target</em> value to set.
     * @param what  The <em>what</em> value to set.
     * @param arg1  The <em>arg1</em> value to set.
     * @param arg2  The <em>arg2</em> value to set.
     * @param obj  The <em>obj</em> value to set.
     * @return  A Message object from the global pool.
     */
    public static Message obtain(Handler h, int what, 
            int arg1, int arg2, Object obj) {
        Message m = obtain();
        m.target = h;
        m.what = what;
        m.arg1 = arg1;
        m.arg2 = arg2;
        m.obj = obj;

        return m;
    }
```

Message为我们提供带一系列obtain()方法， 可以传入各种不同的参数， 不过其内部都是调用了无参数的`obtain()`方法。 注释中写的很清楚， 与方法`obtain()`一样， 不过是设置了 target， arg1， arg2， obj等等各种参数。  `@return` 都是说从全局的消息池中返回了一个消息对象。对吧。

我们看看这个无参的obtain()方法， 他到底做了什么。

```java
    /**
     * Return a new Message instance from the global pool. Allows us to
     * avoid allocating new objects in many cases.
     */
     // 从全局的消息池中返回一个Message对象。避免创建太多的新对象。
    public static Message obtain() {
        synchronized (mPoolSync) {
            if (mPool != null) {
                Message m = mPool;
                mPool = m.next;
                m.next = null;
                return m;
            }
        }
        return new Message();
    }

```

先上锁， 然后判断 链表的头指针是否是null。 (mPool就是链表的头指针， 这是一个单向的链表)。如果链表头指针 mPool 是null， 代表全局的消息池中没有Message对象， 那么直接new 一个 Message对象返回。
如果头指针（mPool）不是null， 代表全局的消息池中至少有一个Message对象， 那么就先找个临时变量（Message m） 记录一下这个链表的头部的这个对象（因为我们就要用他了。） 然后把链表头指针往后移动一个（也就是 mPool = m.next）。然后把我们要用的这个对象的next置null， 并且返回。这样我们就拿到了链表头部的Message对象。

那么使用完了之后， 他又是如何回收的呢？ 我们来看`recycle()`方法。

```java
    /**
     * Return a Message instance to the global pool.  You MUST NOT touch
     * the Message after calling this function -- it has effectively been
     * freed.
     */
    public void recycle() {
        synchronized (mPoolSync) {
            if (mPoolSize < MAX_POOL_SIZE) {
                clearForRecycle();
                
                next = mPool;
                mPool = this;
            }
        }
    }

     /*package*/ void clearForRecycle() {
        what = 0;
        arg1 = 0;
        arg2 = 0;
        obj = null;
        replyTo = null;
        when = 0;
        target = null;
        callback = null;
        data = null;
    }
```

先上锁， 如果当前消息池的数量没有到达最大值， 就把当前Message对象里面的东西清空， 然后把当前对象放在链表的头部。（`next = mPool; mPool = this`  这两行就是做了这个， 把原来的链表接在当前Message的屁股后面）。




# 总结


Handler -> Looper -> MessageQueue


首先, 在哪个线程执行 Loop.prepare() 就会在那个线程关联 Looper.


比如 在 子线程 t1 执行 Looper.prepare()
```java
    public static final void prepare() {
        // 调用Looper的私有构造， 创建一个Looper对象， 放到threadlocal中
        // 这里之所以要做成ThreadLocal是因为，prepare方法可能在不同的线程中调用，比如main线程，比如需要loop能力的其他子线程， 不管哪个线程使用的时候，比如get set，都只是针对自己线程里面的变量操作，不影响其他线程
        sThreadLocal.set(new Looper());
    }
```

就是 在子线程  t1  对象的 map 中添加一个 <ThreadLocal, Looper>  的键值对.



prepare 之后, 这个 新的 Looper()对象就是子线程 t1 的内部变量了.  同时,  在 Looper 对象中还新创建了一个 MessageQueue.

然后, 再创建 Handler

```java
    public Handler() {
        mLooper = Looper.myLooper();
        if (mLooper == null) {
            throw new RuntimeException(
                "Can't create handler inside thread that has not called Looper.prepare()");
        }
        mQueue = mLooper.mQueue;
        // callback默认为null
        mCallback = null;
    }

```

用无参构造创建 Handler, Handler 中的 Looper 对象就是当前线程的那个 Looper.

因此, 我们在 子线程 t1 中 new Handler, 那 Handler 中的 Looper 自然就是刚刚 Looper.prepare() 创建出来并且关联到 t1 上的 Looper 对象了.
然后 t1 的 mMessageQueue 就是 Looper 的 MessageQueue





然后, 我们使用 Handler.sendMessage, 就是把消息发送到 t1 线程的 Looper 的 MessageQueue  里.  

之后Looper.loop 会把这个 messageQueue 的消息取出, 并在 t1 上执行.


结论:  Handler 把消息发到哪里, 主要是看这个 Handler 关联的 那个 Looper 呢.


在主线程, ActivityThread 中提前做了 Looper.loop().  因此, 我们在主线程上 new Handler, 消息就是发送到主线程了.

在子线程中, new Handler(getMainLooper()), 这样也行, 直接指定 Handler 关联的 Looper 是主线程的, 消息就发送到主线程的 Looper 的 MessageQueue 里了, 然后主线程处理.

在子线程中, new Handler()  如果不明确指定用哪个 Looper, 就是用当前线程的, 那在此之前一定要先 Looper.prepare, 把 Looper 创建出来并关联上. 否则报错的.


那, 在主线程, 想把消息发到子线程执行呢?
```java
    class LooperThread extends Thread {

        Looper subLooper;
        
        public void run() {
  
            Looper.prepare();
            subLooper = Looper.myLooper();
            Looper.loop();
        }
    }
```

存储一下子线程创建并关联的 Looper, 然后在主线程  创建 Handler 的时候, 指定这个子线程的 Looper,  new Handler(LooperThread.subLooper)  这样就把 Handler 和 子线程的 Looper 关联了. Handler 发消息后, 就是子线程处理了.



 结束.










