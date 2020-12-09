---
title: Android性能优化第3季

date: 2017-8-16

categories: 
   - Android
   - 性能优化

tags: 
   - Android 
   - U性能优化 

description: ​
---

<!-- TOC -->

- [1.Fun with ArrayMaps](#1fun-with-arraymaps)
- [2.Beware Autoboxing](#2beware-autoboxing)
- [3.SparseArray Family Ties](#3sparsearray-family-ties)
- [4.The price of ENUMs](#4the-price-of-enums)
- [5.](#5)
- [6.DO NOT LEAK VIEWS](#6do-not-leak-views)
    - [6.1避免使用异步回调](#61避免使用异步回调)
    - [6.2 避免使用Static对象](#62-避免使用static对象)
    - [6.4 避免把View添加到没有清除机制的容器里面](#64-避免把view添加到没有清除机制的容器里面)
- [7.Location & Battery Drain](#7location--battery-drain)
- [8.Double Layout Taxation](#8double-layout-taxation)
- [9.Network Performance 101](#9network-performance-101)
    - [9.1减少移动网络被激活的时间与次数](#91减少移动网络被激活的时间与次数)
    - [9.2 压缩传输数据](#92-压缩传输数据)
- [10.](#10)
- [11.Optimizing Network Request Frequencies](#11optimizing-network-request-frequencies)
- [12.Effective Prefetching](#12effective-prefetching)

<!-- /TOC -->


# 1.Fun with ArrayMaps
---

ArrayMap 代替 HashMap

# 2.Beware Autoboxing
---

有时候性能问题也可能是因为那些不起眼的小细节引起的，例如在代码中不经意的“自动装箱”。我们知道基础数据类型的大小：boolean(8 bits), int(32 bits), float(32 bits)，long(64 bits)，为了能够让这些基础数据类型在大多数Java容器中运作，会需要做一个autoboxing的操作，转换成Boolean，Integer，Float等对象，如下演示了循环操作的时候是否发生autoboxing行为的差异：

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/android_perf_3_autoboxing_for.png)

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/android_perf_3_autoboxing_perf.png)

Autoboxing的行为还经常发生在类似HashMap这样的容器里面，对HashMap的增删改查操作都会发生了大量的autoboxing的行为。为了避免这些autoboxing带来的效率问题，Android特地提供了一些如下的Map容器用来替代HashMap，不仅避免了autoboxing，还减少了内存占用：

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/android_perf_3_autoboxing_sparse.png)

# 3.SparseArray Family Ties
---

为了避免HashMap的autoboxing行为，Android系统提供了SparseBoolMap，SparseIntMap，SparseLongMap，LongSparseMap等容器。关于这些容器的基本原理请参考前面的ArrayMap的介绍，另外这些容器的使用场景也和ArrayMap一致，需要满足数量级在千以内，数据组织形式需要包含Map结构。

# 4.The price of ENUMs
---

Android官方强烈建议不要在Android程序里面使用到enum。

# 5.
---

Android系统的一大特色是多任务，用户可以随意在不同的app之间进行快速切换。为了确保你的应用在这种复杂的多任务环境中正常运行，我们需要了解下面的知识。

为了让background的应用能够迅速的切换到forground，每一个background的应用都会占用一定的内存。Android系统会根据当前的系统内存使用情况，决定回收部分background的应用内存。如果background的应用从暂停状态直接被恢复到forground，能够获得较快的恢复体验，如果background应用是从Kill的状态进行恢复，就会显得稍微有点慢。

Android系统提供了一些回调来通知应用的内存使用情况，通常来说，当所有的background应用都被kill掉的时候，forground应用会收到onLowMemory()的回调。在这种情况下，需要尽快释放当前应用的非必须内存资源，从而确保系统能够稳定继续运行。Android系统还提供了onTrimMemory()的回调，当系统内存达到某些条件的时候，所有正在运行的应用都会收到这个回调，同时在这个回调里面会传递以下的参数，代表不同的内存使用情况，下图介绍了各种不同的回调参数：

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/android_perf_3_memory_ontrimmemory.png)

onTrimMemory()的回调可以发生在Application，Activity，Fragment，Service，Content Provider。

从Android 4.4开始，ActivityManager提供了isLowRamDevice()的API，通常指的是Heap Size低于512M或者屏幕大小<=800*480的设备。

# 6.DO NOT LEAK VIEWS
---

通常来说，View会保持Activity的引用，Activity同时还和其他内部对象也有可能保持引用关系。当屏幕发生旋转的时候，activity很容易发生泄漏，这样的话，里面的view也会发生泄漏。Activity以及view的泄漏是非常严重的，为了避免出现泄漏，请特别留意以下的规则：

## 6.1避免使用异步回调
---

异步回调被执行的时间不确定，很有可能发生在activity已经被销毁之后，这不仅仅很容易引起crash，还很容易发生内存泄露。（也就是在Activity里面写子线程、定时器等）

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/android_perf_3_leak_asyncback.png)

## 6.2 避免使用Static对象
---

因为static的生命周期过长，使用不当很可能导致leak，在Android中应该尽量避免使用static对象。

## 6.4 避免把View添加到没有清除机制的容器里面
---

假如把view添加到WeakHashMap，如果没有执行清除操作，很可能会导致泄漏。
（在Activity里面将Fragment加入HashMap， 离开的时候要不要将HashMap 清空）


# 7.Location & Battery Drain

开启定位功能是一个相对来说比较耗电的操作，通常来说，我们会使用类似下面这样的代码来发出定位请求：

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/android_perf_3_location_request.png)

上面演示中有一个方法是setInterval()指的意思是每隔多长的时间获取一次位置更新，时间相隔越短，自然花费的电量就越多，但是时间相隔太长，又无法及时获取到更新的位置信息。其中存在的一个优化点是，我们可以通过判断返回的位置信息是否相同，从而决定设置下次的更新间隔是否增加一倍，通过这种方式可以减少电量的消耗。

通过GPS定位服务相比起使用网络进行定位更加的耗电，但是也相对更加精准一些

# 8.Double Layout Taxation
---

布局中的任何一个View一旦发生一些属性变化，都可能引起很大的连锁反应。例如某个button的大小突然增加一倍，有可能会导致兄弟视图的位置变化，也有可能导致父视图的大小发生改变。当大量的layout()操作被频繁调用执行的时候，就很可能引起丢帧的现象。

例如，在RelativeLayout中，我们通常会定义一些类似alignTop，alignBelow等等属性。
为了获得视图的准确位置，需要经过下面几个阶段。
首先子视图会触发计算自身位置的操作，然后RelativeLayout使用前面计算出来的位置信息做边界的调整。
经历过上面2个步骤，relativeLayout会立即触发第二次layout()的操作来确定所有子视图的最终位置与大小信息。

除了RelativeLayout会发生两次layout操作之外，LinearLayout也有可能触发两次layout操作，通常情况下LinearLayout只会发生一次layout操作，可是一旦调用了measureWithLargetChild()方法就会导致触发两次layout的操作。另外，通常来说，GridLayout会自动预处理子视图的关系来避免两次layout，可是如果GridLayout里面的某些子视图使用了weight等复杂的属性，还是会导致重复的layout操作。

如果只是少量的重复layout本身并不会引起严重的性能问题，但是如果它们发生在布局的根节点，或者是ListView里面的某个ListItem，这样就会引起比较严重的性能问题。

我们可以使用Systrace来跟踪特定的某段操作，如果发现了疑似丢帧的现象，可能就是因为重复layout引起的。通常我们无法避免重复layout，在这种情况下，我们应该尽量保持View Hierarchy的层级比较浅，这样即使发生重复layout，也不会因为布局的层级比较深而增大了重复layout的倍数。另外还有一点需要特别注意，在任何时候都请避免调用requestLayout()的方法，因为一旦调用了requestLayout，会导致该layout的所有父节点都发生重新layout的操作。


# 9.Network Performance 101
---

## 9.1减少移动网络被激活的时间与次数
通常来说，发生网络行为可以划分为如下图所示的三种类型，一个是用户主动触发的请求，另外被动接收服务器的返回数据，最后一个是数据上报，行为上报，位置更新等等自定义的后台操作。

我们绝对坚决肯定不应该使用Polling(轮询)的方式去执行网络请求，这样不仅仅会造成严重的电量消耗，还会浪费许多网络流量，例如：

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/android_perf_3_network_polling.png)

我们应该遵循下面的规则来处理数据同步的问题：

首先，我们应该使用回退机制来避免固定频繁的同步请求，例如，在发现返回数据相同的情况下，推迟下次的请求时间。
其次，我们还可以使用Batching(批处理)的方式来集中发出请求，避免频繁的间隔请求。
最后，我们还可以使用Prefetching(预取)的技术提前把一些数据拿到，避免后面频繁再次发起网络请求。

Google Play Service中提供了一个叫做GCMNetworkManager的类来帮助我们实现上面的那些功能，我们只需要调用对应的API，设置一些简单的参数，其余的工作就都交给Google来帮我们实现了。

## 9.2 压缩传输数据
---

FlatBuffers

# 10.
---

发起网络请求与接收返回数据都是比较耗电的，在网络硬件模块被激活之后，会继续保持几十秒的电量消耗，直到没有新的网络操作行为之后，才会进入休眠状态。前面一个段落介绍了使用Batching的技术来捆绑网络请求，从而达到减少网络请求的频率。那么如何实现Batching技术呢？通常来说，我们可以会把那些发出的网络请求，先暂存到一个PendingQueue里面，等到条件合适的时候再触发Queue里面的网络请求。

可是什么时候才算是条件合适了呢？最简单粗暴的，例如我们可以在Queue大小到10的时候触发任务，也可以是当手机开始充电，或者是手机连接到WiFi等情况下才触发队列中的任务。手动编写代码去实现这些功能会比较复杂繁琐，Google为了解决这个问题，为我们提供了GCMNetworkManager来帮助实现那些功能，仅仅只需要调用API，设置触发条件，然后就OK了。

# 11.Optimizing Network Request Frequencies
---


前面的段落已经提到了应该减少网络请求的频率，这是为了减少电量的消耗。我们可以使用Batching，Prefetching的技术来避免频繁的网络请求。Google提供了GCMNetworkManager来帮助开发者实现那些功能，通过提供的API，我们可以选择在接入WiFi，开始充电，等待移动网络被激活等条件下再次激活网络请求。

# 12.Effective Prefetching
---

假设我们有这样的一个场景，最开始网络请求了一张图片，隔了10秒需要请求另外一张图片，再隔6秒会请求第三张图片，如下图所示：

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/android_perf_3_prefetching.png)

类似上面的情况会频繁触发网络请求，但是如果我们能够预先请求后续可能会使用到网络资源，避免频繁的触发网络请求，这样就能够显著的减少电量的消耗。可是预先获取多少数据量是很值得考量的，因为如果预取数据量偏少，就起不到减少频繁请求的作用，可是如果预取数据过多，就会造成资源的浪费。

我们可以参考在WiFi，4G，3G等不同的网络下设计不同大小的预取数据量，也可以是按照图片数量或者操作时间来作为阀值。这需要我们需要根据特定的场景，不同的网络情况设计合适的方案。

