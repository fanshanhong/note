Flutter中比较有名的[Provider](https://links.jianshu.com/go?to=https%3A%2F%2Fpub.dev%2Fpackages%2Fprovider)核心也是通过InheritedWidget来实现的，接着我们来实现一个自己的简易Provider。





```dart
// 一个通用的InheritedWidget，保存任需要跨组件共享的状态
import 'package:flutter/cupertino.dart';



// 存数据的InheritedWidget
class InheritedProvider<T> extends InheritedWidget {
  // data 和 child 都要从外面传入

  InheritedProvider({@required this.data, Widget child}) : super(child: child);

  //共享状态使用泛型
  final T data;

  @override
  bool updateShouldNotify(InheritedProvider<T> old) {
    //在此简单返回true，则每次更新都会调用依赖其的子孙节点的`didChangeDependencies`。
    return true;
  }
}
```





InheritedWidget 有了。



InheritedProvider 中的数据发生变化后如何通知？



ChangeNotifier





```
Change
```

