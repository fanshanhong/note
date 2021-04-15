---

title: OkHttp

date: 2019-05-30

categories: 
   - Android开源框架

tags: 
   - Android开源框架 


description: 
​
---


# 使用

```java
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).build();

        Request request = new Request.Builder().url("").get().build();

        Call call = client.newCall(request);

        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }
            @Override
            public void onResponse(Call call, okhttp3.Response response) throws IOException {

            }
        });
```


1. 通过 Builder 模式, 创建 OkHttpClient, 配置相关参数

2. 通过 Builder 模式, 创建 Request, 配置 URL, Method 等相关参数

3. 使用 Request 构建真正的请求对象:RealCall

4. enqueue 或者 execute





# 同步流程

大体主要流程:  构建  Request 和 RealCall -> 使用 Dispatcher 将 RealCall 丢入正在执行的同步请求队列(runningSyncCalls) -> 使用过滤器一步一步递进 -> 真正网络连接和请求发送 -> 获取 Response -> 走过滤器对 Response 层层处理 -> 在当前线程返回最终结果

注意: 同步就是在调用线程执行的, 不涉及线程池, 不涉及线程切换


同步请求使用: call.execute()

```java
// RealCall.java
  @Override public Response execute() throws IOException {
    synchronized (this) {
       // 判断是否执行过了. 如果已经执行过了, 就抛出异常
       // 一个 Call 只能被执行 一次
      if (executed) throw new IllegalStateException("Already Executed");
         executed = true;
      }

      try {
         // 这里把 Call 对象 放入正在执行的同步请求队列中
         client.dispatcher().executed(this);
         // 使用过滤器执行
         Response result = getResponseWithInterceptorChain(false);
         if (result == null) throw new IOException("Canceled");
         return result;
      } finally {
         client.dispatcher().finished(this);
      }
  }
```

```java
// Dispatcher.java

   // 正在执行的同步请求队列
   private final Deque<RealCall> runningSyncCalls = new ArrayDeque<>();

   synchronized void executed(RealCall call) {
      runningSyncCalls.add(call);
   }
```



```java
// RealCall.java
  Response getResponseWithInterceptorChain() throws IOException {
    // Build a full stack of interceptors.
    List<Interceptor> interceptors = new ArrayList<>();
    interceptors.addAll(client.interceptors());
    interceptors.add(retryAndFollowUpInterceptor);
    interceptors.add(new BridgeInterceptor(client.cookieJar()));
    interceptors.add(new CacheInterceptor(client.internalCache()));
    interceptors.add(new ConnectInterceptor(client));
    if (!forWebSocket) {
      interceptors.addAll(client.networkInterceptors());
    }
    interceptors.add(new CallServerInterceptor(forWebSocket));

    Interceptor.Chain chain = new RealInterceptorChain(interceptors, null, null, null, 0,
        originalRequest, this, eventListener, client.connectTimeoutMillis(),
        client.readTimeoutMillis(), client.writeTimeoutMillis());

    return chain.proceed(originalRequest);
  }
```


先后经过  5 个过滤器, 按照 12345的顺序

1. RetryAndFollowUpInterceptor
2. BridgeInterceptor(负责封装 HTTP 报文)
3. CacheInterceptor(负责缓存)
4. ConnectInterceptor(负责连接:socket.connect())
5. CallServerInterceptor(负责写入请求数据, 并拿到 Response) 这几个过滤器


拿到, 再按照 54321 的顺序回来.

最后拿到结果 Response result, 并把结果返回给调用者. 

流程结束.




# 异步流程


异步请求使用: call.enqueue(callback) 这样的方式

大体主要流程:  构建  Request 和 RealCall -> 先封装一个 AsyncRunnable(Runnable) , 使用 Dispatcher 将 AsyncRunnable 丢入正在执行的异步请求队列(runningAsyncCalls) , 同时, 要把这个 AsyncRunnable 丢人线程池执行  -> 在线程池中通过过滤器链 一步一步处理 -> 请求网络, 拿到 Response -> 过滤器链反向执行 -> 返回处理好的 Response -> 调用用户设置的回调方法(在子线程中)


注意: 异步请求涉及线程池和线程切换相关



1.  

```java
// RealCall.java
@Override public void enqueue(Callback responseCallback) {
    synchronized (this) {
      // 判断是否执行过了. 如果已经执行过了, 就抛出异常
      // 一个 Call 只能被执行 一次
      if (executed) throw new IllegalStateException("Already Executed");
      executed = true;
    }
    ...
    client.dispatcher().enqueue(new AsyncCall(responseCallback));
  }
```


2. 将 用户设置的  responseCallback  封装成一个 AsyncCall(实质是一个 Runnable)

3. 将 封装好的 AsyncCall 交给 Dispatcher, 也就是调用 Dispatcher 的 enqueue


```java
// Dispatcher.java
  synchronized void enqueue(AsyncCall call) {
    if (runningAsyncCalls.size() < maxRequests && runningCallsForHost(call) < maxRequestsPerHost) {
      runningAsyncCalls.add(call);
      executorService().execute(call);
    } else {
      readyAsyncCalls.add(call);
    }
  }
```

4.  进入 Dispatcher 的 enqueue 方法.  2 个条件判断

第一: 当前正在执行的并发请求的数量(runningAsyncCalls.size()) 少于 64  
第二: 针对同一Host 的请求数量(runningCallsForHost) 少于 5


解释一下 Dispatcher 中的几个数据结构:

```java
public final class Dispatcher {
   // 在同一时间, 并发请求 的 最大值. 不能超过这个值
  private int maxRequests = 64;
  // 在同一时间, 针对同一 Host 的并发请求数量, 不能超过这个
  // 比如说, 在同一时间, 请求 baidu.com  这个 host 的 请求, 不能超过 5 个.
  private int maxRequestsPerHost = 5;

  // 异步请求的等待队列
  private final Deque<AsyncCall> readyAsyncCalls = new ArrayDeque<>();

  // 正在执行的异步请求队列
  private final Deque<AsyncCall> runningAsyncCalls = new ArrayDeque<>();
```


readyAsyncCalls  这个队列, 其实就是记录一下当前有哪些并发请求正在执行. 跟先进先出没关系. 仅仅就是一个记录的功能, 跟 set 差不多
runningAsyncCalls 这个队列, 是等待的队列, 当某些条件不满于, 比如, 当前的并发请求数量>=64 了, 就会先把请求放在这个等待队列中.   那这个等待队列, 就是先进先出的, 最先进入的, 应该是最先被取出来执行的. (好像没说有优先级之类的)


这几个数据结果清楚之后, 再说 Dispatcher 的 enqueue 方法


2 个条件判断

第一: 当前正在执行的并发请求的数量(runningAsyncCalls.size()) 少于 64  
第二: 针对同一 Host 的请求数量(runningCallsForHost) 少于 5

我们看下  runningCallsForHost  是如何计算的.
```java
  private int runningCallsForHost(AsyncCall call) {
    int result = 0;
    for (AsyncCall c : runningAsyncCalls) {
      if (c.host().equals(call.host())) result++;
    }
    return result;
  }
```

就是遍历 正在执行的并发请求列表啊, 然后数一数 跟 现在想要执行的这个Call 的 host 相同的有几个. 


如果这两个条件都满足, 则 先把请求(AsyncCall) 加入到正在执行的并发请求队列(就是简单记录一下, 以便知道当前有几个请求在跑呢.)
然后, 把这个 AsyncCall  丢入线程池执行

那如果条件不满足呢?  就把 AsyncCall 丢入等待队列候着.  等待队列的啥时候会执行? 当线程池中的线程执行完他正在执行的那个, 就会从 等待队列中取出一个执行了

5. 线程池执行

先说线程池
```java
  public synchronized ExecutorService executorService() {
    if (executorService == null) {
      executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
          new SynchronousQueue<Runnable>(), Util.threadFactory("OkHttp Dispatcher", false));
    }
    return executorService;
  }
```

这个线程池有几点注意的:
corePoolSize = 0  表示核心线程数是 0.  这样的话, 便于回收.
为啥呢, 因为当线程池中的线程数小于等于 corePoolSize 的时候, 是不用回收的.

举例: 比如, corePoolSize = 3,  keepAlive = 60 秒.
比如当前线程池中有 5 个线程在跑.  当所有的都执行完了, 并且空闲了 60 秒了, 那此时只释放 2 个线程, 剩下 3 个不释放.  可以理解为: 核心线程是不回收的.

这里设置 corePoolSize = 0  , 就是想要在空闲的时候全部回收了.

线程池的执行流程如下:

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/thread_pool.jpeg)

就是有任务提交到线程池, 记得先判断当前 corePoolSize, 如果 正在运行的线程 小于  corePoolSize  , 就开线程直接执行.

如果不小于 corePoolSize, 就加入到 等待队列中等待.

如果等待队列满了, 就看看当前线程是否小于 最大线程数, 如果小于, 就开启后备线程执行任务.

如果当前线程也不小于最大线程了, 就进行拒绝策略.


当线程池 开启线程执行一个任务的时候, 这个任务就是这个现成的 firstTask.  执行完这个 firstTask, 就去等待队列中取, 继续执行.
如果没有了, 就歇着. 如果歇着超过了指定了 keepAlive 时间, 此时就要判断当前线程池中的线程数量 是否 还是 大于 corePoolSize, 如果大于的话, 那这个线程就要被回收了.  



线程池流程说完, 说: SynchronousQueue. 这是个同步, 单向 Queue, 容量是 0.

一般正常的 Queue, 我们可以向 Queue 中添加(offer)几条数据, 然后 take() 出来

这个SynchronousQueue呢. 容量是 0, 也就是说 在SynchronousQueue内部没有任何存放元素的能力。

对于每一个take的线程会阻塞直到有一个put的线程向SynchronousQueue放入元素为止，反之亦然。

所以类似peek操作或者迭代器操作也是无效的，元素只能通过put类操作或者take类操作才有效。

非常适合做交换的工作

6. 线程池说完了, 就开始真正的执行

```java
executorService().execute(call);
```

我们知道, 一般把一个 Runnable 丢入线程池, 在子线程中执行 Runnable 的run 方法. 我们看下这个 AsyncCall 的 run 方法

```java
final class AsyncCall extends NamedRunnable {
    private final Callback responseCallback;

    AsyncCall(Callback responseCallback) {
      super("OkHttp %s", redactedUrl());
      this.responseCallback = responseCallback;
    }
...

    @Override protected void execute() {
      boolean signalledCallback = false;
      try {
        Response response = getResponseWithInterceptorChain();
        if (retryAndFollowUpInterceptor.isCanceled()) {
          signalledCallback = true;
          responseCallback.onFailure(RealCall.this, new IOException("Canceled"));
        } else {
          signalledCallback = true;
          responseCallback.onResponse(RealCall.this, response);
        }
      } catch (IOException e) {
        if (signalledCallback) {
          // Do not signal the callback twice!
          Platform.get().log(INFO, "Callback failure for " + toLoggableString(), e);
        } else {
          eventListener.callFailed(RealCall.this, e);
          responseCallback.onFailure(RealCall.this, e);
        }
      } finally {
        client.dispatcher().finished(this);
      }
    }
  }
```


```java
public abstract class NamedRunnable implements Runnable {
  protected final String name;

  public NamedRunnable(String format, Object... args) {
    this.name = Util.format(format, args);
  }

  @Override public final void run() {
    String oldName = Thread.currentThread().getName();
    Thread.currentThread().setName(name);
    try {
      execute();
    } finally {
      Thread.currentThread().setName(oldName);
    }
  }

  protected abstract void execute();
}
```


可以看到, AsyncCall 没有 run 方法, 实际是执行 NamedRunnable  的 run.

NamedRunnable  的 run 中, 先设置了一下线程名字, 然后执行子类实现的 execute(); 方法.

看下  AsyncCall  的 execute(); 方法

7. `Response response = getResponseWithInterceptorChain();`

使用过滤器链开始执行请求了.

执行完拿到 Response, 然后就回调用户的回调方法. 注意这一直是在线程池中的子线程中执行的.
`responseCallback.onFailure`   `responseCallback.onResponse`

就完了


## 过滤器链

说下过滤器链


>   责任链模式  一般做法:

   1. 有个 Filter 接口. 有 doFilter() 或者 intercept()方法

   2. 不同的过滤器实现这个 Filter 接口就行. 然后实现里面的 doFilter 方法

   3. 有个 Chain, 里面维护一个 List 集合, 把所有的 Filter 链起来. 同时 , 在 Filter 中要持有 Chain 的引用

   4. 想要执行整个过滤器链的时候, 调用 Chain.proceed()方法.

   Chain 里会根据当前执行到哪里Filter 了(有 index 记录), 取得对应的 Filter

   就执行 Filter 的 doFilter 即可.

   在 Filter 的 doFilter 中, 处理完成, 记得继续调用 Chain.proceed()方法, 把任务丢到下一个Filter 执行.  这样一层一层递进下去

   5. 注意, 在 Filter 中要持有 Chain 的引用


 看下 OkHttp 的过滤器链 是不是这样实现的


```java
public final class RealInterceptorChain implements Interceptor.Chain {
   // 所有的过滤器集合
  private final List<Interceptor> interceptors;
  // 当前执行到哪个过滤器了
  private final int index;
  // 要处理的对象(任务)
  private final Request request;

public Response proceed(Request request, StreamAllocation streamAllocation, HttpCodec httpCodec,
      RealConnection connection) throws IOException {
    if (index >= interceptors.size()) throw new AssertionError();
   ...
    // Call the next interceptor in the chain.
    RealInterceptorChain next = new RealInterceptorChain(interceptors, streamAllocation, httpCodec,
        connection, index + 1, request, call, eventListener, connectTimeout, readTimeout,
        writeTimeout);
    // 取出下一个过滤器
    Interceptor interceptor = interceptors.get(index);
    // 执行下一个过滤器, 过滤器里要持有  Chain 的引用, 便于在处理完之后调用 chain.proceed().
    // 但是不知道上面为啥非要再 new 一个 Chain, 把当前的这个 Chain 的 index++ 不就好了么...
    Response response = interceptor.intercept(next);
   ...
    return response;
  }
}
```

我们看到 RealInterceptorChain  的 proceed方法中
先判断一下 index 合法.

然后`Interceptor interceptor = interceptors.get(index);` 取出下一个 过滤器
`Response response = interceptor.intercept(next);`执行下一个过滤器, 过滤器里要持有  Chain 的引用, 便于在处理完之后调用 chain.proceed().


我们找个简单的过滤器看下  intercept 方法
```java

public final class ConnectInterceptor implements Interceptor {
  public final OkHttpClient client;

  public ConnectInterceptor(OkHttpClient client) {
    this.client = client;
  }

  @Override public Response intercept(Chain chain) throws IOException {
    RealInterceptorChain realChain = (RealInterceptorChain) chain;
    Request request = realChain.request();
    StreamAllocation streamAllocation = realChain.streamAllocation();

    // We need the network to satisfy this request. Possibly for validating a conditional GET.
    boolean doExtensiveHealthChecks = !request.method().equals("GET");
    HttpCodec httpCodec = streamAllocation.newStream(client, chain, doExtensiveHealthChecks);
    RealConnection connection = streamAllocation.connection();

// 调用 chain.proceed, 把 request 丢到下一个过滤器执行
    return realChain.proceed(request, streamAllocation, httpCodec, connection);
  }
}
```


## 说下这几个过滤器的作用

```java
Response getResponseWithInterceptorChain() throws IOException {
    // Build a full stack of interceptors.
    List<Interceptor> interceptors = new ArrayList<>();
    interceptors.addAll(client.interceptors()); // 用户自定义过滤器
    interceptors.add(retryAndFollowUpInterceptor); // 重试过滤器
    interceptors.add(new BridgeInterceptor(client.cookieJar())); // 桥接过滤器
    interceptors.add(new CacheInterceptor(client.internalCache())); // 缓存过滤器
    interceptors.add(new ConnectInterceptor(client)); // 连接过滤器
    if (!forWebSocket) {
      interceptors.addAll(client.networkInterceptors());
    }
    interceptors.add(new CallServerInterceptor(forWebSocket)); // 请求过滤器

    Interceptor.Chain chain = new RealInterceptorChain(interceptors, null, null, null, 0,
        originalRequest, this, eventListener, client.connectTimeoutMillis(),
        client.readTimeoutMillis(), client.writeTimeoutMillis());

    return chain.proceed(originalRequest);
  }
```


### retryAndFollowUpInterceptor

主要处理超时重连和重定向相关处理

### BridgeInterceptor


```java
// BridgeInterceptor.java
@Override public Response intercept(Chain chain) throws IOException {
    Request userRequest = chain.request();
    Request.Builder requestBuilder = userRequest.newBuilder();

    RequestBody body = userRequest.body();
    if (body != null) {
      MediaType contentType = body.contentType();
      if (contentType != null) {
        requestBuilder.header("Content-Type", contentType.toString());
      }

      long contentLength = body.contentLength();
      if (contentLength != -1) {
        requestBuilder.header("Content-Length", Long.toString(contentLength));
        requestBuilder.removeHeader("Transfer-Encoding");
      } else {
        requestBuilder.header("Transfer-Encoding", "chunked");
        requestBuilder.removeHeader("Content-Length");
      }
    }

    if (userRequest.header("Host") == null) {
      requestBuilder.header("Host", hostHeader(userRequest.url(), false));
    }

    if (userRequest.header("Connection") == null) {
      requestBuilder.header("Connection", "Keep-Alive");
    }

    // If we add an "Accept-Encoding: gzip" header field we're responsible for also decompressing
    // the transfer stream.
    boolean transparentGzip = false;
    if (userRequest.header("Accept-Encoding") == null && userRequest.header("Range") == null) {
      transparentGzip = true;
      requestBuilder.header("Accept-Encoding", "gzip");
    }

    List<Cookie> cookies = cookieJar.loadForRequest(userRequest.url());
    if (!cookies.isEmpty()) {
      requestBuilder.header("Cookie", cookieHeader(cookies));
    }

    if (userRequest.header("User-Agent") == null) {
      requestBuilder.header("User-Agent", Version.userAgent());
    }

    Response networkResponse = chain.proceed(requestBuilder.build());

    HttpHeaders.receiveHeaders(cookieJar, userRequest.url(), networkResponse.headers());

    Response.Builder responseBuilder = networkResponse.newBuilder()
        .request(userRequest);

    if (transparentGzip
        && "gzip".equalsIgnoreCase(networkResponse.header("Content-Encoding"))
        && HttpHeaders.hasBody(networkResponse)) {
      GzipSource responseBody = new GzipSource(networkResponse.body().source());
      Headers strippedHeaders = networkResponse.headers().newBuilder()
          .removeAll("Content-Encoding")
          .removeAll("Content-Length")
          .build();
      responseBuilder.headers(strippedHeaders);
      String contentType = networkResponse.header("Content-Type");
      responseBuilder.body(new RealResponseBody(contentType, -1L, Okio.buffer(responseBody)));
    }

    return responseBuilder.build();
  }
```


主要是把用户配置的相关信息, 转成真正的 HTTP 报文头部

比如: Content-Type   Content-Length 这些

同时, 对Response 进行处理.
如果 Response 在服务端进行了压缩处理(gzip), 则在这里要解压, 拿到原始报文


### CacheInterceptor

缓存相关处理

参考: https://blog.csdn.net/chunqiuwei/article/details/73224494

### ConnectInterceptor

用于真正的网络连接

```java
public final class ConnectInterceptor implements Interceptor {
  public final OkHttpClient client;

  public ConnectInterceptor(OkHttpClient client) {
    this.client = client;
  }

  @Override public Response intercept(Chain chain) throws IOException {
    ...
    HttpCodec httpCodec = streamAllocation.newStream(client, chain, doExtensiveHealthChecks);
    RealConnection connection = streamAllocation.connection();

    return realChain.proceed(request, streamAllocation, httpCodec, connection);
  }
}
```

streamAllocation.newStream(client, chain, doExtensiveHealthChecks); 里

```java
  public HttpCodec newStream(
      OkHttpClient client, Interceptor.Chain chain, boolean doExtensiveHealthChecks) {
...

    try {
      RealConnection resultConnection = findHealthyConnection(connectTimeout, readTimeout,
          writeTimeout, connectionRetryEnabled, doExtensiveHealthChecks);
     ...
    } catch (IOException e) {
      ...
    }
  }
```


findHealthyConnection

```java
private RealConnection findHealthyConnection(int connectTimeout, int readTimeout,
      int writeTimeout, boolean connectionRetryEnabled, boolean doExtensiveHealthChecks)
      throws IOException {
    while (true) {
      RealConnection candidate = findConnection(connectTimeout, readTimeout, writeTimeout,
          connectionRetryEnabled);
...

      return candidate;
    }
  }
```

findConnection
```java
private RealConnection findConnection(int connectTimeout, int readTimeout, int writeTimeout,
      boolean connectionRetryEnabled) throws IOException {
    boolean foundPooledConnection = false;
    RealConnection result = null;
    ...

    // Do TCP + TLS handshakes. This is a blocking operation.
    result.connect(
        connectTimeout, readTimeout, writeTimeout, connectionRetryEnabled, call, eventListener);
    routeDatabase().connected(result.route());
...
    return result;
  }
```


最终调用了 RealConnection  的 connect

在 connect 中, 调用`connectSocket(connectTimeout, readTimeout, call, eventListener);`

内部

```java
  private void connectSocket(int connectTimeout, int readTimeout, Call call,
      EventListener eventListener) throws IOException {
    Proxy proxy = route.proxy();
    Address address = route.address();
   // 这个就是真正用于网络连接的 Socket.
   // 根据类型不同, 创建出不同的 Socket 对象
    rawSocket = proxy.type() == Proxy.Type.DIRECT || proxy.type() == Proxy.Type.HTTP
        ? address.socketFactory().createSocket()
        : new Socket(proxy);

    eventListener.connectStart(call, route.socketAddress(), proxy);
    rawSocket.setSoTimeout(readTimeout);
    try {
       // 这里就执行了 socket.connect(address, connectTimeout);方法
      Platform.get().connectSocket(rawSocket, route.socketAddress(), connectTimeout);
    } catch (ConnectException e) {
     ...
    }

    try {
       // 连接建立之后, 这里拿到了 socket 的 输入输出流
      
      source = Okio.buffer(Okio.source(rawSocket)); // 输入流, 用 Okio 包装了一下
      
      sink = Okio.buffer(Okio.sink(rawSocket));// 输出流, 用 Okio 包装了一下
    } catch (NullPointerException npe) {
      ...
    }
  }

```

建立连接后, 拿到socket 的 输入输出流, 但是并没有进行读写操作. 读写操作在后面一个过滤器执行


### CallServerInterceptor

```java
@Override public Response intercept(Chain chain) throws IOException {
...

    // 写入 HTTP 请求的 Header 信息
    httpCodec.writeRequestHeaders(request);
    /******************************************
         @Override public void writeRequestHeaders(Request request) throws IOException {
            String requestLine = RequestLine.get(
               request, streamAllocation.connection().route().proxy().type());
            writeRequest(request.headers(), requestLine);
         }

         public void writeRequest(Headers headers, String requestLine) throws IOException {
            if (state != STATE_IDLE) throw new IllegalStateException("state: " + state);
            sink.writeUtf8(requestLine).writeUtf8("\r\n");
            for (int i = 0, size = headers.size(); i < size; i++) {
               sink.writeUtf8(headers.name(i))
                  .writeUtf8(": ")
                  .writeUtf8(headers.value(i))
                  .writeUtf8("\r\n");
            }
            sink.writeUtf8("\r\n");
            state = STATE_OPEN_REQUEST_BODY;
         }

         就是通过 socket 的输出流, 把 HTTP 请求头写进去了.
    *****************************************/
  ...

    Response.Builder responseBuilder = null;
    // 如果有请求体, 就走这里了
    if (HttpMethod.permitsRequestBody(request.method()) && request.body() != null) {
       ...
      if (responseBuilder == null) {
        // Write the request body if the "Expect: 100-continue" expectation was met.
        realChain.eventListener().requestBodyStart(realChain.call());
        // 拿到 ContentLength
        long contentLength = request.body().contentLength();

        // 这里是把 socket 的输出流 sink 包装成了一个CountingSink
        CountingSink requestBodyOut =
            new CountingSink(httpCodec.createRequestBody(request, contentLength));
        BufferedSink bufferedRequestBody = Okio.buffer(requestBodyOut);

         // 最终是写入到了 socket 的输出流里
        request.body().writeTo(bufferedRequestBody);
        bufferedRequestBody.close();
        realChain.eventListener()
            .requestBodyEnd(realChain.call(), requestBodyOut.successfulCount);
      } else if (!connection.isMultiplexed()) {
      ...
      }
    }

      // 这里调用 socket 输出流的flush 方法:  sink.flush(); 将数据刷新到网络上.
      // 到这里, 请求的 Header 和 请求体才真正发送到服务器了
    httpCodec.finishRequest();

    if (responseBuilder == null) {
      realChain.eventListener().responseHeadersStart(realChain.call());
      // 这里使用 socket 的输入流 source 读(正常是会阻塞的, 等待数据返回) , 获取到服务器返回的响应
      responseBuilder = httpCodec.readResponseHeaders(false);
    }

   // 将服务器返回的响应, 封装成 okhttp 自己的 Response
    Response response = responseBuilder
        .request(request)
        .handshake(streamAllocation.connection().handshake())
        .sentRequestAtMillis(sentRequestMillis)
        .receivedResponseAtMillis(System.currentTimeMillis())
        .build();

    realChain.eventListener()
        .responseHeadersEnd(realChain.call(), response);
      // 响应状态码判断一波
    int code = response.code();
    if (forWebSocket && code == 101) {
      // Connection is upgrading, but we need to ensure interceptors see a non-null response body.
      response = response.newBuilder()
          .body(Util.EMPTY_RESPONSE)
          .build();
    } else {
      response = response.newBuilder()
          .body(httpCodec.openResponseBody(response))
          .build();
    }

      // 把响应返回
    return response;
  }
```



另外, Okhttp 的过滤器, 同时处理 Request 和 Response.

流程: 以 CacheInterceptor / ConnectInterceptor / CallServerInterceptor 为例


```
调用者
   > CacheInterceptor
   > 处理 Request
   > 调用 chain.proceed. 前进到下一个过滤器

            > ConnectInterceptor
            > 处理 Request
            > 调用 chain.proceed. 前进到下一个过滤器

                  > CallServerInterceptor 发起真正的网络请求, 拿到 Response, 返回
            
            > 返回到 ConnectInterceptor , 拿到 Response, 继续处理 Response, 处理完, 返回
   
   > 返回到这里, 拿到Response, 处理 Response, 继续返回

返回到调用者
```






参考: 

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/interceptor1.png)



![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/CacheInterceptor.png)

图片来自:https://blog.csdn.net/chunqiuwei/article/details/73224494




