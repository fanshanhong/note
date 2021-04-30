---
title: RxJava使用

date: 2017-10-25

categories: 
   - Rxjava

tags: 
   - Rxjava 

description: ​
---

<!-- TOC -->

- [Rxjava观察者模式](#rxjava观察者模式)
- [基本实现](#基本实现)
    - [创建Observer(观察者)](#创建observer观察者)
    - [创建 Observable(被观察者)](#创建-observable被观察者)
    - [subscribe(订阅)](#subscribe订阅)
    - [场景例子](#场景例子)
        - [打印字符串数组](#打印字符串数组)
        - [由 id 取得图片并显示](#由-id-取得图片并显示)
- [线程控制(Scheduler)](#线程控制scheduler)
    - [Scheduler 的 API (一)](#scheduler-的-api-一)
    - [Scheduler 的原理 (一)](#scheduler-的原理-一)
- [变换](#变换)
    - [API](#api)
    - [变换的原理：lift()](#变换的原理lift)
    - [compose: 对 Observable 整体的变换](#compose-对-observable-整体的变换)
- [线程控制](#线程控制)
    - [Scheduler 的 API (二)](#scheduler-的-api-二)
    - [Scheduler 的原理（二）](#scheduler-的原理二)
    - [延伸：doOnSubscribe()](#延伸doonsubscribe)

<!-- /TOC -->

# Rxjava观察者模式

RxJava 有四个基本概念：Observable (可观察者，即被观察者)、 Observer (观察者)、 subscribe (订阅)、事件。Observable 和 Observer 通过 subscribe() 方法实现订阅关系，从而 Observable 可以在需要的时候发出事件来通知 Observer。

RxJava 的事件回调方法除了普通事件 onNext() （相当于 onClick() / onEvent()）之外，还定义了两个特殊的事件：onCompleted() 和 onError()。

* onCompleted(): 事件队列完结。RxJava 不仅把每个事件单独处理，还会把它们看做一个队列。RxJava 规定，当不会再有新的 onNext() 发出时，需要触发 onCompleted() 方法作为标志。
* onError(): 事件队列异常。在事件处理过程中出异常时，onError() 会被触发，同时队列自动终止，不允许再有事件发出。
* 在一个正确运行的事件序列中, onCompleted() 和 onError() 有且只有一个，并且是事件序列中的最后一个。需要注意的是，onCompleted() 和 onError() 二者也是互斥的，即在队列中调用了其中一个，就不应该再调用另一个。


# 基本实现


## 创建Observer(观察者)


Observer 即观察者，**它决定事件触发的时候将有怎样的行为**。 RxJava 中的 Observer 接口的实现方式：
```java
// Observer是个接口, 这里创建了一个匿名类的对象:observer
Observer<String> observer = new Observer<String>() {
    @Override
    public void onNext(String s) {
        Log.d(tag, "Item: " + s);
    }

    @Override
    public void onCompleted() {
        Log.d(tag, "Completed!");
    }

    @Override
    public void onError(Throwable e) {
        Log.d(tag, "Error!");
    }
};
```
除了 Observer 接口之外，RxJava 还内置了一个实现了 Observer 的抽象类：Subscriber。 Subscriber 对 Observer 接口进行了一些扩展，但他们的基本使用方式是完全一样的：


```java
// public abstract class Subscriber<T> implements Observer<T>, Subscription{}
// Subscriber 同时实现了Observer接口和  Subscription 接口
Subscriber<String> subscriber = new Subscriber<String>() {
    @Override
    public void onNext(String s) {
        Log.d(tag, "Item: " + s);
    }

    @Override
    public void onCompleted() {
        Log.d(tag, "Completed!");
    }

    @Override
    public void onError(Throwable e) {
        Log.d(tag, "Error!");
    }
};
```

不仅基本使用方式一样，实质上，在 RxJava 的 subscribe 过程中，Observer 也总是会先被转换成一个 Subscriber 再使用。所以如果你只想使用基本功能，选择 Observer 和 Subscriber 是完全一样的。它们的区别对于使用者来说主要有两点：

1. onStart(): 这是 Subscriber 增加的方法。它会在 subscribe 刚开始，而事件还未发送之前被调用，可以用于做一些准备工作，例如数据的清零或重置。这是一个可选方法，默认情况下它的实现为空。需要注意的是，如果对准备工作的线程有要求（例如弹出一个显示进度的对话框，这必须在主线程执行）， onStart() 就不适用了，因为它总是在 subscribe 所发生的线程被调用，而不能指定线程。要在指定的线程来做准备工作，可以使用 doOnSubscribe() 方法，具体可以在后面的文中看到。

也就是在让被观察者(Observable) 执行任务之前, 让观察者先准备一下.  注意, 这是观察者的方法.

2. unsubscribe(): 这是 Subscriber 所实现的另一个接口 Subscription 的方法，用于取消订阅。在这个方法被调用后，Subscriber 将不再接收事件。一般在这个方法调用前，可以使用 isUnsubscribed() 先判断一下状态。 unsubscribe() 这个方法很重要，因为在 subscribe() 之后， Observable(被观察者) 会持有 Subscriber(观察者) 的引用，这个引用如果不能及时被释放，将有内存泄露的风险。所以最好保持一个原则：要在不再使用的时候尽快在合适的地方（例如 onPause() onStop() 等方法中）调用 unsubscribe() 来解除引用关系，以避免内存泄露的发生。



Subscription接口提供了下面两个方法,  onStart() 是 Subscriber 自己增加的方法

```java
public interface Subscription {
    void unsubscribe();
    boolean isUnsubscribed();
}
```


## 创建 Observable(被观察者)

Observable 即被观察者，**它决定什么时候触发事件以及触发怎样的事件**。 RxJava 使用 create() 方法来创建一个 Observable ，并为它定义事件触发规则：

```java
Observable observable = Observable.create(new Observable.OnSubscribe<String>() {
    @Override
    public void call(Subscriber<? super String> subscriber) {
        subscriber.onNext("Hello");
        subscriber.onNext("Hi");
        subscriber.onNext("Aloha");
        subscriber.onCompleted();
    }
});
```

可以看到，这里传入了一个 OnSubscribe 对象作为参数。OnSubscribe 会被存储在返回的 Observable 对象(onSubscribe)中，它的作用相当于一个计划表，当 Observable 被订阅的时候，onSubscribe 的 call() 方法会自动被调用，事件序列就会依照设定依次触发（对于上面的代码，就是观察者Subscriber 将会被调用三次 onNext() 和一次 onCompleted()）。这样，由被观察者调用了观察者的回调方法，就实现了由被观察者向观察者的事件传递，即观察者模式。


create() 方法是 RxJava 最基本的创造事件序列的方法。基于这个方法， RxJava 还提供了一些方法用来快捷创建事件队列，例如：

* just(T...): 将传入的参数依次发送出来。

```java
Observable observable = Observable.just("Hello", "Hi", "Aloha");
// 将会依次调用：
// subscriber.onNext("Hello");
// subscriber.onNext("Hi");
// subscriber.onNext("Aloha");
// subscriber.onCompleted();
```

just 方法内部, 会创建一个Observable.OnSubscribe对象, 赋给 Observable 中的一个变量. 然后 Observable.OnSubscribe 中的 call 方法, 依次调用  三次`subscriber.onNext()`和 一次  `subscriber.onComplete()`



* from(T[]) / from(Iterable<? extends T>) : 将传入的数组或 Iterable 拆分成具体对象后，依次发送出来。

```java

String[] words = {"Hello", "Hi", "Aloha"};
Observable observable = Observable.from(words);
// 将会依次调用：
// subscriber.onNext("Hello");
// subscriber.onNext("Hi");
// subscriber.onNext("Aloha");
// subscriber.onCompleted();
```

上面 just(T...) 的例子和 from(T[]) 的例子，都和之前的 create(OnSubscribe) 的例子是等价的。


## subscribe(订阅)

创建了 Observable 和 Observer 之后，再用 subscribe() 方法将它们联结起来，整条链子就可以工作了。代码形式很简单：

```java
observable.subscribe(observer);
// 或者：
observable.subscribe(subscriber);
```


```java
    public final Subscription subscribe(final Observer<? super T> observer) {
        if (observer instanceof Subscriber) {
            // 如果传入的是 Subscriber 对象 , 直接继续调用subscribe(Subscriber) 方法
            return subscribe((Subscriber<? super T>)observer);
        }
        // 如果传入的不是 subscriber, 那就是个 Observer, 此时创建一个新的Subscriber, 并且它的 onComplete() onNext() 都是直接使用了Observer的对应方法, 就是个委托
        return subscribe(new Subscriber<T>() {

            @Override
            public void onCompleted() {
                observer.onCompleted();
            }

            @Override
            public void onError(Throwable e) {
                observer.onError(e);
            }

            @Override
            public void onNext(T t) {
                observer.onNext(t);
            }

        });
    }
```




Observable.subscribe(Subscriber) 的内部实现是这样的（仅核心代码）：
```java
// 注意：这不是 subscribe() 的源码，而是将源码中与性能、兼容性、扩展性有关的代码剔除后的核心代码。
// 如果需要看源码，可以去 RxJava 的 GitHub 仓库下载。
public Subscription subscribe(Subscriber subscriber) {
    // 先让观察者自己准备一下
    subscriber.onStart();
    
    // 这个onSubscribe变量, 就是在创建 Observable 对象的时候传入的那个计划表.
    // 因此, 这里是让被观察者去执行操作, 必要时候会通知观察者(也就是说在 call 中会调用 subscriber的的 onNext()  onComplete()等方法)
    onSubscribe.call(subscriber);

    // 把观察者返回
    return subscriber;
}
```

可以看到，subscriber() 做了3件事：

1. 调用 Subscriber.onStart() 。这个方法在前面已经介绍过，是一个可选的准备方法。
2. 调用 Observable 中的 OnSubscribe.call(Subscriber) 。在这里，事件发送的逻辑开始运行。从这也可以看出，在 RxJava 中， Observable 并不是在创建的时候就立即开始发送事件，而是在它被订阅的时候，即当 subscribe() 方法执行的时候。
3. 将传入的 Subscriber 作为 Subscription 返回。这是为了方便 unsubscribe().



除了 `Observable.subscribe(Observer)` 和 `Observable.subscribe(Subscriber)` ，subscribe() 还支持不完整定义的回调，RxJava 会自动根据定义创建出 Subscriber 。形式如下：

```java
Action1<String> onNextAction = new Action1<String>() {
    // onNext()
    @Override
    public void call(String s) {
        Log.d(tag, s);
    }
};
Action1<Throwable> onErrorAction = new Action1<Throwable>() {
    // onError()
    @Override
    public void call(Throwable throwable) {
        // Error handling
    }
};
Action0 onCompletedAction = new Action0() {
    // onCompleted()
    @Override
    public void call() {
        Log.d(tag, "completed");
    }
};

// 自动创建 Subscriber ，并使用 onNextAction 来定义 onNext()
observable.subscribe(onNextAction);
// 自动创建 Subscriber ，并使用 onNextAction 和 onErrorAction 来定义 onNext() 和 onError()
observable.subscribe(onNextAction, onErrorAction);
// 自动创建 Subscriber ，并使用 onNextAction、 onErrorAction 和 onCompletedAction 来定义 onNext()、 onError() 和 onCompleted()
observable.subscribe(onNextAction, onErrorAction, onCompletedAction);
```

简单解释一下这段代码中出现的 Action1 和 Action0。 Action0 是 RxJava 的一个接口，它只有一个方法 call()，这个方法是无参无返回值的；由于 onCompleted() 方法也是无参无返回值的，因此 Action0 可以被当成一个**包装对象**，将 onCompleted() 的内容打包起来将自己作为一个参数传入 subscribe() 以实现不完整定义的回调。这样其实也可以看做将 onCompleted()方法作为参数传进了 subscribe()，相当于其他某些语言中的『闭包』。 

Action1 也是一个接口，它同样只有一个方法 call(T param)，这个方法也无返回值，但有一个参数；与 Action0 同理，由于 onNext(T obj) 和 onError(Throwable error) 也是单参数无返回值的，因此 Action1 可以将 onNext(obj) 和 onError(error) 打包起来传入 subscribe() 以实现不完整定义的回调。事实上，虽然 Action0 和 Action1 在 API 中使用最广泛，但 RxJava 是提供了多个 ActionX 形式的接口 (例如 Action2, Action3) 的，它们可以被用以包装不同的无返回值的方法。

看下源码就好理解了:
```java
// Action.java
    public interface Action1<T> extends Action {
        void call(T t);
    }

// Observable.java
    public final Subscription subscribe(final Action1<? super T> onNext) {
        if (onNext == null) {
            throw new IllegalArgumentException("onNext can not be null");
        }

        return subscribe(new Subscriber<T>() {

            @Override
            public final void onCompleted() {
                // do nothing
            }

            @Override
            public final void onError(Throwable e) {
                throw new OnErrorNotImplementedException(e);
            }

            @Override
            public final void onNext(T args) {
                onNext.call(args);
            }

        });
    }
```

当我们调用subscribe, 并且参数只有一个 Action1, 内部就是创建了一个Subscriber, 并且把传入的 Action1 的 call 方法作为 Subscriber的 onNext 实现, 另外, 给onCompleted() 和 onError() 有默认的实现. 然后调用 subscribe(Subscriber subscribe) 方法.  归根结底, 最终都是要调用: subscribe(Subscriber subscribe) 方法的, 这个里面进行真正的逻辑实现.

当我们调用 subscribe, 传入两个 Action1  的参数, 内部也是创建一个 Subscriber对象. 把第一个 Action1 当做 Subscriber对象的 onNext, 把第二个 Action1, 当做 Subscriber对象的 onError, 然后 onComplete() 给一个默认实现.  最后, 把创建好的 Subscriber对象 传入 subscribe, 调用 subscribe(Subscriber subscribe) 方法.  归根结底, 最终都是要调用: subscribe(Subscriber subscribe) 方法的, 这个里面进行真正的逻辑实现.

```java
    public final Subscription subscribe(final Action1<? super T> onNext, final Action1<Throwable> onError) {
        if (onNext == null) {
            throw new IllegalArgumentException("onNext can not be null");
        }
        if (onError == null) {
            throw new IllegalArgumentException("onError can not be null");
        }

        return subscribe(new Subscriber<T>() {

            @Override
            public final void onCompleted() {
                // do nothing
            }

            @Override
            public final void onError(Throwable e) {
                onError.call(e);
            }

            @Override
            public final void onNext(T args) {
                onNext.call(args);
            }

        });
    }
```


## 场景例子


### 打印字符串数组
将字符串数组 names 中的所有字符串依次打印出来：

```java
String[] names = ...;
Observable.from(names)
    .subscribe(new Action1<String>() {
        @Override
        public void call(String name) {
            Log.d(tag, name);
        }
    });
```

这怎么理解.

分开两步哦.
1. `Observable observable = Observable.from(names);`
2. `observable.subscribe(Action1);`


第一步, from

参照一下 Observable.create()

```java
Observable observable = Observable.create(new Observable.OnSubscribe<String>() {
    @Override
    public void call(Subscriber<? super String> subscriber) {
        subscriber.onNext("Hello");
        subscriber.onNext("Hi");
        subscriber.onNext("Aloha");
        subscriber.onCompleted();
    }
});
```
先创建一个 Observable.OnSubscribe 对象 , 我们称之为计划表对象, 传入 create 方法.  在 Observable.create() 方法中, 创建 Observable, 并将上面创建好的计划表对象赋给 Observable 中的onSubscribe变量(计划表变量).

那 from 内部, 也是会创建一个 Observable.OnSubscribe (计划表)对象 和 Observable对象, 并将计划表对象 赋给 Observable 中的计划表变量. 在 Observable.OnSubscribe 中的 call 方法, 会遍历数组, 多次调用 subscriber.onNext() 和 一次  `subscriber.onComplete()`

第一步完成后, 我们就创建好了 Observable(被观察者) 对象, 同时, Observable 中的计划表有有值了

第二步, `observable.subscribe(Action1);`

调用observable.subscribe, 并且参数只有一个 Action1, 内部是创建了一个Subscriber, 并且把传入的 Action1 的 call 方法作为 Subscriber 的 onNext 实现, 另外, 给onCompleted() 和 onError() 有默认的实现. 然后调用 subscribe(Subscriber subscribe) 方法.  归根结底, 最终都是要调用: subscribe(Subscriber subscribe) 方法的, 这个里面进行真正的逻辑实现.


下面 subscribe 方法会做三件事
```java
public Subscription subscribe(Subscriber subscriber) {
    // 先让观察者自己准备一下
    subscriber.onStart();
    
    // 让计划表执行操作, 因为是 from 的方式创建的, Observable.OnSubscribe 中的 call 方法, 会遍历数组, 多次调用 subscriber.onNext() 和 一次  `subscriber.onComplete()`
    onSubscribe.call(subscriber);

    // 把观察者返回
    return subscriber;
}
```

计划表的 call 方法,  调用subscriber.onNext() , 观察者就被通知到了.


### 由 id 取得图片并显示

由指定的一个 drawable 文件 id drawableRes 取得图片，并显示在 ImageView 中，并在出现异常的时候打印 Toast 报错：

```java
int drawableRes = ...;
ImageView imageView = ...;
Observable.create(new OnSubscribe<Drawable>() {
    @Override
    public void call(Subscriber<? super Drawable> subscriber) {
        Drawable drawable = getTheme().getDrawable(drawableRes));
        subscriber.onNext(drawable);
        subscriber.onCompleted();
    }
}).subscribe(new Observer<Drawable>() {
    @Override
    public void onNext(Drawable drawable) {
        imageView.setImageDrawable(drawable);
    }

    @Override
    public void onCompleted() {
    }

    @Override
    public void onError(Throwable e) {
        Toast.makeText(activity, "Error!", Toast.LENGTH_SHORT).show();
    }
});
```


在 RxJava 的默认规则中，事件的发出和消费都是在同一个线程的。也就是说，如果只用上面的方法，实现出来的只是一个同步的观察者模式。观察者模式本身的目的就是『后台处理，前台回调』的异步机制，因此异步对于 RxJava 是至关重要的。而要实现异步，则需要用到 RxJava 的另一个概念： Scheduler 。

# 线程控制(Scheduler)


在不指定线程的情况下， RxJava 遵循的是线程不变的原则，即：在哪个线程调用 subscribe()，就在哪个线程生产事件；在哪个线程生产事件，就在哪个线程消费事件。如果需要切换线程，就需要用到 Scheduler （调度器）。


## Scheduler 的 API (一)

在RxJava 中，Scheduler ——调度器，相当于线程控制器，RxJava 通过它来指定每一段代码应该运行在什么样的线程。RxJava 已经内置了几个 Scheduler ，它们已经适合大多数的使用场景：

* Schedulers.immediate(): 直接在当前线程运行，相当于不指定线程。这是默认的 Scheduler。

* Schedulers.newThread(): 总是启用新线程，并在新线程执行操作。

* Schedulers.io(): I/O 操作（读写文件、读写数据库、网络信息交互等）所使用的 Scheduler。行为模式和 newThread() 差不多，区别在于 io() 的内部实现是是用一个无数量上限的线程池，可以重用空闲的线程，因此多数情况下 io() 比 newThread() 更有效率。不要把计算工作放在 io() 中，可以避免创建不必要的线程。

* Schedulers.computation(): 计算所使用的 Scheduler。这个计算指的是 CPU 密集型计算，即不会被 I/O 等操作限制性能的操作，例如图形的计算。这个 Scheduler 使用的固定的线程池，大小为 CPU 核数。不要把 I/O 操作放在 computation() 中，否则 I/O 操作的等待时间会浪费 CPU。

* 另外， Android 还有一个专用的 AndroidSchedulers.mainThread()，它指定的操作将在 Android 主线程运行。



有了这几个 Scheduler ，就可以使用 subscribeOn() 和 observeOn() 两个方法来对线程进行控制了。 

* subscribeOn(): 指定 subscribe() 所发生的线程，即 Observable.OnSubscribe 被激活时所处的线程。或者叫做事件产生的线程。 
* observeOn(): 指定 Subscriber 所运行在的线程。或者叫做事件消费的线程。


```java
Observable.just(1, 2, 3, 4)
    .subscribeOn(Schedulers.io()) // 指定 subscribe() 发生在 IO 线程
    .observeOn(AndroidSchedulers.mainThread()) // 指定 Subscriber 的回调发生在主线程
    .subscribe(new Action1<Integer>() {
        @Override
        public void call(Integer number) {
            Log.d(tag, "number:" + number);
        }
    });
```

上面这段代码中，由于 subscribeOn(Schedulers.io()) 的指定，被创建的事件的内容 1、2、3、4 将会在 IO 线程发出；

也就是 那个计划表的 call 方法执行的线程是在 io 线程, call 里面应该会遍历 1234, 然后依次 onNext(1) onNext(2) onNext(3) onNext(4) onComplete().

而由于 observeOn(AndroidScheculers.mainThread()) 的指定，因此 subscriber 数字的打印将发生在主线程。

事实上，这种在 subscribe() 之前写上两句 subscribeOn(Scheduler.io())和 observeOn(AndroidSchedulers.mainThread()) 的使用方式非常常见，它适用于多数的 『后台线程取数据，主线程显示』的程序策略。


由于使用 just 或者 from, 看不到 生产事件执行的线程信息, 我使用 create , 打印了一些线程信息来验证
```java

        Observable.create(new Observable.OnSubscribe<Integer>() {
            @Override
            public void call(Subscriber<? super Integer> subscriber) {
                Log.d("=====", "发射1: thread:" + Thread.currentThread().getName());
                subscriber.onNext(1);
                Log.d("=====", "发射2: thread:" + Thread.currentThread().getName());
                subscriber.onNext(2);
                Log.d("=====", "发射3: thread:" + Thread.currentThread().getName());
                subscriber.onNext(3);
                Log.d("=====", "发射4: thread:" + Thread.currentThread().getName());
                subscriber.onNext(4);
                Log.d("=====", "发射完成: thread:" + Thread.currentThread().getName());
                subscriber.onCompleted();
            }
        })
                .subscribeOn(Schedulers.io()) // 指定 subscribe() 发生在 IO 线程
                .observeOn(AndroidSchedulers.mainThread()) // 指定 Subscriber 的回调发生在主线程
                .subscribe(new Action1<Integer>() {
                    @Override
                    public void call(Integer number) {
                        Log.d("=====", "number:" + number + "  thread:" + Thread.currentThread().getName());
                    }
                });
```


而前面提到的由图片 id 取得图片并显示的例子，如果也加上这两句：
```java
int drawableRes = ...;
ImageView imageView = ...;
Observable.create(new OnSubscribe<Drawable>() {
    @Override
    public void call(Subscriber<? super Drawable> subscriber) {
        Drawable drawable = getTheme().getDrawable(drawableRes));
        subscriber.onNext(drawable);
        subscriber.onCompleted();
    }
})
.subscribeOn(Schedulers.io()) // 指定 subscribe() 发生在 IO 线程
.observeOn(AndroidSchedulers.mainThread()) // 指定 Subscriber 的回调发生在主线程
.subscribe(new Observer<Drawable>() {
    @Override
    public void onNext(Drawable drawable) {
        imageView.setImageDrawable(drawable);
    }

    @Override
    public void onCompleted() {
    }

    @Override
    public void onError(Throwable e) {
        Toast.makeText(activity, "Error!", Toast.LENGTH_SHORT).show();
    }
});
```

那么，加载图片将会发生在 IO 线程，而设置图片则被设定在了主线程。这就意味着，即使加载图片耗费了几十甚至几百毫秒的时间，也不会造成丝毫界面的卡顿。


##  Scheduler 的原理 (一)
RxJava 的 Scheduler API 很方便，也很神奇（加了一句话就把线程切换了，怎么做到的？而且 subscribe() 不是最外层直接调用的方法吗，它竟然也能被指定线程？）。然而 Scheduler 的原理需要放在后面讲，因为它的原理是以下一节《变换》的原理作为基础的。



# 变换


所谓变换，就是将事件序列中的对象或整个序列进行加工处理，转换成不同的事件或事件序列。


## API

首先看一个 map() 的例子：
```java
Observable.just("images/logo.png") // 输入类型 String
    .map(new Func1<String, Bitmap>() {
        @Override
        public Bitmap call(String filePath) { // 参数类型 String
            return getBitmapFromPath(filePath); // 返回类型 Bitmap
        }
    })
    .subscribe(new Action1<Bitmap>() {
        @Override
        public void call(Bitmap bitmap) { // 参数类型 Bitmap
            showBitmap(bitmap);
        }
    });
```

这里出现了一个叫做 Func1 的类。它和 Action1 非常相似，也是 RxJava 的一个接口，用于包装含有一个参数的方法。 Func1 和 Action 的区别在于， Func1 包装的是有返回值的方法。另外，和 ActionX 一样， FuncX 也有多个，用于不同参数个数的方法。FuncX 和 ActionX 的区别在 FuncX 包装的是有返回值的方法。

可以看到，map() 方法将参数中的 String 对象转换成一个 Bitmap 对象后返回，而在经过 map() 方法后，事件的参数类型也由 String 转为了 Bitmap。这种直接变换对象并返回的，是最常见的也最容易理解的变换。

不过 RxJava 的变换远不止这样，它不仅可以针对事件对象，还可以针对整个事件队列，这使得 RxJava 变得非常灵活。我列举几个常用的变换：

* map(): 事件对象的直接变换，具体功能上面已经介绍过。它是 RxJava 最常用的变换。

* flatMap()

应该是和 Kotlin 和 flatMap 或者 flaten 差不多?

还有, LiveData 中也有 map 和 switchMap 


 首先假设这么一种需求：假设有一个数据结构『学生』，现在需要打印出一组学生的名字。实现方式很简单：

```java
Student[] students = ...;
Subscriber<String> subscriber = new Subscriber<String>() {
    @Override
    public void onNext(String name) {
        Log.d(tag, name);
    }
    ...
};
Observable.from(students)
    .map(new Func1<Student, String>() {
        @Override
        public String call(Student student) {
            return student.getName();
        }
    })
    .subscribe(subscriber);
```


再假设：如果要打印出每个学生所需要修的所有课程的名称呢？（需求的区别在于，每个学生只有一个名字，但却有多个课程。）首先可以这样实现：

```java
Student[] students = ...;
Subscriber<Student> subscriber = new Subscriber<Student>() {
    @Override
    public void onNext(Student student) {
        // 在观察者中 处理每个 Student 事件
        List<Course> courses = student.getCourses();

        // 打印所有的课程
        for (int i = 0; i < courses.size(); i++) {
            Course course = courses.get(i);
            Log.d(tag, course.getName());
        }
    }
    ...
};
Observable.from(students) // 把每个 Student 作为事件发射了
    .subscribe(subscriber);
```

那么如果我不想在 Subscriber 中使用 for 循环，而是希望 Subscriber 中直接传入单个的 Course 对象呢（这对于代码复用很重要）？用 map() 显然是不行的，因为 map() 是一对一的转化，而我现在的要求是一对多的转化。那怎么才能把一个 Student 转化成多个 Course 呢？

这个时候，就需要用 flatMap() 了：
```java
Student[] students = ...;
Subscriber<Course> subscriber = new Subscriber<Course>() {
    @Override
    public void onNext(Course course) {
        Log.d(tag, course.getName());
    }
    ...
};

Observable.from(students)// 发射每个学生
    .flatMap(new Func1<Student, Observable<Course>>() {
        @Override
        public Observable<Course> call(Student student) {
            // 注意这里返回的是Observable对象
            return Observable.from(student.getCourses());
        }
    })
    .subscribe(subscriber);
```

理一下:

比如有三个学生 ABC,   每个学生 2 个课程(语文和数学)

先是发射学生A, 然后进入 flatMap 中的 call 方法进行变化.  在 flatMap 的 call 中, 拿到 A 学生的课程, 把课程作为事件, 依次发射.

    > 也就是对于学生 A, 要先发射 一次  语文课程, 发射后会进入 subscriber 的 onNext 
    > 再发射数学课程, 然后也进入subscriber 的 onNext 
    
然后是学生 B,进入 flatMap 中的 call 方法进行变化.  在 flatMap 的 call 中, 拿到 B 学生的课程, 把课程作为事件, 依次发射. 跟 A 一样,会发射两次课程

C 同理.

这样, 三个学生, 每个学生 2 门课程, 就相当于发射了 6 次 事件.  就是对每个学生事件进行变换, 变换成了 2 个课程事件.


现在看来, 这个 flatMap , 跟 Kotlin 的 flatMap 差不多, 都是平铺开来.

另外, 跟 LiveData 的 switchMap 也挺像, 都是返回了一个新的事件.

还有 协程也有 flatMap 和 flatten...

都差不多.



由于可以在嵌套的 Observable 中添加异步代码， flatMap() 也常用于嵌套的异步操作，例如嵌套的网络请求。示例代码（Retrofit + RxJava）：
```java
networkClient.token() // 返回 Observable<String>，在订阅时请求 token，并在响应后发送 token
    .flatMap(new Func1<String, Observable<Messages>>() {
        @Override
        public Observable<Messages> call(String token) {
            // 返回 Observable<Messages>，在订阅时请求消息列表，并在响应后发送请求到的消息列表
            return networkClient.messages();
        }
    })
    .subscribe(new Action1<Messages>() {
        @Override
        public void call(Messages messages) {
            // 处理显示消息列表
            showMessages(messages);
        }
    });
```

就相当于是串行请求, 先 获取 token, 成功后, 再获取 messages 列表


Rxjava 的串行请求还可以用: concat 吧??


* throttleFirst(): 在每次事件触发后的一定时间间隔内丢弃新的事件。常用作去抖动过滤，例如按钮的点击监听器：
```java
RxView.clickEvents(button) // RxBinding 代码，后面的文章有解释 
    .throttleFirst(500, TimeUnit.MILLISECONDS) // 设置防抖间隔为 500ms 
    .subscribe(subscriber); 
```
再也不怕用户手抖点开两个重复的界面啦。


## 变换的原理：lift()

这些变换虽然功能各有不同，但实质上都是针对事件序列的处理和再发送。而在 RxJava 的内部，它们是基于同一个基础的变换方法： lift(Operator)。首先看一下 lift() 的内部实现（仅核心代码）：   lift(Operator) 是 Observable的方法
```java
// Observable.java
// 注意：这不是 lift() 的源码，而是将源码中与性能、兼容性、扩展性有关的代码剔除后的核心代码。
// 如果需要看源码，可以去 RxJava 的 GitHub 仓库下载。
public <R> Observable<R> lift(Operator<? extends R, ? super T> operator) {
    
    // 这里创建新的 Observable 
    return Observable.create(new OnSubscribe<R>() {
        @Override
        public void call(Subscriber subscriber) {
            Subscriber newSubscriber = operator.call(subscriber);
            newSubscriber.onStart();
            onSubscribe.call(newSubscriber);
        }
    });
}
```
一般, 我们先 `Observable.create(new Observable.OnSubscribe());`

这样, 创建一个 Observable, 并且该 Observable 持有计划表 onSubscribe. 这两个是**原始的**


然后 lift()

这段代码很有意思：它生成了一个新的 Observable 并返回，而且创建新 Observable 所用的参数 OnSubscribe 的也是新创建的.


当调用 lift() 时：
1. lift() 创建了一个 新的 Observable 后，加上之前的原始 Observable，已经有两个 Observable 了；

2. 而同样地，新 Observable 里的新 OnSubscribe 加上之前的   原始 Observable 中的原始 OnSubscribe，也就有了两个 OnSubscribe；

3. 当用户调用经过 lift() 后的 Observable 的 `subscribe(final Observer<? super T> observer)` 的时候，使用的是 lift() 所返回的新的 Observable.  于是  当  调用 subscribe 的时候, 它所触发的 onSubscribe.call(subscriber)，也是用的新 Observable 中的新 OnSubscribe，即在 lift() 中生成的那个 OnSubscribe；

4.  这样就进入上面 lift()方法中的 新创建的 OnSubscribe  的 call 方法了.  注意, 这个参数 subscriber 是我们调用 subscribe 的时候传入的 Observer对象, 也叫他原始 Observer对象 对象把.

    在这个 call() 方法里， 利用 operator.call(subscriber) 生成了一个新的 Subscriber（Operator 就是在这里，通过自己的 call() 方法将新 Subscriber 和原始 Subscriber 进行关联，并插入自己的『变换』代码以实现变换），然后利用这个新 Subscriber 向原始 Observable 进行订阅。

    可以想象, 在 operator.call 方法中, 应该就做了一些处理, 同时把原始Observer对象记录在newSubscriber中. 因为后续 newSubscriber 肯定还要再去调用 原始 Observer 的方法的.

    然后 调用 ` onSubscribe.call(newSubscriber);`  这个onSubscribe ，就是指的原始 Observable 中的原始 OnSubscribe ，这样就进入了原始 OnSubscribe的 call 方法



这样就实现了 lift() 过程，有点像一种代理机制，通过事件拦截和处理实现事件序列的变换。


精简掉细节的话，也可以这么说：在 Observable 执行了 lift(Operator) 方法之后，会返回一个新的 Observable，这个新的 Observable 会像一个代理一样，负责接收原始的 Observable 发出的事件，并在处理后发送给 Subscriber。



举一个具体的 Operator 的实现。下面这是一个将事件中的 Integer 对象转换成 String 的例子，仅供参考：

```java
observable.lift(new Observable.Operator<String, Integer>() {
    @Override
    public Subscriber<? super Integer> call(final Subscriber<? super String> subscriber) {
        // 将事件序列中的 Integer 对象转换为 String 对象
        return new Subscriber<Integer>() {
            @Override
            public void onNext(Integer integer) {
                subscriber.onNext("" + integer);
            }

            @Override
            public void onCompleted() {
                subscriber.onCompleted();
            }

            @Override
            public void onError(Throwable e) {
                subscriber.onError(e);
            }
        };
    }
});
```

针对这个例子, 写了一个简答的分析, 梳理一下 lift 的执行过程, 也可以运行验证一下, 看看分析的对不对
```java
// ===================Step1:
        // 这里创建原始Observable
        Observable.create(new Observable.OnSubscribe<Integer>() {

            // 这个是原始的 计划表(onSubscribe)
            @Override
            public void call(Subscriber<? super Integer> subscriber) {
                //  ===================Step7:
                Log.d("=====", "1111");

                // 这个 subscriber 是新的 观察者.  这样就调用了新的观察者的 onNext. 在新的观察者的 onNext 中, 会调用原始的观察者的 onNext, 这样就回去了
                subscriber.onNext(1);
                subscriber.onCompleted();

            }
        }).lift(new Observable.Operator<String, Integer>() {
            // ===================Step2:
            // 开始变换, 主要是创建了一个新的 Observable, 还有个新的计划表(onSubscribe) , 并将新的Observable返回, lift 方法就结束了
            // call 方法的调用是在发射事件的时候才会触发, 先不管
            //      return Observable.create(new OnSubscribe<R>() {
            //          @Override
            //          public void call(Subscriber subscriber) {
            //          ===================Step4:
            //              Subscriber newSubscriber = operator.call(subscriber); // 这里, 执行了把我们最下面指定的那个匿名观察者传入了 call 方法

            //          ===================Step6:
            //              newSubscriber.onStart();// 让新的观察者也准备一下
            //              onSubscribe.call(newSubscriber);// 调用原始的  计划表(onSubscribe)的 call 方法, 传入了新的观察者
            //
            //          }
            //      });

            @Override
            public Subscriber<? super Integer> call(final Subscriber<? super String> subscriber) {
                //===================Step5:

                // 将事件序列中的 Integer 对象转换为 String 对象

                // 返回了一个新的 观察者.  这个观察者就是做了一些转换, 然后调用了 我们之前指定的匿名观察的相关方法
                return new Subscriber<Integer>() {
                    @Override
                    public void onNext(Integer integer) {
                        //===================Step9:
                        // 这里是调用了原始的观察者的 onNext
                        subscriber.onNext("" + (integer + 1));
                    }

                    @Override
                    public void onCompleted() {
                        subscriber.onCompleted();
                    }

                    @Override
                    public void onError(Throwable e) {
                        subscriber.onError(e);
                    }
                };
            }
        }).subscribe(new Subscriber<String>() {
            // ===================Step3:
            // lift 方法之后, 拿到新的 Observable, 然后对新的 Observable 调用 subscribe, 传入一个匿名的观察者, 就是我们这里写的这个.
            // 现在就开始执行 subscribe 的逻辑了, 应该是三步:
            //
            // public Subscription subscribe(Subscriber subscriber) {
            //    // 这里走的是 下面的 onStart
            //    subscriber.onStart();
            //    // 这个onSubscribe变量, 是当前 Observable 对象的计划表变量, 当前 Observable 是哪个? 就是 lift 返回的那个新的 Observable
            //    // 参数subscriber 就是我们这个匿名的 观察者
            //    onSubscribe.call(subscriber); // 因此, 这里就跑去执行 lift 中新的 Observable 中的 onSubscribe变量的 call 方法了.
            //
            //    return subscriber;
            //}

            @Override
            public void onStart() {
                Log.d("====", "hhhh");
            }

            @Override
            public void onCompleted() {
                Log.d("=====", "Observer onCompleted:");
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onNext(String s) {
                // ===================Step10:
                Log.d("=====", "Observer onNext:" + s);
            }
        });
```

代码大体知道了, 再看下面这个图, 就一目了然了

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/lift1.webp)


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/lift2.webp)



感觉像是个责任链模式..



## compose: 对 Observable 整体的变换


除了 lift() 之外， Observable 还有一个变换方法叫做 compose(Transformer)。它和 lift() 的区别在于， lift() 是针对事件项和事件序列的，而 compose() 是针对 Observable 自身进行变换。举个例子，假设在程序中有多个 Observable ，并且他们都需要应用一组相同的 lift() 变换。你可以这么写：

```java
observable1
    .lift1()
    .lift2()
    .lift3()
    .lift4()
    .subscribe(subscriber1);
observable2
    .lift1()
    .lift2()
    .lift3()
    .lift4()
    .subscribe(subscriber2);
observable3
    .lift1()
    .lift2()
    .lift3()
    .lift4()
    .subscribe(subscriber3);
observable4
    .lift1()
    .lift2()
    .lift3()
    .lift4()
    .subscribe(subscriber4);
```

你觉得这样太不软件工程了，于是你改成了这样：

```java
private Observable liftAll(Observable observable) {
    return observable
        .lift1()
        .lift2()
        .lift3()
        .lift4();
}
...
liftAll(observable1).subscribe(subscriber1);
liftAll(observable2).subscribe(subscriber2);
liftAll(observable3).subscribe(subscriber3);
liftAll(observable4).subscribe(subscriber4);
```

可是 Observable 被一个方法包起来，这种方式对于 Observale 的灵活性似乎还是增添了那么点限制。怎么办？这个时候，就应该用 compose() 来解决了：

```java
public class LiftAllTransformer implements Observable.Transformer<Integer, String> {
    @Override
    public Observable<String> call(Observable<Integer> observable) {
        return observable
            .lift1()
            .lift2()
            .lift3()
            .lift4();
    }
}
...
Transformer liftAll = new LiftAllTransformer();
observable1.compose(liftAll).subscribe(subscriber1);
observable2.compose(liftAll).subscribe(subscriber2);
observable3.compose(liftAll).subscribe(subscriber3);
observable4.compose(liftAll).subscribe(subscriber4);
```


像上面这样，使用 compose() 方法，Observable 可以利用传入的 Transformer 对象的 call 方法直接对自身进行处理，也就不必被包在方法的里面了。


# 线程控制


## Scheduler 的 API (二)


observeOn() 指定的是它之后的操作所在的线程。因此如果有多次切换线程的需求，只要在每个想要切换线程的位置调用一次 observeOn() 即可。上代码：
```java
Observable.just(1, 2, 3, 4) // IO 线程，由 subscribeOn() 指定
    .subscribeOn(Schedulers.io())
    .observeOn(Schedulers.newThread())
    .map(mapOperator) // 新线程，由 observeOn() 指定
    .observeOn(Schedulers.io())
    .map(mapOperator2) // IO 线程，由 observeOn() 指定
    .observeOn(AndroidSchedulers.mainThread) 
    .subscribe(subscriber);  // Android 主线程，由 observeOn() 指定
```

如上，通过 observeOn() 的多次调用，程序实现了线程的多次切换。

不过，不同于 observeOn() ， subscribeOn() 的位置放在哪里都可以，但它是只能调用一次的。


## Scheduler 的原理（二）

其实， subscribeOn() 和 observeOn() 的内部实现，也是用的 lift()。具体看图（不同颜色的箭头表示不同的线程）：

subscribeOn() 原理图：


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/scheduler1.webp)


observeOn() 原理图：


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/scheduler2.webp)



从图中可以看出，subscribeOn() 和 observeOn() 都做了线程切换的工作（图中的 "schedule..." 部位）。不同的是， subscribeOn() 的线程切换发生在 OnSubscribe 中，即在它通知上一级 OnSubscribe 时，这时事件还没有开始发送，因此 subscribeOn() 的线程控制可以从事件发出的开端就造成影响；而 observeOn() 的线程切换则发生在它内建的 Subscriber 中，即发生在它即将给下一级 Subscriber 发送事件时，因此 observeOn() 控制的是它后面的线程。


最后，我用一张图来解释当多个 subscribeOn() 和 observeOn() 混合使用时，线程调度是怎么发生的（由于图中对象较多，相对于上面的图对结构做了一些简化调整）：

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/scheduler3.webp)


图中共有 5 处含有对事件的操作。由图中可以看出，①和②两处受第一个 subscribeOn() 影响，运行在红色线程；③和④处受第一个 observeOn() 的影响，运行在绿色线程；⑤处受第二个 onserveOn() 影响，运行在紫色线程；而第二个 subscribeOn() ，由于在通知过程中线程就被第一个 subscribeOn() 截断，因此对整个流程并没有任何影响。这里也就回答了前面的问题：当使用了多个 subscribeOn() 的时候，只有第一个 subscribeOn() 起作用。


这个图画的太好了, 我终于懂了........!!!

## 延伸：doOnSubscribe()

然而，虽然超过一个的 subscribeOn() 对事件处理的流程没有影响，但在流程之前却是可以利用的。

在前面讲 Subscriber 的时候，提到过 Subscriber 的 onStart() 可以用作流程开始前的初始化。然而 onStart() 由于在 subscribe() 发生时就被调用了，因此不能指定线程，而是只能执行在 subscribe() 被调用时的线程。这就导致如果 onStart() 中含有对线程有要求的代码（例如在界面上显示一个 ProgressBar，这必须在主线程执行），将会有线程非法的风险，因为有时你无法预测 subscribe() 将会在什么线程执行。

而与 Subscriber.onStart() 相对应的，有一个方法 Observable.doOnSubscribe() 。它和 Subscriber.onStart() 同样是在 subscribe() 调用后而且在事件发送前执行，但区别在于它可以指定线程。默认情况下， doOnSubscribe() 执行在 subscribe() 发生的线程；而如果在 doOnSubscribe() 之后有 subscribeOn() 的话，它将执行在离它最近的 subscribeOn()所指定的线程。

示例代码：

```java
Observable.create(onSubscribe)
    .subscribeOn(Schedulers.io())
    .doOnSubscribe(new Action0() {
        @Override
        public void call() {
            progressBar.setVisibility(View.VISIBLE); // 需要在主线程执行
        }
    })
    .subscribeOn(AndroidSchedulers.mainThread()) // 指定主线程
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(subscriber);
```

如上，在 doOnSubscribe()的后面跟一个 subscribeOn() ，就能指定准备工作的线程了。