---

title: Glide

date: 2019-05-30

categories: 
   - Android开源框架

tags: 
   - Android开源框架 


description: 
​
---

<!-- TOC -->

- [用法](#用法)
- [生命周期回调](#生命周期回调)
- [流程](#流程)
- [load()](#load)
- [into](#into)
- [缓存](#缓存)

<!-- /TOC -->



# 用法


```
Model           ->          Data             ->         Resource    ->      TransformedResource -> TranscodedResource -> Target
本地文件/url    ModelLoader  InputStream       Decoder    Bitmap     Transform   裁剪后的        Transcode                    View
```




with( xx ) 影响  Glide 加载图片的生命周期

load() 很多重载

placeholder  占位符

error 错误占位符

override    显示之前,指定图片尺寸


裁剪技术
fitCenter()  宽高<=ImageView 宽高, 能完全显示

centerCrop()  填充整个 ImageView, 可能无法完全显示出来

skipMemoryCache  跳过内存缓存, 但还是会用磁盘缓存.  默认会使用内存缓存的.

diskCacheStrategy 硬盘缓存策略

NONE 跳过磁盘缓存
SOURCE 只缓存原来的全分辨率图片
RESULT 只缓存最终的图片
ALL 缓存全部

priority 优先级
into(target)



先后台线程进行网络请求下载图片, 然后切换到 UI 线程显示


# 生命周期回调


```java
    RequestManager fragmentGet(Context context, android.app.FragmentManager fm) {
        // 创建没有界面的 Fragment, Fragment 中持有一个 lifecycle 的引用
        RequestManagerFragment current = getRequestManagerFragment(fm);
        RequestManager requestManager = current.getRequestManager();
        if (requestManager == null) {
            requestManager = new RequestManager(context, current.getLifecycle(), current.getRequestManagerTreeNode());
            current.setRequestManager(requestManager);
        }
        return requestManager;
    }
```

```java
class ActivityFragmentLifecycle implements Lifecycle {
    private final Set<LifecycleListener> lifecycleListeners =
            Collections.newSetFromMap(new WeakHashMap<LifecycleListener, Boolean>());
    private boolean isStarted;
    private boolean isDestroyed;

    /**
     * Adds the given listener to the list of listeners to be notified on each lifecycle event.
     *
     * <p>
     *     The latest lifecycle event will be called on the given listener synchronously in this method. If the
     *     activity or fragment is stopped, {@link LifecycleListener#onStop()}} will be called, and same for onStart and
     *     onDestroy.
     * </p>
     *
     * <p>
     *     Note - {@link com.bumptech.glide.manager.LifecycleListener}s that are added more than once will have their
     *     lifecycle methods called more than once. It is the caller's responsibility to avoid adding listeners
     *     multiple times.
     * </p>
     */
    @Override
    public void addListener(LifecycleListener listener) {
        lifecycleListeners.add(listener);

        if (isDestroyed) {
            listener.onDestroy();
        } else if (isStarted) {
            listener.onStart();
        } else {
            listener.onStop();
        }
    }

    void onStart() {
        isStarted = true;
        for (LifecycleListener lifecycleListener : Util.getSnapshot(lifecycleListeners)) {
            lifecycleListener.onStart();
        }
    }

    void onStop() {
        isStarted = false;
        for (LifecycleListener lifecycleListener : Util.getSnapshot(lifecycleListeners)) {
            lifecycleListener.onStop();
        }
    }

    void onDestroy() {
        isDestroyed = true;
        for (LifecycleListener lifecycleListener : Util.getSnapshot(lifecycleListeners)) {
            lifecycleListener.onDestroy();
        }
    }
}
```

```java
public interface Lifecycle {
    /**
     * Adds the given listener to the set of listeners managed by this Lifecycle implementation.
     */
    void addListener(LifecycleListener listener);
}
```

```java
public interface LifecycleListener {

    /**
     * Callback for when {@link android.app.Fragment#onStart()}} or {@link android.app.Activity#onStart()} is called.
     */
    void onStart();

    /**
     * Callback for when {@link android.app.Fragment#onStop()}} or {@link android.app.Activity#onStop()}} is called.
     */
    void onStop();

    /**
     * Callback for when {@link android.app.Fragment#onDestroy()}} or {@link android.app.Activity#onDestroy()} is
     * called.
     */
    void onDestroy();
}

```




在 RequestManagerFragment 这个隐藏的 Fragemnt 中,  生命周期中, 会调用 lifecycle 的方法.
```java
 @Override
    public void onStart() {
        super.onStart();
        lifecycle.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        lifecycle.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        lifecycle.onDestroy();
    }
```


然后, RequestManager 中持有  lifecycle 的引用. 并且把自己作为监听器注册上.


因此, 当 RequestManagerFragment 的生命周期发生变化了, 调用 lifecycle 的相关方法的时候, lifecycle 就会去调用 注册在它上面的 listener 的相关方法, 因此, RequestManager 的方法就被调用了.

通过这样的方式, RequestManager 就能感知到 Fragemnt / Activity 的 生命周期变化了

比如, 在  RequestManager  onStart() 中, 
```java
    @Override
    public void onStart() {
        // onStart might not be called because this object may be created after the fragment/activity's onStart method.
        resumeRequests();
    }
```

```java
    public void resumeRequests() {
        Util.assertMainThread();
        requestTracker.resumeRequests();
    }
```

其实是调用 requestTracker 的相关方法去执行.

```java
// RequestTracker.java
    /**
     * Starts any not yet completed or failed requests.
     */
    public void resumeRequests() {
        isPaused = false;
        for (Request request : Util.getSnapshot(requests)) {
            if (!request.isComplete() && !request.isCancelled() && !request.isRunning()) {
                request.begin();
            }
        }
        pendingRequests.clear();
    }

```

显然, 是调用了 Request 的  begin()方法.


# 流程



Glide.with().load(url).into(imageview);
```java
    /**
     * Begin a load with Glide that will be tied to the given {@link android.app.Activity}'s lifecycle and that uses the
     * given {@link Activity}'s default options.
     *
     * @param activity The activity to use.
     * @return A RequestManager for the given activity that can be used to start a load.
     */
    public static RequestManager with(Activity activity) {
        RequestManagerRetriever retriever = RequestManagerRetriever.get();
        return retriever.get(activity);
    }

    /**
     * Begin a load with Glide that will tied to the give {@link android.support.v4.app.FragmentActivity}'s lifecycle
     * and that uses the given {@link android.support.v4.app.FragmentActivity}'s default options.
     *
     * @param activity The activity to use.
     * @return A RequestManager for the given FragmentActivity that can be used to start a load.
     */
    public static RequestManager with(FragmentActivity activity) {
        RequestManagerRetriever retriever = RequestManagerRetriever.get();
        return retriever.get(activity);
    }

```



```java
/**
 * A collection of static methods for creating new {@link com.bumptech.glide.RequestManager}s or retrieving existing
 * ones from activities and fragment.
 */
public class RequestManagerRetriever implements Handler.Callback {

    private static final RequestManagerRetriever INSTANCE = new RequestManagerRetriever();

    private volatile RequestManager applicationManager;

    ...

    /**
     * Retrieves and returns the RequestManagerRetriever singleton.
     */
    public static RequestManagerRetriever get() {
        return INSTANCE;
    }

    private RequestManager getApplicationManager(Context context) {
        // Either an application context or we're on a background thread.
        if (applicationManager == null) {
            synchronized (this) {
                if (applicationManager == null) {
                    // Normally pause/resume is taken care of by the fragment we add to the fragment or activity.
                    // However, in this case since the manager attached to the application will not receive lifecycle
                    // events, we must force the manager to start resumed using ApplicationLifecycle.
                    applicationManager = new RequestManager(context.getApplicationContext(),
                            new ApplicationLifecycle(), new EmptyRequestManagerTreeNode());
                }
            }
        }
        return applicationManager;
    }

    public RequestManager get(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("You cannot start a load on a null Context");
        } else if (Util.isOnMainThread() && !(context instanceof Application)) {
            if (context instanceof FragmentActivity) {
                return get((FragmentActivity) context);
            } else if (context instanceof Activity) {
                return get((Activity) context);
            } else if (context instanceof ContextWrapper) {
                return get(((ContextWrapper) context).getBaseContext());
            }
        }
        return getApplicationManager(context);
    }

    public RequestManager get(FragmentActivity activity) {
        if (Util.isOnBackgroundThread()) {
            return get(activity.getApplicationContext());
        } else {
            assertNotDestroyed(activity);
            FragmentManager fm = activity.getSupportFragmentManager();
            return supportFragmentGet(activity, fm);
        }
    }

    public RequestManager get(Fragment fragment) {
        if (fragment.getActivity() == null) {
            throw new IllegalArgumentException("You cannot start a load on a fragment before it is attached");
        }
        if (Util.isOnBackgroundThread()) {
            return get(fragment.getActivity().getApplicationContext());
        } else {
            FragmentManager fm = fragment.getChildFragmentManager();
            return supportFragmentGet(fragment.getActivity(), fm);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public RequestManager get(Activity activity) {
        if (Util.isOnBackgroundThread() || Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            return get(activity.getApplicationContext());
        } else {
            assertNotDestroyed(activity);
            android.app.FragmentManager fm = activity.getFragmentManager();
            return fragmentGet(activity, fm);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private static void assertNotDestroyed(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) {
            throw new IllegalArgumentException("You cannot start a load for a destroyed activity");
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public RequestManager get(android.app.Fragment fragment) {
        if (fragment.getActivity() == null) {
            throw new IllegalArgumentException("You cannot start a load on a fragment before it is attached");
        }
        if (Util.isOnBackgroundThread() || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return get(fragment.getActivity().getApplicationContext());
        } else {
            android.app.FragmentManager fm = fragment.getChildFragmentManager();
            return fragmentGet(fragment.getActivity(), fm);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    RequestManagerFragment getRequestManagerFragment(final android.app.FragmentManager fm) {
        RequestManagerFragment current = (RequestManagerFragment) fm.findFragmentByTag(FRAGMENT_TAG);
        if (current == null) {
            current = pendingRequestManagerFragments.get(fm);
            if (current == null) {
                current = new RequestManagerFragment();
                pendingRequestManagerFragments.put(fm, current);
                fm.beginTransaction().add(current, FRAGMENT_TAG).commitAllowingStateLoss();
                handler.obtainMessage(ID_REMOVE_FRAGMENT_MANAGER, fm).sendToTarget();
            }
        }
        return current;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    RequestManager fragmentGet(Context context, android.app.FragmentManager fm) {
        RequestManagerFragment current = getRequestManagerFragment(fm);
        RequestManager requestManager = current.getRequestManager();
        if (requestManager == null) {
            requestManager = new RequestManager(context, current.getLifecycle(), current.getRequestManagerTreeNode());
            current.setRequestManager(requestManager);
        }
        return requestManager;
    }

    SupportRequestManagerFragment getSupportRequestManagerFragment(final FragmentManager fm) {
        SupportRequestManagerFragment current = (SupportRequestManagerFragment) fm.findFragmentByTag(FRAGMENT_TAG);
        if (current == null) {
            current = pendingSupportRequestManagerFragments.get(fm);
            if (current == null) {
                current = new SupportRequestManagerFragment();
                pendingSupportRequestManagerFragments.put(fm, current);
                fm.beginTransaction().add(current, FRAGMENT_TAG).commitAllowingStateLoss();
                handler.obtainMessage(ID_REMOVE_SUPPORT_FRAGMENT_MANAGER, fm).sendToTarget();
            }
        }
        return current;
    }

    RequestManager supportFragmentGet(Context context, FragmentManager fm) {
        SupportRequestManagerFragment current = getSupportRequestManagerFragment(fm);
        RequestManager requestManager = current.getRequestManager();
        if (requestManager == null) {
            requestManager = new RequestManager(context, current.getLifecycle(), current.getRequestManagerTreeNode());
            current.setRequestManager(requestManager);
        }
        return requestManager;
    }

    ...
}
```

with() 获取 RequestManager 对象, 管理图片请求.
传入不同的 context, RequestManager 的生命周期不同
注意with()方法中传入的实例会决定Glide加载图片的生命周期，如果传入的是Activity或者Fragment的实例，那么当这个Activity或Fragment被销毁的时候，图片加载也会停止。如果传入的是ApplicationContext，那么只有当应用程序被杀掉的时候，图片加载才会停止。

RequestManagerRetriver 是工厂, 用于生产 RequestManager 

无界面的 Fragment
最终的流程都是一样的，那就是会向当前的Activity当中添加一个隐藏的Fragment。具体添加的逻辑是在上述代码的第117行和第141行，分别对应的app包和v4包下的两种Fragment的情况。那么这里为什么要添加一个隐藏的Fragment呢？因为Glide需要知道加载的生命周期。很简单的一个道理，如果你在某个Activity上正在加载着一张图片，结果图片还没加载出来，Activity就被用户关掉了，那么图片还应该继续加载吗？当然不应该。可是Glide并没有办法知道Activity的生命周期，于是Glide就使用了添加隐藏Fragment的这种小技巧，因为Fragment的生命周期和Activity是同步的，如果Activity被销毁了，Fragment是可以监听到的，这样Glide就可以捕获这个事件并停止图片加载了。


果我们是在非主线程当中使用的Glide，那么不管你是传入的Activity还是Fragment，都会被强制当成Application来处理。


RequestManager 作用:

1. 管理图片请求
2. 完成 Glide 对象构造
3. 控制各种方法
3. 生命周期监听




LifeCycle 进行生命周期的关联和绑定.


RequestManager 的构造
```java
if (Util.isOnBackgroundThread()) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    lifecycle.addListener(RequestManager.this);
                }
            });
        } else {
            lifecycle.addListener(this);
        }
        lifecycle.addListener(connectivityMonitor);
```

把自己作为监听器加入LifeCycler, 监听 LifeCycler, 其实就是 监听 Fragment 的生命周期.  当 Fragment 的生命周期回调, 就会通知到 RequestManager, RequestManager 进行相应的操作.



with 就是要拿到 RequestManager


# load()

由于with()方法返回的是一个RequestManager对象，那么很容易就能想到，load()方法是在RequestManager类当中的，所以说我们首先要看的就是RequestManager这个类。不过在上一篇文章中我们学过，Glide是支持图片URL字符串、图片本地路径等等加载形式的，因此RequestManager中也有很多个load()方法的重载。但是这里我们不可能把每个load()方法的重载都看一遍，因此我们就只选其中一个加载图片URL字符串的load()方法来进行研究吧。

```java
public class RequestManager implements LifecycleListener {

    ...

    /**
     * Returns a request builder to load the given {@link String}.
     * signature.
     *
     * @see #fromString()
     * @see #load(Object)
     *
     * @param string A file path, or a uri or url handled by {@link com.bumptech.glide.load.model.UriLoader}.
     */
    public DrawableTypeRequest<String> load(String string) {
        return (DrawableTypeRequest<String>) fromString().load(string);
    }

    /**
     * Returns a request builder that loads data from {@link String}s using an empty signature.
     *
     * <p>
     *     Note - this method caches data using only the given String as the cache key. If the data is a Uri outside of
     *     your control, or you otherwise expect the data represented by the given String to change without the String
     *     identifier changing, Consider using
     *     {@link GenericRequestBuilder#signature(Key)} to mixin a signature
     *     you create that identifies the data currently at the given String that will invalidate the cache if that data
     *     changes. Alternatively, using {@link DiskCacheStrategy#NONE} and/or
     *     {@link DrawableRequestBuilder#skipMemoryCache(boolean)} may be appropriate.
     * </p>
     *
     * @see #from(Class)
     * @see #load(String)
     */
    public DrawableTypeRequest<String> fromString() {
        return loadGeneric(String.class);
    }

    private <T> DrawableTypeRequest<T> loadGeneric(Class<T> modelClass) {
        ModelLoader<T, InputStream> streamModelLoader = Glide.buildStreamModelLoader(modelClass, context);
        ModelLoader<T, ParcelFileDescriptor> fileDescriptorModelLoader =
                Glide.buildFileDescriptorModelLoader(modelClass, context);
        ...
        return optionsApplier.apply(
                new DrawableTypeRequest<T>(modelClass, streamModelLoader, fileDescriptorModelLoader, context,
                        glide, requestTracker, lifecycle, optionsApplier));
    }

    ...

}
```

loadString()->fromString()->loadGeneric  这里, 会创建两个 ModelLoader

ModelLoader对象是用于加载图片的，而我们给load()方法传入不同类型的参数，这里也会得到不同的ModelLoader对象。
由于我们刚才传入的参数是String.class，因此最终得到的是StreamStringLoader对象，它是实现了ModelLoader接口的。

然后 loadGeneric 返回 DrawableTypeRequest. 也就是 loadString() 会返回一个 DrawableTypeRequest 对象, 然后调用 DrawableTypeRequest  的 load(String) 方法.


DrawableTypeRequest.load(String) 会调用父类的 DrawableRequestBuilder.load(ModelType) 方法. 
DrawableRequestBuilder 是用于配置的类. 调用 load 就是把参数丢进 DrawableRequestBuilder 里维护. 

DrawableRequestBuilder.load() 还要调用` super.load(model);`, 进入 GenericRequestBuilder , 这个是最终极的配置的. 里面维护了用户配置的所有的参数.

最终, RequestManager 的 load 方法返回 DrawableTypeRequest<String>


DrawableTypeRequest extends DrawabelTypeBuilder extends GenericRequestBuilder

在 load 之后, 拿到了 DrawableTypeRequest, 然后就可以配置很多东西了.  我们可以看到, GenericRequestBuilder 中的各种配置方法, 都是返回 this, 便于链式调用

```java
    /**
     * Sets a {@link Drawable} to display if a load fails.
     *
     * @param drawable The drawable to display.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> error(
            Drawable drawable) {
        this.errorPlaceholder = drawable;

        return this;
    }
```




# into

into()  是调用了 DrawableRequestBuilder 的 into() 方法


```java
    /**
     * {@inheritDoc}
     *
     * <p>
     *     Note - If no transformation is set for this load, a default transformation will be applied based on the
     *     value returned from {@link android.widget.ImageView#getScaleType()}. To avoid this default transformation,
     *     use {@link #dontTransform()}.
     * </p>
     *
     * @param view {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override
    public Target<GlideDrawable> into(ImageView view) {
        return super.into(view); // 调用  GenericRequestBuilder   的 into()
    }
```

```java
public Target<TranscodeType> into(ImageView view) {
    Util.assertMainThread();
    if (view == null) {
        throw new IllegalArgumentException("You must pass in a non null View");
    }
    if (!isTransformationSet && view.getScaleType() != null) {
        switch (view.getScaleType()) {
            case CENTER_CROP:
                applyCenterCrop();
                break;
            case FIT_CENTER:
            case FIT_START:
            case FIT_END:
                applyFitCenter();
                break;
            //$CASES-OMITTED$
            default:
                // Do nothing.
        }
    }
    return into(glide.buildImageViewTarget(view, transcodeClass));
}
```
最后一行代码先是调用了glide.buildImageViewTarget()方法，这个方法会构建出一个Target对象，Target对象则是用来最终展示图片用的，如果我们跟进去的话会看到如下代码：
```java
<R> Target<R> buildImageViewTarget(ImageView imageView, Class<R> transcodedClass) {
    return imageViewTargetFactory.buildTarget(imageView, transcodedClass);
}
```

```java
public class ImageViewTargetFactory {

    @SuppressWarnings("unchecked")
    public <Z> Target<Z> buildTarget(ImageView view, Class<Z> clazz) {
        if (GlideDrawable.class.isAssignableFrom(clazz)) {
            return (Target<Z>) new GlideDrawableImageViewTarget(view);
        } else if (Bitmap.class.equals(clazz)) {
            return (Target<Z>) new BitmapImageViewTarget(view);
        } else if (Drawable.class.isAssignableFrom(clazz)) {
            return (Target<Z>) new DrawableImageViewTarget(view);
        } else {
            throw new IllegalArgumentException("Unhandled class: " + clazz
                    + ", try .as*(Class).transcode(ResourceTranscoder)");
        }
    }
}
```

可以看到，在buildTarget()方法中会根据传入的class参数来构建不同的Target对象。那如果你要分析这个class参数是从哪儿传过来的，这可有得你分析了，简单起见我直接帮大家梳理清楚。这个class参数其实基本上只有两种情况，如果你在使用Glide加载图片的时候调用了asBitmap()方法，那么这里就会构建出BitmapImageViewTarget对象，否则的话构建的都是GlideDrawableImageViewTarget对象。至于上述代码中的DrawableImageViewTarget对象，这个通常都是用不到的，我们可以暂时不用管它。


也就是说，通过glide.buildImageViewTarget()方法，我们构建出了一个GlideDrawableImageViewTarget对象。那现在回到刚才into()方法的最后一行，可以看到，这里又将这个参数传入到了GenericRequestBuilder另一个接收Target对象的into()方法当中了。我们来看一下这个into()方法的源码：



```java
// GenericRequestBuilder.java
public <Y extends Target<TranscodeType>> Y into(Y target) {
    Util.assertMainThread();
    if (target == null) {
        throw new IllegalArgumentException("You must pass in a non null Target");
    }
    if (!isModelSet) {
        throw new IllegalArgumentException("You must first set a model (try #load())");
    }
    Request previous = target.getRequest();
    if (previous != null) {
        previous.clear();
        requestTracker.removeRequest(previous);
        previous.recycle();
    }
    Request request = buildRequest(target);
    target.setRequest(request);
    lifecycle.addListener(target);
    requestTracker.runRequest(request);
    return target;
}
```

这里我们还是只抓核心代码，其实只有两行是最关键的，调用`buildRequest()`方法构建出了一个Request对象，还有`requestTracker.runRequest(request);`来执行这个Request。



Request是用来发出加载图片请求的，它是Glide中非常关键的一个组件。我们先来看buildRequest()方法是如何构建Request对象的：



```java
// GenericRequestBuilder.java
private Request buildRequest(Target<TranscodeType> target) {
    if (priority == null) {
        priority = Priority.NORMAL;
    }
    return buildRequestRecursive(target, null);
}

private Request buildRequestRecursive(Target<TranscodeType> target, ThumbnailRequestCoordinator parentCoordinator) {
    if (thumbnailRequestBuilder != null) {
       ...
    } else if (thumbSizeMultiplier != null) {
        ...
    } else {
        // Base case: no thumbnail.
        return obtainRequest(target, sizeMultiplier, priority, parentCoordinator);
    }
}

private Request obtainRequest(Target<TranscodeType> target, float sizeMultiplier, Priority priority,
        RequestCoordinator requestCoordinator) {
    return GenericRequest.obtain(
            loadProvider,
            model,
            signature,
            context,
            priority,
            target,
            sizeMultiplier,
            placeholderDrawable,
            placeholderId,
            errorPlaceholder,
            errorId,
            fallbackDrawable,
            fallbackResource,
            requestListener,
            requestCoordinator,
            glide.getEngine(),
            transformation,
            transcodeClass,
            isCacheable,
            animationFactory,
            overrideWidth,
            overrideHeight,
            diskCacheStrategy);
}
```


注意这个obtain()方法需要传入非常多的参数，而其中很多的参数我们都是比较熟悉的，像什么placeholderId、errorPlaceholder、diskCacheStrategy等等。

这些 参数, 都是我们在  Glide.with().load()  之后指定的那些参数, 现在都传入了 GenericRequest.obtain 方法中, 然后用这些参数组装 Request 对象了

那么我们进入到这个GenericRequest的obtain()方法瞧一瞧：
```java
public final class GenericRequest<A, T, Z, R> implements Request, SizeReadyCallback,
        ResourceCallback {
    ...
    public static <A, T, Z, R> GenericRequest<A, T, Z, R> obtain(
            LoadProvider<A, T, Z, R> loadProvider,
            A model,
            Key signature,
            Context context,
            Priority priority,
            Target<R> target,
            float sizeMultiplier,
            Drawable placeholderDrawable,
            int placeholderResourceId,
            Drawable errorDrawable,
            int errorResourceId,
            Drawable fallbackDrawable,
            int fallbackResourceId,
            RequestListener<? super A, R> requestListener,
            RequestCoordinator requestCoordinator,
            Engine engine,
            Transformation<Z> transformation,
            Class<R> transcodeClass,
            boolean isMemoryCacheable,
            GlideAnimationFactory<R> animationFactory,
            int overrideWidth,
            int overrideHeight,
            DiskCacheStrategy diskCacheStrategy) {
        @SuppressWarnings("unchecked")
        GenericRequest<A, T, Z, R> request = (GenericRequest<A, T, Z, R>) REQUEST_POOL.poll();
        if (request == null) {
            request = new GenericRequest<A, T, Z, R>();
        }
        request.init(loadProvider,
                model,
                signature,
                context,
                priority,
                target,
                sizeMultiplier,
                placeholderDrawable,
                placeholderResourceId,
                errorDrawable,
                errorResourceId,
                fallbackDrawable,
                fallbackResourceId,
                requestListener,
                requestCoordinator,
                engine,
                transformation,
                transcodeClass,
                isMemoryCacheable,
                animationFactory,
                overrideWidth,
                overrideHeight,
                diskCacheStrategy);
        return request;
    }

    ...
}
```


这里去new了一个GenericRequest对象，然后调用 GenericRequest的init(), 把参数都组装到 Request 中,     并在最后一行返回，也就是说，obtain()方法实际上获得的就是一个GenericRequest对象。


好，那现在解决了构建Request对象的问题，接下来我们看一下这个Request对象又是怎么执行的。

回到刚才的into()方法，你会发现调用了requestTracker.runRequest()方法来去执行这个Request，那么我们跟进去瞧一瞧

```java
    /**
     * Starts tracking the given request.
     */
    public void runRequest(Request request) {
        requests.add(request);
        if (!isPaused) {
            request.begin();
        } else {
            pendingRequests.add(request);
        }
    }
```

这里有一个简单的逻辑判断，就是先判断Glide当前是不是处理暂停状态，如果不是暂停状态就调用Request的begin()方法来执行Request，否则的话就先将Request添加到待执行队列里面，等暂停状态解除了之后再执行。


```java
// GenericRequest.java
@Override
public void begin() {
    startTime = LogTime.getLogTime();
    if (model == null) {
        onException(null);
        return;
    }
    status = Status.WAITING_FOR_SIZE;
    if (Util.isValidDimensions(overrideWidth, overrideHeight)) {
        onSizeReady(overrideWidth, overrideHeight);
    } else {
        target.getSize(this);
    }
    if (!isComplete() && !isFailed() && canNotifyStatusChanged()) {
        target.onLoadStarted(getPlaceholderDrawable());
    }
    if (Log.isLoggable(TAG, Log.VERBOSE)) {
        logV("finished run method in " + LogTime.getElapsedMillis(startTime));
    }
}
```


这里我们来注意几个细节，首先如果model等于null，model也就是我们在第二步load()方法中传入的图片URL地址，这个时候会调用onException()方法。如果你跟到onException()方法里面去看看，你会发现它最终会调用到一个setErrorPlaceholder()当中，如下所示：


```java
    /**
     * A callback method that should never be invoked directly.
     */
    @Override
    public void onException(Exception e) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "load failed", e);
        }

        status = Status.FAILED;
        //TODO: what if this is a thumbnail request?
        if (requestListener == null || !requestListener.onException(e, model, target, isFirstReadyResource())) {
            setErrorPlaceholder(e);
        }
    }
```
```java
    private void setErrorPlaceholder(Exception e) {
        if (!canNotifyStatusChanged()) {
            return;
        }

        Drawable error = model == null ? getFallbackDrawable() : null;
        if (error == null) {
          error = getErrorDrawable();
        }
        if (error == null) {
            error = getPlaceholderDrawable();
        }
        target.onLoadFailed(e, error);
    }
```

这个方法中会先去获取一个error的占位图，如果获取不到的话会再去获取一个loading占位图，然后调用target.onLoadFailed()方法并将占位图传入。那么onLoadFailed()方法中做了什么呢？我们看一下：

```java
// ImageView.java
public abstract class ImageViewTarget<Z> extends ViewTarget<ImageView, Z> implements GlideAnimation.ViewAdapter {
    ..
    @Override
    public void onLoadStarted(Drawable placeholder) {
        view.setImageDrawable(placeholder);
    }

    @Override
    public void onLoadFailed(Exception e, Drawable errorDrawable) {
        view.setImageDrawable(errorDrawable);
    }
    ...
}
```

就是将这张error占位图显示到ImageView上而已，因为现在出现了异常，没办法展示正常的图片了。


begin()方法中还调用了`target.onLoadStarted(getPlaceholderDrawable());`并传入了一个loading占位图，在也就说，在图片请求开始之前，会先使用这张占位图代替最终的图片显示。

begin()方法的分情况调用了`onSizeReady(overrideWidth, overrideHeight);` 和 `target.getSize()`。这里要分两种情况，一种是你使用了override() API为图片指定了一个固定的宽高，一种是没有指定。如果指定了的话，就会执行onSizeReady()方法。如果没指定的话，就会执行target.getSize()方法。这个target.getSize()方法的内部会根据ImageView的layout_width和layout_height值做一系列的计算，来算出图片应该的宽高。总之在计算完之后，它也会调用onSizeReady()方法。也就是说，不管是哪种情况，最终都会调用到onSizeReady()方法，在这里进行下一步操作。那么我们跟到这个方法里面来：


```java
@Override
public void onSizeReady(int width, int height) {
    ...
    status = Status.RUNNING;
    width = Math.round(sizeMultiplier * width);
    height = Math.round(sizeMultiplier * height);
    ModelLoader<A, T> modelLoader = loadProvider.getModelLoader();
    final DataFetcher<T> dataFetcher = modelLoader.getResourceFetcher(model, width, height);
    if (dataFetcher == null) {
        onException(new Exception("Failed to load model: \'" + model + "\'"));
        return;
    }
    ResourceTranscoder<Z, R> transcoder = loadProvider.getTranscoder();
    if (Log.isLoggable(TAG, Log.VERBOSE)) {
        logV("finished setup for calling load in " + LogTime.getElapsedMillis(startTime));
    }
    loadedFromMemoryCache = true;
    loadStatus = engine.load(signature, width, height, dataFetcher, loadProvider, transformation, transcoder,
            priority, isMemoryCacheable, diskCacheStrategy, this);
    loadedFromMemoryCache = resource != null;
    if (Log.isLoggable(TAG, Log.VERBOSE)) {
        logV("finished onSizeReady in " + LogTime.getElapsedMillis(startTime));
    }
}
```

`loadProvider.getModelLoader();`

那么我们第一个要搞清楚的就是，这个loadProvider是什么？要搞清楚这点，需要先回到第二步的load()方法当中。还记得load()方法是返回一个DrawableTypeRequest对象吗？  记得.   在 RequestManager.load()方法返回的时候, 返回了一个 DrawableTypeRequest<String>对象, 该对象的部分属性已经被构造好了, 比如, url 之类的.  然后, load() 之后, 还可以调用 placeholder()  errorholder() 之类的, 设置其他参数, 设置后都是返回 DrawableTypeRequest<String> 对象

刚才我们只是分析了DrawableTypeRequest当中的asBitmap()和asGif()方法，并没有仔细看它的构造函数，现在我们重新来看一下DrawableTypeRequest类的构造函数：

```java
// DrawableTypeRequest.java
public class DrawableTypeRequest<ModelType> extends DrawableRequestBuilder<ModelType> implements DownloadOptions {
    private final ModelLoader<ModelType, InputStream> streamModelLoader;
    private final ModelLoader<ModelType, ParcelFileDescriptor> fileDescriptorModelLoader;
    private final RequestManager.OptionsApplier optionsApplier;

    private static <A, Z, R> FixedLoadProvider<A, ImageVideoWrapper, Z, R> buildProvider(Glide glide,
            ModelLoader<A, InputStream> streamModelLoader,
            ModelLoader<A, ParcelFileDescriptor> fileDescriptorModelLoader, Class<Z> resourceClass,
            Class<R> transcodedClass,
            ResourceTranscoder<Z, R> transcoder) {
        if (streamModelLoader == null && fileDescriptorModelLoader == null) {
            return null;
        }

        if (transcoder == null) {
            transcoder = glide.buildTranscoder(resourceClass, transcodedClass);
        }
        DataLoadProvider<ImageVideoWrapper, Z> dataLoadProvider = glide.buildDataProvider(ImageVideoWrapper.class,
                resourceClass);
        ImageVideoModelLoader<A> modelLoader = new ImageVideoModelLoader<A>(streamModelLoader,
                fileDescriptorModelLoader);
        return new FixedLoadProvider<A, ImageVideoWrapper, Z, R>(modelLoader, transcoder, dataLoadProvider);
    }

    DrawableTypeRequest(Class<ModelType> modelClass, ModelLoader<ModelType, InputStream> streamModelLoader,
            ModelLoader<ModelType, ParcelFileDescriptor> fileDescriptorModelLoader, Context context, Glide glide,
            RequestTracker requestTracker, Lifecycle lifecycle, RequestManager.OptionsApplier optionsApplier) {
        super(context, modelClass,
        // 这里调用了 buildProvider, 
                buildProvider(glide, streamModelLoader, fileDescriptorModelLoader, GifBitmapWrapper.class,
                        GlideDrawable.class, null),
                glide, requestTracker, lifecycle);
        this.streamModelLoader = streamModelLoader;
        this.fileDescriptorModelLoader = fileDescriptorModelLoader;
        this.optionsApplier = optionsApplier;
    }
}
```

可以看到，是构造函数中，调用 super 的时候, 调用了一个buildProvider()方法，并把streamModelLoader和fileDescriptorModelLoader等参数传入到这个方法中，这两个ModelLoader就是之前在loadGeneric()方法中构建出来的。


那么我们再来看一下 buildProvider()方法里面做了什么

1. 调用了glide.buildTranscoder()方法来构建一个ResourceTranscoder，它是用于对图片进行转码的，由于 ResourceTranscoder 是一个接口，这里实际会构建出一个GifBitmapWrapperDrawableTranscoder对象。

2. 调用了glide.buildDataProvider()方法来构建一个DataLoadProvider，它是用于对图片进行编解码的，由于DataLoadProvider是一个接口，这里实际会构建出一个ImageVideoGifDrawableLoadProvider对象。

3. new了一个ImageVideoModelLoader的实例，并把之前loadGeneric()方法中构建的两个ModelLoader封装到了ImageVideoModelLoader当中。

也就是说 ImageVideoModelLoader 是 streamModelLoader的一个包装类. 最终执行东西还是 streamModelLoader 来做的应该.

4. 最后，new出一个 FixedLoadProvider ，并把刚才构建的出来的GifBitmapWrapperDrawableTranscoder、ImageVideoModelLoader、ImageVideoGifDrawableLoadProvider 都封装进去，构造一个 FixedLoadProvider对象.    然后回到构造方法中, 把这个 FixedLoadProvider 作为第三个参数传入 DrawableRequestBuilder  的 构造.  最终赋给了: GenericRequestBuilder  的 loadProvider.   

因此, 在 onSizeReady() 方法中 拿到的的loadProvider 就是 这个 FixedLoadProvider对象.

```java
@Override
public void onSizeReady(int width, int height) {
    ...
    status = Status.RUNNING;
    width = Math.round(sizeMultiplier * width);
    height = Math.round(sizeMultiplier * height);
    ModelLoader<A, T> modelLoader = loadProvider.getModelLoader();
    final DataFetcher<T> dataFetcher = modelLoader.getResourceFetcher(model, width, height);
    if (dataFetcher == null) {
        onException(new Exception("Failed to load model: \'" + model + "\'"));
        return;
    }
    ResourceTranscoder<Z, R> transcoder = loadProvider.getTranscoder();
...
    loadedFromMemoryCache = true;
    loadStatus = engine.load(signature, width, height, dataFetcher, loadProvider, transformation, transcoder,
            priority, isMemoryCacheable, diskCacheStrategy, this);
    ...
}
```

在 onSizeReady()方法中, 分别调用了loadProvider的getModelLoader()方法和getTranscoder()方法，那么得到的对象也就是刚才我们分析的ImageVideoModelLoader和GifBitmapWrapperDrawableTranscoder了。

还调用了 `modelLoader.getResourceFetcher`

```java
public class ImageVideoModelLoader<A> implements ModelLoader<A, ImageVideoWrapper> {
    private static final String TAG = "IVML";

    private final ModelLoader<A, InputStream> streamLoader;
    private final ModelLoader<A, ParcelFileDescriptor> fileDescriptorLoader;

    public ImageVideoModelLoader(ModelLoader<A, InputStream> streamLoader,
            ModelLoader<A, ParcelFileDescriptor> fileDescriptorLoader) {
        if (streamLoader == null && fileDescriptorLoader == null) {
            throw new NullPointerException("At least one of streamLoader and fileDescriptorLoader must be non null");
        }
        this.streamLoader = streamLoader;
        this.fileDescriptorLoader = fileDescriptorLoader;
    }

    @Override
    public DataFetcher<ImageVideoWrapper> getResourceFetcher(A model, int width, int height) {
        DataFetcher<InputStream> streamFetcher = null;
        if (streamLoader != null) {
            streamFetcher = streamLoader.getResourceFetcher(model, width, height);
        }
        DataFetcher<ParcelFileDescriptor> fileDescriptorFetcher = null;
        if (fileDescriptorLoader != null) {
            fileDescriptorFetcher = fileDescriptorLoader.getResourceFetcher(model, width, height);
        }

        if (streamFetcher != null || fileDescriptorFetcher != null) {
            return new ImageVideoFetcher(streamFetcher, fileDescriptorFetcher);
        } else {
            return null;
        }
    }

    static class ImageVideoFetcher implements DataFetcher<ImageVideoWrapper> {
        private final DataFetcher<InputStream> streamFetcher;
        private final DataFetcher<ParcelFileDescriptor> fileDescriptorFetcher;

        public ImageVideoFetcher(DataFetcher<InputStream> streamFetcher,
                DataFetcher<ParcelFileDescriptor> fileDescriptorFetcher) {
            this.streamFetcher = streamFetcher;
            this.fileDescriptorFetcher = fileDescriptorFetcher;
        }

        ...
    }
}
```

可以看到，会先调用streamLoader.getResourceFetcher()方法获取一个DataFetcher，而这个streamLoader其实就是我们在loadGeneric()方法中构建出的StreamStringLoader ，调用它的getResourceFetcher()方法会得到一个HttpUrlFetcher对象。最后, new出了一个ImageVideoFetcher对象，并把获得的HttpUrlFetcher对象传进去。也就是说，ImageVideoModelLoader的getResourceFetcher()方法得到的是一个ImageVideoFetcher。这个 Fetcher 里有个 HttpUrlFetcher. 感觉它才是真正执行的.


那么我们再次回到 onSizeReady() 方法，在onSizeReady()方法的最后，这里将刚才获得的ImageVideoFetcher、GifBitmapWrapperDrawableTranscoder等等一系列的值一起传入到了Engine的load()方法当中。接下来我们就要看一看，这个Engine的load()方法当中，到底做了什么？代码如下所示：

```java
public class Engine implements EngineJobListener,
        MemoryCache.ResourceRemovedListener,
        EngineResource.ResourceListener {

    ...    

    public <T, Z, R> LoadStatus load(Key signature, int width, int height, DataFetcher<T> fetcher,
            DataLoadProvider<T, Z> loadProvider, Transformation<Z> transformation, ResourceTranscoder<Z, R> transcoder,
            Priority priority, boolean isMemoryCacheable, DiskCacheStrategy diskCacheStrategy, ResourceCallback cb) {
        ...
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

构建 EngineJob, 它的主要作用就是用来开启线程的，为后面的异步加载图片做准备。
\
接着创建了一个DecodeJob对象，从名字上来看，它好像是用来对图片进行解码的

创建了一个EngineRunnable对象, 并把 engineJob/encodeJob 传入. 这个 EngineRunnable  就是一个 Runnable

并且用了EngineJob的start()方法来运行EngineRunnable对象，这实际上就是让EngineRunnable的run()方法在子线程当中执行了。那么我们现在就可以去看看EngineRunnable的run()方法里做了些什么，如下所示：


```java

    public void start(EngineRunnable engineRunnable) {
        this.engineRunnable = engineRunnable;
        future = diskCacheService.submit(engineRunnable);
    }

```

`diskCacheService` 是 `private final ExecutorService diskCacheService;`

然后把  engineRunnable 丢到 线程池去执行了


突然想知道 这个 线程池是在哪里创建的.
 是在 EngineJob 的构造

```java
    public EngineJob(Key key, ExecutorService diskCacheService, ExecutorService sourceService, boolean isCacheable,
            EngineJobListener listener, EngineResourceFactory engineResourceFactory) {
        this.key = key;
        this.diskCacheService = diskCacheService;
        ...
    }
```

EngineJob哪里创建?就上面的 `EngineJob engineJob = engineJobFactory.build(key, isMemoryCacheable);`

```java
        public EngineJobFactory(ExecutorService diskCacheService, ExecutorService sourceService,
                EngineJobListener listener) {
            this.diskCacheService = diskCacheService;
            this.sourceService = sourceService;
            this.listener = listener;
        }
public EngineJob build(Key key, boolean isMemoryCacheable) {
            return new EngineJob(key, diskCacheService, sourceService, isMemoryCacheable, listener);
        }
```
engineJobFactory.build 调用  这个 build()方法的时候, diskCacheService 已经有值了.


看  EngineJobFactory  的构造哪里调用的:
创建Engine 的时候.

最终找到是在 GlideBuilder.java 中
```java
Glide createGlide() {
        if (sourceService == null) {
            final int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
            sourceService = new FifoPriorityThreadPoolExecutor(cores);
        }
        if (diskCacheService == null) {
            diskCacheService = new FifoPriorityThreadPoolExecutor(1);
        }

        MemorySizeCalculator calculator = new MemorySizeCalculator(context);
        if (bitmapPool == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                int size = calculator.getBitmapPoolSize();
                bitmapPool = new LruBitmapPool(size);
            } else {
                bitmapPool = new BitmapPoolAdapter();
            }
        }

        if (memoryCache == null) {
            memoryCache = new LruResourceCache(calculator.getMemoryCacheSize());
        }

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

这里创建了 Engine.  还创建了 Glide

`createGlide` 是 在  RequestManager的构造中会调用

RequestManager 的构造是在 RequestManagerRetriever 的 fragmentGet()方法中会调用.
最终就回溯到了 Glide.with()方法. 在 这个 with() 方法里创建好了很多东西...
 



EngineRunnable 的 run 方法
```java
@Override
public void run() {
    if (isCancelled) {
        return;
    }
    Exception exception = null;
    Resource<?> resource = null;
    try {
        resource = decode();
    } catch (Exception e) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Exception decoding", e);
        }
        exception = e;
    }
    if (isCancelled) {
        if (resource != null) {
            resource.recycle();
        }
        return;
    }
    if (resource == null) {
        onLoadFailed(exception);
    } else {
        onLoadComplete(resource);
    }
}
```
调用了一个decode()方法，并且这个方法返回了一个Resource对象。然后就 onLoadFailed 和 onLoadComplete 了.  看上去所有的逻辑应该都在这个decode()方法执行的了，那我们跟进去瞧一瞧：

```java
private Resource<?> decode() throws Exception {
    if (isDecodingFromCache()) {
        return decodeFromCache();
    } else {
        return decodeFromSource();
    }
}
```


decode()方法中又分了两种情况，从缓存当中去decode图片的话就会执行decodeFromCache()，否则的话就执行decodeFromSource()。本篇文章中我们不讨论缓存的情况，那么就直接来看decodeFromSource()方法的代码吧，如下所示：
```java
private Resource<?> decodeFromSource() throws Exception {
    return decodeJob.decodeFromSource();
}
```




```java
// DecodeJob.java
    public Resource<Z> decodeFromSource() throws Exception {
        // 先  decodeSource
        Resource<T> decoded = decodeSource();
        // 再 transform
        return transformEncodeAndTranscode(decoded);
    }


    private Resource<T> decodeSource() throws Exception {
        Resource<T> decoded = null;
        try {
            long startTime = LogTime.getLogTime();
            // 这个 fetcher 是: ImageVideoFetcher
            // 拿到的data 是 ImageVideoWrapper类型
            final A data = fetcher.loadData(priority);
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                logWithTimeAndKey("Fetched data", startTime);
            }
            if (isCancelled) {
                return null;
            }
            decoded = decodeFromSourceData(data);
        } finally {
            fetcher.cleanup();
        }
        return decoded;
    }

    private Resource<Z> transformEncodeAndTranscode(Resource<T> decoded) {
        long startTime = LogTime.getLogTime();
        Resource<T> transformed = transform(decoded);
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            logWithTimeAndKey("Transformed resource from source", startTime);
        }

        writeTransformedToCache(transformed);

        startTime = LogTime.getLogTime();
        Resource<Z> result = transcode(transformed);
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            logWithTimeAndKey("Transcoded transformed from source", startTime);
        }
        return result;
    }
```


看下 ImageViewFetcher
```java
@Override
public ImageVideoWrapper loadData(Priority priority) throws Exception {
    InputStream is = null;
    if (streamFetcher != null) {
        try {
            // 这个streamFetcher是什么呢？自然就是刚才在组装ImageVideoFetcher对象时传进来的HttpUrlFetcher了
            // 拿到 InputStream
            is = streamFetcher.loadData(priority);
        } catch (Exception e) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Exception fetching input stream, trying ParcelFileDescriptor", e);
            }
            if (fileDescriptorFetcher == null) {
                throw e;
            }
        }
    }
    ParcelFileDescriptor fileDescriptor = null;
    if (fileDescriptorFetcher != null) {
        try {
            fileDescriptor = fileDescriptorFetcher.loadData(priority);
        } catch (Exception e) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Exception fetching ParcelFileDescriptor", e);
            }
            if (is == null) {
                throw e;
            }
        }
    }
    return new ImageVideoWrapper(is, fileDescriptor);
}
```
因此这里又会去调用HttpUrlFetcher的loadData()方法，那么我们继续跟进去瞧一瞧：

```java
public class HttpUrlFetcher implements DataFetcher<InputStream> {

    ...

    @Override
    public InputStream loadData(Priority priority) throws Exception {
        return loadDataWithRedirects(glideUrl.toURL(), 0 /*redirects*/, null /*lastUrl*/, glideUrl.getHeaders());
    }

    private InputStream loadDataWithRedirects(URL url, int redirects, URL lastUrl, Map<String, String> headers)
            throws IOException {
        if (redirects >= MAXIMUM_REDIRECTS) {
            throw new IOException("Too many (> " + MAXIMUM_REDIRECTS + ") redirects!");
        } else {
            // Comparing the URLs using .equals performs additional network I/O and is generally broken.
            // See http://michaelscharf.blogspot.com/2006/11/javaneturlequals-and-hashcode-make.html.
            try {
                if (lastUrl != null && url.toURI().equals(lastUrl.toURI())) {
                    throw new IOException("In re-direct loop");
                }
            } catch (URISyntaxException e) {
                // Do nothing, this is best effort.
            }
        }
        urlConnection = connectionFactory.build(url);
        for (Map.Entry<String, String> headerEntry : headers.entrySet()) {
          urlConnection.addRequestProperty(headerEntry.getKey(), headerEntry.getValue());
        }
        urlConnection.setConnectTimeout(2500);
        urlConnection.setReadTimeout(2500);
        urlConnection.setUseCaches(false);
        urlConnection.setDoInput(true);

        // Connect explicitly to avoid errors in decoders if connection fails.
        urlConnection.connect();
        if (isCancelled) {
            return null;
        }
        final int statusCode = urlConnection.getResponseCode();
        if (statusCode / 100 == 2) { // 2XX的状态码这样判断...   通过 getStreamForSuccessfulRequest拿到 InputStream
            return getStreamForSuccessfulRequest(urlConnection);
        } else if (statusCode / 100 == 3) {
            String redirectUrlString = urlConnection.getHeaderField("Location");
            if (TextUtils.isEmpty(redirectUrlString)) {
                throw new IOException("Received empty or null redirect url");
            }
            URL redirectUrl = new URL(url, redirectUrlString);
            return loadDataWithRedirects(redirectUrl, redirects + 1, url, headers);
        } else {
            if (statusCode == -1) {
                throw new IOException("Unable to retrieve response code from HttpUrlConnection.");
            }
            throw new IOException("Request failed " + statusCode + ": " + urlConnection.getResponseMessage());
        }
    }

    private InputStream getStreamForSuccessfulRequest(HttpURLConnection urlConnection)
            throws IOException {
        if (TextUtils.isEmpty(urlConnection.getContentEncoding())) {
            int contentLength = urlConnection.getContentLength();
            stream = ContentLengthInputStream.obtain(urlConnection.getInputStream(), contentLength);
        } else {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Got non empty content encoding: " + urlConnection.getContentEncoding());
            }
            stream = urlConnection.getInputStream();
        }
        return stream;
    }

    ...
}
```

看到 HttpUrlFetcher的loadData()方法  是使用 UrlConnection 去请求网络了, 并返回了 InputStream

回到刚才ImageVideoFetcher的loadData()方法中，在这个方法的最后一行，创建了一个ImageVideoWrapper对象，并把刚才得到的InputStream作为参数传了进去。


回到DecodeJob的decodeSource()方法当中,   拿到的 data 是ImageVideoWrapper 对象, 然后调用: decodeFromSourceData(data)

```java
private Resource<T> decodeFromSourceData(A data) throws IOException {
    final Resource<T> decoded;
    ...
    decoded = loadProvider.getSourceDecoder().decode(data, width, height);
    ...
    return decoded;
}
```

loadProvider就是刚才在onSizeReady()方法中得到的FixedLoadProvider，而getSourceDecoder()得到的则是一个GifBitmapWrapperResourceDecoder对象，也就是要调用这个对象的decode()方法来对图片进行解码。那么我们来看下 GifBitmapWrapperResourceDecoder 的代码：

```java
public class GifBitmapWrapperResourceDecoder implements ResourceDecoder<ImageVideoWrapper, GifBitmapWrapper> {
    ...
    @SuppressWarnings("resource")
    // @see ResourceDecoder.decode
    @Override
    public Resource<GifBitmapWrapper> decode(ImageVideoWrapper source, int width, int height) throws IOException {
        ByteArrayPool pool = ByteArrayPool.get();
        byte[] tempBytes = pool.getBytes();
        GifBitmapWrapper wrapper = null;
        try {
            wrapper = decode(source, width, height, tempBytes);
        } finally {
            pool.releaseBytes(tempBytes);
        }
        return wrapper != null ? new GifBitmapWrapperResource(wrapper) : null;
    }

    private GifBitmapWrapper decode(ImageVideoWrapper source, int width, int height, byte[] bytes) throws IOException {
        final GifBitmapWrapper result;
        if (source.getStream() != null) {
            // 走这里
            result = decodeStream(source, width, height, bytes);
        } else {
            result = decodeBitmapWrapper(source, width, height);
        }
        return result;
    }

    private GifBitmapWrapper decodeStream(ImageVideoWrapper source, int width, int height, byte[] bytes)
            throws IOException {
        InputStream bis = streamFactory.build(source.getStream(), bytes);     
        bis.mark(MARK_LIMIT_BYTES);
        // // decodeStream()方法中会先从流中读取2个字节的数据，来判断这张图是GIF图还是普通的静图，如果是GIF图就调用decodeGifWrapper()方法来进行解码，如果是普通的静图就用调用decodeBitmapWrapper()方法来进行解码。

        // parser  是 ImageHeaderParser
        ImageHeaderParser.ImageType type = parser.parse(bis);
        bis.reset();
        GifBitmapWrapper result = null;
        if (type == ImageHeaderParser.ImageType.GIF) {
            result = decodeGifWrapper(bis, width, height);
        }
        // Decoding the gif may fail even if the type matches.
        if (result == null) {
            // We can only reset the buffered InputStream, so to start from the beginning of the stream, we need to
            // pass in a new source containing the buffered stream rather than the original stream.
            ImageVideoWrapper forBitmapDecoder = new ImageVideoWrapper(bis, source.getFileDescriptor());
            // 静图: 调用decodeBitmapWrapper()
            result = decodeBitmapWrapper(forBitmapDecoder, width, height);
        }
        return result;
    }

    private GifBitmapWrapper decodeBitmapWrapper(ImageVideoWrapper toDecode, int width, int height) throws IOException {
        GifBitmapWrapper result = null;
        // bitmapDecoder 是: ImageVideoBitmapDecoder对象
        Resource<Bitmap> bitmapResource = bitmapDecoder.decode(toDecode, width, height);
        if (bitmapResource != null) {
            result = new GifBitmapWrapper(bitmapResource, null);
        }
        return result;
    }

    ...
}
```


ImageViewBitmapDecoder.java
```java
public class ImageVideoBitmapDecoder implements ResourceDecoder<ImageVideoWrapper, Bitmap> {
    private final ResourceDecoder<InputStream, Bitmap> streamDecoder;
    private final ResourceDecoder<ParcelFileDescriptor, Bitmap> fileDescriptorDecoder;

    public ImageVideoBitmapDecoder(ResourceDecoder<InputStream, Bitmap> streamDecoder,
            ResourceDecoder<ParcelFileDescriptor, Bitmap> fileDescriptorDecoder) {
        this.streamDecoder = streamDecoder;
        this.fileDescriptorDecoder = fileDescriptorDecoder;
    }

    @Override
    public Resource<Bitmap> decode(ImageVideoWrapper source, int width, int height) throws IOException {
        Resource<Bitmap> result = null;
        InputStream is = source.getStream();// 获取到服务器返回的InputStream
        if (is != null) {
            try {
                // streamDecode是一个StreamBitmapDecoder对象
                result = streamDecoder.decode(is, width, height);
            } catch (IOException e) {
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "Failed to load image from stream, trying FileDescriptor", e);
                }
            }
        }
        if (result == null) {
            ParcelFileDescriptor fileDescriptor = source.getFileDescriptor();
            if (fileDescriptor != null) {
                result = fileDescriptorDecoder.decode(fileDescriptor, width, height);
            }
        }
        return result;
    }

    ...
}
```
看下 StreamBitmapDecoder  的 decode

```java
// StreamBitmapDecoder.java
public class StreamBitmapDecoder implements ResourceDecoder<InputStream, Bitmap> {

    ...

    private final Downsampler downsampler;
    private BitmapPool bitmapPool;
    private DecodeFormat decodeFormat;

    public StreamBitmapDecoder(Downsampler downsampler, BitmapPool bitmapPool, DecodeFormat decodeFormat) {
        this.downsampler = downsampler;
        this.bitmapPool = bitmapPool;
        this.decodeFormat = decodeFormat;
    }

    @Override
    public Resource<Bitmap> decode(InputStream source, int width, int height) {
        // 又去调用了Downsampler的decode()方法
        // 这里最后返回了 一个 Bitmap, 大概可以用了
        Bitmap bitmap = downsampler.decode(source, bitmapPool, width, height, decodeFormat);
        // 又调用了BitmapResource.obtain()方法，将Bitmap对象包装成了Resource<Bitmap>对象。
        return BitmapResource.obtain(bitmap, bitmapPool);
    }

    ...
}
```

```java
public abstract class Downsampler implements BitmapDecoder<InputStream> {

    ...

    @Override
    public Bitmap decode(InputStream is, BitmapPool pool, int outWidth, int outHeight, DecodeFormat decodeFormat) {
            ...
            options.inTempStorage = bytesForOptions;
            // 这里 先调用 getDimensions()获取图片真实的尺寸, 内部: options.inJustDecodeBounds = true;, 只计算一下大小
            final int[] inDimens = getDimensions(invalidatingStream, bufferedStream, options);
            final int inWidth = inDimens[0]; // 图片真实宽
            final int inHeight = inDimens[1];// 图片高
            final int degreesToRotate = TransformationUtils.getExifOrientationDegrees(orientation);
            // 这个是计算一下, 要不要压缩, 压缩比例是多少
            final int sampleSize = getRoundedSampleSize(degreesToRotate, inWidth, inHeight, outWidth, outHeight);
            final Bitmap downsampled =
            // downsampleWithSize 里面还是会调用 decodeStream.  拿到BitmapFactory.decodeStream()返回的 Bitmap 对象
                    downsampleWithSize(invalidatingStream, bufferedStream, options, pool, inWidth, inHeight, sampleSize,
                            decodeFormat);
            // BitmapFactory swallows exceptions during decodes and in some cases when inBitmap is non null, may catch
            // and log a stack trace but still return a non null bitmap. To avoid displaying partially decoded bitmaps,
            // we catch exceptions reading from the stream in our ExceptionCatchingInputStream and throw them here.
            final Exception streamException = exceptionStream.getException();
            if (streamException != null) {
                throw new RuntimeException(streamException);
            }
            Bitmap rotated = null;
            if (downsampled != null) {
                rotated = TransformationUtils.rotateImageExif(downsampled, pool, orientation);
                if (!downsampled.equals(rotated) && !pool.put(downsampled)) {
                    downsampled.recycle();
                }
            }
            return rotated;
        } finally {
            byteArrayPool.releaseBytes(bytesForOptions);
            byteArrayPool.releaseBytes(bytesForStream);
            exceptionStream.release();
            releaseOptions(options);
        }
    }

    private Bitmap downsampleWithSize(MarkEnforcingInputStream is, RecyclableBufferedInputStream  bufferedStream,
            BitmapFactory.Options options, BitmapPool pool, int inWidth, int inHeight, int sampleSize,
            DecodeFormat decodeFormat) {
        // Prior to KitKat, the inBitmap size must exactly match the size of the bitmap we're decoding.
        Bitmap.Config config = getConfig(is, decodeFormat);
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = config;
        if ((options.inSampleSize == 1 || Build.VERSION_CODES.KITKAT <= Build.VERSION.SDK_INT) && shouldUsePool(is)) {
            int targetWidth = (int) Math.ceil(inWidth / (double) sampleSize);
            int targetHeight = (int) Math.ceil(inHeight / (double) sampleSize);
            // BitmapFactory will clear out the Bitmap before writing to it, so getDirty is safe.
            setInBitmap(options, pool.getDirty(targetWidth, targetHeight, config));
        }
        return decodeStream(is, bufferedStream, options);
    }

    /**
     * A method for getting the dimensions of an image from the given InputStream.
     *
     * @param is The InputStream representing the image.
     * @param options The options to pass to
     *          {@link BitmapFactory#decodeStream(InputStream, android.graphics.Rect,
     *              BitmapFactory.Options)}.
     * @return an array containing the dimensions of the image in the form {width, height}.
     */
    public int[] getDimensions(MarkEnforcingInputStream is, RecyclableBufferedInputStream bufferedStream,
            BitmapFactory.Options options) {
        options.inJustDecodeBounds = true; // 只计算图片大小, 不加载到内存
        decodeStream(is, bufferedStream, options);
        options.inJustDecodeBounds = false; // 改回来, 下次就加载到内存了
        // 返回图片真实的尺寸
        return new int[] { options.outWidth, options.outHeight };
    }

    private static Bitmap decodeStream(MarkEnforcingInputStream is, RecyclableBufferedInputStream bufferedStream,
            BitmapFactory.Options options) {
         ...
         // 当 inJustDecodeBounds = true, 这里返回值 resule 应该是 null, 返回值没用
         // 当 inJustDecodeBounds = false, 返回 Bitmap, 就是真正解码后的 Bitmap 了.
        final Bitmap result = BitmapFactory.decodeStream(is, null, options);
         ...
        return result;
    }

    ...
}
```



回到刚才的StreamBitmapDecoder当中，你会发现，它的decode()方法返回的是一个Resource<Bitmap>对象。而我们从Downsampler中得到的是一个Bitmap对象，因此这里又调用了BitmapResource.obtain()方法，将Bitmap对象包装成了Resource<Bitmap>对象。代码如下所示：

```java
public class BitmapResource implements Resource<Bitmap> {
    private final Bitmap bitmap;
    private final BitmapPool bitmapPool;

    /**
     * Returns a new {@link BitmapResource} wrapping the given {@link Bitmap} if the Bitmap is non-null or null if the
     * given Bitmap is null.
     *
     * @param bitmap A Bitmap.
     * @param bitmapPool A non-null {@link BitmapPool}.
     */
    public static BitmapResource obtain(Bitmap bitmap, BitmapPool bitmapPool) {
        if (bitmap == null) {
            return null;
        } else {
            return new BitmapResource(bitmap, bitmapPool);
        }
    }

    public BitmapResource(Bitmap bitmap, BitmapPool bitmapPool) {
       ...
        this.bitmap = bitmap;
        this.bitmapPool = bitmapPool;
    }

    @Override
    public Bitmap get() {
        return bitmap;
    }

    @Override
    public int getSize() {
        return Util.getBitmapByteSize(bitmap);
    }

    @Override
    public void recycle() {
        if (!bitmapPool.put(bitmap)) {
            bitmap.recycle();
        }
    }
}
```
BitmapResource的源码也非常简单，经过这样一层包装之后，如果我还需要获取Bitmap，只需要调用Resource<Bitmap>的get()方法就可以了。


然后我们需要一层层继续向上返回，StreamBitmapDecoder会将值返回到ImageVideoBitmapDecoder当中，而ImageVideoBitmapDecoder又会将值返回到GifBitmapWrapperResourceDecoder的decodeBitmapWrapper()方法当中。由于代码隔得有点太远了，我重新把decodeBitmapWrapper()方法的代码贴一下：
```java
private GifBitmapWrapper decodeBitmapWrapper(ImageVideoWrapper toDecode, int width, int height) throws IOException {
    GifBitmapWrapper result = null;
    // 这个是 刚刚封装的 BitmapResource  
    // 继承关系: public class BitmapResource implements Resource<Bitmap> 
    Resource<Bitmap> bitmapResource = bitmapDecoder.decode(toDecode, width, height);
    if (bitmapResource != null) {
        // 又将Resource<Bitmap>封装到了一个GifBitmapWrapper对象当中。这个GifBitmapWrapper顾名思义，就是既能封装GIF，又能封装Bitmap，从而保证了不管是什么类型的图片Glide都能从容应对
        result = new GifBitmapWrapper(bitmapResource, null);
    }
    return result;
}
```

又将Resource<Bitmap>封装到了一个GifBitmapWrapper对象当中。这个GifBitmapWrapper顾名思义，就是既能封装GIF，又能封装Bitmap，从而保证了不管是什么类型的图片Glide都能从容应对.


```java
public class GifBitmapWrapper {
    private final Resource<GifDrawable> gifResource;// gif
    private final Resource<Bitmap> bitmapResource;// bitmap

    public GifBitmapWrapper(Resource<Bitmap> bitmapResource, Resource<GifDrawable> gifResource) {
        ...
        this.bitmapResource = bitmapResource; 
        this.gifResource = gifResource;
    }

    /**
     * Returns the size of the wrapped resource.
     */
    public int getSize() {
        if (bitmapResource != null) {
            return bitmapResource.getSize();
        } else {
            return gifResource.getSize();
        }
    }

    /**
     * Returns the wrapped {@link Bitmap} resource if it exists, or null.
     */
    public Resource<Bitmap> getBitmapResource() {
        return bitmapResource;
    }

    /**
     * Returns the wrapped {@link GifDrawable} resource if it exists, or null.
     */
    public Resource<GifDrawable> getGifResource() {
        return gifResource;
    }
}
```
就是分别对gifResource和bitmapResource做了一层封装而已，


然后这个GifBitmapWrapper对象会一直向上返回，返回到GifBitmapWrapperResourceDecoder最外层的decode()方法的时候，会对它再做一次封装，如下所示：

```java
@Override
public Resource<GifBitmapWrapper> decode(ImageVideoWrapper source, int width, int height) throws IOException {
    ByteArrayPool pool = ByteArrayPool.get();
    byte[] tempBytes = pool.getBytes();
    GifBitmapWrapper wrapper = null;
    try {
        wrapper = decode(source, width, height, tempBytes);
    } finally {
        pool.releaseBytes(tempBytes);
    }
    // 将GifBitmapWrapper封装到了一个GifBitmapWrapperResource对象当中
    return wrapper != null ? new GifBitmapWrapperResource(wrapper) : null;
}
```

将GifBitmapWrapper封装到了一个GifBitmapWrapperResource对象当中，最终返回的是一个Resource<GifBitmapWrapper>对象。

这个GifBitmapWrapperResource和刚才的BitmapResource是相似的，它们都实现的Resource接口，都可以通过get()方法来获取封装起来的具体内容。GifBitmapWrapperResource的源码如下所示：

```java
public class GifBitmapWrapperResource implements Resource<GifBitmapWrapper> {
    private final GifBitmapWrapper data;

    public GifBitmapWrapperResource(GifBitmapWrapper data) {
        if (data == null) {
            throw new NullPointerException("Data must not be null");
        }
        this.data = data;
    }

    @Override
    public GifBitmapWrapper get() {
        return data;
    }

    @Override
    public int getSize() {
        return data.getSize();
    }

    @Override
    public void recycle() {
        Resource<Bitmap> bitmapResource = data.getBitmapResource();
        if (bitmapResource != null) {
            bitmapResource.recycle();
        }
        Resource<GifDrawable> gifDataResource = data.getGifResource();
        if (gifDataResource != null) {
            gifDataResource.recycle();
        }
    }
}
```

经过这一层的封装之后，我们从网络上得到的图片就能够以Resource接口的形式返回，并且还能同时处理Bitmap图片和GIF图片这两种情况。



那么现在我们可以回到DecodeJob当中了，它的decodeFromSourceData()方法返回的是一个Resource<T>对象，其实也就是Resource<GifBitmapWrapper>对象了。然后继续向上返回，最终返回到decodeFromSource()方法当中，如下所示：
```java
    public Resource<Z> decodeFromSource() throws Exception {
        Resource<T> decoded = decodeSource();
        return transformEncodeAndTranscode(decoded);
    }
```

刚才我们就是从这里跟进到decodeSource()方法当中，然后执行了一大堆一大堆的逻辑，最终得到了这个Resource<T>对象。然而你会发现，decodeFromSource()方法最终返回的却是一个Resource<Z>对象，那么这到底是怎么回事呢？我们就需要跟进到transformEncodeAndTranscode()方法来瞧一瞧了，代码如下所示：

```java
private Resource<Z> transformEncodeAndTranscode(Resource<T> decoded) {
    long startTime = LogTime.getLogTime();
    Resource<T> transformed = transform(decoded);
...
    writeTransformedToCache(transformed);
    startTime = LogTime.getLogTime();
    // transcode()方法，就把Resource<T>对象转换成Resource<Z>对象了。
    Resource<Z> result = transcode(transformed);
    if (Log.isLoggable(TAG, Log.VERBOSE)) {
        logWithTimeAndKey("Transcoded transformed from source", startTime);
    }
    return result;
}

private Resource<Z> transcode(Resource<T> transformed) {
    if (transformed == null) {
        return null;
    }
    // 在第二步load()方法返回的那个DrawableTypeRequest对象，它的构建函数中去构建了一个FixedLoadProvider对象，然后我们将三个参数传入到了FixedLoadProvider当中，其中就有一个GifBitmapWrapperDrawableTranscoder对象。后来在onSizeReady()方法中获取到了这个参数，并传递到了Engine当中，然后又由Engine传递到了DecodeJob当中。因此，这里的transcoder其实就是这个GifBitmapWrapperDrawableTranscoder对象。
    return transcoder.transcode(transformed);
}
```

那么我们来看一下它的源码：
```java
public class GifBitmapWrapperDrawableTranscoder implements ResourceTranscoder<GifBitmapWrapper, GlideDrawable> {
    private final ResourceTranscoder<Bitmap, GlideBitmapDrawable> bitmapDrawableResourceTranscoder;

    public GifBitmapWrapperDrawableTranscoder(
            ResourceTranscoder<Bitmap, GlideBitmapDrawable> bitmapDrawableResourceTranscoder) {
        this.bitmapDrawableResourceTranscoder = bitmapDrawableResourceTranscoder;
    }

    @Override
    public Resource<GlideDrawable> transcode(Resource<GifBitmapWrapper> toTranscode) {
        GifBitmapWrapper gifBitmap = toTranscode.get();
        Resource<Bitmap> bitmapResource = gifBitmap.getBitmapResource();
        final Resource<? extends GlideDrawable> result;

        if (bitmapResource != null) {
            // 而如果Resource<Bitmap>不为空，那么就需要再做一次转码，将Bitmap转换成Drawable对象才行，因为要保证静图和动图的类型一致性，不然逻辑上是不好处理的。
            result = bitmapDrawableResourceTranscoder.transcode(bitmapResource);
        } else {
            // 如果Resource<Bitmap>为空，那么说明此时加载的是GIF图
            result = gifBitmap.getGifResource();
        }
        return (Resource<GlideDrawable>) result;
    }

    ...
}
```
GifBitmapWrapperDrawableTranscoder的核心作用就是用来转码的。

因为GifBitmapWrapper是无法直接显示到ImageView上面的，只有Bitmap或者Drawable才能显示到ImageView上。因此，这里的transcode()方法先从Resource<GifBitmapWrapper>中取出GifBitmapWrapper对象(gifBitmap)，然后再从GifBitmapWrapper中取出Resource<Bitmap>对象(bitmapResource)。

如果Resource<Bitmap>为空，那么说明此时加载的是GIF图，直接调用getGifResource()方法将图片取出即可，因为Glide用于加载GIF图片是使用的GifDrawable这个类，它本身就是一个Drawable对象了

而如果Resource<Bitmap>不为空，那么就需要再做一次转码，将Bitmap转换成Drawable对象才行，因为要保证静图和动图的类型一致性，不然逻辑上是不好处理的。


又进行了一次转码，是调用的GlideBitmapDrawableTranscoder对象的transcode()方法，代码如下所示：
```java
public class GlideBitmapDrawableTranscoder implements ResourceTranscoder<Bitmap, GlideBitmapDrawable> {
    private final Resources resources;
    private final BitmapPool bitmapPool;

    public GlideBitmapDrawableTranscoder(Context context) {
        this(context.getResources(), Glide.get(context).getBitmapPool());
    }

    public GlideBitmapDrawableTranscoder(Resources resources, BitmapPool bitmapPool) {
        this.resources = resources;
        this.bitmapPool = bitmapPool;
    }

    @Override
    public Resource<GlideBitmapDrawable> transcode(Resource<Bitmap> toTranscode) {
        // 这里new出了一个GlideBitmapDrawable对象，并把Bitmap封装到里面。
        GlideBitmapDrawable drawable = new GlideBitmapDrawable(resources, toTranscode.get());
        // 然后对GlideBitmapDrawable再进行一次封装，返回一个Resource<GlideBitmapDrawable>对象。
        return new GlideBitmapDrawableResource(drawable, bitmapPool);

        // public class GlideBitmapDrawableResource extends DrawableResource<GlideBitmapDrawable>
        // public abstract class DrawableResource<T extends Drawable> implements Resource<T>
    }
    ...
}
```
现在再返回到GifBitmapWrapperDrawableTranscoder的transcode()方法中，你会发现它们的类型就一致了。因为不管是静图的Resource<GlideBitmapDrawable>对象，还是动图的Resource<GifDrawable>对象，它们都是属于父类Resource<GlideDrawable>对象的。因此transcode()方法也是直接返回了Resource<GlideDrawable>，而这个Resource<GlideDrawable>其实也就是转换过后的Resource<Z>了。

那么我们继续回到DecodeJob当中，它的decodeFromSource()方法得到了Resource<Z>对象，当然也就是Resource<GlideDrawable>对象。然后继续向上返回会回到EngineRunnable的decodeFromSource()方法，再回到decode()方法，再回到run()方法当中。那么我们重新再贴一下EngineRunnable run()方法的源码：



```java
@Override
public void run() {
    if (isCancelled) {
        return;
    }
    Exception exception = null;
    Resource<?> resource = null;
    try {
        resource = decode();
    } catch (Exception e) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Exception decoding", e);
        }
        exception = e;
    }
    if (isCancelled) {
        if (resource != null) {
            resource.recycle();
        }
        return;
    }
    if (resource == null) {
        onLoadFailed(exception);
    } else {
        onLoadComplete(resource);
    }

```

经过decode()方法的执行，我们最终得到了这个Resource<GlideDrawable>对象，那么接下来就是如何将它显示出来了。可以看到，下面调用了onLoadComplete()方法，表示图片加载已经完成了，代码如下所示
```java
private void onLoadComplete(Resource resource) {
    manager.onResourceReady(resource);
}
```

这个manager就是EngineJob对象，因此这里实际上调用的是EngineJob的onResourceReady()方法，代码如下所示：


```java
class EngineJob implements EngineRunnable.EngineRunnableManager {

    private static final Handler MAIN_THREAD_HANDLER = new Handler(Looper.getMainLooper(), new MainThreadCallback());

    private final List<ResourceCallback> cbs = new ArrayList<ResourceCallback>();
    ...
    public void addCallback(ResourceCallback cb) {
        Util.assertMainThread();
        if (hasResource) {
            cb.onResourceReady(engineResource);
        } else if (hasException) {
            cb.onException(exception);
        } else {
            cbs.add(cb);
        }
    }

    @Override
    public void onResourceReady(final Resource<?> resource) {
        this.resource = resource;
        // 这里在onResourceReady()方法使用Handler发出了一条MSG_COMPLETE消息，那么在MainThreadCallback的handleMessage()方法中就会收到这条消息。

        // 这个  MAIN_THREAD_HANDLER  主线程的 Handler
        // 发出后, 由  MainThreadCallback  处理
        MAIN_THREAD_HANDLER.obtainMessage(MSG_COMPLETE, this).sendToTarget();
    }

    private void handleResultOnMainThread() {
        if (isCancelled) {
            resource.recycle();
            return;
        } else if (cbs.isEmpty()) {
            throw new IllegalStateException("Received a resource without any callbacks to notify");
        }
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

    @Override
    public void onException(final Exception e) {
        this.exception = e;
        MAIN_THREAD_HANDLER.obtainMessage(MSG_EXCEPTION, this).sendToTarget();
    }

    private void handleExceptionOnMainThread() {
        if (isCancelled) {
            return;
        } else if (cbs.isEmpty()) {
            throw new IllegalStateException("Received an exception without any callbacks to notify");
        }
        hasException = true;
        listener.onEngineJobComplete(key, null);
        for (ResourceCallback cb : cbs) {
            if (!isInIgnoredCallbacks(cb)) {
                cb.onException(exception);
            }
        }
    }

    private static class MainThreadCallback implements Handler.Callback {

        @Override
        public boolean handleMessage(Message message) {
            if (MSG_COMPLETE == message.what || MSG_EXCEPTION == message.what) {
                EngineJob job = (EngineJob) message.obj;
                if (MSG_COMPLETE == message.what) {
                    job.handleResultOnMainThread();
                } else {
                    job.handleExceptionOnMainThread();
                }
                return true;
            }
            return false;
        }
    }

    ...
}
```

job.handleResultOnMainThread调用 EngineJob 的 方法
```java
// EngineJob.java
    private void handleResultOnMainThread() {
        if (isCancelled) {
            resource.recycle();
            return;
        } else if (cbs.isEmpty()) {
            throw new IllegalStateException("Received a resource without any callbacks to notify");
        }
        engineResource = engineResourceFactory.build(resource, isCacheable);
        hasResource = true;

        // Hold on to resource for duration of request so we don't recycle it in the middle of notifying if it
        // synchronously released by one of the callbacks.
        engineResource.acquire();
        listener.onEngineJobComplete(key, engineResource);

    // 调用了所有ResourceCallback的onResourceReady()方法
        for (ResourceCallback cb : cbs) {
            if (!isInIgnoredCallbacks(cb)) {
                engineResource.acquire();
                cb.onResourceReady(engineResource);
            }
        }
        // Our request is complete, so we can release the resource.
        engineResource.release();
    }
```

那么这个ResourceCallback是什么呢？答案在addCallback()方法当中，它会向cbs集合中去添加ResourceCallback。

```java
public interface ResourceCallback {

    /**
     * Called when a resource is successfully loaded.
     */
    void onResourceReady(Resource<?> resource);

    /**
     * Called when a resource fails to load successfully.
     *
     */
    void onException(Exception e);
}
```

那么这个addCallback()方法又是哪里调用的呢？其实调用的地方我们早就已经看过了，只不过之前没有注意，现在重新来看一下Engine的load()方法，如下所示：


```java
public <T, Z, R> LoadStatus load(Key signature, int width, int height, DataFetcher<T> fetcher,
            DataLoadProvider<T, Z> loadProvider, Transformation<Z> transformation, ResourceTranscoder<Z, R> transcoder, Priority priority, 
            boolean isMemoryCacheable, DiskCacheStrategy diskCacheStrategy, ResourceCallback cb) { //那这个 cb 参数是哪里传入的??
        ...
        EngineJob engineJob = engineJobFactory.build(key, isMemoryCacheable);
        DecodeJob<T, Z, R> decodeJob = new DecodeJob<T, Z, R>(key, width, height, fetcher, loadProvider, transformation,
                transcoder, diskCacheProvider, diskCacheStrategy, priority);
        EngineRunnable runnable = new EngineRunnable(engineJob, decodeJob, priority);
        jobs.put(key, engineJob);
        // 这里!!!  那这个 cb 参数是哪里传入的??  
        engineJob.addCallback(cb);
        engineJob.start(runnable);

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            logWithTimeAndKey("Started new load", startTime, key);
        }
        return new LoadStatus(cb, engineJob);
    }
```


在onSizeReady()方法中调用load()方法时传入的最后一个参数是什么？代码如下所示：
```java
public final class GenericRequest<A, T, Z, R> implements Request, SizeReadyCallback,
        ResourceCallback {

    ...

    @Override
    public void onSizeReady(int width, int height) {
        ...
        status = Status.RUNNING;
        width = Math.round(sizeMultiplier * width);
        height = Math.round(sizeMultiplier * height);
        ModelLoader<A, T> modelLoader = loadProvider.getModelLoader();
        final DataFetcher<T> dataFetcher = modelLoader.getResourceFetcher(model, width, height);
        if (dataFetcher == null) {
            onException(new Exception("Failed to load model: \'" + model + "\'"));
            return;
        }
        ResourceTranscoder<Z, R> transcoder = loadProvider.getTranscoder();
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            logV("finished setup for calling load in " + LogTime.getElapsedMillis(startTime));
        }
        loadedFromMemoryCache = true;
        loadStatus = engine.load(signature, width, height, dataFetcher, loadProvider, transformation, 
                transcoder, priority, isMemoryCacheable, diskCacheStrategy, this); // 这里, 传入了 GenericRequest 自己, 它实现了ResourceCallback接口
        loadedFromMemoryCache = resource != null;
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            logV("finished onSizeReady in " + LogTime.getElapsedMillis(startTime));
        }
    }
    ...
}
```

因此EngineJob的回调最终其实就是回调到了GenericRequest的onResourceReady()方法当中了，代码如下所示：
```java
public void onResourceReady(Resource<?> resource) {
    ...
    // 调用resource.get()方法获取到了封装的图片对象，也就是GlideBitmapDrawable对象，或者是GifDrawable对象
    Object received = resource.get();
    ...
    // 调用下面的重载方法
    onResourceReady(resource, (R) received);
}

private void onResourceReady(Resource<?> resource, R result) {
    // We must call isFirstReadyResource before setting status.
    boolean isFirstResource = isFirstReadyResource();
    status = Status.COMPLETE;
    this.resource = resource;
    if (requestListener == null || !requestListener.onResourceReady(result, model, target, loadedFromMemoryCache,
            isFirstResource)) {
        GlideAnimation<R> animation = animationFactory.build(loadedFromMemoryCache, isFirstResource);
        // 这里!!!
        target.onResourceReady(result, animation);
    }
    notifyLoadSuccess();
}
```

那么这个target又是什么呢？这个又需要向上翻很久了，在第三步into()方法的一开始，我们就分析了在into()方法的最后一行，调用了glide.buildImageViewTarget()方法来构建出一个Target，而这个Target就是一个GlideDrawableImageViewTarget对象。


试试找一下:

Glide.with  返回的 RequestManager

RequestManager.load  返回的 是 DrawableTypeRequest 对象.  load 就是赋值, load 之后的 placeholder , errorholder 也都是赋值, 给GenericRequestBuilder里面赋值

然后调用 DrawableTypeRequest  的 into. 进入 GenericRequestBuilder  的 into
```java
// GenericRequestBuilder.java
public Target<TranscodeType> into(ImageView view) {
       ...
        // glide.buildImageViewTarget 是个 GlideDrawableImageViewTarget对象
        return into(glide.buildImageViewTarget(view, transcodeClass));
    }
```

```java
// GenericRequestBuilder.java
public <Y extends Target<TranscodeType>> Y into(Y target) {
        Util.assertMainThread();

        // 把 target 传入 buildRequest
        Request request = buildRequest(target);
        requestTracker.runRequest(request);

        return target;
    }
```

```java
// GenericRequestBuilder.java
private Request buildRequest(Target<TranscodeType> target) {
        if (priority == null) {
            priority = Priority.NORMAL;
        }
        // 传入 target
        return buildRequestRecursive(target, null);
    }
```

```java
private Request buildRequestRecursive(Target<TranscodeType> target, ThumbnailRequestCoordinator parentCoordinator) {
        return obtainRequest(target, sizeMultiplier, priority, parentCoordinator);
}
```

调用obtainRequest, 把 target 传入.

GenericRequest.obtain里面构建 GenericRequest 对象, 并调用 init 方法, 把 target 传入对它进行赋值了.

```java
private Request obtainRequest(Target<TranscodeType> target, float sizeMultiplier, Priority priority,
            RequestCoordinator requestCoordinator) {
        return GenericRequest.obtain(
                loadProvider,
                model,
                signature,
                context,
                priority,
                target,
                sizeMultiplier,
                placeholderDrawable,
                placeholderId,
                errorPlaceholder,
                errorId,
                fallbackDrawable,
                fallbackResource,
                requestListener,
                requestCoordinator,
                glide.getEngine(),
                transformation,
                transcodeClass,
                isCacheable,
                animationFactory,
                overrideWidth,
                overrideHeight,
                diskCacheStrategy);
    }
```




继续说 GenericRequest 的 onResourceReady  , 调用了 GlideDrawableImageViewTarget    的 onResourceReady
```java
public class GlideDrawableImageViewTarget extends ImageViewTarget<GlideDrawable> {
    private static final float SQUARE_RATIO_MARGIN = 0.05f;
    private int maxLoopCount;
    private GlideDrawable resource;

    public GlideDrawableImageViewTarget(ImageView view) {
        this(view, GlideDrawable.LOOP_FOREVER);
    }

    public GlideDrawableImageViewTarget(ImageView view, int maxLoopCount) {
        super(view);
        this.maxLoopCount = maxLoopCount;
    }

    @Override
    public void onResourceReady(GlideDrawable resource, GlideAnimation<? super GlideDrawable> animation) {
...
// 父类的 onResourceReady
        super.onResourceReady(resource, animation);
        this.resource = resource;
        resource.setLoopCount(maxLoopCount);
        resource.start();
    }

    @Override
    protected void setResource(GlideDrawable resource) {
        view.setImageDrawable(resource);
    }

    @Override
    public void onStart() {
        if (resource != null) {
            resource.start();
        }
    }

    @Override
    public void onStop() {
        if (resource != null) {
            resource.stop();
        }
    }
}
```

调用了super.onResourceReady()方法，GlideDrawableImageViewTarget的父类是ImageViewTarget，我们来看下它的代码吧：

```java
public abstract class ImageViewTarget<Z> extends ViewTarget<ImageView, Z> implements GlideAnimation.ViewAdapter {

    ...

    @Override
    public void onResourceReady(Z resource, GlideAnimation<? super Z> glideAnimation) {
        if (glideAnimation == null || !glideAnimation.animate(resource, this)) {
            setResource(resource);
        }
    }

    protected abstract void setResource(Z resource);

}
```
可以看到，在ImageViewTarget的onResourceReady()方法当中调用了setResource()方法，而ImageViewTarget的setResource()方法是一个抽象方法，具体的实现还是在子类那边实现的。

那子类的setResource()方法是怎么实现的呢？回头再来看一下GlideDrawableImageViewTarget的setResource()方法，没错，调用的view.setImageDrawable()方法，而这个view就是ImageView。代码执行到这里，图片终于也就显示出来了。

那么，我们对Glide执行流程的源码分析，到这里也终于结束了。



# 缓存
缓存 

内存缓存

EngineKey 

1. LruCache

2. 弱引用

硬盘缓存

