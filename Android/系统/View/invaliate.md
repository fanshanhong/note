---
title: View invalidate

date: 2020-03-08

categories: 
   - Android

tags: 
   - Android 


description: ​
---

<!-- TOC -->

- [invalidate](#invalidate)
- [postInvalidate方法](#postinvalidate方法)
- [总结](#总结)
- [requestLayout](#requestlayout)

<!-- /TOC -->

# invalidate

看一下View类中的一些invalidate方法（ViewGroup没有重写这些方法）

```java
 /**
     * Mark the area defined by dirty as needing to be drawn. If the view is
     * visible, {@link #onDraw(android.graphics.Canvas)} will be called at some
     * point in the future.
     * <p>
     * This must be called from a UI thread. To call from a non-UI thread, call
     * {@link #postInvalidate()}.
     * <p>
     * <b>WARNING:</b> In API 19 and below, this method may be destructive to
     * {@code dirty}.
     *
     * @param dirty the rectangle representing the bounds of the dirty region
     */
    public void invalidate(Rect dirty) {
        final int scrollX = mScrollX;
        final int scrollY = mScrollY;
        // 内部实质调用invalidateInternal()
        invalidateInternal(dirty.left - scrollX, dirty.top - scrollY,
                dirty.right - scrollX, dirty.bottom - scrollY, true, false);
    }

    // 同上
    public void invalidate(int l, int t, int r, int b) {
        final int scrollX = mScrollX;
        final int scrollY = mScrollY;
        invalidateInternal(l - scrollX, t - scrollY, r - scrollX, b - scrollY, true, false);
    }


```

翻译: 把参数 dirty 定义的这块区域标记成需要重新绘制. 如果 这个 view 是可见的, 那 onDrar()方法将在未来的某一刻被调用
该方法必须在 UI 线程调用.如果要在非 UI 线程调用, 需要使用 postInvalidate()方法

```java
    /**
     * Invalidate the whole view. If the view is visible,
     * {@link #onDraw(android.graphics.Canvas)} will be called at some point in
     * the future.
     * 
     * This must be called from a UI thread. To call from a non-UI thread, call
     * {@link #postInvalidate()}.
     */
    public void invalidate() {
        //invalidate的实质还是调运invalidateInternal方法
        invalidate(true);
    }
```

翻译: 使整个 view 都无效. 如果 view 是可见的, onDraw() 方法将在未来某一时刻被调用.

注意,上面是使 Rect 的一块区域重绘. 这个是让整个 view 重绘.

```java
    /**
     * This is where the invalidate() work actually happens. A full invalidate()
     * causes the drawing cache to be invalidated, but this function can be
     * called with invalidateCache set to false to skip that invalidation step
     * for cases that do not need it (for example, a component that remains at
     * the same dimensions with the same content).
     * 这个是 invalidate() 被调用的时候真正发生了什么.
     * 一个完全的 invalidate() 方法调用, 会导致 drawing cache 也无效. 但是可以把参数 invalidateCache 设置成 false 来跳过这一步
     *
     * @param invalidateCache Whether the drawing cache for this view should be
     *            invalidated as well. This is usually true for a full
     *            invalidate, but may be set to false if the View's contents or
     *            dimensions have not changed.
     */
    void invalidate(boolean invalidateCache) {
        invalidateInternal(0, 0, mRight - mLeft, mBottom - mTop, invalidateCache, true);
    }
```

最终都是调用了 invalidateInternal()


```java
 void invalidateInternal(int l, int t, int r, int b, boolean invalidateCache,
            boolean fullInvalidate) {
        ......
            // Propagate the damage rectangle to the parent view.
            final AttachInfo ai = mAttachInfo;
            // 这个 mParent 就是 当前 view 的父布局
            final ViewParent p = mParent;
            if (p != null && ai != null && l < r && t < b) {
                final Rect damage = ai.mTmpInvalRect;
                //设置刷新区域
                damage.set(l, t, r, b);
                //传递调用Parent ViewGroup的invalidateChild方法
                p.invalidateChild(this, damage);
            }
            ......
    }
```


View的invalidate（invalidateInternal）方法实质是将要刷新区域直接传递给了父ViewGroup的invalidateChild方法，在invalidate中，调用父View的invalidateChild()方法，这是一个从当前向上级父View回溯的过程，每一层的父View都将自己的显示区域与传入的刷新Rect做交集 。所以我们看下ViewGroup的invalidateChild方法，源码如下：

```java
/**
     * Don't call or override this method. It is used for the implementation of
     * the view hierarchy.
     */
    public final void invalidateChild(View child, final Rect dirty) {
            ViewParent parent = this;
            ...
            do {
                ...
                parent = parent.invalidateChildInParent(location, dirty);
                ...
            } while (parent != null);
        }
    }
```

在 do while 循环中, 不断通过 parent.invalidateChildInParent() 回溯 父布局. 直到 parent==null

我们看下 ViewGroup 中  invalidateChildInParent()的实现
```java
 /**
     * Don't call or override this method. It is used for the implementation of
     * the view hierarchy.
     *
     * This implementation returns null if this ViewGroup does not have a parent,
     * if this ViewGroup is already fully invalidated or if the dirty rectangle
     * does not intersect with this ViewGroup's bounds.
     */
    public ViewParent invalidateChildInParent(final int[] location, final Rect dirty) {
        if ((mPrivateFlags & PFLAG_DRAWN) == PFLAG_DRAWN ||
                (mPrivateFlags & PFLAG_DRAWING_CACHE_VALID) == PFLAG_DRAWING_CACHE_VALID) {
            if ((mGroupFlags & (FLAG_OPTIMIZE_INVALIDATE | FLAG_ANIMATION_DONE)) !=
                        FLAG_OPTIMIZE_INVALIDATE) {
                return mParent;
            } else {
                return mParent;
            }
        }
        return null;
    }
```

该方法 说了以下几种返回 null 的情况:
1. 如果 这个 ViewGroup 没有 parent
2. 这个 ViewGroup 已经被完全的失效了
3. 这个 dirty rectangle 与 这个 ViewGroup 没有交集
这三种情况返回 null

其他情况都正常返回了父布局.

正常情况下, `parent.invalidateChildInParent` 层层找到上级，直到ViewRootImpl会返回null.

所以我们看下ViewRootImpl的invalidateChildInParent方法，如下：
```java
    @Override
    public ViewParent invalidateChildInParent(int[] location, Rect dirty) {
        ......
        scheduleTraversals();
        ......
        return null;
    }
```

scheduleTraversals会通过Handler的Runnable发送一个异步消息，调运doTraversal方法，然后最终调用performTraversals()执行重绘。开头背景知识介绍说过的，performTraversals就是整个View数开始绘制的起始调运地方，所以说View调运invalidate方法的实质是层层上传到父级，直到传递到ViewRootImpl后触发了scheduleTraversals方法，然后整个View树开始重新按照上面分析的View绘制流程进行重绘任务。



疑问: 为什么说 View 最终的 父布局是 ViewRootImpl??

我们都知道, 正常理解下, Activity 中 View 最终的父布局是 DecorView.

DecorView 里 有个 mContentRoot, mContentRoot 中 id 是 "content" 的 FrameLayout  是: mContentParent, 我们自己写的 布局, 是加入到了 mContentParent 中. 因此正常情况下, DecorView 是父布局.  那 ViewRootImpl 干嘛的.


首先要明确一下, 父布局说的是  View 类中的 成员`protected ViewParent mParent;` 

我想到了在 回调 Activity 的 onCreate 方法之后, onResume()方法之后, 有个把DecorView 加入 ViewRoomImpl 的地方.

```java
// ActivityThread.java
  final void handleResumeActivity(IBinder token,
            boolean clearHide, boolean isForward, boolean reallyResume) {
        // 回调 onResume
        ActivityClientRecord r = performResumeActivity(token, clearHide);

        if (r != null) {
            final Activity a = r.activity;
            ...
            if (r.window == null && !a.mFinished && willBeVisible) {
                r.window = r.activity.getWindow();
                View decor = r.window.getDecorView();
                decor.setVisibility(View.INVISIBLE);
                ViewManager wm = a.getWindowManager();
                WindowManager.LayoutParams l = r.window.getAttributes();
                a.mDecor = decor;
                l.type = WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
                l.softInputMode |= forwardBit;
                if (a.mVisibleFromClient) {
                    a.mWindowAdded = true;
                    wm.addView(decor, l);
                }
                ...
            }
        }
        
```

这里, 调用了 WindowManagerImpl 的 addView 方法.
接着调用到 WindowManagerGlobal 的 addView
最终调用: ViewRootImpl.setView(decorView, xx, xx)

在 ViewRootImpl.setView 中, 我们看到了 view.assignParent的影子

```java

    public void setView(View view, WindowManager.LayoutParams attrs, View panelParentView) {
        synchronized (this) {
           ...
           view.assignParent(this);
           ...
        }
    }
```

看下 view 的 assignParent 方法
```java
// View.java
   void assignParent(ViewParent parent) {
        if (mParent == null) {
            mParent = parent;
        } else if (parent == null) {
            mParent = null;
        } else {
            throw new RuntimeException("view " + this + " being added, but"
                    + " it already has a parent");
        }
    }

```

可以看到, 就是把 ViewRootImpl 赋值给了 DecorView 的 mParent 属性. 这样就明白了.




还想看个例子


在我们的 Activity 里, 一个 RelativeLayout, RelativeLayout内部有一个 LinearLayout, LinearLayout中一个 Button. 想看下  在 Button 上调用 invalidate 方法 , 是怎么一步一步回溯到 ViewRootImpl 的.

```
DecorView(FrameLayout)
    RelativeLayout
        LinearLayout
            Button
```

首先, 在 Button 上调用 invalidate, 内部调用到 invalidateInternal(), invalidateInternal()内部会调用 p.invalidateChild(this, damage);

这里, Button 的 parent 是 LinearLayout. 因此是: LinearLayout.invalidateChild(Button, Rect)

子类都不能重写 invalidateChild 方法. 因此调用 ViewGroup 的 invalidateChild().  在这里开始 do while 循环. 记得: 最开始 parent = this, 也就是现在 parent 是 LinearLayout 
```
    通过 invalidateChildInParent  拿到 LinearLayout 的 父 : RelativeLayout.  此时, parent 是 RelativeLayout. parent != null, 继续.

    RelativeLayout 通过 invalidateChildInParent 拿到他的父布局: DecorView(FrameLayout). 此时 , parent 是  DecorView(FrameLayout), parent!=null, 继续.

    DecorView(FrameLayout) 通过 invalidateChildInParent拿到他的父布局, ViewRootImpl, 此时 , parent 是  ViewRootImpl, parent!=null, 继续.

    ViewRootImpl 调用 invalidateChildInParent()方法, 先 执行了: scheduleTraversals();  然后返回 null.  
```

scheduleTraversals()会通过Handler的Runnable发送一个异步消息，调运doTraversal方法，然后最终调用performTraversals()执行重绘.

然后 ViewRootImpl 的 invalidateChildInParent()方法 返回 null, 就使得前面的 do while 循环结束了.


# postInvalidate方法


说 postInvalidate方法 之前, 先说一下 为啥  invalidate方法 必须在 UI 线程调用.  

因为最终进入 ViewRootImpl 的 invalidateChildInParent() 中,它的第一行就是在检查是否是 UI 线程, 如果不是就报错了
```java
    public ViewParent invalidateChildInParent(int[] location, Rect dirty) {
        checkThread();
        ...
    }
```



其他线程更新 UI 使用 postInvalidate方法，猜想也是通过 Handle 来发消息的.
```java

    public void postInvalidate() {
        postInvalidateDelayed(0);
    }
    
    public void postInvalidateDelayed(long delayMilliseconds) {
        // We try only with the AttachInfo because there's no point in invalidating
        // if we are not attached to our window
        final AttachInfo attachInfo = mAttachInfo;
        //核心，实质就是调运了ViewRootImpl.dispatchInvalidateDelayed方法
        if (attachInfo != null) {
            attachInfo.mViewRootImpl.dispatchInvalidateDelayed(this, delayMilliseconds);
        }
    }
```

ViewRootImpl 类的dispatchInvalidateDelayed方法
```java
// ViewRootImpl.java
    public void dispatchInvalidateDelayed(View view, long delayMilliseconds) {
        Message msg = mHandler.obtainMessage(MSG_INVALIDATE, view);
        mHandler.sendMessageDelayed(msg, delayMilliseconds);
    }
```

显然是通过 Handle 发送了一条 MSG_INVALIDATE 消息.

```java
public void handleMessage(Message msg) {
    ......
    switch (msg.what) {
    case MSG_INVALIDATE:
        ((View) msg.obj).invalidate();
        break;
    ......
    }
    ......
}
```

在UI Thread中拿到消息后, 又调用了View的invalidate();

突然想说, 这个 Handle 是 UI 线程的么. 它的 Looper 是 mainLooper 么?没看到他写啊.

想起来了, 这个 Handle 是在哪个线程创建的, 默认它的 Looper 就是哪个 Looper.

那这个 Handle 是啥时候创建的, 是在 ViewRootImpl 创建的时候.  ViewRootImpl 是啥时候创建的?  是在 目标进程创建完成之后, 在 新进程的第一个线程(UI 线程)执行onResume 之后, 调用 wm.addView(DecorView) 的时候, 创建了 ViewRoomImpl, 因此是在主线程的.


# 总结 

invalidate系列方法请求重绘View树（也就是draw方法），如果View大小没有发生变化就不会调用layout过程，并且只绘制那些“需要重绘的”View，也就是哪个View(View只绘制该View，ViewGroup绘制整个ViewGroup)请求invalidate系列方法，就绘制该View。

常见的引起invalidate方法操作的原因主要有：

* 直接调用invalidate方法.请求重新draw，但只会绘制调用者本身。
* 触发setSelection方法。请求重新draw，但只会绘制调用者本身。
* 触发setVisibility方法。 当View可视状态在INVISIBLE转换VISIBLE时会间接调用invalidate方法，继而绘制该View。当View的可视状态在INVISIBLE\VISIBLE 转换为GONE状态时会间接调用requestLayout和invalidate方法，同时由于View树大小发生了变化，所以会请求measure过程以及draw过程，同样只绘制需要“重新绘制”的视图。
* 触发setEnabled方法。请求重新draw，但不会重新绘制任何View包括该调用者本身。
    > 先调用 refreshDrawableState() 把颜色改了!!  再调用invalidate(true), 但是其实并没有重绘任何东西!!!
* 触发requestFocus方法。请求View树的draw过程，只绘制“需要重绘”的View。(因为有可能有些失去焦点, 有些得到焦点, 涉及多个控件)





# requestLayout

如下View的requestLayout源码


```java

 public void requestLayout() {
        ......
        if (mParent != null && !mParent.isLayoutRequested()) {
            //由此向ViewParent请求布局
            //从这个View开始向上一直requestLayout，最终到达ViewRootImpl的requestLayout
            mParent.requestLayout();
        }
        ......
    }
    
```

```java
 @Override
    public void requestLayout() {
        if (!mHandlingLayoutInLayoutRequest) {
            checkThread();
            mLayoutRequested = true;
            //View调运requestLayout最终层层上传到ViewRootImpl后最终触发了该方法
            scheduleTraversals();
        }
    }
```

类似于上面分析的invalidate过程, 类似于上面分析的invalidate过程，只是设置的标记不同，导致对于View的绘制流程中触发的方法不同而已.
scheduleTraversals会通过Handler的Runnable发送一个异步消息，调运doTraversal方法，然后最终调用performTraversals()执行重绘。

在接下来的 performTraversals()  方法中, 会根据 `mLayoutRequested = true;` 这个标记, 有选择性的执行部分内容.



————————————————
版权声明：本文为CSDN博主「工匠若水」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
原文链接：https://blog.csdn.net/yanbober/article/details/46128379
