---
title: Flutter的pubspec.yaml中的依赖版本号之前的插入号（^）是什么？

date: 2019-03-08

categories:
   - Flutter

tags:
   - Flutter


description: ​
---



Flutter的pubspec.yaml中的依赖版本号之前的插入号（^）是什么？


# 脱字符号（^）

脱字符号（^）用于Dart中的发布依赖关系，以指示允许的版本号范围。具体来说，从指定版本到（但不包括）下一个
非破坏性 版本的任何版本都是可以的。

* 所以^3.1.5和'>=3.1.5 <4.0.0'
* 并且^1.2.3将与'>=1.2.3 <2.0.0'

它是较长形式的缩写。

该^是说，我想自动使用最先进的最新包从酒吧只要该更新我的应用程序不会破坏任何东西。


# 澄清低于1.0.0的版本

本来我以为

^0.1.2与 （错误！） 相同'>=0.1.2 <1.0.0' __
但是，这是对语义版本控制的错误理解。当主版本号为时0（如0的0.1.2），其含义是该API不稳定，即使是次要版本号的更改（如1的0.1.2）也可能表示重大更改。

在语义版本的文章中指出：

> 主要版本零（0.yz）用于初始开发。随时可能发生任何变化。公共API不应被认为是稳定的。

并且

> 我应该如何在0.yz初始开发阶段处理修订？

> 最简单的方法是从0.1.0开始初始开发版本，然后为每个后续版本增加次要版本。

因此，以下是更正的形式：

* ^0.1.2 是相同的 '>=0.1.2 <0.2.0'