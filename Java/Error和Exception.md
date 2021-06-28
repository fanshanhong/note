---
title: Java中Error和Exception的异同以及运行时异常（Runtime exception)与检查型异常（checked exception）的区别

date: 2016-03-16

categories: 
   - Java

tags: 
   - Java 

description: 
---

<!-- TOC -->

- [Error和Exception的基本概念](#error和exception的基本概念)
    - [二者的不同之处(小总结)](#二者的不同之处小总结)
- [常见的 Exception 和 Error](#常见的-exception-和-error)
    - [示例](#示例)
- [Error和Exception的使用场景](#error和exception的使用场景)
- [运行时异常（Runtime exception)与检查型异常（checked exception）的区别](#运行时异常runtime-exception与检查型异常checked-exception的区别)

<!-- /TOC -->

# Error和Exception的基本概念

首先Exception和Error都是继承于Throwable 类，在 Java 中只有 Throwable 类型的实例才可以被抛出（throw）或者捕获（catch），它是异常处理机制的基本组成类型。

Exception 和 Error 体现了 Java 平台设计者对不同异常情况的分类，Exception和Error体现了JAVA这门语言对于异常处理的两种方式。

Exception 是程序正常运行过程中可以预料到的意外情况，并且应该被开发者捕获，进行相应的处理。

 

Error是java程序运行中不可预料的异常情况（正常情况下不大可能出现的情况），这种异常发生以后，会直接导致JVM不可处理或者不可恢复的情况。所以这种异常不可能抓取到，比如OutOfMemoryError、NoClassDefFoundError等。【表示由JVM所侦测到的无法预期的错误，由于这是属于JVM层次的严重错误 ，导致JVM无法继续执行，因此，这是不可捕捉到的，无法采取任何恢复的操作，顶多只能显示错误信息。 Error类体系描述了Java运行系统中的内部错误以及资源耗尽的情形.应用程序不应该抛出这种类型的对象(一般是由虚拟机抛出).假如出现这种错误,除了尽力使程序安全退出外,在其他方面是无能为力的。】

 

其中的Exception又分为检查性异常（checked）和非检查性异常（unchecked, 又称运行时异常(RuntimeException)）。两个根本的区别在于，检查性异常 必须在编写代码时，使用try catch捕获（比如：IOException异常）。非检查性异常 在代码编写使，可以忽略捕获操作（比如：ArrayIndexOutOfBoundsException），这种异常是在代码编写或者使用过程中通过规范可以避免发生的，具体根据需要来判断是否需要捕获，并不会在编译器强制要求。

切记，Error是Throw不是Exception 。


## 二者的不同之处(小总结)

Exception：
1. 可以是可被控制(checked) 或不可控制的(unchecked)。
2. 表示一个由程序员导致的错误。
3. 应该在应用程序级被处理。

Error：
1. 总是不可控制的(unchecked)。
2. 经常用来用于表示系统错误或低层资源的错误。
3. 如何可能的话，应该在系统级被捕捉。


# 常见的 Exception 和 Error

如下是常见的 Error 和 Exception：

1）运行时异常（RuntimeException）也称 【非检查型异常 UncheckedException】：

* Nullpointer Exception：空指针异常；
* ClassCastException：类型强制转换异常
* IllegalArgumentException：传递非法参数异常
* IndexOutOfBoundsException：下标越界异常
* NumberFormatException：数字格式异常
* ArrayIndexOutOfBoundsException: 数组越界异常
* ArrayStoreException: 数据存储异常，操作数组时类型不一致
* ArithmeticException： (算术异常)
* BufferOverflowException： (缓冲区溢出异常)

2）非运行时异常（CheckedException）也称 【检查型异常】：

* ClassNotFoundException：找不到指定 class 的异常
* IOException：IO 操作异常
* FileNotFoundException：文件不存在异常
* SQLException：SQL语句异常
* InterruptedException： (中断异常-调用线程睡眠时候)

3）错误（Error）：

* NoClassDefFoundError：找不到 class 定义异常
* StackOverflowError：深递归导致栈被耗尽而抛出的异常
* OutOfMemoryError：内存溢出异常


## 示例

下面代码会导致 Java 堆栈溢出错误。

```java
// 通过无限递归演示堆栈溢出错误
class StackOverflow {
    public static void test(int i) {
        if (i == 0) {
            return;
        } else {
            test(i++);
        }
    }
}
public class ErrorEg {
    public static void main(String[] args) {
        // 执行StackOverflow方法
        StackOverflow.test(5);
    }
}
```

运行输出为：
```
Exception in thread "main" java.lang.StackOverflowError
    at ch11.StackOverflow.test(ErrorEg.java:9)
    at ch11.StackOverflow.test(ErrorEg.java:9)
    at ch11.StackOverflow.test(ErrorEg.java:9)
    at ch11.StackOverflow.test(ErrorEg.java:9)
```

上面代码通过无限递归调用最终引发了 java.lang.StackOverflowError 错误。


# Error和Exception的使用场景

经典的面试题目: 就是 NoClassDefFoundError 和 ClassNotFoundException 有什么区别

```java
区别一：NoClassDefFoundError它是Error，ClassNotFoundException是Exception。

区别二：NoClassDefFoundError是JVM运行时通过classpath加载类时，找不到对应的类而抛出的错误。       ClassNotFoundException是在编译过程中如果可能出现此异常，在编译过程中必须将ClassNotFoundException异常抛出！

NoClassDefFoundError发生场景如下：
    1、类依赖的class或者jar不存在 （简单说就是maven生成运行包后被篡改）
    2、类文件存在，但是存在不同的域中 （简单说就是引入的类不在对应的包下)
    3、大小写问题，javac编译的时候是无视大小的，很有可能你编译出来的class文件就与想要的不一样！这个没有做验证


ClassNotFoundException发生场景如下：
   1、调用class的forName方法时，找不到指定的类
   2、ClassLoader 中的 findSystemClass() 方法时，找不到指定的类
    举例说明如下:
           Class.forName("abc"); 比如abc这个类不存项目中，代码编写时，就会提示此异常是检查性异常，比如将此异常抛出。
```



# 运行时异常（Runtime exception)与检查型异常（checked exception）的区别

Java提供了两类主要的异常:runtime exception和checked exception。

1. checked （检查型异常）也就是我们经常遇到的IO异常，以及SQL异常都是这种异常。编译器要检查这类异常，检查的目的一方面是因为该类异常的发生难以避免，另一方面就是让开发者去解决掉这类异常，所以称为必须处理（try ...catch）的异常。如果不处理这类异常，集成开发环境中的编译器一般会给出错误提示。

2. runtime exception（运行时异常），编译器不会检查这类异常，不检查的则开发者在代码的编辑编译阶段就不是必须处理，这类异常一般可以避免，因此无需处理（try ...catch）, 如果不处理这类异常，集成开发环境中的编译器也不会给出错误提示。    当出现这样的异常时，总是由虚拟机接管。

    eg：我们从来没有人去处理过NullPointerException异常，它就是运行时异常，并且这种异常还是最常见的异常之一。 