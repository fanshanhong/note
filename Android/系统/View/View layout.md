---
title: View layout

date: 2020-03-08

categories: 
   - Android

tags: 
   - Android 


description: ​
---

<!-- TOC -->

- [流程](#流程)
- [分析 LinearLayout](#分析-linearlayout)

<!-- /TOC -->

# 流程

performTraversals()方法中, perfromMeasure()方法执行完之后, 就开始 performLayout()


```java
 mView.layout(0, 0, mView.getMeasuredWidth(), mView.getMeasuredHeight());
```
四个参数,  left, top, right, bottom.

从 ViewGroup 的 layout()方法开始.
```java
    @Override
    public final void layout(int l, int t, int r, int b) {
       ...
        super.layout(l, t, r, b);
       ...
    }
```

内部直接是 super.layout(), 也就是去找 View 的 layout 方法, 如下:

```java
 public void layout(int l, int t, int r, int b) {
        ......
        //实质都是调用setFrame方法把参数分别赋值给mLeft、mTop、mRight和mBottom这几个变量
        boolean changed = isLayoutModeOptical(mParent) ?
                setOpticalFrame(l, t, r, b) : setFrame(l, t, r, b);
        ////判断View的位置是否发生过变化，以确定有没有必要对当前的View进行重新layout
        if (changed || (mPrivateFlags & PFLAG_LAYOUT_REQUIRED) == PFLAG_LAYOUT_REQUIRED) {
            //回调onLayout
            onLayout(changed, l, t, r, b);
            ......
        }
        ......
    }
```


```java
/**
  * 分析：setFrame()
  * 作用：根据传入的4个位置值，设置View本身的四个顶点位置. 注意:这里是确定 View 自己的位置!
  * 即：最终确定View本身的位置. 注意:这里是确定 View 自己的位置!
  */ 
  protected boolean setFrame(int left, int top, int right, int bottom) {
        ...
    // 通过以下赋值语句记录下了视图的位置信息，即确定View的四个顶点
    // 从而确定了视图的位置
    mLeft = left;
    mTop = top;
    mRight = right;
    mBottom = bottom;

    mRenderNode.setLeftTopRightBottom(mLeft, mTop, mRight, mBottom);

  }
    
 /**
  * 分析：setOpticalFrame()
  * 作用：根据传入的4个位置值，设置View本身的四个顶点位置. 注意:这里是确定 View 自己的位置!
  * 即：最终确定View本身的位置
  */ 
  private boolean setOpticalFrame(int left, int top, int right, int bottom) {

        Insets parentInsets = mParent instanceof View ?
                ((View) mParent).getOpticalInsets() : Insets.NONE;

        Insets childInsets = getOpticalInsets();

        // 内部实际上是调用setFrame（）
        return setFrame(
                left   + parentInsets.left - childInsets.left,
                top    + parentInsets.top  - childInsets.top,
                right  + parentInsets.left + childInsets.right,
                bottom + parentInsets.top  + childInsets.bottom);
    }
```


在 layout 中, 就是确定 View 自己的位置.
接下来的 onLayout(), 才是计算所有的子View在父容器的位置.


对于 单一 View 而言, 由于它没有子 View, 所以单一 View 的 onLayout()是一个空实现, 单一View的layout过程在layout()后就已完成了. 它已经确定了自己的位置了, 而且不需要管子 View(就没有子 View).

那对于 ViewGroup 而言, 先使用 layout() 方法, 确定自身位置.
然后调用 ViewGroup.onLayout, 在 ViewGroup.onLayout()中,  遍历子View & 确定自身子View在ViewGroup的位置（调用子 View 的 layout())）

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/layout_flow.png)


此处需注意：ViewGroup 和 View 同样拥有layout())和onLayout()，但二者不同的：

一开始计算ViewGroup位置时，调用的是ViewGroup的layout())和onLayout()；
当开始遍历子View & 计算子View位置时，调用的是子View的layout())和onLayout()



流程: 
```
ViewGroup.layout()  确定 ViewGroup自身位置
    ->ViewGroup.onLayout()  遍历子 View, 确定子 View 在 ViewGroup 中的自身位置, 调用子 View 的 layout()方法. ViewGroup 的 onLayout()是 abstruct 的, 必须子类来实现. 因：子View的确定位置与具体布局有关，所以onLayout（）在ViewGroup没有实现
        -> 子view.layout()       确定子 View 的在 ViewGroup 中的位置
            ->子view.onLayout()     如果是单一 View, 这个是空实现, 因为它自己没有子 View 了
```


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/viewgroup_lauyout_flow.png)




# 分析 LinearLayout


1. layout()  
LinearLayout 没有重写 ViewGroup的 layout()方法. 因此, layout()过程还是调用 ViewGrou 的 layout()方法, 内部 setFrame()一下


2. onLayout()

```java
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (mOrientation == VERTICAL) {
            layoutVertical(l, t, r, b);
        } else {
            layoutHorizontal(l, t, r, b);
        }
    }

/**
  * 分析：layoutVertical(l, t, r, b)
  */
    void layoutVertical(int left, int top, int right, int bottom) {
       
        // 子View的数量
        final int count = getVirtualChildCount();

        // 1. 遍历子View
        for (int i = 0; i < count; i++) {
            final View child = getVirtualChildAt(i);
            if (child == null) {
                childTop += measureNullChild(i);
            } else if (child.getVisibility() != GONE) {

                // 2. 计算子View的测量宽 / 高值
                final int childWidth = child.getMeasuredWidth();
                final int childHeight = child.getMeasuredHeight();

                // 3. 确定自身子View的位置
                // 即：递归调用子View的setChildFrame()，实际上是调用了子View的layout() 
                setChildFrame(child, childLeft, childTop + getLocationOffset(child),
                        childWidth, childHeight);

                // childTop逐渐增大，即后面的子元素会被放置在靠下的位置
                // 这符合垂直方向的LinearLayout的特性
                childTop += childHeight + lp.bottomMargin + getNextLocationOffset(child);

                i += getChildrenSkipCount(child, i);
            }
        }
    }

    /**
    * setChildFrame()
    */
    private void setChildFrame( View child, int left, int top, int width, int height){
        
        // setChildFrame（）仅仅只是调用了子View的layout（）而已
        child.layout(left, top, left ++ width, top + height);

    }
    // 在子View的layout()又通过调用setFrame（）确定View的四个顶点
    // 即确定了子View的位置
    // 如此不断循环确定所有子View的位置
```


摘自:
版权声明：本文为CSDN博主「Carson_Ho」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
原文链接：https://blog.csdn.net/carson_ho/article/details/56011112