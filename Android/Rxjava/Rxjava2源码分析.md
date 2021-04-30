---
title: RxJava2源码分析

date: 2018-07-25

categories: 
   - Rxjava

tags: 
   - Rxjava 

description: ​
---

<!-- TOC -->

- [Single工作原理](#single工作原理)
    - [Single](#single)
    - [map操作符](#map操作符)
- [Observer工作原理](#observer工作原理)
    - [加上 map 操作符](#加上-map-操作符)
- [线程切换](#线程切换)
    - [`subscribeOn()`](#subscribeon)
    - [`observeOn()`](#observeon)
    - [AndroidSchedulers.mainThread()](#androidschedulersmainthread)
- [线程切换总结](#线程切换总结)

<!-- /TOC -->




# Single工作原理


## Single


从简单的Single开始, 可以更容易理解框架. 然后再扩展到 Observable


`Single.just` 是在订阅的时候立刻发射事件


```java
        Single<String> single = Single.just("1");
        single.subscribe(new SingleObserver<String>() {
            @Override
            public void onSubscribe(@NonNull Disposable d) {
            }

            @Override
            public void onSuccess(@NonNull String s) {
                Log.d(TAG, "single just onSuccess:" + s);
            }

            @Override
            public void onError(@NonNull Throwable e) {
            }
        });
```

`Single.just("1")` 是创建一个被观察者对象. `single.subscribe()` 是订阅.

`Single.just()`内部就是简单创建了一个 SingleJust 对象
```java
    @CheckReturnValue
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Single<T> just(final T item) {
        // 判空, 表示发射的值不能为null
        ObjectHelper.requireNonNull(item, "value is null");
        // RxJavaPlugins.onAssembly是hook 操作, 默认什么都不做, 直接返回了传入的参数SingleJust对象
        return RxJavaPlugins.onAssembly(new SingleJust<T>(item));
    }
```

因此, `Single.just()` 方法, 就是创建了一个 SingleJust(被观察者)对象. 

被观察者创建好之后 , 就开始订阅, 调用 被观察者(SingleJust)的  `subscribe(SingleObserver)` 方法.


SingleJust 的 subscribe 方法是继承自 父类 Single 的
```java
// Single.java
    @SchedulerSupport(SchedulerSupport.NONE)
    @Override
    public final void subscribe(SingleObserver<? super T> observer) {
        // 判空, 就是传入的 观察者不能是 null
        ObjectHelper.requireNonNull(observer, "subscriber is null");
        // hook 操作, 就是直接返回了传入的参数 observer
        observer = RxJavaPlugins.onSubscribe(this, observer);
        // 再次判空, 防止在 hook 操作中把observer(观察者)对象给改成 null 了.
        ObjectHelper.requireNonNull(observer, "The RxJavaPlugins.onSubscribe hook returned a null SingleObserver. Please check the handler provided to RxJavaPlugins.setOnSingleSubscribe for invalid null returns. Further reading: https://github.com/ReactiveX/RxJava/wiki/Plugins");
        //...上面的都没啥用
        try {
            // 这里, 真正的订阅操作
            subscribeActual(observer);
        } catch (NullPointerException ex) {
            throw ex;
        } catch (Throwable ex) {
            ...
        }
    }

```

由于 `SingleJust` 重写了 `subscribeActual` 方法, 因此, 是调用 `SingleJust` 的 `subscribeActual`  方法去了

```java
// SingleJust.java
public final class SingleJust<T> extends Single<T> {

    final T value;

    public SingleJust(T value) {
        this.value = value;
    }

    @Override
    protected void subscribeActual(SingleObserver<? super T> observer) {
        observer.onSubscribe(Disposables.disposed());
        observer.onSuccess(value);
    }
}
```
参数observer是什么呢?就是我们调用 subscribe  时候自己写的那个匿名的 SingleObserver对象 (观察者)

可以看到, 就是执行了观察者的 onSubscribe, 表示订阅成功了.

然后直接调用了 观察者的 onSuccess.  


由于 Single.just 只有一个事件, 直接发射, 根本不会失败, 因此, 就通知 观察者成功了.

到这里, Single 的一套订阅发布 流程就完成了.




## map操作符

```java
        Single<Integer> single = Single.just(1);

        Single<String> map = single.map(new Function<Integer, String>() {
            @Override
            public String apply(@NonNull Integer integer) throws Exception {
                return String.valueOf(integer);
            }
        });

        map.subscribe(new SingleObserver<String>() {
            @Override
            public void onSubscribe(@NonNull Disposable d) {
            }
            @Override
            public void onSuccess(@NonNull String s) {
                Log.d(TAG, "single just onSuccess:" + s);
            }
            @Override
            public void onError(@NonNull Throwable e) {
            }
        });
```

开始, `Single.just()` 方法, 创建了一个 SingleJust(被观察者)对象. 

然后调用 SingleJust 对象的 map 方法. SingleJust 的 map 方法是继承自父类 Single 的.

```java
// Single.java
    @CheckReturnValue
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Single<R> map(Function<? super T, ? extends R> mapper) {
        // 先判断传入 map 方法的 Function 是否为空
        ObjectHelper.requireNonNull(mapper, "mapper is null");
        // hook 操作什么也没做, 最终还是返回 new SingleMap()
        return RxJavaPlugins.onAssembly(new SingleMap<T, R>(this, mapper));
    }
```

map 方法最主要的操作, 是新建了一个 SingleMap 对象并返回. 并把this 传入. this 是什么. 就是 SingleJust 对象(被观察者),  也是 map 方法上游的那个被观察者对象.  

同时还传入了 mapper, 也就是我们自己定义的转换器.

map 方法最后返回了这个 SingleMap 对象, 这个 SingleMap 也是一个被观察者, 我们看一下


```java
// SingleMap.java

// SingleMap 是 Single 的子类, 就是个被观察者了
public final class SingleMap<T, R> extends Single<R> {
    // 上游的被观察者, 称为 source
    final SingleSource<? extends T> source;
    // 转换器对象
    final Function<? super T, ? extends R> mapper;
    // 构造, 上面就是调用的这个
    public SingleMap(SingleSource<? extends T> source, Function<? super T, ? extends R> mapper) {
        this.source = source;
        this.mapper = mapper;
    }

    @Override
    protected void subscribeActual(final SingleObserver<? super R> t) {
        source.subscribe(new MapSingleObserver<T, R>(t, mapper));
    }

    static final class MapSingleObserver<T, R> implements SingleObserver<T> {

        final SingleObserver<? super R> t;

        final Function<? super T, ? extends R> mapper;

        MapSingleObserver(SingleObserver<? super R> t, Function<? super T, ? extends R> mapper) {
            this.t = t;
            this.mapper = mapper;
        }

        @Override
        public void onSubscribe(Disposable d) {
            t.onSubscribe(d);
        }

        @Override
        public void onSuccess(T value) {
            R v;
            try {
                v = ObjectHelper.requireNonNull(mapper.apply(value), "The mapper function returned a null value.");
            } catch (Throwable e) {
                Exceptions.throwIfFatal(e);
                onError(e);
                return;
            }

            t.onSuccess(v);
        }

        @Override
        public void onError(Throwable e) {
            t.onError(e);
        }
    }
}
```


好了, 现在我们知道了, map() 方法 是返回了一个被观察者对象(SingleMap)

然后继续执行  subscribe() 方法.  这个subscribe() 方法就是执行了  SingleMap 对象的 subscribe() 方法. 上面的 demo是分开写的, 所以很容易看到这一点.

我们去看 SingleMap的 subscribe() 方法是怎么做的.


```java
// SingleMap.java
    @Override
    protected void subscribeActual(final SingleObserver<? super R> t) {
        source.subscribe(new MapSingleObserver<T, R>(t, mapper));
    }
```

可以看到, 调用 source 的 subscribe方法.  source 是谁, 就是上游的那个被观察者, 在我们这个例子中, 就是 SingleJust 对象. 

调用 SingleJust 的 subscribe 方法, 传入了一个新的观察者. 叫 MapSingleObserver.

```java
// SingleJust.java
public final class SingleJust<T> extends Single<T> {

    final T value;

    public SingleJust(T value) {
        this.value = value;
    }

    @Override
    protected void subscribeActual(SingleObserver<? super T> observer) {
        observer.onSubscribe(Disposables.disposed());
        observer.onSuccess(value);
    }

}
```

在SingleJust 的 subscribe 方法中, 执行 observer的 两个回调. 这个observer 就是上面刚刚创建的新的观察者MapSingleObserver.

我们也看看MapSingleObserver做了什么

```java
// SingleMap.java
 static final class MapSingleObserver<T, R> implements SingleObserver<T> {

        final SingleObserver<? super R> t;

        final Function<? super T, ? extends R> mapper;

        MapSingleObserver(SingleObserver<? super R> t, Function<? super T, ? extends R> mapper) {
            this.t = t;
            this.mapper = mapper;
        }

        @Override
        public void onSubscribe(Disposable d) {
            t.onSubscribe(d);
        }

        @Override
        public void onSuccess(T value) {
             R v;
            try {
                // 这里使用转换器进行转换操作, 将转换的结果传入下游的 onSuccess()方法
                v = ObjectHelper.requireNonNull(mapper.apply(value), "The mapper function returned a null value.");
            } catch (Throwable e) {
               ...
            }
            t.onSuccess(v);
        }

        @Override
        public void onError(Throwable e) {
            t.onError(e);
        }
    }
```

很简单, 它的onSubscribe() 方法 和  onSuccess()方法都是直接回调了 t 的相关方法.  那 t 是谁???   t 就是下游的那个观察者. 在这个例子中, 是我们自己写的匿名观察者对象了


梳理一下:

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/RxJava_map.png)



# Observer工作原理


```java
Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(@NonNull ObservableEmitter<String> emitter) throws Exception {
                emitter.onNext("1");
            }
        }).subscribe(new Observer<String>() {
            @Override
            public void onSubscribe(@NonNull Disposable d) {
            }

            @Override
            public void onNext(@NonNull String s) {
            }

            @Override
            public void onError(@NonNull Throwable e) {
            }
            
            @Override
            public void onComplete() {

            }
        });
```

首先, `new ObservableOnSubscribe()` 创建计划表. 

什么是计划表, 我的理解是:观察者订阅了被观察者之后, 被观察者  需要一直做事情, 某一刻数据变化需要通知观察者了,就告诉一下观察者.

那这里的计划表, 就是被观察者要做的事情. 比如网络请求等.

有了计划表, 传入 Observer.create() 方法, 创建一个被观察者
```java
    public static <T> Observable<T> create(ObservableOnSubscribe<T> source) {
        // 判空, 计划表不能是空的
        ObjectHelper.requireNonNull(source, "source is null");
        // hook 操作, 什么也没做, 直接返回了ObservableCreate对象(被观察者对象), 注意, 计划表作为 source 被ObservableCreate对象存起来了
        return RxJavaPlugins.onAssembly(new ObservableCreate<T>(source));
    }
```

接下来调用 被观察者 ObservableCreate  的 subscribe() 方法, 并传入了一个最下游的匿名观察者
```java
// Observable.java
    @SchedulerSupport(SchedulerSupport.NONE)
    @Override
    public final void subscribe(Observer<? super T> observer) {
        // 判空, 观察者不能为空
        ObjectHelper.requireNonNull(observer, "observer is null");
        try {
            // hook 操作, 啥也没做
            observer = RxJavaPlugins.onSubscribe(this, observer);
            // 再次判空
            ObjectHelper.requireNonNull(observer, "The RxJavaPlugins.onSubscribe hook returned a null Observer. Please change the handler provided to RxJavaPlugins.setOnObservableSubscribe for invalid null returns. Further reading: https://github.com/ReactiveX/RxJava/wiki/Plugins");
            // 实际操作
            subscribeActual(observer);
        } catch (NullPointerException e) { // NOPMD
            throw e;
        } catch (Throwable e) {
            ...
        }
    }
```

实际操作还是调用了  ObservableCreate 的 subscribeActual, 并传入了最下游的匿名观察这
```java
    @Override
    protected void subscribeActual(Observer<? super T> observer) {
        // 拿着观察者, 创建了一个发射器
        CreateEmitter<T> parent = new CreateEmitter<T>(observer);
        // 回调观察者的一个方法. 不重要
        observer.onSubscribe(parent);

        try {
            // 这里, source 是那个计划表. 调用计划表的 subscribe方法, 并传入发射器.
            source.subscribe(parent);
        } catch (Throwable ex) {
            Exceptions.throwIfFatal(ex);
            parent.onError(ex);
        }
    }
```

因此, 我们在计划表里自己写的  `emitter.onNext("1");` 是调用发射器的相关方法, 最终还是调用了 观察者的 onNext 方法. 这样一个流程就完了
```java
       CreateEmitter(Observer<? super T> observer) {
            this.observer = observer;
        }

        @Override
        public void onNext(T t) {
            ...
            if (!isDisposed()) {
                observer.onNext(t);
            }
        }
```


梳理:

1. 创建计划表 ObservableOnSubscribe
2. 创建被观察者 ObservableCreate, 它的 source 属性是: 计划表(ObservableOnSubscribe)
3. 调用被观察者 ObservableCreate  的 subscribe 方法, 传入 最下游的观察者 Observer.
4. 拿着Observer 包装了一个发射器 CreateEmitter, 然后调用 计划表(source)的 subscribe 方法, 并传入发射器
5. 计划表(source)的 subscribe 方法 中执行各种操作, 然后使用发射器通知, 其实就是直接调用 观察者 Observer 的 onNext 方法.


这里就是多了个计划表, 其他的跟 Single 都一样

## 加上 map 操作符

```java
Observable.create(new ObservableOnSubscribe<Integer>() {
            @Override
            public void subscribe(@NonNull ObservableEmitter<Integer> emitter) throws Exception {
                emitter.onNext(1);
            }
        }).map(new Function<Integer, String>() {
            @Override
            public String apply(@NonNull Integer i) throws Exception {
                return String.valueOf(i);
            }
        })
                .subscribe(new Observer<String>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {

                    }

                    @Override
                    public void onNext(@NonNull String s) {

                    }

                    @Override
                    public void onError(@NonNull Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
```

1. 创建计划表 ObservableOnSubscribe
2. 创建被观察者 ObservableCreate, 它的 source 属性是: 计划表(ObservableOnSubscribe)

调用 ObservableCreate 的 map 方法
```java
    public final <R> Observable<R> map(Function<? super T, ? extends R> mapper) {
        ObjectHelper.requireNonNull(mapper, "mapper is null");
        return RxJavaPlugins.onAssembly(new ObservableMap<T, R>(this, mapper));
    }
```

返回一个 被观察者对象 ObservableMap, 它的source 是上游的 那个被观察者:ObservableCreate, 同时给它一个转换器

然后就调用ObservableMap的 subscribe 方法了, 传入了最下游的观察者对象 Observer

最终进入 ObservableMap 的subscribeActual, 这个 t 就是最下游的那个 Observer

```java
    @Override
    public void subscribeActual(Observer<? super U> t) {
        source.subscribe(new MapObserver<T, U>(t, function));
    }
```

看到, 还是创建了一个新的 观察者 MapObserver(t), 包装了最下游的 Observer, 并用这个 MapObserver(t) 去订阅上游的 被观察者.

这个 source 就是上游的那个 ObservableCreate

调用 ObservableCreate  的 subscribe 方法, 传入的参数是:MapObserver
```java
    @Override
    protected void subscribeActual(Observer<? super T> observer) {
        CreateEmitter<T> parent = new CreateEmitter<T>(observer);
        observer.onSubscribe(parent);

        try {
            source.subscribe(parent);
        } catch (Throwable ex) {
            ...
        }
    }
```

使用 参数: MapObserver  创建发射器

然后回调 source 的 subscribe 方法.  对于 ObservableCreate 而言, 这个 source 是: 计划表, 对吧.

因此进入计划表的subscribe() 方法.  

我们在计划表中进行各种操作, 比如网络请求, 最终发射一个事件  emitter.onNext(1);.   内部就是调用 MapObserver.onNext().

这样就通知到最进的一个观察者 MapObserver了.


MapObserver的 onNext 做了什么呢

```java
        @Override
        public void onNext(T t) {
            ...
            try {
                // 使用转换器先转换一下
                v = ObjectHelper.requireNonNull(mapper.apply(t), "The mapper function returned a null value.");
            } catch (Throwable ex) {
                fail(ex);
                return;
            }
            // 通知下游, 这样就调用到最下游的 Observer 的 onNext 方法了
            downstream.onNext(v);
        }
```

梳理:
1. 创建计划表 ObservableOnSubscribe
2. 创建被观察者 ObservableCreate, 它的 source 属性是: 计划表(ObservableOnSubscribe)
3. 调用被观察者 ObservableCreate 的 map 方法, map 方法返回一个 被观察者对象 ObservableMap, 它的source 是上游的 那个被观察者:ObservableCreate, 同时给它一个转换器
4. 调用ObservableMap的 subscribe 方法, 传入最下游的 Observer
5. ObservableMap的 subscribe 方法中, 创建新的 观察者 MapObserver(t), 包装了最下游的 Observer, 并用这个 MapObserver(t) 去订阅上游的 被观察者. 也就是调用上游ObservableCreate的 subscribe, 传入参数:MapObserver
6. 最上游的 ObservableCreate 的 subscribe 方法, 就是调用 计划表的 subscribe 方法了. 
7. 计划表中执行任务, 然后发射事件, 进而通知下游的 MapObserver
8. MapObserver 使用转换器处理完成, 继续通知下游 Observer
9. 进入Observer.onNext().


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/Observable_map.png)


# 线程切换


```java
 Single.just(1)
                .subscribeOn(Schedulers.io())
                //.observeOn(Schedulers.newThread())
                .subscribe(new SingleObserver<String>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                    }

                    @Override
                    public void onSuccess(@NonNull String s) {
                        Log.d(TAG, "single just onSuccess:" + s);
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                    }
                });
```


`subscribeOn(Schedulers.io())` 是指定事件发射的线程. 

多次指定`subscribeOn()` , 只有第一次指定的有效, 其余无效.

`observeOn(Schedulers.newThread())` 是指定下游的操作的线程.

分析源码, 来看看如何实现的.


## `subscribeOn()`

 Single.just(1) 返回一个 SingleJust 对象(被观察对象)

 然后调用 SingleJust 的subscribeOn() 方法, 指定一个线程调度器

```java
    public final Single<T> subscribeOn(final Scheduler scheduler) {
        // 判空, 指定的线程调度器不能为空
        ObjectHelper.requireNonNull(scheduler, "scheduler is null");
        // hook 操作, 没用, 直接返回SingleSubscribeOn对象. 
        return RxJavaPlugins.onAssembly(new SingleSubscribeOn<T>(this, scheduler));
    }
```
创建并返回一个SingleSubscribeOn对象, 显然, SingleSubscribeOn 也是一个被观察者对象. 

同时, this, 也就是 SingleJust 对象传入, 应该是作为 SingleSubscribeOn 的 source 属性了, 也就是上游.

然后调用 SingleSubscribeOn 的 subscribe 方法, 并传入 最下游的观察者 Observer.

SingleSubscribeOn 的 subscribe 方法是继承自父类 Single 的. 最终会调用到 SingleSubscribeOn的 subscribeActual方法.

```java
    @Override
    // 传入的参数 observer 就是我们自己写的最下游的观察者
    protected void subscribeActual(final SingleObserver<? super T> observer) {
        // 创建一个新的 Observer, 这个 parent 还是个 Runnable
        final SubscribeOnObserver<T> parent = new SubscribeOnObserver<T>(observer, source);

        observer.onSubscribe(parent);

        Disposable f = scheduler.scheduleDirect(parent);

        parent.task.replace(f);

    }
```

按照前面的理解:  每次被观察者调用 subscribe 的时候， 都是2 个操作:

1. 先把下游订阅到自己身上。什么是把下游订阅到自己身上？就是持有一个下游观察者的引用。当有事件的时候，方便通知下游的观察者。

2. 自己再创建一个新的观察者， 并调用上游的 subscribe 方法， 把新的观察者订阅到上游去。

不知这里为啥没调用 source.subscribe 呢?

注意, 这里在创建 新的观察者SubscribeOnObserver的时候, 除了传入下游的观察者 observer, 还传入了上游的被观察者 source.  本应该调用 source.subscribe 的, 他这里没调用, 可能是放在其他地方调用了吧..


我们看下SubscribeOnObserver 这个类, 他除了是个观察者, 还是个 Runnable.

```java
    static final class SubscribeOnObserver<T>
    extends AtomicReference<Disposable>
    implements SingleObserver<T>, Disposable, Runnable {

        private static final long serialVersionUID = 7000911171163930287L;

        final SingleObserver<? super T> downstream;

        final SequentialDisposable task;

        final SingleSource<? extends T> source;

        SubscribeOnObserver(SingleObserver<? super T> actual, SingleSource<? extends T> source) {
            this.downstream = actual;
            this.source = source;
            this.task = new SequentialDisposable();
        }

        @Override
        public void onSubscribe(Disposable d) {
            DisposableHelper.setOnce(this, d);
        }

        @Override
        public void onSuccess(T value) {
            downstream.onSuccess(value);
        }

        @Override
        public void onError(Throwable e) {
            downstream.onError(e);
        }

        @Override
        public void dispose() {
            DisposableHelper.dispose(this);
            task.dispose();
        }

        @Override
        public boolean isDisposed() {
            return DisposableHelper.isDisposed(get());
        }

        @Override
        public void run() {
            source.subscribe(this);
        }
```
在他的 run 方法中, 执行了`source.subscribe(this);` . 这样就明确了, 这里 其实是先切换了线程, 然后在对应的线程中执行了 `source.subscribe(this);` 方法, 将自己订阅到上游.

接着看 `scheduler.scheduleDirect(parent);` , 是做计划任务

`scheduler` 就是我们调用`subscribeOn(Schedulers.io())`时候传入的参数, 这里是IoScheduler对象

因此`scheduler.scheduleDirect(parent)`方法调用的是 IoScheduler 的对应方法.

我们看看 `scheduler.scheduleDirect(parent)`方法, 参数 run 就是上面的 SubscribeOnObserver 对象: parent, 要做的事情就是`source.subscribe(this);`
```java
    @NonNull
    public Disposable scheduleDirect(@NonNull Runnable run) {
        return scheduleDirect(run, 0L, TimeUnit.NANOSECONDS);
    }
```

```java
    public Disposable scheduleDirect(@NonNull Runnable run, long delay, @NonNull TimeUnit unit) {
        // createWorker() 是调用 IoScheduler 的方法
        // 创建的 Worker w 是一个 IoScheduler.EventLoopWorker对象
        final Worker w = createWorker();

        // hook 操作, 没用, 还是返回 run 对象
        final Runnable decoratedRun = RxJavaPlugins.onSchedule(run);

        // 将 run 对象 和  EventLoopWorker对象包装成一个 task
        DisposeTask task = new DisposeTask(decoratedRun, w);
        // 这里!!  调用 IoScheduler.EventLoopWorker对象 的 schedule 方法, 并且传入了 task, task 里有 run 对象
        w.schedule(task, delay, unit);

        return task;
    }
```

```java
// IoScheduler.java
    public Worker createWorker() {
        return new EventLoopWorker(pool.get());
    }
```

我们看下IoScheduler.EventLoopWorker对象 的 schedule 方法
```java
public Disposable schedule(@NonNull Runnable action, long delayTime, @NonNull TimeUnit unit) {
            if (tasks.isDisposed()) {
                // don't schedule, we are unsubscribed
                return EmptyDisposable.INSTANCE;
            }

            return threadWorker.scheduleActual(action, delayTime, unit, tasks);
        }
```

醉了, 这个 threadWorker又是个啥呀.

```java
static final class ThreadWorker extends NewThreadWorker {
        private long expirationTime;

        ThreadWorker(ThreadFactory threadFactory) {
            super(threadFactory);
            this.expirationTime = 0L;
        }

        public long getExpirationTime() {
            return expirationTime;
        }

        public void setExpirationTime(long expirationTime) {
            this.expirationTime = expirationTime;
        }
    }
```

ThreadWorker 好像没啥用, 看他的父类 NewThreadWorker
```java
// NewThreadWorker.java
public class NewThreadWorker extends Scheduler.Worker implements Disposable {
    // 线程池哦...ScheduledExecutorService
    private final ScheduledExecutorService executor;

    volatile boolean disposed;

    public NewThreadWorker(ThreadFactory threadFactory) {
        // 线程池哦...
        executor = SchedulerPoolFactory.create(threadFactory);
    }

    @NonNull
    @Override
    public Disposable schedule(@NonNull final Runnable run) {
        return schedule(run, 0, null);
    }

    @NonNull
    @Override
    public Disposable schedule(@NonNull final Runnable action, long delayTime, @NonNull TimeUnit unit) {
        if (disposed) {
            return EmptyDisposable.INSTANCE;
        }
        return scheduleActual(action, delayTime, unit, null);
    }

    public ScheduledRunnable scheduleActual(final Runnable run, long delayTime, @NonNull TimeUnit unit, @Nullable DisposableContainer parent) {
        // 这个 run , 就是上面  的那个 task
        Runnable decoratedRun = RxJavaPlugins.onSchedule(run);

        // 把 run 包装成ScheduledRunnable
        ScheduledRunnable sr = new ScheduledRunnable(decoratedRun, parent);

        if (parent != null) {
            if (!parent.add(sr)) {
                return sr;
            }
        }

        Future<?> f;
        try {
            if (delayTime <= 0) {
                // ScheduledRunnable 当做 Callable 丢到线程池去执行
                f = executor.submit((Callable<Object>)sr);
            } else {
                f = executor.schedule((Callable<Object>)sr, delayTime, unit);
            }
            sr.setFuture(f);
        } catch (RejectedExecutionException ex) {
            if (parent != null) {
                parent.remove(sr);
            }
            RxJavaPlugins.onError(ex);
        }

        return sr;
    }
}
```


我们再回到上面  `w.schedule(task, delay, unit);` 

最终就是把那个 DisposeTask 丢到线程池执行了

DispostTask 的 run 方法
```java
  @Override
        public void run() {
            runner = Thread.currentThread();
            try {
                // 调用SubscribeOnObserver parent 的 run
                // 实际操作是: `source.subscribe(this);` 就是在子线程执行.
                decoratedRun.run();
            } finally {
                dispose();
                runner = null;
            }
        }
```

我们这个例子中, `source.subscribe(this);` 是干了啥呢.

source 是上游的被观察者, 也就是 SingleJust 了.  

也就是说 `SingleJust.subscribe(this)` 这个方法是在我们指定的 io 线程执行.

也就是发射事件在 io 线程执行了.

如果我们没用 Single, 用的是 Observable, 那就意味着计划表的操作是在 我们指定的 io 线程执行.



总结: `subscribeOn()`  切换线程, 是在  source.subscribe()向上游订阅之前切换的. 也就是说, source.subscribe()方法会执行在我们指定的线程上!


那如果多次调用 `subscribeOn()`  来指定线程, 比如我们从下到上  , 有下游 Observer, 中游 Observable , 中上游 Observable, 上游 Observable 四个.

在中游 和 中上游都切换了线程.

那其实到了上游进行事件发射, 肯定按照中上游的线程来.  相当于中游设置的线程被拦截了. 发射事件所在的线程还是中上游指定的.



## `observeOn()`

```java
 Single.just(1)
                //.subscribeOn(Schedulers.io())
                .observeOn(Schedulers.newThread())
                .subscribe(new SingleObserver<String>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                    }
                    @Override
                    public void onSuccess(@NonNull String s) {
                        Log.d(TAG, "single just onSuccess:" + s);
                    }
                    @Override
                    public void onError(@NonNull Throwable e) {
                    }
                });
```
简单来看.

Single.just(1) 返回 SingleJust 被观察对象.

然后调用 SingleJust 的 observeOn() 方法

```java
    public final Single<T> observeOn(final Scheduler scheduler) {
        ObjectHelper.requireNonNull(scheduler, "scheduler is null");
        return RxJavaPlugins.onAssembly(new SingleObserveOn<T>(this, scheduler));
    }
```

上游的被观察者 this, 也就是 SingleJust 对象, 还有指定的 线程调度器 scheduler, 创建一个 SingleObserveOn 对象, 并返回. 

SingleObserveOn 肯定是个被观察对象

scheduler 我们指定的是  NewThreadScheduler对象


我们看 SingleObserveOn 的 关键方法
```java
    protected void subscribeActual(final SingleObserver<? super T> observer) {
        source.subscribe(new ObserveOnSingleObserver<T>(observer, scheduler));
    }
```

直接订阅, 不切换线程, 因为 observeOn 指定的是它下游操作的线程.

因此在订阅的时候, 不需要切换线程. 只有拿到消息, 通知下游的时候, 切换线程就好了.

当拿到消息, 指定一个新的线程, 在新的线程中向下游通知, 那下游自然就在指定的线程运行了.

我们看下, SingleObserveOn 是如何在通知下游的时候切换线程的.

比如通知下游 onSuccess(). 那我的思路就很简单, new 个线程, 然后在这个新的线程上 调用 downstream.onSuccess()就好啦.  

看看它是怎么做的

```java
        @Override
        public void onSuccess(T value) {
            this.value = value;
            Disposable d = scheduler.scheduleDirect(this);
            DisposableHelper.replace(this, d);
        }
```
` Disposable d = scheduler.scheduleDirect(this);` 

这里 scheduler 是 NewThreadScheduler. 调用 NewThreadScheduler的 scheduleDirect方法, 传入的 this 是ObserveOnSingleObserver自己. 本来他想直接调用 downstream.onSuccess 或者 downstream.onError.  但是现在要切换线程了, 他就把自己弄成 Runnable传入, 在 run 方法里 去执行 downstream.onSuccess 或者 downstream.onError

```java
  @Override
        public void run() {
            Throwable ex = error;
            if (ex != null) {
                downstream.onError(ex);
            } else {
                downstream.onSuccess(value);
            }
        }
```

进入NewThreadScheduler的 scheduleDirect方法


```java
 @NonNull
    public Disposable scheduleDirect(@NonNull Runnable run) {
        return scheduleDirect(run, 0L, TimeUnit.NANOSECONDS);
    }

    @NonNull
    public Disposable scheduleDirect(@NonNull Runnable run, long delay, @NonNull TimeUnit unit) {
        final Worker w = createWorker();

        final Runnable decoratedRun = RxJavaPlugins.onSchedule(run);

        DisposeTask task = new DisposeTask(decoratedRun, w);

        w.schedule(task, delay, unit);

        return task;
    }
```

可以看到跟 subscribeOn() 的差不多. 都是走到scheduleDirect 方法. 进而让对应的线程池去执行了这个 task(runnbale). 最终, 想要执行的 downstream.onError / downstream.onSuccess 就是在指定的线程执行了


## AndroidSchedulers.mainThread()

```java
    private static final class MainHolder {
        static final Scheduler DEFAULT
            = new HandlerScheduler(new Handler(Looper.getMainLooper()), false);
    }
```

最终是通过 `Handler(Looper.getMainLooper())`, 也就是主线程的 Handler 来做的


当需要切换线程的时候, 通过 handler.post 来切换到主线程

```java
    @Override
    public Disposable scheduleDirect(Runnable run, long delay, TimeUnit unit) {
        ...
        run = RxJavaPlugins.onSchedule(run);
        ScheduledRunnable scheduled = new ScheduledRunnable(handler, run);
        handler.postDelayed(scheduled, unit.toMillis(delay));
        return scheduled;
    }
```

# 线程切换总结


几种不同的线程调度器

```java
    static {
        SINGLE = RxJavaPlugins.initSingleScheduler(new SingleTask());

        COMPUTATION = RxJavaPlugins.initComputationScheduler(new ComputationTask());

        IO = RxJavaPlugins.initIoScheduler(new IOTask());

        TRAMPOLINE = TrampolineScheduler.instance();

        NEW_THREAD = RxJavaPlugins.initNewThreadScheduler(new NewThreadTask());
    }
```  
最终都是用ScheduledExecutorService来做的

