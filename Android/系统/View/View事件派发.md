---
title: View事件派发

date: 2020-03-08

categories: 
   - Android

tags: 
   - Android 


description: ​
---


# 流程概览



事件过来,第一个是ACTION_DOWN


先走 Activity 的 dispatchTouchEvent()

getWindow().superDispatchTouchEvent(ev)

mDecor.superDispatchTouchEvent(event)

ViewGroup.dispatchTouchEvent(event)

	ACTION_DOWN 拦截,默认调用 onInterceptTouchEvent()

	遍历子View, 每个都执行 dispatchTransformedTouchEvent.在该方法中为一个递归调用，会递归调用子View的dispatchTouchEvent()方法.

		进入 dispatchTransformedTouchEvent, child.dispatchTouchEvent.
		如果是ViewGroup. 继续递归调用ViewGroup.dispatchTouhEvent()
		如果是View, 就 走View的 dispatchTouchEvent, 先onTouch,再 onTouchEvent.

	如果事件被子View 消费掉了,即 dispatchTransformedTouchEvent 返回了true, 也就是在递归的过程中, 有子View的dispatchTouchEvent返回了true,就说明被消费了,ViewGroup.dispatchTouchEvent 就返回true, 继续下一个事件
	如果始终没有子View 消费, 调用自己的  super.dispatchTouchEvent , 自己消费.自己消费完了也要返回true, 才有后续事件派发


下一个事件过来 ACTION_MOVE


先走 Activity 的 dispatchTouchEvent()

getWindow().superDispatchTouchEvent(ev)

mDecor.superDispatchTouchEvent(event)

ViewGroup.dispatchTouchEvent(event)
	
	如果之前的ACTION_DOWN 被子View 消费过了,就直接找到刚才那个处理ACTION_DOWN的子View, 把这个ACTION_MOVE交给给它处理就行了. 这个子View去执行自己的 dispatchTouchEvent方法. 记得ViewGroup.dispatchTouchEvent 要返回true, 这样才有后续事件派发
	如果之前的ACTION_DOWN 没有一个子View愿意消费它, 那ViewGroup就不派发这个ACTION_MOVE事件了,直接自己调用View.dispatchTouchEvent处理事件.记得ViewGroup.dispatchTouchEvent 要返回true, 这样才有后续事件派发


如果是 ACTION_CANCEL
	如果之前的事件 被子View 消费过了,就直接找到之前的那个子View, 把这个ACTION_CANCEL交给给它处理就行了. 并且要要重置 Touch 状态. Resets all touch state in preparation for a new cycle. 等待新一轮的事件
	如果之前的事件 没有一个子View愿意消费它, 就自己处理掉拉到了, 同时自己(ViewGroup)的状态不需要重置. 因为这个TouchState 主要记录的就是 到底是哪个子View 消费(mFirstTarget). 都没人消费过,因此不需要重置状态 

	



看个简单例子理解:
```java
public class TestButton extends Button {

    public TestButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        Log.i(null, "dispatchTouchEvent-- action=" + event.getAction());
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.i(null, "onTouchEvent-- action="+event.getAction());
        return super.onTouchEvent(event);
    }
}
public class ListenerActivity extends Activity implements View.OnTouchListener, View.OnClickListener {
    private LinearLayout mLayout;
    private TestButton mButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        mLayout = (LinearLayout) this.findViewById(R.id.mylayout);
        mButton = (TestButton) this.findViewById(R.id.my_btn);

        mLayout.setOnTouchListener(this);
        mButton.setOnTouchListener(this);

        mLayout.setOnClickListener(this);
        mButton.setOnClickListener(this);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        Log.i(null, "OnTouchListener--onTouch-- action="+event.getAction()+" --"+v);
        return false;
    }

    @Override
    public void onClick(View v) {
        Log.i(null, "OnClickListener--onClick--"+v);
    }
}
```

将TestButton类的onTouchEvent方法修改如下，其他和基础代码保持不变：
 @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.i(null, "onTouchEvent-- action="+event.getAction());
        return false;
    }


    结果:
    dispatchTouchEvent action 0
    onTouch action 0 TestButton
    onTouchEvent action0 
    onTouch action0 LinearLayout
    onTouch action1 LinearLayout
    onClick LinearLayout


	流程:
	ACTION_DOWN
	进入 LinearLayout 的  dispatchTouchEvent
	遍历子View,递归调用 dispatchTouchEvent
		子View 就一个TestButton. 先 onTouch 返回false,再onTouchEvent. onTouchEvent 还返回false, 因此子View的 dispatchTouchEvent 返回false, 表示事件不消费
	事件都没人消费, LinearLayout 执行 View 的 dispatchTouchEvent. 由于默认的LinearLayout 没设置 onTouchListener, 就直接执行 onTouchEvent 流程, 该方法true.  因此, View默认的 dispatchTouchEvent 返回true, 然后LinearLayout 的 dispatchTouchEvent方法把这个true 返回了,  说明,ACTION_DWON事件被 LinearLayout消费了
	继续, dispatchTouchEvent返回true, 才会有后续事件派发.

	又来了 ACTION_MOVE
	进入 LinearLayout 的  dispatchTouchEvent
	LinearLayout 发现 mFirstTouchTarget == null, 认为子View(TestButton) 全都不消费事件(连刚才的ACTION_DOWN都没消费呢),所以就不派发给 子View
	LinearLayout 自己处理ACTION_MOVE 事件, 执行View 的 dispatchTouchEvent
	继续, LinearLayout的dispatchTouchEvent返回true, 才会有后续事件派发.

	如果 之前的 ACTION_DOWN 有子View 处理掉了的话, mFirstTouchTarget != null
	再来 ACTION_MOVE的时候, 就找到刚才那个处理ACTION_DOWN的子View, 给它处理就行了

	ACTION_UP
	...


    结论:
    LinearLayout 先派发给子View 处理.子View 的 dispatchTouchEvent  返回false, 表示不处理,以后LinearLayout 就不派发给子View了,都自己处理了
    但是LinearLayout 自己处理完,还是返回true, 这样上层才继续给LinearLayout派发后续事件


# View

```java

// 在Android中你只要触摸控件首先都会触发控件的dispatchTouchEvent方法
public class View {
    /**
     * Pass the touch screen motion event down to the target view, or this
     * view if it is the target.
     *
     * @param event The motion event to be dispatched.
     * @return True if the event was handled by the view, false otherwise.
     */
    /**
     * 1. mOnTouchListener 是在 View.setOnTouchListener 设置
     * 2. ENABLE
     * 3. 执行onTouch，如果 为 true， 则 不执行onTouchEvent， dispatchTouchEvent返回true
     * 否则， dispatchTouchEvent 返回值由  onTouchEvent 决定
     *
     * 先执行 onTouch
     * 如果控件不是enable的设置了onTouch方法也不会执行，只能通过重写控件的onTouchEvent方法处理。并且，dispatchTouchEvent返回值与onTouchEvent返回一样。
     */
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mOnTouchListener != null && (mViewFlags & ENABLED_MASK) == ENABLED &&
                mOnTouchListener.onTouch(this, event)) {
            return true;
        }
        return onTouchEvent(event);
    }


    public boolean onTouchEvent(MotionEvent event) {
        // 如果 disable， 上面的 onTouch 不执行， 直接进入 onTouchEvent
        // 在这里， 如果发现是 clickable的， 不响应， 直接返回true， 消费！

        if ((viewFlags & ENABLED_MASK) == DISABLED) {
            // A disabled view that is clickable still consumes the touch
            // events, it just doesn't respond to them.
            // 如果是 disable， 并且 clickable，仍然消费事件，并返回 true。只是不响应而已。
            return (((viewFlags & CLICKABLE) == CLICKABLE ||
                    (viewFlags & LONG_CLICKABLE) == LONG_CLICKABLE));
        }
        // 走到这里， 肯定不是 disable的了
        // 这里判断是否是 clickable。如果不是 clickable， 不进入 if， 直接返回false
        // 只要进入了 if， 最终onTouchEvent都返回了true
        if (((viewFlags & CLICKABLE) == CLICKABLE ||
                (viewFlags & LONG_CLICKABLE) == LONG_CLICKABLE)) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_UP:
                    ....
                    if (!focusTaken) {

                        // 执行 onClick
                        if (mPerformClick == null) {
                            mPerformClick = new PerformClick();
                        }
                        if (!post(mPerformClick)) {
                            performClick();
                        }
                    }
                    ...
                    break;
                case MotionEvent.ACTION_DOWN:
                    ...
                    break;

                case MotionEvent.ACTION_CANCEL:
                   ...
                    break;

                case MotionEvent.ACTION_MOVE:
                   ...
                    break;
            }
            return true;
        }
        return false;
    }

    public void setOnClickListener(View.OnClickListener l) {
        // 如果该View 不是  clickable的， 会设置成clickable的
        if (!isClickable()) {
            setClickable(true);
        }
        getListenerInfo().mOnClickListener = l;
    }

}
```


# ViewGroup


```java

public class ViewGroup {
    public boolean dispatchTouchEvent(MotionEvent ev) {
        ....
        boolean handled = false;
        if (onFilterTouchEventForSecurity(ev)) {
            final int action = ev.getAction();
            final int actionMasked = action & MotionEvent.ACTION_MASK;

            // Handle an initial down.
            if (actionMasked == MotionEvent.ACTION_DOWN) {
                // Step1: cancelAndClearTouchTargets() 中设置 mFirstTouchTarget = null,表示暂未找到能够处理事件的View
                cancelAndClearTouchTargets(ev);
                resetTouchState();
            }

            // Step2: Check for interception. 检查是否拦截
            // 注意:DOWN的时候才拦截.UP 之类的事件 不拦截
            final boolean intercepted;
            if (actionMasked == MotionEvent.ACTION_DOWN
                    || mFirstTouchTarget != null) {
                // disallowIntercept 默认 false。  不允许Intercept ， 是false， 代表可以打断，因此默认执行：onInterceptTouchEvent
                final boolean disallowIntercept = (mGroupFlags & FLAG_DISALLOW_INTERCEPT) != 0;
                // 可以在其他地方调用requestDisallowInterceptTouchEvent(boolean disallowIntercept)方法，从而禁止执行是否打断操作(onInterceptTouchEvent)
                // 比如把  disallowIntercept 设置成 true, 就不会执行 onInterceptTouchEvent 方法了
                if (!disallowIntercept) {
                    intercepted = onInterceptTouchEvent(ev); // onInterceptTouchEvent 默认返回false
                    ev.setAction(action); // restore action in case it was changed
                } else {
                    intercepted = false;
                }
            } else {
                // There are no touch targets and this action is not an initial down
                // so this view group continues to intercept touches.
                intercepted = true;// 代表没有  touch target ， 或者不是  down 事件， 就设置成 true
            }

            ...
            TouchTarget newTouchTarget = null;
            boolean alreadyDispatchedToNewTouchTarget = false;
            if (!canceled && !intercepted) {// 如果 cancel 了 , 或者  如果intercepted=true,就不进入这里了,不进入这里,事件就无法派发给子View了.
                                            // 因此,如果 onInterceptTouchEvent  返回true, 事件就无法派发给子View了
                ...
                if (actionMasked == MotionEvent.ACTION_DOWN
                        || (split && actionMasked == MotionEvent.ACTION_POINTER_DOWN)
                        || actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
                    ...
                    final int childrenCount = mChildrenCount;
                    // newTouchTarget
                    if (newTouchTarget == null && childrenCount != 0) {
                        // Find a child that can receive the event.
                        // Scan children from front to back.
                        final ArrayList<View> preorderedList = buildOrderedChildList();
                        final boolean customOrder = preorderedList == null
                                && isChildrenDrawingOrderEnabled();
                        final View[] children = mChildren;
                        // Step3: 通过一个for循环倒序遍历所有的子view，这是因为preorderedList中的顺序是按照addView或者XML布局文件中的顺序来的，后addView添加的子View，会因为Android的UI后刷新机制显示在上层；
                        for (int i = childrenCount - 1; i >= 0; i--) {
                            final int childIndex = customOrder
                                    ? getChildDrawingOrder(childrenCount, i) : i;
                            final View child = (preorderedList == null)
                                    ? children[childIndex] : preorderedList.get(childIndex);
                            // 说明找到了接收Touch事件的子View. 开始肯定没找到, newTouchTarget肯定==null, 因为最开始, mFirstTarget 是null, 咋找啊.
                            newTouchTarget = getTouchTarget(child);
                            if (newTouchTarget != null) {
                                // Child is already receiving touch within its bounds.
                                // Give it the new pointer in addition to the ones it is handling.
                                newTouchTarget.pointerIdBits |= idBitsToAssign;
                                break;
                            }

                            ...
                            // 遍历子View, 每个都执行 dispatchTransformedTouchEvent.调用 child的  dispatchTouchEvent 方法.
                            // 在该方法中为一个递归调用，会递归调用dispatchTouchEvent()方法
                            // 如果是个 View, 在 dispatchTouchEvent() 就直接调用自己的 onTouch 之类的.
                            // 如果是个 ViewGroup, 就又进入现在的这个 ViewGroup.dispatchTouchEvent 方法了
                            // dispatchTransformedTouchEvent()的返回值和 dispatchTouchEvent()的返回值相同
                            // 如果 child 的  dispatchTouchEvent() 方法返回true, 表示,被消费了, 赋值, 并且break
                            if (dispatchTransformedTouchEvent(ev, false, child, idBitsToAssign)) {
                                ...
                                // 赋值
                                newTouchTarget = addTouchTarget(child, idBitsToAssign); // 这里会给 mFirstTouchTarget 赋值, break之后,下面的 mFirstTarget 就!=null了,进入else, handled=true, 并且返回
                                alreadyDispatchedToNewTouchTarget = true;
                                break;
                            }
                        }
                    }
                   ...
                }
            }
            // 走到这里, mFirstTarget 还是 null, 就是压根没找到能够消费事件的View,就自己处理
            // Dispatch to touch targets.
            if (mFirstTouchTarget == null) {
                // 如何自己处理,调用dispatchTransformedTouchEvent,第三个参数传入null
                // 进而执行 super.dispatchTouchEvent(transformedEvent);,就是自己处理了
                handled = dispatchTransformedTouchEvent(ev, canceled, null,
                        TouchTarget.ALL_POINTER_IDS);
            } else {
                // 找到刚才处理ACTION_DOWN的那个子View, 把事件再派发给它,让他处理
                // dispatchTouchEvent 返回true, 才会触发下一个action
                handled = true;

                // 如果是CANCEL事件, Resets all touch state in preparation for a new cycle.
                // 其中设置 mFirstTarget = null;
                if (canceled || actionMasked == MotionEvent.ACTION_UP
                        || actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
                    resetTouchState();
                }
              ...
            }
           ...
        }
       ...
        return handled;
    }
    /**
     * Transforms a motion event into the coordinate space of a particular child view,
     * filters out irrelevant pointer ids, and overrides its action if necessary.
     * If child is null, assumes the MotionEvent will be sent to this ViewGroup instead.
     */
    private boolean dispatchTransformedTouchEvent(MotionEvent event, boolean cancel,
                                                  View child, int desiredPointerIdBits) {
        final boolean handled;

        // Canceling motions is a special case.  We don't need to perform any transformations
        // or filtering.  The important part is the action, not the contents.
        // 对cancel事件特殊处理
        final int oldAction = event.getAction();
        if (cancel || oldAction == MotionEvent.ACTION_CANCEL) {
            event.setAction(MotionEvent.ACTION_CANCEL);
            if (child == null) {
                handled = super.dispatchTouchEvent(event);
            } else {
                handled = child.dispatchTouchEvent(event);
            }
            event.setAction(oldAction);
            return handled;
        }

        ...

        // Perform any necessary transformations and dispatch.
        if (child == null) {
            handled = super.dispatchTouchEvent(transformedEvent);
        } else {
            // 调用child的 dispatchTouchEvent 方法
           handled = child.dispatchTouchEvent(transformedEvent);
        }

        return handled;
    }
}

```



# Activity
```java
public class Activity {
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            // 只有ACTION_DOWN事件派发时调运了onUserInteraction方法
            onUserInteraction();
        }
        // getWindow()返回的就是PhoneWindow对象
        // 执行的是一个ViewGroup的dispatchTouchEvent方法
        // 若Activity下面的子view拦截了touchEvent事件(返回true)则Activity.onTouchEvent方法就不会执行。
        if (getWindow().superDispatchTouchEvent(ev)) {
            return true;
        }

        // 默认返回false, 没做啥
        return onTouchEvent(ev);
    }
}

/**
 *
 * PhoneWindow 的superDispatchTouchEvent实现, 直接调用了 DecorView的 superDispatchTouchEvent方法
 *  @Override
 *     public boolean superDispatchTouchEvent(MotionEvent event) {
 *         return mDecor.superDispatchTouchEvent(event);
 *     }
 *
 *
 *
 *
 * DecorView 的 superDispatchTouchEvent 方法
 *
 * public boolean superDispatchTouchEvent(MotionEvent event) {
 *       return super.dispatchTouchEvent(event);
 * }
 *
 *
 * DecorView 是 FrameLayout的 子类,因此,super.dispatchTouchEvent(event);  本质执行的是一个ViewGroup的dispatchTouchEvent方法
 *
 * onTouchEvent()方法如下:
 *     public boolean onTouchEvent(MotionEvent event) {
 *         if (mWindow.shouldCloseOnTouch(this, event)) {
 *             finish();
 *             return true;
 *         }
 *
 *         return false;
 *     }
 *
 */

```