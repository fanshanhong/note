---

title: Glide缓存

date: 2019-05-30

categories: 
   - Android开源框架

tags: 
   - Android开源框架 


description: 
​
---

<!-- TOC -->

- [Glide缓存简介](#glide缓存简介)
- [缓存创建](#缓存创建)

<!-- /TOC -->


# Glide缓存简介


Glide将缓存分成了两个模块，一个是内存缓存，一个是硬盘缓存。



# 缓存创建


Glide 类中的 MemoryCache 是用于内存缓存的. 看下这个成员是在哪里创建的

```java
// Glide.java
 public class Glide {
    private final MemoryCache memoryCache; // 用于内存缓存
 }
````

```java
 Glide.with()
 ```

 ```java
 // Glide.java
     public static RequestManager with(Activity activity) {
        RequestManagerRetriever retriever = RequestManagerRetriever.get();
        return retriever.get(activity);
    }
 ```
调用 RequestManagerRetriever 的 get 方法
```java
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public RequestManager get(Activity activity) {
         ...
            return fragmentGet(activity, fm);

    }
```

调用  fragmentGet()

```java
    RequestManager fragmentGet(Context context, android.app.FragmentManager fm) {
        RequestManagerFragment current = getRequestManagerFragment(fm);
        RequestManager requestManager = current.getRequestManager();
        if (requestManager == null) {
            requestManager = new RequestManager(context, current.getLifecycle(), current.getRequestManagerTreeNode());
            current.setRequestManager(requestManager);
        }
        return requestManager;
    }
```

使用 new RequestManager  创建 RequestManager

在 Requestmanager的构造方法中, 
```java
    RequestManager(Context context, final Lifecycle lifecycle, RequestManagerTreeNode treeNode,
            RequestTracker requestTracker, ConnectivityMonitorFactory factory) {
        ...
        this.glide = Glide.get(context);
        this.optionsApplier = new OptionsApplier();
   }
```

调用 Glide.get() , 单例模式, 创建 glide 对象

```java
    public static Glide get(Context context) {
        if (glide == null) {
            synchronized (Glide.class) {
                if (glide == null) {
                    Context applicationContext = context.getApplicationContext();
                    List<GlideModule> modules = new ManifestParser(applicationContext).parse();

                    GlideBuilder builder = new GlideBuilder(applicationContext);
                    for (GlideModule module : modules) {
                        module.applyOptions(applicationContext, builder);
                    }
                    glide = builder.createGlide();
                    for (GlideModule module : modules) {
                        module.registerComponents(applicationContext, glide);
                    }
                }
            }
        }

        return glide;
    }
```

里面调用了  glide = builder.createGlide();

```java
Glide createGlide() {
        ...
      // LruCache
        if (memoryCache == null) {
            memoryCache = new LruResourceCache(calculator.getMemoryCacheSize());
        }

      // 硬盘缓存
        if (diskCacheFactory == null) {
            diskCacheFactory = new InternalCacheDiskCacheFactory(context);
        }

        if (engine == null) {
            engine = new Engine(memoryCache, diskCacheFactory, diskCacheService, sourceService);
        }

        if (decodeFormat == null) {
            decodeFormat = DecodeFormat.DEFAULT;
        }

        return new Glide(engine, memoryCache, bitmapPool, context, decodeFormat);
    }
```


# 内存缓存使用(读取)


Engine.load
```java
public class Engine implements EngineJobListener,
        MemoryCache.ResourceRemovedListener,
        EngineResource.ResourceListener {
    ...    

    public <T, Z, R> LoadStatus load(Key signature, int width, int height, DataFetcher<T> fetcher,
            DataLoadProvider<T, Z> loadProvider, Transformation<Z> transformation, ResourceTranscoder<Z, R> transcoder,
            Priority priority, boolean isMemoryCacheable, DiskCacheStrategy diskCacheStrategy, ResourceCallback cb) {
        Util.assertMainThread();
        long startTime = LogTime.getLogTime();

        final String id = fetcher.getId();
        EngineKey key = keyFactory.buildKey(id, signature, width, height, loadProvider.getCacheDecoder(),
                loadProvider.getSourceDecoder(), transformation, loadProvider.getEncoder(),
                transcoder, loadProvider.getSourceEncoder());

        EngineResource<?> cached = loadFromCache(key, isMemoryCacheable);
        if (cached != null) {
            cb.onResourceReady(cached);
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                logWithTimeAndKey("Loaded resource from cache", startTime, key);
            }
            return null;
        }

        EngineResource<?> active = loadFromActiveResources(key, isMemoryCacheable);
        if (active != null) {
            cb.onResourceReady(active);
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                logWithTimeAndKey("Loaded resource from active resources", startTime, key);
            }
            return null;
        }

        EngineJob current = jobs.get(key);
        if (current != null) {
            current.addCallback(cb);
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                logWithTimeAndKey("Added to existing load", startTime, key);
            }
            return new LoadStatus(cb, current);
        }

        EngineJob engineJob = engineJobFactory.build(key, isMemoryCacheable);
        DecodeJob<T, Z, R> decodeJob = new DecodeJob<T, Z, R>(key, width, height, fetcher, loadProvider, transformation,
                transcoder, diskCacheProvider, diskCacheStrategy, priority);
        EngineRunnable runnable = new EngineRunnable(engineJob, decodeJob, priority);
        jobs.put(key, engineJob);
        engineJob.addCallback(cb);
        engineJob.start(runnable);

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            logWithTimeAndKey("Started new load", startTime, key);
        }
        return new LoadStatus(cb, engineJob);
    }

    ...
}
```

将id连同着signature、width、height等等10个参数一起传入到EngineKeyFactory的buildKey()方法当中，从而构建出了一个EngineKey对象，这个EngineKey也就是Glide中的缓存Key了.

然后 loadFromCache，如果获取到就直接调用 cb.onResourceReady()方法进行回调。 就结束了. 这个 cb 就是 RequestManager.

如果没有获取到，则会继续调用 loadFromActiveResources() 方法来获取缓存图片，获取到的话也直接进行回调。

只有在两个方法都没有获取到缓存的情况下，才会继续向下执行，从而开启线程来加载图片。



## loadFromCache

```java
// Engine.java
public class Engine implements EngineJobListener,
        MemoryCache.ResourceRemovedListener,
        EngineResource.ResourceListener {

    private final MemoryCache cache;
    private final Map<Key, WeakReference<EngineResource<?>>> activeResources;
    ...
   // 在loadFromCache()方法的一开始，首先就判断了isMemoryCacheable是不是false，如果是false的话就直接返回null。这是什么意思呢？其实很简单，有个skipMemoryCache()方法吗？如果在这个方法中传入true，那么这里的isMemoryCacheable就会是false，表示内存缓存已被禁用。
    private EngineResource<?> loadFromCache(Key key, boolean isMemoryCacheable) {
        if (!isMemoryCacheable) {// 跳过内存缓存
            return null;
        }

        // 会使用缓存Key来从cache当中取值，而这里的cache对象就是在构建Glide对象时创建的LruResourceCache，那么说明这里其实使用的就是LruCache算法了。
        EngineResource<?> cached = getEngineResourceFromCache(key);

        // 如果成功拿到缓存
        if (cached != null) {
            cached.acquire(); //引用+1

            // activeResources 表示正在使用的图片
            // 往 正在使用的 HashMap 中添加一个弱引用, 表示该图片正在使用
            activeResources.put(key, new ResourceWeakReference(key, cached, getReferenceQueue()));
        }
        return cached;
    }

    private EngineResource<?> getEngineResourceFromCache(Key key) {
       // 使用 remove 方法来获取.  如果 remove 成功, 返回值 不是 null, 就说明有缓存, 并且拿到了缓存. 这里移除了, 后面要记得加到正在使用的 HashMap 中去..
       // 否则, 就说明没有缓存. 就是未命中
        Resource<?> cached = cache.remove(key);
        final EngineResource result;
        if (cached == null) { // 未命中
            result = null;
        } else if (cached instanceof EngineResource) { // 命中, 拿到缓存
            result = (EngineResource) cached;
        } else {
            result = new EngineResource(cached, true /*isCacheable*/);
        }
        return result;
    }
    ...
}
```



## loadFromActiveResources()

如果 loadFromCache没有获取到, 就走这个


```java
private EngineResource<?> loadFromActiveResources(Key key, boolean isMemoryCacheable) {
        if (!isMemoryCacheable) {// 跳过内存缓存
            return null;
        }
        EngineResource<?> active = null;
        // 从activeResources这个HashMap当中取值的。使用activeResources来缓存正在使用中的图片，可以保护这些图片不会被LruCache算法回收掉。
        WeakReference<EngineResource<?>> activeRef = activeResources.get(key);
        if (activeRef != null) {
            active = activeRef.get();
            if (active != null) {// 拿到了缓存, 记得引用+1
                active.acquire();
            } else {
                activeResources.remove(key);
            }
        }
        return active;
    }
```


概括一下来说，就是如果能从内存缓存当中读取到要加载的图片，那么就直接进行回调，如果读取不到的话，才会开启线程执行后面的图片加载逻辑。


# 内存缓存写入

现在我们已经搞明白了内存缓存读取的原理，接下来的问题就是内存缓存是在哪里写入的呢？

当图片加载完成之后，会在EngineJob当中通过Handler发送一条消息将执行逻辑切回到主线程当中，从而执行handleResultOnMainThread()方法。那么我们现在重新来看一下这个方法，代码如下所示：

```java
class EngineJob implements EngineRunnable.EngineRunnableManager {

    private final EngineResourceFactory engineResourceFactory;
    ...

    private void handleResultOnMainThread() {
      ...
         // 
        engineResource = engineResourceFactory.build(resource, isCacheable);
        hasResource = true;
        engineResource.acquire();
        listener.onEngineJobComplete(key, engineResource);
        for (ResourceCallback cb : cbs) {
            if (!isInIgnoredCallbacks(cb)) {
                engineResource.acquire();
                cb.onResourceReady(engineResource);
            }
        }
        engineResource.release();
    }

    static class EngineResourceFactory {
        public <R> EngineResource<R> build(Resource<R> resource, boolean isMemoryCacheable) {
            return new EngineResource<R>(resource, isMemoryCacheable);
        }
    }
    ...
}
```

里通过EngineResourceFactory构建出了一个包含图片资源的EngineResource对象，然后会将这个对象回调到Engine的onEngineJobComplete()方法当中，如下所示：

```java
@Override
    public void onEngineJobComplete(Key key, EngineResource<?> resource) {
        Util.assertMainThread();
        // A null resource indicates that the load failed, usually due to an exception.
        if (resource != null) {
            resource.setResourceListener(key, this);
            if (resource.isCacheable()) {
                activeResources.put(key, new ResourceWeakReference(key, resource, getReferenceQueue()));
            }
        }
        jobs.remove(key);
    }
```

回调过来的EngineResource被put到了activeResources当中，也就是在这里写入的缓存。

那么这只是弱引用缓存，还有另外一种LruCache缓存是在哪里写入的呢？这就要介绍一下EngineResource中的一个引用机制了。观察刚才的handleResultOnMainThread()方法，有调用EngineResource的acquire()方法，有调用它的release()方法。其实，EngineResource是用一个acquired变量用来记录图片被引用的次数，调用acquire()方法会让变量加1，调用release()方法会让变量减1.


也就是说，当acquired变量大于0的时候，说明图片正在使用中，也就应该放到activeResources弱引用缓存当中。而经过release()之后，如果acquired变量等于0了，说明图片已经不再被使用了，那么此时会调用listener的onResourceReleased()方法来释放资源，这个listener就是Engine对象，我们来看下它的onResourceReleased()方法：

```java
public class Engine implements EngineJobListener,
        MemoryCache.ResourceRemovedListener,
        EngineResource.ResourceListener {

    private final MemoryCache cache;
    private final Map<Key, WeakReference<EngineResource<?>>> activeResources;
    ...    

    @Override
    public void onResourceReleased(Key cacheKey, EngineResource resource) {
        Util.assertMainThread();
        activeResources.remove(cacheKey);
        if (resource.isCacheable()) {
            cache.put(cacheKey, resource);
        } else {
            resourceRecycler.recycle(resource);
        }
    }

    ...
}
```

可以看到，这里首先会将缓存图片从activeResources中移除，然后再将它put到LruResourceCache当中。这样也就实现了正在使用中的图片使用弱引用来进行缓存，不在使用中的图片使用LruCache来进行缓存的功能。



# 硬盘缓存


有一个概念大家需要了解，就是当我们使用Glide去加载一张图片的时候，Glide默认并不会将原始图片展示出来，而是会对图片进行压缩和转换.


在去网络请求之前, 就通过 onSizeReady ,   获取到 override()中指定的大小, 或者是通过计算, 获取到 ImageView 的大小.\

Glide默认情况下在硬盘缓存的就是转换过后的图片，我们通过调用diskCacheStrategy()方法则可以改变这一默认行为。

首先，和内存缓存类似，硬盘缓存的实现也是使用的LruCache算法，而且Google还提供了一个现成的工具类DiskLruCache

在 Engine 的 load 方法中, 

Glide开启线程来加载图片, 会执行EngineRunnable的run()方法，run()方法中又会调用一个decode()方法，那么我们重新再来看一下这个decode()方法的源码：

```java
private Resource<?> decode() throws Exception {
    if (isDecodingFromCache()) {
        return decodeFromCache();
    } else {
        return decodeFromSource();
    }
}
```
默认情况下Glide会优先从缓存当中读取，只有缓存中不存在要读取的图片时，才会去读取原始图片。那么我们现在来看一下decodeFromCache()方法的源码，如下所示：

```java
private Resource<?> decodeFromCache() throws Exception {
    Resource<?> result = null;
    try {
        result = decodeJob.decodeResultFromCache();
    } catch (Exception e) {
        ...
    }
    if (result == null) {
        result = decodeJob.decodeSourceFromCache();
    }
    return result;
}
```
可以看到，这里会先去调用DecodeJob的decodeResultFromCache()方法来获取缓存，如果获取不到，会再调用decodeSourceFromCache()方法获取缓存，这两个方法的区别其实就是DiskCacheStrategy.RESULT和DiskCacheStrategy.SOURCE这两个参数的区别。

就是优先获取压缩和变换后的缓存. 
如果没有的话, 才去获取原始图片的缓存.


那么我们来看一下这两个方法的源码吧，如下所示：

```java
public Resource<Z> decodeResultFromCache() throws Exception {
    if (!diskCacheStrategy.cacheResult()) {
        return null;
    }
    long startTime = LogTime.getLogTime();
    Resource<T> transformed = loadFromCache(resultKey);
    startTime = LogTime.getLogTime();
    Resource<Z> result = transcode(transformed);
    return result;
}

public Resource<Z> decodeSourceFromCache() throws Exception {
    if (!diskCacheStrategy.cacheSource()) {
        return null;
    }
    long startTime = LogTime.getLogTime();
    Resource<T> decoded = loadFromCache(resultKey.getOriginalKey());
    return transformEncodeAndTranscode(decoded);
}
```



可以看到，它们都是调用了loadFromCache()方法从缓存当中读取数据，如果是decodeResultFromCache()方法就直接将数据解码并返回,如果是decodeSourceFromCache()方法，还要调用一下transformEncodeAndTranscode()方法先将数据转换一下再解码并返回。

然而我们注意到，这两个方法中在调用loadFromCache()方法时传入的参数却不一样，一个传入的是resultKey，另外一个却又调用了resultKey的getOriginalKey()方法。这个其实非常好理解，刚才我们已经解释过了，Glide的缓存Key是由10个参数共同组成的，包括图片的width、height等等。但如果我们是缓存的原始图片，其实并不需要这么多的参数，因为不用对图片做任何的变化。那么我们来看一下getOriginalKey()方法的源码：

```java
public Key getOriginalKey() {
    if (originalKey == null) {
        originalKey = new OriginalKey(id, signature);
    }
    return originalKey;
}
```
可以看到，这里其实就是忽略了绝大部分的参数，只使用了id和signature这两个参数来构成缓存Key。而signature参数绝大多数情况下都是用不到的，因此基本上可以说就是由id（也就是图片url）来决定的Original缓存Key。


搞明白了这两种缓存Key的区别，那么接下来我们看一下loadFromCache()方法的源码吧：
```java
private Resource<T> loadFromCache(Key key) throws IOException {
    File cacheFile = diskCacheProvider.getDiskCache().get(key);
    if (cacheFile == null) {
        return null;
    }
    Resource<T> result = null;
    try {
        result = loadProvider.getCacheDecoder().decode(cacheFile, width, height);
    } finally {
        if (result == null) {
            diskCacheProvider.getDiskCache().delete(key);
        }
    }
    return result;
}
```

调用getDiskCache()方法获取到的就是Glide自己编写的DiskLruCache工具类的实例，然后调用它的get()方法并把缓存Key传入，就能得到硬盘缓存的文件了。如果文件为空就返回null，如果文件不为空则将它解码成Resource对象后返回即可。 注意, 这里都是在子线程跑的.

这样我们就将硬盘缓存读取的源码分析完了，那么硬盘缓存又是在哪里写入的呢？趁热打铁我们赶快继续分析下去。






接上面, 如果没有缓存的话, 走 decodeFromSource


```java
public Resource<Z> decodeFromSource() throws Exception {
    Resource<T> decoded = decodeSource();
    return transformEncodeAndTranscode(decoded);
}
```

decodeSource()顾名思义是用来解析原图片的(其实内部还使用 UrlConnection 下载图片了)，而transformEncodeAndTranscode()则是用来对图片进行转换和转码的。我们先来看decodeSource()方法：

```java
private Resource<T> decodeSource() throws Exception {
    Resource<T> decoded = null;
    try {
        long startTime = LogTime.getLogTime();
        // 这里内部用 UrlConnection 下载图片了.  
        // 这里 A data 拿到的是 InputStream
        // 然后下面 decodeFromSourceData
        final A data = fetcher.loadData(priority);
        if (isCancelled) {
            return null;
        }
        decoded = decodeFromSourceData(data);
    } finally {
        fetcher.cleanup();
    }
    return decoded;
}
```

拿到 InputStream 后, 调用decodeFromSourceData()方法来对图片进行解码
```java
private Resource<T> decodeFromSourceData(A data) throws IOException {
    final Resource<T> decoded;
    if (diskCacheStrategy.cacheSource()) { // 是否允许缓存原始图片
        decoded = cacheAndDecodeSourceData(data); // cacheAndDecodeSourceData 方法中调用了getDiskCache()方法来获取DiskLruCache实例，接着调用它的put()方法就可以写入硬盘缓存了，注意原始图片的缓存Key是用的resultKey.getOriginalKey()。
    } else { // 不允许缓存就解码返回
        long startTime = LogTime.getLogTime();
        decoded = loadProvider.getSourceDecoder().decode(data, width, height);
    }
    return decoded;
}
```


好的，原始图片的缓存写入就是这么简单，接下来我们分析一下transformEncodeAndTranscode()方法的源码，来看看转换过后的图片缓存是怎么写入的。代码如下所示：
```java
private Resource<Z> transformEncodeAndTranscode(Resource<T> decoded) {
    long startTime = LogTime.getLogTime();
    // 调用transform()方法来对图片进行转换
    Resource<T> transformed = transform(decoded);
    // 转换完, 将转换过后的图片写入到硬盘缓存中
    writeTransformedToCache(transformed);
    startTime = LogTime.getLogTime();
    Resource<Z> result = transcode(transformed);
    return result;
}

private void writeTransformedToCache(Resource<T> transformed) {
    if (transformed == null || !diskCacheStrategy.cacheResult()) {
        return;
    }
    long startTime = LogTime.getLogTime();
    SourceWriter<Resource<T>> writer = new SourceWriter<Resource<T>>(loadProvider.getEncoder(), transformed);
    // 调用的同样是DiskLruCache实例的put()方法，不过这里用的缓存Key是resultKey
    diskCacheProvider.getDiskCache().put(resultKey, writer);
}
```

# 注意

内存缓存的获取, 是在主线程,  内存缓存的写入, 也是在主线程
硬盘缓存的换区, 是在子线程, 因为要 decode.

