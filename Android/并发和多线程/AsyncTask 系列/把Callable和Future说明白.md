---

title: 把Callable和Future说明白

date: 2018-06-10 

categories: 

   - Android
   - 并发和多线程
   - Future 

tags: 

   - Android 
   - Future 

description: ​

---

<!-- TOC -->

- [把 Callable 和 Future 说明白](#把-callable-和-future-说明白)
- [Future<V>](#futurev)
- [FutureTask类](#futuretask类)
- [AsyncTask 的做法](#asynctask-的做法)
- [其他思考](#其他思考)

<!-- /TOC -->


# 把 Callable 和 Future 说明白





Runnable 
```java
public interface Runnable {
    public abstract void run();
}
```


```java
public interface Callable<V> { 
      V   call()   throws Exception; 
} 
```


ExecutorService提供的方法

```java
// submit提交一个实现Callable接口的任务，并且返回封装了异步计算结果的Future
<T> Future<T> submit(Callable<T> task);  
// submit提交一个实现Runnable接口的任务，并且指定了在调用Future的get方法时返回的result类型
// 这里是要获取返回值的, Runnable无法指定返回值, 因此多加了一个参数来指定
<T> Future<T> submit(Runnable task, T result);
Future<?> submit(Runnable task);
```

我们看下他的子类是如何实现的.
```java
// AbstractExecutorService.java
    public Future<?> submit(Runnable task) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<Void> ftask = newTaskFor(task, null);
        execute(ftask);
        return ftask;
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public <T> Future<T> submit(Runnable task, T result) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<T> ftask = newTaskFor(task, result);
        execute(ftask);
        return ftask;
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public <T> Future<T> submit(Callable<T> task) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<T> ftask = newTaskFor(task);
        execute(ftask);
        return ftask;
    }
    
```

```java
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new FutureTask<T>(runnable, value);
    }
```



```java
// ScheduledThreadPoolExecutor.java
    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public <T> Future<T> submit(Runnable task, T result) {
        return schedule(Executors.callable(task, result), 0, NANOSECONDS);
    }
```


除了我们自己实现Callable对象外，我们还可以使用工厂类Executors来把一个Runnable对象包装成Callable对象。Executors工厂类提供的方法如下：

```java
public static Callable<Object> callable(Runnable task)
public static <T> Callable<T> callable(Runnable task, T result)
```

第二个方法, 显然就是 将 Runnable 和 它的返回值 组成了一个 Callable 接口




# Future<V> 

Future<V>接口是用来获取异步计算结果的，说白了就是对具体的Runnable或者Callable对象任务执行的结果进行获取(get()),取消(cancel()),判断是否完成等操作。我们看看Future接口的源码：

```java
public interface Future<V> {
    boolean cancel(boolean mayInterruptIfRunning);
    boolean isCancelled();
    boolean isDone();
    V get() throws InterruptedException, ExecutionException;
    V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;
}
```


# FutureTask类

```java
public class FutureTask<V> implements RunnableFuture<V> {
```

```java
public interface RunnableFuture<V> extends Runnable, Future<V> {
    void run();
}
```



# AsyncTask 的做法


mWorker 是个 Callable

然后创建 mFuture  = FutureTask(mWorker), 把 mWorker(Callable) 传入. 并且重写了 done 方法.


看下 FutureTask 的构造, 如果传入 Callable 就存一下, 如果传入 Runnable, 就转成 Callable 存一下
```java
 public FutureTask(Callable<V> callable) {
        if (callable == null)
            throw new NullPointerException();
        this.callable = callable;
        this.state = NEW;       // ensure visibility of callable
    }

    public FutureTask(Runnable runnable, V result) {
        this.callable = Executors.callable(runnable, result);
        this.state = NEW;       // ensure visibility of callable
    }
```
构建好之后, 

在 AsyncTask.execute() 中

1. onPreExecute();
2. sExecutor.execute(mFuture); 把 FutureTask(mFuture) 丢入 线程池执行.

然后就会执行 FutureTask 的 run 方法
```java
// FutureTask.java
public void run() {
        if (state != NEW ||
            !U.compareAndSwapObject(this, RUNNER, null, Thread.currentThread()))
            return;
        try {
            Callable<V> c = callable;
            if (c != null && state == NEW) {
                V result;
                boolean ran;
                try {
                    result = c.call();
                    ran = true;
                } catch (Throwable ex) {
                    result = null;
                    ran = false;
                    setException(ex);
                }
                if (ran)
                    set(result);
            }
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            runner = null;
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            int s = state;
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
    }
```

我们看到, 在 run 方法中, 拿到 成员 Callable 对象, 并执行了它的 call 方法. 也就是执行了我们传入的 那个 Callable 的 call 方法
然后 , set(result)

```java
    protected void set(V v) {
        if (U.compareAndSwapInt(this, STATE, NEW, COMPLETING)) {
            outcome = v;
            U.putOrderedInt(this, STATE, NORMAL); // final state
            finishCompletion();
        }
    }
```

```java
   private void finishCompletion() {
      ...
      done();

      callable = null;        // to reduce footprint
    }

```

最后调用了 FutureTask 的 done 方法.  因此,  在 done 方法中就可以拿到结果.

AsyncTask 中,  done 中, 做了 发消息给主线程, 在主线程执行:  onPostExecute(result);







# 其他思考

这里是借助 FutureTask 来做的.

想看下直接把 Callable 丢进线程池的情况.  也急速是调用 submit 方法, 其内部是怎么做的.

```java
// AbstractExecutorService.java
    public Future<?> submit(Runnable task) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<Void> ftask = newTaskFor(task, null);
        execute(ftask);
        return ftask;
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public <T> Future<T> submit(Runnable task, T result) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<T> ftask = newTaskFor(task, result);
        execute(ftask);
        return ftask;
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public <T> Future<T> submit(Callable<T> task) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<T> ftask = newTaskFor(task);
        execute(ftask);
        return ftask;
    }
    
```

```java
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new FutureTask<T>(runnable, value);
    }

    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new FutureTask<T>(callable);
    }
```

调用 submit ,传入  Runnable 和 T, 或者传入 Callable, 最后都是 封装成了 FutureTask, 并把 FutureTask 丢入线程池来执行.