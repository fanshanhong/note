---
title: ViewStub

date: 2020-03-08

categories: 
   - Android

tags: 
   - Android 


description: ​
---
<!-- TOC -->


<!-- /TOC -->

在开发应用程序的时候，经常会遇到这样的情况，会在运行时动态根据条件来决定显示哪个View或某个布局。那么最通常的想法就是把可能用到的View都写在上面，先把它们的可见性都设为View.GONE，然后在代码中动态的更改它的可见性。这样的做法的优点是逻辑简单而且控制起来比较灵活。但是它的缺点就是，耗费资源。虽然把View的初始可见View.GONE但是在Inflate布局的时候View仍然会被Inflate，也就是说仍然会创建对象，会被实例化，会被设置属性。也就是说，会耗费内存等资源。

 推荐的做法是使用android.view.ViewStub，ViewStub 是一个轻量级的View，它一个看不见的，不占布局位置，占用资源非常小的控件。可以为ViewStub指定一个布局，在Inflate布局的时候，只有 ViewStub会被初始化，然后当ViewStub被设置为可见的时候，或是调用了ViewStub.inflate()的时候，ViewStub所向 的布局就会被Inflate和实例化，然后ViewStub的布局属性都会传给它所指向的布局。这样，就可以使用ViewStub来方便的在运行时，要还 是不要显示某个布局。

      但ViewStub也不是万能的，下面总结下ViewStub能做的事儿和什么时候该用ViewStub，什么时候该用可见性的控制。

     首先来说说ViewStub的一些特点：

         1. ViewStub只能Inflate一次，之后ViewStub对象会被置为空。按句话说，某个被ViewStub指定的布局被Inflate后，就不会够再通过ViewStub来控制它了。

         2. ViewStub只能用来Inflate一个布局文件，而不是某个具体的View，当然也可以把View写在某个布局文件中。

     基于以上的特点，那么可以考虑使用ViewStub的情况有：

         1. 在程序的运行期间，某个布局在Inflate后，就不会有变化，除非重新启动。

              因为ViewStub只能Inflate一次，之后会被置空，所以无法指望后面接着使用ViewStub来控制布局。所以当需要在运行时不止一次的显示和隐藏某个布局，那么ViewStub是做不到的。这时就只能使用View的可见性来控制了。

         2. 想要控制显示与隐藏的是一个布局文件，而非某个View。

              因为设置给ViewStub的只能是某个布局文件的Id，所以无法让它来控制某个View。

     所以，如果想要控制某个View(如Button或TextView)的显示与隐藏，或者想要在运行时不断的显示与隐藏某个布局或View，只能使用View的可见性来控制。

下面来看一个实例

在这个例子中，要显示二种不同的布局，一个是用TextView显示一段文字，另一个则是用ImageView显示一个图片。这二个是在onCreate()时决定是显示哪一个，这里就是应用ViewStub的最佳地点。

先来看看布局，一个是主布局，里面只定义二个ViewStub，一个用来控制TextView一个用来控制ImageView，另外就是一个是为显示文字的做的TextView布局，一个是为ImageView而做的布局：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:orientation="vertical"
    android:layout_width="fill_parent"
    android:layout_height="fill_parent"
    android:gravity="center_horizontal">
    <ViewStub
        android:id="@+id/viewstub_demo_text"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginLeft="5dip"
        android:layout_marginRight="5dip"
        android:layout_marginTop="10dip"
        android:layout="@layout/viewstub_demo_text_layout"/>
    <ViewStub
        android:id="@+id/viewstub_demo_image"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginLeft="5dip"
        android:layout_marginRight="5dip"
        android:layout="@layout/viewstub_demo_image_layout"/>
</LinearLayout>
```

为TextView的布局：

```xml

<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:orientation="vertical"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content">
    <TextView
        android:id="@+id/viewstub_demo_textview"
        android:layout_width="fill_parent"
        android:layout_height="wrap_content"
        android:background="#aa664411"
        android:textSize="16sp"/>
</LinearLayout>

```

为ImageView的布局：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:orientation="vertical"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content">
    <ImageView
        android:id="@+id/viewstub_demo_imageview"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"/>
</LinearLayout>
```

下面来看代码，决定来显示哪一个，只需要找到相应的ViewStub然后调用其infalte()就可以获得相应想要的布局：

```java

public class ViewStubTestActivity extends AppCompatActivity {
    TextView text;
    LinearLayout viewstubTextLayout;
    Random random = new Random(100);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_stub_test);

        if (random.nextBoolean()) {
            // to show text
            // all you have to do is inflate the ViewStub for textview
            ViewStub stub = (ViewStub) findViewById(R.id.viewstub_demo_text);
            stub.inflate();
            viewstubTextLayout = findViewById(R.id.viewstub_text_layout);
            text = (TextView) findViewById(R.id.viewstub_demo_textview);
            text.setText("The tree of liberty must be refreshed from time to time" +
                    " with the blood of patroits and tyrants! Freedom is nothing but " +
                    "a chance to be better!");
        } else {
            // to show image
            // all you have to do is inflate the ViewStub for imageview
            ViewStub stub = (ViewStub) findViewById(R.id.viewstub_demo_image);
            // 此时,stub 不为null. 但是只要  setVisibility 或者 inflate 一次, 再find 就是null了
            stub.setVisibility(View.VISIBLE);
            ImageView image = (ImageView) findViewById(R.id.viewstub_demo_imageview);
            image.setImageResource(R.mipmap.ic_launcher);
            // 此时stub != null
            if (stub == null) {
                Toast.makeText(ViewStubTestActivity.this, "stub == null", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(ViewStubTestActivity.this, "stub != null", Toast.LENGTH_LONG).show();
            }
        }

        findViewById(R.id.set_text_layout_gone).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //  ViewStub stub = (ViewStub) findViewById(R.id.viewstub_demo_text);//由于上面 inflate 过了,这里 findViewById 总是返回 null
                // 虽然  findViewById(stub) 是null了, 但是我们还是能对stub里面的空间进行操作的.比如设置控件的显示和隐藏等
                text.setVisibility(View.GONE);
                viewstubTextLayout.setVisibility(View.GONE);

            }
        });
        findViewById(R.id.test_find_stub).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ViewStub stub = (ViewStub) findViewById(R.id.viewstub_demo_image);
                // 由于上面 inflate 了. 后续总是null.
                if (stub == null) {
                    Toast.makeText(ViewStubTestActivity.this, "stub == null", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(ViewStubTestActivity.this, "stub != null", Toast.LENGTH_LONG).show();
                }

                // 虽然  findViewById(stub) 是null了, 但是我们还是能对stub里面的空间进行操作的.比如设置控件的显示和隐藏等
            }
        });

    }
}
```

运行结果:
![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/viewstub_resule.png)


使用的时候的注意事项：
1. 某些布局属性要加在ViewStub而不是实际的布局上面，才会起作用，比如上面用的android:layout_margin*系列属性，如果加在 TextView上面，则不会起作用，需要放在它的ViewStub上面才会起作用。而ViewStub的属性在inflate()后会都传给相应的布 局。

2. ViewStub之所以常称之为“延迟化加载”，是因为在教多数情况下，程序 无需显示ViewStub所指向的布局文件，只有在特定的某些较少条件下，此时ViewStub所指向的布局文件才需要被inflate，且此布局文件直 接将当前ViewStub替换掉，具体是通过viewStub.infalte()或 viewStub.setVisibility(View.VISIBLE)来完成；

3. 正确把握住ViewStub的应用场景非常重要，正如如1中所描述需求场景下，使用ViewStub可以优化布局；

4. 对ViewStub的inflate操作只能进行一次，因为inflate的 时候是将其指向的布局文件解析inflate并替换掉当前ViewStub本身（由此体现出了ViewStub“占位符”性质），一旦替换后，此时原来的 布局文件中就没有ViewStub控件了，因此，如果多次对ViewStub进行infalte，会出现错误信息：ViewStub must have a non-null ViewGroup viewParent。

5. 2中所讲到的ViewStub指向的布局文件解析inflate并替换掉当前 ViewStub本身，并不是完全意义上的替换（与include标签还不太一样），替换时，布局文件的layout params是以ViewStub为准，其他布局属性是以布局文件自身为准。

6. ViewStub本身是不可见的，对 ViewStub setVisibility(..)与其他控件不一样，ViewStub的setVisibility 成View.VISIBLE或INVISIBLE如果是首次使用，都会自动inflate其指向的布局文件，并替换ViewStub本身，再次使用则是相 当于对其指向的布局文件设置可见性