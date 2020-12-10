---
title: TextEditingController说明

date: 2019-03-24

categories: 
   - Flutter

tags: 
   - Flutter 


description: ​
---


这里我们以给 `TextField` 主动赋值为例，其实 Flutter 中，给有状态的 Widget 传递状态或者数据，一般都是通过各种 controller 

`TextEditingController` 可以构造

```dart
TextEditingController({ String text })
    : super(text == null ? TextEditingValue.empty : TextEditingValue(text: text));
```


当给TextField 赋值的时候， 可以

```dart
controller.value = new TextEditingValue(text: "给输入框填入参数");
```

也可以

```dart
controller.text = ’123456‘;
```



TextEditingController 其实 `extends   ValueNotifier  extends ChangeNotifier`

对ValueNotifier不明白的，建议先看明白： [ChangeNotifier的使用](https://github.com/fanshanhong/note/blob/master/Flutter/ChangeNotifier%E7%9A%84%E4%BD%BF%E7%94%A8.md)


其内部维护了一个value



`controller.value=xx` 其实是调用 其set方法. Set 之后就会notify。





```dart

/// A [ChangeNotifier] that holds a single value.
///
/// When [value] is replaced with something that is not equal to the old
/// value as evaluated by the equality operator ==, this class notifies its
/// listeners.
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



同样的，   controller.text = ’111‘  这样赋值， 

其内部也是在给value 赋值

```dart
set text(String newText) {
  value = value.copyWith(
    text: newText,
    selection: const TextSelection.collapsed(offset: -1),
    composing: TextRange.empty,
  );
}
```

调用了value的set方法， 然后就notify了。 



所以

```dart
controller.text = ’123456‘;
```



```dart
controller.value=xx 
```



都可以修改TextField的值。 并且不需要 setState了。