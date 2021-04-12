---
title: RecyclerView绘制流程2

date: 2020-03-08

categories: 
   - Android

tags: 
   - Android 


description: ​
---
<!-- TOC -->

- [RecyclerView的绘制三个步骤](#recyclerview的绘制三个步骤)
    - [onMeasure](#onmeasure)
    - [onLayout](#onlayout)
        - [dispatchLayoutStep()](#dispatchlayoutstep)
    - [draw() 和 onDraw()](#draw-和-ondraw)
    - [RecyclerView的绘制三个步骤总结：](#recyclerview的绘制三个步骤总结)
- [dispatchLayoutStep2()](#dispatchlayoutstep2)

<!-- /TOC -->

# RecyclerView的绘制三个步骤


RecyclerView设置布局管理器，这一步是必要的，用什么样的LayoutManager来绘制RecyclerView，不然RecyclerView也不知道怎么绘制。

```java
 public void setLayoutManager(@Nullable LayoutManager layout) {
        if (layout == mLayout) {//和之前的管理器一样则直接return
            return;
        }
        stopScroll();//停止滚动
        if (mLayout != null) {//每次设置layoutManager都重新设置recyclerView的初始参数，动画回收view等
        	if (mItemAnimator != null) {
                mItemAnimator.endAnimations();//结束动画
            }
            mLayout.removeAndRecycleAllViews(mRecycler);//移除回收所有itemView
            mLayout.removeAndRecycleScrapInt(mRecycler);//移除回收所有已经废弃的itemView
            mRecycler.clear();//清除所有缓存
            
            mLayout.setRecyclerView(null);//重置RecyclerView
            mLayout = null;
        } else {
            mRecycler.clear();
        }
     	·······
     	mLayout.setRecyclerView(this);//LayoutManager与RecyclerView关联
        mRecycler.updateViewCacheSize();//更新缓存大小
        requestLayout();//请求重绘
    }
```

首先做了重置回收工作，然后LayoutManager与RecyclerView关联起来，最后请求重绘。这里调用了请求重绘requestLayout()方法，那么说明每次设置layoutManager都会执行View树的绘制，那么就会重走RecyclerView的onMeasure()、onLayout()、onDraw()绘制三部曲。


关于 requestLayout(), 一层一层向上回溯, 最终调用到 ViewRootImpl 的 requestLayout()方法, 并触发了`scheduleTraversals();`方法

在 `scheduleTraversals();`方法中会执行 performTranversal() , 会重走RecyclerView的onMeasure()、onLayout()、onDraw()绘制三部曲.



## onMeasure

```java
 @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        if (mLayout == null) {//如果mLayout为空则采用默认测量，然后结束
            defaultOnMeasure(widthSpec, heightSpec);
            return;
        }
        if (mLayout.mAutoMeasure) {//如果为自动测量，默认为true
        	final int widthMode = MeasureSpec.getMode(widthSpec);
            final int heightMode = MeasureSpec.getMode(heightSpec);
        	mLayout.onMeasure(mRecycler, mState, widthSpec, heightSpec);//测量RecyclerView的宽高, 主要是看是不是精确值
        	 //当前RecyclerView的宽高是否为精确值
        	final boolean measureSpecModeIsExactly =
                    widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY;
            if (measureSpecModeIsExactly || mAdapter == null) {//如果RecyclerView的宽高为精确值或者mAdapter为空，则结束
                return;
            }
            //RecyclerView的宽高为wrap_content时，即measureSpecModeIsExactly = false则进行测量
            //因为RecyclerView的宽高为wrap_content时，需要先测量itemView的宽高才能知道RecyclerView的宽高
            if (mState.mLayoutStep == State.STEP_START) {//还没测量过
                dispatchLayoutStep1();//1.适配器更新、动画运行、保存当前视图的信息、运行预测布局
            }
            dispatchLayoutStep2();//2.最终实际的布局视图，如果有必要会多次运行
            //根据itemView得到RecyclerView的宽高
            mLayout.setMeasuredDimensionFromChildren(widthSpec, heightSpec);
        }
    }
```

onMeasure()主要是RecyclerView宽高测量工作，主要有两种情况：

1. (1)当RecyclerView的宽高为match_parent或者精确值时，即measureSpecModeIsExactly = true，此时只需要测量自身的宽高就知道RecyclerView的宽高，测量方法结束；
2. (2)当RecyclerView的宽高为wrap_content时，即measureSpecModeIsExactly = false，会往下执行dispatchLayoutStep1()和dispatchLayoutStep2()，就是遍历测量ItemView的大小从而确定RecyclerView的宽高，这种情况真正的测量操作都是在dispatchLayoutStep2()中完成。


## onLayout

在onLayout()方法中， 直接调用dispatchLayout()方法布局：
```java
 @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        dispatchLayout(); //直接调用dispatchLayout()方法布局
        mFirstLayoutComplete = true;
    }
```

dispatchLayout() 主要 处理由布局引起的动态变化：
```java
void dispatchLayout() {
  		······
        mState.mIsMeasuring = false;//设置RecyclerView布局完成状态，前面已经设置预布局完成了。
        if (mState.mLayoutStep == State.STEP_START) {//如果没在OnMeasure阶段提前测量子ItemView
            dispatchLayoutStep1();//布局第一步：适配器更新、动画运行、保存当前视图的信息、运行预测布局
            mLayout.setExactMeasureSpecsFrom(this);
            dispatchLayoutStep2();
        } else if (mAdapterHelper.hasUpdates() || mLayout.getWidth() != getWidth()
                || mLayout.getHeight() != getHeight()) {//前两步完成测量，但是因为大小改变不得不再次运行下面的代码
            mLayout.setExactMeasureSpecsFrom(this);
            dispatchLayoutStep2();//布局第二步：最终实际的布局视图，如果有必要会多次运行
        } else {
            mLayout.setExactMeasureSpecsFrom(this);
        }
        dispatchLayoutStep3();//布局第三步：最后一步的布局，保存视图动画、触发动画和不必要的清理。
    }
```

可以看到dispatchLayout()和onMeasure()阶段中一样选择性地进行测量布局的三个步骤：

1. 如果没在onMeasure阶段提前测量子ItemView，即RecyclerView宽高为match_parent或者精确值时，调用dispatchLayoutStep1()和dispatchLayoutStep2()测量itemView宽高；
2. 如果在onMeasure阶段提前测量子ItemView，但是子视图发生了改变或者期望宽高和实际宽高不一致，则会调用dispatchLayoutStep2()重新测量；
3. 最后都会执行dispatchLayoutStep3()方法。




### dispatchLayoutStep()

* dispatchLayoutStep1()  表示进行预布局，适配器更新、动画运行、保存当前视图的信息等工作；
* dispatchLayoutStep2()  表示对最终状态的视图进行实际布局，有必要时会多次执行；
* dispatchLayoutStep3()  表示布局最后一步，保存和触发有关动画的信息，相关清理等工作。

## draw() 和 onDraw()

```java
public class RecyclerView extends ViewGroup implements ScrollingView, NestedScrollingChild2 {
    @Override
    public void draw(Canvas c) {
        super.draw(c);

        final int count = mItemDecorations.size();
        for (int i = 0; i < count; i++) {
            mItemDecorations.get(i).onDrawOver(c, this, mState);
        }
        
    @Override
    public void onDraw(Canvas c) {
        super.onDraw(c);

        final int count = mItemDecorations.size();
        for (int i = 0; i < count; i++) {
            mItemDecorations.get(i).onDraw(c, this, mState);
        }
    }
}
```


RecyclerView 重写了 draw() 方法.  调用了 super.draw(), 即 View 的 draw()方法.
在 View 的 draw()方法中, 四步: 1.drawBackground() 2.onDraw()  3.dispatchDraw() 4.drawScrollBars()  由于RecyclerView重写了onDraw()方法, 因此所以是先执行了RecyclerView中的onDraw()方法。在 RecyclerView 的 onDraw 中, 执行了 ItemDecorations 的onDraw(),  等 View 的 draw()执行完了, 也就是 super 执行好了, RecyclerView 的 draw 方法中才开始执行ItemDecorations的:onDrawOver()

因此，mItemDecorations方法的执行顺序为：onDraw()->dispatchDraw()->onDrawOver()。



## RecyclerView的绘制三个步骤总结：
1. RecyclerView的itemView可能会被测量多次，如果RecyclerView的宽高是固定值或者match_parent，那么在onMeasure()阶段是不会提前测量ItemView布局，如果RecyclerView的宽高是wrap_content，由于还没有知道RecyclerView的实际宽高，那么会提前在onMeasure()阶段遍历测量itemView布局确定内容显示区域的宽高值来确定RecyclerView的实际宽高；

2. dispatchLayoutStep1()、 dispatchLayoutStep2()、 dispatchLayoutStep3()这三个方法一定会执行，在RecyclerView的实际宽高不确定时，会提前多次执行dispatchLayoutStep1()、 dispatchLayoutStep2()方法，最后在onLayout()阶段执行 dispatchLayoutStep3()，如果有itemView发生改变会再次执行dispatchLayoutStep2()；

3. 真正的测量和布局itemView实际在dispatchLayoutStep2()方法中。



![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/recycler_measure_flow.png)



# dispatchLayoutStep2()

dispatchLayoutStep2 中会将真正的布局工作委托给 LayoutManager, 调用 LayoutManager.onLayoutChildren
```java
    private void dispatchLayoutStep2() {
        ...
        mState.mInPreLayout = false;
        mLayout.onLayoutChildren(mRecycler, mState);
        ...
    }
```

onLayoutChildren 中布局算法:

```java
    updateAnchorInfoForLayout(recycler, state, mAnchorInfo); 计算锚点的位置.

    // fill towards end  (1)
    updateLayoutStateToFillEnd(mAnchorInfo); //确定AnchorView到RecyclerView的底部的布局可用空间
    ...
    fill(recycler, mLayoutState, state, false); //填充view, 从 AnchorView 到RecyclerView的底部
    endOffset = mLayoutState.mOffset; 

    // fill towards start (2)
    updateLayoutStateToFillStart(mAnchorInfo); //确定AnchorView到RecyclerView的顶部的布局可用空间
    ...
    fill(recycler, mLayoutState, state, false); //填充view,从 AnchorView 到RecyclerView的顶部    
```

fill 中会通过循环, 测量每一个ItemView, 并将 ItemView 填充到 RecyclerView 中.

```java
 //填充方法，返回的是填充itemView的像素，方便后续滚动时使用
  int fill(RecyclerView.Recycler recycler, LayoutState layoutState,
            RecyclerView.State state, boolean stopOnFocusable) {
        //核心  == while()循环 ==
        while ((layoutState.mInfinite || remainingSpace > 0) && layoutState.hasMore(state)) {//一直循环，知道没有数据

            //填充itemView的核心方法
            layoutChunk(recycler, state, layoutState, layoutChunkResult);
            ······
        }
     	······
        return start - layoutState.mAvailable;//返回这次填充的区域大小
    }
```

fill()核心就是一个while()循环，循环执行layoutChunk()填充一个itemView到屏幕，同时返回这次填充的区域大小。首先根据屏幕还有多少剩余空间remainingSpace，根据这个数值减去子View所占的空间大小，小于0时布局子View结束，如果当前所有子View还没有超过remainingSpace时，调用layoutChunk()安排View的位置。


layoutChunk()作为最终填充布局itemView的方法，对itemView创建、填充、测量、布局，主要有以下几个步骤：

1. layoutState.next(recycler)从缓存中获取itemView，如果没有则创建itemView；
2. 根据实际情况来添加itemView到RecyclerView中，最终调用的还是ViewGroup的addView()方法；
3. measureChildWithMargins()测量itemView大小包括父视图的填充、项目装饰和子视图的边距； 里面调用  child.measure()
4. 根据计算好的left, top, right, bottom通过layoutDecoratedWithMargins()  在RecyclerView中布局itemView。 里面调用 child.layout()


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/onLayoutChildren_flow.png)