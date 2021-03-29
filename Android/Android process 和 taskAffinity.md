---

title: Android process 和 taskAffinity

date: 2019-07-04

categories: 
   - Java

tags: 
   - Java


description: 
​
---


android:process

组件的process属性继承自application的 process属性
默认情况下，application的 process 属性是包名，也就是manifest中指定的package的值


activity的 process 属性，可以让activity运行在指定名字的进程中。

如果 process 以 冒号: 开头，就会创建一个新的，私有进程，让activity运行在里面

如果 process 以小写字母开头（必须带.），就是创建个公有进程。
公有进程意味着：如果其他应用（APP）可以共享这个进程。
也就是说，如果其他APP也给它的 activity 指定了相同名字的 process 属性，那这两个APP的activity将会运行在同一进程中


taskAffinity

0. Android 中 task 可以理解为一系列的Activity。其实就是我们所说的Activity栈。默认情况下，一个App 就是一个 Task

1. taskAffinity 可以理解为Android 中 Activity 栈的名字。

taskAffinity 是继承自 application的taskAffinity 属性。application 的 taskAffinity默认值是包名。
因此，默认情况下，APP的activity 都是维护在名为<package-name> 栈中。

2. android中的应用列表，列出的就是 task。



①非singleInstance且taskAffinity相同（缺省）的情况下，新建的activity实例是位于同一个task栈中。

②taskAffinity不同，因此新建的activity实例被放入了指定名称的task栈中。


③在taskAffinity指定了相同的task栈前提下，singleInstance仍然将新建的activity实例放入了新的task栈中，因此可以得出singleInstance的优先级大于taskAffinity。
（名字是相同的，但是id不同

）
④在既设置singleInstance，taskAffinity又不同的情况下，二者都是在新的task栈，理所当然新建的activity实例被放进了新的task栈中。

结论： 即使taskAffinity相同，当LaunchMode为singleInstance时，仍然会在新的task中创建activity实例。因此singleInstance的优先级大于taskAffinity。




如果 launch mode 是 single instance ， 那创建这个Activity时候，无论它的taskAffinity是什么，都会创建一个新的Task，而且这个task不允许有其他Activity
并且，启动这个 single instance activity时候，android 的应用列表不会显示这个只能存放一个activity的task，它被认为是启动它的task的一部分







 <activity android:name=".HelpActivity"
            android:launchMode="singleTask"
            android:taskAffinity="com.xx.xx.xxxxx" />

	    app 列表是展示两个task


	            <activity android:name=".HelpActivity"
            android:launchMode="singleInstance" />
	    不论是否指定 taskAffinity， app 列表是都是一个task

	    <activity android:name=".HelpActivity"
            android:process=":remote"  或者 com.xx.xxxxx 
            android:launchMode="singleInstance"></activity>
	    这样写也还是一个， 不受process的影像（不管是私有进程还是公有进程）

	     <activity android:name=".HelpActivity"
            android:process="com.xx.xxxxxx"
            android:taskAffinity="com.xx.xx"
            android:launchMode="singleTask"></activity>
	    应该2个

	     <activity android:name=".HelpActivity"
            android:process="com.xx.xxxxxx"
            android:taskAffinity="com.xx.xx"
            android:excludeFromRecents="true"
            android:launchMode="singleTask"></activity>
	    变1个

	excludeFromRecents 就是不在 app列表展示





如果一个 activity有taskAffinity，但是没有singleTask/FALG_ACTIVITY_NEW_TASK, taskAffinity无效








可以通过 adb shell dumpsys |grep ActivityRecord 来查看 TASKS的ActivityStacks
可以通过 adb shell dumpsys activity activities |grep packageName| grep Run 来查看某个packageName的ActivityStatcks
