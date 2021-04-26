---
title: ARouter分析

date: 2018-10-16

categories: 
   -  组件化
   -  ARouter

tags: 
   -  组件化
   -  ARouter

description: 
---

<!-- TOC -->

- [APT 注解处理器做了什么](#apt-注解处理器做了什么)
    - [自定义注解](#自定义注解)
    - [APT 注解处理器做了什么](#apt-注解处理器做了什么-1)
- [ARouter.init()](#arouterinit)
- [navigation()](#navigation)

<!-- /TOC -->



只分析 Route 关于Activity 跳转的部分, 理解了就可以了.

# APT 注解处理器做了什么

## 自定义注解

在 module: arouter-annotation 中定义了注解: Route
```java
package com.alibaba.android.arouter.facade.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark a page can be route by router.
 *
 * @author Alex <a href="mailto:zhilong.liu@aliyun.com">Contact me.</a>
 * @version 1.0
 * @since 16/8/15 下午9:29
 */
// 应用在类上
@Target({ElementType.TYPE})
// 持续到编译器(字节码阶段)
@Retention(RetentionPolicy.CLASS)
public @interface Route {

    /**
     * Path of route
     */
    String path();

    /**
     * Used to merger routes, the group name MUST BE USE THE COMMON WORDS !!!
     */
    String group() default "";

    /**
     * Name of route, used to generate javadoc.
     */
    String name() default "";

    /**
     * Extra data, can be set by user.
     * Ps. U should use the integer num sign the switch, by bits. 10001010101010
     */
    int extras() default Integer.MIN_VALUE;

    /**
     * The priority of route.
     */
    int priority() default -1;
}

```

我们使用的时候, 就在一个 Activity 上面写上
```java
@Route(path = "/home/HomeMainActivity")
public class HomeMainActivity extends AppCompatActivity {}
```


## APT 注解处理器做了什么

在module:arouter-compiler 中, 有 RouteProcessor 和 BaseProcessor

二者的关系是: `RouteProcessor extends BaseProcessor`


在 BaseProcessor  中做的事情很简单, 主要是 init方法中做了一些准备工作
```java
@Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        // 拿到几个工具类
        mFiler = processingEnv.getFiler();
        types = processingEnv.getTypeUtils();
        elementUtils = processingEnv.getElementUtils();
        typeUtils = new TypeUtils(types, elementUtils);
        logger = new Logger(processingEnv.getMessager());

        // Attempt to get user configuration [moduleName]
        // 获取 options.  这个 options 的设置是在 一个 module 的 build.gradle 中
        // 一般都是 module 的名字
        /*******************************************
        android {
                ...
                defaultConfig {
                    ...
                    javaCompileOptions {
                        annotationProcessorOptions {
                            arguments = [AROUTER_MODULE_NAME: project.getName()]
                        }
                    }
                }
        }
        ***********************************************/

        Map<String, String> options = processingEnv.getOptions();
        if (MapUtils.isNotEmpty(options)) {
            // 这里拿到  build.gradle 中设置的 AROUTER_MODULE_NAME
            moduleName = options.get(KEY_MODULE_NAME);
            generateDoc = VALUE_ENABLE.equals(options.get(KEY_GENERATE_DOC_NAME));
        }

        if (StringUtils.isNotEmpty(moduleName)) {
            // 这里对 build.gradle 中的 moduleName 处理了一下.
            moduleName = moduleName.replaceAll("[^0-9a-zA-Z_]+", "");

            logger.info("The user has configuration the module name, it was [" + moduleName + "]");
        } else {
            // 如果在 build.gradle 中没有设置 AROUTER_MODULE_NAME 就报错
            logger.error(NO_MODULE_NAME_TIPS);
            throw new RuntimeException("ARouter::Compiler >>> No module name, for more information, look at gradle log.");
        }
    }
```

下面就是 RouteProcessor 了. 直接看 process 方法.
```java
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // 首先判断`CollectionUtils.isNotEmpty(annotations)`. 我之前自己写都没判断过, 不知道是判断啥呢. 下次自己写也要注意写上这里
        if (CollectionUtils.isNotEmpty(annotations)) {

            // 拿到一个 module 下的所有被 @Route 注解过的 元素.
            Set<? extends Element> routeElements = roundEnv.getElementsAnnotatedWith(Route.class);
            try {
                logger.info(">>> Found routes, start... <<<");

                //  解析
                this.parseRoutes(routeElements);

            } catch (Exception e) {
                logger.error(e);
            }
            return true;
        }

        return false;
    }
```

注意啊: process方法要进入多次的.   编译每个 module 都要调用一次 process 方法, 方法中`roundEnv.getElementsAnnotatedWith(Route.class)`拿到的是:该 module 中, 所有使用@Route 注解过的元素.

然后我们创建新的类, 注意不要重名啊. 重名会报错.

比如, moduleA 创建一个 ARouter$$XX 这个类.
moduleB 也创建 ARouter$$XX的话 , 就会报错.

关于 APT 还想说一点, 允许我们在编译器创建新的类, 但是不允许修改已有的类. 修改已有的类是字节码插桩技术了, 不是 APT


继续 RouteProcessor 的 process 方法. 拿到一个 module 下的所有被 @Route 注解过的 元素, 就调用 `this.parseRoutes(routeElements);`去解析


parseRoutes 方法一大堆, 解析了一大堆, 反正, 最后就创建了几个主要的类.

1. 在 com.alibaba.android.arouter.routes  包下, 创建 ARouter$$Group$$groupName 类.  groupName就是 path 中写的第一级.
2. 在 com.alibaba.android.arouter.routes  包下, 创建 ARouter$$Root$$moduleName 类
3. 在 com.alibaba.android.arouter.routes  包下, 创建 ARouter$$Provider$$moduleName 类

这几个类分别长什么样子?

我的 groupName 是 home.

我的 moduleName 是 module_home

```java
public class ARouter$$Group$$home implements IRouteGroup {
    public ARouter$$Group$$home() {
    }

    public void loadInto(Map<String, RouteMeta> atlas) {
        // 这里是把 path-RouteMeta 的映射关系加入到一个 Map 中
        atlas.put("/home/HomeMainActivity", RouteMeta.build(RouteType.ACTIVITY, HomeMainActivity.class, "/home/homemainactivity", "home", (Map)null, -1, -2147483648));
    }
}
```

RouteMeta就是对目的地址的封装. 其实我们就是通过 path 找到是哪个类.看看 RouteMeta 是咋个封装的
```java
public class RouteMeta {
    private RouteType type;         // Type of route
    private Element rawType;        // Raw type of route
    private Class<?> destination;   // Destination
    private String path;            // Path of route
    private String group;           // Group of route
    private int priority = -1;      // The smaller the number, the higher the priority
    private int extra;              // Extra data
    private Map<String, Integer> paramsType;  // Param type
    private String name;
```

可以看到, 该有的都有了.  destination 表示类的 Class 对象. 

Element 是可以代表任何元素的, 包括类(TypeElement) / 属性 / 包 等等

RouteMeta 中还封装了目的的 group 和 path.

可以简单的理解, RouteMeta就是对我们添加了@Route 的类的包装, 通过这个 RouteMeta 就能找到那个目的类.

在 方法 `loadInto` 中, 传了一个 map 进来, 也不知道哪传来的, 反正是把这个映射关系丢在了这个 map 中, 后续, 我们 通过这个 map, 应该可以根据 path拿到我们想找的那个类吧.



```java
public class ARouter$$Root$$module_home implements IRouteRoot {
    public ARouter$$Root$$module_home() {
    }

    public void loadInto(Map<String, Class<? extends IRouteGroup>> routes) {
        // 这里是把 groupName 和 组加载器 的映射关系放在 map 中
        // 组加载器, 就是上面的 ARouter$$Group$$home 类
        routes.put("home", home.class);
    }
}
```


```java
// 这个还没看, 暂时没用到
public class ARouter$$Providers$$module_home implements IProviderGroup {
    public ARouter$$Providers$$module_home() {
    }

    public void loadInto(Map<String, RouteMeta> providers) {
    }
}
```

总结:
1. 扫描所有被 Route 注解的类. 根据组名, 生成不同的组.  生成的文件全部放在 com.alibaba.android.arouter.routes 这个包名下

2. Group  中把 Path-RouteMeta的映射关系放入一个 map

3. Root  中 把  GroupName-组加载器(也就是 ARouter$$Group$$moduleName类 ) 的 映射关系放入一个 map

上面的例子,  module 中只有一个 group, 如果有多个 group, `ARouter$$Root$$module_home.loadInfo` 方法 中应该put多个. 


也就是在正常的逻辑: path -> RouteMeta的映射关系之上, 又添加了一个映射: groupName -> class 的.

感觉, 先检索是属于哪个 group, 然后找到这个 group 对应的 class, 然后再执行 对应的 loadInfo ?? 这样???

到这里, APT 注解处理器就好了. 反正就是生成了几个类.

# ARouter.init()

```java
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 尽可能早，推荐在Application中初始化
        ARouter.init(this);
```

```java
// ARouter.java
    public static void init(Application application) {
        if (!hasInit) {
            logger = _ARouter.logger;
            _ARouter.logger.info(Consts.TAG, "ARouter init start.");
            // 这里调用了 _ARouter.init(application);
            hasInit = _ARouter.init(application);

            if (hasInit) {
                _ARouter.afterInit();
            }

            _ARouter.logger.info(Consts.TAG, "ARouter init over.");
        }
    }

```


```java
// _ARouter.java
    protected static synchronized boolean init(Application application) {
        mContext = application;
        // 这里
        LogisticsCenter.init(mContext, executor);
        logger.info(Consts.TAG, "ARouter init success!");
        hasInit = true;

        // 创建了主线程的 Handler, 后面要用
        mHandler = new Handler(Looper.getMainLooper());

        // It's not a good idea.
        // if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
        //     application.registerActivityLifecycleCallbacks(new AutowiredLifecycleCallback());
        // }
        return true;
    }
```

```java
    public synchronized static void init(Context context, ThreadPoolExecutor tpe) throws HandlerException {
        ...
        try {
            ...
            if (registerByPlugin) {
                logger.info(TAG, "Load router map by arouter-auto-register plugin.");
            } else {
                Set<String> routerMap;

                // It will rebuild router map every times when debuggable.
                // 第一次走这里
                if (ARouter.debuggable() || PackageUtils.isNewVersion(context)) {
                    logger.info(TAG, "Run with debug mode or new install, rebuild router map.");
                    // These class was generated by arouter-compiler.
                    // 检索com.alibaba.android.arouter.routes包下的所有的 class的名字(全限定名,即包名+类名), 放在routerMap中
                    // 注意, com.alibaba.android.arouter.routes 这个包下就是 APT 生成的几个class 文件. 包括:ARouter$$Root$$xx  ARouter$$Root$$xx  ARouter$$Provider$$xx 
                    routerMap = ClassUtils.getFileNameByPackageName(mContext, ROUTE_ROOT_PAKCAGE);
                    if (!routerMap.isEmpty()) {
                        // 如果不为空, 就把这些文件名存缓存在 sp 中. 
                        context.getSharedPreferences(AROUTER_SP_CACHE_KEY, Context.MODE_PRIVATE).edit().putStringSet(AROUTER_SP_KEY_MAP, routerMap).apply();
                    }

                    PackageUtils.updateVersion(context);    // Save new version name when router map update finishes.
                } else {
                    // 如果不是第一次, 就从 sp 中取
                    logger.info(TAG, "Load router map from cache.");
                    routerMap = new HashSet<>(context.getSharedPreferences(AROUTER_SP_CACHE_KEY, Context.MODE_PRIVATE).getStringSet(AROUTER_SP_KEY_MAP, new HashSet<String>()));
                }

                ...

                // 遍历这些文件名字
                for (String className : routerMap) {
                    // // 如果是 root 的
                    if (className.startsWith(ROUTE_ROOT_PAKCAGE + DOT + SDK_NAME + SEPARATOR + SUFFIX_ROOT)) {
                        // This one of root elements, load root.
                        // 如果是 root 的. 就通过反射创建一个对象, 然后调用loadInto方法.
                        // 以上面的  ARouter$$Root$$module_home为例,  想想结果: 这里就是创建了一个ARouter$$Root$$module_home对象, 然后调用它的 loadInto 方法, 传入的 map 是 Warehouse类中的 static 的 groupsIndex.
                        // 那, 在 loadInto 方法中, 就把 "home"->home.class 这个映射放入了Warehouse.groupsIndex  Map 中
                        ((IRouteRoot) (Class.forName(className).getConstructor().newInstance())).loadInto(Warehouse.groupsIndex);
                    } else if (className.startsWith(ROUTE_ROOT_PAKCAGE + DOT + SDK_NAME + SEPARATOR + SUFFIX_INTERCEPTORS)) { // 如果是 拦截器
                        // Load interceptorMeta
                        ((IInterceptorGroup) (Class.forName(className).getConstructor().newInstance())).loadInto(Warehouse.interceptorsIndex);
                    } else if (className.startsWith(ROUTE_ROOT_PAKCAGE + DOT + SDK_NAME + SEPARATOR + SUFFIX_PROVIDERS)) {//如果是 Provider.
                        // Load providerIndex
                        ((IProviderGroup) (Class.forName(className).getConstructor().newInstance())).loadInto(Warehouse.providersIndex);
                    }
                }
            }
            ...
        } catch (Exception e) {
            throw new HandlerException(TAG + "ARouter init logistics center exception! [" + e.getMessage() + "]");
        }
    }
```

总结, init 方法主要检索 com.alibaba.android.arouter.routes 包下的 ARouter$$Root$$XX 类, 反射创建对象, 并执行了 loadInto 方法.

loadInto 方法中, 把哪个 group 对应哪个组加载器全部放入了 Warehouse.groupsIndex 里面维护.

那, ARouter$$Group$$XX 类呢, 不需要维护一下下???

继续往后看吧, 反正这里是 init 完了..

# navigation()

然后我们跳转的时候, 执行下面的方法
```java
                ARouter.getInstance()
                        .build("/cart/cartActivity")
                        .withString("key1", "value1")//携带参数1
                        .withString("key2", "value2")//携带参数2
                        .navigation();
```

要先说下这个 build
```java
/**
     * Build the roadmap, draw a postcard.
     *
     * @param path Where you go.
     */
    public Postcard build(String path) {
        return _ARouter.getInstance().build(path);
    }
```

就是根据 path, 创建了一个 PostCard, 那 PostCard 又是啥? 明信片
```java
/**
 * A container that contains the roadmap.
 */
public final class Postcard extends RouteMeta {
     // Base
    private Uri uri;
    private Object tag;             // A tag prepare for some thing wrong.
    private Bundle mBundle;         // Data to transform
    private int flags = -1;         // Flags of route
    private int timeout = 300;      // Navigation timeout, TimeUnit.Second
    private IProvider provider;     // It will be set value, if this postcard was provider.
    private boolean greenChannel;
    private SerializationService serializationService;

    // Animation
    private Bundle optionsCompat;    // The transition animation of activity
    private int enterAnim = -1;
    private int exitAnim = -1;
    ...

    public Postcard(String path, String group, Uri uri, Bundle bundle) {
        setPath(path);
        setGroup(group);
        setUri(uri);
        this.mBundle = (null == bundle ? new Bundle() : bundle);
    }
}
```

可以看到 Postcard 是 RouteMeta 的子类. 

也就是说, 这个 Postcard  也是对目的地址的封装. 但是, Postcard不单知道目的类的 group , path 这些属性, 还可以携带参数(Bundle), 还知道转场动画, 就是比 RouteMeta的功能稍微多点.

有了 PostCard, 就要开始 跳转了.



navigation 最终执行到了:
```java
// _ARouter.java
  protected Object navigation(final Context context, final Postcard postcard, final int requestCode, final NavigationCallback callback) {
        try {
            // 这里, 是实现按需加载的关键
            LogisticsCenter.completion(postcard);
        } catch (NoRouteFoundException ex) {
           ...
            return null;
        }

        if (null != callback) {
            callback.onFound(postcard);
        }

        if (!postcard.isGreenChannel()) {   // It must be run in async thread, maybe interceptor cost too mush time made ANR.
            interceptorService.doInterceptions(postcard, new InterceptorCallback() {
                /**
                 * Continue process
                 *
                 * @param postcard route meta
                 */
                @Override
                public void onContinue(Postcard postcard) {
                    _navigation(context, postcard, requestCode, callback);
                }

                /**
                 * Interrupt process, pipeline will be destory when this method called.
                 *
                 * @param exception Reson of interrupt.
                 */
                @Override
                public void onInterrupt(Throwable exception) {
                    if (null != callback) {
                        callback.onInterrupt(postcard);
                    }

                    logger.info(Consts.TAG, "Navigation failed, termination by interceptor : " + exception.getMessage());
                }
            });
        } else {
            return _navigation(context, postcard, requestCode, callback);
        }

        return null;
    }
```


在 ` LogisticsCenter.completion(postcard); ` 方法中, 实现了按需加载.

前面我不是一直疑惑, 那个  ARouter$$Group$$XX 类 咋一直没用呢, 就在这里用了.

```java
// 参数 postcard 就是对我们刚才传入的 path 的封装, 别忘记了
   public synchronized static void completion(Postcard postcard) {
        if (null == postcard) {
            throw new NoRouteFoundException(TAG + "No postcard!");
        }
        // Warehouse.routes 对象从一开始创建出来一直是空的, 我们好像从往里面 put 东西
        // 其实, 我们需要的 path -> RouteMeta 映射关系 就是存在这个 hashMap 中的.
        RouteMeta routeMeta = Warehouse.routes.get(postcard.getPath());
        // 刚开始肯定 null, 走 if
        if (null == routeMeta) {    // Maybe its does't exist, or didn't load.
        // 先根据 我们传入的 group , 从Warehouse.groupsIndex中拿到组加载器.
        // 别忘记了啊, 我们在 init 的时候, 是把 group->组加载器 class 的映射关系放在这个Warehouse.groupsIndex Map 中了
            Class<? extends IRouteGroup> groupMeta = Warehouse.groupsIndex.get(postcard.getGroup());  // Load route meta.
            if (null == groupMeta) {// 没组加载器就出错了
                throw new NoRouteFoundException(TAG + "There is no route match the path [" + postcard.getPath() + "], in group [" + postcard.getGroup() + "]");
            } else {
                // Load route and cache it into memory, then delete from metas.
                try {
                    ...
                    // 反射创建组加载器的对象
                    IRouteGroup iGroupInstance = groupMeta.getConstructor().newInstance();
                    // 调用组加载器的loadInto方法
                    iGroupInstance.loadInto(Warehouse.routes);

                    // 以上面的 ARouter$$Group$$home 为例, 看看执行完上面这两行, 结果怎样
                    // 创建 ARouter$$Group$$home 对象, 然后调用它的 loadInto 方法, 传入了Warehouse.routes
                    // 在 ARouter$$Group$$home.loadInto 中, 把 path("/home/HomeMainActivity") 与 RouteMeta 的映射关系放入了Warehouse.routes里面.
                    // 那后续再跳转的话, RouteMeta routeMeta = Warehouse.routes.get(postcard.getPath());就不为空, 直接就跳转了.


                    Warehouse.groupsIndex.remove(postcard.getGroup());

                    ...
                } catch (Exception e) {
                    ...
                }

                completion(postcard);   // Reload
            }
        } else { //
        }
```

到这里我们就明白了;

1. 最开始, Warehouse.routes 是空的

2. 在 init 的时候, 只是把 group->组加载器class 的映射关系放入Warehouse.groupsIndex中. 并没有把全部的 path->RouteMeta的映射加载到 Warehouse.routes.

3. 当真正跳转的时候, 会先去 Warehouse.routes  检索, 如果没有,  就会创建 group 对应的组加载器, 把这个组里的 path->RouteMeta 映射全部加载到Warehouse.routes.


这段代码就是“按需加载”的核心逻辑所在了，我对其进行了简化，分析其逻辑是这样的：

1. 首先从Warehouse.routes(前面说了，这里存放的是path到目标的映射)里拿到目标信息，如果找不到，说明这个信息还没加载，需要加载，实际上，刚开始这个routes里面什么都没有。
2. 加载流程：首先从Warehouse.groupsIndex里获取“组加载器”，组加载器是一个类，需要通过反射将其实例化，实例化为iGroupInstance，接着调用组加载器的加载方法loadInto，将该组的路由映射关系加载到Warehouse.routes中，加载完成后，routes中就缓存下来当前组的所有路由映射了，因此这个组加载器其实就没用了，为了节省内存，将其从Warehouse.groupsIndex移除。
3. 如果之前加载过，则在Warehouse.routes里面是可以找到路有映射关系的，因此直接将目标信息routeMeta传递给postcard，保存在postcard中，这样postcard就知道了最终要去哪个组件了。



> 对于一个大型项目，组件数量会很多，可能会有一两百或者更多，把这么多组件都放到这个Map里，显然会对内存造成很大的压力，因此，Arouter作为一款阿里出品的优秀框架，显然是要解决这个问题的。
Arouter采用的方法就是“分组+按需加载”，分组还带来的另一个好处是便于管理，加快检索速度。


现在加载完成了, 但是我要跳转呀.. 想要 startActivity 呢...

我们继续, 代码再贴一下
```java
// _ARouter.java
  protected Object navigation(final Context context, final Postcard postcard, final int requestCode, final NavigationCallback callback) {
        try {
            // 这里, 是实现按需加载的关键
            LogisticsCenter.completion(postcard);

            // 执行到这里了
        } catch (NoRouteFoundException ex) {
           ...
            return null;
        }

        if (null != callback) {
            callback.onFound(postcard);
        }

        // 这个应该是判断拦截器的吧.
        if (!postcard.isGreenChannel()) {   // It must be run in async thread, maybe interceptor cost too mush time made ANR.
        // 如果有拦截器, 就做拦截器的操作, 注意这里的注释, 拦截器里的操作可能耗时, 因此是在子线程的.
            interceptorService.doInterceptions(postcard, new InterceptorCallback() {
                /**
                 * Continue process
                 *
                 * @param postcard route meta
                 */
                @Override
                public void onContinue(Postcard postcard) {
                    // 拦截器执行完了, 调用_navigation() 跳转
                    _navigation(context, postcard, requestCode, callback);
                }

                /**
                 * Interrupt process, pipeline will be destory when this method called.
                 *
                 * @param exception Reson of interrupt.
                 */
                @Override
                public void onInterrupt(Throwable exception) {
                    if (null != callback) {
                        callback.onInterrupt(postcard);
                    }

                    logger.info(Consts.TAG, "Navigation failed, termination by interceptor : " + exception.getMessage());
                }
            });
        } else {
            // 没有拦截器的任务, 直接跳转
            return _navigation(context, postcard, requestCode, callback);
        }

        return null;
    }
```

可以看到, 判断是否需要拦截器做一些处理, 如果需要, 就先让拦截器处理, 然后在 `onContinue` 回调中 进行跳转

如果不需要拦截器, 直接跳转

最终都是 进入 `_navigation(context, postcard, requestCode, callback);` 方法




```java
    private Object _navigation(final Context context, final Postcard postcard, final int requestCode, final NavigationCallback callback) {
        final Context currentContext = null == context ? mContext : context;

        switch (postcard.getType()) {
            // 我们就看这个
            case ACTIVITY:
                // Build intent
                final Intent intent = new Intent(currentContext, postcard.getDestination());
                intent.putExtras(postcard.getExtras());

                // Set flags.
                int flags = postcard.getFlags();
                if (-1 != flags) {
                    intent.setFlags(flags);
                } else if (!(currentContext instanceof Activity)) {    // Non activity, need less one flag.
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }

                // Set Actions
                String action = postcard.getAction();
                if (!TextUtils.isEmpty(action)) {
                    intent.setAction(action);
                }

                // Navigation in main looper.
                // 判断是否是 UI 线程, 不是的话, 就用前面创建好的 主线程的 Handler 发消息
                if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            startActivity(requestCode, currentContext, intent, postcard, callback);
                        }
                    });
                } else {
                    // 如果是 UI 线程, 直接 startActivity .
                    startActivity(requestCode, currentContext, intent, postcard, callback);
                }

                break;
          
            default:
                return null;
        }

        return null;
    }

```

结束.

