---
title: ChangeNotifier的使用

date: 2019-03-05

categories: 
   - Flutter

tags: 
   - Flutter 


description: ​
---

<!-- TOC -->

- [Listenable](#listenable)
- [ValueListenable](#valuelistenable)
- [ChangeNotifier](#changenotifier)
- [ChangeNotifier 使用](#changenotifier-使用)
- [ValueNotifier 使用](#valuenotifier-使用)

<!-- /TOC -->


# Listenable

ChangeNotifier的根是 Listenable



```dart
/// An object that maintains a list of listeners.
/// 一个对象，维护了一系列的监听器。
///
/// The listeners are typically used to notify clients that the object has been
/// updated.
/// 这些监听器通常用于去通知客户端 object被更新了。
///
/// There are two variants of this interface:
/// 两个变种的接口：ValueListenable   Animation
///
///  * [ValueListenable], an interface that augments the [Listenable] interface
///    with the concept of a _current value_.
///
///  * [Animation], an interface that augments the [ValueListenable] interface
///    to add the concept of direction (forward or reverse).
///
/// Many classes in the Flutter API use or implement these interfaces. The
/// following subclasses are especially relevant:
///
///  * [ChangeNotifier], which can be subclassed or mixed in to create objects
///    that implement the [Listenable] interface.
///
///  * [ValueNotifier], which implements the [ValueListenable] interface with
///    a mutable value that triggers the notifications when modified.
abstract class Listenable {
  /// Abstract const constructor. This constructor enables subclasses to provide
  /// const constructors so that they can be used in const expressions.
  const Listenable();

  /// Return a [Listenable] that triggers when any of the given [Listenable]s
  /// themselves trigger.
  ///
  /// The list must not be changed after this method has been called. Doing so
  /// will lead to memory leaks or exceptions.
  ///
  /// The list may contain nulls; they are ignored.
  factory Listenable.merge(List<Listenable> listenables) = _MergingListenable;

  /// Register a closure to be called when the object notifies its listeners.
  void addListener(VoidCallback listener);

  /// Remove a previously registered closure from the list of closures that the
  /// object notifies.
  void removeListener(VoidCallback listener);
}
```




# ValueListenable


ValueListenable 这个就是Listenable里面多维护了一个value

```dart
/// An interface for subclasses of [Listenable] that expose a [value].
///
/// This interface is implemented by [ValueNotifier<T>] and [Animation<T>], and
/// allows other APIs to accept either of those implementations interchangeably.
abstract class ValueListenable<T> extends Listenable {
  /// Abstract const constructor. This constructor enables subclasses to provide
  /// const constructors so that they can be used in const expressions.
  const ValueListenable();

  /// The current value of the object. When the value changes, the callbacks
  /// registered with [addListener] will be invoked.
  T get value;
}
```



# ChangeNotifier



```dart
class ChangeNotifier implements Listenable {
  ObserverList<VoidCallback> _listeners = ObserverList<VoidCallback>();
 }
```

ChangeNotifier继承自Listenable， 里面维护了一些监听器

ValueNotifier 相比较 ChangeNotifier 是里面多了一个value

```dart
class ValueNotifier<T> extends ChangeNotifier implements ValueListenable<T> {
  /// Creates a [ChangeNotifier] that wraps this value.
  ValueNotifier(this._value);

  /// The current value stored in this notifier.
  ///
  /// When the value is replaced with something that is not equal to the old
  /// value as evaluated by the equality operator ==, this class notifies its
  /// listeners.
  @override
  T get value => _value;
  T _value;
  set value(T newValue) {
    if (_value == newValue)
      return;
    _value = newValue;
    notifyListeners();
  }

  @override
  String toString() => '${describeIdentity(this)}($value)';
}
```



# ChangeNotifier 使用

ChangeNotifier 使用：先注册监听器， 再需要触发的时候调用notify就行了。

```dart
///
/// 由于ChangeNotifier里面没有携带数据, 这里继承自ChangeNotifier,然后自己维护一个数据
class MyChangeNotifier extends ChangeNotifier {

//  int data = 0;
//
//  void setData(int d) {
//    this.data = d;
//    // 手动调用
//    notifyListeners();
//  }
//
//  int getData() {
//    return data;
//  }
}
```

```dart
myChangeNotifier.addListener((){
  // 注册一个监听
  print('aaaaaa');
});

myChangeNotifier.addListener((){
  // 注册一个监听
  print('vvv');
});
```

```dart
RaisedButton(
  child: Text('触发'),
  onPressed: (){
    myChangeNotifier.notifyListeners();
  },
),
```




# ValueNotifier 使用

ValueNotifier 使用:


这个里面维护了一个value， 并且set的时候， 自动会notify， 因此不需要手动调用notify了。

```dart
 myValueNotifier.addListener((){
      // 注册一个监听
      print('myChangeNotifieraaaaaa');
    });

    myValueNotifier.addListener((){
      // 注册一个监听
      print('myChangeNotifier  ${myValueNotifier.value}');
    });
```

```dart
RaisedButton(
  child: Text('触发2'),
  onPressed: (){
    myValueNotifier.value=DateTime.now().millisecondsSinceEpoch;
  },
)
```

这样就可以了。