---
title: APP与WindowManagerService通信

date: 2021-03-08

categories: 
   - Android

tags: 
   - Android 


description: ​
---

<!-- TOC -->

- [概览](#概览)
- [SystemServiceRegistry](#systemserviceregistry)
- [](#)

<!-- /TOC -->

# 概览

WMS(这里为了后续简述方便将WindowManagerService简称为WMS)服务却是运行在system_server进程中的，而通常我们的窗口的需求最开始的发起端通常是在Android应用程序进程端的，而做为窗口最最具体的载体Android应用程序中关于窗口的各种操作都离不开和WMS服务之间的跨进程交互。



![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/WMS_binder.png)



APP端 通过IWindowManager.Stub.Proxy 与 WMS通信 ，openSession，拿到WMS 端的 Session的远端代理，后续APP端想要同WMS 通信， 全都通过 IWindowSession.Stub.Proxy 这个远端代理。

然后，APP端，把W(IWindow.Stub)发给 WMS 端，后续WMS 端想要对APP端发起请求， 就通过  IWindow.Stub.Proxy 进行。



# SystemServiceRegistry



Activity的启动中，  AMS先通知 APP 端  `IApplicationThread.bindApplication()`，在bindApplication中主要是创建Application，并回调构造。然后，AMS 继续通知 APP端  IApplicationThread.scheduleLaunchActivity，这里会执行Activity的创建和 回调。


在反射创建 Activity 后，调用activity.atach()

attach中会创建 PhoneWindow `mWindow = new PhoneWindow(this, window);`

然后
```java
mWindow.setWindowManager(
                (WindowManager)context.getSystemService(Context.WINDOW_SERVICE),
                mToken, mComponent.flattenToString(),
                (info.flags & ActivityInfo.FLAG_HARDWARE_ACCELERATED) != 0);
```

context.getSystemService() 最终会调用到： ContextImpl中getSystemService()的实现

```java
//[ContextImpl.java]
    @Override
    public Object getSystemService(String name) {
        return SystemServiceRegistry.getSystemService(this, name);
    }
```

```java
//[SystemServiceRegistry.java]
    private static final HashMap<Class<?>, String> SYSTEM_SERVICE_NAMES =
            new HashMap<Class<?>, String>();
    private static final HashMap<String, ServiceFetcher<?>> SYSTEM_SERVICE_FETCHERS =
            new HashMap<String, ServiceFetcher<?>>();
    public static Object getSystemService(ContextImpl ctx, String name) {
        ServiceFetcher<?> fetcher = SYSTEM_SERVICE_FETCHERS.get(name);
        return fetcher != null ? fetcher.getService(ctx) : null;
    }
```

直接从SYSTEM_SERVICE_FETCHERS哈希列表中根据服务名称进行查找，这里我们看下SYSTEM_SERVICE_FETCHERS是啥时候被填充的，我们接着查找：

```java

//[SystemServiceRegistry.java]
    private static <T> void registerService(String serviceName, Class<T> serviceClass,
            ServiceFetcher<T> serviceFetcher) {
        SYSTEM_SERVICE_NAMES.put(serviceClass, serviceName);
        SYSTEM_SERVICE_FETCHERS.put(serviceName, serviceFetcher);
    }
```

可以看到在registerService()方法中注册了一系列的服务，我接着继续查找看看那里调用了它

```java
//[SystemServiceRegistry.java]
    static {
	    // Not instantiable.
	    private SystemServiceRegistry() { }
    	...
    	registerService(Context.ACTIVITY_SERVICE, ActivityManager.class,
                new CachedServiceFetcher<ActivityManager>() {
            @Override
            public ActivityManager createService(ContextImpl ctx) {
                return new ActivityManager(ctx.getOuterContext(), ctx.mMainThread.getHandler());
            }});
        registerService(Context.WINDOW_SERVICE, WindowManager.class,
                new CachedServiceFetcher<WindowManager>() {
            @Override
            public WindowManager createService(ContextImpl ctx) {
            	//是不是很熟悉了
                return new WindowManagerImpl(ctx);
            }});       
}
```

这里我们可以看到SystemServiceRegistry类的static静态方法区中注册了一系列的服务，而我们的WindowManagerImpl实例对象也被注册到了里面.

那为啥就说 通过这个 WindowManager 就能喝 WMS 通信了呢？  WindowManager 不是 继承自  ViewManager 么？

因为这里 createService。当我们getSystemService的时候，拿到的是这里返回的 WindowManagerImpl对象。在 WindowManagerImpl对象中，持有 `private final WindowManagerGlobal mGlobal = WindowManagerGlobal.getInstance();`。  在这个 Global 中 持有一个：`private static IWindowManager sWindowManagerService;` 这个sWindowManagerService是  WMS 的远端代理 IWindowManager.Stub.Proxy对象。这样，一层一层的，就能通WMS通信了。


这么麻烦么， 那 ActivityManager 呢？

可以看到，registerService(Context.ACTIVITY_SERVICE） 直接就是 ActivityManager。然后我们看下 ActivityManager 是怎么同AMS 通信的？我们从ActivityManager 中随便找了个方法：

```java
    public List<RecentTaskInfo> getRecentTasksForUser(int maxNum, int flags, int userId)
            throws SecurityException {
        try {
            return ActivityManagerNative.getDefault().getRecentTasks(maxNum,
                    flags, userId).getList();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

```
可以看到，直接就是  ActivityManagerNative.getDefault() 拿到的就是 ActivityManagerProxy对象了，然后进行 Binder 跨进程调用。不像 WindowManager 那么麻烦。

我们知道，各个Framework 的系统服务（Service）都是注册在 serviceManager 上的，比如 WMS， AMS，PMS，都是注册在上面的。什么时候注册的？系统系统的时候，Zygote进程里注册的。注册后，如果我们想要拿到 Service 的远端代理的话，我们要自己去ServiceManager 上  getService()  才能拿到。 Android 相当于为我们提供了方便，不用我们自己去ServiceManager 自己拿了，都弄好了，同时，还给包装成更好用的 各种 Manager， 比如  ActivityManager， WindowManager， 全部放在了这个 SystemServiceRegistry 里面。

当我们用的时候， 直接调用 getSystemService， 就从 SystemServiceRegistry 里面拿到Android 给我们提供好的 Manager， 然后调用里面的方法，内部帮我们去请求到对应的 Service了，这些Service 都在 system_server 进程中。



# 

在添加窗口的时候， 调用顺序：


```java
WindowManagerImpl.addView(...)--->
WindowManagerGlobal.addView(...)--->
	new ViewRootImpl(...)--->
		WindowManagerGlobal.getWindowSession()--->
		new W(this)--->
	ViewRootImpl.setView(...)--->
		mWindowSession.addToDisplay(...)--->
```


new ViewRootImpl(...) 的时候
```java
 public ViewRootImpl(Context context, Display display) {
        mContext = context;
        mWindowSession = WindowManagerGlobal.getWindowSession();
    ..
 }
```


```java
// WindowManagerGlobal.java
public static IWindowSession getWindowSession() {
        synchronized (WindowManagerGlobal.class) {
            if (sWindowSession == null) {
                try {
                    InputMethodManager imm = InputMethodManager.getInstance();
                    IWindowManager windowManager = getWindowManagerService();
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

```java
// WindowManagerGlobal.java
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

分析：先调用  getWindowManagerService， 在里面拿到了  IWindowManager.Stub 的 远端代理：IWindowManager.Stub.Proxy，也就是 WMS 的远端代理，赋给 sWindowManagerService。然后Binder跨进程调用到的WMS服务的openSession()方法。
```java
//[windowManagerService.java]
    @Override
    public IWindowSession openSession(IWindowSessionCallback callback, IInputMethodClient client,
            IInputContext inputContext) {
        //异常检测
        if (client == null) throw new IllegalArgumentException("null client");
        if (inputContext == null) throw new IllegalArgumentException("null inputContext");
        //直接明了，构建一个Session实例
        Session session = new Session(this, callback, client, inputContext);
        return session;
    }
```

WMS端对openSession()的处理简单明了，直接通过传递过来的参数构建了Session的Binder实体端，然后跨Binder返回回去。至此构建了如下的一条Binder通信通道：
![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/IWindowSession2.png)


然后APP端进程就拿到了 这个  Session  的  Binder 代理端对象：IWindowSession.Stub.Proxy。这样，APP端进程，就能通过这个   代理给WMS 发消息了。


但是，此时，WMS 还不能主动给 App端进程发消息。  类似AMS 中还需要还一个 IApplicationThread 一样的东西。

在 ViewRootImpl 的构造中，还创建了一个 W实体。`mWindow = new W(this);`  

下来在  ViewRootImpl.setView 中，调用了如下：
```java
requestLayout();

 res = mWindowSession.addToDisplay(mWindow, mSeq, mWindowAttributes,
                            getHostVisibility(), mDisplay.getDisplayId(),
                            mAttachInfo.mContentInsets, mAttachInfo.mStableInsets,
                            mAttachInfo.mOutsets, mInputChannel);
```

就是使用了刚才创建好的  IWindowSession.Stub.Proxy 代理对象，调用它的  addToDisplay 方法，并且把这个 W实体（匿名Binder）传入了，这样远端 WMS 就能拿到 IWindow.Stub.Proxy 代理对象，然后WMS就能主动给APP进程发消息了。


让我们接着来看看mWindowSession的实体端是怎么处理addToDisplay方法的
```java
//[Sesstion.java'
    @Override
    public int addToDisplay(IWindow window, int seq, WindowManager.LayoutParams attrs,
            int viewVisibility, int displayId, Rect outContentInsets, Rect outStableInsets,
            Rect outOutsets, InputChannel outInputChannel) {
        //注意这里的mService是在前面WMS调用openSession方法构造Session时传递过来的引用
        return mService.addWindow(this, window, seq, attrs, viewVisibility, displayId,
                outContentInsets, outStableInsets, outOutsets, outInputChannel);//详见3.2
    }
```

其实Session就是一个中间人而已(掮客，不需要留下买路钱的那种)，APP应用程序段通过它的跨进程Binder能力发送来的请求，它想都不想直接传给WMS服务进行处理了/

那我们只能接着看WMS中的addWindow方法是怎么处理传递过来的W对应的Binder引用了

```java
//[WindowManagerService.java]
    public int addWindow(Session session, IWindow client, int seq,
            WindowManager.LayoutParams attrs, int viewVisibility, int displayId,
            Rect outContentInsets, Rect outStableInsets, Rect outOutsets,
            InputChannel outInputChannel) {

			//这里的client为IWindow的代理对象，用于WMS和Activity进行通信
			//session为前面创建的用于APP端和WMS通信的匿名Binder

			...
		     boolean addToken = false;
			//根据attrs.token从mWindowMap中取出应用程序窗口在WMS服务中的描述符WindowToken
            WindowToken token = mTokenMap.get(attrs.token);
            AppWindowToken atoken = null;
            if (token == null) {//第一次add的情况下token怎么会有值
            	...
            	token = new WindowToken(this, attrs.token, -1, false);
                addToken = true;
            }//应用程序窗口
			else if (type >= FIRST_APPLICATION_WINDOW && type <= LAST_APPLICATION_WINDOW) {
				...
			}
            ...
			//在WMS服务中为窗口创建WindowState对象
            WindowState win = new WindowState(this, session, client, token,
                    attachedWindow, appOp[0], seq, attrs, viewVisibility, displayContent);


			//以键值对<IWindow.Proxy/Token,WindowToken>形式保存到mTokenMap表中
            if (addToken) {
                mTokenMap.put(attrs.token, token);
            }
            // 这里涉及  SurfaceFlinger
            win.attach();
			//以键值对<IWindow的代理对象,WindowState>形式保存到mWindowMap表中
            mWindowMap.put(client.asBinder(), win);
            ...
}
```

在该方法中，为应用程序进程新增的窗口在 WMS 服务中创建对应的WindowState对象，并且将WMS接收应用程序进程的Session的Binder实体，应用程序进程中的W代理对象保存到WindowState中。

```java
//[WindowState.java]
    void attach() {
        if (WindowManagerService.localLOGV) Slog.v(
            TAG, "Attaching " + this + " token=" + mToken
            + ", list=" + mToken.windows);
        mSession.windowAddedLocked();
    }
```

调用Session的windowAddedLocked()函数来创建请求SurfaceFlinger的SurfaceSession对象，同时将接收应用程序进程请求的Session保存到WMS服务的mSessions数组中。


```java
//[WindowState.java]
    void windowAddedLocked() {
     
            mSurfaceSession = new SurfaceSession();

    }

```


SurfaceSession 的创建会调用 JNI，在 JNI 调用 nativeCreate()。

```java
static jlong nativeCreate(JNIEnv* env, jclass clazz) {
    SurfaceComposerClient* client = new SurfaceComposerClient();
    client->incStrong((void*)nativeCreate);
    return reinterpret_cast<jlong>(client);}


```

创建 SurfaceComposerClient 对象， 作为跟 SurfaceFlinger 通信的代理对象

```java
SurfaceComposerClient::SurfaceComposerClient() {
    //getComposerService() 将返回 SurefaceFlinger 的 Binder 代理端的 BpSurfaceFlinger 对象
    sp<ISurfaceComposer> sm(getComposerService());
    
    //先调用 SF 的 createConnection()，再调用_init
    _init(sm, sm->createConnection());
    if(mClient != 0) {
       Mutex::Autolock _l(gLock);
       
       //gActiveConnections 是全局变量，把刚才创建的 client 保存到这个 map 中去
       gActiveConnections.add(mClient->asBinder(), this);
    }
}

```

拿到SurefaceFlinger 的 Binder 代理端的 BpSurfaceFlinger 对象，调用  CreateConnection().




setView() 方法。在 setView() 中调用了 requestLayout() 

```java
public void requestLayout() {
   checkThread();
   mLayoutRequested = true;
   scheduleTraversals();
}

public void scheduleTraversals() {
    if(!mTraversalScheduled) {
       mTraversalScheduled = true;
       sendEmptyMessage(DO_TRAVERSAL); //发送 DO_TRAVERSAL 消息
    }
}

public void handleMessage(Message msg) {
   switch (msg.what) {
    ......
    case DO_TRAVERSAL:
        ......
        performTraversals();//调用 performTraversals()
        ......
        break;
    ......
    }
}

private void performTraversals() {
    finalView host = mView;//还记得这mView吗？它就是 DecorView
    booleaninitialized = false;
    booleancontentInsetsChanged = false;
    booleanvisibleInsetsChanged;
    
    try {
        relayoutResult= // 1. 关键函数relayoutWindow
        relayoutWindow(params, viewVisibility,insetsPending);
    }
    ......
    draw(fullRedrawNeeded);// 2. 开始绘制
    ......
}

private int relayoutWindow(WindowManager.LayoutParams params, int viewVisibility, boolean insetsPending)throws RemoteException {
       //原来是调用 IWindowSession 的 relayout()，暂且记住这个调用
       int relayoutResult = sWindowSession.relayout(mWindow, params, (int) (mView.mMeasuredWidth * appScale + 0.5f),  (int) (mView.mMeasuredHeight * appScale + 0.5f), viewVisibility, insetsPending, mWinFrame, mPendingContentInsets, mPendingVisibleInsets, mPendingConfiguration, mSurface); //mSurface 做为参数传进去了。
       }
   ......
}

private void draw(boolean fullRedrawNeeded) {
    Surface surface = mSurface;//mSurface 是 ViewRoot 的成员变量
    ......
    Canvascanvas;

    try {
       int left = dirty.left;
       int top = dirty.top;
       int right = dirty.right;
       int bottom = dirty.bottom;

       //从 mSurface 中 lock 一块 Canvas
       canvas = surface.lockCanvas(dirty);
       ......
       mView.draw(canvas);//调用 DecorView 的 draw 函数，canvas 就是画布
       ......
       //unlock 画布，屏幕上马上就能看到 View 的样子了
       surface.unlockCanvasAndPost(canvas);
    }
    ......
}
```


在 ViewRoot 构造时，会创建一个 Surface，它使用无参构造函数，代码如下所示：

```java
final Surface mSurface = new Surface()
```

此时创建完的 Surface 是空的，什么都没有。接着继续分析 relayoutWindow()，在 relayoutWindow() 中会调用 IWindowSession 的 relayout()，这是一个跨进程方法会调用到 WMS 中的 Session.relayout()，最后调用到 WindowManagerService.relayoutWindow()。

public int relayoutWindow(Session session,IWindow client,
           WindowManager.LayoutParams attrs, int requestedWidth,
           int requestedHeight, int viewVisibility, boolean insetsPending,
           Rect outFrame, Rect outContentInsets, Rect outVisibleInsets,
            Configuration outConfig, SurfaceoutSurface){
        .....

    try {
         //win 就是 WinState，这里将创建一个本地的 Surface 对象
        Surfacesurface = win.createSurfaceLocked();
        if(surface != null) {
            //先创建一个本地 surface，然后在 outSurface 的对象上调用 copyFrom
            //将本地 Surface 的信息拷贝到 outSurface 中，为什么要这么麻烦呢？
            outSurface.copyFrom(surface);
        ......
}



1. 在 App 进程中创建 PhoneWindow 后会创建 ViewRoot。
2. ViewRoot 的创建会创建一个 Surface，这个 Surface 其实是空的，通过与 WindowManagerService 通信 copyFrom() 一个 NativeSurface。
3. WindowManagerService 在与 SurfaceFlinger 通信时，会创建 SharedClient 一段共享内存，里面存放的是 SharedBufferStack 对应 SurfaceFlinger 中的 SurfaceLayer, 每个 Layer 其实是一个 FrameBuffer，每个 FrameBuffer 中有两个 GraphicBuffer 记作 FrontBuffer 和 BackBuffer。

4. 在 SurfaceFlinger 中 SharedBufferServer 来管理 FrameBuffer。同时在 App 端 copyFrom() 出来 NativeSurface 时会创建一个 SharedBufferClient 与 SharedClient 这块共享内存关联。当客户端 addView() 或者需要更新 View 时，会通过 SharedBufferClient 写入数据到 ShareClient 中，SurfaceFlinger 中的 SharedBufferServer 接收到通知会将 FrameBuffer 中的数据传输到屏幕上。

HWComposer 是基于硬件来产生 VSync 信号的，来通知 SurfaceFlinger 重绘控制显示的帧率。







