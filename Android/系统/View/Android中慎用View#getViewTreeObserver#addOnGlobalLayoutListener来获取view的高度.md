---
title: Android中慎用View#getViewTreeObserver#addOnGlobalLayoutListener来获取view的高度

date: 2020-03-08

categories: 
   - Android

tags: 
   - Android 


description: ​
---

https://blog.csdn.net/weixin_33858485/article/details/91396507




Android中获取View准确宽高的三种方法


# 通过onWindowFocusChanged方法

```java
//Activity的窗口得到焦点时，View已经初始化完成，此时获取到的View的宽高是准确的
public class GetHeightSampleActivity extends AppCompatActivity {

    TextView textView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_get_height);
        textView = findViewById(R.id.tv);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            Log.w("tv_width", "" + textView.getWidth());
            Log.w("tv_height", "" + textView.getHeight());
        }
    }
}
```

# 通过View.post()来实现

通过post可以将一个Runnable放置到消息队列中，等到Looper调用此Runnable时，View已经初始化完成

```java
@Override
protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_get_height);
    textView = findViewById(R.id.tv);

    textView.post(new Runnable() {

        @Override
        public void run() {
            Log.w("tv_width", "" + textView.getWidth());
            Log.w("tv_height", "" + textView.getHeight());
        }
    });
}
```

# 通过ViewTreeObserver的OnGlobalLayoutListener回调

```java
@Override
protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_get_height);
    textView = findViewById(R.id.tv);

    final ViewTreeObserver viewTreeObserver = textView.getViewTreeObserver();
    viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            textView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            Log.w("tv_width", "" + textView.getWidth());
            Log.w("tv_height", "" + textView.getHeight());
        }
    });
}
```