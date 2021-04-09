---
title: LayoutInflate

date: 2020-03-08

categories: 
   - Android

tags: 
   - Android 


description: ​
---

<!-- TOC -->

- [源码分析](#源码分析)
- [DEMO验证](#demo验证)
- [从LayoutInflater与setContentView来说说应用布局文件的优化技巧](#从layoutinflater与setcontentview来说说应用布局文件的优化技巧)

<!-- /TOC -->

# 源码分析

从LayoutInflater源码实例化说起


```java
  public static LayoutInflater from(Context context) {
        LayoutInflater LayoutInflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (LayoutInflater == null) {
            throw new AssertionError("LayoutInflater not found.");
        }
        return LayoutInflater;
    }
```

看见from方法仅仅是对getSystemService的一个安全封装而已。

getSystemService 之前说过了.

我们知道，各个Framework 的系统服务（Service）都是注册在 serviceManager 上的，比如 WMS， AMS，PMS，都是注册在上面的。什么时候注册的？系统系统的时候，Zygote进程里注册的。注册后，如果我们想要拿到 Service 的远端代理的话，我们要自己去 ServiceManager 上  getService()  才能拿到。 Android 相当于为我们提供了方便，不用我们自己去ServiceManager 自己拿了，都弄好了，同时，还将这些系统的远端代理包装成更好用的 各种 Manager， 比如  ActivityManager， WindowManager， 全部放在了这个 SystemServiceRegistry 里面。

当我们用的时候， 直接调用 getSystemService， 就从 SystemServiceRegistry 里面拿到Android 给我们提供好的 Manager， 然后调用里面的方法，内部帮我们去请求到对应的 Service了，这些Service 都在 system_server 进程中。


这里,拿到的就是LayoutInflater这个远端代理了.



得到LayoutInflater对象之后我们就是传递xml然后解析得到View，如下方法：
```java
    public View inflate(int resource, ViewGroup root) {
        return inflate(resource, root, root != null); // 调用重载的 inflate
    }


    public View inflate(int resource, ViewGroup root, boolean attachToRoot) {
        final Resources res = getContext().getResources();

        final XmlResourceParser parser = res.getLayout(resource);
        try {
            return inflate(parser, root, attachToRoot);
        } finally {
            parser.close();
        }
    }
```


获取到XmlResourceParser接口的实例（Android默认实现类为Pull解析XmlPullParser）


然后 `inflate(parser, root, attachToRoot);`，你会发现无论哪个inflate重载方法最后都调用了`inflate(XmlPullParser parser, ViewGroup root, boolean attachToRoot)`方法，如下：

```java
  public View inflate(XmlPullParser parser, ViewGroup root, boolean attachToRoot) {
        synchronized (mConstructorArgs) {
            ...
            //定义返回值，初始化为传入的形参root
            View result = root;

            try {
                // Look for the root node.
                int type;
                while ((type = parser.next()) != XmlPullParser.START_TAG &&
                        type != XmlPullParser.END_DOCUMENT) {
                    // Empty
                }
                //如果一开始就是END_DOCUMENT，那说明xml文件有问题
                if (type != XmlPullParser.START_TAG) {
                    throw new InflateException(parser.getPositionDescription()
                            + ": No start tag found!");
                }
                //有了上面判断说明这里type一定是START_TAG，也就是xml文件里的root node
                final String name = parser.getName();

                if (DEBUG) {
                    System.out.println("**************************");
                    System.out.println("Creating root view: "+ name);
                    System.out.println("**************************");
                }

                if (TAG_MERGE.equals(name)) {
                    //处理merge tag的情况（merge，你懂的，APP的xml性能优化）
                    //root必须非空且attachToRoot为true，否则抛异常结束（APP使用merge时要注意的地方，
                    //因为merge的xml并不代表某个具体的view，只是将它包起来的其他xml的内容加到某个上层
                    //ViewGroup中。）
                    if (root == null || !attachToRoot) {
                        throw new InflateException("<merge /> can be used only with a valid "
                                + "ViewGroup root and attachToRoot=true");
                    }
                    //递归inflate方法调运
                    rInflate(parser, root, attrs, false, false);
                } else {
                    // Temp is the root view that was found in the xml
                    // xml文件中的root view，根据tag节点创建view对象
                    final View temp = createViewFromTag(root, name, attrs, false);

                    ViewGroup.LayoutParams params = null;

                    if (root != null) {
                        if (DEBUG) {
                            System.out.println("Creating params from root: " +
                                    root);
                        }
                        // Create layout params that match root, if supplied
                        //根据root生成合适的LayoutParams实例
                        params = root.generateLayoutParams(attrs);
                        if (!attachToRoot) {
                            // Set the layout params for temp if we are not
                            // attaching. (If we are, we use addView, below)
                            //如果attachToRoot=false就调用view的setLayoutParams方法
                            temp.setLayoutParams(params);
                        }
                    }

                    if (DEBUG) {
                        System.out.println("-----> start inflating children");
                    }
                    // Inflate all children under temp
                    //递归inflate剩下的children
                    rInflate(parser, temp, attrs, true, true);
                    if (DEBUG) {
                        System.out.println("-----> done inflating children");
                    }

                    // We are supposed to attach all the views we found (int temp)
                    // to root. Do that now.
                    if (root != null && attachToRoot) {
                        //root非空且attachToRoot=true则将xml文件的root view加到形参提供的root里
                        root.addView(temp, params);
                    }

                    // Decide whether to return the root that was passed in or the
                    // top view found in xml.
                    if (root == null || !attachToRoot) {
                        //返回xml里解析的root view
                        result = temp;
                    }
                }

            } catch (IOException e) {
               ...
            } finally {
               ...
            }

            Trace.traceEnd(Trace.TRACE_TAG_VIEW);
            //返回参数root或xml文件里的root view
            return result;
        }
    }
```

调用了 createViewFromTag()这个方法，并把节点名和参数传了进去。看到这个方法名，我们就应该能猜到，它是用于根据节点名来创建View对象的。确实如此，在createViewFromTag()方法的内部又会去调用createView()方法，然后使用反射的方式创建出View的实例并返回。

当然，这里只是创建出了一个根布局的实例而已，接下来会调用rInflate()方法来循环遍历这个根布局下的子元素，代码如下所示：

```java
private void rInflate(XmlPullParser parser, View parent, final AttributeSet attrs)
        throws XmlPullParserException, IOException {
    final int depth = parser.getDepth();
    int type;
    while (((type = parser.next()) != XmlPullParser.END_TAG ||
            parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
        if (type != XmlPullParser.START_TAG) {
            continue;
        }
        final String name = parser.getName();
        if (TAG_REQUEST_FOCUS.equals(name)) {
            parseRequestFocus(parser, parent);
        } else if (TAG_INCLUDE.equals(name)) {
            if (parser.getDepth() == 0) {
                throw new InflateException("<include /> cannot be the root element");
            }
            parseInclude(parser, parent, attrs);
        } else if (TAG_MERGE.equals(name)) {
            throw new InflateException("<merge /> must be the root element");
        } else {
            final View view = createViewFromTag(name, attrs);
            final ViewGroup viewGroup = (ViewGroup) parent;
            final ViewGroup.LayoutParams params = viewGroup.generateLayoutParams(attrs);
            rInflate(parser, view, attrs);
            viewGroup.addView(view, params);
        }
    }
     //parent的所有子节点都inflate完毕的时候回onFinishInflate方法
    parent.onFinishInflate();
}
```
可以看到，同样是createViewFromTag()方法来创建View的实例，然后还会递归调用rInflate()方法来查找这个View下的子元素，每次递归完成后则将这个View添加到父布局当中。

解析结束回调View类的onFinishInflate方法，所以View类的onFinishInflate方法是一个空方法，如下：
当我们自定义View时在构造函数inflate一个xml后可以实现onFinishInflate这个方法一些自定义的逻辑。

这样的话，把整个布局文件都解析完成后就形成了一个完整的DOM结构，最终会把最顶层的根布局返回，至此inflate()过程全部结束。



Temp is the root view that was found in the xml
Temp 就是我们传入的 XML 中的 整个layout.

方便理解, 我们统一把第二个参数叫 root. 

1. 如果root为null，attachToRoot将失去作用，设置任何值都没有意义。

inflate(xmlId, null); 
只创建temp，然后直接返回temp。


2. 如果root不为null，attachToRoot设为true，则会给加载的布局文件的指定一个父布局，即root。

inflate(xmlId, root, true); 
创建temp
会调用:params = root.generateLayoutParams(attrs); 根据 root 是什么 ViewGroup,生成一个对应的 LayoutParams 对象
然后  root.addView(temp, params);
最后返回root。

3. 如果root不为null，attachToRoot设为false，则会将布局文件最外层的所有layout属性进行设置，当该view被添加到父view当中时，这些layout属性会自动生效。

inflate(xmlId, root, false); 
创建temp
会调用:params = root.generateLayoutParams(attrs); 根据 root 是什么 ViewGroup,生成一个对应的 LayoutParams 对象
然后执行temp.setLayoutParams(params);
然后再返回temp。


4. 在不设置attachToRoot参数的情况下，如果root不为null，attachToRoot参数默认为true。



* 如果root=null,那布局文件中指定的布局参数直接被忽略了。 
这种方式创建的View对象没有LayoutParams。没指定父容器(null)，也就是宽高参数是空的。
后续如果调用addView方法, 把它添加到其他布局中, addView()中会调用generateDefaultLayoutParams()生成默认的宽高参数：
就是：new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
如果你的原本布局宽高不是wrap_content，wrap_content，那么就有问题了。

* 如果root！=null，分两种情况讨论， attachToRoot为false的情况下，直接就把LayoutParam设置给了该view，当它在被添加到父容器中能生效(并没有添加到当前的 root 中)； 
attachToRoot为true,直接在添加到root的时候就生效了；







# DEMO验证


main_activity.xml
```xml

<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/ccc"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    tools:context=".MainActivity">

    <Button
        android:id="@+id/button1"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="inflate" />


    <FrameLayout
        android:id="@+id/button_frame_layout"
        android:layout_width="10dp"
        android:layout_height="10dp"
        android:background="@color/teal_700"
        android:visibility="visible">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:background="#919191"
            android:text="djdjdjjdjdjdjdjdjdj" />

    </FrameLayout>

</LinearLayout>
```



button.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<Button xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="40dp"
    android:layout_height="20dp"
    android:background="@color/design_default_color_error"
    android:text="111111"
    tools:context=".MainActivity"


    />
```

button_layout.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".MainActivity">

    <Button
        android:layout_width="50dp"
        android:layout_height="60dp"
        android:background="@color/purple_500"
        android:text="Button_Layout" />

</RelativeLayout>
```

```java
public class MainActivity extends Activity {
    Button button1;
    FrameLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        button1 = (Button) findViewById(R.id.button1);
        root = findViewById(R.id.button_frame_layout);
        LayoutInflater inflater = LayoutInflater.from(this);

        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // 无 Layout, Button 的 layout_width 和 layout_height 不能生效
                View vv = inflater.inflate(R.layout.button, null);
                if (vv instanceof Button) {
                    int measuredWidth = vv.getMeasuredWidth();
                    Log.d("ViewStudy", "inflater.inflate(R.layout.button, null) is Button:measuredWidth");
                }
                ((LinearLayout)findViewById(R.id.ccc)).addView(vv);

                // 套了一个 root, 生成一个对应的 LayoutParams 对象, 并且 root.addView(), 返回 root
                // Button 的 layout_width 和 layout_height 能生效,比如设置了固定值,都是可以的,但是受 root 限制了
                View retRoot = inflater.inflate(R.layout.button, MainActivity.this.root);
                if(retRoot instanceof  FrameLayout) {
                    Log.d("ViewStudy", "inflater.inflate(R.layout.button, MainActivity.this.root) is FrameLayout");
                }


                // 第三个参数 false, 就是不添加到 root
                // Button 的 layout_width 和 layout_height 能生效,比如设置了固定值,都是可以的,就是不添加到 root
                View temp = inflater.inflate(R.layout.button, MainActivity.this.root, false);
                if(temp instanceof  Button) {
                    Log.d("ViewStudy", "inflater.inflate(R.layout.button, null) is Button");
                }
                ((LinearLayout)findViewById(R.id.ccc)).addView(temp);

                //root.addView(temp); // 这样就跟上面 root!=null, attachToRoot=true 一样

                // 有 Layout, Button 的 layout_width 和 layout_height 能生效
                View button_layout = inflater.inflate(R.layout.button_layout, null);
                ((LinearLayout)findViewById(R.id.ccc)).addView(button_layout);

            }
        });

    }
}
```


# 从LayoutInflater与setContentView来说说应用布局文件的优化技巧

通过上面的源码分析可以发现，xml文件解析实质是递归控件，解析属性的过程。所以说嵌套过深不仅效率低下还可能引起调运栈溢出。同时在解析那些tag时也有一些特殊处理，从源码看编写xml还是有很多要注意的地方的。所以说对于Android的xml来说是有一些优化技巧的（PS：布局优化可以通过 hierarchyviewer 来查看，通过lint也可以自动检查出来一些），如下：

1. 尽量使用相对布局，减少不必要层级结构。不用解释吧？递归解析的原因。

2. 使用merge属性。使用它可以有效的将某些符合条件的多余的层级优化掉。使用merge的场合主要有两处：
* 自定义View中使用，父元素尽量是FrameLayout，当然如果父元素是其他布局，而且不是太复杂的情况下也是可以使用的；
* Activity中的整体布局，根元素需要是FrameLayout。

但是使用merge标签还是有一些限制的，具体是：①merge只能用在布局XML文件的根元素；②使用merge来inflate一个布局时，必须指定一个ViewGroup作为其父元素，并且要设置inflate的attachToRoot参数为true。（参照inflate(int, ViewGroup, boolean)方法）；③不能在ViewStub中使用merge标签；最直观的一个原因就是ViewStub的inflate方法中根本没有attachToRoot的设置。
④merge里面的控件的布局方式（垂直或者是水平）并不能自己控制，它的布局方式受制于容纳include的布局

3. 使用ViewStub。一个轻量级的页面，我们通常使用它来做预加载处理，来改善页面加载速度和提高流畅性，ViewStub本身不会占用层级，它最终会被它指定的层级取代。ViewStub也是有一些缺点，譬如：ViewStub只能Inflate一次，之后ViewStub对象会被置为空。按句话说，某个被ViewStub指定的布局被Inflate后，就不能够再通过ViewStub来控制它了。所以它不适用 于需要按需显示隐藏的情况；ViewStub只能用来Inflate一个布局文件，而不是某个具体的View，当然也可以把View写在某个布局文件中。如果想操作一个具体的view，还是使用visibility属性吧；ViewStub中不能嵌套merge标签。

4. 使用include。这个标签是为了布局重用。

5. 控件设置widget以后对于layout_hORw-xxx设置0dp。减少系统运算次数。

如上就是一些APP布局文件基础的优化技巧。


