---
title: InheritedWidget

date: 2019-03-18

categories: 
   - Flutter

tags: 
   - Flutter 


description: ​
---

<!-- TOC -->

- [InheritedWidget](#inheritedwidget)
- [didChangeDependencies调用](#didchangedependencies调用)
- [整体流程](#整体流程)

<!-- /TOC -->

# InheritedWidget


InheritedWidget是 Flutter 中的一种功能型组件  ， 用于实现 Flutter 组件之间的数据共享。其数据传递方向在Widget 树传递是从上到下的。

就是说：InheritedWidget 里面保存一份共享的数据，这个数据可以给它的子组件来使用。



```dart
import 'package:flutter/material.dart';

///
/// 一个 InheritedWidget 组件
/// 其中保存了用于给子组件共享的数据
///
class ShareDataWidget extends InheritedWidget {
  // 一般属性用私有的, 然后对外提供 get 和 set 方法, 这里保存了点击的次数
  int _data;

  int get data => _data;

  set data(int value) {
    _data = value;
  }

  // Constructor
  ShareDataWidget({@required int data, Widget child}) : super(child: child) {
    this._data = data;
    print('ShareDataWidget构造方法被调用了');
  }

  static ShareDataWidget of(BuildContext buildContext) {

    // 方法已经废弃, 使用 dependOnInheritedWidgetOfExactType 替换
    Widget widget  = buildContext.inheritFromWidgetOfExactType(ShareDataWidget);

    // 使用给定的类型T 获取一个最近的Widget(必须是InheritedWidget的子类)
    // 并且, 会把这个当前的这个 buildContext 注册
    // 当父组件变化的时候,  当前的  buildContext 会重新构建, 这样就能拿到那个组件里的新的值了.

    // Obtains the nearest widget of the given type [T], which must be the type of a
    // concrete [InheritedWidget] subclass, and registers this build context with
    // that widget such that when that widget changes (or a new widget of that
    // type is introduced, or the widget goes away), this build context is
    // rebuilt so that it can obtain new values from that widget.

    Widget widget1  = buildContext.dependOnInheritedWidgetOfExactType<ShareDataWidget>();

    // 方法已经废弃, 使用 findAncestorWidgetOfExactType 替换
    Widget widget2 = buildContext.ancestorInheritedElementForWidgetOfExactType(ShareDataWidget).widget;
    // 不注册
    Widget widget3 = buildContext.getElementForInheritedWidgetOfExactType<ShareDataWidget>().widget;
    return widget1;
  }

  @override
  bool updateShouldNotify(ShareDataWidget oldWidget) {
    return oldWidget.data != data;
  }
}

```

- 首先继承自 InheritedWidget。InheritedWidget 的继承关系是这样的：InheritedWidget -> ProxyWidget -> Widget 。

- ShareDataWidget 中存放需要共享的数据，这里我们保存了点击次数，一个int 值

- `of`方法。

  可以使用`BuildContext.dependOnInheritedWidgetOfExactType`获取指定类型的Inherited Widget 的"最近"的实例。
  此处的"最近"的意思是从当前Widget（调用`BuildContext.dependOnInheritedWidgetOfExactType`方法的Widget）， （也就是，在调用of方法的时候需要传入参数BuildContext，传入了哪个Widget的Context，当前 Widget 指的就是那个Widget），向上溯源，找到第一个指定类型的Inherited Widget的实例。因此，`dependOnInheritedWidgetOfExactType` 这个方法的参数肯定应该是 InheritedWidget 的子类。

  如果在Widget Tree内没有发现指定类型的Widget，在上述的例子中，这种情况下会返回null，但是也可以定义一个默认值。

  `of`方法一般是给子Widget调用的。

  

  举个列子解释下？

  

  ```dart
  import 'package:flutter/material.dart';
  import 'package:my_provider/inherited/child_widget.dart';
  
  import 'share_date_widget.dart';
  
  ///
  /// 测试页面
  ///
  class TestInheritedPage extends StatefulWidget {
    @override
    State<StatefulWidget> createState() {
      return _InheritedWidgetTestState();
    }
  }
  
  class _InheritedWidgetTestState extends State<TestInheritedPage> {
    int count = 0;
  
    @override
    void initState() {
      print('TestInheritedPage initState');
      super.initState();
    }
  
    @override
    Widget build(BuildContext context) {
      print('TestInheritedPage build');
      return Center(
        //使用InheritedWidget, 其子组件就可以使用InheritedWidget内部维护的数据了
        child: ShareDataWidget(
          data: 0,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: <Widget>[
              Padding(
                padding: const EdgeInsets.only(bottom: 20.0),
                child: ChildWidget(), //子widget中依赖ShareDataWidget(InheritedWidget)
              ),
              RaisedButton(
                child: Text("计数增加"),
                onPressed: () {
                  setState(() {
                    ++count;
                  });
                },
              )
            ],
          ),
        ),
      );
    }
  }
  ```

  ChildWidget 是ShareDataWidget的子组件，可以使用它的共享数据

  ChildWidget 里面调用`of`方法， 其实质是：从当前这个 ChildWidget 开始，向上溯源，找最近的类型为ShareDataWidget的组件。那就找到了ShareDataWidget。然后就可以在ChildWidget里面使用  ShareDataWidget共享数据 data了。

  ```dart
  
  import 'package:flutter/material.dart';
  import 'package:my_provider/inherited/share_date_widget.dart';
  
  ///
  /// 作为 ShareDataWidget 的子组件, 使用 ShareDataWidget 中维护的数据
  ///
  class ChildWidget extends StatefulWidget {
    // Constructor
    ChildWidget() {
      print('ChildWidget构造方法被调用了');
    }
  
    @override
    State<StatefulWidget> createState() {
      return _ChildWidgetState();
    }
  }
  
  class _ChildWidgetState extends State<ChildWidget> {
    @override
    Widget build(BuildContext context) {
      print('ChildWidget build');
  
      //使用 InheritedWidget 中的共享数据
      return Text(ShareDataWidget.of(context).data.toString());
    }
  
    @override
    void didChangeDependencies() {
      super.didChangeDependencies();
      //父或祖先widget中的InheritedWidget改变(updateShouldNotify返回true)时会被调用。
      //如果build中没有依赖InheritedWidget，则此回调不会被调用。
      print("ChildWidget Dependencies change");
    }
  }
  
  ```

  

  `of`方法的返回值：

  如果想要of方法直接返回InheritedWidget中保存的共享数据，就直接把共享数据返回就行了。返回值改成对应的。比如这里，如果我想要直接返回data，就

  ```dart
  int ShareDataWidget of(BuildContext context){
     ShareDataWidget shareData =  context.dependOnInheritedWidgetOfExactType<ShareDataWidget>(); 
       return  shareData.data;
    }
  ```

  

  

- 由以上实现我们可以看到updateShouldNotify 返回值 决定当data发生变化时，是否通知子树中依赖data的Widget 更新数据

  

```dart
 @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    //上层 widget中的InheritedWidget改变(updateShouldNotify返回true)时会被调用。
    //如果build中没有依赖InheritedWidget，则此回调不会被调用。
    print("didChangeDependencies");
  }
```





# didChangeDependencies调用

- 继承StatefulWidget时State对象有一个回调didChangeDependencies，它会在“依赖”发生变化时被Flutter Framework调用。
   而这个“依赖”指的就是是否使用了父widget中InheritedWidget的数据，如果使用了，则代表有依赖，如果没有使用则代表没有依赖。
   这种机制可以使子组件在所依赖的主题、locale等发生变化时有机会来做一些事情。

- 如果不想调用让didChangeDependencies被调用，也是有办法的，如下改变ShareDataWidget的of方法

```dart
 // 子树中的widget获取共享数据 方法
  static ShareDataWidget of(BuildContext context){
    return context.getElementForInheritedWidgetOfExactType(ShareDataWidget).widget;
  }
```

- 使用context.ancestorInheritedElementForWidgetOfExactType（getElementForInheritedWidgetOfExactType）方法，为什么didChangeDependencies就不会被调用呢？看源码就是最好的解释，我们直接翻到**framework.dart**中这两个方法的源码



```dart
/**
 * framework.dart  
 */
  @Deprecated(
    'Use getElementForInheritedWidgetOfExactType instead. '
    'This feature was deprecated after v1.12.1.'
  )
  @override
  InheritedElement ancestorInheritedElementForWidgetOfExactType(Type targetType) {
    assert(_debugCheckStateIsActiveForAncestorLookup());
    final InheritedElement ancestor = _inheritedWidgets == null ? null : _inheritedWidgets[targetType];
    return ancestor;
  }

  @override
  InheritedElement getElementForInheritedWidgetOfExactType<T extends InheritedWidget>() {
    assert(_debugCheckStateIsActiveForAncestorLookup());
    final InheritedElement ancestor = _inheritedWidgets == null ? null : _inheritedWidgets[T];
    return ancestor;
  }

 

@Deprecated(
    'Use dependOnInheritedWidgetOfExactType instead. '
    'This feature was deprecated after v1.12.1.'
  )
  @override
  InheritedWidget inheritFromWidgetOfExactType(Type targetType, { Object aspect }) {
    assert(_debugCheckStateIsActiveForAncestorLookup());
    final InheritedElement ancestor = _inheritedWidgets == null ? null : _inheritedWidgets[targetType];
    if (ancestor != null) {
      assert(ancestor is InheritedElement);
      return inheritFromElement(ancestor, aspect: aspect);
    }
    _hadUnsatisfiedDependencies = true;
    return null;
  }

  @override
  T dependOnInheritedWidgetOfExactType<T extends InheritedWidget>({Object aspect}) {
    assert(_debugCheckStateIsActiveForAncestorLookup());
    final InheritedElement ancestor = _inheritedWidgets == null ? null : _inheritedWidgets[T];
    if (ancestor != null) {
      assert(ancestor is InheritedElement);
      return dependOnInheritedElement(ancestor, aspect: aspect) as T;
    }
    _hadUnsatisfiedDependencies = true;
    return null;
  }
```

- 显然，一对比我们就可以看到dependOnInheritedWidgetOfExactType多调用了dependOnInheritedElement方法，继续看该方法源码



```csharp
/**
 * framework.dart  dependOnInheritedElement方法源码
 */
 
  @override
  InheritedWidget dependOnInheritedElement(InheritedElement ancestor, { Object aspect }) {
    assert(ancestor != null);
    _dependencies ??= HashSet<InheritedElement>();
    _dependencies.add(ancestor);
    ancestor.updateDependencies(this, aspect);
    return ancestor.widget;
  }
```

- 到这里，一切都变得很清晰， dependOnInheritedWidgetOfExactType方法中调用了dependOnInheritedElement方法，而在该方法中InheritedWidget将其子widget添加了依赖关系（`_dependencies.add(ancestor);`），所以InheritedWidget发生改变，依赖它的子widget就会更新，也就会调用刚刚所说的didChangeDependencies方法，而getElementForInheritedWidgetOfExactType方法没有和子widget注册依赖关系，当然也不会调用didChangeDependencies方法。






# 整体流程

最后说一下整体的流程

代码贴一下

```dart
import 'package:flutter/material.dart';
import 'package:my_provider/inherited/child_widget.dart';

import 'share_date_widget.dart';

///
/// 测试页面
///
class TestInheritedPage extends StatefulWidget {
  @override
  State<StatefulWidget> createState() {
    return _InheritedWidgetTestState();
  }
}

class _InheritedWidgetTestState extends State<TestInheritedPage> {
  int count = 0;

  @override
  void initState() {
    print('_InheritedWidgetTestState initState');
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    print('_InheritedWidgetTestState build');
    return Center(
      //使用InheritedWidget, 其子组件就可以使用InheritedWidget内部维护的数据了
      child: ShareDataWidget(
        data: count,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            Padding(
              padding: const EdgeInsets.only(bottom: 20.0),
              child: ChildWidget(), //子widget中依赖ShareDataWidget(InheritedWidget)
            ),
            RaisedButton(
              child: Text("计数增加"),
              onPressed: () {
                setState(() {
                  ++count;
                });
              },
            )
          ],
        ),
      ),
    );
  }
}
```



第一次build组件：

ChildWidget 作为子组件，在其中调用了 ShareDataWidget 的 of方法， 拿到ShareDataWidget中的数据并且展示， 没问题。



然后，点击

count++， 并且setState。 页面重绘，导致重新构建 ShareDataWidget（InheritedWidget）以及其子组件ChildWidget，并把新的data传入ShareDataWidget了。这时候，ShareDataWidget中的那个data是1（新的值）。ChildWidget里面再用ShareDataWidget中的data， 就显示新的值1了。





setState的时候， ShareDataWidget 、ChildWidget和RaisedButton都会重新构建的。可以自行在构造方法和build 方法中打Log测试一下。



tip：Theme 和 Locale(当前语言环境)都是用InheritedWidget 来实现的。







参考：

作者：ershixiong
链接：https://www.jianshu.com/p/19b053fa0819
来源：简书
著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。

作者：maoqitian
链接：https://www.jianshu.com/p/ce05ad0bdb1f
来源：简书
著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。