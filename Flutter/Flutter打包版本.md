---
title: Flutter打包版本

date: 2019-03-08

categories: 
   - Flutter

tags: 
   - Flutter 


description: ​
---




![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/flutter_version_code.png)



Android 里面， 版本是用项目的 app 模块的 build.gradle文件中的的 versionCode 和 versionName 来指定的。

这里使用了 flutterVersionCode 和 flutterVersionName。

flutterVersionCode 和 flutterVersionName的值来自于 localProperties


localProperties 是哪里来的呢？在build.gradle的顶部有：

```
def localProperties = new Properties()
def localPropertiesFile = rootProject.file('local.properties')
if (localPropertiesFile.exists()) {
    localPropertiesFile.withReader('UTF-8') { reader ->
        localProperties.load(reader)
    }
}
```

可以看到， 是从 rootProject（项目根目录） 下的  local.properties文件中读取了


local.properties文件如下


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/flutter_local_properties.png)


里面有 flutter.versionName 和 flutter.VersionCode

也就是从这里来的。

那这里这两个字段的值， 是在打包的时候生成的。  打包的时候， 会将pubspec.yaml中的version内容生成到local.properties里。然后再从 local.properties 文件中读取，读到flutterVersionCode 和 flutterVersionName的值，赋给build.gradle中的versionCode和versionName。

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/flutter_yaml_version_code.png)


**明确一点：Android打包，版本号最终还是由build.gradle中指定的。**


如果pubspec.yaml里没有配置version， 那local.properties里的versionName 和 versionCOde字段就没有，  打包的时候， 就会用build.gradle中默认的版本号。 或者你直接指定build.gradle中的版本号也可以。  
