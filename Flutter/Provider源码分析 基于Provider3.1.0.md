---
title: Provider源码分析 基于Provider3.1.0

date: 2019-03-21

categories: 
   - Flutter

tags: 
   - Flutter 


description: ​
---

<!-- TOC -->

- [Provider源码分析 基于Provider3.1.0](#provider源码分析-基于provider310)

<!-- /TOC -->

# Provider源码分析 基于Provider3.1.0





先看Provider

```dart
class Provider<T> extends ValueDelegateWidget<T>
    implements SingleChildCloneableWidget {
  /// Creates a value, store it, and expose it to its descendants.
  ///
  /// The value can be optionally disposed using [dispose] callback. This
  /// callback which will be called when [Provider] is unmounted from the
  /// widget tree, or if [Provider] is rebuilt to use [Provider.value] instead.
  ///
  Provider({
    Key key,
    @required ValueBuilder<T> builder,
    Disposer<T> dispose,
    Widget child,
  }) : this._(
          key: key,
          delegate: BuilderStateDelegate<T>(builder, dispose: dispose),
          updateShouldNotify: null,
          child: child,
        );

  /// Allows to specify parameters to [Provider].
  Provider.value({
    Key key,
    @required T value,
    UpdateShouldNotify<T> updateShouldNotify,
    Widget child,
  }) : this._(
          key: key,
          delegate: SingleValueDelegate<T>(value),
          updateShouldNotify: updateShouldNotify,
          child: child,
        );

  /// Obtains the nearest [Provider<T>] up its widget tree and returns its
  /// value.
  ///
  /// If [listen] is `true` (default), later value changes will trigger a new
  /// [State.build] to widgets, and [State.didChangeDependencies] for
  /// [StatefulWidget].
  static T of<T>(BuildContext context, {bool listen = true}) {
    // this is required to get generic Type
    final type = _typeOf<InheritedProvider<T>>();
    final provider = listen
        ? context.inheritFromWidgetOfExactType(type) as InheritedProvider<T>
        : context.ancestorInheritedElementForWidgetOfExactType(type)?.widget
            as InheritedProvider<T>;

    if (provider == null) {
      throw ProviderNotFoundError(T, context.widget.runtimeType);
    }

    return provider._value;
  }
}
```



Provider的继承关系为：Provider -> ValueDelegateWidget -> DelegateWidget -> StatefulWidget

因此， Provider 本质是个 StatefulWidget， 所以在需要使用Widget的地方都可以使用 Provider。 

然后是`of`方法。 of方法只是用于获取InheritedWidget中维护的数据。在of 方法中， 根据参数是否 listen， 来选择调用 inheritFromWidgetOfExactType  或者  ancestorInheritedElementForWidgetOfExactType。



inheritFromWidgetOfExactType  和  ancestorInheritedElementForWidgetOfExactType的区别：

在inheritFromWidgetOfExactType方法中InheritedWidget将其子widget添加了依赖关系，所以InheritedWidget发生改变，依赖它的子widget就会更新；而ancestorInheritedElementForWidgetOfExactType方法没有和子widget注册依赖关系，当然也不会调用didChangeDependencies方法。





捎带解释一下为啥 Provider.value 是管理一个恒定数据。

```dart
class Provider<T> extends ValueDelegateWidget<T>
    implements SingleChildCloneableWidget {
      Provider.value({
    Key key,
    @required T value,
    UpdateShouldNotify<T> updateShouldNotify,
    Widget child,
  }) : this._(
          key: key,
          delegate: SingleValueDelegate<T>(value),
          updateShouldNotify: updateShouldNotify,
          child: child,
        );
}
```

这个T 没有 extends ChangeNotifier， 所以， 当value 变化了， 是无法 notifyListeners的， 因此， 也不会刷新或者重新构建。



  





下面来看 ChangeNotifierProvider

```dart
class ChangeNotifierProvider<T extends ChangeNotifier>
    extends ListenableProvider<T>{
}
```

```dart
class ListenableProvider<T extends Listenable> extends ValueDelegateWidget<T>
```

```dart
abstract class ValueDelegateWidget<T> extends DelegateWidget{}
```

```dart
abstract class DelegateWidget extends StatefulWidget {}
```

显然， ChangeNotifierProvider 实质也是一个StatefulWidget， 因此在需要Widget的地方， 都可以使用ChangeNotifierProvider。





然后我们看一下ChangeNotifierProvider 是如何使用 InheritedWidget 和 ChangeNotifier 来实现状态管理的。

先看命名构造方法ChangeNotifierProvider.value。这个方法是使用 value（extends ChangeNotifier）来创建一个ChangeNotifierProvider。

```dart
  /// Provides an existing [ChangeNotifier].
  ChangeNotifierProvider.value({
    Key key,
    @required T value,
    Widget child,
  }) : super.value(key: key, value: value, child: child);
```

直接调用了父类的命名构造方法 super.value。 super.value 是去调用 ListenableProvider 里的命名构造方法去了。



```dart
/// Listens to a [Listenable], expose it to its descendants and rebuilds
/// dependents whenever the listener emits an event.
///
/// For usage informations, see [ChangeNotifierProvider], a subclass of
/// [ListenableProvider] made for [ChangeNotifier].
///
/// You will generaly want to use [ChangeNotifierProvider] instead.
/// But [ListenableProvider] is available in case you want to implement
/// [Listenable] yourself, or use [Animation].
class ListenableProvider<T extends Listenable> extends ValueDelegateWidget<T>
    implements SingleChildCloneableWidget {
  /// Creates a [Listenable] using [builder] and subscribes to it.
  ///
  /// [dispose] can optionally passed to free resources
  /// when [ListenableProvider] is removed from the tree.
  ///
  /// [builder] must not be `null`.
  ListenableProvider({
    Key key,
    @required ValueBuilder<T> builder,
    Disposer<T> dispose,
    Widget child,
  }) : this._(
          key: key,
          delegate: _BuilderListenableDelegate(builder, dispose: dispose),
          child: child,
        );

  /// ChangeNotifierProvider.value 是调用了这个命名构造
  /// Provides an existing [Listenable].
  ListenableProvider.value({
    Key key,
    @required T value,
    Widget child,
  }) : this._(
          key: key,
          delegate: _ValueListenableDelegate(value),
          child: child,
        );

  ListenableProvider._valueDispose({
    Key key,
    @required T value,
    Disposer<T> disposer,
    Widget child,
  }) : this._(
          key: key,
          delegate: _ValueListenableDelegate(value, disposer),
          child: child,
        );

  ListenableProvider._({
    Key key,
    @required _ListenableDelegateMixin<T> delegate,
    // ignore: lines_longer_than_80_chars
    // TODO: updateShouldNotify for when the listenable instance change with `.value` constructor
    this.child,
  }) : super(key: key, delegate: delegate);

  /// The widget that is below the current [ListenableProvider] widget in the
  /// tree.
  ///
  /// {@macro flutter.widgets.child}
  final Widget child;

  @override
  ListenableProvider<T> cloneWithChild(Widget child) {
    return ListenableProvider._(
      key: key,
      delegate: delegate as _ListenableDelegateMixin<T>,
      child: child,
    );
  }

  @override
  Widget build(BuildContext context) {
    final delegate = this.delegate as _ListenableDelegateMixin<T>;
    return InheritedProvider<T>(
      value: delegate.value,
      updateShouldNotify: delegate.updateShouldNotify,
      child: child,
    );
  }
}
```



ChangeNotifierProvider.value 是调用了这个命名构造 

```dart
  /// Provides an existing [Listenable].
  ListenableProvider.value({
    Key key,
    @required T value,
    Widget child,
  }) : this._(
          key: key,
          delegate: _ValueListenableDelegate(value),
          child: child,
        );
```

然后归根结底，会调用 `this._()`  ，  塞入参数delegate。delegate 是创建了一个  `_ValueListenableDelegate`对象。稍后会说

 `this._()`   如下：

```dart
ListenableProvider._({
  Key key,
  @required _ListenableDelegateMixin<T> delegate,
  // ignore: lines_longer_than_80_chars
  // TODO: updateShouldNotify for when the listenable instance change with `.value` constructor
  this.child,
}) : super(key: key, delegate: delegate);
```

super 是调用 ValueDelegateWidget  ， 最后调了  DelegateWidget的构造，传入了key 和delegate。 DelegateWidget其实是一个 StatefulWidget了。

ListenableProvider 的build 方法， 返回了 InheritedProvider（InheritedWidget）。



下面看DelegateWidget

DelegateWidget 相较于  StatefulWidget， 就是多了一个delegate。

DelegateWidget的 initState里。调用了 _initDelegate

```dart
 void _initDelegate() {
    assert(() {
      (context as _DelegateElement)._debugIsInitDelegate = true;
      return true;
    }());
    widget.delegate.initDelegate();
    assert(() {
      (context as _DelegateElement)._debugIsInitDelegate = false;
      return true;
    }());
  }
```

这个delegate 是 ListenableProvider 里面创建的 _ValueListenableDelegate 类型。

```dart
class _ValueListenableDelegate<T extends Listenable>
    extends SingleValueDelegate<T> with _ListenableDelegateMixin<T> {
  _ValueListenableDelegate(T value, [this.disposer]) : super(value);

	...

  @override
  void startListening(T listenable, {bool rebuild = false}) {
    assert(disposer == null || debugCheckIsNewlyCreatedListenable(listenable));
    super.startListening(listenable, rebuild: rebuild);
  }
}
```

 上面调用的widget.delegate.initDelegate();    delegate 是 _ValueListenableDelegate类型。  调用它的initDelegate  是调用的 _ListenableDelegateMixin 这个类的。

```dart
mixin _ListenableDelegateMixin<T extends Listenable> on ValueStateDelegate<T> {
  UpdateShouldNotify<T> updateShouldNotify;
  VoidCallback _removeListener;

	...

  @override
  void initDelegate() {
    super.initDelegate();
    if (value != null) startListening(value);
  }

  void startListening(T listenable, {bool rebuild = false}) {
    /// The number of time [Listenable] called its listeners.
    ///
    /// It is used to differentiate external rebuilds from rebuilds caused by
    /// the listenable emitting an event.  This allows
    /// [InheritedWidget.updateShouldNotify] to return true only in the latter
    /// scenario.
    var buildCount = 0;
    final setState = this.setState;
    final listener = () => setState(() => buildCount++);

    var capturedBuildCount = buildCount;
    // purposefully desynchronize buildCount and capturedBuildCount
    // after an update to ensure that the first updateShouldNotify returns true
    if (rebuild) capturedBuildCount--;
    updateShouldNotify = (_, __) {
      final res = buildCount != capturedBuildCount;
      capturedBuildCount = buildCount;
      return res;
    };

    listenable.addListener(listener);
    _removeListener = () {
      listenable.removeListener(listener);
      _removeListener = null;
      updateShouldNotify = null;
    };
  }
}
```

initDelegate 里面掉了 startListening





_ValueListenableDelegate  继承自  _ListenableDelegateMixin。

传入了 _ValueListenableDelegate 对象。调用 widget.delegate.initDelegate(); 
_ValueListenableDelegate 里面没有 .initDelegate() 方法， 就调用了父类_ListenableDelegateMixin的initDelegate。

父类_ListenableDelegateMixin的initDelegate里面会调用 startListening。 

_ValueListenableDelegate 和  _ListenableDelegateMixin  都有  startListening方法，调的是哪个？？

应该是调用_ValueListenableDelegate的（delegate 真正的类型是 _ValueListenableDelegate， 动态绑定啊。。。）， _ValueListenableDelegate里面的startListening调用了super。



在startListening里



```dart
void startListening(T listenable, {bool rebuild = false}) {
    /// The number of time [Listenable] called its listeners.
    ///
    /// It is used to differentiate external rebuilds from rebuilds caused by
    /// the listenable emitting an event.  This allows
    /// [InheritedWidget.updateShouldNotify] to return true only in the latter
    /// scenario.
    var buildCount = 0;
    final setState = this.setState;
    final listener = () => setState(() => buildCount++);

    var capturedBuildCount = buildCount;
    // purposefully desynchronize buildCount and capturedBuildCount
    // after an update to ensure that the first updateShouldNotify returns true
    if (rebuild) capturedBuildCount--;
    updateShouldNotify = (_, __) {
      final res = buildCount != capturedBuildCount;
      capturedBuildCount = buildCount;
      return res;
    };

    listenable.addListener(listener);
    _removeListener = () {
      listenable.removeListener(listener);
      _removeListener = null;
      updateShouldNotify = null;
    };
  }
```

this.setState 是什么



```dart
abstract class StateDelegate {
  BuildContext _context;

  /// The location in the tree where this widget builds.
  ///
  /// See also [State.context].
  BuildContext get context => _context;

  StateSetter _setState;

  /// Notify the framework that the internal state of this object has changed.
  ///
  /// See the discussion on [State.setState] for more information.
  @protected
  StateSetter get setState => _setState;
```



```dart
/// The signature of [State.setState] functions.
typedef StateSetter = void Function(VoidCallback fn);
```

就是我们经常用的那个setState  。我们不是经常写  setState((){}); 里面传一个 方法。



先定义了一个Listener  。 回调是   setState(() => buildCount++)



然后listenable.addListener(listener);  注册了监听。

这个 listenable 是  T value。   T是 ChangeNotifier 类型的。

因此， 当value调用notifyListeners的时候， 这个回调方法就执行了， 就执行了setState







整体流程梳理：



通过InheritedWidget 和 ChangeNotifier 来实现Provider



Step1： 封装InheritedProvider， 由于要适配各种不同类型， 采用泛型

```dart
class InheritedProvider<T> extends InheritedWidget {
  /// Allow customizing [updateShouldNotify].
  const InheritedProvider({
    Key key,
    @required T value,
    UpdateShouldNotify<T> updateShouldNotify,
    Widget child,
  })  : _value = value,
        _updateShouldNotify = updateShouldNotify,
        super(key: key, child: child);

  /// The currently exposed value.
  ///
  /// Mutating `value` should be avoided. Instead rebuild the widget tree
  /// and replace [InheritedProvider] with one that holds the new value.
  final T _value;
  final UpdateShouldNotify<T> _updateShouldNotify;
}
```

 

Step2：

```dart
class ListenableProvider<T extends Listenable> extends ValueDelegateWidget<T>
    implements SingleChildCloneableWidget {

  @override
  Widget build(BuildContext context) {
    final delegate = this.delegate as _ListenableDelegateMixin<T>;
    return InheritedProvider<T>(
      value: delegate.value,
      updateShouldNotify: delegate.updateShouldNotify,
      child: child,
    );
  }
}
```

Provider 和  ChangeNotifierProvider 都是DelegateWidget 的子类。

在 ChangeNotifierProvider的build 中返回 InheritedProvider。InheritedProvider 的 value 是  child 都是外面传入的。传入到Provider 中的那个child， 最终是传给了InheritedWidget。



然后想办法在value 变化的时候， 让InheritedWidget 刷新。因此让 T 继承自 ChangeNotifier。 





Step3：

ChangeNotifierProvider 是` DelegateWidget extends StatefulWidget `子类。

在它的 initState 方法中， 调用_initDelegate， 然后调用widget.delegate.initDelegate();

widget.delegate.initDelegate的时候， 会startListening， 就是 value.addListener((){setState})  给ChangeNotifier添加监听回调



Step4：

然后当value 变化的时候，  notifyListeners，   就直接setState了。 

setState 会导致 ChangeNotifierProvider 重新构建， 然后会传入新的 value 值， 并且InheritedProvider会重新构建。这样， InheritedProvider的子组件里面就能拿到InheritedProvider里最新的值了。





完毕。





接下来再看这个帖子是如何封装的， 就能很明白了。


链接：https://www.jianshu.com/p/5b8cc89cb9de
来源：简书
著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。