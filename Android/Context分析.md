---
title: Context分析

date: 2018-11-06

categories: 
   - Android

tags: 
   - Android 


description: ​
---


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/context.png)


可以看到Context有两个直接继承子类ContextImpl和ContextWrapper，并且ContextWrapper又通过mBase(指向了ContextImpl)，即ContextWrapper的核心工作都是交给ContextImpl)来完成，其二者之间是一个典型的代理模式


Application/Activity/Service通过attach()调用父类ContextWrapper的attachBaseContext(),从而设置父类成员变量mBase为ContextImpl对象,所以说真正执行Context使命的是ContextImpl，而ContextWrapper只是一个"傀儡"而已！