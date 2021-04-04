---
title: Activity启动

date: 2021-03-08

categories: 
   - Android

tags: 
   - Android 


description: ​
---

<!-- TOC -->

- [Activity启动的整体概括](#activity启动的整体概括)
- [流程](#流程)

<!-- /TOC -->

# Activity启动的整体概括

  在正式开始Activity漫长而又复杂的启动流程分析之前，让我们先来高屋建瓴的整体概括一下Activity的启动流程(以冷启动为例),其大致可以划分为如下阶段：

 Souruce端进程发送请求目标Activity启动阶段：

1. Source端发起显示/隐式启动请求启动Activity

 system_server进程通过AMS处理启动Activity请求：
 
2. 解析启动目标Activity的Intent
3. 创建目标Activity对应的ActivityRecord
4. 为目标Activity查找/分配或者创建最优Task栈
5. Pause前台Activity
6. Resume请求的目标Activity
7. AMS请求zygote进程为目标Activity创建所属进程

 zygote进程处理system_server进程发送的创建目标Activity进程请求阶段：

8. zygote接受AMS请求fork创建目标Activity所属进程
9. 调用RuntimeInit，初始化目标进程运行环境
10. 通过反射调用目标进程ActivityThread主线程main方法，开启目标进程新时代

 目标Activity进程启动阶段：
 11. 开启目标Activity进程的的Looper消息循环
 12. 注册ApplicationThread到system_server进程
 13. 创建目标进程Application，并执行其onCreate方法

 开启目标Activity生命周期阶段：

14. 真正启动目的端Activity
15. 通过反射加载目标Activity
16. 执行目标Activity生命周期
17. 初始化目标Activity窗口为显示做准备

 目标Activity显示阶段：

18. 新建DecorView
19. 新建ViewRootImpl
20. 将window添加到WMS准备显示

 Source端Activity收尾工作：
 
21. Source端Activity执行onStop()等逻辑





通常我们启动Activity有两个方法startActivity和startActivityForResult。这里我们可以看到Activity中调用startActivity的内部也是调用的startActivityForResult的。
  而我们知道通过startActivityForResult启动目标Activity可以在Activity中回调onActivityResult接收目标Acitiy启动的情况，而调用startActivity则不可以呢？其最最主要的原因就是调用startActivity启动目标Acitivity时，其内部调用startActivityForResult传递的参数requestCode被默认赋予为-1了在后续过程中会根据此值判断是否需要传递回调用结果，这也意味着我们在Activity调用startActivityForResult的时候传递的requestCode值为-1(通过后续分析我们可知，只要小于0)的话，那么onActivityResult是不起作用的。所以当我们调用startActivityForResult的时候需要注意这一点。



# 流程



```java
startActivity()
```

```java
startActivityForResult()
```

```java
mInstrumentation.execStartActivity(
                    this, mMainThread.getApplicationThread(), mToken, this,
                    intent, requestCode, options)
```


>                    Instrumentation是android系统中启动Activity的一个实际操作类

>                    说的Source端开始请求执行启动Activity实际上就是Instrumentation进行实际操作执行的 这里有一个知识点我想强调一下的就是对于每一个
                    
>                   Android App进程来说，它的总入口都是ActivityThread的main方法，每一个应用的进程都有且仅有一个ActivityThread对象，而每一个ActivityThread对象有且仅有一个Instrumentation成员变量，即整个App进程中ActivityThread和Instrumentation实例对象是唯一的。


```java
//Instrumentation.java
    public ActivityResult execStartActivity(
            Context who, 
            IBinder contextThread, 
            IBinder token, 
            Activity target,
            Intent intent, 
            int requestCode, 
            Bundle options)
```
* contextThread
参数类型为IBinder实例，该对象继承于ApplicationThreadNative(Binder服务端)，这个ApplicationThread对象很重要，因为正是通过它串联其了AMS对发起端进程的ActivityThread的交互(如果把ApplicationThread当作服务端，那么此时AMS 就是客户端)

其两者之间的关系建立详见下述的示意图，即AMS持有ApplicationThread的代理端，而应用端进程持有AMS的代理端AMP，二者相互持有各自Binder服务端的代理端进而完成了二者之间的RPC调用。在目标Activiy启动后，发起端Activity的onPause的执行是由ApplicationThread的代理端在AMS中通过Binder IPC透传过来，然后发起端Activity执行onPause流程

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/application_thread_binder.png)

* token
参数类型也为IBinder实例，指向发起端（Source端）Activity的ActivityRecord对象中的Token（其实就是 Binder的代理端），其Binder实体在AMS中。是个 window manager token

* target
参数类型为Activity实例，标明发起端Activity，如果发起端不为Activity此时为null

* intent
参数类型为Intent实例，用来表明要启动的Activity信息，此时的intent可能是显示Intent，也可能是隐式Intent，我们此处的是一个隐式Intent。显式intent通常用在包内启动组件，如果是启动其他APP的组件，则通常用隐式intent。显式intent里面包含了一个ComponentName，ComponentName由包名 + 类名组成，可以唯一标识一个组件，系统通过ComponentName就可以找到要启动的组件。隐式intent通常通过Action来过滤出要启动的组件

* requestCode
参数类型为int，启动Activity的请求码，此请求码表明发起端是否需要接收目标Activity启动的结果

* options
参数类型为Bundle ，可以理解我启动目标Activity的附件参数，譬如附件传输的一些额外信息

内部调用：
```java
ActivityManagerNative.getDefault()
                .startActivity(whoThread, who.getBasePackageName(), intent,
                        intent.resolveTypeIfNeeded(who.getContentResolver()),
                        token, target != null ? target.mEmbeddedID : null,
                        requestCode, 0, null, options);
```
ActivityManagerNative.getDefault() 最后构造一个  new ActivityManagerProxy(new BinderProxy(new BpBinder(handle)))

实际是调用  ActivityManagerProxy 的  startActivity  方法，通过Binder底层，最后进入ActivityManagerNative  的 onTransact()方法，然后进入 ActivityManagerService  的  startActivity() 方法


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/ActivityManagerProxy.startactivity.png)

关于上述整个Binder IPC调用流程，可以使用如下伪代码来简述

```java
AMP.startActivity(...)---> 
BinderProxy.transact(...) --->
BpBinder.transact(...)--->
binder驱动传输--->
JavaBBinder.onTransact(...)--->
AMN.onTransact(..)--->
AMN.startActivity(...)
```


我们先看一下 ActivityManagerProxy 的 startActivity

```java
//ActivityManagerNative.java
    public int startActivity(IApplicationThread caller, 
    						 String callingPackage, 
    						 Intent intent,
            				 String resolvedType, 
            				 IBinder resultTo, 
            				 String resultWho, 
            				 int requestCode,
                             int startFlags, 
                             ProfilerInfo profilerInfo, 
                             Bundle options) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        //写入AMS Binder服务描述信息即android.app.IActivityManager
        data.writeInterfaceToken(IActivityManager.descriptor);
        //写入 IApplicationThread 匿名Binder服务实体(这个在attachApplication时写入过)
        data.writeStrongBinder(caller != null ? caller.asBinder() : null);
        ...
        data.writeStrongBinder(resultTo);
        ...
		//mRemote指向BinderProxy，而BinderProxy持有C++端的BpBinder，进而借助Binder驱动和AMS通信,最后调用到ActivityManagerNative的onTransact()方法中
        mRemote.transact(START_ACTIVITY_TRANSACTION, data, reply, 0);
        reply.readException();
        int result = reply.readInt();
        reply.recycle();
        data.recycle();
        return result;
        }
```

注意第一个参数 caller。是在 startActivityForResult  方法中一路传下来的。
最开始传入的是：mMainThread.getApplicationThread()，丢入mInstrumentation.execStartActivity()，然后放入 ActivityManagerProxy.startActivity(…)

Activity 中的  mMainThread  是：ActivityThread
```java
// Activity.java中
ActivityThread mMainThread;
```


ActivityThread 中
```java
// ActivityThread.java
 final ApplicationThread mAppThread = new ApplicationThread();
    public ApplicationThread getApplicationThread()
    {
        return mAppThread;
    }

    private class ApplicationThread extends ApplicationThreadNative {}
```
因此，拿到的是发起端进程的 ApplicationThread  的实体

data.writeStrongBinder 的时候写入了 ApplicationThread.asBinder()
ApplicationThread  的 asBinder()方法

```java
    // ApplicationThreadNative.java
    public IBinder asBinder()
    {
        return this;
    }
```
因此，写入 Parcel的是 发起端进程 ApplicationThreadNative 实体。


好了，下面Binder驱动跳过，直接进入ActivityManagerNative.onTransact()的  case START_ACTIVITY_TRANSACTION:
```java
//ActivityManagerNative.java
    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        switch (code) {
        case START_ACTIVITY_TRANSACTION:
        {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
			//转换成ApplicationThread Binder实体代理端ApplicationThreadProxy
            IApplicationThread app = ApplicationThreadNative.asInterface(b);
            String callingPackage = data.readString();
            Intent intent = Intent.CREATOR.createFromParcel(data);
            String resolvedType = data.readString();
            IBinder resultTo = data.readStrongBinder();
            ...
			//调用AMN的子类ActivityManagerService
            int result = startActivity(app, callingPackage, intent, resolvedType,
                    resultTo, resultWho, requestCode, startFlags, profilerInfo, options);
            reply.writeNoException();
            reply.writeInt(result);
            return true;
        }
```


> 在正式开始上述的源码分析前，我们先来阐述一个重要的知识点,即在这个调用过程中涉及到两个进程，不妨令startActivity的发起进程记为进程Process_A，AMS Service所属进程记为进程Process_B；那么进程Process_A通过Binder机制（采用IActivityManager接口）向进程Process_B发起请求服务，进程Process_B则通过Binder机制(采用IApplicationThread接口)向进程Process_A发起请求服务。也就是说进程Process_A与进程Process_B能相互间主动发起请求，进而完成进程通信，但是这里有一点需要注意IApplicationThread的Binder实体端并没有注册到servicemanager进程中，它是一个依赖于实名Binder的匿名Binder。


调用AMN的子类ActivityManagerService处理 startActivity。
最终由 ActivityStarter来处理 startActivity。`mActivityStarter.startActivityMayWait`

ActivityStarter.startActivityMayWait 
```java
//ActivityStarter.java
    final int startActivityMayWait(IApplicationThread caller, int callingUid,
            String callingPackage, Intent intent, String resolvedType,
            IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor,
            IBinder resultTo, String resultWho, int requestCode, int startFlags,
            ProfilerInfo profilerInfo, IActivityManager.WaitResult outResult, Configuration config,
            Bundle bOptions, boolean ignoreTargetSecurity, int userId,
            IActivityContainer iContainer, TaskRecord inTask) {
		...
        final Intent ephemeralIntent = new Intent(intent);
        //以传递进来的intent为参数重新创建新的Intent对象，即便intent被修改也不受影响
        intent = new Intent(intent);
        //收集Intent所指向的Activity信息, 当存在多个可供选择的Activity,则直接向用户弹出resolveActivity 
        ResolveInfo rInfo = mSupervisor.resolveIntent(intent, resolvedType, userId);//重点分析该方法
        if (rInfo == null) {//不会进入该分支
        	...
        }
		//根据获取的rInfo信息重新组装intent和设置启动的参数信息
        ActivityInfo aInfo = mSupervisor.resolveActivity(intent, rInfo, startFlags, profilerInfo);
        ...

        final ActivityStack stack;
        if (container == null || container.mStack.isOnHomeDisplay()) {//传入的参数container为null
            stack = mSupervisor.mFocusedStack;//进入该分支
        } else {
            stack = container.mStack;
        }
        ...
         final ActivityRecord[] outRecord = new ActivityRecord[1];

		//继续跟进
         int res = startActivityLocked(caller, intent, ephemeralIntent, resolvedType,
                 aInfo, rInfo, voiceSession, voiceInteractor,
                 resultTo, resultWho, requestCode, callingPid,
                 callingUid, callingPackage, realCallingPid, realCallingUid, startFlags,
                 options, ignoreTargetSecurity, componentSpecified, outRecord, container,
                 inTask);

         Binder.restoreCallingIdentity(origId);
		...
		return res;        
}
```

初步处理启动Activiyt的请求,

借助 ActivityStackSupervisor的 resolveIntent方法，收集Activity信息。
```
ResolveInfo rInfo = mSupervisor.resolveIntent(intent, resolvedType, userId);
```
该方法内部主要通过 PackageManagerService来来解析Intent。在应用没起来之前，只有PMS知道应用都有哪些组件，所以AMS必须借助PKMS来完成相关intent的查询。应用四大组件的信息在应用安装的时候，就已经被PMS解析保存起来了。如果没有声明在AndroidManifest.xml文件中，那么AMS就无法获取目标组件的信息，对于显式intent，会抛出错误；对于隐式intent，也会启动失败。

解析Intent，就是从系统中查找符合要求的目标Activity。找到后，封装在ResolveInfo里。

```java
//根据获取的rInfo信息重新组装intent和设置启动的参数信息
        ActivityInfo aInfo = mSupervisor.resolveActivity(intent, rInfo, startFlags, profilerInfo);
```
根据前面获取的rInfo信息重新组装intent和设置Activity启动的参数信息。


然后调用ActivityStarter的startActivityLocked方法

在这里做了：

0. 做各种异常检查，权限检查
1. 创建要启动的 Activity 的 ActivityRecord对象
2. ActivityStarter.startActivityUnchecked

至此目标Ativity相关信息已经在AMS中建立了

小结：
* 发起端进程是怎么通过Instrumentation管理类，并且借助AMP完成启动Activity请求的发送
* system_server进程中的AMS初步处理启动Activiyt的请求，并借助PKMS服务解析intent获取目标Activity的ActivityInfo信息，然后通过上述解析得到的数据为目标Activiyt构建ActivityRecord数据结构




ActivityStarter.startActivityUnchecked主要工作：为目标Activity查找/分配或者创建最优Task栈
1、初始化Activity启动状态
2、计算launchFlag
3、计算调用者的ActivityStack
4、检查是否存在复用的TaskRecord
5、对于存在复用的TaskRecord则进行相应的ActivityStack、TaskRecord的移动（说实话，我也没有真的搞懂，希望这块比较有经验的小伙们能和我一起学习）
6、计算当前启动Activity所属的TaskRecord
7、把当前启动的Activity放到所属TaskRecord的栈顶

8、最后调用resumeFocusedStackTopActivityLocked创建正在启动的Activity、Paused当前resumed的Activity，
9、以及resumed当前启动的Activity


从8开始，

```java
if (mDoResume) {
                mSupervisor.resumeFocusedStackTopActivityLocked();
}
```

进入 ActivityStackSupervisor 的 resumeFocusedStackTopActivityLocked方法
最终进入：ActivityStack.resumeTopActivityUncheckedLocked
继续： ActivityStack.resumeTopActivityInnerLocked
先 做一些初始化和可能的"异常"处理工作。 比如 A(Launcher) 启动 B(APP)

```java
//[ActivityStack.java]
    private boolean resumeTopActivityInnerLocked(ActivityRecord prev, ActivityOptions options) {
        // 这个 prev 传入的就是要启动的Activity 的 ActivityRecord对象
		...
        /*  比如 A 启动 B
	        找到第一个没有finishing的栈顶activity,通常指向了要启动的Activity目标组件
	        此场景下prev和next都是同一个，都指向了Activity B
        */
        final ActivityRecord next = topRunningActivityLocked();

        // 拿到 目标Activity 的 TaskRecord
        final TaskRecord prevTask = prev != null ? prev.task : null;
        if (next == null) {//这个表示如果当前ActivityStack不存在待启动的Activity，那么会启动Launcher桌面
        }

        next.delayedResume = false;


        //检查要启动的Activity 组件是否等于当前被激活的 Activity 组件，如果等于
        //并且处于 RESUMED 状态，直接返回，我们前面演示的启动情况很显然不满足条件
        if (mResumedActivity == next && next.state == ActivityState.RESUMED &&
                    mStackSupervisor.allResumedActivitiesComplete()) {
             //当前正在显示的Activity正好就是下一个待显示的Activity，
            // 那么，就中断对目标ActivityRecord的调度
        }

        final TaskRecord nextTask = next.task;
         /*这个是对上一个resumed的Activity的相关处理
		 * 由于我们是第一次启动B Activity，所以不可能处于finish跳过此处
		 */
        if (prevTask != null && prevTask.stack == this &&
                prevTask.isOverHomeStack() && prev.finishing && prev.frontOfTask) {
			...
        }

        // 系统进入休眠状态，当前Stack的栈顶Activity已经处于Paused状态
        // 那么，中断待显示Activity的相关调度(有点拗口，学习源码就是这么枯燥的事情)
        if (mService.isSleepingOrShuttingDownLocked()
                && mLastPausedActivity == next
                && mStackSupervisor.allPausedActivitiesComplete()) {
        }

		...

		/*
			在ASS中存在很多的数据结构，用来统一管理ActivityRecord的状态
	    	譬如mStoppingActivities记录了当前所有处于Stopping状态的ActivityRecord
	    	mGoingToSleepActivities记录了当前所有要进入休眠状态的ActivityRecord
	    	在某些场景下，待显示的ActivityRecord可能处于这些数组中，但需要从中剔除
		*/
        mStackSupervisor.mStoppingActivities.remove(next);
        mStackSupervisor.mGoingToSleepActivities.remove(next);
        next.sleeping = false;
        mStackSupervisor.mWaitingVisibleActivities.remove(next);

             // 如果当前ASS中还有ActivityRecord不是处于PAUSED, STOPPED或STOPPING这三个状态之一，
    	// 那么，需要先等这些ActivityRecord进入停止状态
        if (!mStackSupervisor.allPausedActivitiesComplete()) {
            return false;
        }
```


前面的代码片段，主要是做一些初始化和可能的"异常"处理工作。

比如 A(Launcher) 启动 B(APP)

然后开始 Pause  前台 Activity(A  Launcher) ：
```java
		//此时要带入真是场景了，此时的mResumedActivity表示目标Stack栈中处于Resume状态的Activity，那么在此场景下就是Activity A，这个因该比较容易理解
		if (mResumedActivity != null) {
			//当前resumd状态activity不为空，则需要先暂停该Activity
			// pause当前栈的activity，即执行Activity的生命周期onPause
            pausing |= startPausingLocked(userLeaving, false, true, dontWaitForPause);
			
        }
        if (pausing) {//当前有正在pause的Activity，尼玛按照我们场景Activity A启动Activity B，那不是到此就结束了啊，直接返回了
            if (next.app != null && next.app.thread != null) {
                mService.updateLruProcessLocked(next.app, true, null);
            }
            return true;
        }
```

Pause 前台Activity 的 核心方法： ActivityStack.startPausingLocked

```java
//[ActivityStack.java]
    final boolean startPausingLocked(boolean userLeaving, 
    								boolean uiSleeping, //此时传递进来的参数为false
    								boolean resuming,//此时传递进来的参数为true
            						boolean dontWait) 
   {
        ...
        //获取当前Stack栈中处于Resume状态的Activity，在我们当前的环境下就是Activity A了
        ActivityRecord prev = mResumedActivity;
        if (prev == null) {
            if (!resuming) {
                mStackSupervisor.resumeFocusedStackTopActivityLocked();
            }
            return false;
        }
		...
		// 变更ActivityStack中pauseActivity的记录，此处是重点
        mResumedActivity = null;
        mPausingActivity = prev;
        mLastPausedActivity = prev;
        mLastNoHistoryActivity = (prev.intent.getFlags() & Intent.FLAG_ACTIVITY_NO_HISTORY) != 0
                || (prev.info.flags & ActivityInfo.FLAG_NO_HISTORY) != 0 ? prev : null;
        prev.state = ActivityState.PAUSING;
        prev.task.touchActiveTime();
        clearLaunchTime(prev);
        
		...
		 // 通知APP执行发起端的pause操作
        if (prev.app != null && prev.app.thread != null) {
            if (DEBUG_PAUSE) Slog.v(TAG_PAUSE, "Enqueueing pending pause: " + prev);
            try {
                EventLog.writeEvent(EventLogTags.AM_PAUSE_ACTIVITY,
                        prev.userId, System.identityHashCode(prev),
                        prev.shortComponentName);
                mService.updateUsageStats(prev, false);
                prev.app.thread.schedulePauseActivity(prev.appToken, prev.finishing,
                        userLeaving, prev.configChangeFlags, dontWait);
            } catch (Exception e) {
            ...
            }
        } else {
            ...
        }
        ...
    }
```

首先拿到 当前的前台Activity： ActivityRecord prev = mResumedActivity;

然后更改 ActivityStack  维护的相关记录。就是把 mResumedActivity（前台Activity）设置为null。然后把之前的那个前台Activity(A)赋给mPausingActivity，因为它马上就要Pause了。

之后通过 prev.app.thread.schedulePauseActivity(prev.appToken, prev.finishing,
                        userLeaving, prev.configChangeFlags, dontWait); 

发起Binder 跨进程调用，让那个前台Activity进程(Launcher进程)去把 Launcher Activity Pause掉。

prev.app.thread 说的是 在 AMS 里面持有的 前台进程的 ApplicationThreadProxy。
调用 ApplicationThreadProxy的 schedulePauseActivity，最终进入 ApplicationThread.schedulePauseActivity

```java
//[ActivityThread.java]
    private class ApplicationThread extends ApplicationThreadNative {
        public final void schedulePauseActivity(IBinder token, boolean finished,
                boolean userLeaving, int configChanges, boolean dontReport) {
            int seq = getLifecycleSeq();
            sendMessage(
                    finished ? H.PAUSE_ACTIVITY_FINISHING : H.PAUSE_ACTIVITY,
                    token,
                    (userLeaving ? USER_LEAVING : 0) | (dontReport ? DONT_REPORT : 0),
                    configChanges,
                    seq);
        }    	
    }
```

这里，使用 Handle 发送消息 到主线程。然后在主线程中 handleMessage
```java
//ActivityThread.java
private class H extends Handler {
	public void handleMessage(Message msg) {
	    switch (msg.what) {
	        ...
                case PAUSE_ACTIVITY: {
                    Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "activityPause");
                    SomeArgs args = (SomeArgs) msg.obj;
                    handlePauseActivity((IBinder) args.arg1, false,
                            (args.argi1 & USER_LEAVING) != 0, args.argi2,
                            (args.argi1 & DONT_REPORT) != 0, args.argi3);//详见章节1.3.3
                ...
                } break;
	        ...
	    }
	}
}
```

进入 ActivityThread.handlePauseActivity
```java
//[ActivityThread.java]
    private void handlePauseActivity(IBinder token, boolean finished,
            boolean userLeaving, int configChanges, boolean dontReport, int seq) {
        //获取需要
        ActivityClientRecord r = mActivities.get(token);
        if (r != null) {
            if (userLeaving) {
                performUserLeavingActivity(r);
            }
            r.activity.mConfigChangeFlags |= configChanges;
            //执行Activity的onPause操作
            performPauseActivity(token, finished, r.isPreHoneycomb(), "handlePauseActivity");
            ...
            //通知AMS已经Pause成功了
            ActivityManagerNative.getDefault().activityPaused(token);
            ...
            }
        }
    }
```

先 ActivityThread.performPauseActivity->进入另一个重载方法->ActivityThread.performPauseActivityIfNeeded->mInstrumentation.callActivityOnPause(r.activity);->activity.performPause();

```java
    final void performPause() {
        ...
        onPause();
        ...
    }
```

最终执行了Activity的 onPause。

之后，回到ActivityThread.handlePauseActivity，

           //通知AMS已经Pause成功了
            ActivityManagerNative.getDefault().activityPaused(token);

这个 ActivityManagerNative.getDefault() 又是拿到 ActivityManagerProxy，Binder调用，告诉 AMS onPause 执行好了！最后会进入ActivityManagerService的activityPaused方法执行

```java
    // ActivityManagerService.java
    @Override
    public final void activityPaused(IBinder token) {
        final long origId = Binder.clearCallingIdentity();
        synchronized(this) {
            ActivityStack stack = ActivityRecord.getStackLocked(token);
            if (stack != null) {
                stack.activityPausedLocked(token, false);
            }
        }
        Binder.restoreCallingIdentity(origId);
    }
```
ActivityStack.activityPausedLocked->ActivityStack.activityPausedLocked->ActivityStack.completePauseLocked


```java
if (!mService.isSleepingOrShuttingDownLocked()) {//会进入此分支
                //此时的prev为前台显示已经pause的Activity
                mStackSupervisor.resumeFocusedStackTopActivityLocked(topStack, prev, null);
            } 
```
最终进入：ActivityStackSupervisor.resumeFocusedStackTopActivityLocked

进入ActivityStack.resumeTopActivityInnerLocked
此时的传入的prev就是我们已经pause的Activity的了。

这一次，由于所有resumedActivity都已经paused了，所以返回的结果pausing为false，所以可以继续进行目标activity的resume工作。
```java
       if (next.app != null && next.app.thread != null) {//如果目的端进程已经创建,即要启动的目标Activity所属进程已经存在
			...
            next.state = ActivityState.RESUMED;
            mResumedActivity = next;
            next.task.touchActiveTime();
            mRecentTasks.addLocked(next.task);
            mService.updateLruProcessLocked(next.app, true, null);
            updateLRUListLocked(next);
            mService.updateOomAdjLocked();
			...
            try {
				...
                next.sleeping = false;
                mService.showUnsupportedZoomDialogIfNeededLocked(next);
                mService.showAskCompatModeDialogLocked(next);
                next.app.pendingUiClean = true;
                next.app.forceProcessStateUpTo(mService.mTopProcessState);
                next.clearOptionsLocked();
				//执行目的端Activity的scheduleResumeActivity操作
                next.app.thread.scheduleResumeActivity(next.appToken, next.app.repProcState,
                        mService.isNextTransitionForward(), resumeAnimOptions);
				...
            } catch (Exception e) {
				...
            }

            try {
                completeResumeLocked(next);
            } catch (Exception e) {
				//处理异常
                requestFinishActivityLocked(next.appToken, Activity.RESULT_CANCELED, null,
                        "resume-exception", true);
                return true;
            }
        } else {//当目标Activity所属进程没有启动的时候，则会创建进程

            if (!next.hasBeenLaunched) {
                next.hasBeenLaunched = true;
            } else {
                if (SHOW_APP_STARTING_PREVIEW) {
                    next.showStartingWindow(null, true);
                }
            }
			//创建目标Activity进程
            mStackSupervisor.startSpecificActivityLocked(next, true, true);	
        }
```

* 如果要启动的目标Activity所属进程已经创建，则直接通过ApplicationThreadProxy调用目标进程的ActivityThread执行相关的Activity的onCreate等相关生命周期

* 如果目标Activity所属进程没有创建，则通过startSpecificActivityLocked方法创建目标进程，经过层层调用最后会调用到AMS.attachApplicationLocked, 然后再执行resumeTopActivityInnerLocked继续resume操作。


Pause前台显示Activity，Resume目标Activity小结
在上述过程中我们会进行两次resumeTopActivityInnerLocked方法：

* 第一次是将所有resumedActivity进行pause，此时流程不会继续往下进行而是待前台显示的Activity真正执行pause后，然后回调AMS继续第二执行resumeTopActivityInnerLocked相关操作

* 由于此时所有处于Resume状态的Activity已经都被Pause了，所以继续往下执行，此时会判断目标Activity所属进程是否创建，如果创建则直接执行目标Activity的生命周期，如果没有创建则会创建目标Activity所属进程，进而再执行下一步操作



我们假设目标进程没有创建，进入：

mStackSupervisor.startSpecificActivityLocked(next, true, true);	

进入调用
//调用AMS开启startProcess处理流程
        mService.startProcessLocked(r.processName, r.info.applicationInfo, true, 0,
                "activity", r.intent.getComponent(), false, false, true);//详见章节2.2

后面，就要为即将创建的进程在AMS中创建ProcessRecord信息记录，然调用Process.start去请求Zygote进程创建新的APP进程

Process.start(entryPoint,
                    app.processName, uid, uid, gids, debugFlags, mountExternal,
                    app.info.targetSdkVersion, app.info.seinfo, requiredAbi, instructionSet,
                    app.info.dataDir, entryPointArgs)


start后面就清楚了，使用Socket 同 Zygote 进程进行通信，Zygote进程创建新进程完成后，返回给 AMS。

新进程创建好，先打开Binder驱动，开启Binder线程池，然后会执行ActivityThread.main方法


从此，进入新开启的APP进程。

我们知道当Zygote创建完一个应用进程之后，得到的仅仅是一个可以运行的载体，对于应用开发者来说这还远远不够，Android的四大组件还没有侵入到这个新创的进程之中。对于Android应用开发者来说，基本淡化了进程相关的概念，所以当zygote进程创建目标Activity进程之，还需要创建一个运行环境，就是Context，然后创建Application，然后再装载Provider等组件信息，经过如上步骤操作之后才是应用开发者所熟悉的Android应用。



ActivityThread 的 main

* ActivityThread对象构建后，会调用自身的attach()函数，发起一个绑定操作，向system_server发起一个绑定操作，告诉AMS进程应启动完毕，可以进行其他事情了

* 初始化应用进程的主线程的Looper，并开启loop消息循环


在  attach 中
```java
//获取AMS服务端的远程代理对象AMP
            final IActivityManager mgr = ActivityManagerNative.getDefault();
            try {
            	//通过Binder远程调用AMS的attachApplication方法
                mgr.attachApplication(mAppThread);
            }
```

注意这里传递的mAppThread是一个匿名Binder实例（ApplicationThread对象），因此可以作为跨进程传递的参数。这里的mAppThread对象存在于应用进程，但会被传递到系统进程，在系统进程看来，此时的mAppThread就是操作应用进程的一个通信工具。后续，系统进程system_server如果想要向应用进程发起跨进程调用，也都需要通过mAppThread这个对象的远端代理ApplicationThreadProxy来完成相关的调度。

下面进入到 ActivityManagerService 的 attachApplication 方法，
//[ActivityManagerService.java]
    public final void attachApplication(IApplicationThread thread) {
        synchronized (this) {
        	//获取调用进程端pid
            int callingPid = Binder.getCallingPid();
            final long origId = Binder.clearCallingIdentity();
            //attachApplicationLocked进行进一步处理
            attachApplicationLocked(thread, callingPid);
            Binder.restoreCallingIdentity(origId);
        }
    }

注意的是此时的参数类型IApplicationThread已经变成了匿名Binder的代理端了ATP了。
    进而调用：ActivityManagerService.attachApplicationLocked.


>    为啥目标Activity进程需要重新注册(attach)到system_server进程吗，上面的验证安全性是一个方面，另外一个方面就是通过注册(attach)的Binder远程调用传递匿名Binde类IApplicationThread给AMS，然后AMS就可以通过上述的匿名Binder继续对目标Activity进程的相关组件进行调度。



在 attachApplicationLocked 里，主要工作：

1. 获取Activity目标进程在启动阶段由AMS向zygote进程发起请求时创建的ProcessRecord数据结构。
2. 激活ProcessRecord对象。所谓“激活”，就是将ProcessRecord绑定到了一个具体的应用进程，绑定的标识就是将应用进程的ApplicationThread对象赋值给ProcessRecord.thread变量，注意此处的ApplicationThread是Binder的代理端，其实体端是在目标Activity进程端。
3. 在进行一些调试与性能相关的变量设置之后，通过IApplicationThread.bindApplication()向目标Activity进程发起跨进程Binder调用，这样一来，诸如进程名、ApplicationInfo等等相关信息就传递给应用进程了。

下来，ApplicationThread.bindApplication(...) （注意：ApplicationThread 是  ActivityThread.java中）

```java
	//将AMS传递过来的参数封装到AppBindData 数据结构中
            AppBindData data = new AppBindData();
            data.processName = processName;
			...
            sendMessage(H.BIND_APPLICATION, data); //通过Handle 发消息
```

```java
    private void sendMessage(int what, Object obj, int arg1, int arg2, boolean async) {
        if (DEBUG_MESSAGES) Slog.v(
            TAG, "SCHEDULE " + what + " " + mH.codeToString(what)
            + ": " + arg1 + " / " + obj);
        Message msg = Message.obtain();
        msg.what = what;
        msg.obj = obj;
        msg.arg1 = arg1;
        msg.arg2 = arg2;
        if (async) {
            msg.setAsynchronous(true);
        }
        mH.sendMessage(msg);
    }
```

发消息之后，就到了handleMessage中
```java
//[ActivityThread.java]
private class H extends Handler {
	public void handleMessage(Message msg) {
	    switch (msg.what) {
	        ...
                case BIND_APPLICATION:
                    Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "bindApplication");
                    AppBindData data = (AppBindData)msg.obj;
                    handleBindApplication(data);
                    Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                    break;
	        ...
	    }
	}
}
```

然后在 ActivityThread.handleBindApplication中做如下工作：
1. 给当前进程（新创建的APP进程）设置进程名之类的
2. 创建LoadedApk对象
3. 创建ContextImpl对象
4. 创建Instrumentation
5. 创建Application对象,通过LoadedApk.makeApplication()函数，就能创建一个Application对象。保证一个LoadedApk对象只创建一个对应的Application对象实例，
6. 安装providers，看来providers的安装都前于其它三大组件啊
7. 调用Application.onCreate()方法


创建 Application，是 Instrumentation.newApplication(…)通过反射，
```java
//[Instrumentation.java]
    public Application newApplication(ClassLoader cl, String className, Context context)
            throws InstantiationException, IllegalAccessException, 
            ClassNotFoundException {
        return newApplication(cl.loadClass(className), context);
    }

    static public Application newApplication(Class<?> clazz, Context context)
            throws InstantiationException, IllegalAccessException, 
            ClassNotFoundException {
        Application app = (Application)clazz.newInstance();
        app.attach(context);//执行attach操作
        return app;
    }
```

创建好后，执行Application的 attach
```java
final void attach(Context context) {
    attachBaseContext(context); //Application的mBase
    mLoadedApk = ContextImpl.getImpl(context).mPackageInfo;
}
```

其实就是调用Application 的额 attachBaseContext。

然后回调 LoadApk.makeApplication中，又调用了instrumentation.callApplicationOnCreate(app);



```java
// Instrumentation.java
    public void callApplicationOnCreate(Application app) {
        app.onCreate();
    }
```

这样就调用到了 Application的 onCreate。




继续：


在 ActivityManagerService 的 attachApplicationLocked里，
调用IApplicationThread.bindApplication之后，执行 `mStackSupervisor.attachApplicationLocked(app)`

进入：realStartActivityLocked

执行：app.thread.scheduleLaunchActivity
通过app.thread.scheduleLaunchActivity实际上调用的就是目标Activity进程中ActivityThread的内部类ApplicationThread的scheduleLaunchActivity方法中去了，进而调度启动目标Activity



此时 ApplicationThread 中的 scheduleLaunchActivity()方法在ApplicationThread的Binder线程中执行，在该方法中将AMS传递过来的参数填充ActivityClientRecord数据结构，然后通过Handle发送Message，然后在ActivityThread的主线程中执行处理，而处理上述逻辑的即是handleLaunchActivity()方法！
```java
    private void handleLaunchActivity(ActivityClientRecord r, Intent customIntent, String reason) {
       ...
        // //加载目标Activity，并最终回调目标Activity的onCreate和onStart
        Activity a = performLaunchActivity(r, customIntent);

        if (a != null) {
           ...
           // //最终回调目标Activity的onResume.
            handleResumeActivity(r.token, false, r.isForward,
                    !r.activity.mFinished && !r.startsNotResumed, r.lastProcessedSeq, reason);

            if (!r.activity.mFinished && r.startsNotResumed) {
                ...
                performPauseActivityIfNeeded(r, reason);
                ...
            }
        } else {
           ...
        }
    }

```



在 ActivityThread 的  handleLaunchActivity中：`Activity a = performLaunchActivity(r, customIntent);`
```java
//[ActivityThread.java]
    private Activity performLaunchActivity(ActivityClientRecord r, Intent customIntent) {
        ...
        ComponentName component = r.intent.getComponent();
        if (component == null) {
            component = r.intent.resolveActivity(
                mInitialApplication.getPackageManager());
            r.intent.setComponent(component);
        }

        if (r.activityInfo.targetActivity != null) {
            component = new ComponentName(r.activityInfo.packageName,
                    r.activityInfo.targetActivity);
        }

        Activity activity = null;
        try {
        	/*
        		通过反射加载目标Activity
        	*/
            java.lang.ClassLoader cl = r.packageInfo.getClassLoader();
            activity = mInstrumentation.newActivity(
                    cl, component.getClassName(), r.intent);
            /*****************************************************/
            	//这里为了演示方便，直接把源码展开
            	//[Instrumentation.java]
			    public Activity newActivity(ClassLoader cl, String className,
			            Intent intent)
			            throws InstantiationException, IllegalAccessException,
			            ClassNotFoundException {
			        return (Activity)cl.loadClass(className).newInstance();
			    }            	
            /*****************************************************/      
            ...
        } catch (Exception e) {
			...
        }
        try {
        	/*
        		创建目标Actiivty应用进程Application，目标Application在前面博客中已经有被创建了，而且
        		应用进程的Application是唯一的，所以会直接返回前面创建的Applcation
        	*/
            Application app = r.packageInfo.makeApplication(false, mInstrumentation);
            if (activity != null) {
            	//创建目标Activity对应的Context上下文
                Context appContext = createBaseContextForActivity(r, activity);
                ...
                //将上述创建的相关信息，attach到Activity中为后续Activity显示运行做准备     
                activity.attach(appContext, this, getInstrumentation(), r.token,
                        r.ident, app, r.intent, r.activityInfo, title, r.parent,
                        r.embeddedID, r.lastNonConfigurationInstances, config,
                        r.referrer, r.voiceInteractor, window);

                ...
                //开始执行目标Activity的onCreate()方法回调
                if (r.isPersistable()) {
                    mInstrumentation.callActivityOnCreate(activity, r.state, r.persistentState);
                } else {
                    mInstrumentation.callActivityOnCreate(activity, r.state);
                }
                ...
                if (!r.activity.mFinished) {
                    activity.performStart();//执行目标Activity的onStart()方法
                    r.stopped = false;
                }
				...
            }
            r.paused = true;
			//将目标Activity相关信息，保存到mActivities中
            mActivities.put(r.token, r);

        } catch (SuperNotCalledException e) {        
            throw e;

        } catch (Exception e) {
			...
        }
        return activity;
    }  
```
首先拿到 ComponentName，然后拿到 ClassCloader，使用 mInstrumentation  newActivity。


然后这块使用反射，创建了一个Activity出来
```java
    public Activity newActivity(ClassLoader cl, String className,
            Intent intent)
            throws InstantiationException, IllegalAccessException,
            ClassNotFoundException {
        return (Activity)cl.loadClass(className).newInstance();
    }
```

Activity创建好了之后，给它一个Context
Context appContext = createBaseContextForActivity(r, activity);

然后调用` mInstrumentation.callActivityOnCreate(activity, r.state, r.persistentState);`
```java
    public void callActivityOnCreate(Activity activity, Bundle icicle,
            PersistableBundle persistentState) {
        prePerformCreate(activity);
        activity.performCreate(icicle);
        postPerformCreate(activity);
    }
```

调用Activity的 activity.performCreate(icicle);

进而调用到Activity的 onCreate()方法
```java
 final void performCreate(Bundle icicle) {
        restoreHasCurrentPermissionRequest(icicle);
        onCreate(icicle);
        mActivityTransitionState.readState(icicle);
        performCreateCommon();
    }
```

之后，回到ActivityThread的 performLaunchActivity 方法中，又调用
```java
 if (!r.activity.mFinished) {
                    activity.performStart();//执行目标Activity的onStart()方法
                    r.stopped = false;
                }
```


这就执行了Activity的 onStart方法。

完了之后，回到： `ActivityThread.handleLaunchActivity(…)`，继续执行 `ActivityThread.handleResumeActivity`

进而：
`r = performResumeActivity(token, clearHide, reason);`

里面： `r.activity.performResume();`

```java
final void performResume() {
        // 先判断是否要Restart，如果有必须，要执行onRestart方法
        performRestart();

      ...
        mInstrumentation.callActivityOnResume(this);
      ...

        onPostResume();
        ...
    }
```


performRestart里面会根据条件，执行 `mInstrumentation.callActivityOnRestart(this);`

```java
// Instrumentation.java
  public void callActivityOnRestart(Activity activity) {
        activity.onRestart();
    }

```

然后就执行了Activity的 onRestart()方法.

mInstrumentation.callActivityOnResume(this);

```java
public void callActivityOnResume(Activity activity) {
        activity.mResumed = true;
        activity.onResume(); // 执行Activity的onResume方法
       ...
    }
```


至此我们的整个的Actiivty启动流程到此就结束了！




















