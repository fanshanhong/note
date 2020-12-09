---
title: versionName和versionCode

date: 2018-12-08

categories: 
   - Android

tags: 
   - Android 


description: ​
---


原文链接：https://blog.csdn.net/q123_xi/article/details/100700983



Android的版本可以在androidmainfest.xml中定义，主要有 `android:versionCode` 和 `android:versionName`

1. android:versionCode

* 主要是用于版本升级所用，是Integer类型的，第一个版本定义为1，以后递增，这样只要判断该值就能确定是否需要升级，该值不显示给用户。

* 不要将versionCode设置的太大，最好不要超过Integer的取值范围，一般大发布第一个应用到市场的时候，版本取值为1（versionCode=1），这也是目前典型和普遍的做法。

* 每次发布更新版本时可以递增versionCode的值，一个新版本的应用的versionCode不能小于之前旧版本的versionCode值，否则进行替换更新升级时会出错，系统提示无法安装。这也不是强制的，只是正式发布应用时，建议必须考虑的问题。

* (同一个APP低版本是不能直接覆盖安装手机中已存在的高版本应用（通过版本号(versionCode)来判断）。)


2. `android:versionName`
  
* 这个是我们常说明的版本号，这是一个值为String类型的属性，由三部分组成<major>.<minor>.<point>。VersionCode是方便程序开发者运行和维护Application而设置的一个有效的值。versionName是一个版本的描述，给用户看的，也是用户放在各个第3方平台上提供给使用者看的一个版本名，可以说是对VersionCode的解释和描述。

major是主版本号，一般在软件有重大升级时增长
minor是次版本号，一般在软件有新功能时增长
maintenance是维护版本，一般在软件有主要的问题修复后增长

————————————————
版权声明：本文为CSDN博主「q123_xi」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
原文链接：https://blog.csdn.net/q123_xi/article/details/100700983