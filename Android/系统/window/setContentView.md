---
title: setContentView

date: 2021-03-08

categories: 
   - Android

tags: 
   - Android 


description: ​
---

<!-- TOC -->

- [`setContentView`](#setcontentview)
- [Step1:`installDecor()`](#step1installdecor)
    - [先 `generateDecor(-1)`](#先-generatedecor-1)
    - [然后 `mContentParent = generateLayout(mDecor)`](#然后-mcontentparent--generatelayoutmdecor)
- [mLayoutInflater.inflate(layoutResID, mContentParent);](#mlayoutinflaterinflatelayoutresid-mcontentparent)

<!-- /TOC -->

# `setContentView`

Activity实例的onCreate()的 setContentView()调用，会调用 PhoneWindow的 setContentView。


可以看到Activity中很多关于View或者布局的相关操作都不是由Activity直接处理的而是交由mWindow窗口代为处理的，此时的mWindow就是我们Activity和View之间的代理和桥梁将二者之间关联起来了。

Activity通过setContentView将自定义布局View设置到了PhoneWindow上，而View通过WindowManagerImpl的addView()、removeView()、updateViewLayout()对View进行管理。


```java
//[PhoneWindow.java]
    @Override
    public void setContentView(int layoutResID) {
        if (mContentParent == null) {//在Activity没有调用setContentView之前，它对应的Window中的mContentParent为NULL
            installDecor();//完成DecorView的创建工作和初始化mContentParent
        } else if (!hasFeature(FEATURE_CONTENT_TRANSITIONS)) {
            mContentParent.removeAllViews();
        }

        if (hasFeature(FEATURE_CONTENT_TRANSITIONS)) {
           ...
        } else {
        	// 这里将我们的自定义的布局文件inflate到mContentParent中
            // 那这里的mContentParent表示什么呢，是我们contentView的父布局
            mLayoutInflater.inflate(layoutResID, mContentParent);
        }
        mContentParent.requestApplyInsets();
        ...
    }
```

上面的setContentView方法，归纳起来核心的只有两点

* 调用installDecor()方法，完成DecorView的创建工作和初始化mContentParent

* 接着通过LayoutInflater.inflate方法将我们传入的资源文件转换为view树，装载到mContentParent中



根据上面的分析，目前感觉，DecorView mContentParent  和 contentView（就是 layoutResID 对应的View）层级关系应该是：

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/DecorView1.png)




先解释一下 DecorView（mDecor） 和  mContentParent 的关系

```java
// This is the top-level view of the window, containing the window decor.
    private DecorView mDecor;
    
    // This is the view in which the window contents are placed. It is either
    // mDecor itself, or a child of mDecor where the contents go.
    ViewGroup mContentParent;
``` 
mDecor 是 Window 的顶层视图，mContentParent 是 Window 中 ，用于放 contents 的容器。放啥呢？应该就是放  setContentView()时所加入的 View 视图树。

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/activity_layout_relationship.png)

上面感觉的应该是对的哦。

然后我们一点一点来分析。






# Step1:`installDecor()`

```java
    //[PhoneWindow.java]
    private void installDecor() {
        mForceDecorInstall = false;
        if (mDecor == null) {//在没有进行相关的初始化之前mDecor肯定为null
            mDecor = generateDecor(-1);//创建DecorView,可以认为是我们Activity的根布局
            mDecor.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            mDecor.setIsRootNamespace(true);
            if (!mInvalidatePanelMenuPosted && mInvalidatePanelMenuFeatures != 0) {
                mDecor.postOnAnimation(mInvalidatePanelMenuRunnable);
            }
        } else {
            mDecor.setWindow(this);//将mDecor和Window关联起来
        }
        if (mContentParent == null) {
            mContentParent = generateLayout(mDecor);//这个也是重点，根据Activity的相关配置信息加载具体的mContentParent父布局
            ...
            /*
            	这里省略了与分析无关的代码，主要是对feature和style属性的一些判断和设置
            	并不是说明它们不重要，只是影响我们对整体框架的理解罢了
            	Activity呈现出五彩缤纷的世界，离不开各种feature和style的协助
            */
        } 
    }
```

第一次调用setContentView方法，所以此时的mDecor 和mContentParent肯定为null了，此时在installDecor()方法中会开始DecorView的创建工作和初始化mContentParent。

## 先 `generateDecor(-1)`

```java
//[PhoneWindow.java]
    protected DecorView generateDecor(int featureId) {
       ...
        return new DecorView(context, featureId, this, getAttributes());
    }
```

就是依据我们Actiiviyt相关的feature和上下文当相关信息直接new出一个实例对象而已，这里我们只需要知道的是DecorView是FrameLayout子类。执行完 `mDecor = generateDecor(-1)` 这一句，DecorView就好了，下面看看它里面的 mContentParent contentView 是怎么塞入的。addView么？


## 然后 `mContentParent = generateLayout(mDecor)`

```java
//[PhoneWindow.java]
    protected ViewGroup generateLayout(DecorView decor) {
        // 这个传入的 DecorView 就是刚刚创建好的那个 DecorView
	
        int layoutResource;
        int features = getLocalFeatures();
		//根据feathres给layoutResource赋值
		if ((features & (1 << FEATURE_SWIPE_TO_DISMISS)) != 0) {
			...
		}else if(...){
			...
		}else if ((features & (1 << FEATURE_NO_TITLE)) == 0) {
            if (mIsFloating) {
            } else if ((features & (1 << FEATURE_ACTION_BAR)) != 0) {//我们的加载的就是此分支
                layoutResource = a.getResourceId(
                        R.styleable.Window_windowActionBarFullscreenDecorLayout,
                        R.layout.screen_action_bar);
            } else {
				...
            }
        } else if ((features & (1 << FEATURE_ACTION_MODE_OVERLAY)) != 0) {
			...
        } else {
            layoutResource = R.layout.screen_simple;
        }

		//mDecor要改变的标记位
        mDecor.startChanging();
		//此处将layoutResource布局文件解析成 View 添加到了 DecorView 之中
        mDecor.onResourcesLoaded(mLayoutInflater, layoutResource);		
                        /****************************************************************

                                void onResourcesLoaded(LayoutInflater inflater, int layoutResource) {
                                    ...
                                    final View root = inflater.inflate(layoutResource, null);
                                    ..
                                    addView(root, 0, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
                                    ...
                                    mContentRoot = (ViewGroup) root;
        
                                }
                        *******************************************************************/

		//通过findViewById给contentParent赋值
		ViewGroup contentParent = (ViewGroup)findViewById(ID_ANDROID_CONTENT);
        if (contentParent == null) {
            throw new RuntimeException("Window couldn't find content container view");
        }
        ...
        //mDecor改变结束的标记位
		mDecor.finishChanging();

        return contentParent;
    }
```

这个 layoutResource 是啥呀？  说是： R.layout.screen_action_bar 这个？
我找出来看了一下：如下
![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/screen_action_bar.png)

前面就是根据不同的主题样式的属性值选择不同的布局文件，然后将其ID赋给 layoutResource，然后 调用： `mDecor.onResourcesLoaded(mLayoutInflater, layoutResource);`。

DecorView的  onResourcesLoaded  我也贴了出来：

就是先把  screen_action_bar  加载进来，赋值给root。然后把这个root添加到mDecorView中。同时，DecorView的成员变量 mContentRoot 也被赋值成root了。

现在 ，UI的嵌套关系是：  DecorView在最外层，里面一个 screen_action_bar.xml 这东西整的 root？  不是说好的 mContentParent么。。。懵了
同时，DecorView的成员 mContentRoot 也是这个 root。

这个 mContentRoot  跟 mContentParent 还不一样呢？？代码里也没个注释，不知道干嘛的。


> 并且此处蕴含着一个知识点就是我们要在setContentView() 之前执行 requestWindowFeature()才可以生效的原因

先继续看吧。

接着在的 mDecor 以及子节点中查找id为 ID_ANDROID_CONTENT 的一块区域，赋给contentParent

ID_ANDROID_CONTENT 又是啥 值是这个：com.android.internal.R.id.content 其实就是 @android:id/content。 在上面的 screen_action_bar.xml里，就有一个 @android:id/content FrameLayout。因此，找到的这个 contentParent  是 screen_action_bar.xml（mContentRoot） 里的一个子View

最后将找到的contentParent返回给最外层PhoneWindow的成员mContentParent.

> 这里有第一点我们需要注意的是，无论layoutResource的id或者说对应的xml文件怎么变，它一定要有一个id为ID_ANDROID_CONTENT的字段，否则会抛出异常。

到这里，嵌套关系是： 最外层DecorView 里，有个 root（也就是它的成员 mContentRoot）。
然后在  mContentRoot 里，有个 id 为   @android:id/content的 FrameLayout，也就是  mContentParent。


到这，Step1:`installDecor()` 就完了



# mLayoutInflater.inflate(layoutResID, mContentParent);

下面就将用户传入的布局转化为view再加入到mContentParent。所以  contentView 就放在 mContentParent里了！

因此，最终嵌套关系如下：

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/setContentView_view_relationship.png)



此时，我们已经将布局文件添加到DecorView中了，具体Android系统是怎么对窗口进行管理和添加以及绘制的，后面看。