---
title: onResume

date: 2021-03-08

categories: 
   - Android

tags: 
   - Android 


description: ​
---

<!-- TOC -->

- [handleResumeActivity](#handleresumeactivity)
- [WindowManagerImpl.addView](#windowmanagerimpladdview)
- [构造：root = new ViewRootImpl(view.getContext(), display);](#构造root--new-viewrootimplviewgetcontext-display)
    - [IWindowSession](#iwindowsession)
- [`root.setView(view, wparams, panelParentView);`](#rootsetviewview-wparams-panelparentview)
- [结束](#结束)

<!-- /TOC -->

# handleResumeActivity

在 AcitityThread.handleLauncherActivity() 中执行完performLaunchAcitity()创建好Acitivity并完成onCreate()方法的调用后，继续执行handleResumeActivity()方法。


```java
//[ActivityThread.java]
    final void handleResumeActivity(IBinder token,
            boolean clearHide, boolean isForward, boolean reallyResume, int seq, String reason) {
        ActivityClientRecord r = mActivities.get(token);
		...
        //该方法执行过程中会调用到Acitity的onResume()方法，
        //返回的ActivityClientRecord对象对应的已经创建好并且初始化好的Activity
        r = performResumeActivity(token, clearHide, reason);

        if (r != null) {
            final Activity a = r.activity;//得到前面创建好的Activity
			/*
           		判断该Acitivity是否可见
                mStartedAcitity记录的是一个Activity是否还处于启动状态
　　　　　　	  如果还处于启动状态则mStartedAcitity为true，表示该activity还未启动好，则该Activity还不可见
				注意mStartedActivity的值在performLaunchActivity中会被设为false，代表已经启动好了
			*/
            boolean willBeVisible = !a.mStartedActivity;

			/*
				此处再次向AMS查询，目标Activity是否应该可见
			*/
            if (!willBeVisible) {
                try {
                    willBeVisible = ActivityManagerNative.getDefault().willActivityBeVisible(
                            a.getActivityToken());
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            }
            if (r.window == null && !a.mFinished && willBeVisible) {
				/*
					获取前面为目标Activity创建好的::
					窗口Window
					窗口管理器
					DecorView
					
				*/
                r.window = r.activity.getWindow(); // 拿到  Activity  的 mWindow，即 PhoneWindow
                View decor = r.window.getDecorView(); // 拿到PhoneWindow 的 DecorView
                decor.setVisibility(View.INVISIBLE); // 设置 DecorView 不可见
                ViewManager wm = a.getWindowManager(); // 拿到Activity的 WindowManagerImpl 对象
                WindowManager.LayoutParams l = r.window.getAttributes();
                a.mDecor = decor; // 把 PhoneWindow的 decor 赋给 Activity的 Decor。注意啊，在这之前，DecorView一直都是PhoneWindow的成员变量，没有与Activity关联，现在把它赋给了Activity的成员
                l.type = WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
                l.softInputMode |= forwardBit;

				//调用了WindowManagerImpl的addView方法
                if (a.mVisibleFromClient && !a.mWindowAdded) {
                    a.mWindowAdded = true;
                    wm.addView(decor, l);//这个是重点
                }

            } else if (!willBeVisible) {
				...
            }
			...
            if (!r.activity.mFinished && willBeVisible
                    && r.activity.mDecor != null && !r.hideForNow) {
				...
				//设置目标Activity可见
                if (r.activity.mVisibleFromClient) {
                    r.activity.makeVisible();
                }
            }
            ...
            //此处是通知AMS目标Activity已经执行完onResume完毕了
            if (reallyResume) {
                try {
                    ActivityManagerNative.getDefault().activityResumed(token);
                } catch (RemoteException ex) {
                    throw ex.rethrowFromSystemServer();
                }
            }
        } else {
			...
        }
    }
```


流程：


1. 执行目标Activity的onResume()方法

2. 如果Activity 可见，赋值一堆东西

3. 接着调用WindowManagerImpl实例对象的addView()方法对Activity对应的DecorView做进一步的处理

4. 最后将目标Activiyt设置为可见



# WindowManagerImpl.addView

看下 WindowManagerImpl实例对象的addView()方法，参数view 是 Activity 中的 Window 对应的 DecorView 对象
```java
// WindowManagerImpl
    @Override
    public void addView(@NonNull View view, @NonNull ViewGroup.LayoutParams params) {
        applyDefaultToken(params);
        // 注意此时的mParentWindow指向了前面创建的Activity对应的窗口PhoneWindow
        mGlobal.addView(view, params, mContext.getDisplay(), mParentWindow);
    }
```



使用 WindowManagerGlobal 的 addView 方法。先看下 WindowManagerGlobal 的成员变量


```java
//[WindowManagerGlobal.java]
	private static WindowManagerGlobal sDefaultWindowManager; // 单例模式，WindowManagerGlobal 自己
    private static IWindowManager sWindowManagerService;  // 这个也是  WMS 的远端代理
    private static IWindowSession sWindowSession;// WMS服务提供给ViewRootImpl,用来和其进行跨Binder通信的接口 ，即 WMS 的远端代理

    private final Object mLock = new Object();

    private final ArrayList<View> mViews = new ArrayList<View>();
    private final ArrayList<ViewRootImpl> mRoots = new ArrayList<ViewRootImpl>();  // ViewRootImpl 是  WindowManagerGlobal中真正做事的
    private final ArrayList<WindowManager.LayoutParams> mParams =
            new ArrayList<WindowManager.LayoutParams>();
    private final ArraySet<View> mDyingViews = new ArraySet<View>();
```


下面看 WindowManagerGlobal 的 addView 方法
```java
public void addView(View view, //此处的view指向DecorView
    					ViewGroup.LayoutParams params,//这里的params为Window对应的默认WindowManager.LayoutParams实例对象mWindowAttributes
            			Display display, //这里的Display具体指向表示物理显示设备有关的逻辑显示的大小(譬如尺寸分辨率)和密度的信息
            			Window parentWindow) //这里的parentWindow指向了Activity对应的窗口实例对象PhoneWindow
                         {
        // 拿到传入的布局参数
        final WindowManager.LayoutParams wparams = (WindowManager.LayoutParams) params;
       ...
        ViewRootImpl root;
        View panelParentView = null;

        synchronized (mLock) {
            ...
            // 查找是否已经有该 View
            int index = findViewLocked(view, false);
            if (index >= 0) {
                ...
            }
            ...
            // 构建ViewRootImpl,它是WindowManagerGlobal最最得力的干将没有之一,基本包揽了WindowManagerGlobal绝大部分工作
            root = new ViewRootImpl(view.getContext(), display);

            view.setLayoutParams(wparams);

            mViews.add(view);
            mRoots.add(root);
            mParams.add(wparams);
        }

        // do this last because it fires off messages to start doing things
        try {
            // 将DecorView添加到ViewRootImpl中,最后添加到WMS中。这里应该是真正去调用WMS了吧？
            root.setView(view, wparams, panelParentView);
        } catch (RuntimeException e) {
            ...
        }
    }
```
先构建一个 ViewRootImpl，然后通过 ViewRootImpl  的  setView 方法，完成窗口视图的添加和显示过程。
Activiyt的显示更多的就是和WMS的交互来完成的。

分析至此我们知道，当应用程序向窗口管理器（WindowManagerImpl）中添加一个窗口视图对象（addView）时，首先会为该视图对象创建一个ViewRootImpl对象，并且将视图对象（View）、ViewRootImpl对象、视图布局参数（ViewGroup.LayoutParams）分别保存到窗口管理器WindowManagerGlobal的mViews、mRoots、mParams数组中(并且他们三者在对应的数组中的索引是一一对应的)，如下图所示：
![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/DecorView_ViewRootImpl_LayoutParams.png)



下面我们看  ViewRootImpl 是如果构造的，以及如何 setView的。

# 构造：root = new ViewRootImpl(view.getContext(), display);

```java
//[ViewRootImpl.java]

	final IWindowSession mWindowSession;
    // 这个 ViewRootHandler 就是个普通的Handler
    // final class ViewRootHandler extends Handler{}
	final ViewRootHandler mHandler = new ViewRootHandler(); 
	// These can be accessed by any thread, must be protected with a lock.
    // Surface can never be reassigned or cleared (use Surface.clear()).
    final Surface mSurface = new Surface();
    
    public ViewRootImpl(Context context, Display display) {
        mContext = context;

		/*
			得到IWindowSession的远端代理对象
            该对象是WMS端暴露给窗口端的，用于和WMS服务端通信的Binder通道
		*/
        mWindowSession = WindowManagerGlobal.getWindowSession();
        ...
		/*
			创建了一个W本地Binder对象，用于WMS通知应用程序进程
			有点像ActiviytThread中的IApplicationThread实例对象
            后续应该会把这个 W 作为匿名Binder 发到 WMS，然后WMS拿到W就可以和客户端交互了？

			关于Activity和WMS的交互完全可以参考Activity启动过程中和AMS的交互
			但是ActivityThread中的IApplicationThread是单一实例，而这里的W却是每一个
			ViewRootImpl都拥有一个			
		*/
        mWindow = new W(this);
        ...
		/*
			构造一个AttachInfo对象
			注意类型为Handler的参数，传入的是：ViewRootHandler实例mHandler，
			这个Handler用于处理Android应用程序的窗口信息
		*/
        mAttachInfo = new View.AttachInfo(mWindowSession, mWindow, display, this, mHandler, this);
        ...
    }
```

ViewRootImpl 构造总结：创建ViewRootHandler，创建Surface，获取IWindowSession的代理对象，创建IWindow实体 mWindow，创建View.AttachInfo



![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/ViewRootImpl_WMS_releationship.png)


## IWindowSession

getWindowSession()获取和WMS通信的Binder代理端IWindowSession

IWindowSession是一个AIDL类
```java
//[IWindowSession.aidl]
/**
	中文翻译一下就是用于每个应用程序和WMS进行通信的接口
 * System private per-application interface to the window manager.
 *
 * {@hide}
 */
interface IWindowSession {
}
```

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/IWindowSession.png)

看下源码
```java
//[WindowManagerGlobal.java]
    public static IWindowSession getWindowSession() {
        synchronized (WindowManagerGlobal.class) {
            if (sWindowSession == null) {
                try {
					//获取输入法管理器服务Binder代理端
                    InputMethodManager imm = InputMethodManager.getInstance();
					//获取窗口管理器服务Binder代理端
                    IWindowManager windowManager = getWindowManagerService();
					//得到IWindowSession代理对象
					//这里需要注意的是传递的两个参数都是匿名Binder，所以匿名Binder读者最好能掌握掌握
                    sWindowSession = windowManager.openSession(
                            new IWindowSessionCallback.Stub() {
                                @Override
                                public void onAnimatorScaleChanged(float scale) {
                                    ValueAnimator.setDurationScale(scale);
                                }
                            },
                            imm.getClient(), imm.getInputContext());
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            }
            return sWindowSession;
        }
    }
```


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/IWindowManager.png)

拿到 IWindowManager  的 代理端：IWindowManager.Stub.Proxy，然后调用 Proxy的 openSession方法。通过Binder驱动，最终调用到WindowManagerService的openSession()方法中。

```java
//[windowManagerService.java]
    @Override
    public IWindowSession openSession(IWindowSessionCallback callback, IInputMethodClient client,
            IInputContext inputContext) {
        //异常检测
        if (client == null) throw new IllegalArgumentException("null client");
        if (inputContext == null) throw new IllegalArgumentException("null inputContext");
        //直接明了，构建一个Session实例。这个 this  是  WMS 自己，后续Session实体再收到请求，就直接转发给 WMS了。
        Session session = new Session(this, callback, client, inputContext);
        return session;
    }
```

在WindowManagerService的openSession()方法中，直接创建了一个 Session 对象，Session 是什么
```java
final class Session extends IWindowSession.Stub
        implements IBinder.DeathRecipient {}
```

可以看到对象，Session是 IWindowSession.Stub  的 服务实体。把Session 直接返回，然后就通过Binder驱动把Session返回到 APP进程端，APP端在 WindowManagerGlobal.getWindowSession 中拿到的就是 IWindowSession代理对象了，即：IWindowSession.Stub.Proxy 。并且保存在 WindowManagerGlobal的 静态变量 sWindowSession 中。

现在，WindowManagerGlobal 就持有了两个变量了
1. sWindowManagerService 是 WMS 的远端代理： IWindowManager.Stub.Proxy
2. sWindowSession，也是一个远端代理，IWindowSession.Stub.Proxy

要这么多干嘛？




IWindow
```java
//[IWindow.aidl]
/**
	这里我用我蹩脚的英文简单翻译一下就是，WMS服务用于通知客户端窗口一些事情的通道
 * API back to a client window that the Window Manager uses to inform it of
 * interesting things happening.
 *
 * {@hide}
 */
oneway interface IWindow {
}
```
此处的W本地对象用于WMS通知应用程序进程，有点像ActiviytThread中的IApplicationThread实例对象用于AMS和APP进程通信的方法，都是使用匿名Binder！

即：WMS 端会持有一个 IWindow的 远端代理对象，然后APP端持有IWindow的服务实体， 这样WMS通过 IWindow的远端代理同APP进程进行通信。


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/IWindow.png)



现在，APP进程通过WindowManagerService的远端代理 sWindowManagerService 同AMS通信， AMS通过IWindow 的远端代理通客户端通信。那 IWindowSession.Stub.Proxy 还需要么。


ViewRootImpl 构造好了，下面看它如何 setView


# `root.setView(view, wparams, panelParentView);`

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/addView.png)

注意第一阶段，是在 handleResumeActivity，当Activity  onResume 之后，使用 Activity 的 WindowManagerImpl 的 addView方法。

WindowManagerImpl.add(decorView)

这里，Activity 的 WindowManagerImpl 和 PhoneWindow 的 WindowManagerImpl 是相同的。
Activity的 DecorView 和  PhoneWindow 的 DecorView 也是相同的。

也可以理解为：使用PhoneWindow的 WindowManagerImpl 把它（PhoneWindow）的DecorView 加入到 Window管理器中。



```java
  /**
		 这里我们分析的是Activity的DecorView窗口视图添加的逻辑，所以此时不存在父视图的概念，
     	 不会走到这里，此时的panelParentView为null
     */
    public void setView(View view, WindowManager.LayoutParams attrs, View panelParentView) {
        synchronized (this) {
            if (mView == null) {
                ..
                
                /*
                 	这里调用异步刷新请求，最终会调用performTraversals方法来完成View的绘制
                 	在向WMS添加窗口前进行UI布局
                */
                requestLayout();
				
                if ((mWindowAttributes.inputFeatures
                        & WindowManager.LayoutParams.INPUT_FEATURE_NO_INPUT_CHANNEL) == 0) {
                    mInputChannel = new InputChannel();
                }
				...
                try {
					...
					/*
						将窗口添加到WMS服务中，mWindow为W本地Binder对象，
						通过Binder传输到WMS服务端后，变为IWindow代理对象
					*/
                    res = mWindowSession.addToDisplay(mWindow, mSeq, mWindowAttributes,
                            getHostVisibility(), mDisplay.getDisplayId(),
                            mAttachInfo.mContentInsets, mAttachInfo.mStableInsets,
                            mAttachInfo.mOutsets, mInputChannel);
                } catch (RemoteException e) {
                }
    			...
            }
        }
    }
```

就做了两件事情，
1. 调用ViewRootImpl内部方法requestLayout，对窗口视图中的UI进行窗口布局等操作(测量，布局，绘图)，
2. 然后调用前面的获取的代理端IWindowSession的addToDisplay向WMS添加一个窗口对象。这里把 应用进程的 W本地 Binder 服务实体传入了。在WMS端接收到之后，就能通过W的远端代理和应用进程通信了。

伪代码：
```java
WindowManagerImpl.addView(...)--->
WindowManagerGlobal.addView(...)--->
	parentWindow.adjustLayoutParamsForSubWindow(...)--->
	new ViewRootImpl(...)--->
		WindowManagerGlobal.getWindowSession()--->
		new W(this)--->
		Choreographer.getInstance()--->
	mViews.add(view)--->
    mRoots.add(root)--->
    mParams.add(wparams)--->
	ViewRootImpl.setView(...)--->
		mWindowSession.addToDisplay(...)--->
```




IWindowSession调用addToDisplay，进入 WMS 端 
```java
//[Sesstion.java'
    @Override
    public int addToDisplay(IWindow window, int seq, WindowManager.LayoutParams attrs,
            int viewVisibility, int displayId, Rect outContentInsets, Rect outStableInsets,
            Rect outOutsets, InputChannel outInputChannel) {
        //注意这里的mService是在前面WMS调用openSession方法构造Session时传递过来的引用
        return mService.addWindow(this, window, seq, attrs, viewVisibility, displayId,
                outContentInsets, outStableInsets, outOutsets, outInputChannel);
    }
```

其实Session就是一个中间人而已，APP应用程序段通过它的跨进程Binder能力发送来的请求，它想都不想直接传给WMS服务进行处理了。那这个Session有何用。


下面WMS开始真正开始窗口添加。

```java
//[WindowManagerService.java]
    /**
     * All currently active sessions with clients.
     */
    final ArraySet<Session> mSessions = new ArraySet<>();

    /**
     * Mapping from an IWindow IBinder to the server's Window object.
     * This is also used as the lock for all of our state.
     * NOTE: Never call into methods that lock ActivityManagerService while holding this object.
     */
    final HashMap<IBinder, WindowState> mWindowMap = new HashMap<>();

    /**
     * Mapping from a token IBinder to a WindowToken object.
     */
    final HashMap<IBinder, WindowToken> mTokenMap = new HashMap<>();



     public int addWindow(Session session, IWindow client, int seq,
            WindowManager.LayoutParams attrs, int viewVisibility, int displayId,
            Rect outContentInsets, Rect outStableInsets, Rect outOutsets,
            InputChannel outInputChannel) {
		//这里的client为IWindow的代理对象，用于WMS和Activity进行通信
        ...
        WindowState attachedWindow = null;
		...

        synchronized(mWindowMap) {
			//判断我们添加的窗口是否已经存在
            if (mWindowMap.containsKey(client.asBinder())) {
                Slog.w(TAG_WM, "Window " + client + " is already added");
                return WindowManagerGlobal.ADD_DUPLICATE_ADD; // 返回重复添加
            }
            ..

            boolean addToken = false;
			//根据attrs.token从mTokenMap中取出应用程序窗口在WMS服务中的描述符WindowToken
            WindowToken token = mTokenMap.get(attrs.token);
            AppWindowToken atoken = null;
            boolean addToastWindowRequiresToken = false;

           ...
			//(注意这里只是因为分析的是Activity窗口的添加而已)创建WindowState对象
            WindowState win = new WindowState(this, session, client, token,
                    attachedWindow, appOp[0], seq, attrs, viewVisibility, displayContent);
 
            ...

			//以键值对<IWindow.Proxy/Token,WindowToken>形式保存到mTokenMap表中
			//在Activity的窗口添加过程中，不会走到此处，因为在Activity启动中在创建的时候已经有添加了，这个在Token传递的篇章里面已经有分析过了
            if (addToken) {
                mTokenMap.put(attrs.token, token);
            }
            win.attach();
			//以键值对<IWindow的代理对象,WindowState>形式保存到mWindowMap表中
            mWindowMap.put(client.asBinder(), win);
			
			...

            boolean imMayMove = true;

            if (type == TYPE_INPUT_METHOD) {
				...
            } else if (type == TYPE_INPUT_METHOD_DIALOG) {
				...
                addWindowToListInOrderLocked(win, true);
            } else {
                addWindowToListInOrderLocked(win, true);//将WindowState添加到关联的AppWindowToken中
				...
            }
		...
        return res;
    }
```



从我们前面的分析可知知道当应用程序进程添加一个DecorView到窗口管理器时，会为当前添加的窗口创建ViewRootImpl对象，同时构造了一个W本地Binder对象，无论是窗口视图对象DecorView还是ViewRootImpl对象，都只是存在于应用程序进程中，在添加窗口过程中仅仅将该窗口的W对象传递给WMS服务，经过Binder传输后，到达WMS服务端进程后变为IWindow.Proxy代理对象，因此该函数的参数client的类型为IWindow.Proxy，这个就是我们client参数的终极由来


1. 根据attrs.token从mTokenMap取出应用程序窗口在WMS服务中的描述符WindowToken。attrs.token 是key。
2. 构建一个 WindowState
3. 将 WindowState 以键值对<IWindow的代理对象,WindowState>形式保存到mWindowMap表中



![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/tokenmap.png)



后续调用：addWindowToListInOrderLocked(win, true);//将WindowState添加到关联的AppWindowToken中


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/apptoke.png)



# 结束

* 应用程序端的每个窗口对应的ViewRootImpl对持有一个W对象，而WMS端持有它的代理端用于和APP的信息回调
* 然后每个应用程序进程都持有一个与WMS服务会话通道IWindowSession，系统中创建的所有IWindowSession都被记录到WMS服务的mSessions成员变量中，这样WMS就可以知道自己正在处理那些应用程序的请求。
* 然后为每个窗口在WMS端创建一个WindowState对象，该对象是应用程序窗口在WMS服务端的描述符，有点类似于Activity在AMS端的描述符ActivityRecord




现在只是把 WindowState 注册到WMS 了。但是并没有送到 SurfaceFlinger去绘制。

如何送到 SurfaceFlinger的呢？


https://blog.csdn.net/windskier/article/details/7041610

