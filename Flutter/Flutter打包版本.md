Flutter打包版本





![image-20200514104816436](/Users/shanhongfan/Library/Application Support/typora-user-images/image-20200514104816436.png)



Android 里面， 是用的flutterVersionCode 和 flutterVersionName



localProperties 是哪里？

```
def localProperties = new Properties()
def localPropertiesFile = rootProject.file('local.properties')
if (localPropertiesFile.exists()) {
    localPropertiesFile.withReader('UTF-8') { reader ->
        localProperties.load(reader)
    }
}
```

读取了

项目 目录下的 local.properties文件





local.properties

![image-20200514105031205](/Users/shanhongfan/Library/Application Support/typora-user-images/image-20200514105031205.png)



里面有 flutter.versionName 和 flutterVersionCode





也就是从这里来的。那这里这两个字段的值， 是在打包的时候生成的。  打包的时候， 会将pubspec.yaml中的version内容生成到local.properties里

![image-20200514105155307](/Users/shanhongfan/Library/Application Support/typora-user-images/image-20200514105155307.png)





明确一点：Android打包，版本号还是由build.gradle中指定的。



如果pubspec.yaml里没有配置version， 那local.properties里的versionName 和 versionCOde字段就没有，  打包的时候， 就会用build.gradle中默认的版本号。 或者你直接指定build.gradle中的版本号也可以。  

明确一点：Android打包，版本号还是由build.gradle中指定的。