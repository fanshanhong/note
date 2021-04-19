---

title: JVM是什么

date: 2020-03-16

categories: 

   - Kotlin

tags: 

   - Kotlin 

description: 
​
---
<!-- TOC -->

- [定义](#定义)
- [好处](#好处)
- [JDK JRE JVM](#jdk-jre-jvm)
- [常见的JVM](#常见的jvm)

<!-- /TOC -->
# 定义


JVM: Java Virtual Machine , Java的运行环境, Java二进制字节码的运行环境



# 好处

1. 一次编写, 到处运行

2. 自动内存管理, 垃圾回收机制

3. 数组下标越界管理

4. 多态


#  JDK JRE JVM

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/22A5C986B27542790792C6AF0915362F.png)



# 常见的JVM

JVM 是一套规范.只要遵循这套规范, 就能开发自己的JVM

常见的有:
1. Oracle的HotSpot
2. Eclipse的OpenJ9
不同的实现, 底层实现不同.  我们以 HotSpot 来说