---

title:  JVM内存结构

date: 2020-03-16

categories: 

   - Kotlin

tags: 

   - Kotlin 

description: 
​
---


# JVM内存结构
![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/JMM.png)


JVM内存结构主要有三大块：堆内存、方法区和栈。堆内存是JVM中最大的一块由年轻代和老年代组成，而年轻代内存又被分成三部分，Eden空间、From Survivor空间、To Survivor空间,默认情况下年轻代按照8:1:1的比例来分配；

方法区存储类信息、常量、静态变量等数据，是线程共享的区域，为与Java堆区分，方法区还有一个别名Non-Heap(非堆)；栈又分为java虚拟机栈和本地方法栈主要用于方法的执行。

 也可以说分5块:

 1. 程序计数器
 2. 虚拟机栈
 3. 本地方法栈
 4. 方法区
 5. 堆

# PC Register

程序计数器 Program Counter Register

可以看做是当前线程所执行的字节码的行号指示器.


字节码解释器工作时,就是通过改变这个计数器的值,来选取下一条需要执行的字节码指令, 分支/循环/跳转/异常/线程恢复等基础功能都需要依赖这个计数器来完成

Java虚拟机的多线程是通过时间片轮转来实现的, 在一个确定的时刻, 一个处理器(一核心)只会执行一条线程中的指令.因此, 为了线程奇幻后能够恢复到正确的位置, 每条线程都需要一个独立的程序计数器, 各个线程之间计数器互不影响,独立存储. 我们称这类内存区域为线程私有内存.


如果线程正在执行的Java方法, 这个计数器记录的是正在执行的虚拟机字节码指令的地址;
如果正在执行的是Native方法, 这个计数器值为空(Undefined)


此内存区域是唯一一个在Java虚拟机规范中没有规定任何 OutOfMemory情况的区域.

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/program_counter_register.png)

Java源代码 -> 字节码(JVM指令) -> 解释器 -> 机器码 -> CPU





# JVM Stacks

与程序计数器一样, Java Virtual Machine Stacks 也是线程私有, 它的生命周期同线程相同.

虚拟机栈描述的是Java方法执行的内存模型:每个方法在执行的同时, 都会创建一个栈帧, 用于存储方法中的 局部变量表/操作数栈/动态链接/方法出口等信息.(也就是方法的参数, 方法中的局部变量, 以及方法的返回值等信息)

 栈帧（stack frame）是用于支持虚拟机进行方法调用和方法执行的数据结构，它是虚拟机运行时数据区中的虚拟机栈的栈元素。栈帧存储了方法的局部变量表、操作数栈、动态连接和方法返回地址等信息。
 
 一个栈帧就是一个方法运行时候需要的内存

每个方法从调用到执行完成的过程, 就对应一个栈帧在虚拟机栈中入栈和出栈的过程.


虚拟机栈中, 有个局部变量表,里面存编译器期间已知的各种基本数据类型/对象的引用(reference类型, 不用于对象本身, 可能是一个执行对象起始地址的引用指针, 也可能是执行一个代表对象的句柄)/retureAddress(指向一条字节码指令的地址)




调试, debug 模式,  Frames 就是虚拟机栈. 每个线程都有自己的 Frames(虚拟机栈)




## 问题

1. 垃圾回收是否涉及栈内存?


GC 不涉及栈内存

2. 栈内存分配是不是越大越好?

JVM 指定栈内存大小   -Xss size
默认:
Linux(64-bit) 1024KB
MacOS(64-bit) 1024KB
Windows(64-bit) 依赖默认的虚拟内存

当把栈内存划分的过大, 那物理内存总大小一定, 线程数量就会变少.


3. 方法内的局部变量是否是线程安全的?
判断一个方法/变量是不是线程安全的, 最主要的就是看这个变量是不是多个线程共享的.

方法内的变量, 不光要看这个变量是不是局部变量, 还要看这个变量是否逃离了该方法的作用范围: 比如,参数是外面传入的, 比如, 虽然是局部变量, 但是作为返回值返回了, 这些都可能造成变量被其他线程共享访问.



栈内存溢出  StackOverflowError

1. 栈帧过多. 也就是方法嵌套的层数多.

虚拟机栈的大小是固定的, 比如Linux, 1024KB, 那如果方法调用的层次非常深, 栈中的栈帧特别多, 就可能溢出了吧

比如递归调用, 没有正确的结束条件


2. 栈帧过大
就是方法里面的局部变量特别特别多啊.

一个int 4 字节. 一个方法中有 超过 1024/4=256  个int 局部变量, 就溢出了...

## 线程运行诊断

### CPU占用过高


top 命令  查看进程对CPU占用

ps 查看线程对CPU占用

ps H -eo pid,tid,%cpu | grep 进程id


JDK 命令: jstack  进程id  
可以列出进程中所有java线程的信息

可以根据线程id 找到对应出问题的线程. 看到出问题的代码行数

### 线程很长时间无结果

当心死锁

用jstack 也能分析到

## 栈帧的组成


### 局部变量表

局部变量表是一组变量值存储空间，用于存放方法参数和方法内部定义的局部变量。在Java程序被编译成Class文件时，就在方法的Code属性的max_locals数据项中确定了该方法所需要分配的最大局部变量表的容量。
     
局部变量表的容量以变量槽（Slot）为最小单位，32位虚拟机中一个Slot可以存放一个32位以内的数据类型（boolean、byte、char、short、int、float、reference和returnAddress八种）。

那一个Long 或者 double 就需要2个Slot 来存储

reference类型虚拟机规范没有明确说明它的长度，但一般来说，虚拟机实现至少都应当能从此引用中直接或者间接地查找到对象在Java堆中的起始地址索引和方法区中的对象类型数据。

returnAddress类型是为字节码指令jsr、jsr_w和ret服务的，它指向了一条字节码指令的地址。

虚拟机是使用局部变量表完成参数值到参数变量列表的传递过程的，如果是实例方法（非static），那么局部变量表的第0位索引的Slot默认是用于传递方法所属对象实例的引用，在方法中通过this访问。
      
Slot是可以重用的，当Slot中的变量超出了作用域，那么下一次分配Slot的时候，将会覆盖原来的数据。Slot对对象的引用会影响GC（要是被引用，将不会被回收）。

系统不会为局部变量赋予初始值（实例变量和类变量都会被赋予初始值）。也就是说不存在类变量那样的准备阶段。

### 操作数栈

Java虚拟机的解释执行引擎被称为"基于栈的执行引擎"，其中所指的栈就是指－操作数栈。

操作数栈也常被称为操作栈。

和局部变量区一样，操作数栈也是被组织成一个以字长为单位的数组。但是和前者不同的是，它不是通过索引来访问，而是通过标准的栈操作—压栈和出栈—来访问的。比如，如果某个指令把一个值压入到操作数栈中，稍后另一个指令就可以弹出这个值来使用。

虚拟机在操作数栈中存储数据的方式和在局部变量区中是一样的：如int、long、float、double、reference和returnType的存储。对于byte、short以及char类型的值在压入到操作数栈之前，也会被转换为int。

虚拟机把操作数栈作为它的工作区——大多数指令都要从这里弹出数据，执行运算，然后把结果压回操作数栈。比如，iadd指令就要从操作数栈中弹出两个整数，执行加法运算，其结果又压回到操作数栈中，看看下面的示例，它演示了虚拟机是如何把两个int类型的局部变量相加，再把结果保存到第三个局部变量的：
  

```
begin  
iload_0    // push the int in local variable 0 onto the stack  
iload_1    // push the int in local variable 1 onto the stack  
iadd       // pop two ints, add them, push result  
istore_2   // pop int, store into local variable 2  
end  
```
 
在这个字节码序列里，前两个指令iload_0和iload_1将存储在局部变量中索引为0和1的整数压入操作数栈中，其后iadd指令从操作数栈中弹出那两个整数相加，再将结果压入操作数栈。第四条指令istore_2则从操作数栈中弹出结果，并把它存储到局部变量区索引为2的位置。下图详细表述了这个过程中局部变量和操作数栈的状态变化，图中没有使用的局部变量区和操作数栈区域以空白表示。

from to : http://wangwengcn.iteye.com/blog/1622195

# Method Area(方法区)

所有Java虚拟机线程共享的区域.

方法区存储存储类结构的相关信息.包括:成员变量, 成员方法, 构造器, 常量, 静态变量


方法区在虚拟机启动的时候创建. 逻辑上是堆的组成部分, 但是不同 厂商实现可能有所不同.


Oracle 的 HotSpot 在JDK1.8之前, 
实现叫做永久带, 就是使用了堆内存的一部分作为方法区

JDK1.8以后, 叫做元空间,  就使用操作系统的内存作为方法区

不同的实现, 对方法区的位置选择有所不同


当方法区无法满足内存分配的需求的时候 ,  也会抛出 OutOfMemoryError

## 方法区内存溢出

加载的方法太太太多了.
一般很难出现

1.8以前, 永久代实现. 使用参数 -XX:MaxPermSIze=8m 来设置永久代的大小
1.8以后,元空间实现, 使用参数 -XX:MaxMetaspaceSize=8m 来设置源空间的大小



但是, 动态生成类并加载类的时候, 也可能出现方法区溢出的情况

Spring AOP  使用 Cglib 或者 JDK 动态代理, 生成代理类

MyBatis Mapper接口实现类 的生成, 也是使用 Cglib的动态代理


## 运行时常量池


先看下常量池

```java
public class Main {
    public static void main(String[] args) {
        System.out.println("hello world");
    }
}

```


使用 javap 工具, 查看java编译器为我们生成的字节码是什么样子的, 如下:

```java

D:\fan\netty\okhttp\app\build\intermediates\javac\debug\classes\com\fan\okhttp>javap -v Main.class
Classfile /D:/fan/netty/okhttp/app/build/intermediates/javac/debug/classes/com/fan/okhttp/Main.class
  Last modified 2021-4-17; size 545 bytes
  MD5 checksum 84b924d09e9e7e86002aa8b990f706b3
  Compiled from "Main.java"
public class com.fan.okhttp.Main
  minor version: 0
  major version: 51
  flags: ACC_PUBLIC, ACC_SUPER
Constant pool:
   #1 = Methodref          #6.#20         // java/lang/Object."<init>":()V
   #2 = Fieldref           #21.#22        // java/lang/System.out:Ljava/io/PrintStream;
   #3 = String             #23            // hello world
   #4 = Methodref          #24.#25        // java/io/PrintStream.println:(Ljava/lang/String;)V
   #5 = Class              #26            // com/fan/okhttp/Main
   #6 = Class              #27            // java/lang/Object
   #7 = Utf8               <init>
   #8 = Utf8               ()V
   #9 = Utf8               Code
  #10 = Utf8               LineNumberTable
  #11 = Utf8               LocalVariableTable
  #12 = Utf8               this
  #13 = Utf8               Lcom/fan/okhttp/Main;
  #14 = Utf8               main
  #15 = Utf8               ([Ljava/lang/String;)V
  #16 = Utf8               args
  #17 = Utf8               [Ljava/lang/String;
  #18 = Utf8               SourceFile
  #19 = Utf8               Main.java
  #20 = NameAndType        #7:#8          // "<init>":()V
  #21 = Class              #28            // java/lang/System
  #22 = NameAndType        #29:#30        // out:Ljava/io/PrintStream;
  #23 = Utf8               hello world
  #24 = Class              #31            // java/io/PrintStream
  #25 = NameAndType        #32:#33        // println:(Ljava/lang/String;)V
  #26 = Utf8               com/fan/okhttp/Main
  #27 = Utf8               java/lang/Object
  #28 = Utf8               java/lang/System
  #29 = Utf8               out
  #30 = Utf8               Ljava/io/PrintStream;
  #31 = Utf8               java/io/PrintStream
  #32 = Utf8               println
  #33 = Utf8               (Ljava/lang/String;)V
{
  public com.fan.okhttp.Main();
    descriptor: ()V
    flags: ACC_PUBLIC
    Code:
      stack=1, locals=1, args_size=1
         0: aload_0
         1: invokespecial #1                  // Method java/lang/Object."<init>":()V
         4: return
      LineNumberTable:
        line 11: 0
      LocalVariableTable:
        Start  Length  Slot  Name   Signature
            0       5     0  this   Lcom/fan/okhttp/Main;

  public static void main(java.lang.String[]);
    descriptor: ([Ljava/lang/String;)V
    flags: ACC_PUBLIC, ACC_STATIC
    Code:
      stack=2, locals=1, args_size=1
         0: getstatic     #2                  // Field java/lang/System.out:Ljava/io/PrintStream;
         3: ldc           #3                  // String hello world
         5: invokevirtual #4                  // Method java/io/PrintStream.println:(Ljava/lang/String;)V
         8: return
      LineNumberTable:
        line 13: 0
        line 14: 8
      LocalVariableTable:
        Start  Length  Slot  Name   Signature
            0       9     0  args   [Ljava/lang/String;
}
SourceFile: "Main.java"

```



二进制字节码主要包含三部分信息:

1. 类基本信息:  最开始的一部分, Constant Pool 之前的
```java
Classfile /D:/fan/netty/okhttp/app/build/intermediates/javac/debug/classes/com/fan/okhttp/Main.class    // 类的文件
  Last modified 2021-4-17; size 545 bytes           // 最后修改时间
  MD5 checksum 84b924d09e9e7e86002aa8b990f706b3     // MD5值
  Compiled from "Main.java"
public class com.fan.okhttp.Main                    // 访问修饰符, 包名, 类名
  minor version: 0
  major version: 51                                 // 51 是 JDK1.8
  flags: ACC_PUBLIC, ACC_SUPER
```


2. 常量池 Constant Pool 里面都是地址和符号

3. 类方法定义. 

构造方法. 虽然我们没有定义构造方法, 但是编译器会自动生成一个无参构造, 如下:

```java
  public com.fan.okhttp.Main();
    descriptor: ()V
    flags: ACC_PUBLIC
    Code:
      stack=1, locals=1, args_size=1
         0: aload_0
         1: invokespecial #1                  // Method java/lang/Object."<init>":()V
         4: return
      LineNumberTable:
        line 11: 0
      LocalVariableTable:
        Start  Length  Slot  Name   Signature
            0       5     0  this   Lcom/fan/okhttp/Main;
```


另外, 还有一个 static main 方法

```java
  public static void main(java.lang.String[]);
    descriptor: ([Ljava/lang/String;)V
    flags: ACC_PUBLIC, ACC_STATIC
    Code:
      stack=2, locals=1, args_size=1
      // 这里开始, 就是虚拟机的指令了
         0: getstatic     #2                  // Field java/lang/System.out:Ljava/io/PrintStream;
         3: ldc           #3                  // String hello world
         5: invokevirtual #4                  // Method java/io/PrintStream.println:(Ljava/lang/String;)V
         8: return
      LineNumberTable:
        line 13: 0
        line 14: 8
      LocalVariableTable:
        Start  Length  Slot  Name   Signature
            0       9     0  args   [Ljava/lang/String;
```


那我们的一句代码:`System.out.println("hello world");`  就变成了下面这四条Java虚拟机指令.

解释器 对 指令的执行过程, 就是 查表翻译的一个过程, 我们看一看.

```java
        // getstatic 就是获取静态变量, 就是System.out. 为啥呢.  我们去常量池 Constant Pool 中看 #2
        // Constant Pool中:  
        // #2 = Fieldref           #21.#22        //Fieldref 就表示去引用了一个成员变量(其实是静态变量), 引用哪个成员变量呢?  #21.#22  就表示 #21类的#22变量. 我们继续找

        //  #21 = Class              #28           // #21是个类, 哪个类呢? #28     #28 是  java/lang/System, 这样就找到了类 System.
        //  #22 = NameAndType        #29:#30       // NameAndType 表示成员的名字和类型, 这里引用了 #29:#30.  #29 是 out, #30 是 PrintStream. 那就表示成员名字是:out, 它的类型是一个PrintStream

        // 说到这里, 回到#2.  它的意思就是获取  java/lang/System 类中的 out 静态变量, 并且out变量的类型是 PrintStream. 我们翻上去看看 Constant Pool 中的注释关于 #2的注释: java/lang/System.out:Ljava/io/PrintStream;   它说的就是我们刚刚分析的意思了 System的静态变量 out, 类型是:PrintStream
        // 第一行执行完, 我们就拿到了 System.out
         0: getstatic     #2         

         // 继续. ldc 就是取一个常量. 取的是哪个常量呢?  #3, 去Constant Pool 里看一下:  
         // #3 = String             #23             // #3 是个字符串, 重新指向了 #23
         // #23 = Utf8               hello world    // #23 就是字符串 "hello world"
         // 这样, 我们就拿到了字符串了
         3: ldc           #3            
         //   invokevirtual只的是执行虚方法调用.  执行哪个虚方法呢?  #4
         //   #4 = Methodref          #24.#25      // Methodref  代表方法引用, 引用哪个方法呢?  其实是  #24类的#25方法
         //     #24 = Class              #31            // #24 指向了: java/io/PrintStream
         //      #25 = NameAndType        #32:#33        // #25 表示: println:(Ljava/lang/String;)V

         // 到这里, invokevirtual #4 指的是 调用 PrintStream 的 println 方法.

         // 我们把从常量池中查找出来的整合在一起, 就知道了是调用哪个类的哪个方法, 参数是什么.
         5: invokevirtual #4                  // Method java/io/PrintStream.println:(Ljava/lang/String;)V


         8: return
```


因此, 常量池就是存储一些符号, 便于解释器在执行虚拟机指令的时候查询.


* 常量池: 就是一张表, 虚拟机指令根据这张表找到要执行的类名, 方法名, 参数类型, 字面量等信息.

* 运行时常量池: 我们知道, 常量池是在 .class 文件中的. 当该类被加载, 它的常量池信息就会放在运行时常量池(就是内存中), 便于使用. 并把里面的符号地址改为真实地址.

在运行的时候, #1 #2 #3 这些就替换成真实的地址了


### StringTable

StringTable (串池)是运行时常量池的一部分.  



#### 常量池和 串池之间的关系:




先看个方法
```java
public class Main {
    public static void main(String[] args) {
        String s1 = "a";
        String s2 = "b";
        String s3 = "ab";
    }
}
```

javap 查看字节码

```java

D:\fan\netty\okhttp\app\build\intermediates\javac\debug\classes\com\fan\okhttp>javap -v Main.class
Classfile /D:/fan/netty/okhttp/app/build/intermediates/javac/debug/classes/com/fan/okhttp/Main.class
  Last modified 2021-4-17; size 494 bytes
  MD5 checksum b21592b8ad6b78f7427835fe9267199f
  Compiled from "Main.java"
public class com.fan.okhttp.Main
  minor version: 0
  major version: 51
  flags: ACC_PUBLIC, ACC_SUPER
Constant pool:
   #1 = Methodref          #6.#24         // java/lang/Object."<init>":()V
   #2 = String             #25            // a
   #3 = String             #26            // b
   #4 = String             #27            // ab
   #5 = Class              #28            // com/fan/okhttp/Main
   #6 = Class              #29            // java/lang/Object
   #7 = Utf8               <init>
   #8 = Utf8               ()V
   #9 = Utf8               Code
  #10 = Utf8               LineNumberTable
  #11 = Utf8               LocalVariableTable
  #12 = Utf8               this
  #13 = Utf8               Lcom/fan/okhttp/Main;
  #14 = Utf8               main
  #15 = Utf8               ([Ljava/lang/String;)V
  #16 = Utf8               args
  #17 = Utf8               [Ljava/lang/String;
  #18 = Utf8               s1
  #19 = Utf8               Ljava/lang/String;
  #20 = Utf8               s2
  #21 = Utf8               s3
  #22 = Utf8               SourceFile
  #23 = Utf8               Main.java
  #24 = NameAndType        #7:#8          // "<init>":()V
  #25 = Utf8               a
  #26 = Utf8               b
  #27 = Utf8               ab
  #28 = Utf8               com/fan/okhttp/Main
  #29 = Utf8               java/lang/Object
{
  public com.fan.okhttp.Main();
    descriptor: ()V
    flags: ACC_PUBLIC
    Code:
      stack=1, locals=1, args_size=1
         0: aload_0
         1: invokespecial #1                  // Method java/lang/Object."<init>":()V
         4: return
      LineNumberTable:
        line 11: 0
      LocalVariableTable:
        Start  Length  Slot  Name   Signature
            0       5     0  this   Lcom/fan/okhttp/Main;

  public static void main(java.lang.String[]);
    descriptor: ([Ljava/lang/String;)V
    flags: ACC_PUBLIC, ACC_STATIC
    Code:
      stack=1, locals=4, args_size=1
         0: ldc           #2                  // String a
         2: astore_1
         3: ldc           #3                  // String b
         5: astore_2
         6: ldc           #4                  // String ab
         8: astore_3
         9: return
      LineNumberTable:
        line 13: 0
        line 14: 3
        line 15: 6
        line 16: 9
      // 栈帧的局部变量表. 记得, 一个方法, 就对应一个栈帧啊. 对于成员方法, 第一个 Slot , 就是 Slot 应该是存this的.  但是这个main 方法是static 方法, 所以不存this. 因此Slot0 存储了参数.  上面的构造方法的栈帧, Slot0 存的就是 this.
      LocalVariableTable:
        Start  Length  Slot  Name   Signature
            0      10     0  args   [Ljava/lang/String;
            3       7     1    s1   Ljava/lang/String;
            6       4     2    s2   Ljava/lang/String;
            9       1     3    s3   Ljava/lang/String;
}
SourceFile: "Main.java"

```


重点看这几个指令:

```java
         // 去常量池中查找 #2 这个符号.   #2 指向 #25, #25 就是a
         0: ldc           #2                  // String a
         // 将 a 这个字符串变量, 存入 1 号 Slot. 可以从上面看到, 1号Slot 存的是 s1变量
         2: astore_1
         3: ldc           #3                  // String b
         5: astore_2
         6: ldc           #4                  // String ab
         8: astore_3
         9: return
```

常量池最初是放在 字节码文件中(.class 文件), 运行的时候, 才会被加载到运行时常量池中.

注意: 当常量池信息被加载到运行时常量池的时候,  a b ab 这些符号, 并没有真正的生成对象.   他们还仅仅是常量池中的符号. 还没有变成java中的字符串对象.

只有当   引用该符号的  虚拟机 指令开始执行, 这个时候, 才会真正生成Java对象

比如, 当执行到 ldc #2

会把a符号, 变成 "a"的字符串对象.   

然后, 要 把"a"这个字符串对象当做key,    从StringTable (实质是个HashMap)中查找有没有.  如果没有, 就把刚刚生成的"a"对象放入StringTable. 

然后继续执行 ldc #3

就会把b 符号变成 "b"的字符串对象

然后, 把 "b"字符串对象当做 key, 去StringTable 中查找, 如果没有, 就把刚刚生成的 "b" 字符串对象添加到 StringTable 中. 如果有了, 就把StringTable 中的这个字符串对象的引用赋给s2变量

由于使用了 HashMap, 相同字面量的, 在 HashMap 中只存在一份.


总结一下: 常量池的字符串仅仅是符号, 只有在第一次用到的时候, 才会变成对象放在StringTable中.



#### 字符串拼接


看两个字符串拼接的例子
```java
public class Main {
    public static void main(String[] args) {
        String s1 = "a";
        String s2 = "b";
        String s3 = "ab";
        String s4 = s1+s2;
    }
}
```


```java
D:\fan\netty\okhttp\app\build\intermediates\javac\debug\classes\com\fan\okhttp>javap -v Main.class
Classfile /D:/fan/netty/okhttp/app/build/intermediates/javac/debug/classes/com/fan/okhttp/Main.class
  Last modified 2021-4-17; size 679 bytes
  MD5 checksum 5efa64ef2c8075d7dc7f71f48fb86efc
  Compiled from "Main.java"
public class com.fan.okhttp.Main
  minor version: 0
  major version: 51
  flags: ACC_PUBLIC, ACC_SUPER
Constant pool:
   #1 = Methodref          #10.#29        // java/lang/Object."<init>":()V
   #2 = String             #30            // a
   #3 = String             #31            // b
   #4 = String             #32            // ab
   #5 = Class              #33            // java/lang/StringBuilder
   #6 = Methodref          #5.#29         // java/lang/StringBuilder."<init>":()V
   #7 = Methodref          #5.#34         // java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
   #8 = Methodref          #5.#35         // java/lang/StringBuilder.toString:()Ljava/lang/String;
   #9 = Class              #36            // com/fan/okhttp/Main
  #10 = Class              #37            // java/lang/Object
  #11 = Utf8               <init>
  #12 = Utf8               ()V
  #13 = Utf8               Code
  #14 = Utf8               LineNumberTable
  #15 = Utf8               LocalVariableTable
  #16 = Utf8               this
  #17 = Utf8               Lcom/fan/okhttp/Main;
  #18 = Utf8               main
  #19 = Utf8               ([Ljava/lang/String;)V
  #20 = Utf8               args
  #21 = Utf8               [Ljava/lang/String;
  #22 = Utf8               s1
  #23 = Utf8               Ljava/lang/String;
  #24 = Utf8               s2
  #25 = Utf8               s3
  #26 = Utf8               s4
  #27 = Utf8               SourceFile
  #28 = Utf8               Main.java
  #29 = NameAndType        #11:#12        // "<init>":()V
  #30 = Utf8               a
  #31 = Utf8               b
  #32 = Utf8               ab
  #33 = Utf8               java/lang/StringBuilder
  #34 = NameAndType        #38:#39        // append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
  #35 = NameAndType        #40:#41        // toString:()Ljava/lang/String;
  #36 = Utf8               com/fan/okhttp/Main
  #37 = Utf8               java/lang/Object
  #38 = Utf8               append
  #39 = Utf8               (Ljava/lang/String;)Ljava/lang/StringBuilder;
  #40 = Utf8               toString
  #41 = Utf8               ()Ljava/lang/String;
{
  public com.fan.okhttp.Main();
    descriptor: ()V
    flags: ACC_PUBLIC
    Code:
      stack=1, locals=1, args_size=1
         0: aload_0
         1: invokespecial #1                  // Method java/lang/Object."<init>":()V
         4: return
      LineNumberTable:
        line 11: 0
      LocalVariableTable:
        Start  Length  Slot  Name   Signature
            0       5     0  this   Lcom/fan/okhttp/Main;

  public static void main(java.lang.String[]);
    descriptor: ([Ljava/lang/String;)V
    flags: ACC_PUBLIC, ACC_STATIC
    Code:
      stack=2, locals=5, args_size=1
         0: ldc           #2                  // String a
         2: astore_1
         3: ldc           #3                  // String b
         5: astore_2
         6: ldc           #4                  // String ab
         8: astore_3
         9: new           #5                  // class java/lang/StringBuilder
        12: dup
        13: invokespecial #6                  // Method java/lang/StringBuilder."<init>":()V
        16: aload_1
        17: invokevirtual #7                  // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
        20: aload_2
        21: invokevirtual #7                  // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
        24: invokevirtual #8                  // Method java/lang/StringBuilder.toString:()Ljava/lang/String;
        27: astore        4
        29: return
      LineNumberTable:
        line 13: 0
        line 14: 3
        line 15: 6
        line 16: 9
        line 17: 29
      LocalVariableTable:
        Start  Length  Slot  Name   Signature
            0      30     0  args   [Ljava/lang/String;
            3      27     1    s1   Ljava/lang/String;
            6      24     2    s2   Ljava/lang/String;
            9      21     3    s3   Ljava/lang/String;
           29       1     4   s4   Ljava/lang/String;
}
SourceFile: "Main.java"

```


看下字符串拼接操作的字节码指令
```java
        // new 表示创建对象, 创建了一个StringBuilder()对象
         9: new           #5                  // class java/lang/StringBuilder
        12: dup
        // 调用 StringBuilder中的一个特殊方法init, 其实就是它的构造方法
        13: invokespecial #6                  // Method java/lang/StringBuilder."<init>":()V
        // 构造好了之后, 把变量从Slot1中加载上.  aload 和 astore 是相对的操作. astore是把数据放入Slot, aload 是从Slot 中取数据. 因此后面要调用方法了, 在调用方法之前, 先把参数准备好哦
        16: aload_1
        // 把aload_1拿到的参数, 也就是 s1, 作为参数, 然后调用 StringBuilder的 append方法
        17: invokevirtual #7                  // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
        // 从Slot2中取数据
        20: aload_2
        // 把aload_2拿到的参数, 也就是 s2, 作为参数, 然后调用 StringBuilder的 append方法
        21: invokevirtual #7                  // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
        // 最后调用了StringBuilder.toString方法
        24: invokevirtual #8                  // Method java/lang/StringBuilder.toString:()Ljava/lang/String;
        // 把最终转换的结果, 存入 Slot4
        27: astore        4
```

因此 , String s4 = s1+s2;  底层实现是:  new StringBuilder().append(s1).append(s2).toString()


问题: 此时,   s3 == s4 么?  肯定不等于啊. s3 是StringTable 中的,  s4 是堆上的.

```java
//StringBuilder.java
    @Override
    public String toString() {
        // BEGIN Android-added: Return a constant "" for an empty buffer to keep historic behavior.
        if (count == 0) {
            return "";
        }
        // END Android-added: Return a constant "" for an empty buffer to keep historic behavior.
        // Create a copy, don't share the array
        return new String(value, 0, count);
    }
```


问题: 下面的代码, 在不考虑StringTable 已经存在相同的字面量的时候, 创建了几个对象?
```java
        String s1 = "a";
        String s2 = "b";
        String s4 = s1+s2;
```

第一个:StringTable 中创建 "a"
第二个:StringTable 中创建 "b"
第三个:StringBuilder对象, 在堆中
第四个:toString中创建的 new String(), 在堆中



问题:在不考虑StringTable 已经存在相同的字面量的时候, 创建了几个对象?
```java
String str1 = "abc" + "def";
String str2 = "abcdef"
System.out.println(str1 == str2)  //true  都是StringTable 中取的相同的对象
```

上面的问题涉及到字符串常量重载“+”的问题，当一个字符串由多个字符串常量拼接成一个字符串时，它自己也肯定是字符串常量。**字符串常量的“+”号连接, Java虚拟机会在程序编译期将其优化为连接后的值。**
因此, 只在StringTable 中创建了一个 "abcdef" 对象

这种字面量直接相加, 和上面的 s4 = s1 + s2 不同.


问题: 下面的这个代码, 在不考虑StringTable 已经存在相同的字面量的时候,创建了几个对象?

```java
String str = "abc" + new String("def");
```
创建了4个字符串对象和1个StringBuilder对象。

第一个: StringTable中创建"abc"
第二个: StringTable中创建"def"
第三个:堆中创建对象, 其中的  value[] 指向 StringTable 中的 "def"
第四个:堆中StringBuilder对象
第五个:StringBuilder.toStirng会在堆中再创建一个 String对象


```java

String str1 = "abc";  // 在常量池中
 
String str2 = new String("abc"); // 在堆上
```


当直接赋值时，字符串“abc”会被存储在常量池中，只有1份，此时的赋值操作等于是创建0个或1个对象。如果常量池中已经存在了“abc”，那么不会再创建对象，直接将引用赋值给str1；如果常量池中没有“abc”，那么创建一个对象，并将引用赋值给str1。

那么，通过new String("abc");的形式又是如何呢？答案是1个或2个。

当JVM遇到上述代码时，会先检索常量池中是否存在“abc”，如果不存在“abc”这个字符串，则会先在常量池中创建这个一个字符串。然后再执行new操作，会在堆内存中创建一个存储“abc”的String对象，对象的引用赋值给str2。此过程创建了2个对象。

当然，如果检索常量池时发现已经存在了对应的字符串，那么只会在堆内创建一个新的String对象，此过程只创建了1个对象。

在上述过程中检查常量池是否有相同Unicode的字符串常量时，使用的方法便是String中的intern()方法。

下面通过一个简单的示意图看一下String在内存中的两种存储模式。

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/string_pool.png)


总结一下: 

1. 常量池的字符串仅仅是符号, 只有在第一次用到的时候, 才会变成对象放在StringTable中.
2. 利用StringTable, 可以避免重复创建字符串对象
3. 字符串拼接StringBuilder
4. 字符串拼接的编译器优化
5. 可以使用intern, 主动将串池中还没有的实例, 放入串池


针对5, 说一下
```java
String s = new String("a") + new String("b");
```

分析这行代码: 

在StringTable 中创建 "a" 和 "b"

在堆上创建 new String("a")  和 new String("b")

然后 在堆上创建 StringBuilder对象

然后StringBuilder.toString 中会在堆上创建 new String("ab") 对象

那此时, StringTable 中只有 "a" 和 "b".  String("ab") 由于是动态创建的, 在 StringBuilder中是没有"ab"这个对象的.

那如果想要把"ab"也添加到StringBuilder中呢?

调用 `s2 = s.intern()`方法即可. 这个 intern 方法, 会把s所指向的对象, 就是 在堆上创建的  new String("ab")的 那个对象放入串池.  

如果此时串池中没有, 就放入, 并返回串池中的 对象. 如果串池有已经有了, 就无法放入, 但还是会返回串池有的那个对象
也就是说, 不管是否放入成功,  会把串池中的对象返回回来. 
那此时,   s2 == "ab"  true
s == "ab"  true
s == s2  true


这个规则针对JDK1.8及以上

intern 注意区分 版本. 在JDK1.6, 如果串池中没有, 会把堆上的String()的副本放入串池



#### StringTable的位置

1. JDK1.6, StringTable 是放在常量池中, 常量池放在方法区中. 方法区采用永久代实现. 就是使用了堆内存的一部分作为方法区

如果StringTable内存溢出, 报错: OutOfMemory:PermGen space. 
表示, 是由于永久代的空间不足, 造成内存溢出


2. JDK1.8  StringTable 放在堆中了. 方法区使用元空间实现, 放在操作系统的内存中.

内存溢出, 报错: OutOfMemory:GC overhead limit exceeded
这是由于 默认UseGCOverheadLimit是开启的.
UseGCOverheadLimit 参数的意思是: 如果JVM 98%的时间花在垃圾回收上, 但是仅仅释放了2%的堆内存被回收, 就认为JVM已经GG了, 因此报错 OutOfMemory:GC overhead limit exceeded

因此要关掉这个开关: 记得加上参数: -XX:-UseGCOverheadLimit

此时, 
内存溢出, 报错: OutOfMemory:GC java heap space

就说明, StringTable 是在堆中.



![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/string_pool_location.png)


####  StringTable垃圾回收机制


# Heap


Java堆是被所有线程共享的一块内存区域, 在虚拟机启动的时候创建.

此区域的唯一目的就是存放对象的实例. 我们通过new 关键字, 创建出来的对象, 几乎都是在堆上分配内存.

Java堆是垃圾收集器管理的主要区域.  不再被引用的对象就会被回收和释放.

根据虚拟机规范的规定, Java堆可以处于物理上不连续的内存空间.主要逻辑上连续的即可. 在实现时, 既可以实现成固定大小的, 也可以实现成可扩展的, 不过当前主流的虚拟机都是按照可扩展的来实现的.(通过-Xmx 和 -Xms来控制).

如果在堆中没有剩余内存完成实例的分配, 并且堆也无法再扩展的时候, 就会抛出OutOfMemoryError



1. jps 查看当前进程中有哪些Java进程

2. jmap 查看某一时刻对内存占用情况

jap -head 进程id

3. jconsole 图形界面, 连续监测. 还可以监测线程, CPU等

4. java Visual VM
可视化展示JVM内容



# Native Method Stacks

当C/C++方法运行的时候, 会分配本地方法栈. 
