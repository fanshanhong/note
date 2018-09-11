
## LruCache的介绍

---
LRU（Least recently used，最近最少使用）算法根据数据的历史访问记录来进行淘汰数据，其核心思想是“如果数据最近被访问过，那么将来被访问的几率也更高”，因此, 当缓存满了的时候, 会优先淘汰那些近期最少使用的缓存对象。
LruCache是Android 3.1所提供的一个缓存类。

## LruCache的使用

---

```java

    // 获取到可用内存的最大值，使用内存超出这个值会引起OutOfMemory异常。
    // // LruCache通过构造函数传入缓存值，以KB为单位(注意这里的单位要和sizeIf()方法的返回值的单位一致)
    int maxMemory = (int) (Runtime.getRuntime().totalMemory() / 1024);
    // 使用最大可用内存值的1/8作为缓存的大小。
    int cacheSize = maxMemory / 8;
    // 创建一个LruCache, 构造中传入设定的缓存大小
    // 重写sizeOf()方法, 计算出每个要缓存的对象的大小
    LruCache mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getRowBytes() * value.getHeight() / 1024;
        }
    };

```

注意：缓存的总容量和每个缓存对象的大小所用单位要一致。

调用LruCache的方法如下:

```java
    // 当要把对象加入到LruCache的时候, 直接put()
    public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
    	 if (getBitmapFromMemCache(key) == null) {
    		   mMemoryCache.put(key, bitmap);
    	 }
    }

    // 根据key, 从LruCache中获取缓存对象, 直接get()
    public Bitmap getBitmapFromMemCache(String key) {
    	 return mMemoryCache.get(key);
    }
```


## LruCache整体分析

---

LruCache是个泛型类，它内部维护了一个LinkedHashMap以强引用的方式存放了外部的缓存对象，然后对外提供了get和put方法来完成缓存的获取和添加操作。 当缓存满时，LruCache会把最近最少使用的缓存对象从LinkedHashMap中移除，然后再添加新的缓存对象。

另外，LruCache 是线程安全的，源码分析中可以看到。


## LruCache原理
LruCache的核心思想很好理解，就是要维护一个缓存对象链表，其中对象链表的排列方式是按照访问顺序实现的，即一直没访问的对象，将放在队头，即将被淘汰。而最近访问的对象将放在队尾，最后被淘汰。

![](https://thumbnail0.baidupcs.com/thumbnail/97255e9508fc1af5e5eb2e1d5dfcafd2?fid=2265468443-250528-165032914333856&time=1536663600&rt=sh&sign=FDTAER-DCb740ccc5511e5e8fedcff06b081203-eFJpPl2WiwU4QtpExN2rGTsGGTI%3D&expires=8h&chkv=0&chkbd=0&chkpc=&dp-logid=5886294429050973315&dp-callid=0&size=c710_u400&quality=100&vuk=-&ft=video)

从图中可以看到， 当有新的缓存对象到来的时候(put)的时候, 不断从队尾加入。那么，队头的元素就是最老的元素了。 当队列满了的时候（即F进入的时候），队头的A就要被淘汰了（因为队头的元素就是那个最近一直没有访问的）。一旦访问了队列中的某个元素（比如元素C）， 那么就把该缓存对象重新移动到队尾， 只要独享被访问， 那么这个缓存的对象就相当于最新的， 暂时不会被淘汰。

LruCache的原理就是这样， 下来我们看看它是怎么实现的。

## LruCache源码分析

LruCache就一个文件。为了兼容低版本, 在android.util包下和android.support.v4.util下各有一份, 我们这里分析android.support.v4.util包下的LruCache源文件.

LruCache是个泛型类. 为啥是泛型类, 因为需要维护缓存对象key-value的映射啊. 其内部维护了一个LinkedHashMap, (也就是上图中的那个队列, 它是用LinkedHashMap来实现的).

```java
public class LruCache<K, V> {

    // 内部维护一个LinkedHashMap
    private final LinkedHashMap<K, V> map;

```

为了控制缓存占用内存的大小， 这里还维护了两个size。 可以想象， 当size >= maxSize的时候， 就是队列满了。

```java

   // 当前已有的缓存对象已经占用了多少容量
   private int size;

   // 用于缓存的最大的容量
   private int maxSize;
```

另外为了计算这个缓存队列的命中率, 还维护了几个count

```java
   // LinkedHashMap中已经put了多少, 也就是当前队列有多少各元素
   private int putCount;
   private int createCount;
   private int evictionCount;

   // 命中了多少.  当你通过 lruCache.get(key)能够拿到一个缓存的对象, 那么就称为hit. 否则就是miss
   private int hitCount;
   // 丢失了多少
   private int missCount;
```


下面我们来看LruCache的构造方法

```java
   // 构造, 传入缓存的maxSize
   public LruCache(int maxSize) {
       if (maxSize <= 0) {
           throw new IllegalArgumentException("maxSize <= 0");
       }
       // 用于缓存的最大的容量
       this.maxSize = maxSize;

       // 其中accessOrder设置为true则为访问顺序,为false,则为插入顺序
       // 初始容量0,  负载因子0.75
       this.map = new LinkedHashMap<K, V>(0, 0.75f, true);

   }

```

很简单, 就传入一个缓存的最大容量, 给maxSize赋值, 然后创建了一个新的LinkedHashMap对象。
这里使用了LinkedHashMap三个参数的构造
* 第一个参数initialCapacity , 指定Map的初始容量为 0
* 第二个参数loadFactor, 指定map的负载因子为 0.75, 其实HashMap的负载因子默认就是 0.75
* 第三个参数accessOrder是指定这个LinkedHashMap的结构是按照什么顺序
  * true 访问顺序
  * false 插入顺序


如何理解这个访问顺序和插入顺序, 我们看下面的一个栗子

```java
public static final void main(String[] args) {
        // 这里设置为true, 访问顺序
        LinkedHashMap<Integer, Integer> map = new LinkedHashMap<>(0, 0.75f, true);
        map.put(0, 0);
        map.put(1, 1);
        map.put(2, 2);
        map.put(3, 3);
        map.put(4, 4);
        map.put(5, 5);
        map.get(1);
        map.get(2);

        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            System.out.println(entry.getKey() + ":" + entry.getValue());
        }
}
```

结果如下:
> 0:0
3:3
4:4
5:5
1:1
2:2

将 LinkedHashMap构造方法的第三个参数改成false, 结果如下:

> 0:0
1:1
2:2
3:3
4:4
5:5

* 当第三个参数传入true, LinkedHashMap会实现Lru, 按照访问顺序创建LinkedHashMap
* 当第三个参数传入false, LinkedHashMap会按照插入顺序构建

![](https://thumbnail0.baidupcs.com/thumbnail/5e83758deb52e4ea5d2e1c24f2f539f7?fid=2265468443-250528-778805023237367&time=1536663600&rt=sh&sign=FDTAER-DCb740ccc5511e5e8fedcff06b081203-1Y9P07PjnLUkkaxIZAmo1t%2F7oHA%3D&expires=8h&chkv=0&chkbd=0&chkpc=&dp-logid=5886307983701188147&dp-callid=0&size=c710_u400&quality=100&vuk=-&ft=video)


如图所示:
* 如果按照访问顺序: 一开始put, 每次都从队尾向队列添加, put(5,5)之后, 队列从头到尾是(0,0)(1,1)(2,2)(3,3)(4,4)(5,5)。 此时get(1), 访问了一下(1,1), 那(1,1)就是被最近访问过的, 就是最新的, 把它重新移动到队尾, 它暂时不会淘汰。 随后又访问了(2,2), 同样要把(2,2)移动到队尾。 此时, 就出现了图中最后所描述的场景。 遍历LinkedHashMap并且输出, 是按照从head到tail的顺序来遍历的, 结果如上.


* 如果是按照插入顺序,  那么在put完之后, 是否get就没所谓了。  反正都会按照put的顺序依次打印出来。 在图中, 就是put(5,5)之后的队列的情况, 从head到tail依次打印。   而且不管之后是否还会get(), 都不会影响队列中元素的顺序..  因为是按照put的顺序呀...

从构造中可以看出,  LruCache正是使用LinkedHashMap的访问顺序.

下面看put()方法

```java
    /**
    * Caches {@code value} for {@code key}. The value is moved to the head of
    * the queue.
    *
    * @return the previous value mapped by {@code key}.
    */
    // 英文注释翻译: 使用key来缓存value. 这个value被放到了队列的头部
   public final V put(K key, V value) {
       // 异常处理
       if (key == null || value == null) {
           throw new NullPointerException("key == null || value == null");
       }

       V previous; // 前一个value
       synchronized (this) {
           putCount++; // 计数

           // 获取当前要缓存的对象的大小
           // 增加已有缓存的大小
           size += safeSizeOf(key, value);
           // 向map中加入缓存对象
           previous = map.put(key, value);
           // 调用map的put, 如果map之前有这个key, 就覆盖了, 然后返回之前的value
           if (previous != null) {
               // 所以要减去之前的大小
               size -= safeSizeOf(key, previous);
           }
       }

       if (previous != null) {
           entryRemoved(false, key, previous, value);
       }

       // 走到这里,  map已经put了, 并且size也修改了.
       // 如果超过了maxSize, 就要把最久未使用的移出map
       trimToSize(maxSize);
       return previous;
   }
```

正如英文注释所说, put()方法主要是把value放在了队列的头部.  具体执行流程如下:
1. 先进行异常检查, key 和 value不能为null
2. put的数量自增, 这个主要是用在后面统计命中率的
3. 使用safeSizeOf获取将要缓存的这个对象的大小. safeSizeOf内部调用了sizeOf()方法, 没错, 就是我们重写的那个sizeOf()方法. sizeOf的默认实现如下:  这里拿到将要缓存的这个对象的大小之后, 修改当前的size值. size是什么? 前面说了,size是当前已有的缓存对象已经占用了多少容量.  如果这个size的值超过了maxSize, 那么就要开始进行淘汰了.

```java
/**
 * Returns the size of the entry for {@code key} and {@code value} in
 * user-defined units.  The default implementation returns 1 so that size
 * is the number of entries and max size is the maximum number of entries.
 * <p>
 * <p>An entry's size must not change while it is in the cache.
 */

// 默认实现返回1,  所以maxSize 能放 maxSize 个entry
// 我们要重写这个方法, 返回要缓存的对象的大小.
// 注意:一定要使用和maxSize一样的单位.
// 注意:这个缓存的对象在被缓存期间, 它的大小不可以改变
protected int sizeOf(K key, V value) {
    return 1;
}
```

4.调用map.put()向map中加入缓存对象。注意这个put方法, 他是把key-value映射好然后放在map中, 如果之前在map中已经包含了将要put的这个key, 就替换value的值, 然后把之前的value返回来.
5.
  * 如果 previous != null , 代表map中已经有了将要put的key, put之后就把previous覆盖了.  那么size就要减掉已经被覆盖的那部分大小.
  * 如果previous == null, 代表之前并没有, 是新put的, 在此之前size已经增加了, 所以这里什么也不用做
  *
6. entryRemoved是个空的实现, 在必要时候可以重写
7. trimToSize(maxSize); 走到这里,  map已经put了, 并且size也修改了. 这个方法主要就是判断当前占用的缓存的容量是否已经超过了最大容量, 如果超过, 则移除最老的缓存对象, 直到剩下的总容量(size)达到或低于要求的大小(maxSize)

```java
/**
    * Remove the eldest entries until the total of remaining entries is at or
    * below the requested size.
    *
    * @param maxSize the maximum size of the cache before returning. May be -1
    *                to evict even 0-sized elements.
    */
   // 移除最老的entry, 直到剩下的entry总容量达到或低于要求的大小
   public void trimToSize(int maxSize) {
       // 死循环
       while (true) {
           K key;
           V value;
           synchronized (this) {
               // 异常
               if (size < 0 || (map.isEmpty() && size != 0)) {
                   throw new IllegalStateException(getClass().getName()
                           + ".sizeOf() is reporting inconsistent results!");
               }

               // 如果缓存大小size小于最大缓存,或者map为空,不需要再删除缓存对象,跳出循环,
               // 退出循环的条件
               if (size <= maxSize || map.isEmpty()) {
                   break;
               }

               //  //迭代器获取第一个对象,即队头的元素,近期最少访问的元素
               Map.Entry<K, V> toEvict = map.entrySet().iterator().next();
               key = toEvict.getKey();
               value = toEvict.getValue();
               // 移除队头元素
               map.remove(key);
               // 更新大小
               size -= safeSizeOf(key, value);
               // 驱逐(收回)的数量++
               evictionCount++;
           }

           entryRemoved(true, key, value, null);
       }
   }
```

首先这是个死循环  `while(true)` , 那么跳出循环的条件就是` if (size <= maxSize || map.isEmpty())`. 只有当前的缓存大小size <= 允许的缓存的最大容量的时候 或者 map是空,  跳出.
如果进入的时候, size < maxSize, 那么直接跳出...
如果进入的时候, size < maxSize, 那么
  * 先迭代器获取map中的第一个对象,即队头的元素, 它就是近期最少访问的元素
  * 然后从map中移除这个队头元素, 并且更新当前已经使用的容量size
  * 收回的计数器++
  * 执行一遍之后, 如果size 还是 >= maxSize, 那么就再一次移除队头的元素, 一直循环, 直到满足条件为止..因此, trimToSize这个方法执行完之后,  size肯定是<maxSize的.
  *
8.最后把previous返回. 之所以返回这个previous, 是跟map的put()方法保持一致的.

总结一下:当put一个东东的时候,
* step1.先获取一下要缓存的这个东东多大..并且更新size.
* step2. 然后把这个东东先丢到LinkedHashMap中.  丢的过程中, 如果覆盖了之前的,  就要把之前的那个占用的空间释放一下, 就是从size里减掉. 如果丢的过程中, 并没有覆盖之前的, 那正好, 之前已经给size增加过了, 现在啥也不用做了.
* step3.这些都完事之后, 判断一下size有没有超过maxSize, 如果超过了, 循环, 每次都移除队列的头部的元素, 直到size < maxSize为止.
* step4.最后, 把previous返回去就行了.


put()完了, 我们看看get()方法做了啥.

```java
/**
    * Returns the value for {@code key} if it exists in the cache or can be
    * created by {@code #create}. If a value was returned, it is moved to the
    * head of the queue. This returns null if a value is not cached and cannot
    * be created.
    */
   public final V get(K key) {
       // 异常
       if (key == null) {
           throw new NullPointerException("key == null");
       }

       V mapValue;
       synchronized (this) {
           // map中有 get()方法会实现将访问的元素更新到队列尾部的功能
           // get()方法中执行了
           //  if (accessOrder) accessOrder 为true代表按照访问顺序
           //            afterNodeAccess(e);
           // afterNodeAccess是 move node to last(将刚刚访问过的元素移动到队列尾部)
           mapValue = map.get(key);
           if (mapValue != null) {
               // 命中
               hitCount++;
               return mapValue;
           }
           // 丢失
           missCount++;
       }

       // 丢失会走到这里

       /*
        * Attempt to create a value. This may take a long time, and the map
        * may be different when create() returns. If a conflicting value was
        * added to the map while create() was working, we leave that value in
        * the map and release the created value.
        */

       // create的默认实现就是返回null, 所以如果没有命中的话, 就直接return null了
       V createdValue = create(key);
       if (createdValue == null) {
           return null;
       }

       synchronized (this) {
           createCount++;
           mapValue = map.put(key, createdValue);

           if (mapValue != null) {
               // There was a conflict so undo that last put
               map.put(key, mapValue);
           } else {
               size += safeSizeOf(key, createdValue);
           }
       }

       if (mapValue != null) {
           entryRemoved(false, key, createdValue, mapValue);
           return mapValue;
       } else {
           trimToSize(maxSize);
           return createdValue;
       }
   }
```

get()的方法比较长, 但是感觉后面没啥用..
1. 首先还是异常判断
2. 然后直接调用map的get()方法, 如果map中有(即mapValue!= null), 就拿到了缓存的对象, 命中数++, 把缓存的对象返回即可.  否则, 就miss, 丢失数量++
3. 如果丢失了, 会走到create方法,  create方法默认返回null, 所以直接返回null就结束了.

当调用LruCache的get()方法获取集合中的缓存对象时，就代表访问了一次该元素，将会更新队列，保持整个队列是按照访问顺序排序。这个更新过程就是在LinkedHashMap中的get()方法中完成的。下面是LinkedHashMap中get()方法的源码

```java
public V get(Object key) {
    Node<K,V> e;
    if ((e = getNode(hash(key), key)) == null)
        return null;
    if (accessOrder)
        afterNodeAccess(e);
    return e.value;
}
```


当 getNode之后,  e == null , 就是map中没有, 返回null结束了.
当 getNode之后,  e != null,  也就是访问了一次这个元素e, 然后判断accessOrder(这个accessOrder就是我们在创建LinedHashMap的时候指定的第三个参数) 还记得我们指定了true, 因此执行afterNodeAccess(e). 那么这个afterNodeAccess(e)具体做了什么呢. 我们看一下他的源码. 源码太长没仔细看,  但是注意到那唯一的一句注释: move node to last.....就是把这个访问过的元素e移动到了队尾...

```java
void afterNodeAccess(Node<K,V> e) { // move node to last
        LinkedHashMapEntry<K,V> last;
        if (accessOrder && (last = tail) != e) {
            LinkedHashMapEntry<K,V> p =
                (LinkedHashMapEntry<K,V>)e, b = p.before, a = p.after;
            p.after = null;
            if (b == null)
                head = a;
            else
                b.after = a;
            if (a != null)
                a.before = b;
            else
                last = b;
            if (last == null)
                head = p;
            else {
                p.before = last;
                last.after = p;
            }
            tail = p;
            ++modCount;
        }
    }
```

真相大白了...

最后看一下toString()方法,  就明白那几个Count到底是干嘛的了..

```java

    // 重写toString方法
    @Override
    public synchronized final String toString() {
        // 访问的总次数 = 命中次数+丢失次数
        int accesses = hitCount + missCount;
        // 命中率
        int hitPercent = accesses != 0 ? (100 * hitCount / accesses) : 0;
        // 格式化  最大容量/命中数/丢失数/命中率
        return String.format(Locale.US, "LruCache[maxSize=%d,hits=%d,misses=%d,hitRate=%d%%]",
                maxSize, hitCount, missCount, hitPercent);
    }
```



前面说LruCache 是线程安全的  从LruCache的put和get方法中可以看到, 在有可能出现不一致的地方都进行了加锁.

ending
