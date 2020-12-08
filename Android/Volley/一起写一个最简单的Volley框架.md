# 一起写一个最简单的Volley框架

* [Request](#request)
* [Response](#response)
* [RequestQueue](#requestqueue)
* [NetworkDispatcher](#networkdispatcher)
* [HttpStack](#httpstack)
* [UrlHttpStack](#urlhttpstack)
* [Delivery](#delivery)
* [SimpleVolley](#simplevolley)

[源码地址github](https://github.com/fanshanhong/simple-volley)

# Request

首先定义请求类（ Request 类 ）

`Request` 类需要的属性有如下几个：
* url 
请求的URL
* method 
HTTP 请求的方法， GET、POST、HEAD或者是其他
* 请求头
HTTP 请求头，比如 Content-Type，Accept 等
* 请求参数  
对于 POST 方法，想要往服务端传入请求的参数
* 回调方法
包括请求成功、失败后的回调方法


为什么用泛型 T 呢？因为对于网络请求来说，用户得到的请求结果格式是不确定的，可能是 JSON， 也可能是XML， String 等。但是 HTTP 的响应实体全部都是二进制流，因此，我们要在请求的基类中预留解析方法 `abstract void parseResponse2(Response<T> response);`, 该方法可以将 HTTP 响应实体中的二进制数据，解析成用户需要的具体类型。因此我们可以把Request作为泛型类，它的泛型类型就是它的返回数据类型，返回的类型是字符串，那么我们就用`Request<String>`。

```java
package com.fan.simplevolley.volley;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * @Description: 请求的基类封装
 * @Author: fan
 * @Date: 2020/3/5 23:34
 * @Modify:
 */
public abstract class Request<T> implements Comparable<Request<T>> {

    /**
     * 请求的URL
     */
    public String url;

    /**
     * 请求的方法
     */
    private Method method;

    /**
     * 默认的参数编码
     */
    private String DEFAULT_PARAMS_ENCODING = "UTF-8";

    /**
     * 请求的序列号,用于在请求队列中排序
     */
    private int sequenceNumber;

    /**
     * 请求的优先级,用于在请求队列中排序
     */
    private Priority priority;
    

    /**
     * 请求头
     */
    Map<String, String> headers = new HashMap<>();

    /**
     * 请求的参数
     */
    Map<String, String> params = new HashMap<>();

    /**
     * 请求结果回调
     */
    Response.RequestListener<T> listener;


    /**
     * constructor
     *
     * @param method
     * @param url
     * @param listener
     */
    public Request(Method method, String url, Response.RequestListener<T> listener) {
        this.method = method;
        this.url = url;
        this.listener = listener;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public Method getHttpMethod() {
        return method;
    }

    protected String getParamsEncoding() {
        return DEFAULT_PARAMS_ENCODING;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public Priority getPriority() {
        return priority;
    }

    /**
     * 指定请求时的Content-Type
     *
     * @return
     */
    public String getBodyContentType() {
        return "application/x-www-form-urlencoded; charset=" + getParamsEncoding();
    }


    /**
     * 如果是GET请求, 参数拼接在URL后面, 并且没有请求实体
     * 如果是POST请求, 请求参数是放在请求实体里面
     * <p>
     * 该方法用于获取指定的请求参数, 并将这些参数按照指定格式编码, 生成字节数组。  对于POST 和 PUT请求, 该字节数组的内容将作为请求实体发送
     *
     * @return
     */
    public byte[] getBody() {
        // 这里会调用getParams()方法, 获取指定的参数
        // 多态, 向 RequestQueue 中 add 了Request的子类, 如果子类重写了该方法, 就调用子类的该方法
        Map<String, String> params = getParams();
        if (params != null && params.size() > 0) {
            return encodeParameters(params, getParamsEncoding());
        }
        return null;
    }

    /**
     * 将参数转换为Url编码的参数串, 也就是 key=value&key2=value2的形式, 但是要注意用URLEncoder编码一下
     * 如果请求以这种形式作为请求实体进行请求, 需要将 Content-Type 指定为 application/x-www-form-urlencoded
     */
    private byte[] encodeParameters(Map<String, String> params, String paramsEncoding) {
        StringBuilder encodedParams = new StringBuilder();
        try {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                encodedParams.append(URLEncoder.encode(entry.getKey(), paramsEncoding));
                encodedParams.append('=');
                encodedParams.append(URLEncoder.encode(entry.getValue(), paramsEncoding));
                encodedParams.append('&');
            }
            return encodedParams.toString().getBytes(paramsEncoding);
        } catch (UnsupportedEncodingException uee) {
            throw new RuntimeException("Encoding not supported: " + paramsEncoding, uee);
        }
    }

    /**
     * 将 response 解析成需要的数据对象
     *
     * @param response
     * @return
     */
    abstract T parseResponse(Response<T> response);

    abstract void parseResponse2(Response<T> response);

    /**
     * 将解析后的数据对象投递到 UI 线程
     *
     * @param response
     */
    abstract void deliverResponse(T response);

    /**
     * 投递错误
     *
     * @param error
     */
    void deliverError(VolleyError error) {
        if (listener != null) {
            listener.onError(error);
        }
    }

    @Override
    public int compareTo(Request<T> another) {
        Priority myPriority = this.getPriority();
        Priority anotherPriority = another.getPriority();
        // 如果优先级相等,那么按照添加到队列的序列号顺序来执行
        return myPriority.equals(another) ? this.getSequenceNumber() - another.getSequenceNumber()
                : myPriority.ordinal() - anotherPriority.ordinal();
    }


    /**
     * 请求的方法枚举
     */
    public enum Method {
        GET("GET"),
        POST("POST");

        private String method = "";

        private Method(String method) {
            this.method = method;
        }

        @Override
        public String toString() {
            return method;
        }
    }

    /**
     * 请求的优先级枚举
     */
    public enum Priority {
        LOW,
        NORMAL,
        HIGH
    }

}

```

# Response

根据 HTTP 协议，HTTP 响应报文结构如下：


![HTTP 响应报文](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/http_header_message.jpg)


在 `org.apache.http` 包下有个 `BasicHttpResponse` 类。这个类中帮我们维护了 HTTP 响应报文中的  `①报文协议及版本`  `②状态码及状态描述` 的相关内容。因此我们直接继承自它，再将 HTTP 响应报文中的`③响应头`和 `④响应实体`维护进去就可以了。因此我们的 `Response` 类这样声明  `public class Response<T> extends BasicHttpResponse {}`, 并在里面添加了如下两个变量：
    
1. `byte[] rawData;` 响应实体中的原始二进制数据

2. `public T result;` 将响应实体解析后的对象
    
tip1: 简单起见，响应头的处理我们给暂时忽略了。

tip2: `Response` 类为啥也做成泛型的呢？

因为每个请求都对应一个 Response，但这里的问题是这个 Response 的数据格式我们是不知道的。这个数据格式应该是跟 Request的 泛型 T 一致，因此我们将 Response 也写成泛型的。


```java
package com.fan.simplevolley.volley;

import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.message.BasicHttpResponse;

/**
 * @Description: 响应的封装类
 * @Author: fan
 * @Date: 2020/3/5 23:34
 * @Modify:
 */
public class Response<T> extends BasicHttpResponse {

    /**
     * 响应实体数据
     */
    byte[] rawData;

    /**
     * 将响应实体解析后的对象
     */
    public T result;

    public Response(StatusLine statusline) {
        super(statusline);
    }

    public Response(ProtocolVersion ver, int code, String reason) {
        super(ver, code, reason);
    }

    public Response<T> success(T parsed) {
        result = parsed;
        return this;
    }

    public void setData(byte[] rowData) {
        this.rawData = rowData;
    }

    public void setResult(T result) {
        this.result = result;
    }

    public interface RequestListener<T> {

        void onSuccess(T result);

        void onError(VolleyError error);

    }
}

```


现在，有了 请求的基类 `Request` 和  响应的类 `Response`， 就可以开始具体工作流程了。


大体流程如下：

1. 创建一个 `Request` 对象, 指定要请求所需的各种数据， url， 方法， 回调方法等。
2. 将这个 Request 对象 add 到队列中。 
3. 由网络处理线程进行网络请求的处理， 并拿到数据返回。
4. 交给用户再处理（比如更新UI）。

我们一起看看是如何完成的。

`Request` 有了，我们来看请求队列。


# RequestQueue

我们在 `RequestQueue` 这个类中维护了一个阻塞队列，`PriorityBlockingQueue<Request<?>> blockingQueue = new PriorityBlockingQueue<>();`, 用于存放想要执行的请求。

在我们需要请求的时候， 就把构建好的 `Request` 对象丢到这个队列中。

然后网络处理线程会不断的从这个请求队列中取出 Request 对象，并去进行真正的网络请求。

```java
package com.fan.simplevolley.volley;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * @Description: 请求队列封装
 * @Author: fan
 * @Date: 2020/3/5 23:34
 * @Modify:
 */
public class RequestQueue {

    /**
     * 阻塞的请求队列
     */
    PriorityBlockingQueue<Request<?>> blockingQueue = new PriorityBlockingQueue<>();

    /**
     * 每一个请求的序列号生成器
     */
    AtomicInteger sequenceNumberGenerator = new AtomicInteger();

    /**
     * 默认执行网络请求的线程数
     */
    public static final int DEFAULT_NETWORK_THREAD_POOL_SIZE = 4;

    /**
     * 自己维护的网络请求线程
     */
    NetworkDispatcher[] networkDispatchers;

    /**
     * 真正执行网络请求的东东
     */
    HttpStack httpStack;

    /**
     * constructor
     *
     * @param threadPoolSize
     * @param httpStack
     */
    public RequestQueue(int threadPoolSize, HttpStack httpStack) {
        networkDispatchers = new NetworkDispatcher[DEFAULT_NETWORK_THREAD_POOL_SIZE];
        this.httpStack = httpStack != null ? httpStack : new UrlHttpStack();
    }


    /**
     * add的时候还要处理优先级等相关内容。这里没考虑。
     */
    public void add(Request<?> request) {
        if (!blockingQueue.contains(request)) {
            blockingQueue.add(request);
        } else {

            System.out.println("请求已经在队列中, 请不要重复add");
        }
    }

    public void start() {

        stop();

        for (int i = 0; i < DEFAULT_NETWORK_THREAD_POOL_SIZE; i++) {
            networkDispatchers[i] = new NetworkDispatcher(blockingQueue, httpStack);
            networkDispatchers[i].start();
        }

    }

    public void stop() {

        if (networkDispatchers!= null)
        for (int i = 0; i < networkDispatchers.length; i++) {
            if (networkDispatchers[i] != null) {
                networkDispatchers[i].quit();
            }
        }
    }
}

```

下面来看网络处理线程。


# NetworkDispatcher

它的本质是一个 `Thread`，在该工作线程中（死循环），不断地从队列中取出 Request 对象并用 `HttpStack`去真正执行请求。

```java
package com.fan.simplevolley.volley;

import java.util.concurrent.BlockingQueue;

/**
 * @Description: 网络请求线程, 其主要工作是:
 * 1. 从请求队列中取出Request(需要持有RequestQueue 中的 blockingQueue 的引用)
 * 2. 使用HttpStack执行网络请求并拿到响应结果 (需要持有HttpStack的引用)
 * 3. 将响应结果投递要UI线程进行处理 (需要一个投递者Delivery)
 * @Author: fan
 * @Date: 2020/3/5 23:34
 * @Modify:
 */
public class NetworkDispatcher extends Thread {

    /**
     * 是否退出
     */
    boolean quit;

    /**
     * 真正执行网络请求的
     */
    HttpStack httpStack;

    /**
     * 存放请求的队列
     */
    BlockingQueue<Request<?>> blockingQueue;

    /**
     * 将响应结果投递到UI线程的投递者
     */
    Delivery delivery = new Delivery();

    /**
     * constructor
     *
     * @param blockingQueue
     * @param httpStack
     */
    NetworkDispatcher(BlockingQueue<Request<?>> blockingQueue, HttpStack httpStack) {
        this.httpStack = httpStack;
        this.blockingQueue = blockingQueue;
    }


    @Override
    public void run() {
        // 死循环,  不断从阻塞队列中取出Request 去执行
        while (true) {
            Request<?> request;
            try {
                // Take a request from the queue, 如果没有的话就阻塞了.
                request = blockingQueue.take();
            } catch (InterruptedException e) {
                // We may have been interrupted because it was time to quit.
                if (quit) {
                    return;
                }
                continue;
            }

            Response response = null;
            try {

                // 调用 httpStack 去执行真正的网络请求, 此时, response 中的rawData已经存入了响应的实体
                response = httpStack.performRequest(request);

                // 方式1:
                //Object o = request.parseResponse(response);
                //response.setResult(o);

                // 方式2:
                request.parseResponse2(response);

                delivery.postResponse(request, response);
            } catch (Exception e) {
                e.printStackTrace();
                delivery.postError(request, new VolleyError(e));
            }
        }
    }

    public void quit() {
        quit = true;
        interrupt();
    }
}

```


# HttpStack


```java
package com.fan.simplevolley.volley;

/**
 * @Description: 执行网络请求的接口, 其子类需要实现performRequest()方法, 去真正的进行网络请求
 * @Author: fan
 * @Date: 2020/3/5 23:34
 * @Modify:
 */
public interface HttpStack {
    public Response<?> performRequest(Request<?> request) throws Exception;
}

```
HttpStack 是个接口, 我们构造了一个它的实现类 UrlHttpStack

# UrlHttpStack 

在`UrlHttpStack`的  `performRequest` 方法内部， 先从 `Request` 对象中读取用户设置的关于请求的各种参数，包括url， 是GET方法还是POST方法，请求头，请求的参数参数。获取到这些参数之后，将这些参数设置到 `HttpUrlConnection` 对象中，然后由 `HttpUrlConnection` 对象真正的发起请求，获取数据。

请求返回后， 我们可以拿到相应的状态码、状态描述以及响应实体等相关信息，我们用这些信息去构建一个`Response` 对象。此时， Response对象中的 状态码、状态描述、响应实体 这些数据已经赋值，只有那个存放解析后的对象 `public T result;` 还没赋值。此时我们调用  `Request` 的 `parseResponse2()` 方法，将响应实体中的二进制数据解析成需要的类型，并存入 T result; 中。

```java
package com.fan.simplevolley.volley;

import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.message.BasicStatusLine;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Set;

/**
 * @Description: 使用 URLConnection 进行网络请求的工具
 * @Author: fan
 * @Date: 2020/3/5 23:34
 * @Modify:
 */
public class UrlHttpStack implements HttpStack {
    @Override
    public Response<?> performRequest(Request<?> request) throws Exception {
        URL newURL = new URL(request.url);
        HttpURLConnection connection = (HttpURLConnection) newURL.openConnection();
        connection.setDoInput(true);
        connection.setUseCaches(false);

        Set<String> headersKeys = request.getHeaders().keySet();
        for (String headerName : headersKeys) {
            connection.addRequestProperty(headerName, request.getHeaders().get(headerName));
        }

        //request.getParams();

        Request.Method method = request.getHttpMethod();
        connection.setRequestMethod(method.toString());
        // add params
        byte[] body = request.getBody();
        if (body != null) {
            // enable output
            connection.setDoOutput(true);
            // set content type
            connection
                    .addRequestProperty("Content-Type", request.getBodyContentType());
            // write params data to connection
            DataOutputStream dataOutputStream = new DataOutputStream(connection.getOutputStream());
            dataOutputStream.write(body);
            dataOutputStream.close();
        }

//        connection.connect();

        ProtocolVersion protocolVersion = new ProtocolVersion("HTTP", 1, 1);
        int responseCode = connection.getResponseCode();
        if (responseCode == -1) {
            throw new IOException("Could not retrieve response code from HttpUrlConnection.");
        }
        // 状态行数据
        StatusLine responseStatus = new BasicStatusLine(protocolVersion,
                connection.getResponseCode(), connection.getResponseMessage());
        // 构建response
        Response<?> response = new Response(responseStatus);
        // 设置response数据
        //BasicHttpEntity entity = new BasicHttpEntity();
        InputStream inputStream = null;
        try {
            inputStream = connection.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
            inputStream = connection.getErrorStream();
        }

        int len = -1;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] n = new byte[1024];
        while ((len = inputStream.read(n)) != -1) {
            baos.write(n, 0, len);
        }
        baos.flush();

        response.setData(baos.toByteArray());
        return response;


    }
}

```
这样一次请求就完成了， 下面由 `Deliver `派发到UI线程

# Delivery

`NetworkDispatch` 对象持有一个默认的 `Delivey`，请求结束后，由 `Delivery `中的 Handler（`Handler handler = new Handler(Looper.getMainLooper());`）将请求结果 `Response` 对象派到到UI线程。

所谓派发，就是在UI线程去调用Request的回调方法。就完了。

```java
package com.fan.simplevolley.volley;

import android.os.Handler;
import android.os.Looper;

/**
 * @Description: 将响应数据从工作线程投递到UI线程的投递者。使用 Android Handler来实现
 * @Author: fan
 * @Date: 2020/3/5 23:34
 * @Modify:
 */
public class Delivery {

    Handler handler = new Handler(Looper.getMainLooper());

    public void postResponse(final Request request, final Response response) {
        handler.post(new Runnable() {
            @Override
            public void run() {

                // Deliver a normal response or error, depending.
                // 成功, 就去调用request设置好的成功回调
                // 这里deliverResponse 只是在  request.callback上又包了一层, 无其他
                request.deliverResponse(response.result);
            }
        });
    }

    public void postError(final Request request, final VolleyError error) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                request.deliverError(error);
            }
        });
    }
}

```


# SimpleVolley
网络请求的主体流程完了，最后我们要想办法把 构建好的 Request 加到 请求队列， 参照Volley

在SimpleVolley中， 创建一个单例的RequestQueue。
```java
package com.fan.simplevolley.volley;

/**
 * @Description: 这是一个简单的 volley 网络请求框架的实现。 我称它为 simple-volley。
 * @Author: fan
 * @Date: 2020/3/5 23:34
 * @Modify:
 */
public class SimpleVolley {

    /**
     * 创建一个 RequestQueue, 类似 Volley.newRequestQueue();
     * @return
     */
    public static RequestQueue newRequestQueue() {
        return newRequestQueue(RequestQueue.DEFAULT_NETWORK_THREAD_POOL_SIZE);
    }

    /**
     * 创建一个请求队列,NetworkExecutor数量为coreNums
     *
     * @param coreNums
     * @return
     */
    public static RequestQueue newRequestQueue(int coreNums) {
        return newRequestQueue(coreNums, null);
    }

    public static RequestQueue newRequestQueue(int coreNum, HttpStack httpStack) {

        RequestQueue requestQueue = new RequestQueue(coreNum, httpStack);
        requestQueue.start();
        return requestQueue;
    }
}

```


具体使用：

```java
    // 创建一个RequestQueue, 对应 Volley.newRequestQueue();
    RequestQueue requestQueue = SimpleVolley.newRequestQueue();

    StringRequest request = new StringRequest(Request.Method.GET, "http://www.baidu.com", new Response.RequestListener<String>() {
                    @Override
                    public void onSuccess(String result) {
                        System.out.println(result);
                        resultTextView.setText(result);
                    }

                    @Override
                    public void onError(VolleyError error) {
                        System.out.println(error.getMessage());
                        resultTextView.setText(error.getMessage());
                    }
                });
                requestQueue.add(request);
```


[源码地址github](https://github.com/fanshanhong/simple-volley)