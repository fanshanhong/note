/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>AsyncTask enables proper and easy use of the UI thread. 
 * AsyncTask 能够合适并且容易的使用UI线程
 * This class allows to
 * perform background operations and publish results on the UI thread without
 * having to manipulate threads and/or handlers.</p>
 * 这个类允许执行后台操作, 并且发布结果到UI线程, 且不需要操作thread 或者  handler
 * 
 * <p>An asynchronous task is defined by a computation that runs on a background thread and
 * whose result is published on the UI thread. 
 * 一个异步任务被定义为  一个 计算运行在后台线程, 并且它的结果被发布到UI线程

 * AsyncTask的类声明:public abstract class AsyncTask<Params, Progress, Result> {}
 * An asynchronous task is defined by 3 generic 一个异步任务通过三个泛型类型来定义, 分别是Params Progress Result 
 * types, called <code>Params</code>, <code>Progress</code> and <code>Result</code>,
 * and 4 steps, called <code>begin</code>, <code>doInBackground</code>,
 * <code>processProgress</code> and <code>end</code>.</p>
 *
 * <h2>Usage</h2>
 * <p>AsyncTask must be subclassed to be used. The subclass will override at least
 * one method ({@link #doInBackground}), and most often will override a
 * second one ({@link #onPostExecute}.)</p>
 *
 * <p>Here is an example of subclassing:</p>
 * <pre class="prettyprint">
 * private class DownloadFilesTask extends AsyncTask&lt;URL, Integer, Long&gt; {
 *     protected Long doInBackground(URL... urls) {
 *         int count = urls.length;
 *         long totalSize = 0;

 			// 后台串行下载多个文件
 *         for (int i = 0; i < count; i++) {
				// downloadFile返回下载的字节数
 *             totalSize += Downloader.downloadFile(urls[i]);
 				// 文件个数百分比
 *             publishProgress((int) ((i / (float) count) * 100));
 *         }
 			// 总共下载的字节数
 *         return totalSize;
 *     }
 *
 *     protected void onProgressUpdate(Integer... progress) {
 *         setProgressPercent(progress[0]);
 *     }
 *
 *     protected void onPostExecute(Long result) {
 			// 展示总共下载了多少字节
 *         showDialog("Downloaded " + result + " bytes");
 *     }
 * }
 * </pre>
 *
 * <p>Once created, a task is executed very simply:</p>
 * <pre class="prettyprint">
 * new DownloadFilesTask().execute(url1, url2, url3);
 * </pre>
 *
 * <h2>AsyncTask's generic types</h2> 三个泛型参数
 * <p>The three types used by an asynchronous task are the following:</p>
 * <ol>
 *     <li><code>Params</code>, the type of the parameters sent to the task upon
 *     execution.</li>
 *		 Params参数是当task执行的时候传入, 也就是传入doInBackground方法中
 *
 *     <li><code>Progress</code>, the type of the progress units published during
 *     the background computation.</li>
 *		在后台计算过程中, 被发布出来的进度单元
 *
 *     <li><code>Result</code>, the type of the result of the background
 *     computation.</li>
 *		后台计算的结果, 会传入onPostExecute方法中
 *		
 * </ol>
 * <p>Not all types are always used by an asynchronous task. To mark a type as unused,
 * simply use the type {@link Void}:</p>
 *	并不是所有的泛型都是必须的. 如果一个参数不需要, 就简单的写成 Void
 * 比如:
 * <pre>
 * private class MyTask extends AsyncTask&lt;Void, Void, Void&gt; { ... }
 * </pre>
 *
 * <h2>The 4 steps</h2>
 * <p>When an asynchronous task is executed, the task goes through 4 steps:</p>
 * 当一个异步任务被执行, 经历以下4个步骤
 * <ol>
 *     <li>{@link #onPreExecute()}, invoked on the UI thread immediately after the task
 *     is executed. This step is normally used to setup the task, for instance by
 *     showing a progress bar in the user interface.</li>
 *     onPresExecute方法, 当task被执行的时候, 立刻在ui线程中被调用.这一步一般被用作建立任务(初始化工作), 比如展示一个progressbar在ui界面

 *     <li>{@link #doInBackground}, invoked on the background thread
 *     immediately after {@link #onPreExecute()} finishes executing. This step is used
 *     to perform background computation that can take a long time. The parameters
 *     of the asynchronous task are passed to this step. The result of the computation must
 *     be returned by this step and will be passed back to the last step. This step
 *     can also use {@link #publishProgress} to publish one or more units
 *     of progress. These values are published on the UI thread, in the
		doInBackground方法, 在onPreExecute()方法调用完成后, 立刻在后台执行.
		这一步用来执行耗时的后台操作.参数Params会在这一步传入doInBackground方法.
		计算结果, 在这一步必须返回, 且结果会被传入下一个步骤, 也就是onPostExecute中
		在这一步中, 还可以调用publishProgress方法发布当前执行的进度, 这些进度值将会被发布到ui线程中的onProgressUpdate方法中
 
 *     {@link #onProgressUpdate} step.</li>
 *     <li>{@link #onProgressUpdate}, invoked on the UI thread after a
 *     call to {@link #publishProgress}. The timing of the execution is
 *     undefined. This method is used to display any form of progress in the user
 *     interface while the background computation is still executing. For instance,
 *     it can be used to animate a progress bar or show logs in a text field.</li>
		 在publishprogress方法调用后, onProgressUpdate方法将在ui线程中被调用.它的执行时间是不确定的.
		 该方法用来在界面上展示任何形式的进度(当后台计算还在继续执行).比如, 把一个progressbar做成动画来展示, 或者在textview中展示文本
 
 *     <li>{@link #onPostExecute}, invoked on the UI thread after the background
 *     computation finishes. The result of the background computation is passed to
 *     this step as a parameter.</li>
		当后台任务完成后, onPostExecute方法在ui线程执行.doInBackground的结果, 也就是return值, 被传入该方法
 
 * </ol>
 *
 * <h2>Threading rules</h2>线程规则
 * <p>There are a few threading rules that must be followed for this class to
 * work properly:</p>
 	为了这个类更好地工作 , 有一些线程规则必须去遵守, 
 * <ul>
 *     <li>The task instance must be created on the UI thread.</li>  这个task实例必须在ui线程创建
 *     <li>{@link #execute} must be invoked on the UI thread.</li>    execute方法, 必须在ui线程调用
 *     <li>Do not call {@link #onPreExecute()}, {@link #onPostExecute},      不要去手动调用onPreExecute  onPostExecute
 *     {@link #doInBackground}, {@link #onProgressUpdate} manually.</li>    doInbackGround  onProgressUpdate
 *     <li>The task can be executed only once (an exception will be thrown if  这个task只能被执行一次, 如果企图再次执行, 将会throw exception
 *     a second execution is attempted.)</li>
 * </ul>
 */

// 抽象类
public abstract class AsyncTask<Params, Progress, Result> {
	// LOG
    private static final String LOG_TAG = "AsyncTask";

    private static final int CORE_POOL_SIZE = 5;
    private static final int MAXIMUM_POOL_SIZE = 128;
    private static final int KEEP_ALIVE = 1;

    private static final BlockingQueue<Runnable> sWorkQueue =
            new LinkedBlockingQueue<Runnable>(10);

    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(Runnable r) {
            return new Thread(r, "AsyncTask #" + mCount.getAndIncrement());
        }
    };

	// 线程池
    private static final ThreadPoolExecutor sExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE,
            MAXIMUM_POOL_SIZE, KEEP_ALIVE, TimeUnit.SECONDS, sWorkQueue, sThreadFactory);

    private static final int MESSAGE_POST_RESULT = 0x1;// 常量, task完成
    private static final int MESSAGE_POST_PROGRESS = 0x2;// 常量, 进度条
    private static final int MESSAGE_POST_CANCEL = 0x3;// 常量, cancel, 这几个常量用于handler处理

    private static final InternalHandler sHandler = new InternalHandler();

    private final WorkerRunnable<Params, Result> mWorker;
    private final FutureTask<Result> mFuture;

    private volatile Status mStatus = Status.PENDING;

    /**
     * Indicates the current status of the task. Each status will be set only once
     * during the lifetime of a task.
     * 指示当前的状态, 每个status只会被set一次, 在其生命周期中
     */
    public enum Status {
        /**
         * Indicates that the task has not been executed yet.  指示task还没有被执行
         */
        PENDING,
        /**
         * Indicates that the task is running.  指示task正在被执行
         */
        RUNNING,
        /**
         * Indicates that {@link AsyncTask#onPostExecute} has finished.  指示onPostExecute已经执行完成
         */
        FINISHED,
    }

    /**
     * Creates a new asynchronous task. This constructor must be invoked on the UI thread.
     * 创建一个异步任务实例, 该构造必须在ui线程调用
     * 所以上面说到, 创建异步任务必须在ui线程
     */
    public AsyncTask() {
     	// 初始化成员变量mWorker, 重写接口里的方法call
         mWorker = new WorkerRunnable<Params, Result>() {
            public Result call() throws Exception {
				// 设置优先级, 查看Process类
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
				// 直接调用doInBackground()方法
                return doInBackground(mParams);
            }
        };

		// 初始化成员变量mFuture, 将Callable接口对象传入, 进行初始化
		// import java.util.concurrent.FutureTask;
		// done()方法的注释如下:
		/* Protected method invoked when this task transitions to state
	     * {@code isDone} (whether normally or via cancellation). The
	     * default implementation does nothing.  Subclasses may override
	     * this method to invoke completion callbacks or perform
	     * bookkeeping. Note that you can query status inside the
	     * implementation of this method to determine whether this task
	     * has been cancelled.*/
	     // 这个Result是第三个参数, 泛型
        mFuture = new FutureTask<Result>(mWorker) {
            @Override
            protected void done() {// 该方法依然在子线程执行的
                Message message;
                Result result = null;

                try {
                    result = get();
                } catch (InterruptedException e) {// error
                    android.util.Log.w(LOG_TAG, e);
                } catch (ExecutionException e) {// error
                    throw new RuntimeException("An error occured while executing doInBackground()",
                            e.getCause());
                } catch (CancellationException e) {// error, 发消息出去cancel
                    message = sHandler.obtainMessage(MESSAGE_POST_CANCEL,
                            new AsyncTaskResult<Result>(AsyncTask.this, (Result[]) null));// 参数直接塞null
                    message.sendToTarget();
                    return;
                } catch (Throwable t) {
                    throw new RuntimeException("An error occured while executing "
                            + "doInBackground()", t);
                }

				// 获取并且发送message
				// sHandler 是InternalHandler类型
				// 第一个参数what, 第二个参数message的obj   
                message = sHandler.obtainMessage(MESSAGE_POST_RESULT,
                        new AsyncTaskResult<Result>(AsyncTask.this, result));  // 传入当前AsyncTask.this对象
                message.sendToTarget();
            }
        };
    }

    /**
     * Returns the current status of this task. 获取当前task的状态
     *
     * @return The current status.
     */
    public final Status getStatus() {
        return mStatus;
    }

    /**
     * Override this method to perform a computation on a background thread. The
     * specified parameters are the parameters passed to {@link #execute}
     * by the caller of this task.
     * 
     * 重写该方法, 去执行一些后台计算.通过方法execute指定的参数, 将会被传入这里
     * This method can call {@link #publishProgress} to publish updates
     * on the UI thread.
     * 该方法中, 可以调用publshProgress去发布更新在ui线程, publishProgress方法会导致onProgressUpdate方法被调用
     *
     * @param params The parameters of the task.
     *
     * @return A result, defined by the subclass of this task.
     *
     * @see #onPreExecute()
     * @see #onPostExecute
     * @see #publishProgress
     */
    protected abstract Result doInBackground(Params... params);

    /**
     * Runs on the UI thread before {@link #doInBackground}.
     * 在doInBackground方法之前, 在ui线程运行
     *
     * @see #onPostExecute
     * @see #doInBackground
     */
    protected void onPreExecute() {
    }

    /**
     * Runs on the UI thread after {@link #doInBackground}. The
     * specified result is the value returned by {@link #doInBackground}
     * or null if the task was cancelled or an exception occured.
     * doInBackground之后, 运行在ui线程,
     * 指定的参数result是在doInBackground中返回的, 如果该task被cancel, 或者异常了, 该参数就是null, 因此使用时候, 要判断
     * 
     * doInBackground
     * @param result The result of the operation computed by {@link #doInBackground}.
     *
     * @see #onPreExecute
     * @see #doInBackground
     */
    @SuppressWarnings({"UnusedDeclaration"})
    protected void onPostExecute(Result result) {
    }

    /**
     * Runs on the UI thread after {@link #publishProgress} is invoked.
     * The specified values are the values passed to {@link #publishProgress}.
     * 
     * @param values The values indicating progress.
     *
     * @see #publishProgress
     * @see #doInBackground
     */
    @SuppressWarnings({"UnusedDeclaration"})
    protected void onProgressUpdate(Progress... values) {
    }

    /**
     * Runs on the UI thread after {@link #cancel(boolean)} is invoked.
     * 当cancel方法被调用后运行, 在ui线程
     *
     * @see #cancel(boolean)
     * @see #isCancelled()
     */
    protected void onCancelled() {
    }

    /**
     * Returns <tt>true</tt> if this task was cancelled before it completed
     * normally.
     *
     * @return <tt>true</tt> if task was cancelled before it completed
     *
     * @see #cancel(boolean)
     */
    public final boolean isCancelled() {
        return mFuture.isCancelled();
    }

    /**
     * Attempts to cancel execution of this task.  This attempt will
     * fail if the task has already completed, already been cancelled,
     * or could not be cancelled for some other reason. If successful,
     * and this task has not started when <tt>cancel</tt> is called,
     * this task should never run.  If the task has already started,
     * then the <tt>mayInterruptIfRunning</tt> parameter determines
     * whether the thread executing this task should be interrupted in
     * an attempt to stop the task.
     * 企图去cancel  任务的执行.
     * 如果task 已经完成, 或者已经cancel,, 或者因为其他原因不能cancel, 将会失败.
     * 如果该task并没有启动的时候调用cancel方法, 那么该任务永远不会再启动.
     * 如果task已经启动, 参数mayInterruptIfRunning决定是否打断task的执行
     * 
     * @param mayInterruptIfRunning <tt>true</tt> if the thread executing this
     *        task should be interrupted; otherwise, in-progress tasks are allowed
     *        to complete.
     *			参数mayInterruptIfRunning 为true, 会打断task执行
     *			否则, 如果task正在运行, 则允许它执行完毕
     *
     * @return <tt>false</tt> if the task could not be cancelled,
     *         typically because it has already completed normally;
     *         <tt>true</tt> otherwise
     *
     * @see #isCancelled()
     * @see #onCancelled()
     */
    public final boolean cancel(boolean mayInterruptIfRunning) {
        return mFuture.cancel(mayInterruptIfRunning);// 简单调用FutureTask的cancel方法
    }

    /**
     * Waits if necessary for the computation to complete, and then
     * retrieves its result.
     *
     * @return The computed result.
     *
     * @throws CancellationException If the computation was cancelled.
     * @throws ExecutionException If the computation threw an exception.
     * @throws InterruptedException If the current thread was interrupted
     *         while waiting.
     */
    public final Result get() throws InterruptedException, ExecutionException {
		// FutureTask中的get方法, 会调用report()方法
		// report方法中:
		//  Object x = outcome;
        //	if (s == NORMAL)
        //    return (V)x;
        // 所以get()方法, 拿到的就是outcome对象, 在FutureTask中的run方法中, 调用call->doInBackground(), 已经将返回值赋给了outcome对象 /** The result to return or exception to throw from get() */
		// 
        return mFuture.get();
    }

    /**
     * Waits if necessary for at most the given time for the computation
     * to complete, and then retrieves its result.
     *
     * @param timeout Time to wait before cancelling the operation.
     * @param unit The time unit for the timeout.
     *
     * @return The computed result.
     *
     * @throws CancellationException If the computation was cancelled.
     * @throws ExecutionException If the computation threw an exception.
     * @throws InterruptedException If the current thread was interrupted
     *         while waiting.
     * @throws TimeoutException If the wait timed out.
     */
    public final Result get(long timeout, TimeUnit unit) throws InterruptedException,
            ExecutionException, TimeoutException {
        return mFuture.get(timeout, unit);
    }

    /** 
     * Executes the task with the specified parameters. The task returns
     * itself (this) so that the caller can keep a reference to it.
     * 用指定的参数执行task, 返回task 自己, 便于调用者持有它的引用
     *
     *
     * This method must be invoked on the UI thread.
     * 必须在ui线程调用
     *
     * @param params The parameters of the task.
     *
     * @return This instance of AsyncTask.
     *
     * @throws IllegalStateException If {@link #getStatus()} returns either
     *         {@link AsyncTask.Status#RUNNING} or {@link AsyncTask.Status#FINISHED}.
     */
    public final AsyncTask<Params, Progress, Result> execute(Params... params) {
    	// 判断当前task的状态, 如果不是pending状态, 都throw exception, 因为只有在pending 状态, 才能进入running状态
        if (mStatus != Status.PENDING) {
            switch (mStatus) {
                case RUNNING:
                    throw new IllegalStateException("Cannot execute task:"
                            + " the task is already running.");
                case FINISHED:
                    throw new IllegalStateException("Cannot execute task:"
                            + " the task has already been executed "
                            + "(a task can be executed only once)");
            }
        }

        mStatus = Status.RUNNING;// 修改当前状态

        onPreExecute();// 在ui线程回调onPreExecute()方法

        mWorker.mParams = params;// 为mWorker赋值

		// sExecutor是ThreadPoolExecutor对象, 这里调用的是ThreadPoolExecutor对象的execute方法
		// 一个任务通过 execute(Runnable)方法被添加到线程池，任务就是一个 Runnable类型的对象，任务的执行方法就是Runnable类型对象的run()方法。
		// public class FutureTask<V> implements RunnableFuture<V>
		// public interface RunnableFuture<V> extends Runnable, Future<V>
		// 因此FutureTask是也runnable对象
		// execute方法中, 会执行mFuture中的run方法, 在其run方法中, 会回调Callable的call方法, 这里就是调用mWorker的call方法, 代码如下:
		//  V  result = c.call();  V是泛型, 指的就是AsyncTask的第三个参数Result类型, call中调用了doInBackground方法, 返回值给了result
        //   ran = true;
        // 完成之后, 会调用set()方法, 并且将result传入
        // if (ran)
        //      set(result);
        // set方法中, 先给outcome对象赋值,  outcome = v;  outcome就是一个object   然后 调用 finishCompletion();
        // finishCompletion()方法中, 会调用done, 这就是FutureTask中重写done()方法的调用时机
        sExecutor.execute(mFuture);// 在线程池中执行

        return this;
    }

    /**
     * This method can be invoked from {@link #doInBackground} to
     * publish updates on the UI thread while the background computation is
     * still running. Each call to this method will trigger the execution of
     * {@link #onProgressUpdate} on the UI thread.
     * 当后台计算依然在运行的过程中, 该方法能够在doInBackground中被调用, 去发布更新在ui线程 
     * 每次该方法调用会触发 onProgressUpdate 方法执行
     * 
     * @param values The progress values to update the UI with.
     *
     * @see #onProgressUpdate
     * @see #doInBackground
     */
    protected final void publishProgress(Progress... values) {// 可以把Progress当成 int  / float 
    	// 通过handler发消息
        sHandler.obtainMessage(MESSAGE_POST_PROGRESS,
                new AsyncTaskResult<Progress>(this, values)).sendToTarget();
    }

    private void finish(Result result) {
        if (isCancelled()) result = null;
        onPostExecute(result);// 回调onPostExecute方法, 参数是doInBackground的返回值
        mStatus = Status.FINISHED;// 修改当前状态
    }

    private static class InternalHandler extends Handler {
        @SuppressWarnings({"unchecked", "RawUseOfParameterizedType"})
        @Override
        public void handleMessage(Message msg) {
            AsyncTaskResult result = (AsyncTaskResult) msg.obj;
            switch (msg.what) {
                case MESSAGE_POST_RESULT:
                    // There is only one result
                    // 调用当前AsyncTask的finish()方法
                    // result.mData[0]这个参数, 就是done()方法中, 用FutureTask get()方法拿到的结果
                    result.mTask.finish(result.mData[0]);
                    break;
                case MESSAGE_POST_PROGRESS:
                    result.mTask.onProgressUpdate(result.mData);// result.mData就是那个int/float值, 然后自己重写onProgressUpdate类, 实现界面的更新
                    break;
                case MESSAGE_POST_CANCEL:
                    result.mTask.onCancelled();
                    break;
            }
        }
    }

	// 抽象类, 实现了Callable接口, 该接口是  java.util.concurrent.Callable
    private static abstract class WorkerRunnable<Params, Result> implements Callable<Result> {
        Params[] mParams;// 只有一个成员变量
    }

/** 异步任务结果的封装类
* 该类就是为了handler  message用的, 把AsyncTask及参数封装成message
* 
*/
    @SuppressWarnings({"RawUseOfParameterizedType"})
    private static class AsyncTaskResult<Data> {
        final AsyncTask mTask;// 当前异步任务
        final Data[] mData;// 结果, 数组实质上只有一个元素

        AsyncTaskResult(AsyncTask task, Data... data) {
            mTask = task;
            mData = data;
        }
    }
}
