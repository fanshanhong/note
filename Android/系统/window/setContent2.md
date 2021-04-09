---
title: setContentView2

date: 2021-03-08

categories: 
   - Android

tags: 
   - Android 


description: ​
---

<!-- TOC -->

- [setContentView](#setcontentview)
- [installDecor](#installdecor)
- [inflate](#inflate)
- [onContentChanged](#oncontentchanged)
- [setContentView源码分析总结](#setcontentview源码分析总结)
- [setContentView完以后界面显示](#setcontentview完以后界面显示)

<!-- /TOC -->

# setContentView


```java
public void setContentView(int layoutResID) {
        // Note: FEATURE_CONTENT_TRANSITIONS may be set in the process of installing the window
        // decor, when theme attributes and the like are crystalized. Do not check the feature
        // before this happens.
        if (mContentParent == null) {
            installDecor();
        } else if (!hasFeature(FEATURE_CONTENT_TRANSITIONS)) {
            mContentParent.removeAllViews();
        }

        if (hasFeature(FEATURE_CONTENT_TRANSITIONS)) {
            final Scene newScene = Scene.getSceneForLayout(mContentParent, layoutResID,
                    getContext());
            transitionTo(newScene);
        } else {
            mLayoutInflater.inflate(layoutResID, mContentParent);
        }
        final Callback cb = getCallback();
        if (cb != null && !isDestroyed()) {
            cb.onContentChanged();
        }
    }
```


首先判断mContentParent是否为null，也就是第一次调用;

如果是第一次调用，则调用installDecor()方法，否则 判断是否设置FEATURE_CONTENT_TRANSITIONS Window属性（默认false），如果没有就移除该mContentParent内所有的所有子View；这也是 setContentView 为啥能多次调用的原因, 只要mContentParent!=null,就会先移除再 add,这样就不报错.

mLayoutInflater.inflate(layoutResID, mContentParent);将我们的资源文件通过LayoutInflater对象转换为View树，并且添加至mContentParent视图中.

上面的 setContentView 方法传入参数  是:layoutResId, 因此使用 LayoutInflater.inflate.
如果使用 `setContentView(View view)` / `setContentView(View view, ViewGroup.LayoutParams params) `这样的方法, 就会调用  `mContentParent.addView(view, params);`

如果你在Activity中调运setContentView(View view)方法，实质也是调运setContentView(View view, ViewGroup.LayoutParams params)，只是LayoutParams设置为了MATCH_PARENT而已。


# installDecor


```java
   private void installDecor() {
        if (mDecor == null) {
            mDecor = generateDecor();
            mDecor.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            mDecor.setIsRootNamespace(true);
            if (!mInvalidatePanelMenuPosted && mInvalidatePanelMenuFeatures != 0) {
                mDecor.postOnAnimation(mInvalidatePanelMenuRunnable);
            }
        }
        if (mContentParent == null) {
            //根据窗口的风格修饰，选择对应的修饰布局文件，并且将id为content的FrameLayout赋值给mContentParent
            mContentParent = generateLayout(mDecor);
            //......
            //初始化一堆属性值
        }
    }
```

DecorView是PhoneWindow的内部类，是FrameLayout的子类，是对FrameLayout进行功能的修饰（所以叫DecorXXX） 。判断mDecor对象是否为空，如果为空则调用generateDecor()创建一个DecorView    

```java
 protected DecorView generateDecor() {
        return new DecorView(getContext(), -1);
    }
```
`generateDecor()` 很简单, 直接 `new DecorView()`


回到`installDecor()`方法继续往下看，当mContentParent对象不为空则调用generateLayout()方法去创建mContentParent对象。 第一次 mCntentParent 肯定空.

```java
 protected ViewGroup generateLayout(DecorView decor) {
        // Apply data from current theme.

        TypedArray a = getWindowStyle();

        //......
        //依据主题style设置一堆值进行设置

        int layoutResource;
        int features = getLocalFeatures();
        //......
        //根据设定好的features值选择不同的窗口修饰布局文件,得到layoutResource值

        //把选中的窗口修饰布局文件添加到DecorView对象里，并且指定mContentRoot值
        View in = mLayoutInflater.inflate(layoutResource, null);
        decor.addView(in, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        mContentRoot = (ViewGroup) in;

        ViewGroup contentParent = (ViewGroup)findViewById(ID_ANDROID_CONTENT);
        if (contentParent == null) {
            throw new RuntimeException("Window couldn't find content container view");
        }

        //......
        //继续一堆属性设置，完事返回contentParent
        return contentParent;
    }
```

上面方法主要作用就是根据窗口的 FEATURE,THEME等  为该窗口选择不同的窗口根布局文件 layoutResource 。

得到 layoutResource 之后,把它转成 View 树,赋值给 mContentRoot , 并将该 View 添加到 mDecor 中去.

然后从 DecorView 中获取id为content的FrameLayout返回给mContentParent对象。所以installDecor方法实质就是产生mDecor和mContentParent对象。

现在,在 DecorView 中的嵌套关系为:DecorView 在最外层,里面有个 mContentRoot(根据窗口的 FEATURE,THEME等  为该窗口选择不同的窗口根布局文件),再内部,有个 mContentParent(即:id="content"的 ViewGroup).

在这里顺带提一下：还记得我们平时写应用Activity时设置的theme或者feature吗（全屏啥的，NoTitle等）？我们一般是不是通过XML的android:theme属性或者java的requestFeature()方法来设置的呢？譬如：

通过java文件设置：

requestWindowFeature(Window.FEATURE_NO_TITLE);

通过xml文件设置：

android:theme="@android:style/Theme.NoTitleBar"

对的，其实我们平时requestWindowFeature()设置的值就是在这里通过getLocalFeature()获取的；而android:theme属性也是通过这里的getWindowStyle()获取的。

所以这下你应该就明白在java文件设置Activity的属性时必须在setContentView方法之前调用requestFeature()方法的原因了吧。


# inflate

回过头可以看见上面PhoneWindow类的setContentView方法最后通过调用mLayoutInflater.inflate(layoutResID, mContentParent);或者mContentParent.addView(view, params);语句将我们的xml或者java View插入到了mContentParent（id为content的FrameLayout对象）ViewGroup中。


到这里, PhoneWindow 中的 DecorView 就构建好了.

层次关系为:  DecorView->mContentParent(根据 FEATURE/THEME 选择的布局)->mContentRoot(mContentParent中 id="content"的 ViewGroup)->我们自己添加的布局


# onContentChanged

setContentView最后还会调用一个Callback接口的成员函数onContentChanged来通知对应的Activity组件视图内容发生了变化。

```java
 public void setContentView(int layoutResID) {
        ......
        final Callback cb = getCallback();
        if (cb != null && !isDestroyed()) {
            cb.onContentChanged();
        }
    }
```
首先通过getCallback获取对象cb（回调接口），PhoneWindow没有重写Window的这个方法，所以到抽象类Window中可以看到：

```java
  /**
     * Return the current Callback interface for this window.
     */
    public final Callback getCallback() {
        return mCallback;
    }
```

这个mCallback在哪赋值的呢.

是在 创建 反射创建 Activity 的时候,调用了 Activity.attach 方法.
```java
final void attach(Context context, ActivityThread aThread,
            Instrumentation instr, IBinder token, int ident,
            Application application, Intent intent, ActivityInfo info,
            CharSequence title, Activity parent, String id,
            NonConfigurationInstances lastNonConfigurationInstances,
            Configuration config, String referrer, IVoiceInteractor voiceInteractor) {
        attachBaseContext(context);
        ...
        mWindow.setCallback(this);
        ...
}
```

也就是说Activity类实现了Window的Callback接口。那就是看下Activity实现的onContentChanged方法。如下：
```java
    public void onContentChanged() {
    }
```
咦？onContentChanged是个空方法。那就说明当Activity的布局改动时，即setContentView()或者addContentView()方法执行完毕时就会调用该方法。

所以当我们写App时，Activity的各种View的findViewById()方法等都可以放到该方法中，系统会帮忙回调。



# setContentView源码分析总结

可以看出来setContentView整个过程主要是如何把Activity的布局文件或者java的View添加至窗口里，上面的过程可以重点概括为：

1. 创建一个DecorView的对象mDecor，该mDecor对象将作为整个应用窗口的根视图。

2. 依据Feature等style theme创建不同的窗口修饰布局文件，并且通过findViewById获取Activity布局文件该存放的地方（窗口修饰布局文件中id为content的FrameLayout）。

3. 将Activity的布局文件添加至id为content的FrameLayout内。


# setContentView完以后界面显示

注意：Activity调运setContentView方法自身不会显示布局的.

在 Activity 反射创建好后, 并且回调了自己的 onCreate 和 onStart 方法, AMS 会再通知 Activity 回调它的 onResume 方法.

在 handleResumeActivity  中
```java
 final void handleResumeActivity(IBinder token,
            boolean clearHide, boolean isForward, boolean reallyResume) {
        // If we are getting ready to gc after going to the background, well
        // we are back active so skip it.
        ......
        // TODO Push resumeArgs into the activity for consideration
        ActivityClientRecord r = performResumeActivity(token, clearHide);

        if (r != null) {
            ......
            // If the window hasn't yet been added to the window manager,
            // and this guy didn't finish itself or start another activity,
            // then go ahead and add the window.
            ......
            // If the window has already been added, but during resume
            // we started another activity, then don't yet make the
            // window visible.
            ......
            // The window is now visible if it has been added, we are not
            // simply finishing, and we are not starting another activity.
            if (!r.activity.mFinished && willBeVisible
                    && r.activity.mDecor != null && !r.hideForNow) {
                ......
                if (r.activity.mVisibleFromClient) {
                    r.activity.makeVisible();
                }
            }
            ......
        } else {
            // If an exception was thrown when trying to resume, then
            // just end this activity.
            ......
        }
    }
```

调用Activity的makeVisible方法显示我们上面通过setContentView创建的mDecor视图族。所以我们看下Activity的makeVisible方法，如下：

```java
void makeVisible() {
        if (!mWindowAdded) {
            ViewManager wm = getWindowManager();
            wm.addView(mDecor, getWindow().getAttributes());
            mWindowAdded = true;
        }
        mDecor.setVisibility(View.VISIBLE);
    }
```

通过DecorView（FrameLayout，也即View）的setVisibility方法将View设置为VISIBLE，至此显示出来。
