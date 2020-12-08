
--- 

title: 把 ThreadLocal 原理说明白 

date: 2019-05-08 

categories: 
    - Android - 并发和多线程 
    - Handler 系列 
    
tags: 
    - Android 
    - 并发和多线程 

description: ​

---




# 把 ThreadLocal 原理说明白

<!-- TOC -->

- [把 ThreadLocal 原理说明白](#把-threadlocal-原理说明白)
    - [ThreadLocal 是什么?](#threadlocal-是什么)
    - [ThreadLocal 源码分析](#threadlocal-源码分析)

<!-- /TOC -->


## ThreadLocal 是什么?

参考:

https://www.cnblogs.com/xzwblog/p/7227509.html



引用其中的说明:

> ThreadLocal类是修饰变量的，重点是在控制变量的作用域，初衷可不是为了解决线程并发和线程冲突的，而是为了让变量的种类变的更多更丰富，方便人们使用罢了。很多开发语言在语言级别都提供这种作用域的变量类型。
>
> 根据变量的作用域，可以将变量分为全局变量，局部变量。简单的说，类里面定义的变量是全局变量，函数里面定义的变量是局部变量。
> 还有一种作用域是线程作用域，线程一般是跨越几个函数的。为了在几个函数之间共用一个变量，所以才出现：线程变量，这种变量在Java中就是ThreadLocal变量。(注意这句话，要考， 在 Looper 中有具体的体现)
>
> 全局变量，范围很大；局部变量，范围很小。无论是大还是小，其实都是定死的。而线程变量，调用几个函数，则决定了它的作用域有多大。
>
> ThreadLocal是跨函数的，虽然全局变量也是跨函数的，但是跨所有的函数，而且不是动态的。
>
> ThreadLocal是跨函数的，但是跨哪些函数呢，由线程来定，更灵活。

总之，ThreadLocal类是修饰变量的，是在控制它的作用域。

举例：现在有两个方法， fun1 和 fun2，每个方法内部都有一个成员变量 a，那么变量 a 就只能在各自的方法内部访问，方法结束后变量 a 自动销毁；

```java
fun1(){
		Object a = new Object();
}

fun2(){
  	Object a = new Object();
}
```



现在，我们有两个线程 thread1 和 thread2， 其内部都想维护各自的变量 t，那么该怎么办呢？这里就用 ThreadLocal 来实现，每个线程内都维护一个 ThreadLocal 变量即可；ThreadLocal 表示该变量的作为范围是在该线程内，该变量不能被其他线程所访问；并且在线程结束后，ThreadLocal 变量自动销毁。



下面我们来看看 ThreadLocal 是怎么实现的。



## ThreadLocal 源码分析

![ThreadLocal 方法列表](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/ThreadLocal_method_list.png)



我们先来 一起看一下构造方法，很简单。

```java
    public ThreadLocal() {
    }
```



然后我们来看一下他的 set 方法。因为要先 set 之后才能 get ..  ^_^

```java
    public void set(T value) {
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null)
            map.set(this, value);
        else
            createMap(t, value);
    }
```



首先使用`Thread.currentThread();`获取当前线程 t， 然后把 t 作为参数传入 `getMap(t)`方法；

我们来看看 `getMap(t)`方法做了什么

```java
    ThreadLocalMap getMap(Thread t) {
        return t.threadLocals;
    }
```



很简单， `getMap()`方法返回了`Thread `类的 `threadLocals` 。



我们找到 `Thread`类，找到`threadLocals`成员变量， 他是`ThreadLocal.ThreadLocalMap`类型

```java
public
class Thread implements Runnable {
		/* ThreadLocal values pertaining to this thread. This map is maintained
     * by the ThreadLocal class. */
    ThreadLocal.ThreadLocalMap threadLocals = null;
}
```



这个ThreadLocalMap又是干嘛的。。好多啊。。。 我们看一下他的类注释就明白啦。他说： ThreadLocalMap 是一个自定义的HashMap, 仅仅用于维护ThreadLocal 的值。哦了， 明白了。 

同时， 在ThreadLocalMap中还定义了存储数据用的Entry， key是ThreadLocal对象（key 为啥是 ThreadLocal？为啥不是网上说的用的线程对象或者线程 id？后面再说）， value是用户的值， 是个Object对象。

```java
    /**
     * ThreadLocalMap is a customized hash map suitable only for
     * maintaining thread local values. No operations are exported
     * outside of the ThreadLocal class. The class is package private to
     * allow declaration of fields in class Thread.  To help deal with
     * very large and long-lived usages, the hash table entries use
     * WeakReferences for keys. However, since reference queues are not
     * used, stale entries are guaranteed to be removed only when
     * the table starts running out of space.
     */
    static class ThreadLocalMap {

        /**
         * The entries in this hash map extend WeakReference, using
         * its main ref field as the key (which is always a
         * ThreadLocal object).  Note that null keys (i.e. entry.get()
         * == null) mean that the key is no longer referenced, so the
         * entry can be expunged from table.  Such entries are referred to
         * as "stale entries" in the code that follows.
         */
        static class Entry extends WeakReference<ThreadLocal<?>> {
            /** The value associated with this ThreadLocal. */
            Object value;

            Entry(ThreadLocal<?> k, Object v) {
                super(k);
                value = v;
            }
        }
    }
```





到这里，  set()方法或许就能说得通了。再贴一遍代码吧， 太远了， 看不到了。 

首先获取到当前的线程t， 然后拿到存储在线程中的ThreadLocalMap对象（就是个自定义的HashMap）。 然后， 如果map 不空， 把 当前的ThreadLocal对象作为key， 把用户设置的值作为value， 存储到map中。 如果map 为空， 就创建map。看看`createMap(t, value)`是咋创建的。

```java
  public void set(T value) {
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null)
            map.set(this, value);
        else
            createMap(t, value);
    }
```

```java
    /**
     * Create the map associated with a ThreadLocal. Overridden in
     * InheritableThreadLocal.
     *
     * @param t the current thread
     * @param firstValue value for the initial entry of the map
     */
    void createMap(Thread t, T firstValue) {
        t.threadLocals = new ThreadLocalMap(this, firstValue);
    }
```

createMap()中， 就是用 先创建一个map， 然后把 当前的ThreadLocal -> firstValue 作为第一个键值对，  丢在map中。最后把这个map赋值给 Thread中的 threadLocals变量。完活。。



一句话概括调用 `threadLocal.set(value)`的过程：拿到当前所在线程(Thread) 中的 HashMap，  然后把`<threadLocal, value> ` 存入 HashMap 中。



set说完了， 我们回答上面的那个问题：为啥 HashMap 中的 key 要用 threadLocal 对象， 而不用线程对象或者线程 id？

举个栗子：我们在一个线程里面， 想要维护 2 个作用范围为线程内 的变量。

现在我们用线程 id 来作为 key， 调用 `threadLocal.set(value)`的时候，拿到当前所在线程(Thread) 中的 HashMap后，  就把`<threadId, value> ` 存入 HashMap 中。如果再存一个 threadLocal 变量呢？继续 set， HashMap 再把`<threadId, value> ` 存入 HashMap 中。这不是把之前的覆盖了嘛。。。





`set()`方法清楚了， 我们来瞅瞅 `get()`方法

```java
    public T get() {
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null) {
            ThreadLocalMap.Entry e = map.getEntry(this);
            if (e != null) {
                @SuppressWarnings("unchecked")
                T result = (T)e.value;
                return result;
            }
        }
        return setInitialValue();
    }
```



首先拿到当前线程对象中的 HashMap， 然后以 this(ThreadLocal对象)作为 key ， 获取 Entry， 进而拿到 Entry 中的 value， 也就是我们之前 set 进去的 value。

如果 `map == null`, 就调用了`setInitialValue();`并返回。

在`setInitialValue()`中， 把 value 赋值为null， 然后返回， 捎带做了一件事， 就是创建了一个HashMap， 并把`<this, null>`作为初始值丢到 HashMap 中了。

```java
    private T setInitialValue() {
        T value = initialValue();
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null)
            map.set(this, value);
        else
            createMap(t, value);
        return value;
    }
```

