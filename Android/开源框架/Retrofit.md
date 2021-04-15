---

title: Retrofit

date: 2019-05-30

categories: 
   - Android开源框架

tags: 
   - Android开源框架 


description: 
​
---

# 动态代理

动态代理参考:
![](https://camo.githubusercontent.com/e2500a382802725ac869c79f8782f6c3646ccd3b52ecc03ef1470d61c54cd2e3/68747470733a2f2f63646e2e6a7364656c6976722e6e65742f67682f66616e7368616e686f6e672f6e6f74652d696d6167652f4a444b2532302545352538412541382545362538302538312545342542422541332545372539302538362e706e67)





# 基本使用


Retrofit 将我们的 HTTP API 转换成一个 接口形式。所以我们第一步定义一个 interface
```java
public interface GitHubService {
    @GET("user/{user}/repos")
    Call<List<Integer>> listRepos(@Path("user") String user);
}
```
然后构建一个 Retrofit，通过 create 方法生成 GitHubService 的一个实现。
```java
Retrofit retrofit = new Retrofit.Builder()
    .baseUrl("https://api.github.com/")
    .build();

GitHubService service = retrofit.create(GitHubService.class);

```

调用 listRepos 拿到 Call 实例，可以做同步或异步请求。
```java
 Call<List<Integer>> repos = service.listRepos("octocat");
```
每个 Call 实例只能使用一次，但调用 clone() 将创建一个可以使用的新实例。 这是因为 OkHttp 中每个 Call 只能执行一次. 多次执行会报错


# 构建

```java

// Retrofit.java
  // 一个线程安全的、支持高效并发的HashMap，Key 是 Method，Value 是 ServiceMethod。Method 我们能猜到应该就是上面接口中定义的 listRepos，而这个方法中有很多注解，@GET、@Path 啥的，那这个 ServiceMethod 很有可能是这个方法的封装。而变量名带个 Cache 说明，会把这个 Method 对应的 ServiceMethod 缓存起来。
  private final Map<Method, ServiceMethod<?, ?>> serviceMethodCache = new ConcurrentHashMap<>();

  // 这个 callFactory 就是 OkHttpClient
  // 因为在 OkHttp 中会使用 okHttpClient.newCall()创建用于请求的 Call 对象, 因此就是个 Call 的工厂
  final okhttp3.Call.Factory callFactory;
  // 这个很好理解了，就是上面 基本使用 中的 baseUrl，转成了 HttpUrl 类型
  final HttpUrl baseUrl;
  // Converter 是个转换器，用于把我们的 响应 转换成特定的格式
  final List<Converter.Factory> converterFactories;
  // CallAdapter 就是将 Call 对象, 转成其他的, 比如 Rxjava 的 Observerable
  final List<CallAdapter.Factory> callAdapterFactories;
  // Executor 很熟悉了，这是个回调 Executor，想必就是用来切换线程的了
  final @Nullable Executor callbackExecutor;
  // 暂时理解为一个标志位
  final boolean validateEagerly;
```

使用 Builder 模式



```java
    public Builder() {
      this(Platform.get());
    }
```

调用了 Platform.get()方法，然后赋值给自己的 platform 变量。 我们看看这个 Platform 类。

```java
class Platform {
  private static final Platform PLATFORM = findPlatform(); // 查找当前平台

  static Platform get() {
    return PLATFORM; // 直接返回
  }

  private static Platform findPlatform() {
    try {
       // Class.forName 要求 JVM 根据 className 查找并加载指定的类
       // 如果能够加载到, 就说明是 Android 平台
      Class.forName("android.os.Build");
      if (Build.VERSION.SDK_INT != 0) {
        return new Android();
      }
    } catch (ClassNotFoundException ignored) {
    }
    try {
      Class.forName("java.util.Optional");
      return new Java8();
    } catch (ClassNotFoundException ignored) {
    }
    try {
      Class.forName("org.robovm.apple.foundation.NSObject");
      return new IOS();
    } catch (ClassNotFoundException ignored) {
    }
    return new Platform();
  }
}
```

Android 平台

```java
//Platform 内部
static class Android extends Platform {

   // 返回默认的回调执行器 : MainThreadExecutor
   // MainThreadExecutor 内部是使用 Handler 将消息发送到主线程, 然后在主线程执行回调方法
    @Override public Executor defaultCallbackExecutor() {
      return new MainThreadExecutor();
    }

// 返回默认的请求适配器
    @Override CallAdapter.Factory defaultCallAdapterFactory(@Nullable Executor callbackExecutor) {
      if (callbackExecutor == null) throw new AssertionError();
      return new ExecutorCallAdapterFactory(callbackExecutor);
    }

    static class MainThreadExecutor implements Executor {
      private final Handler handler = new Handler(Looper.getMainLooper());

      @Override public void execute(Runnable r) {
        handler.post(r);
      }
    }
  }
```

ExecutorCallAdapterFactory

```java
final class ExecutorCallAdapterFactory extends CallAdapter.Factory {
  final Executor callbackExecutor;

  ExecutorCallAdapterFactory(Executor callbackExecutor) {
    this.callbackExecutor = callbackExecutor;
  }

  @Override
  public CallAdapter<?, ?> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {
    if (getRawType(returnType) != Call.class) {
      return null;
    }
    final Type responseType = Utils.getCallResponseType(returnType);
    return new CallAdapter<Object, Call<?>>() {
      @Override public Type responseType() {
        return responseType;
      }

      @Override public Call<Object> adapt(Call<Object> call) {
        return new ExecutorCallbackCall<>(callbackExecutor, call);
      }
    };
  }

  //... ... 省略
}
```


继续:

调用了 `new Retrofit.Builder()`, 就返回了一个 Builder 对象, 里面的 Platform 是配置好了.
然后配置了  baseUrl 等其他属性, 最后 调用 build()方法

```java
public Retrofit build() {
      // 这一句告诉我们，baseUrl 是必不可少的。
      if (baseUrl == null) {
        throw new IllegalStateException("Base URL required.");
      }

      // 这里如果你没配置 callFactory , 会默认配置为 OkHttpClient
      okhttp3.Call.Factory callFactory = this.callFactory;
      if (callFactory == null) {
        callFactory = new OkHttpClient();
      }

      // 同样的，没配置的话，会默认配置为 Platform 的 defaultCallbackExecutor，这里我们之前分析过，它所返回的就是 MainThreadExecutor
      Executor callbackExecutor = this.callbackExecutor;
      if (callbackExecutor == null) {
        callbackExecutor = platform.defaultCallbackExecutor();
      }

      //这里会把你所配置的 CallAdapter.Factory 加到 List 里去，最后把 Platform 默认的 defaultCallAdapterFactory 即 ExecutorCallAdapterFactory 加到 List 的最后边，
      List<CallAdapter.Factory> callAdapterFactories = new ArrayList<>(this.callAdapterFactories);
      callAdapterFactories.add(platform.defaultCallAdapterFactory(callbackExecutor));

      //这里一样会把你配置的 Converter.Factory 加到 List 里去，但是会把一个 BuiltInConverters 加到第一个，而不是最后一个，请注意这点。
      List<Converter.Factory> converterFactories =
          new ArrayList<>(1 + this.converterFactories.size());

      converterFactories.add(new BuiltInConverters());
      converterFactories.addAll(this.converterFactories);

      //最后调用 Retrofit 的构造, 返回一个 Retrofit 对象
      return new Retrofit(callFactory, baseUrl, unmodifiableList(converterFactories),
          unmodifiableList(callAdapterFactories), callbackExecutor, validateEagerly);
    }
```



到这里，我们的 Retrofit 就构建完成了。如果按照我们 基本使用 中的例子，那么此刻，Retrofit 成员变量的值如下：

* serviceMethodCache ：暂时为空的 HashMap 集合
* callFactory ： OkHttpClient 对象
* baseUrl ： 根据配置的baseUrl " https://api.github.com/ " 字符串， 构建出了一个 HttpUrl 对象
* converterFactories ：一个 ArrayList 对象，里面存放着一个BuiltInConverters 对象
* callAdapterFactories ：一个 ArrayList 对象，里面存放着一个 ExecutorCallAdapterFactory 对象
* callbackExecutor ：MainThreadExecutor 对象
* validateEagerly ：默认值 false


# 创建请求接口

下面调用`GitHubService service = retrofit.create(GitHubService.class);`

看下实现
```java
public <T> T create(final Class<T> service) {
    Utils.validateServiceInterface(service);
    // 提前验证, 不想管
    if (validateEagerly) {
      eagerlyValidateMethods(service);
    }

    // 动态代理, 生成 代理对象, 动态代理参考上面的.
    return (T) Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[] { service },
        new InvocationHandler() {
          private final Platform platform = Platform.get();

          @Override public Object invoke(Object proxy, Method method, Object... args)
              throws Throwable {
            // If the method is a method from Object then defer to normal invocation.
            if (method.getDeclaringClass() == Object.class) {
              return method.invoke(this, args);
            }
            if (platform.isDefaultMethod(method)) {
              return platform.invokeDefaultMethod(method, service, proxy, args);
            }
            ServiceMethod serviceMethod = loadServiceMethod(method);
            OkHttpCall okHttpCall = new OkHttpCall<>(serviceMethod, args);
            return serviceMethod.callAdapter.adapt(okHttpCall);
          }
        });
  }
```

# 请求调用流程



调用 create 之后, 就能拿到 代理的对象了.


然后通过 代理对象, 就可以访问服务实体的方法了.

那服务实体在哪里呢??   是在哪里去调用 OkHttp 请求网络的呢? 感觉就是那个 ServiceMethod 吧?

我们继续看调用流程

```java
 Call<List<Integer>> repos = service.listRepos("fanshanhong");
```

注意: service 是代理对象.  

在动态代理中, 一般情况下, 在代理对象中, 持有 InvocationHandler 的引用.  然后在 InvocationHandler 中持有服务实体的引用.

当调用代理对象的方法的时候, 会调用到 InvocationHandler 的 invoke 方法, 然后在 invoke 方法中, 会去调用服务实体的方法.

但是, 在 Retrofit 里, 好像没看到服务实体.

我们看看它是怎么做的.


调用 代理对象 service 的  listRepos方法, 其实就进入了 InvocationHandler 的 invoke 方法. 看下InvocationHandler的 invoke

```java
  public <T> T create(final Class<T> service) {
    Utils.validateServiceInterface(service);
    if (validateEagerly) {
      eagerlyValidateMethods(service);
    }
    return (T) Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[] { service },
        new InvocationHandler() {
          private final Platform platform = Platform.get(); // 这个拿到的是 Android 平台

          @Override public Object invoke(Object proxy, Method method, @Nullable Object[] args)
              throws Throwable {
            // 如果这个方法是声明在 Object 类中，那么不拦截，直接执行
            if (method.getDeclaringClass() == Object.class) {
              return method.invoke(this, args);
            }
            ...

            // 下面三行代码非常重要
            ServiceMethod<Object, Object> serviceMethod = (ServiceMethod<Object, Object>) loadServiceMethod(method);
            OkHttpCall<Object> okHttpCall = new OkHttpCall<>(serviceMethod, args);
            return serviceMethod.adapt(okHttpCall);
          }
        });
  }
```

## `ServiceMethod<Object, Object> serviceMethod =(ServiceMethod<Object, Object>) loadServiceMethod(method);`

```java
  ServiceMethod<?, ?> loadServiceMethod(Method method) {
    // 首先从 缓存 serviceMethodCache 中取 ServiceMethod ，如果存在就返回，不存在继续往下走。
    // 也就是说 我们的 ServiceMethod 只会创建一次。确实没必要创建多次, 存着就行了.
    ServiceMethod<?, ?> result = serviceMethodCache.get(method);
    if (result != null) return result;

    synchronized (serviceMethodCache) {
      //这里又从缓存取了一遍，看到这里有没有一种熟悉的感觉，是不是跟 DCL 单例模式特别像，双重校验。
      result = serviceMethodCache.get(method);
      if (result == null) {
        result = new ServiceMethod.Builder<>(this, method).build();
        serviceMethodCache.put(method, result);
      }
    }
    return result;
  }
```
到这里其实 loadServiceMethod 已经分析完了，很简单，就是个 DCL 单例模式，然后获得 ServiceMethod 。
那其实我们现在的分析任务就很明确了，弄清楚这个 ServiceMethod 究竟是什么 。


### ServiceMethod 分析
```java
 final class ServiceMethod<R, T> {
  // ... 省略部分代码
  private final okhttp3.Call.Factory callFactory;
  private final CallAdapter<R, T> callAdapter;
  private final HttpUrl baseUrl;
  private final Converter<ResponseBody, R> responseConverter;

  // 应该是网络请求的 Http 方法，比如 GET、POST 啥的
  private final String httpMethod;
  // 相对地址 ，应该就是 "user/{user}/repos" 这一段
  private final String relativeUrl;
  // http 请求头
  private final Headers headers;
  // 网络请求的 http 报文的 body 的类型
  private final MediaType contentType;
  // 是否有 body
  private final boolean hasBody;
  // post 提交数据时，是否使用 表单提交 方式
  private final boolean isFormEncoded;
  // post 提交数据时，是否使用 Mutipart 方式，一般用来文件上传
  private final boolean isMultipart;
  // 方法参数处理器，应该是解析方法中的 参数 的吧。
  private final ParameterHandler<?>[] parameterHandlers;

  ServiceMethod(Builder<R, T> builder) {
    this.callFactory = builder.retrofit.callFactory();
    this.callAdapter = builder.callAdapter;
    this.baseUrl = builder.retrofit.baseUrl();
    this.responseConverter = builder.responseConverter;
    this.httpMethod = builder.httpMethod;
    this.relativeUrl = builder.relativeUrl;
    this.headers = builder.headers;
    this.contentType = builder.contentType;
    this.hasBody = builder.hasBody;
    this.isFormEncoded = builder.isFormEncoded;
    this.isMultipart = builder.isMultipart;
    this.parameterHandlers = builder.parameterHandlers;
  }

  // ... 省略部分代码
```


ServiceMethod 对象包含了访问网络的所有基本信息。



回到上面, 如果从缓存中取不到的话, 就使用 Builder 模式创建一个
```java
result = new ServiceMethod.Builder<>(this, method).build();
```


看下 build()方法
```java
public ServiceMethod build() {
      callAdapter = createCallAdapter();
      responseType = callAdapter.responseType();
      ...
      responseConverter = createResponseConverter();

      for (Annotation annotation : methodAnnotations) {
        parseMethodAnnotation(annotation);
      }
      ...
      int parameterCount = parameterAnnotationsArray.length;
      parameterHandlers = new ParameterHandler<?>[parameterCount];
      for (int p = 0; p < parameterCount; p++) {
        Type parameterType = parameterTypes[p];
        if (Utils.hasUnresolvableType(parameterType)) {
          throw parameterError(p, "Parameter type must not include a type variable or wildcard: %s",
              parameterType);
        }

        Annotation[] parameterAnnotations = parameterAnnotationsArray[p];
        if (parameterAnnotations == null) {
          throw parameterError(p, "No Retrofit annotation found.");
        }

        parameterHandlers[p] = parseParameter(p, parameterType, parameterAnnotations);
      }

      ...
      return new ServiceMethod<>(this);
    }
```


第一行`callAdapter = createCallAdapter();` 重要. 是创建请求适配器

```java
private CallAdapter<?> createCallAdapter() {
     ...
        return retrofit.callAdapter(returnType, annotations);
      
    }
```

```java
  public CallAdapter<?> callAdapter(Type returnType, Annotation[] annotations) {
    return nextCallAdapter(null, returnType, annotations);
  }
```


```java
public CallAdapter<?> nextCallAdapter(CallAdapter.Factory skipPast, Type returnType,
      Annotation[] annotations) {
...
      // 可以看到, 就是从 adapterFactories  这个列表中 遍历所有 Factory,   拿到 Factory, 然后调用 get , !=null 找到就返回
      // != null 就代表是合适的
    int start = adapterFactories.indexOf(skipPast) + 1;
    for (int i = start, count = adapterFactories.size(); i < count; i++) {
      CallAdapter<?> adapter = adapterFactories.get(i).get(returnType, annotations, this);
      if (adapter != null) {
        return adapter;
      }
    }
...
  }
```

那这个 adapterFactories 是啥时候加进去的?

是 使用 Builder 模式 构造 Retrofit 的时候加的
```java
    public Retrofit build() {
     ...
      List<CallAdapter.Factory> adapterFactories = new ArrayList<>(this.adapterFactories);
      adapterFactories.add(platform.defaultCallAdapterFactory(callbackExecutor));
...

      return new Retrofit(callFactory, baseUrl, converterFactories, adapterFactories,
          callbackExecutor, validateEagerly);
    }
```


先把用户添加的 CallAdapterFactory 添加到列表中, 然后再把默认的添加到最后. 比如说, 用户添加了  RxJavaCallAdapterFactory

那此时, 这个 adapterFactories 里应该有 2 个. 第一个 是 RxJavaCallAdapterFactory , 第二个是  `platform.defaultCallAdapterFactory(callbackExecutor)`

这个 platform , 说了, 是 Android. 它的 defaultCallAdapterFactory 返回的是:`ExecutorCallAdapterFactory`
```java
    @Override CallAdapter.Factory defaultCallAdapterFactory(Executor callbackExecutor) {
      return new ExecutorCallAdapterFactory(callbackExecutor);
    }
```

现在, adapterFactories 的两个元素就知道了.

回到上面 `nextCallAdapter` 方法中查询, 就是根据返回值和注解, 从列表中找一个合适的 CallAdapterFactory.

什么叫合适的?

首先, 现在  adapterFactories 里应该有 2 个Factory, 第一个 是 `RxJavaCallAdapterFactory`,  第二个是  `ExecutorCallAdapterFactory`

对于我们的方法: 
```java
public interface GitHubService {
    @GET("user/{user}/repos")
    Call<List<Integer>> listRepos(@Path("user") String user);
}
```
先拿到 `RxJavaCallAdapterFactory`, 调用它的 get

```java
@Override
  public CallAdapter<?> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {
    Class<?> rawType = getRawType(returnType);
    String canonicalName = rawType.getCanonicalName();
    boolean isSingle = "rx.Single".equals(canonicalName);
    boolean isCompletable = "rx.Completable".equals(canonicalName);
    if (rawType != Observable.class && !isSingle && !isCompletable) {
      return null;
    }
    ...
    return callAdapter;
  }
```

在 `RxJavaCallAdapterFactory`中, 查看返回值是不是 Observable. 不是就返回null 了.  我们上面的接口, 返回值是 Call, 肯定不对.

因此是 `ExecutorCallAdapterFactory` 合适.

```java
  public CallAdapter<Call<?>> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {
     // 明显这里判断的就是 Call. 就是对应我们的方法啊.
    if (getRawType(returnType) != Call.class) {
      return null;
    }
    final Type responseType = Utils.getCallResponseType(returnType);
    return new CallAdapter<Call<?>>() {
      @Override public Type responseType() {
        return responseType;
      }

      @Override public <R> Call<R> adapt(Call<R> call) {
        return new ExecutorCallbackCall<>(callbackExecutor, call);
      }
    };
  }
```

然后返回了一个 CallAdapter 对象.


如果还有个方法

```java
public interface GitHubService {
    @GET
    Observable<ResponseBaseBody> executeGet(
            @Url String url,
            @QueryMap Map<String, String> maps);
}
```

那肯定是`RxJavaCallAdapterFactory`合适了.


到这里，其实我们大概知道这个 CallAdapter 有什么用了，就是提供两个东西

* 方法responseType 返回的: 网络请求响应要返回的类型 responseType
* adapt 方法返回的: retrofit2.Call< T > ，注意这里不是 okhttp3 下的 Call 
```java
static final class ExecutorCallbackCall<T> implements Call<T>    // retrofit2.Call的子类
```


反正, 我们这里拿到了  ExecutorCallAdapterFactory 为我们创建的 CallAdapter , 赋值给了 ServiceMethod 的 callAdapter属性.




到这里  createAdapter()就完了, 继续:



```java
   for (Annotation annotation : methodAnnotations) {
        parseMethodAnnotation(annotation);
      }
```

这个就是解析请求方法的. 我们不是在方法上添加了注解 @GET  @POST, 就是解析这些,  然后给 ServiceMethod 里面的httpMethod / hasBody属性赋值.

同时, 解析: `value : users/{user}/repos`
给 `relativeUrl`  `relativeUrlParamNames` 赋值

然后, 后面 

```java
parameterHandlers[p] = parseParameter(p, parameterType, parameterAnnotations);
```
又解析了一些东西, 不知道在干嘛.


都完了之后, 就用 Builder 创建一个 ServiceMethod 返回了.

## `OkHttpCall<Object> okHttpCall = new OkHttpCall<>(serviceMethod, args);`


```java
 final class OkHttpCall<T> implements Call<T> {
  // 含有所有网络请求参数信息的 ServiceMethod
  private final ServiceMethod<T, ?> serviceMethod;
  private final @Nullable Object[] args;

  private volatile boolean canceled;

  // 实际进行网络请求的 Call
  private @Nullable okhttp3.Call rawCall;
  @GuardedBy("this") // Either a RuntimeException, non-fatal Error, or IOException.
  private @Nullable Throwable creationFailure;
  @GuardedBy("this")
  private boolean executed;

  // 传入配置好的 ServiceMethod 和 请求参数
  OkHttpCall(ServiceMethod<T, ?> serviceMethod, @Nullable Object[] args) {
    this.serviceMethod = serviceMethod;
    this.args = args;
  }
```

这里是 new 一个 OkHttpCall 对象，这个 OkHttpCall 是 Retrofit 的 Call，它里面就是做请求的地方，会有 request、enqueue 等同步、异步请求方法，但是在这里面真正执行请求的是 okhttp3.Call ，即把请求委托给 okHttp 去执行。下面简要看看它的构造方法和一些成员变量吧，因为这里只是 new 操作，所以暂时不分析其余方法，用到的时候再看。


## `serviceMethod.adapt(okHttpCall);`

```java
 T adapt(Call<R> call) {
    return callAdapter.adapt(call);
  }
```

这是 前面构建好的 ServiceMethod 中的 adapt 方法，会去调用 callAdapter 的 adapt 方法.

我们知道 ServiceMethod 中的 callAdapter 是 ExecutorCallAdapterFactory 中的 get 方法返回的 CallAdapter 实例。而这个实例的 adapt 方法会返回一个 ExecutorCallbackCall 对象。

就把这个 ExecutorCallbackCall  对象返回了. 这个对象是 retrofit.Call 的子类

```java
 <!-- ExecutorCallAdapterFactory 内部类 -->
 static final class ExecutorCallbackCall<T> implements Call<T> {
    // 这里在之前创建ExecutorCallAdapterFactory时，就知道它的值了，就是 MainThreadExecutor ，用来切换线程的
    final Executor callbackExecutor;
    // 这就是刚刚传进来的 OkHttpCall
    final Call<T> delegate;

    ExecutorCallbackCall(Executor callbackExecutor, Call<T> delegate) {
      this.callbackExecutor = callbackExecutor;
      this.delegate = delegate;
    }
```


到这里为止，我们已经成功的返回了一个 Call<List<Integer>>


# enqueue

调用 Call 的 enqueue

```java
 <!-- ExecutorCallbackCall 内部 -->
 @Override
 public void enqueue(final Callback<T> callback) {
      checkNotNull(callback, "callback == null");

      // 真正的 Call 去执行请求
      delegate.enqueue(new Callback<T>() {
        @Override public void onResponse(Call<T> call, final Response<T> response) {
          // 回调后 利用 MainThreadExecutor 中的 Handler 切换到主线程中去。
          callbackExecutor.execute(new Runnable() {
            @Override public void run() {
              if (delegate.isCanceled()) {
                // Emulate OkHttp's behavior of throwing/delivering an IOException on cancellation.
                callback.onFailure(ExecutorCallbackCall.this, new IOException("Canceled"));
              } else {
                callback.onResponse(ExecutorCallbackCall.this, response);
              }
            }
          });
        }

        @Override public void onFailure(Call<T> call, final Throwable t) {
          callbackExecutor.execute(new Runnable() {
            @Override public void run() {
              callback.onFailure(ExecutorCallbackCall.this, t);
            }
          });
        }
      });
    }
```

可以看到是 delegate 执行了 enqueue 操作，而 delegate 就是我们的 OkHttpCall (就是第二行创建的那个 OkHttpCall, 然后第三行调用的时候传入了)

在 OkHttpCall 里的 enqueue 方法是这样工作的。

```java
// OkHttpCall.java
@Override public void enqueue(final Callback<T> callback) {
    if (callback == null) throw new NullPointerException("callback == null");

    okhttp3.Call call;
    Throwable failure;

    synchronized (this) {
      if (executed) throw new IllegalStateException("Already executed.");
      executed = true;

      call = rawCall;
      failure = creationFailure;
      if (call == null && failure == null) {
        try {
           // 创建一个 OkHttp 的 RealCall 对象
          call = rawCall = createRawCall();
        } catch (Throwable t) {
          failure = creationFailure = t;
        }
      }
    }

    if (failure != null) {
      callback.onFailure(this, failure);
      return;
    }

    if (canceled) {
      call.cancel();
    }

// 调用 RealCall 的 enqueue 方法
    call.enqueue(new okhttp3.Callback() {
      @Override public void onResponse(okhttp3.Call call, okhttp3.Response rawResponse)
          throws IOException {
        Response<T> response;
        try {
           // rawResponse  是 OkHttp 返回的 Response, 称为 原始 Response
           // 拿到后, 调用parseResponse, 就是使用 Convertor 转换一下
          response = parseResponse(rawResponse);
        } catch (Throwable e) {
           // 回调 Retrofit 传入的 失败方法
          callFailure(e);
          return;
        }
        // 回调 Retrofit 传入的 成功方法
        callSuccess(response);
      }

      @Override public void onFailure(okhttp3.Call call, IOException e) {
        try {
           // 回调 Retrofit 传入的 失败方法
          callback.onFailure(OkHttpCall.this, e);
        } catch (Throwable t) {
          t.printStackTrace();
        }
      }

      private void callFailure(Throwable e) {
        try {
           // 回调 Retrofit 传入的 失败方法
          callback.onFailure(OkHttpCall.this, e);
        } catch (Throwable t) {
          t.printStackTrace();
        }
      }

      private void callSuccess(Response<T> response) {
        try {
          callback.onResponse(OkHttpCall.this, response);
        } catch (Throwable t) {
          t.printStackTrace();
        }
      }
    });
  }
```


```java
call = rawCall = createRawCall();
```


```java
  private okhttp3.Call createRawCall() throws IOException {
    Request request = serviceMethod.toRequest(args);// 拿到 Request, 这个 Request 就是 OkHttp的 Request
    okhttp3.Call call = serviceMethod.callFactory.newCall(request); // 这个 callFactory 就是 OkHttpClient

    // 连起来就是:
   // OkHttpClient.newCall(request)  构建了一个 OkHttp的  RealCall 对象返回了

    if (call == null) {
      throw new NullPointerException("Call.Factory returned null.");
    }
    return call;
  }
```

有了 RealCall 之后, 就调用了它的 enqueue 方法.

在 OkHttp 的 回调方法中, 调用了 Retrofit 传入的回调

在 Retrofit 的回调方法中, 使用callbackExecutor 把消息发送到主线程执行了, 然后用户设置的那个回调就是在主线程运行了.



# 问题

`return (T) Proxy.newProxyInstance()`, 返回值拿到的到底是不是 代理??

好像不是代理. 好像又是代理.  不管是不是, 反正好像没有对应的服务实体.

这么理解啊.

在调用这个的时候`(T) Proxy.newProxyInstance()`, JVM 生成代理类, 代理类中持有 InvocationHandler 的引用.

然后我们拿到返回值, 调用上面的方法, 内部是调用了: InvocationHandler 的 invoke 方法. 而 invode 方法中, 并没有去调用服务实体的方法, 而是仅仅返回了一个 retrofit.Call 对象.

然后我们拿着 Call 对象, 去 enqueue 了.


也可以把 ServiceMethod 作为服务实体?

调用代理的方法的时候, 通过 InvocationHandler 这个中介, 让 ServiceMethod  给我们生成了一个对应的 Call 对象.

然后使用 Call 对象进行网络请求.  这样理解也行.





