---
title: Window的创建

date: 2021-03-08

categories: 
   - Android

tags: 
   - Android 


description: ​
---

<!-- TOC -->

- [View/Window/WindowManager关系](#viewwindowwindowmanager关系)
- [准备](#准备)
- [创建流程](#创建流程)

<!-- /TOC -->

# View/Window/WindowManager关系

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/window_windowmanager_view.png)


Activity:只是用来给开发者来承载各种界面布局。

Window:Activity对界面布局的加载和管理是都是通过Window对象来实现的。

View:放在Window上的，各种View组件的排列组合搭积木般的实现了界面展示效果。

WindowManager:通过上述三者之间的通力协作终于完成了我们布局文件的加载了，但是加载完成这还远远远不够，因为我们最终的目标是呈现给用户，这就轮到我们的WindowManager上场了。通过它我们和WMS之间建立了关联，进而将上述Window送到surfaceflinger中进行相关的渲染显示，并最终呈现给我们用户。



# 准备

1. startActivity之后，Zygote进程为 目标Activity创建了新的进程之后，在新的进程中，会通过反射调用到 ActivityThread 的 main方法。

2. 在main方法中，执行 `thread.attach(false);`，这里，会`mgr.attachApplication(mAppThread);` 通过 ActivityManagerProxy 跨进程调用，把 mAppThread(ApplicationThread)这个匿名Binder传入到  system_server进程的AMS服务中，这样AMS就能拿到这个 ApplicationThread的代理对象同 APP进程通信了。

3. 在 AMS 的 `attachApplication` 方法中，进入 `attachApplicationLocked`，在进行一些调试与性能相关的变量设置之后，通过刚才拿到的那个`IApplicationThread.bindApplication()`向目标Activity进程发起跨进程Binder调用，这样一来，诸如进程名、ApplicationInfo等等相关信息就传递给应用进程了。

4. 这样又回到 APP进程端ApplicationThread的 bindApplication方法，这是在Binder线程中。在Binder线程通过Handler发出Message，发到主线程，然后在主线程的处理该消息。就调用了：`handleBindApplication()`。何为bind？原本新创建的进程基本是个空的，没有任何Android 特性。在bind方法，就创建了Application。这样这个进程就是一个Application进程了，然后回调了 Application 的  `attachBaseContext()` 和 `onCreate()` 方法。

5. 在 AMS 的 `attachApplication` 方法中，进入`attachApplicationLocked()`，执行完 `IApplicationThread.bindApplication()`，立马又执行了：`mStackSupervisor.attachApplicationLocked(app)`。在 `mStackSupervisor.attachApplicationLocked(app)` 这里，执行 `realStartActivityLocked()`，进而`app.thread.scheduleLaunchActivity`，就是通过IApplicationThread匿名Binder调用到目标Activity进程的ActivityThread中

6. 目标Activity进程 在Binder线程进入 scheduleLaunchActivity，然后通过Handler发Message到主线程，主线程执行 `handleLaunchActivity()`。这个`handleLaunchActivity()`方法主要是回调Activity的生命周期。先后调用了 `Activity a = performLaunchActivity ` 和  `handleResumeActivity()`

7. `performLaunchActivity()`主要是反射创建 Activity，并回调Activity的onCreate 和 onStart。 `handleResumeActivity()` 主要是回调 Activity的 onResume方法


我们就从 `handleLaunchActivity()` 这里说起。

# 创建流程
```java
//[ActivityThread.java]
    private void handleLaunchActivity(ActivityClientRecord r, Intent customIntent, String reason) {
    	...    	
        WindowManagerGlobal.initialize();//这个方法的实质是获取WMS服务代理端

		...
		Activity a = performLaunchActivity(r, customIntent);//创建目标Activity，并创建Activity对应的Window
		...
        if (a != null) {
        	//此方法最终会调用到Activity的onResume()方法中
        	handleResumeActivity(r.token, false, r.isForward,
                    !r.activity.mFinished && !r.startsNotResumed, r.lastProcessedSeq, reason);
        }
        ...		
    }
```

关注三件事：
1. 先初始化 WindowManagerGlobal。
WindowManagerGlobal是单例模式的，一个进程内只有一个，这里调用该类的initialize初始化方法，获取WMS服务的代理端
2. 接着调用performLaunchActivity方法，从而创建目标Activity，并创建Activity对应的Window
3. 调用handleResumeActivity方法，进而执行目标Activity的onResume方法

```java
//[WindowManagerGlobal.java]

	private static IWindowManager sWindowManagerService;
    public static void initialize() {
        getWindowManagerService();
    }
    public static IWindowManager getWindowManagerService() {
        synchronized (WindowManagerGlobal.class) {
            if (sWindowManagerService == null) {
                sWindowManagerService = IWindowManager.Stub.asInterface(
                        ServiceManager.getService("window"));
                try {
                    sWindowManagerService = getWindowManagerService();
                    ValueAnimator.setDurationScale(sWindowManagerService.getCurrentAnimatorScale());
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            }
            return sWindowManagerService;
        }
    }
```
可以看到是线程安全的单例模式，sWindowManagerService 是 static的，创建了  `IWindowManager.Stub.asInterface(ServiceManager.getService("window"))` 。本质上是一个  IWindowManager.Stub.Proxy。就是 个Binder的远端代理对象，可能以后要用它跟 system_server进程中的 WindowManagerService(IWindowManager.Stub)通信吧。（`public class WindowManagerService extends IWindowManager.Stub`。

所以这个 WindowManagerGlobal 初始化，就是 创建好里面的  IWindowManager.Stub.Proxy （WindowManagerService的 Binder的远端代理对象）
![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/windowmanager.png)


下来执行 performLaunchActivity

```java
    //[ActivityThread.java]
    private Activity performLaunchActivity(ActivityClientRecord r, Intent customIntent) {
		//开始构建目标Activity相关信息
		...
        Activity activity = null;
        try {
        	//通过反射加载目标Activity
            java.lang.ClassLoader cl = r.packageInfo.getClassLoader();
            activity = mInstrumentation.newActivity(
                    cl, component.getClassName(), r.intent);
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

                Window window = null;
                if (r.mPendingRemoveWindow != null && r.mPreserveWindow) {//此种情况即复用以前的Window
                    window = r.mPendingRemoveWindow;
                    r.mPendingRemoveWindow = null;
                    r.mPendingRemoveWindowManager = null;
                }   
                //将上述创建的相关信息，attach到Activity中为后续Activity显示运行做准备
                activity.attach(appContext, this, getInstrumentation(), r.token,
                        r.ident, app, r.intent, r.activityInfo, title, r.parent,
                        r.embeddedID, r.lastNonConfigurationInstances, config,
                        r.referrer, r.voiceInteractor, window);
				...
                activity.mCalled = false;//这个地方是干啥的呢，防止开发者在执行onCreate()方法的时候没有初始化父类Activity的onCreate()方法
                //开始执行目标Activity的onCreate()方法回调
                if (r.isPersistable()) {
                    mInstrumentation.callActivityOnCreate(activity, r.state, r.persistentState);
                } else {
                    mInstrumentation.callActivityOnCreate(activity, r.state);
                }
				...

        } catch (SuperNotCalledException e) {        
            throw e;

        } catch (Exception e) {
			...
        }
        return activity;
    }
```

做了两件事：
1. 通过反射创建目标Activity实例对象。
2. 然后调用目标Activity的实例对象的attach()方法，把参数都丢进去。毫不夸张的说我们在performLacunchActivity()方法以前面的一系列调用方法中，绝大部分工作都是为了填充ActivityClientRecord的信息而做的，待ActivityClientRecord的信息填充饱满以后借用attach()方法将这些参数配置到新创建的Activity对象中，从而将目标Activity和我们的应用进程，甚至是AMS等关联起来。


看下attach

```java
//[Activity.java]
    final void attach(  
    					Context context, //目标Activity对应的Context上下文信息，即前面创建的ContextImpl实例对象
    					ActivityThread aThread,//Activity应用进程的主线程ActivityThread实例对象
            			Instrumentation instr, //监控管理Activity运行状态的Instrumentation实例对象
            			IBinder token, //这个token是啥呢，用于和AMS服务通信的IApplicationToken.Proxy代理对象，它对应的实体是在AMS为目标Activity创建对应的ActivityRecored实例对象的时候创建的
            			int ident,
            			Application application, //Activity应用进程对应的Application实例对象
            			Intent intent, 
            			ActivityInfo info,//Activity在AndroidManifest中的配置信息
            			CharSequence title, 
            			Activity parent, //启动当前Activity对应的Activity
            			String id,
            			NonConfigurationInstances lastNonConfigurationInstances,
            			Configuration config, String referrer, 
            			IVoiceInteractor voiceInteractor,
            			Window window  //是否存在可以复用的Window
     ) 
	{
        //将前面创建的上下文对象ContextImpl保存到Activity的成员变量中
        attachBaseContext(context);
        ...        
		//此处是重点，直接以PhoneWindow实例Window窗口对象
        mWindow = new PhoneWindow(this, window);
        ...
        //记录记录应用程序的UI线程
        mUiThread = Thread.currentThread();

		//记录应用进程的ActivityThread实例
        mMainThread = aThread;
        mInstrumentation = instr;
        mToken = token;
        mIdent = ident;
        mApplication = application;
        mIntent = intent;
        mReferrer = referrer;
        mComponent = intent.getComponent();
        mActivityInfo = info;
        mTitle = title;
        mParent = parent;
		...//初始化其它的相关Actiivty成员变量
		
		//为Activity所在的窗口设置窗口管理器，并且这里的setWindowManager没有被重构，直接调用的是父类Window的方法
        mWindow.setWindowManager(
                (WindowManager)context.getSystemService(Context.WINDOW_SERVICE),
                mToken, mComponent.flattenToString(),
                (info.flags & ActivityInfo.FLAG_HARDWARE_ACCELERATED) != 0);
        if (mParent != null) {
            mWindow.setContainer(mParent.getWindow());
        }
        //保存窗口管理器实例对象
        mWindowManager = mWindow.getWindowManager();
        mCurrentConfig = config;
    }
```


首先 `mWindow = new PhoneWindow(this, window);` mWindow是 Activity的成员变量 `private Window mWindow;`。 直接创建一个 PhoneWindow 给它。此处为Activity和Window之间的关联正式建立了！并且我们后续会知道Activity中很多操作View相关的方法，例如setContentView()、findViewById()、getLayoutInflater()等，实际上都是间接调用到PhoneWindow里面的相关方法的，因此说Window是布局的管理类。

然后，`mWindow.setWindowManager` 为 mWindow实例对象创建窗口管理器，总之， 就是 给 mWindow 设置一个 以Activity对应的Context上下文构建的WindowManagerImpl对象实例。这个WindowManagerImpl的mParentWindow成员指向了Activity对应的PhoneWindw窗口（就是那个 mWindow 成员）。
注意：最后，Activity的成员变量mWindowManager也被赋值为这个 WindowManagerImpl 对象实例。


至此，我们为 Activity 的窗口创建了窗口管理器 WindowManagerImpl，并且这个 WindowManagerImpl 它保存了单例对象WindowManagerGloble的引用，即mGlobal变量。此外，通过前面我们的类图可以看到WindowManagerImpl实现了WindowManager，并且WindowManager继承自ViewManager接口，ViewManager接口方法如下所示(可以看到它定义的接口方法都是和View有关系的)：
```java
//[ViewManager.java]
public interface ViewManager
{
    public void addView(View view, ViewGroup.LayoutParams params);
    public void updateViewLayout(View view, ViewGroup.LayoutParams params);
    public void removeView(View view);
}
```
分析到这里我们发现我们Activity直接持有 WindowManagerImpl ，然后通过WindowManagerImpl间接持有WindowManagerGloble，并且WindowManagerGloble在整个应用进程中是唯一的(因为它采用了单例模式)。至此我们可以得出一个结论就是Android系统为每一个启动的Activity创建了一个轻量级的窗口管理器WindowManagerImpl，每个Activity通过WindowManagerImpl来访问WindowManagerGloble,并且Activity中关于窗口的相关操作最后都是由mGlobal亲自操刀的。


现在，关系如下

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/activity_window_relationship_.png)



现在我们看到，在Activity里调用 setContentView，全是由 PhoneWindow 代理执行的。






另一个要关注的就是mDecor变量，这个变量是DecorView类型的。DecorView对象继承自FrameLayout，所以他本质上还是一个View，只是对FrameLayout做了一定的包装，例如添加了一些与Window需要调用的方法setWindowBackground()、setWindowFrame()等。

我们知道，Acitivty界面的View是呈树状结构的，而mDecor变量在这里作为Activity的界面的根View存在。关于这三者之间的关系如果一定要找到现实世界中的映射，那么PhoneWindow就是相框，DecorView就是相框内的白纸，而Activity就是控制白纸要具体显示什么的神秘幕后的操盘手了，View就是白纸上的具体内容。