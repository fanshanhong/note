---

title: 把AsyncTask原理说明白

date: 2018-06-08 

categories: 

   - Android
   - 并发和多线程
   - AsyncTask 

tags: 

   - Android 
   - AsyncTask 

description: ​

---

<!-- TOC -->

- [把 AsyncTask 原理说明白](#把-asynctask-原理说明白)
    - [AsyncTask使用介绍](#asynctask使用介绍)

<!-- /TOC -->



# 把 AsyncTask 原理说明白



## AsyncTask使用介绍

`AsyncTask`类属于抽象类，即使用时需 实现子类





1. 创建 `AsyncTask` 子类 & 根据需求实现核心方法
2. 创建 `AsyncTask`子类的实例对象（即 任务实例）
3. 手动调用`execute(（）`从而执行异步线程任务





```java
/**
  * 步骤1：创建AsyncTask子类
  * 注： 
  *   a. 继承AsyncTask类
  *   b. 为3个泛型参数指定类型；若不使用，可用java.lang.Void类型代替
  *   c. 根据需求，在AsyncTask子类内实现核心方法
  */

  private class MyTask extends AsyncTask<Params, Progress, Result> {

        ....

      // 方法1：onPreExecute（）
      // 作用：执行 线程任务前的操作
      // 注：根据需求复写
      @Override
      protected void onPreExecute() {
           ...
        }

      // 方法2：doInBackground（）
      // 作用：接收输入参数、执行任务中的耗时操作、返回 线程任务执行的结果
      // 注：必须复写，从而自定义线程任务
      @Override
      protected String doInBackground(String... params) {

            ...// 自定义的线程任务

            // 可调用publishProgress（）显示进度, 之后将执行onProgressUpdate（）
             publishProgress(count);
              
         }

      // 方法3：onProgressUpdate（）
      // 作用：在主线程 显示线程任务执行的进度
      // 注：根据需求复写
      @Override
      protected void onProgressUpdate(Integer... progresses) {
            ...

        }

      // 方法4：onPostExecute（）
      // 作用：接收线程任务执行结果、将执行结果显示到UI组件
      // 注：必须复写，从而自定义UI操作
      @Override
      protected void onPostExecute(String result) {

         ...// UI操作

        }

      // 方法5：onCancelled()
      // 作用：将异步任务设置为：取消状态
      @Override
        protected void onCancelled() {
        ...
        }
  }

/**
  * 步骤2：创建AsyncTask子类的实例对象（即 任务实例）
  * 注：AsyncTask子类的实例必须在UI线程中创建
  */
  MyTask mTask = new MyTask();

/**
  * 步骤3：手动调用execute(Params... params) 从而执行异步线程任务
  * 注：
  *    a. 必须在UI线程中调用 xxxxx
  *    b. 同一个AsyncTask实例对象只能执行1次，若执行第2次将会抛出异常
  *    c. 执行任务中，系统会自动调用AsyncTask的一系列方法：onPreExecute() 、doInBackground()、onProgressUpdate() 、onPostExecute() 
  *    d. 不能手动调用上述方法
  */
  mTask.execute()；
```





```java
public class MainActivity extends AppCompatActivity {

    // 线程变量
    MyTask mTask;

    // 主布局中的UI组件
    Button button,cancel; // 加载、取消按钮
    TextView text; // 更新的UI组件
    ProgressBar progressBar; // 进度条
    
    /**
     * 步骤1：创建AsyncTask子类
     * 注：
     *   a. 继承AsyncTask类
     *   b. 为3个泛型参数指定类型；若不使用，可用java.lang.Void类型代替
     *      此处指定为：输入参数 = String类型、执行进度 = Integer类型、执行结果 = String类型
     *   c. 根据需求，在AsyncTask子类内实现核心方法
     */
    private class MyTask extends AsyncTask<String, Integer, String> {

        // 方法1：onPreExecute（）
        // 作用：执行 线程任务前的操作
        @Override
        protected void onPreExecute() {
            text.setText("加载中");
            // 执行前显示提示
        }


        // 方法2：doInBackground（）
        // 作用：接收输入参数、执行任务中的耗时操作、返回 线程任务执行的结果
        // 此处通过计算从而模拟“加载进度”的情况
        @Override
        protected String doInBackground(String... params) {

            try {
                int count = 0;
                int length = 1;
                while (count<99) {

                    count += length;
                    // 可调用publishProgress（）显示进度, 之后将执行onProgressUpdate（）
                    publishProgress(count);
                    // 模拟耗时任务
                    Thread.sleep(50);
                }
            }catch (InterruptedException e) {
                e.printStackTrace();
            }

            return null;
        }

        // 方法3：onProgressUpdate（）
        // 作用：在主线程 显示线程任务执行的进度
        @Override
        protected void onProgressUpdate(Integer... progresses) {

            progressBar.setProgress(progresses[0]);
            text.setText("loading..." + progresses[0] + "%");

        }

        // 方法4：onPostExecute（）
        // 作用：接收线程任务执行结果、将执行结果显示到UI组件
        @Override
        protected void onPostExecute(String result) {
            // 执行完毕后，则更新UI
            text.setText("加载完毕");
        }

        // 方法5：onCancelled()
        // 作用：将异步任务设置为：取消状态
        @Override
        protected void onCancelled() {

            text.setText("已取消");
            progressBar.setProgress(0);

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 绑定UI组件
        setContentView(R.layout.activity_main);

        button = (Button) findViewById(R.id.button);
        cancel = (Button) findViewById(R.id.cancel);
        text = (TextView) findViewById(R.id.text);
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);

        /**
         * 步骤2：创建AsyncTask子类的实例对象（即 任务实例）
         * 注：AsyncTask子类的实例必须在UI线程中创建
         */
        mTask = new MyTask();

        // 加载按钮按按下时，则启动AsyncTask
        // 任务完成后更新TextView的文本
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                /**
                 * 步骤3：手动调用execute(Params... params) 从而执行异步线程任务
                 * 注：
                 *    a. 必须在UI线程中调用
                 *    b. 同一个AsyncTask实例对象只能执行1次，若执行第2次将会抛出异常
                 *    c. 执行任务中，系统会自动调用AsyncTask的一系列方法：onPreExecute() 、doInBackground()、onProgressUpdate() 、onPostExecute()
                 *    d. 不能手动调用上述方法
                 */
                mTask.execute();
            }
        });

        cancel = (Button) findViewById(R.id.cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 取消一个正在执行的任务,onCancelled方法将会被调用
                mTask.cancel(true);
            }
        });

    }

}
```





AsyncTask



几个问题：

1.doInBackground 为啥是子线程

2.pre  post 为啥是主线程



3.主线程是怎么拿到子线程的返回结果







分析：





基于 2.3.5 源码分析

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

3.







