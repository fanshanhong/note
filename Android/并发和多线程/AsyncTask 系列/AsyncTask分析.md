



子线程执行， 主线程能够拿到子线程返回的数据



java 中，  run 方法， 无返回值，  那么如果拿到返回值；





Callable



Future 接口





Callable 传给 FutureTask 去执行



一定要先弄明白  Callable 和 Future 接口







子线程执行的结果， 主线程是拿不到



要用 FutureTask 来解决



FutureTask<>





Callable 中的泛型， 是子线程要返回的值的类型



```java
class Task imp Callable<Integer>{
  // 返回异步任务的执行结果
  Integer call(){
    return 333;
  }
}
```





Callable 需要用 FutureTask 来包装



FutureTask 要用 execute 去执行



exec = Excutors.newCachedThreadPool();

Exec.execute(task);



future.get*();// 获取异步任务的返回值



什么时候才能拿到？？



call 方法里面的任务执行完了， 才能拿到结果

所以 get()  方法是阻塞的， 等待call 执行完毕了才会返回





FutureTask 有几个异步回调方法



done()



FutureTask 源码分析， 里面是如何实现 done 的？？





因此在 AsyncTask 中，    call 里面肯定会调用 doInbackground

onPost里面一定在done 中执行



cancel

如何取消异步任务？   future.cancel()// 取消异步任务





FutureTask

1.获取异步任务返回值

2.监听异步任务执行完毕

3.取消异步任务



LinkingBlockedQueue



当任务队列中的任务都没执行完， 再添加新的， 就会 reject





如果当前线程池中的数量小于corePoolSize，创建并添加的任务。
如果当前线程池中的数量等于corePoolSize，缓冲队列 workQueue未满，那么任务被放入缓冲队列、等待任务调度执行。
如果当前线程池中的数量大于corePoolSize，缓冲队列workQueue已满，并且线程池中的数量小于maximumPoolSize，新提交任务会创建新线程执行任务。
如果当前线程池中的数量大于corePoolSize，缓冲队列workQueue已满，并且线程池中的数量等于maximumPoolSize，新提交任务由Handler处理。
当线程池中的线程大于corePoolSize时，多余线程空闲时间超过keepAliveTime时，会关闭这部分线程。	





串行是神马？将 Task 添加到线程池的时候是串行的。execute 方法的时候

执行是并行的。



1.线程池容量不够用

自定义线程池

2.内存泄漏， 要记得 cancel