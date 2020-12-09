---
title: URLConnection参数详解

date: 2017-7-12

categories: 
   - Android
   - 性能优化

tags: 
   - Android 
   - UrlConnection 

description: ​
---

<!-- TOC -->

- [1.URLConnection的对象问题](#1urlconnection的对象问题)
- [2. HttpURLConnection对象参数问题](#2-httpurlconnection对象参数问题)
- [3.HttpUrlConnection 连接问题](#3httpurlconnection-连接问题)
- [4.HttpURLConnection写数据与发送数据问题](#4httpurlconnection写数据与发送数据问题)
- [5.超时设置](#5超时设置)
- [6.总结](#6总结)

<!-- /TOC -->


# 1.URLConnection的对象问题
---

```java
		URL url = new URL("http://localhost:8080/TestHttpURLConnectionPro/index.jsp"); 

		URLConnection urlConnection = url.openConnection();
		// 此处的urlConnection对象实际上是根据URL的 请求协议(此处是http)生成的URLConnection类的子类HttpURLConnection
		// 故此处最好将其转化 为HttpURLConnection类型的对象,以便用到 HttpURLConnection更多的API.如下: 

		HttpURLConnection httpUrlConnection = (HttpURLConnection) urlConnection; 
```


# 2. HttpURLConnection对象参数问题
---

```java
		// 设置是否向httpUrlConnection输出，因为这个是post请求，参数要放在 
		// http正文内，因此需要设为true, 默认情况下是false; 
		httpUrlConnection.setDoOutput(true); 

		// 设置是否从httpUrlConnection读入，默认情况下是true; 
		httpUrlConnection.setDoInput(true); 

		// Post 请求不能使用缓存 
		httpUrlConnection.setUseCaches(false); 

		// 使用setRequestProperty可以设置一些属性， 比如头信息， 比如请求的方法

		// 设定传送的内容类型是可序列化的java对象 
		// (如果不设此项,在传送序列化对象时,当WEB服务默认的不是这种类型时可能抛java.io.EOFException) 
		httpUrlConnection.setRequestProperty("Content-type", "application/x-java-serialized-object"); 

		// 设定请求的方法为"POST"，默认是GET 
		httpUrlConnection.setRequestMethod("POST"); 

		// 连接，从上述第2条中url.openConnection()至此的配置必须要在connect之前完成， 
		// connect 是真正建立连接， 不过也只是建立连接， 不发送数据
		httpUrlConnection.connect(); 
```


# 3.HttpUrlConnection 连接问题
---

```java

		// 此处getOutputStream会隐含的进行connect
		// 所以在开发中不调用上述的connect()也可以)
		// 因为要向服务器发送数据， 肯定要在建立连接的基础上， 因此在getOutputStream方法内部会调用connect建立连接
		OutputStream outStrm = httpUrlConnection.getOutputStream(); 
		
```

# 4.HttpURLConnection写数据与发送数据问题
---

```java

			OutputStream outStrm = httpUrlConnection.getOutputStream();

			// 现在通过输出流对象构建对象输出流对象，以实现输出可序列化的对象。 
			ObjectOutputStream objOutputStrm = new ObjectOutputStream(outStrm); 

			// 向对象输出流写出数据，这些数据将存到内存缓冲区中 
			objOutputStrm.writeObject(new String("我是测试数据")); 

			// 刷新对象输出流，将任何字节都写入潜在的流中（此处为ObjectOutputStream） 
			objOutputStm.flush(); 

			// 关闭流对象。此时，不能再向对象输出流写入任何数据，先前写入的数据存在于内存缓冲区中, 
			// 在调用下边的getInputStream()函数时才把准备好的http请求正式发送到服务器 
			objOutputStm.close(); 

			// 调用HttpURLConnection连接对象的getInputStream()函数, 
			// 将内存缓冲区中封装好的完整的HTTP请求报文发送到服务端。 
			InputStream inStrm = httpConn.getInputStream(); // <===注意，实际发送请求的代码段就在这里   
			//  getInputStream方法内部也会调用connect， 因为有时候不需要向ouputStream中写入数据， 直接通过URL就可以带参数， 这时直接调用getInputStream就可以保证先建立连接， 然后发送请求， 并且获取返回数据

			// 上边的httpConn.getInputStream()方法已调用,本次HTTP请求已结束,下边向对象输出流的写入数据已无意义， 
			// 既使对象输出流没有调用close()方法，下边的操作也不会向对象输出流写入任何数据. 
			// 因此，要重新发送数据时需要重新创建连接、重新设参数、重新创建流对象、重新写数据、 
			// 重新发送数据(至于是否不用重新这些操作需要再研究) 
			objOutputStm.writeObject(new String("")); 
			httpConn.getInputStream(); 

```

# 5.超时设置
---

HttpURLConnection是基于HTTP协议的，其底层通过socket通信实现。如果不设置超时（timeout），在网络异常的情况下，可能会导致程序僵死而不继续往下执行。可以通过以下两个语句来设置相应的超时：

```java

		urlConn.setConnectTimeout(30000); // 连接超时
		urlConn.setReadTimeout(30000); // 读取超时

```


# 6.总结
---

* HttpURLConnection的connect()函数，实际上只是建立了一个与服务器的tcp连接，并没有实际发送http请求。 无论是post还是get，http请求实际上直到HttpURLConnection的getInputStream()这个函数里面才正式发送出去。 
* 在用POST方式发送URL请求时，URL请求参数的设定顺序是重中之重，对connection对象的一切配置（那一堆set函数）都必须要在connect()函数执行之前完成。
  而对outputStream的写操作，又必须要在inputStream的读操作之前。这些顺序实际上是由http请求的格式决定的。 
  如果inputStream读操作在outputStream的写操作之前，会抛出异常： 
  java.net.ProtocolException: Cannot write output after reading input....... 
*  http请求实际上由两部分组成， 
    一个是http头，所有关于此次http请求的配置都在http头里面定义，  一个是正文content（也就是body）。 
    connect()函数会根据HttpURLConnection对象的配置值生成http头部信息，因此在调用connect函数之前， 就必须把所有的配置准备好， 一般是调用setRequestProperty来设置Header信息。 
* 在http头后面紧跟着的是http请求的正文（body），正文的内容是通过outputStream流写入的（先conn.getOutputStream获取到输出流， 然后向里面写入）， 
  实际上outputStream不是一个网络流，充其量是个字符串流，往里面写入的东西不会立即发送到网络，  而是存在于内存缓冲区中，待outputStream流关闭时（调用close），根据输入的内容生成http正文（body）。 
  至此，http请求的东西已经全部准备就绪。在getInputStream()函数调用的时候，就会把准备好的http请求 正式发送到服务器了，然后返回一个输入流，用于读取服务器对于此次http请求的返回信息。
  由于http请求在getInputStream的时候已经发送出去了（包括http头和正文），因此在getInputStream()函数 之后对connection对象进行设置（对http头的信息进行修改）或者写入outputStream（对正文进行修改） 都是没有意义的了，执行这些操作会导致异常发     生。 




