---
title: DiskLruCache分析

date: 2017-9-15

categories: 
   - Android
   - LruCache

tags: 
   - Android 
   - LruCache 

description: ​
---

<!-- TOC -->

- [DiskLruCache分析](#disklrucache分析)
        - [DiskLruCache介绍](#disklrucache介绍)
        - [初探](#初探)
        - [DiskLruCache的使用](#disklrucache的使用)
            - [打开一个缓存](#打开一个缓存)
            - [写入缓存](#写入缓存)
            - [读取缓存](#读取缓存)
            - [移除缓存](#移除缓存)
            - [其他API](#其他api)
            - [解读journal](#解读journal)

<!-- /TOC -->



# DiskLruCache分析

### DiskLruCache介绍
LruCache 是将数据缓存到内存中(LinkedHashMap)，具体实现请参考`LruCache分析.md` ， 而DiskLruCache是将数据缓存在Disk。


### 初探
相信所有人都知道，网易新闻中的数据都是从网络上获取的，包括了很多的新闻内容和新闻图片，如下图所示：


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/netease_new_pict.png)



但是不知道大家有没有发现，这些内容和图片在从网络上获取到之后都会存入到本地缓存中，因此即使手机在没有网络的情况下依然能够加载出以前浏览过的新闻。而使用的缓存技术不用多说，自然是DiskLruCache了，那么首先第一个问题，这些数据都被缓存在了手机的什么位置呢？

那么这里以网易新闻为例，它的客户端的包名是com.netease.newsreader.activity，它的数据缓存放在了/data/data/com.netease.newsreader.activity/cache下面，我们进入到这个目录中看一下，结果如下图所示：


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/netease_new_cache_dir.png)


其中bitmap_glide就是缓存图片的地方， 他是使用了Glide，  Glide里面用了DiskLruCache
我们进入bitmap_glide目录看看


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/netease_new_bitmap_glide_dir.png)


上面那些文件名很长的文件就是一张张缓存的图片，每个文件都对应着一张图片，而journal文件是DiskLruCache的一个日志文件，程序对每张图片的操作记录都存放在这个文件中，基本上看到journal这个文件就标志着该程序使用DiskLruCache技术了。


下面给出DiskLruCache的地址：
*  https://android.googlesource.com/platform/libcore/+/android-4.1.1_r1/luni/src/main/java/libcore/io/DiskLruCache.java


或者在Jake大神的Github上找到：

* https://github.com/JakeWharton/DiskLruCache


我们使用Jake大神的1.0.0版本进行分析。

下面先看一下DiskLruCache咋用呢。


### DiskLruCache的使用

#### 打开一个缓存

首先你要知道，DiskLruCache是不能new出实例的（稍候源码会分析到），如果我们要创建一个DiskLruCache的实例，则需要调用它的open()方法， 接口如下：

`DiskLruCache open(File directory, int appVersion, int valueCount, long maxSize)`

* directory 指定数据缓存的目录
* appVersion 当前APP的版本号， 当版本号改变时，缓存数据会被清除
* valueCount 同一个key可以对应多少文件， 一般都是1吧
* maxSize 指定最多可以缓存多少字节的数据

其实DiskLruCache并没有限制数据的缓存位置，可以自由地进行设定，但是通常情况下多数应用程序都会将缓存的位置选择为 /sdcard/Android/data/\<package name\>/cache 这个路径。选择在这个位置有两点好处：第一，这是存储外部存储上的，因此即使缓存再多的数据也不会对手机的内置存储空间有任何影响，只要外部存储空间足够就行。第二，这个路径被Android系统认定为应用程序的缓存路径，当程序被卸载的时候，这里的数据也会一起被清除掉，这样就不会出现删除程序之后手机上还有很多残留数据的问题。
但同时我们又需要考虑如果这个手机没有外部存储，或者外部存储正好被移除了的情况，因此比较优秀的程序都会专门写一个方法来获取缓存地址，如下所示：

```java
public File getDiskCacheDir(Context context, String uniqueName) {
  	String cachePath;
  	if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
  			|| !Environment.isExternalStorageRemovable()) {
  		    cachePath = context.getExternalCacheDir().getPath();
  	} else {
  		    cachePath = context.getCacheDir().getPath();
  	}
  	return new File(cachePath + File.separator + uniqueName);
}
```
`Environment.getExternalStorageState()`的作用是：
Returns the current state of the primary shared/external storage media. 返回主要外部存储的当前状态

为啥是主要外部存储？  是因为当插入SD卡的时候， SD卡属于次要外部存储， 手机自带的那个外部存储， 才是主要外部存储。 参考 “Android中的File存储.md”

`Environment.getExternalStorageState()`的返回值， 有如下几种状态（从源码copy过来的）
 *         {@link #MEDIA_UNKNOWN},  Unknown storage state, such as when a path isn't backed by known storage media. （未知的存储状态，  例如一个路径不被已知存储介质支持的时候）
 *         {@link #MEDIA_REMOVED}, Storage state if the media is not present. （当媒体不存在的时候的存储状态）
 *         {@link #MEDIA_UNMOUNTED}, {@link #MEDIA_CHECKING},  Storage state if the media is present but not mounted. （当媒体存在但是没有被挂载的时候的状态）
 *         {@link #MEDIA_NOFS}, {@link #MEDIA_MOUNTED}, Storage state if the media is present but is blank or is using an unsupported filesystem.
 *         {@link #MEDIA_MOUNTED_READ_ONLY},
 *         {@link #MEDIA_SHARED},
 *         {@link #MEDIA_BAD_REMOVAL},
 *         {@link #MEDIA_UNMOUNTABLE}，
 *         {@link #MEDIA_MOUNTED}
 *         {@link #MEDIA_MOUNTED_READ_ONLY}
 *
`Environment.isExternalStorageRemovable()` Returns whether the primary shared/external storage media is physically removable. 这个方法是判断**主要的**外部存储介质能否被物理移除。
返回值： true if the storage device can be removed (such as an SD card), or false if the storage device is built in and cannot be physically removed. 返回true表示这个存储设备能够被移除（比如SD卡）， 返回false表示这个存储设备是内置的， 不能被物理移除。

这个方法有个重载方法 `isExternalStorageRemovable(File path)`    Returns whether the shared/external storage media at the given path is physically removable. 这个方法判断在给定的path这个路径上的外部存储能否被物理移除。具体请参考“Android中的File存储.md”
tip：API 21以上的才能用

我们判断如果外部存储已经挂载了或者外部存储不可移除， 就认为外部存储存在并且正常， 就使用/sdcard/Android/data/\<package name\>/cache/uniqueName/ 目录作为缓存目录

否则， 就使用 /data/data/\<package name\>/cache/uniqueName/ 作为缓存目录

那么这个uniqueName又是什么呢？其实这就是为了对不同类型的数据进行区分而设定的一个唯一值，比如说bitmap、object等文件夹。

> getExternalCacheDir() getCacheDir() 傻傻分不清？ 为啥当程序卸载的时候， 这个Cache里面的内容会被删除？ 如果你有这些问题， 可以参考 “Android中的File存储.md”


接着是应用程序版本号，我们可以使用如下代码简单地获取到当前应用程序的版本号：
```java
public int getAppVersion(Context context) {
    try {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        return info.versionCode;
    } catch (NameNotFoundException e) {
        e.printStackTrace();
    }
    return 1;
}

```

需要注意的是，每当版本号改变，缓存路径下存储的所有数据都会被清除掉，因为DiskLruCache认为当应用程序有版本更新的时候，所有的数据都应该从网上重新获取。

后面两个参数就没什么需要解释的了，第三个参数传1，第四个参数通常传入10M的大小就够了，这个可以根据自身的情况进行调节。

因此，一个非常标准的open()方法就可以这样写：
```java
    DiskLruCache mDiskLruCache = null;
    try {
      	File cacheDir = getDiskCacheDir(context, "bitmap");
      	if (!cacheDir.exists()) {
      		  cacheDir.mkdirs();
      	}
      	mDiskLruCache = DiskLruCache.open(cacheDir, getAppVersion(context), 1, 10 * 1024 * 1024);
    } catch (IOException e) {
        e.printStackTrace();
    }
```

首先调用getDiskCacheDir()方法获取到缓存地址的路径，然后判断一下该路径是否存在，如果不存在就创建一下。接着调用DiskLruCache的open()方法来创建实例，并把四个参数传入即可。


#### 写入缓存
先来看写入，比如说现在有一张图片，地址是 http://img.my.csdn.net/uploads/201309/01/1378037235_7476.jpg
写入的操作是借助DiskLruCache.Editor这个类完成的。这个类也是不能new的，需要调用DiskLruCache的edit()方法来获取实例，接口如下所示：
```java
public Editor edit(String key) throws IOException
```

可以看到，edit()方法接收一个参数key，这个key将会成为缓存文件的文件名， 我们可以使用**MD5编码后**的值作为文件名， 因为编码后的字符串肯定是唯一的，并且只会包含0-F这样的字符，完全符合文件的命名规则。

Android中对字符串MD5加密方式如下：
```java
    @NonNull
    public static String md5(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
            byte[] bytes = md5.digest(string.getBytes());
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) {
                String temp = Integer.toHexString(b & 0xff);
                if (temp.length() == 1) {
                    temp = "0" + temp;
                }
                result.append(temp);
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }
```


有了DiskLruCache.Editor的实例之后，我们可以调用它的newOutputStream()方法来创建一个输出流，然后把它传入到downloadUrlToStream()中就能实现下载并写入缓存的功能了。注意newOutputStream()方法接收一个index参数，由于前面在设置valueCount的时候指定的是1，所以这里index传0就可以了, 也就是第一个文件。在写入操作执行完之后，我们还需要调用一下commit()方法进行提交才能使写入生效，调用abort()方法的话则表示放弃此次写入。

```java
  String url = "http://img.my.csdn.net/uploads/201309/01/1378037235_7476.jpg";
  String key = md5(url);
  DiskLruCache.Editor editor = mDiskLruCache.edit(key);
  if (editor != null) {
      OutputStream outputStream = editor.newOutputStream(0);

      // outputStream.write() ...

      editor.commit();
      editor.abort();
  }
  mDiskLruCache.flush();
```

现在的话缓存应该是已经成功写入了，我们进入到SD卡上的缓存目录里看一下


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/my_disklrucache_cache_dir.png)


可以看到，这里有一个文件名很长的文件，和一个journal文件，那个文件名很长的文件自然就是缓存的图片了，因为是使用了MD5编码来进行命名的。journal文件是DiskLruCache的一个日志文件，程序对每张图片的操作记录都存放在这个文件中。Glide也是用了DiskLruCache的， 所以目前使用了Glide的项目， 基本都能看到这个journal文件。

#### 读取缓存
缓存已经写入成功之后，接下来我们就该学习一下如何读取了。读取的方法要比写入简单一些，主要是借助DiskLruCache的get()方法实现的，接口如下所示：
```java
public synchronized Snapshot get(String key) throws IOException
```

get()方法要求传入一个key来获取到相应的缓存数据，而这个key毫无疑问就是进行MD5编码后的值了，因此读取缓存数据的代码就可以这样写：

```java
String key = md5(url);
DiskLruCache.Snapshot snapShot = mDiskLruCache.get(key);
if (snapShot != null) {
    InputStream is = snapShot.getInputStream(0);

    ... is.read ...
}
```

拿到DiskLruCache.Snapshot对象后， 只需要调用它的getInputStream()方法就可以得到缓存文件的输入流了。同样地，getInputStream()方法也需要传一个index参数，这里传入0就好。有了文件的输入流之后，就可以拿到缓存的内容了， 不管是图片、数据、对象等


#### 移除缓存
移除缓存主要是借助DiskLruCache的remove()方法实现的，接口如下所示：
```java
public synchronized boolean remove(String key) throws IOException
```

remove()方法中要求传入一个key，然后会删除这个key对应的文件，示例代码如下
```java
try {
	String key = md5(url);
	mDiskLruCache.remove(key);
} catch (IOException e) {
	e.printStackTrace();
}
```

用法虽然简单，但是你要知道，这个方法我们并不应该经常去调用它。因为你完全不需要担心缓存的数据过多从而占用SD卡太多空间的问题，DiskLruCache会根据我们在调用open()方法时设定的缓存最大值来自动删除多余的缓存。（也是Lru的算法原理么？？？我们后续分析看看）只有你确定某个key对应的缓存内容已经过期，需要从网络获取最新数据的时候才应该调用remove()方法来移除缓存。


#### 其他API

1. size()

这个方法会返回当前缓存路径下所有缓存数据的总字节数，以byte为单位，如果应用程序中需要在界面上显示当前缓存数据的总大小，就可以通过调用这个方法计算出来。比如网易新闻中就有这样一个功能，如下图所示：


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/netease_new_size_pict.png)


2. flush()

这个方法用于将内存中的操作记录同步到日志文件（也就是journal文件）当中。这个方法非常重要，因为DiskLruCache能够正常工作的前提就是要依赖于journal文件中的内容。前面在讲解写入缓存操作的时候我有调用过一次这个方法，但其实并不是每次写入缓存都要调用一次flush()方法的，频繁地调用并不会带来任何好处，只会额外增加同步journal文件的时间。比较标准的做法就是在Activity的onPause()方法中去调用一次flush()方法就可以了。

3. close()

这个方法用于将DiskLruCache关闭掉，是和open()方法对应的一个方法。关闭掉了之后就不能再调用DiskLruCache中任何操作缓存数据的方法，通常只应该在Activity的onDestroy()方法中去调用close()方法。


4. delete()

这个方法用于将所有的缓存数据全部删除，比如说网易新闻中的那个手动清理缓存功能，其实只需要调用一下DiskLruCache的delete()方法就可以实现了。



#### 解读journal
前面已经提到过，DiskLruCache能够正常工作的前提就是要依赖于journal文件中的内容。DiskLruCache通过日志来辅助保证磁盘缓存的有效性。在应用程序运行阶段，可以通过内存数据来保证缓存的有效性，但是一旦应用程序退出或者被意外杀死，下次再启动的时候就需要通过journal日志来重新构建磁盘缓存数据记录，保证上次的磁盘缓存是有效和可用的。因此，能够读懂journal文件对于我们理解DiskLruCache的工作原理有着非常重要的作用。那么journal文件中的内容到底是什么样的呢？我们来打开瞧一瞧吧，如下图所示：


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/journal.png)


* 其中第一行固定为libcore.io.DiskLruCache；
* 第二行是DiskLruCache的版本，目前固定为1；
* 第三行表示所属应用的版本号；
* 第四行valueCount表示一个缓存key可能对应多少个缓存文件（我们在open的时候填入了1） 它决定了后面一个CLEAN状态记录最多可以缓存size大小的数据；
* 第五行是空行。

此后记录的就是DiskLruCache针对磁盘缓存的操作记录了。其中几个状态表示如下：

* CLEAN 表示缓存处于一个稳定状态，即当前没有对该缓存数据进行写操作，在该状态下，对缓存文件的读写都是安全的。
* DIRTY 表示当前该key对应的缓存文件正在被修改，该状态下对缓存文件的读写都是不安全的，需要阻塞到对文件的修改完成，使该key对应的状态转变成CLEAN为止。
* REMOVE 表示该key对应的缓存文件被删除了，在缓存整理的过程中可能会出现多条这样的记录。
* READ 表示一个对key对应的缓存文件进行读取的操作记录。

每个操作记录状态后面都有一个字符串，表示缓存的key，其中CLEAN状态在后面还会有一个或者多个数字，这些数字表示对应缓存文件的大小。之所以允许一个key对应多个文件，主要是考虑到满足类似于一张图片可能存在多个大小和分辨率不同的缓存的功能。

第六行是以一个DIRTY前缀开始的，后面紧跟着缓存图片的key。通常我们看到DIRTY这个字样都不代表着什么好事情，意味着这是一条脏数据。没错，每当我们调用一次DiskLruCache的edit()方法时，都会向journal文件中写入一条DIRTY记录，表示我们正准备写入一条缓存数据，但不知结果如何。然后调用commit()方法表示写入缓存成功，这时会向journal中写入一条CLEAN记录，意味着这条“脏”数据被“洗干净了”，调用abort()方法表示写入缓存失败，这时会向journal中写入一条REMOVE记录。也就是说，每一行DIRTY的key，后面都应该有一行对应的CLEAN或者REMOVE的记录，否则这条数据就是“脏”的，会被自动删除掉。



如果你足够细心的话应该还会注意到，第七行的那条记录，除了CLEAN前缀和key之外，后面还有一个152313，这是什么意思呢？其实，DiskLruCache会在每一行CLEAN记录的最后加上该条缓存数据的大小，以字节为单位。152313也就是我们缓存的那张图片的字节数了，换算出来大概是148.74K，和缓存图片刚刚好一样大，如下图所示：



![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/journal_pict_size.png)



前面我们所学的size()方法可以获取到当前缓存路径下所有缓存数据的总字节数，其实它的工作原理就是把journal文件中所有CLEAN记录的字节数相加，求出的总合再把它返回而已。

除了DIRTY、CLEAN、REMOVE之外，还有一种前缀是READ的记录，这个就非常简单了，每当我们调用get()方法去读取一条缓存数据时，就会向journal文件中写入一条READ记录。因此，像网易新闻这种图片和数据量都非常大的程序，journal文件中就可能会有大量的READ记录。

那么你可能会担心了，如果我不停频繁操作的话，就会不断地向journal文件中写入数据，那这样journal文件岂不是会越来越大？这倒不必担心，DiskLruCache中使用了一个redundantOpCount变量来记录用户操作的次数，每执行一次写入、读取或移除缓存的操作，这个变量值都会加1，当变量值达到2000的时候就会触发重构journal的事件，这时会自动把journal中一些多余的、不必要的记录全部清除掉，保证journal文件的大小始终保持在一个合理的范围内。

源码放到下一篇分析吧。

这篇， 很多地方都参考了 https://blog.csdn.net/guolin_blog/article/details/28863651 大神写的非常棒， 我简单记录一下 \^_\^
