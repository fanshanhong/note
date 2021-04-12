---
title: RecyclerView的回收复用缓存机制

date: 2020-03-08

categories: 
   - Android

tags: 
   - Android 


description: ​
---


# 概述

onCreateViewHolder()会在创建一个新view的时候调用，onBindViewHolder()会在已存在view，绑定数据的时候调用。


为了方便下面文章的理解，我们先了解几个方法的含义：


方法	对应Flag	含义	出现场景
isInvalid()	FLAG_INVALID	ViewHolder的数据是无效的	1.调用adapter的setAdapter() 2.adapter调用了notifyDataSetChanged(); 3.调用RecyclerView的invalidateItemDecorations()。
isRemoved()	FLAG_REMOVED	ViewHolder已经被移除，源数据被移除了部分数据	adapter调用了notifyItemRemoved()
isUpdated()	FLAG_UPDATE	item的ViewHolder数据信息过时了，需要重新绑定数据	1.上述isInvalid()的三种情况都会；2.调用adapter的onBindViewHolder()；3.调用了adapter的notifyItemChanged()。
isBound()	FLAG_BOUND	ViewHolder已经绑定了某个位置的item上，数据是有效的	调用了onBindViewHolder()方法



# Recycler的几级缓存

RecyclerView不需要像ListView那样if(contentView==null) {}else{}处理复用的逻辑，它回收复用是由Recycler来负责的

```java
public final class Recycler {
        final ArrayList<ViewHolder> mAttachedScrap = new ArrayList<>();
        ArrayList<ViewHolder> mChangedScrap = null;

        final ArrayList<ViewHolder> mCachedViews = new ArrayList<ViewHolder>();

        private final List<ViewHolder>
                mUnmodifiableAttachedScrap = Collections.unmodifiableList(mAttachedScrap);

        private int mRequestedCacheMax = DEFAULT_CACHE_SIZE;
        int mViewCacheMax = DEFAULT_CACHE_SIZE;

        RecycledViewPool mRecyclerPool;

        private ViewCacheExtension mViewCacheExtension;

        static final int DEFAULT_CACHE_SIZE = 2;
    }
```

Recycler中设置了四层缓存池，按照使用的优先级顺序依次是Scrap、CacheView、ViewCacheExtension、RecycledViewPool；其中Scrap包括mAttachedScrap和mChangedScrap，ViewCacheExtension是默认没有实现的，它RecyclerView留给开发者拓展的回收池。



## 缓存池一 (Scrap)


Scrap是RecyclerView最轻量的缓存，包括mAttachedScrap和mChangedScrap，它不参与列表滚动时的回收复用，作为重新布局时的临时缓存，它的作用是，缓存当界面重新布局前和界面重新布局后都出现的ViewHolder，这些ViewHolder是   **无效、未移除、未标记**   的。

在这些无效、未移除、未标记的ViewHolder之中，**mAttachedScrap负责保存其中没有改变的ViewHolder**；剩下的由mChangedScrap负责保存。mAttachedScrap和mChangedScrap也只是分工合作保存不同ViewHolder而已。

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/scrap.png)


在一个手机屏幕中，将itemB删除，并且调用notifyItemRemoved()方法，mAttachedScrap和mChangedScrap会分别存储这几个itemView，itemA没有任何的变化，存储到mAttachedScrap中，itemB虽然被移出了，也被存储到mAttachedScrap中(但是会被标记REMOVED，之后会移除)；itemC和itemD发生了变化，位置往上移动了，会被存储到mChangedScrap中。删除时，ABCD都会进入Scrap中；删除后，ACD都会回来，A没有任何变化，CD只是位置发生了变化，内容没有发生变化。


RecyclerView的局部刷新就是依赖Scrap的临时缓存，当我们通过notifyItemRemoved()，notifyItemChanged()通知item发生变化的时候，通过mAttachedScrap缓存没有发生变化的ViewHolder，其他的则由mChangedScrap缓存，添加itemView的时候快速从里面取出，完成局部刷新。

注意，如果我们使用notifyDataSetChanged()来通知RecyclerView刷新，屏幕上的itemView被标记为FLAG_INVALID并且未被移除，所以不会使用Scrap缓存，而是直接扔到CacheView或者RecycledViewPool池中。

注意：itemE并没有出现在屏幕中，它不属于Scrap管辖的范围，Scrap只会换在屏幕中已经加载出来的itemView的holder。


## 缓存池二 (CacheView)

CacheView用于RecyclerView列表位置产生变动时，对刚刚移出屏幕的view进行回收复用。根据position/id来精准匹配是不是原来的item，如果是则直接返回使用，不需要重新绑定数据；如果不是则去RecycledViewPool中找holder实例返回，并且重新绑定数据。


CacheView的最大容量为2，缓存一个新的ViewHolder时，如果超出了最大限制，那么会将CacheView缓存的第一个数据添加到RecycledViewPool后再移除掉，最后才会将新的ViewHolder添加进来。

我们在滑动RecyclerView的时候，Recycler会不断地缓存刚刚移出屏幕不可见的View到CacheView中，CacheView到达上限时又会不断替换CacheView中旧的ViewHolder，将它们扔到RecycledViewPool中。

如果一直朝一个方向滚动，CacheView并没有在效率上产生帮助，它只是把后面滑过的ViewHolder缓存起来，如果经常来回滑动，那么从CacheView根据对应位置的item直接复用，不需要重新绑定数据，将会得到很好的利用。

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/cache_view.png)

从图中可以看出，CacheView缓存刚刚变为不可见的view，如果当前View再次进入屏幕中的时候，进行精准匹配，这个itemView还是 之前的itemView，那么会从CacheView中获取ViewHolder进行复用。如果一直向某一个方向滑动，那么CacheView将会不断替换缓存里面的ViewHolder(CacheView最多只能存储2个)，将替换掉的ViewHolder先放到RecycledViewPool中。在CacheView中拿不到复用的ViewHolder，那么最后只能去RecycledViewPool中获取。



## 缓存池三 (ViewCacheExtension)


ViewCacheExtension是缓存拓展的帮助类，额外提供了一层缓存池给开发者。开发者视情况而定是否使用ViewCacheExtension增加一层缓存池，Recycler首先去scrap和CacheView中寻找复用view，如果没有就去ViewCacheExtension中寻找View，如果还是没有找到，那么最后去RecycledViewPool寻找复用的View。下面的讲解将会不涉及ViewCacheExtension的知识，大家知道即可。

注意：Recycler并没有将任何的view缓存到ViewCacheExtension中。所以在ViewCacheExtension中并没有缓存任何数据。


## 缓存池四 (RecycledViewPool)

在Scrap、CacheView、ViewCacheExtension都不愿意回收的时候，都会丢到RecycledViewPool中回收，所以RecycledViewPool是Recycler的终极回收站。

RecycledViewPool实际上是以SparseArray嵌套一个ArraryList的形式保存ViewHolder的，因为RecycledViewPool保存的ViewHolder是以itemType来区分的。这样方便不同的itemType保存不同的ViewHolder。它在回收的时候只是回收该viewType的ViewHolder对象，并没有保存原来的数据信息，在复用的时候需要重新走onBindViewHolder()方法重新绑定数据。

我们来看看RecycledViewPool的结构：

```java
    public static class RecycledViewPool {
        private static final int DEFAULT_MAX_SCRAP = 5;
        static class ScrapData {
            final ArrayList<ViewHolder> mScrapHeap = new ArrayList<>();
            int mMaxScrap = DEFAULT_MAX_SCRAP;
        }
        SparseArray<ScrapData> mScrap = new SparseArray<>();//容量5
    }
```

可以看出，RecycledViewPool中定义了SparseArray<ScrapData> mScrap，它是一个根据不同itemType来保存静态类ScrapData对象的SparseArray，ScrapData中包含了ArrayList<ViewHolder> mScrapHeap ，mScrapHeap是保存该itemType类型下ViewHolder的ArrayList。

缓存池定义了默认的缓存大小DEFAULT_MAX_SCRAP = 5，这个数量不是说整个缓存池只能缓存这多个ViewHolder，而是不同itemType的ViewHolder的list的缓存数量，即mScrap的数量，说明最多只有5组不同类型的mScrapHeap。mMaxScrap = DEFAULT_MAX_SCRAP说明每种不同类型的ViewHolder默认保存5个，当然mMaxScrap的值是可以设置的。这样RecycledViewPool就把不同ViewType的ViewHolder按类型分类缓存起来。

其实，Scrap缓存池不参与滚动的回收复用，CacheView缓存池被称为一级缓存，又因为ViewCacheExtension缓存池是给开发者定义的缓存池，一般不用到，所以RecycledViewPool缓存池被称为二级缓存，那么这样来说实际只有两层缓存。



# RecyclerVIew的回收复用原理

## 回收原理

在RecyclerView重新布局onLayoutChildren()或者填充布局fill()的时候，会先把必要的item与屏幕分离或者移除，并做好标记，保存到list中，在重新布局时，再将ViewHolde拿出来重新一个个放到新的位置上去。


1. 如果是RecyclerView不滚动情况下缓存(比如删除item)，重新布局时，把屏幕上的ViewHolder与屏幕分离下来，存放到Scrap中，即发生改变的ViewHolder缓存到mChangedScrap中，不发生改变的ViewHolder存放到mAttachedScrap中；剩下ViewHolder的会按照mCachedViews>RecycledViewPool的优先级缓存到mCachedViews或者RecycledViewPool中。

2. 如果是RecyclerVIew滚动情况下缓存(比如滑动列表)，在滑动时填充布局，先移除滑出屏幕的item，第一级缓存mCachedViews优先缓存这些ViewHolder，但是mCachedViews最大容量为2，当mCachedViews满了以后，会利用先进先出原则，把旧的ViewHolder存放到RecycledViewPool中后移除掉，腾出空间，再将新的ViewHolder添加到mCachedViews中，最后剩下的ViewHolder都会缓存到终极回收池RecycledViewPool中，它是根据itemType来缓存不同类型的ArrayList<ViewHolder>，最大容量为5。


## 复用原理

至此，已经有五个缓存RecyclerView的池子，mChangedScrap、mAttachedScrap、mCachedViews、mViewCacheExtension、mRecyclerPool，除了mViewCacheExtension是系统提供给开发者拓展的没有用到之外，还有四个池子是参与到复用流程中的。

当RecyclerView要拿一个复用的ViewHolder时，如果是预加载，则会先去mChangedScrap中精准查找(分别根据position和id)对应的ViewHolder，如果有就返回，如果没有就再去mAttachedScrap和mCachedViews中精确查找(先position后id)是不是原来的ViewHolder，如果是说明ViewHolder是刚刚被移除的，如果不是，则最终去mRecyclerPool找，如果itemType类型匹配对应的ViewHolder，那么返回实例，让它重新绑定数据，如果mRecyclerPool也没有返回ViewHolder才会调用createViewHolder()重新去创建一个。

这里需要注意：**在mChangedScrap、mAttachedScrap、mCachedViews中拿到的ViewHolder都是精准匹配，但是mChangedScrap的是发生了变化的，需要调用onBindViewHolder()重新绑定数据，mAttachedScrap和mCachedViews没有发生变化，是直接使用的，不需要重新绑定数据，而mRecyclerPool中的ViewHolder的内容信息已经被抹除，需要重新绑定数据。所以在RecyclerView来回滚动时，mCachedViews缓存池的使用效率最高。**


总的来说：RecyclerView着重在两个场景缓存和回收的优化，一是：在数据更新时，使用Scrap进行局部更新，尽可能复用原来viewHolder，减少绑定数据的工作；二是：在滑动的时候，重复利用原来的ViewHolder，尽可能减少重复创建ViewHolder和绑定数据的工作。最终思想就是，能不创建就不创建，能不重新绑定就不重新绑定，尽可能减少重复不必要的工作。


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/recycler_reuse.png)



