---
title: 2017-7-12 HttpClient
tags: HttpClient
grammar_cjkRuby: true
---



# 0.简要介绍
---

* HttpComponents 包括 HttpCore包和HttpClient包 
* HttpClient：用于http请求的执行
* DefaultHttpClient：httpClient默认实现
* HttpPost、HttpGet：POST GET 方法的执行类
* HttpResponse：http请求返回的结果， 包括请求结果实体HttpEntity 和 头信息header
* HttpEntity：http请求返回的结果， 不包含header信息

# 1.基础内容

## 1.1Http Request

### 1.1.1请求的URI
---

请求的URI是统一资源定位符，它标识了应用于哪个请求之上的资源。HTTP请求URI包含一个协议模式，主机名称，可选的端口，资源路径，可选的查询和可选的片段。

```java

	HttpGet httpGet = new HttpGet("http://www.google.com");
	
```

HttpClient提供很多工具方法来简化创建和修改执行URI。URI也可以编程来拼装：

```java

	URIBuilder builder = new URIBuilder();
	builder.setScheme("http");            // http://
	builder.setHost("www.baidu.com");     // www.baidu.com
	builder.setPath("/s");                // /s
	builder.setFragment("foo");           // #foo
	builder.setQuery("wd=查询测试");       // ?wd=查询测试
	// http://www.baidu.com/s?wd=查询测试#foo
	System.out.println(builder.build());

```

查询字符串也可以从独立的参数中来生成：

```java

	List<NameValuePair> params = new ArrayList<NameValuePair>();
	params.add(new BasicNameValuePair("wd", "测试查询"));
	URI uri = new URI("http://www.baidu.com/s?"+ URLEncodedUtils.format(params, "GBK"));
	HttpGet httpget = new HttpGet(uri);
	System.out.println(httpget.getURI());
	
```

### 1.1.2 HTTP请求拦截器
---
一般不用



## 1.2Http Response
---

### 1.2.1 HTTP响应

HTTP响应是由服务器在接收和解释请求报文之后返回发送给客户端的报文。响应报文的第一行包含了协议版本，之后是数字状态码和相关联的文本段。

```java

		HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,HttpStatus.SC_OK, "OK");
		System.out.println(response.getProtocolVersion());
		System.out.println(response.getStatusLine().getStatusCode());
		System.out.println(response.getStatusLine().getReasonPhrase());
		System.out.println(response.getStatusLine());

```
输出：
HTTP/1.1
200
OK
HTTP/1.1 200 OK

response.getStatusLine() 返回StatusLine对象，StatusLine中包含一个ProtocolVersion类型的对象， 一个StatusCode 和 一个String类型的ReasonPhrase。
* ProtocolVersion：Http版本协议， 现在一般都是用1.1版本
* StatusCode：数字状态码， 可以作为请求是否成功的标志， 一般200表示成功。
* ReasonPhrase：相关联的文本段， 可以作为请求出错的原因。

### 1.2.2 响应控制器
---

控制响应的最简便和最方便的方式是使用ResponseHandler接口。这个放完完全减轻了用户关于连接管理的担心。当使用ResponseHandler时，HttpClient将会自动关注并保证释放连接到连接管理器中去，而不管请求执行是否成功或引发了异常。

```java
		HttpGet httpGet = new HttpGet("http://localhost/");
		ResponseHandler<byte[]> handler = new ResponseHandler<byte[]>() {
				public byte[] handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
						HttpEntity entity = response.getEntity();
						if (entity != null) {
								return EntityUtils.toByteArray(entity);
						} else {
								return null;
						}
				}
		};
		byte[] response = client.execute(httpGet, handler);
```

## 1.3 Http Entity
---

HTTP报文可以携带和请求或响应相关的内容实体。实体可以在一些请求和响应中找到，因为它们也是可选的。使用了实体的请求被称为封闭实体请求。HTTP规范定义了两种封闭实体的方法：POST和PUT。响应通常期望包含一个内容实体。这个规则也有特例，比如HEAD方法的响应和204 NoContent，304 Not Modified和205 Reset Content响应。


### 1.3.1 使用HTTP实体
---

 一个实体Entity既可以代表二进制内容又可以代表字符内容，同时也支持字符编码。 要从实体中读取内容，可以通过HttpEntity#getContent()方法从输入流中获取，这会返回一个Java.io.InputStream对象； 想要向实体中写入数据， 提供一个输出流到HttpEntity#writeTo(OutputStream)方法中。
 
当拿到一个实体时，HttpEntity#getContentType()方法和 HttpEntity#getContentLength()方法可以用来读取通用的元数据。
HttpEntity#getContentType()方法用于获取header中的Content-Type
HttpEntity#getContentLength()方法用于获取header中的Content-Length头部信息（如果它们是可用的）。
头部信息Content-Type可以包含对文本MIME类型的字符编码，比如text/plain或text /html，HttpEntity#getContentEncoding()方法用来读取这个信息。
如果头部信息不可用，那么就返回长度-1，而对于内容类型返回NULL。
如果头部信息Content-Type是可用的，那么getContentType就会返回一个Header对象， getContentEncoding也是返回一个Header对象。

```java

		StringEntity entity =new StringEntity("important message","UTF-8");
		System.out.println(entity.getContentType());
		System.out.println(entity.getContentLength());
		System.out.println(ContentType.getOrDefault(entity));
		System.out.println(EntityUtils.toString(entity));
		System.out.println(EntityUtils.toByteArray(entity).length);
```

输出：
Content-Type: text/plain; charset=UTF-8
17
text/plain; charset=UTF-8
important message
17

Entity也有一些set方法

```java
		entity.setContent(inputStream);
		entity.setContentLength(connection.getContentLength());
		entity.setContentEncoding(connection.getContentEncoding());
		entity.setContentType(connection.getContentType());
```



### 1.3.2 确保低级别资源释放
---

当完成一个响应实体，那么保证所有实体内容已经被完全消耗是很重要的，这样连接可以安全的放回到连接池中，而且可以通过连接管理器对后续的请求重用连接。处理这个操作的最方便的方法是调用HttpEntity#consumeContent()方法来消耗流中的任意可用内容。HttpClient探测到内容流尾部已经到达后，会立即会自动释放低层连接，并放回到连接管理器。HttpEntity#consumeContent()方法调用多次也是安全的。

也可能会有特殊情况，当整个响应内容的一小部分需要获取，消耗剩余内容而损失性能，还有重用连接的代价太高，则可以仅仅通过调用HttpUriRequest#abort()方法来中止请求。

```java
		HttpGet httpGet =new HttpGet("http://www.baidu.com");
		HttpResponse response = client.execute(httpGet);
		HttpEntity entity = response.getEntity();
		if (entity !=null) {
				InputStream instream = entity.getContent();
				int byteOne = instream.read();
				int byteTwo = instream.read();
				// Do not need the rest
				httpGet.abort();
		}
```
连接不会被重用，但是由它持有的所有级别的资源将会被正确释放。

### 1.3.3 获取实体内容
---

获取实体内容的方式是使用它的HttpEntity#getContent()方法。
HttpClient也自带EntityUtils类，这些方法可以更加容易地从实体中读取内容或信息，可以使用这个类中的方法以字符串/字节数组的形式获取整个内容体。然而，EntityUtils的使用是强烈不鼓励的，除非响应实体源自可靠的HTTP服务器和已知的长度限制。
 
 ```java
		 HttpGet httpGet =new HttpGet("http://localhost/");
		HttpResponse response = client.execute(httpGet);
		HttpEntity entity = response.getEntity();
		if (entity !=null) {
				long len = entity.getContentLength();
				if (len != -1 && len < 2048) {
						System.out.println(EntityUtils.toString(entity));
				} else {
						// Stream content out
				}
		}
 ```
 
在一些情况下可能会不止一次的读取实体。此时实体内容必须以某种方式在内存或磁盘上被缓冲起来。最简单的方法是通过使用BufferedHttpEntity类来包装源实体完成。这会引起源实体内容被读取到内存的缓冲区中。在其它所有方式中，实体包装器将会得到源实体。


```java

		HttpGet httpGet =new HttpGet("http://localhost/");
		HttpResponse response = client.execute(httpGet);
		HttpEntity entity = response.getEntity();
		if (entity !=null) {
				entity = new BufferedHttpEntity(entity);
		}

```

###  1.3.4 生成实体内容
---

HttpClient提供一些类，它们可以用于生成通过HTTP连接获得内容的有效输出流。为了封闭实体从HTTP请求中获得的输出内容，那些类的实例可以和封闭如POST和PUT请求的实体相关联。HttpClient为很多公用的数据容器，比如字符串，字节数组，输入流和文件提供了一些类：StringEntity，ByteArrayEntity，InputStreamEntity和FileEntity。
  

```java
		File file =new File("somefile.txt");
		ContentType contentType = ContentType.create("text/plain","UTF-8");
		FileEntity entity =new FileEntity(file, contentType);
		HttpPost httpPost =new HttpPost("http://localhost/action.do");
		httpPost.setEntity(entity);
```

### 1.3.5 动态内容实体
---
通常来说，HTTP实体需要基于特定的执行上下文来动态地生成。通过使用EntityTemplate实体类和ContentProducer接口，HttpClient提供了动态实体的支持。内容生成器是按照需求生成它们内容的对象，将它们写入到一个输出流中。它们是每次被请求时来生成内容。所以用EntityTemplate创建的实体通常是自我包含而且可以重复的。
 
```java
		ContentProducer cp =new ContentProducer() {
				public void writeTo(OutputStream outstream) throws IOException {
						Writer writer = new OutputStreamWriter(outstream,"UTF-8");
								writer.write("<response>");
								writer.write(" <content>");
								writer.write(" important stuff");
								writer.write(" </content>");
								writer.write("</response>");
								writer.flush();
						}
				};
		HttpEntity entity =new EntityTemplate(cp);
		HttpPost httppost =new HttpPost("http://localhost/handler.do");
		httppost.setEntity(entity);
```

## 1.4 上下文

# 2.基础GET方法
---

```java
        // 默认的client类。  
        HttpClient client = new DefaultHttpClient();
        // 设置为get取连接的方式.  
        HttpGet get = new HttpGet(url);
        // 得到返回的response.  
        HttpResponse response = client.execute(get);
        // 得到返回的client里面的实体对象信息.  
        HttpEntity entity = response.getEntity();
        if (entity != null) {
		
				// entity.getContentEncoding()获取编码方式， 返回Header对象
				System.out.println(entity.getContentEncoding());
				// 获取header中的Content-Type， 返回Header对象
            	System.out.println(entity.getContentType());
				// 得到返回的主体内容.  InputStream
				InputStream instream = entity.getContent();
				BufferedReader reader = new BufferedReader(new InputStreamReader(instream, encoding));
				System.out.println(reader.readLine());
				// EntityUtils 处理HttpEntity的工具类  
				// System.out.println(EntityUtils.toString(entity));  
        }
        
        // 关闭连接.  
        client.getConnectionManager().shutdown();
```

# 3.基础POST方法
---

```java
		// DefaultHttpClient是默认的HttpClient实现类
        DefaultHttpClient httpclient = new DefaultHttpClient();
        HttpPost httpost = new HttpPost(url);
        // POST请求添加参数， 使用BasicNameValuePair， 然后setEntity即可  
        List<NameValuePair> formparams = new ArrayList<NameValuePair>();
        formparams.add(new BasicNameValuePair("p", "1"));
        formparams.add(new BasicNameValuePair("t", "2"));
        formparams.add(new BasicNameValuePair("e", "3"));

        UrlEncodedFormEntity urlEntity =  new UrlEncodedFormEntity(formparams, "UTF-8");
        httpost.setEntity(urlEntity);
		
		// 执行请求
        HttpResponse response = httpclient.execute(httpost);
        HttpEntity entity = response.getEntity();
		
		// 工具类EntityUtils将entity转成string处理
		EntityUtils.toString(entity);
		
        System.out.println("Post login cookies:");
		// 拿到响应的cookie， 是个List
        List<Cookie> cookies = httpclient.getCookieStore().getCookies();
        for (int i = 0; i < cookies.size(); i++) {
            System.out.println("- " + cookies.get(i).toString());
        }
        // 关闭请求  
        httpclient.getConnectionManager().shutdown();
```



# 4.保留Session, 第二次请求带上第一次请求的Cookie 
---

```java
DefaultHttpClient httpclient = new DefaultHttpClient();  
        HttpPost httpost = new HttpPost(url);  
         // POST请求添加参数， 使用BasicNameValuePair， 然后setEntity即可  
        List<NameValuePair> formparams = new ArrayList<NameValuePair>();  
        formparams.add(new BasicNameValuePair("uname", name));  
        formparams.add(new BasicNameValuePair("pass", "e0c10f451217b93f76c2654b2b729b85"));  
   
        UrlEncodedFormEntity urlEntity =  new UrlEncodedFormEntity(formparams, "UTF-8");  
        httpost.setEntity(urlEntity);  
        HttpContext localContext = new BasicHttpContext();  
  
 		// 执行请求
        HttpResponse response = httpclient.execute(httpost,localContext);  
		
		// 拿到响应的实体
        HttpEntity entity = response.getEntity();  
        // 打印获取值  ， 这里getAllHeaders是获取所有的header，拿到的是Header数组
		// Header headers[] = response.getAllHeaders();
        // while (i < headers.length) { // 遍历拿到每一个Header的内容
        //   System.out.println(headers[i].getName() + ":  " + headers[i].getValue());
        //     i++;
        //}
        System.out.println(Arrays.toString(response.getAllHeaders()));  
   
    	// 获取这一次请求拿到的Cookie  ，是一个List  
		CookieStore cookieStore = httpclient.getCookieStore();  
    	List<Cookie> list = cookieStore.getCookies(); 
   
        // 第二次请求，使用上一次请求的Cookie  
        DefaultHttpClient httpclient2 = new DefaultHttpClient();  
        HttpPost httpost2 = new HttpPost("http://my.ifeng.com/?_c=index&_a=my");  
       
        // 先拿到存储cookie用的CookieStore，然后将使用上一次请求 拿到的cookie全装进去
        CookieStore cookieStore2 = httpclient2.getCookieStore();  
		cookieStore2.clear();
        for(Cookie o : list){  
            cookieStore2.addCookie(o);  
        }  
   	
		// 执行第二次请求
        HttpResponse response2 = httpclient2.execute(httpost2);  
        HttpEntity entity2 = response2.getEntity();  
        System.out.println(Arrays.toString(response2.getAllHeaders()));  
        System.out.println(EntityUtils.toString(entity2));  
```

# 5.获取访问上下文
---

```java
	
		// 创建一个默认的HttpClient
		HttpClient httpclient = new DefaultHttpClient();
        // 设置为get取连接的方式.  
        HttpGet get = new HttpGet(url);
		// 创建上下文
        HttpContext localContext = new BasicHttpContext();
        // 调用execute方法， 执行请求，得到返回的response
		// 第一个参数，是HttpGet、HttpPost的对象， 即要执行的GET、POST请求
		// 第二个参数，是上下文，很好的一个参数！  
       HttpResponse response = httpclient.execute(get, localContext);

        // 从上下文中得到HttpConnection对象  
        HttpConnection con = (HttpConnection) localContext .getAttribute(ExecutionContext.HTTP_CONNECTION);
        System.out.println("socket超时时间：" + con.getSocketTimeout());

        // 从上下文中得到HttpHost对象  
        HttpHost target = (HttpHost) localContext.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
        System.out.println("最终请求的目标:" + target.getHostName() + ":"+ target.getPort());

        // 从上下文中得到代理相关信息.  
        HttpHost proxy = (HttpHost) localContext.getAttribute(ExecutionContext.HTTP_PROXY_HOST);
        if (proxy != null)
            System.out.println("代理主机的目标:" + proxy.getHostName() + ":"+ proxy.getPort());

        System.out.println("是否发送完毕:"+ localContext.getAttribute(ExecutionContext.HTTP_REQ_SENT));

        // 从上下文中得到HttpRequest对象  
        HttpRequest request = (HttpRequest) localContext.getAttribute(ExecutionContext.HTTP_REQUEST);
        System.out.println("请求的版本:" + request.getProtocolVersion());
        Header[] headers = request.getAllHeaders();
        System.out.println("请求的头信息: ");
        for (Header h : headers) {
            System.out.println(h.getName() + "--" + h.getValue());
        }
        System.out.println("请求的链接:" + request.getRequestLine().getUri());

        // 从上下文中得到HttpResponse对象  
        HttpResponse response = (HttpResponse) localContext.getAttribute(ExecutionContext.HTTP_RESPONSE);
        HttpEntity entity = response.getEntity();
        if (entity != null) {
				System.out.println("返回结果内容编码是：" + entity.getContentEncoding());
				System.out.println("返回结果内容类型是：" + entity.getContentType());
        }
```


# 6.连接池和代理
---

```java

        // HttpParams   
        HttpParams httpParams  = new BasicHttpParams();    
        // HttpConnectionParams 设置连接参数  
         // 设置连接超时时间    
        HttpConnectionParams.setConnectionTimeout(httpParams, 30000);    
        // 设置读取超时时间    
        HttpConnectionParams.setSoTimeout(httpParams, 60000);   
  
        SchemeRegistry schemeRegistry = new SchemeRegistry();  
        schemeRegistry.register(  
                 new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));  
		//      schemeRegistry.register(  
		//               new Scheme("https", 443, SSLSocketFactory.getSocketFactory()));  
        PoolingClientConnectionManager cm = new PoolingClientConnectionManager(schemeRegistry);  
        // 设置最大连接数    
        cm.setMaxTotal(200);  
        // 设置每个路由默认最大连接数    
        cm.setDefaultMaxPerRoute(20);  
		//      // 设置代理和代理最大路由  
		//      HttpHost localhost = new HttpHost("locahost", 80);  
		//      cm.setMaxPerRoute(new HttpRoute(localhost), 50);  
        // 设置代理，  
        HttpHost proxy = new HttpHost("10.36.24.3", 60001);  
        httpParams.setParameter(ConnRoutePNames.DEFAULT_PROXY,  proxy);  
          
        HttpClient httpClient = new DefaultHttpClient(cm, httpParams);  
		
```

# 7.自动重连
---

```java
        DefaultHttpClient httpClient = new DefaultHttpClient();
        // 可以自动重连  
        HttpRequestRetryHandler requestRetryHandler = new HttpRequestRetryHandler() {
            // 自定义的恢复策略 （里面定义了在何种情况进行重连的算法）   
            public synchronized boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
                // 设置恢复策略，在发生异常时候将自动重试3次    
                if (executionCount > 3) {
                    // 超过最大次数则不需要重试      
                    return false;
                }
                if (exception instanceof NoHttpResponseException) {
                    // 服务停掉则重新尝试连接      
                    return true;
                }
                if (exception instanceof SSLHandshakeException) {
                    // SSL异常不需要重试      
                    return false;
                }
				
                HttpRequest request = (HttpRequest) context.getAttribute(ExecutionContext.HTTP_REQUEST);
                boolean idempotent = (request instanceof HttpEntityEnclosingRequest);
                if (!idempotent) {
                    // 请求内容相同则重试    
                    return true;
                }
                return false;
            }
        };
		
		//  设置自动重连的Handler
        httpClient.setHttpRequestRetryHandler(requestRetryHandler);
```

# 8.使用自定义ResponseHandler处理返回的请求
---


```java

        HttpClient httpClient = new DefaultHttpClient();
        HttpGet get = new HttpGet(url);
        // 定义一个类处理URL返回的结果  
        ResponseHandler<byte[]> handler = new ResponseHandler<byte[]>() {
            public byte[] handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
					//拿到响应实体
					HttpEntity entity = response.getEntity();
					if (entity != null) {
							// 使用EntityUtils工具类， 转成byte[]
							return EntityUtils.toByteArray(entity);
					} else {
							return null;
					}
            }
        };
        // 不同于 httpClient.execute(request)，返回值是HttpResponse；
		// 返回值由 execute方法中的第二个参数ResponseHandler。 handleResponse的返回值为byte[]， 因此， execute的返回值就是byte[]
		// 相当于请求返回后， 先由Handler进行拦截处理， 处理之后再由execute返回
        byte[] charts = httpClient.execute(get, handler);
        FileOutputStream out = new FileOutputStream(fileName);
        out.write(charts);
        out.close();

		// 断开连接
        httpClient.getConnectionManager().shutdown();

```


