---
title: View绘制

date: 2020-03-08

categories: 
   - Android

tags: 
   - Android 


description: ​
---


<!-- TOC -->

- [开始](#开始)
- [ViewRootImpl类的performTraversals()方法](#viewrootimpl类的performtraversals方法)
- [递归measure](#递归measure)
    - [ViewGroup](#viewgroup)

<!-- /TOC -->


# 开始


整个View树的绘图流程是在ViewRootImpl类的performTraversals()方法.

为啥呢.

在创建/启动 Activity 的时候, 当 AMS 通过 Binder 通知 Activity 所在进程执行 创建 Activity 实例, 创建了时候,为 Activity 创建了 PhoneWindow, 并且设置了 WindowManagerImpl 等相关对象, 并回调相关的回调方法.

在 onCreate 中,Activity 调用了 setContentView,完成DecorView的创建工作和初始化mContentParent,并且把用户传入的布局放入 mContentParent 中.

然后回调 onResume(), 在调用了 onResume() 之后, 进行 wm.addView. 这个 wm 是 Activity 的 WindowManagerImpl, 也是 PhoneWindow 的 WindowManagerImpl. 在 WindowManagerImpl.addView()中, 使用 RootViewImpl.setView(), 在 RootViewImpl.set()中, 执行 `requestLayout();`, 最终会调用performTraversals方法来完成View的绘制


以上就是 为啥说 View 树的绘制流程是从 ViewRootImpl类的performTraversals()方法 开始.



# ViewRootImpl类的performTraversals()方法

该函数做的执行过程主要是根据之前设置的状态，判断是否重新计算视图大小(measure)、是否重新放置视图的位置(layout)、以及是否重绘 (draw)，其核心也就是通过判断来选择顺序执行这三个方法中的哪个，如下：

```java
private void performTraversals() {
        ......
        //lp.width和lp.height在创建ViewGroup实例时等于 MATCH_PARENT
        // 为啥 是 MATCH_PARENT? 我觉得是在 setContentView 中, mContentParent(也就是 id="content"的那个 FrameLayout, layout_width 和 layout_height 都是 match_parent).
        // 代码在下方
        // 因此,这里lp.width 和 lp.height 这里都是 MATCH_PARENT
        // getRootMeasureSpec() 最开始就是 就是使用  屏幕宽高 和  mode 构造一个 MeasureSpec 出来, 给后面用
        int childWidthMeasureSpec = getRootMeasureSpec(mWidth, lp.width);
        int childHeightMeasureSpec = getRootMeasureSpec(mHeight, lp.height);
        ......
        mView.measure(childWidthMeasureSpec, childHeightMeasureSpec);
        ......
        mView.layout(0, 0, mView.getMeasuredWidth(), mView.getMeasuredHeight());
        ......
        mView.draw(canvas);
        ......
    }
```

screen_action_bar.xml

```xml

<com.android.internal.widget.ActionBarOverlayLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/decor_content_parent"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:splitMotionEvents="false"
    android:theme="?attr/actionBarTheme">
    <FrameLayout android:id="@android:id/content"
                 android:layout_width="match_parent"
                 android:layout_height="match_parent" />
</com.android.internal.widget.ActionBarOverlayLayout>
```

```java
 /**
     * Figures out the measure spec for the root view in a window based on it's
     * layout params.
     *
     * @param windowSize
     *            The available width or height of the window
     *
     * @param rootDimension
     *            The layout params for one dimension (width or height) of the
     *            window.
     *
     * @return The measure spec to use to measure the root view.
     */
    private static int getRootMeasureSpec(int windowSize, int rootDimension) {
        int measureSpec;
        switch (rootDimension) {

        case ViewGroup.LayoutParams.MATCH_PARENT:
            // Window can't resize. Force root view to be windowSize. 全屏
            measureSpec = MeasureSpec.makeMeasureSpec(windowSize, MeasureSpec.EXACTLY);
            break;
        ......
        }
        return measureSpec;
    }
```

上面传入参数后这个函数走的是 MATCH_PARENT，使用MeasureSpec.makeMeasureSpec方法组装一个MeasureSpec，MeasureSpec的specMode等于EXACTLY，specSize等于windowSize，也就是为何根视图总是全屏的原因。

                

performTraversals()方法流程

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/perform.png)



# 递归measure


```java
mView.measure(childWidthMeasureSpec, childHeightMeasureSpec);
```


```java
    /**
     * <p>
     * This is called to find out how big a view should be. The parent
     * supplies constraint information in the width and height parameters.
     * </p>
     *
     * <p>
     * The actual measurement work of a view is performed in
     * {@link #onMeasure(int, int)}, called by this method. Therefore, only
     * {@link #onMeasure(int, int)} can and must be overridden by subclasses.
     * </p>
     *
     *
     * @param widthMeasureSpec Horizontal space requirements as imposed by the
     *        parent
                               被父布局强加的横向空间大小要求.
                               第一次调用 measure 方法, 传入的 widthMeasureSpec 就是上面 getRootMeasureSpec返回值: 用屏幕宽度 和 MATCH_PARENT 构造出的一个measureSpec
     * @param heightMeasureSpec Vertical space requirements as imposed by the
     *        parent
     *
     * @see #onMeasure(int, int)
     */
     //final方法，子类不可重写
    public final void measure(int widthMeasureSpec, int heightMeasureSpec) {
        ......
        //回调onMeasure()方法
        onMeasure(widthMeasureSpec, heightMeasureSpec);
        ......
    }
```
翻译:
调用该方法计算这个 view 应该多大. 它的 parent 会在宽和高参数中提供约束信息.
真正的计算工作是在 onMeasure()方法中执行的.子类必须重写onMeasure()方法
因为measure方法是final的，不允许重写，所以View子类只能通过重写onMeasure来实现自己的测量逻辑


参数: widthMeasureSpec被父布局强加的横向空间大小要求.
> 第一次调用 measure 方法, 传入的 widthMeasureSpec 就是上面 getRootMeasureSpec返回值: 用屏幕宽度 和 MATCH_PARENT 构造出的一个measureSpec


在这里可以看出measure方法最终回调了View的onMeasure方法，我们来看下View的onMeasure源码，如下：
```java
/**
     * Measure the view and its content to determine the measured width and the
     * measured height. This method is invoked by {@link #measure(int, int)} and
     * should be overriden by subclasses to provide accurate and efficient
     * measurement of their contents.
     * 测量 view 和 它的 content, 以决定最后的测量结果.
     * 该方法被 measure()方法调用, onMeasure()方法应该被子类重写, 提供准确有效的计算方式
     *
     * <p>
     * <strong>CONTRACT:</strong> When overriding this method, you
     * <em>must</em> call {@link #setMeasuredDimension(int, int)} to store the
     * measured width and height of this view. Failure to do so will trigger an
     * <code>IllegalStateException</code>, thrown by
     * {@link #measure(int, int)}. Calling the superclass'
     * {@link #onMeasure(int, int)} is a valid use.
     * </p>
     * 强约定: 当重写 onMeasure()方法的时候,必须, 把测量好的 width 和 height 存一下.也就是调用一下 setMeasuredDimension()方法
     *
     * <p>
     * The base class implementation of measure defaults to the background size,
     * unless a larger size is allowed by the MeasureSpec. Subclasses should
     * override {@link #onMeasure(int, int)} to provide better measurements of
     * their content.
     * </p>
     * onMeasure 的默认实现就是计算背景的 size.
     * 子类应该重写 onMeasure() , 用来提供一个更好的计算方式.
     *
     * <p>
     * If this method is overridden, it is the subclass's responsibility to make
     * sure the measured height and width are at least the view's minimum height
     * and width ({@link #getSuggestedMinimumHeight()} and
     * {@link #getSuggestedMinimumWidth()}).
     * </p>
     *
     * @param widthMeasureSpec horizontal space requirements as imposed by the parent.
                               被父布局强加的横向空间大小要求.
                               第一次调用 measure 方法, 传入的 widthMeasureSpec 就是上面 getRootMeasureSpec返回值: 用屏幕宽度 和 MATCH_PARENT 构造出的一个measureSpec
     *                         The requirements are encoded with
     *                         {@link android.view.View.MeasureSpec}.
     * @param heightMeasureSpec vertical space requirements as imposed by the parent.
                               被父布局强加的纵向空间大小要求.
     *                         The requirements are encoded with
     *                         {@link android.view.View.MeasureSpec}.
     *
     * @see #getMeasuredWidth()
     * @see #getMeasuredHeight()
     * @see #setMeasuredDimension(int, int)
     * @see #getSuggestedMinimumHeight()
     * @see #getSuggestedMinimumWidth()
     * @see android.view.View.MeasureSpec#getMode(int)
     * @see android.view.View.MeasureSpec#getSize(int)
     */
     //View的onMeasure默认实现方法
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
    }
```



对于非ViewGroup的View而言，通过调用上面默认的onMeasure即可完成View的测量，当然你也可以重载onMeasure并调用setMeasuredDimension来设置任意大小的布局.

我们可以看见onMeasure默认的实现仅仅调用了setMeasuredDimension，setMeasuredDimension函数是一个很关键的函数，它对View的成员变量mMeasuredWidth和mMeasuredHeight变量赋值，measure的主要目的就是对View树中的每个View的mMeasuredWidth和mMeasuredHeight进行赋值，所以一旦这两个变量被赋值意味着该View的测量工作结束。既然这样那我们就看看设置的默认尺寸大小吧，可以看见setMeasuredDimension传入的参数都是通过getDefaultSize返回的，所以再来看下getDefaultSize方法源码，如下：
```java
public static int getDefaultSize(int size, int measureSpec) {
        int result = size;
        //通过MeasureSpec解析获取mode与size
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        switch (specMode) {
        case MeasureSpec.UNSPECIFIED:
            result = size;
            break;
        case MeasureSpec.AT_MOST:
        case MeasureSpec.EXACTLY:
            result = specSize;
            break;
        }
        return result;
    }
```

如果specMode等于AT_MOST或EXACTLY就返回specSize，这就是系统默认的规格。

```java
  protected int getSuggestedMinimumWidth() {
        return (mBackground == null) ? mMinWidth : max(mMinWidth, mBackground.getMinimumWidth());
    }

    protected int getSuggestedMinimumHeight() {
        return (mBackground == null) ? mMinHeight : max(mMinHeight, mBackground.getMinimumHeight());

    }
```

到此一次最基础的元素View的measure过程就完成了。测量的结果通过调用 setMeasuredDimension()方法 对View的成员变量mMeasuredWidth和mMeasuredHeight变量赋值.


## ViewGroup

View实际是嵌套的，而且measure是递归传递的，所以每个View都需要measure。
在ViewGroup中定义了measureChildren, measureChild, measureChildWithMargins方法来对子视图进行测量，measureChildren内部实质只是循环调用measureChild，measureChild和measureChildWithMargins的区别就是是否把margin和padding也作为子视图的大小。如下我们以ViewGroup中稍微复杂的measureChildWithMargins方法来分析：


```java
/**
     * Ask one of the children of this view to measure itself, taking into
     * account both the MeasureSpec requirements for this view and its padding
     * and margins. The child must have MarginLayoutParams The heavy lifting is
     * done in getChildMeasureSpec.
     *
     * @param child The child to measure
     * @param parentWidthMeasureSpec The width requirements for this view
     * @param widthUsed Extra space that has been used up by the parent
     *        horizontally (possibly by other children of the parent)
     * @param parentHeightMeasureSpec The height requirements for this view
     * @param heightUsed Extra space that has been used up by the parent
     *        vertically (possibly by other children of the parent)
     */
    protected void measureChildWithMargins(View child,
            int parentWidthMeasureSpec, int widthUsed,
            int parentHeightMeasureSpec, int heightUsed) {
        //获取子视图的LayoutParams
        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        //调整MeasureSpec
        //通过这两个参数以及子视图本身的LayoutParams来共同决定子视图的测量规格
        final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
                mPaddingLeft + mPaddingRight + lp.leftMargin + lp.rightMargin
                        + widthUsed, lp.width);
        final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec,
                mPaddingTop + mPaddingBottom + lp.topMargin + lp.bottomMargin
                        + heightUsed, lp.height);
        //调运子View的measure方法，子View的measure中会回调子View的onMeasure方法
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }
```


该方法就是对父视图提供的measureSpec参数结合自身的LayoutParams参数进行了调整，然后再来调用child.measure()方法，具体通过方法getChildMeasureSpec来进行参数调整。所以我们继续看下getChildMeasureSpec方法代码，如下：

```java
public static int getChildMeasureSpec(int spec, int padding, int childDimension) {
        //获取当前Parent View的Mode和Size
        int specMode = MeasureSpec.getMode(spec);
        int specSize = MeasureSpec.getSize(spec);
        //获取Parent size与padding差值（也就是Parent剩余大小），若差值小于0直接返回0
        int size = Math.max(0, specSize - padding);
        //定义返回值存储变量
        int resultSize = 0;
        int resultMode = 0;
        //依据当前Parent的Mode进行switch分支逻辑
        switch (specMode) {
        // Parent has imposed an exact size on us
        //默认Root View的Mode就是EXACTLY
        case MeasureSpec.EXACTLY:
            if (childDimension >= 0) {
                //如果child的layout_wOrh属性在xml或者java中给予具体大于等于0的数值
                //设置child的size为真实layout_wOrh属性值，mode为EXACTLY
                resultSize = childDimension;
                resultMode = MeasureSpec.EXACTLY;
            } else if (childDimension == LayoutParams.MATCH_PARENT) {
                //如果child的layout_wOrh属性在xml或者java中给予MATCH_PARENT
                // Child wants to be our size. So be it.
                //设置child的size为size，mode为EXACTLY
                resultSize = size;
                resultMode = MeasureSpec.EXACTLY;
            } else if (childDimension == LayoutParams.WRAP_CONTENT) {
                //如果child的layout_wOrh属性在xml或者java中给予WRAP_CONTENT
                //设置child的size为size，mode为AT_MOST
                // Child wants to determine its own size. It can't be
                // bigger than us.
                resultSize = size;
                resultMode = MeasureSpec.AT_MOST;
            }
            break;
        ......
        //其他Mode分支类似
        }
        //将mode与size通过MeasureSpec方法整合为32位整数返回
        return MeasureSpec.makeMeasureSpec(resultSize, resultMode);
    }
```
可以看见，getChildMeasureSpec的逻辑是通过其父View提供的MeasureSpec参数得到specMode和specSize，然后根据计算出来的specMode以及子View的childDimension（layout_width或layout_height）来计算自身的measureSpec，如果其本身包含子视图，则计算出来的measureSpec将作为调用其子视图measure函数的参数，同时也作为自身调用setMeasuredDimension的参数，如果其不包含子视图则默认情况下最终会调用onMeasure的默认实现，并最终调用到setMeasuredDimension。



通过上面分析可以看出measure过程主要就是从顶层父View向子View递归调用view.measure方法（measure中又回调onMeasure方法）的过程。具体measure核心主要有如下几点：


* View的measure方法是final的，不允许重载，View子类只能重载onMeasure来完成自己的测量逻辑。

* 最顶层DecorView测量时的MeasureSpec是由ViewRootImpl中getRootMeasureSpec方法确定的（LayoutParams宽高参数均为MATCH_PARENT，specMode是EXACTLY，specSize为物理屏幕大小）。

* ViewGroup类提供了measureChild，measureChild和measureChildWithMargins方法，简化了父子View的尺寸计算。

* 只要是ViewGroup的子类就必须要求LayoutParams继承子MarginLayoutParams，否则无法使用layout_margin参数。

* View的布局大小由父View和子View共同决定。

* 使用View的getMeasuredWidth()和getMeasuredHeight()方法来获取View测量的宽高，必须保证这两个方法在onMeasure流程之后被调用才能返回有效值。



--------------

父容器的限制与MeasureSpec
先假定，父容器是300dp*300dp的尺寸，如果子View的布局参数是
```
<!--场景1-->
android:layout_width="match_parent"
android:layout_height="match_parent"
```
那么按照我们的期望，希望子View的尺寸要是300dp*300dp，如果子View的布局参数是

```
<!--场景2-->
android:layout_width="100dp"
android:layout_height="100dp"
```
按照我们的期望，希望子View的尺寸要是100dp*100dp，如果子View的布局参数是

```
<!--场景3-->
android:layout_width="wrap_content"
android:layout_height="wrap_content"
```

按照我们的期望，希望子View的尺寸可以按照自己需求的尺寸来确定，但是最好不要超过300dp*300dp。

那么父容器怎么把这些要求告诉子View呢？MeasureSpec其实就是承担这种作用：**MeasureSpec是父控件提供给子View的一个参数，作为设定自身大小参考，只是个参考，要多大，还是View自己说了算**。先看下MeasureSpec的构成，MeasureSpec由size和mode组成，mode包括三种，UNSPECIFIED、EXACTLY、AT_MOST，size就是配合mode给出的参考尺寸，具体意义如下：

* UNSPECIFIED(未指定),父控件对子控件不加任何束缚，子元素可以得到任意想要的大小，这种MeasureSpec一般是由父控件自身的特性决定的。比如ScrollView，它的子View可以随意设置大小，无论多高，都能滚动显示，这个时候，size一般就没什么意义。
* EXACTLY(完全)，父控件为子View指定确切大小，希望子View完全按照自己给定尺寸来处理，跟上面的场景1跟2比较相似，这时的MeasureSpec一般是父控件根据自身的MeasureSpec跟子View的布局参数来确定的。一般这种情况下size>0,有个确定值。
* AT_MOST(至多)，父控件为子元素指定最大参考尺寸，希望子View的尺寸不要超过这个尺寸，跟上面场景3比较相似。这种模式也是父控件根据自身的MeasureSpec跟子View的布局参数来确定的，一般是子View的布局参数采用wrap_content的时候。


注:"MeasureSpec是父控件提供给子View的一个参数". 这里,父布局的 MeasureSpec不是直接提供了子 View 用的.需要将 父布局的 MeasureSpec 和 子 View 的 LayoutParams 计算,得到一个 childMeasureSpec, 给子 View 用.

因此:

**子View的MeasureSpec值根据子View的布局参数（LayoutParams）和父容器的MeasureSpec值计算得来的**

==

**传给 子 View measure / onMeasure  的  MeasureSpec一般是父控件根据自身的MeasureSpec跟子View的布局参数来确定的**


```
<DecorView> (MeasureSpec=EXACTLY+屏幕尺寸, 相当于是 Window 给的了. 然后 DecorView 根据上面传给它的 MeasureSpec ,以及子 View 的 LayoutParams, 构建出 childMeasureSpec. 把这个 childMeasureSpec 往下传,给了 LinearLayout)         

   <LinearLayout> (拿到 DecorView 传给它的 MeasureSpec(就是上面的childMeasureSpec), 然后再根据 子 View Button 的 LayoutParams,构建一个新的 MeasureSpec, 给了 Button, 让 Button 参考)

      <Button> (Button 拿到 LinearLayout 传给它的 MeasureSpec, 结合自己的 LayoutParams,确定自己的大小,调用 setMeasuredDimension() 方法即可)
```


      传递给子View的MeasureSpec是父容器根据自己的 MeasureSpec 及子View的布局参数所确定的，那么根MeasureSpec是谁创建的呢？也就是 DecorView , 它的 MeasureSpec 是谁给的?它就是顶层,没有父了.

      最初的 MeasureSpec 是直接根据Window的属性构建的，一般对于Activity来说，根 MeasureSpec 是EXACTLY+屏幕尺寸




来看一下ViewGroup源码中measureChild怎么为子View构造MeasureSpec的：
**传给 子 View measure / onMeasure  的  MeasureSpec一般是父控件根据自身的MeasureSpec跟子View的布局参数来确定的** 

```java
// 注意,这个方法一般是在父布局中调用的.
// 参数:parentWidthMeasureSpec 就是 父布局自己的 MeasureSpec
 protected void measureChild(View child, int parentWidthMeasureSpec,
         int parentHeightMeasureSpec) {
     // 拿到子 View 的 LayoutParams, 看到底是 match, 还是 wrap, 还是 xxdp
     final LayoutParams lp = child.getLayoutParams();

      // 根据父布局自己的 MeasureSpec &  子 View 的 LayoutParams(lp.width), 为子 View 生成 MeasureSpec,
      // 然后将生成好的 MeasureSpec 传入 measure, 进而进入到 子 View 的 onMeasure()方法中了
     final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
             mPaddingLeft + mPaddingRight, lp.width);
     final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec,
             mPaddingTop + mPaddingBottom, lp.height);

     child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
 }
```

这样就说通了  MeasureSpec 起什么作用了.

然后我们看下, 是如何根据父布局的 MeasureSpec 和 子 View 的 LayoutParams 计算得出新的 MeasureSpec 并传给 子 View 的.

getChildMeasureSpec() 方法
```java
// spec 就是 父布局的 MeasureSpec , childDimension 就是 子 View 的 layout_width / layout_height
// 比如, 这里我们计算 width 啊.  spce 就是 父布局的 parentWidthMeasureSpec, childDimension 就是 子 View 的 layout_width
public static int getChildMeasureSpec(int spec, int padding, int childDimension) {
   // 拿到父布局的 MeasureSpec 的 specMode 和 specSize, 注意是 父布局的
    int specMode = MeasureSpec.getMode(spec);
    int specSize = MeasureSpec.getSize(spec);

 //通过父view计算出的子view = 父大小-边距（父要求的大小，但子view不一定用这个值） 
 // 如果 小于 0, 就表示 padding 比父布局的大小还大了..那就让它=0 吧
    int size = Math.max(0, specSize - padding);

//子view想要的实际大小和模式（需要计算） 
    int resultSize = 0;
    int resultMode = 0;

    switch (specMode) {
    // Parent has imposed an exact size on us
    case MeasureSpec.EXACTLY: // 父布局指定了EXACTLY, 比如 match_parent, 比如 xxdp
        if (childDimension >= 0) { // 子 View 也指定了 xxdp, 这种情况, 就按照子 View 指定的 xxdp 来.
        // 我们也经常会遇到 子 View 指定 xxdp, 超过了父布局的大小,就显示不下了,就是这样情况
            resultSize = childDimension;
            resultMode = MeasureSpec.EXACTLY;
        } else if (childDimension == LayoutParams.MATCH_PARENT) {// 子 View 指定了 match_parent
        // 那就让子 View 占用父布局剩下的所有的空间就好了. 因此, 就按照父布局指定的 size 来, 因此 mode 就是 EXACTLY
            // Child wants to be our size. So be it.
            resultSize = size;
            resultMode = MeasureSpec.EXACTLY;
        } else if (childDimension == LayoutParams.WRAP_CONTENT) {// 子 View 指定了 wrap
        // 子 View 想要自己决定自己的大小, 但是呢, 不能超过父布局给的 size.
            // Child wants to determine its own size. It can't be
            // bigger than us.
            resultSize = size;
            resultMode = MeasureSpec.AT_MOST;
        }
        break;

    // Parent has imposed a maximum size on us
    case MeasureSpec.AT_MOST:    // 父布局指定了 wrap
        if (childDimension >= 0) { // 子 View 指定了 xxdp, 这种情况, 就按照子 View 指定的 xxdp 来.
        // 我们也经常会遇到 子 View 指定 xxdp, 超过了父布局的大小,就显示不下了,就是这样情况
            // Child wants a specific size... so be it
            resultSize = childDimension;
            resultMode = MeasureSpec.EXACTLY;
        } else if (childDimension == LayoutParams.MATCH_PARENT) { // 子 View 指定了 match_parent
        // 也就是说, 子 View 想要全部占用父布局剩余的空间, 但是父布局剩余空间不固定. 就给子 View 一个最大的限制好了.
            // Child wants to be our size, but our size is not fixed.
            // Constrain child to not be bigger than us.
            resultSize = size;
            resultMode = MeasureSpec.AT_MOST;
        } else if (childDimension == LayoutParams.WRAP_CONTENT) {// 父布局是 wrap, 子View 也是 wrap, 那应该跟上面这个情况一样吧.
            // Child wants to determine its own size. It can't be
            // bigger than us.
            resultSize = size;
            resultMode = MeasureSpec.AT_MOST;
        }
        break;
        // 最终, 使用计算好的  mode 和 size, 生成一个新的 MeasureSpec. 然后会把这个 新的 MeasureSpec 给了子 View.
    return MeasureSpec.makeMeasureSpec(resultSize, resultMode);
}
```



getChildMeasureSpec() 返回后, 拿到为 子 View 生成的新的 MeasureSpec, 并将它传入child.measure(),最终就会进入 子 View 的 onMeasure()方法中了.

如果子 View 是个 普通的 View, 比如 Button, 那直接 setMeasuredDimension() 就好啦.

如果子 View 又是个 ViewGroup, 就要继续遍历所有子 View, 调用 child.measure() 方法啦.


疑问:递归是在 measure() 方法中做的, 还是在 onMeasure 方法中做的??

请注意一点: measure() 方法 是 final 的! 不能被子类重写, 我们直接看 View 中的 measure 方法即可.

measure() 方法很简单, 就是调用`onMeasure(widthMeasureSpec, heightMeasureSpec);`

因此, 递归肯定是在 onMeasure 中做的.
子类重写 onMeasure 方法.
如果是 View, 测量自己, 并且 setMeasuredDimension() 即可.
如果是 ViewGroup, 就需要遍历子 View , 全部测量一遍了.

找个简单的举例:
GridLayout的 onMeasure 方法
先 measureChildrenWithMargins(), 在里面 遍历子 View, 对子 View 调用measureChildWithMargins()
最后 setMeasuredDimension()

比如 FlowLayout
在 onMeasure()方法中, 遍历子 View, 并调用 measureChild(child, widthMeasureSpec, heightMeasureSpec);
最后 setMeasuredDimension()



子 View onMeasure() 的默认实现如下:

```java
protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
            getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
}
```


getSuggestedMinimumWidth() 如下:
```java
    /**
     * Returns the suggested minimum width that the view should use. This
     * returns the maximum of the view's minimum width)
     * and the background's minimum width
     *  ({@link android.graphics.drawable.Drawable#getMinimumWidth()}).
     * 
     * When being used in {@link #onMeasure(int, int)}, the caller should still
     * ensure the returned width is within the requirements of the parent.
     *
     * @return The suggested minimum width of the view.
     */
    protected int getSuggestedMinimumWidth() {
        return (mBackground == null) ? mMinWidth : max(mMinWidth, mBackground.getMinimumWidth());
    }
```

获取建议的 最小宽度.
建议的最小宽度和高度是由View的Background尺寸与通过设置View的miniXXX属性共同决定的。 取最大的那个.

然后 getDefaultSize
```java
// 参数: measureSpec 就是 上面调用 child.measure 之前, 根据父布局MeasureSpec 和 子 View 的 LayoutParams 共同计算出来的新的 MeasureSpec, 给子 View 用了.
   public static int getDefaultSize(int size, int measureSpec) {
        int result = size;
        //通过MeasureSpec解析获取mode与size
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        switch (specMode) {
        case MeasureSpec.UNSPECIFIED:
            result = size;
            break;
            // 如果specMode等于AT_MOST或EXACTLY就返回specSize，这就是系统默认的大小。
        case MeasureSpec.AT_MOST:
        case MeasureSpec.EXACTLY:
            result = specSize;
            break;
        }
        return result;
    }
```

自定义View尺寸的确定
接收到父控件传递的MeasureSpec后，View应该如何用来处理自己的尺寸呢？onMeasure是View测量尺寸最合理的时机，如果View不是ViewGroup相对就比较简单，只需要参照MeasureSpec，并跟自身需求来设定尺寸即可，默认onMeasure的就是完全按照父控件传递MeasureSpec设定自己的尺寸的。这里重点讲一下ViewGroup，为了获得合理的宽高尺寸，ViewGroup在计算自己尺寸的时候，必须预先知道所有子View的尺寸，举个例子，用一个常用的流式布局FlowLayout来讲解一下如何合理的设定自己的尺寸。





到现在, 基本明白  测量的流程了.

我们依照这个栗子再梳理一下

```
<DecorView> (MeasureSpec=EXACTLY+屏幕尺寸, 相当于是 Window 给的了. 然后 DecorView 根据上面传给它的 MeasureSpec ,以及子 View 的 LayoutParams, 构建出 childMeasureSpec. 把这个 childMeasureSpec 往下传,给了 LinearLayout)         

   <LinearLayout> (拿到 DecorView 传给它的 MeasureSpec(就是上面的childMeasureSpec), 然后再根据 子 View Button 的 LayoutParams,构建一个新的 MeasureSpec, 给了 Button, 让 Button 参考)

      <Button> (Button 拿到 LinearLayout 传给它的 MeasureSpec, 结合自己的 LayoutParams,确定自己的大小,调用 setMeasuredDimension() 方法即可)
```


从 performTraversals()  开始

紧接着调用: mView.measure(childWidthMeasureSpec, childHeightMeasureSpec);

这个 mView 是 DecorView, 本质是 FrameLayout, 传给它的 MeasureSpec=EXACTLY+屏幕尺寸, 是通过 getRootMeasureSpec 拿的, 相当于 Window 给的.

由于 measure() 方法是 final 的, 因此调用的是 View 的  measure() 方法, measure 会去调用 onMeasure(). 这个 onMeasure() 就是 FrameLayout 的了.


在 FrameLayout的 onMeasure()方法中, 会遍历所有子 View,   根据上面传给它的   MeasureSpec 和 子 View(LinearLayout) 的 LayoutParams, 一起计算出一个新的 MeasureSpec, 对子 View(linearLayout) 调用 `child.measure(childWidthMeasureSpec, childHeightMeasureSpec);` 将新计算出来的 MeasureSpec 传入 measure 方法.

我们这里子 View 是个 LinearLayout. 最终进入 LinearLayout 的 onMeasure(), onMeasure()方法的参数就是上面FrameLayout计算出来的那个 MeasureSpec. LinearyLayout 在 onMeasure() 里会遍历 它的 子 View, 并根据刚刚传入的 MeasureSpec 和 子 View 的 LayoutParams, 生成新的 MeasureSpec, 调用子 View 的 measure 方法.


Button 拿到 LinearLayout 传给它的 MeasureSpec, 结合自己的 LayoutParams,确定自己的大小,调用 setMeasuredDimension() 方法即可.

到这里, Button 的尺寸就固定了.

然后 LinearLayout 如果还有其他的子 View, 再去用同样的方式测量其他的 子 View. 测量完后, LinearLayout 调用 setMeasuredDimension() ,就是自己的尺寸确定好了.

LinearLayout 的测好了,就回到 FrameLayout 的 onMeasure 啦.  FrameLayout 会继续测量其他的子 View, 都测好了, 再setMeasuredDimension(), 就全部测量好了.


全测量好了, 下面就开始 performLayout()了.



写个最简单的 ViewGroup 看一下:FlowLayout

https://blog.csdn.net/harvic880925/article/details/47035455


```java
protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
   super.onMeasure(widthMeasureSpec, heightMeasureSpec);
   int measureWidth = MeasureSpec.getSize(widthMeasureSpec);
   int measureHeight = MeasureSpec.getSize(heightMeasureSpec);
   int measureWidthMode = MeasureSpec.getMode(widthMeasureSpec);
   int measureHeightMode = MeasureSpec.getMode(heightMeasureSpec);
 

   int lineWidth = 0;//记录每一行的宽度
   int lineHeight = 0;//记录每一行的高度
   int height = 0;//记录整个FlowLayout所占高度
   int width = 0;//记录整个FlowLayout所占宽度
   int count = getChildCount();

   // 遍历所有子 view
   for (int i=0;i<count;i++){
       View child = getChildAt(i);

       // 测量子 view 的大小, 一定要调用该方法之后, 再调用 getMeasuredWidth()才有值
       measureChild(child,widthMeasureSpec,heightMeasureSpec);
       
       MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
       // 拿到子 View 的 宽 和 左右边距
       int childWidth = child.getMeasuredWidth() + lp.leftMargin +lp.rightMargin;
       int childHeight = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
 
       // childWidth 是当前这个 view 的宽. lineWidth 是在这个 view 之前, 已经累计的行的宽度
       if (lineWidth + childWidth > measureWidth){
           //需要换行

           //那 lineWidth 就已经到头了. 取个最大的宽
           width = Math.max(lineWidth,width);
           // 高度也加上刚刚这一行
           height += lineHeight;
           //因为由于盛不下当前控件，而将此控件调到下一行，所以将此控件的高度和宽度初始化给lineHeight、lineWidth
           lineHeight = childHeight;
           lineWidth = childWidth;
       }else{
           // 不换行, 累加值lineWidth,lineHeight取最大高度
           lineHeight = Math.max(lineHeight,childHeight);
           lineWidth += childWidth;
       }
 
       //最后一行是不会超出width范围的，所以要单独处理
       // 这块稍微有点问题. 只取了最后一个.  如果最后一行有 2 个, 最后一个的高度小, 倒数第二个高度大, 这样就把倒数第二个展示不全
       if (i == count -1){
           height += lineHeight;
           width = Math.max(width,lineWidth);
       }
 
   }
   // 当属性是MeasureSpec.EXACTLY时，那么它的宽度和高度就是确定的，
   // 只有当是wrap_content时，根据内部控件的大小来确定它的大小时，大小是不确定的，属性是AT_MOST,此时，就需要我们自己计算它的应当的大小，并设置进去
   setMeasuredDimension((measureWidthMode == MeasureSpec.EXACTLY) ? measureWidth
           : width, (measureHeightMode == MeasureSpec.EXACTLY) ? measureHeight
           : height);
}
```


疑问:什么情况会出现: measureWidthMode == MeasureSpec.EXACTLY

在传入之前, 都会调用 getChildMeasureSpec(), 根据父布局的 MeasureSpec 和 子 View 的 LayoutParams, 创建一个新的 MeasureSpec.

我们看下, 什么情况下, 会创建出 MeasureSpec.EXACTLY.

 1. 父布局是的EXACTLY(指定了 xxdp 或者 MATCH), 子 View 的 LayoutParams  childDimension>0 
 2. 父布局是的EXACTLY(指定了 xxdp 或者 MATCH), 子 View 的 LayoutParams  childDimension == LayoutParams.MATCH_PARENT
 2. 父AT_MOST(wrap) 并且 子 View childDimension>0
 这三种情况.   显然, 这三种都是 要用父传入的 measureWidth. 

 什么情况下 创建出 MeasureSpec.AT_MOST
 1. 父布局是的EXACTLY(指定了 xxdp 或者 MATCH), 子 wrap
 2. 父 wrap, 子 MATCH 或者 wrap
 这两种. 显然, 是要用自己计算好的 width








