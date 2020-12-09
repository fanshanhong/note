---
title: DiskLruCache分析

date: 2017-9-18

categories: 
   - Android
   - DiskLruCache

tags: 
   - Android 
   - DiskLruCache 

description: ​
---

<!-- TOC -->

- [DiskLruCache源码分析](#disklrucache源码分析)
        - [类说明](#类说明)
        - [先看类声明和成员变量](#先看类声明和成员变量)
        - [open方法](#open方法)
        - [Editor](#editor)

<!-- /TOC -->


# DiskLruCache源码分析

给出DiskLruCache的地址：
*  https://android.googlesource.com/platform/libcore/+/android-4.1.1_r1/luni/src/main/java/libcore/io/DiskLruCache.java


或者在Jake大神的Github上找到：

* https://github.com/JakeWharton/DiskLruCache


我们使用Jake大神的1.0.0版本进行分析。



### 类说明

先看看他那个类的说明写了些什么东东。主要说了这个类是干嘛的， 用法还有一些注意事项。。没啥， 镇楼吧。。

```java
/**
 * A cache that uses a bounded amount of space on a filesystem. Each cache
 * entry has a string key and a fixed number of values. Values are byte
 * sequences, accessible as streams or files. Each value must be between {@code
 * 0} and {@code Integer.MAX_VALUE} bytes in length.
 *
 * DiskLruCache 是使用文件系统中有限的空间实现了一个缓存(Cache)
 * 每一个缓存的entry都有一个String类型的key 和 固定数量的value。  这个固定数量就是在open的时候指定的第三个参数
 * value是能够以 流 或者 文件 形式 来访问的字节序列。 (其实就是文件..)
 * 每个entry的length 要在 0 - Integer.MAX_VALUE之间
 *
 *
 * <p>The cache stores its data in a directory on the filesystem. This
 * directory must be exclusive to the cache; the cache may delete or overwrite
 * files from its directory. It is an error for multiple processes to use the
 * same cache directory at the same time.
 *
 * 这个缓存， 将它的数据存储在文件系统中的一个目录中。
 * 该目录必须是缓存专用的。因为这个缓存有可能会删除或者覆盖这个目录下的文件。
 * 多个进程同时使用相同的缓存目录是一个错误。
 * 所以最好就是每个APP都在/data/data/package name/cache/下新建一个专用的缓存目录
 * 或者  是在/sdcard/Android/data/package name/cache/ 下新建一个专用的缓存目录
 * 这样就不会出现问题
 *
 * <p>This cache limits the number of bytes that it will store on the
 * filesystem. When the number of stored bytes exceeds the limit, the cache will
 * remove entries in the background until the limit is satisfied. The limit is
 * not strict: the cache may temporarily exceed it while waiting for files to be
 * deleted. The limit does not include filesystem overhead or the cache
 * journal so space-sensitive applications should set a conservative limit.
 * 这个缓存， 限制了将要存储在文件系统中的大小。（就是可以设定缓存的最大容量， 比如10M）
 * 当存储的容量超过了这个限制，cache将会在后台偷偷的移除entry ，直到满足要求为止（就跟LruCache原理差不多）。
 * 这个限制是不严格的， 巴拉巴拉。。。
 *
 * <p>Clients call {@link #edit} to create or update the values of an entry. An
 * entry may have only one editor at one time; if a value is not available to be
 * edited then {@link #edit} will return null.
 *
 * 用户调用edit()方法创建或者更新这个entry的value （其实value就是文件。。。）（调用edit()方法会返回一个editor， 然后editor.newOutputStream()拿到outputStream， 就使就可以去创建或者更新 文件）
 * 一个entry同一时间只能有一个editor。
 * 如果这个value（也就是文件） 不可用， 那么edit()方法会返回null  所以，使用的时候注意判空
 *
 * <ul>
 *     <li>When an entry is being <strong>created</strong> it is necessary to
 *         supply a full set of values; the empty value should be used as a
 *         placeholder if necessary.
 *     <li>When an entry is being <strong>created</strong>, it is not necessary
 *         to supply data for every value; values default to their previous
 *         value.
 * </ul>
 * Every {@link #edit} call must be matched by a call to {@link Editor#commit}
 * or {@link Editor#abort}. Committing is atomic: a read observes the full set
 * of values as they were before or after the commit, but never a mix of values.
 * edit()方法后面肯定要跟着一个commit()方法或者一个abort()方法。
 * commit()方法是原子性的
 *
 *
 * <p>Clients call {@link #get} to read a snapshot of an entry. The read will
 * observe the value at the time that {@link #get} was called. Updates and
 * removals after the call do not impact ongoing reads.
 * 用户调用 get()方法去读一个entry的快照（snapshot）。这个read‘？’ 将会观察在get()方法被调用的时候的value值
 * 后续的更新和删除不会影响正在进行的读取操作 （在说啥呢这是。。。）
 *
 * <p>This class is tolerant of some I/O errors. If files are missing from the
 * filesystem, the corresponding entries will be dropped from the cache. If
 * an error occurs while writing a cache value, the edit will fail silently.
 * Callers should handle other problems by catching {@code IOException} and
 * responding appropriately.
 */
```


下面分析源码


### 先看类声明和成员变量

```java
public final class DiskLruCache implements Closeable {
    static final String JOURNAL_FILE = "journal";
    static final String JOURNAL_FILE_TMP = "journal.tmp";
    static final String MAGIC = "libcore.io.DiskLruCache";
    static final String VERSION_1 = "1";
    private static final String CLEAN = "CLEAN";
    private static final String DIRTY = "DIRTY";
    private static final String REMOVE = "REMOVE";
    private static final String READ = "READ";
  }
```

final 类， 不允许继承，然后实现了`Closeable`接口。 `Closeable` 接口又 `extends AutoCloseable`

```java
/**
 * A {@code Closeable} is a source or destination of data that can be closed.
 * The close method is invoked to release resources that the object is
 * holding (such as open files).
 * 一个Closeable对象 代表一个source 或者 desination 可以被关闭。
 * 调用这个close方法去释放这个object持有的资源， 比如文件等。
 *
 * @since 1.5
 */
public interface Closeable extends AutoCloseable {
  /**
     * Closes this stream and releases any system resources associated
     * with it. If the stream is already closed then invoking this
     * method has no effect.
     * 关闭这个流， 并且释放跟它关联的所有的系统资源。
     * 如果这个流已经被关闭了， 再调用这个方法也没有影响。 这叫等幂性（我忘了从哪看到的了）
     */
    public void close() throws IOException;
}

```

`Closeable` 接口 `extends AutoCloseable`

```java
public interface AutoCloseable {
    void close() throws Exception;
}

```

从AutoCloseable的注释可知它的出现是为了更好的管理资源，准确说是资源的释放，当一个资源类实现了该接口close方法，在使用try-catch-resources语法创建的资源抛出异常后，JVM会自动调用close 方法进行资源释放，当没有抛出异常正常退出try-block时候也会调用close方法。像数据库链接类Connection,io类InputStream或OutputStream都直接或者间接实现了该接口。

可见， 实现这个接口就是为了让DiskLruCache能够关闭。

然后看他的几个成员变量
```java
static final String JOURNAL_FILE = "journal"; // 日志文件的名字
static final String JOURNAL_FILE_TMP = "journal.tmp"; // 临时的日志文件名字
static final String MAGIC = "libcore.io.DiskLruCache"; // 日志文件开头的第一行内容
static final String VERSION_1 = "1"; //  日志文件的版本号，这个值是恒为1的
// CLEAN DIRTY REMOVE READ 是日志文件下面记录的4种不同状态， 在上一篇已经分析了
private static final String CLEAN = "CLEAN";
private static final String DIRTY = "DIRTY";
private static final String REMOVE = "REMOVE";
private static final String READ = "READ";
```

接下来看到的几个方法：copyOfRange() deleteContents() closeQuietly() readFully() readAsciiLine()这几个都是从libcore   java.util.Arrays里面copy过来的。等会会用到。

下面还有几个成员变量
```java
    private final File directory; // 缓存的目录
    private final File journalFile; // 日志文件
    private final File journalFileTmp; // 临时的日志文件
    private final int appVersion; // APP版本号
    private final long maxSize; // 最大容量， 单位是byte
    private final int valueCount; // 同一个key可以对应多少个缓存文件
    private long size = 0; // 当前已经使用了的容量
    private Writer journalWriter;
    // 一个map， 日志文件到内存的映射
    private final LinkedHashMap<String, Entry> lruEntries
            = new LinkedHashMap<String, Entry>(0, 0.75f, true);

    private int redundantOpCount;
```
这个redundanOpCount的作用， 引用上一篇的介绍：
> 那么你可能会担心了，如果我不停频繁操作的话，就会不断地向journal文件中写入数据，那这样journal文件岂不是会越来越大？这倒不必担心，DiskLruCache中使用了一个redundantOpCount变量来记录用户操作的次数，每执行一次写入、读取或移除缓存的操作，这个变量值都会加1，当变量值达到2000的时候就会触发重构journal的事件，这时会自动把journal中一些多余的、不必要的记录全部清除掉，保证journal文件的大小始终保持在一个合理的范围内。


### open方法

先看下构造
```java
// 构造, 私有的
// 在构造中对几个成员变量赋值
// 然后拿到日志文件和临时日志文件的引用
private DiskLruCache(File directory, int appVersion, int valueCount, long maxSize) {
    // 缓存目录赋值
    this.directory = directory;
    // APP版本号赋值
    this.appVersion = appVersion;
    // 拿到日志文件
    this.journalFile = new File(directory, JOURNAL_FILE);
    // 拿到日志的临时文件
    this.journalFileTmp = new File(directory, JOURNAL_FILE_TMP);
    // 一个key最多对应几个缓存的文件
    this.valueCount = valueCount;
    // 最大容量赋值
    this.maxSize = maxSize;
}
```

构造是`private`的， 也就是为啥不能new的原因。构造是在open()方法中他自己调用的， 我们看open方法

```java

// 在 directory 中打开缓存, 如果不存在的话, 在那创建一个
public static DiskLruCache open(File directory, int appVersion, int valueCount, long maxSize)
        throws IOException {
    // 异常判断
    if (maxSize <= 0) {
        throw new IllegalArgumentException("maxSize <= 0");
    }
    if (valueCount <= 0) {
        throw new IllegalArgumentException("valueCount <= 0");
    }

    // prefer to pick up where we left off
    // 先创建一个DiskLruCache
    DiskLruCache cache = new DiskLruCache(directory, appVersion, valueCount, maxSize);
    // 如果日志文件已经存在了, 那么就代表之前已经有建立好的缓存, 我们就把之前建立好的缓存加载的内存中, 继续使用
    // 这就是上面注释的意思 prefer to pick up where we left off  从哪里离开的, 我们再捡起来...
    if (cache.journalFile.exists()) { // 日志文件以及存在
        try {

            cache.readJournal();
            cache.processJournal();
            cache.journalWriter = new BufferedWriter(new FileWriter(cache.journalFile, true));
            return cache;
        } catch (IOException journalIsCorrupt) {
            System.out.println("DiskLruCache " + directory + " is corrupt: "
                    + journalIsCorrupt.getMessage() + ", removing");
            // 如果日志文件存在, 但是在读取过程中异常, 这里把资源全部关掉后, 把缓存目录全部删掉了
            cache.delete();
        }
    }

    // 如果日志文件不存在, 就创建
    // create a new empty cache
    directory.mkdirs(); // 先创建缓存目录
    // 然后构建一个新的DiskLruCache
    cache = new DiskLruCache(directory, appVersion, valueCount, maxSize);
    // 编写新的日志文件
    cache.rebuildJournal();
    return cache;
}
```
在open()方法中, 先判断日志文件是否已经存在了..如果日志文件已经存在了, 那么就代表之前已经有建立好的缓存, 我们就把之前建立好的缓存加载的内存中, 继续使用.

第一次使用的时候,  日志文件肯定不存在的. 所以我们先考虑日志文件不存在的情况.  当日志文件不存在, 那么
1. 就创建缓存目录.  这个directory是在open的时候传入的缓存目录的路径. 一般可能是/data/data/package name/cache/xxx/ 或者 /sdcard/Android/data/package name/xxx/   这些目录默认不存在的, 要先mkdirs()一下.
2. 然后构建新的DiskLruCache对象, 也就是调用DiskLruCache的私有构造, 创建一个新的cache对象
3. 并编写新的日志文件.

我们看一下rebuildJournal()方法是如何实现的

```java
/**
    * Creates a new journal that omits redundant information. This replaces the
    * current journal if it exists.
    * 创建一个新的, 并且省略了冗余信息的日志文件.  创建的是journal.tmp文件
    * 这个日志文件会替代当前的日志文件
    */
   private synchronized void rebuildJournal() throws IOException {
       // journalWriter 这个是日志文件的Writer
       if (journalWriter != null) {
           journalWriter.close();
       }

       // 先把内容写入临时日志文件
       Writer writer = new BufferedWriter(new FileWriter(journalFileTmp));
       // 第一行
       writer.write(MAGIC);
       writer.write("\n");
       // 第二行
       writer.write(VERSION_1);
       writer.write("\n");
       // 第三行APP版本号
       writer.write(Integer.toString(appVersion));
       writer.write("\n");
       writer.write(Integer.toString(valueCount));
       writer.write("\n");
       // 第五行, 空行
       writer.write("\n");

       // 然后遍历内存, 把内存中维护的映射关系写入日志文件
       for (Entry entry : lruEntries.values()) {
           if (entry.currentEditor != null) {
               writer.write(DIRTY + ' ' + entry.key + '\n');
           } else {
               writer.write(CLEAN + ' ' + entry.key + entry.getLengths() + '\n');
           }
       }

       writer.close();
       // 最后重命名了一下
       journalFileTmp.renameTo(journalFile);
       // 然后这个writer又是写日志文件的了.   FileWriter第二个参数是true, 追加写(append)
       journalWriter = new BufferedWriter(new FileWriter(journalFile, true));
   }
```

可以看到，就是把日志文件的头部写入了`journalFileTmp`这个文件中。   `journalFileTmp` 这个文件是临时日志文件"journal.tmp"。
然后遍历内存中的map，把DIRTY 和 CLEAN 写入了这个临时日志文件(类注释中说省略了冗余信息应该说的是省略了REMOVE 和 READ信息吧...主要原因是用于日志文件太大， 然后需要重构的时候的。)
第一次使用, 内存(lruEntries)里面也没有数据啊...
写好之后，关闭资源， 并且把临时日志文件重命名成了日志文件"journal"
这样， 日志文件就rebuild好了。



### Editor

open()完了之后， 我们就能拿到DiskLruCache对象了。
现在我们要向缓存中写入数据. 要借助这个Editor东东....是不是跟SharedPreference一样啊..
需要在DiskLruCache对象上调用` edit(String key) ` 这个方法， 传入参数key，  来拿到Editor。  拿到这个Editor就表示: 我们当前要对这个key对应的缓存文件进行编辑啦...比如向缓存文件写东西。
我们看一下这个edit()方法的实现:
```java
/**
 * Returns an editor for the entry named {@code key}, or null if it cannot
 * currently be edited.
 */
public synchronized Editor edit(String key) throws IOException {
    // 检查Cache是否已经关闭了
    checkNotClosed();
    // key的合法性判断, 因为key是要作为文件名的, 而文件名有一些要求的....里面不能\n  \r之类的额, 所以我们用MD5加密后的字符串作为文件名
    validateKey(key);
    Entry entry = lruEntries.get(key);
    // 内存中还没有,  构建一个entry, put一下
    if (entry == null) {
        entry = new Entry(key);
        lruEntries.put(key, entry);
    } else if (entry.currentEditor != null) { // 内存中, 并且currentEditor != null, 代表啥????  正在操作这个entry呢...所以返回null
        return null;
    }

    Editor editor = new Editor(entry);
    entry.currentEditor = editor;

    // flush the journal before creating files to prevent file leaks
    journalWriter.write(DIRTY + ' ' + key + '\n'); // 当调用edit(key)的时候, 就代表要对这个key编辑了, 所以先生成一条DIRTY数据
    journalWriter.flush();
    return editor;
}
```

按顺序一步一步来吧.
1. 首先调用`checkNotClosed();`来检查Cache是否已经关闭了。 checkNotClosed()方法中使用 journalWriter == null 代表Cache被关闭了。 因为当调用close()方法关闭Cache的时候， 会设置`journalWriter = null;` 。  所以当 journalWriter == null 的时候， 可以认定是Cache已经被关掉了。
```java
// 检查是否关闭了. 如果已经关闭了,  抛出异常
private void checkNotClosed() {
    if (journalWriter == null) {
        throw new IllegalStateException("cache is closed");
    }
}

```
2. 对key的合法性进行校验。因为这个key是要作为文件名的， 而文件名有要求，比如不能包含空格， 不能包含 \n \r之类的。所以我们用MD5加密后的字符串作为文件名。
3. key校验没问题之后，  就从内存中获取一下。 如果没有（`if (entry == null)`） 就创建一个新的Entry， 然后在内存中缓存一下。 如果有， 并且 `currentEditor != null` 就代表正在操作这个entry呢...所以返回null。返回null了就不让再操作这个key对应的文件了。
4. 如果没有异常， 就使用Entry来构建一个Editor返回。 这里， editor对象里面有个引用， 指示当前正在操作哪个entry。entry中也有个变量currentEditor指示当前的editor是谁。
5. 当调用edit(key)的时候, 就代表要对这个key编辑了, 所以先生成一条DIRTY数据， 写入日志文件中。后续操作， 如果成功， 那么就调用commit()方法写入CLEAN数据，  如果失败， 就调用abort()方法写入REMOVE数据。
6. 这样， 就拿到Editor了。后续就可以通过Editor来操作key对应的缓存文件了。


这里我们有必要来看一下这个Entry到底是啥东东。。

Entry，  这个Entry主要封装了缓存的文件在内存中的表示
```java
// 看看他这个entry是怎么封装的
   private final class Entry {
       // key
       private final String key;

       /** Lengths of this entry's files. */
       // key 所对应的多个缓存文件的大小, 当然也可以是1个文件, 我们一般就一个key对应一个缓存文件
       private final long[] lengths;

       /** True if this entry has ever been published */
       // true表示这个entry被建立好了, 所以可读
       private boolean readable;

       /** The ongoing edit or null if this entry is not being edited. */
       // 正在进行中的编辑器, 或者  如果这个entry并不是正在被编辑, 那个这个值为null
       private Editor currentEditor;

       // 私有的构造
       // 传入key
       private Entry(String key) {
           this.key = key;
           this.lengths = new long[valueCount];
       }

       // 获取同一个key对应的每个缓存文件的大小, 返回字符串, 空格分隔
       public String getLengths() throws IOException {
           StringBuilder result = new StringBuilder();
           for (long size : lengths) {
               result.append(' ').append(size);
           }
           return result.toString();
       }

       /**
        * Set lengths using decimal numbers like "10123".
        * 设置同一个key对应的每个缓存文件的length
        */
       private void setLengths(String[] strings) throws IOException {
           if (strings.length != valueCount) { // 这两个必须相等的
               throw invalidLengths(strings);
           }

           try {
               //挨个赋值
               for (int i = 0; i < strings.length; i++) {
                   lengths[i] = Long.parseLong(strings[i]);
               }
           } catch (NumberFormatException e) {
               throw invalidLengths(strings);
           }
       }

       private IOException invalidLengths(String[] strings) throws IOException {
           throw new IOException("unexpected journal line: " + Arrays.toString(strings));
       }

       public File getCleanFile(int i) {
           return new File(directory, key + "." + i);
       }

       public File getDirtyFile(int i) {
           return new File(directory, key + "." + i + ".tmp");
       }
   }
```

这个Entry主要封装了缓存的文件在内存中的表示。他一共有4个成员变量
* key
* long[] lengths  我们说过， 一个key可以对应多个缓存文件的。这个数组就是 key 所对应的多个缓存文件的大小, 当然也可以是1个文件, 我们一般就一个key对应一个缓存文件。因为文件可能很大， 比较占内存， 所以他这里并没有缓存文件， 只是把文件的大小维护了一下。
* boolean readable   // true表示这个entry被建立好了, 所以可读。 当状态是CLEAN的时候， 才可读的。
* Editor currentEditor // 正在进行中的编辑器, 或者  如果这个entry并不是正在被编辑, 那个这个值为null。 因此， 当数据状态是DIRTY的时候， currentEditor 不为null， 当数据状态是CLEAN的时候， 为null

私有构造， 传入key， 然后创建了一个空的数组。

我们看一下他的getLengths()和setLengths()方法。
先看setLengths()方法。 参数他用了String数组类型，  后续需要用Long.parseLong 转一下。 valueCount 这个变量是在open时候传入的第三个参数， 用于指定一个key对应几个缓存文件。 那么这个valueCount 一定要和 传入的String数组的length一样。 然后我们挨着给数组 long[] lengths 赋值就好啦。。。是不是很简单。。

再看一下getLengths()  就遍历了一下数组 long[] lengths， 然后组成一个字符串返回， 用空格分隔每个大小。

其实我并不知道理解这个有啥用木有， 但是我读源码的时候， 这个大概看明白了就记录一下。分享一下下。。。

好了， 后面这两个方法有用的。
```java
        public File getCleanFile(int i) {
            return new File(directory, key + "." + i);
        }

        public File getDirtyFile(int i) {
            return new File(directory, key + "." + i + ".tmp");
        }
  ```
因为一个key可以对应多个缓存文件的， 所以这里要传入i。我们先用一个key对应一个文件来理解。
getDirtyFile() 是获取DIRTY数据对应的那个文件。就是在缓存目录下面， 创建了一个  key.0.tmp文件
getCleanFile() 是获取CLEAN数据对应的那个文件。就是在缓存目录下面， 创建了一个  key.0文件
为啥是0？ 在上一篇使用的时候， 我们都是传入0。 其实i是几都没问题， 只要写入文件和读取文件写相同的i就好啦。比如写入的时候是key.88  读取的时候去读取key.88就好啦。 因为他这个支持多个嘛， 所以我们一般下标从0开始呗。 比如支持3个， 那就 0 1 2呗。。
前面说到， 我们拿到使用`edit(key)`方法拿到Editor后， 准备去操作的文件， 其实就是这个DIRTY对应的key.0.tmp文件, 后面能看到。
那么CLEAN对应的key.0文件啥时候用呢？ 获取缓存的时候用的。
因为DIRTY代表的是正在操作的文件。
而CLEAN代表的是稳定的文件。那么当你缓存好了， 这个文件就是稳定的了， 就是CLEAN。当你获取缓存的时候， 其实就是获取之前缓存好的CLEAN文件咯。
这里不明白没关系， 后面用的时候一看就懂了。。。


### 写入缓存

思路回来。 当我们拿到Editor之后， 就可以写入缓存了。

```java
        public OutputStream newOutputStream(int index) throws IOException {
            synchronized (DiskLruCache.this) {
                // 异常
                if (entry.currentEditor != this) {
                    throw new IllegalStateException();
                }
                return new FaultHidingOutputStream(new FileOutputStream(entry.getDirtyFile(index)));
            }
        }
```
我们使用editor的newOutputStream()方法， 传入index， 获取到那个DIRTY文件， 然后用OutputStream包装了一下返回来了。
那么我们后续对这个OuputStream.write()其实都是写在了那个DIRTY文件里。
最外面这个 FaultHidingOutputStream是干嘛的? 他主要就是记录一下在write() flush() 这些操作中有没有出问题。一看就懂， 异常的时候， 设置了一下标志位hasErrors。如果有问题， 后续在commit的时候要处理一下。

```java

private class FaultHidingOutputStream extends FilterOutputStream {
           private FaultHidingOutputStream(OutputStream out) {
               super(out);
           }

           @Override public void write(int oneByte) {
               try {
                   out.write(oneByte);
               } catch (IOException e) {
                   hasErrors = true;
               }
           }

           @Override public void write(byte[] buffer, int offset, int length) {
               try {
                   out.write(buffer, offset, length);
               } catch (IOException e) {
                   hasErrors = true;
               }
           }

           @Override public void close() {
               try {
                   out.close();
               } catch (IOException e) {
                   hasErrors = true;
               }
           }

           @Override public void flush() {
               try {
                   out.flush();
               } catch (IOException e) {
                   hasErrors = true;
               }
           }
       }
```

现在我们拿到OutputStream对象了， 然后可以写写写了。
```java
...
outputStream.write()
...
```
在写入操作执行完之后，我们还需要调用一下commit()方法进行提交才能使写入生效，调用abort()方法的话则表示放弃此次写入。
我们一起来看看commit()方法和 abort()都做了什么

```java
public void commit() throws IOException {
           if (hasErrors) {
               completeEdit(this, false);
               remove(entry.key); // the previous entry is stale
           } else {
               completeEdit(this, true);
           }
       }
```

先判断在操作工程中是否有error(这个hasError就是FaultHidingOutputStream类在调用write()/flush()等操作的时候, 如果出错就会设置的.)， 如果没有error， 就`completeEdit(this, true);`  如果有error， 就`completeEdit(this, false);` ， 并且把entry从内存中移除了。

```java
        public void abort() throws IOException {
            completeEdit(this, false);
        }
```

abort()直接就`completeEdit(this, false);`
那我们就看看这个completeEdit()方法

```java
private synchronized void completeEdit(Editor editor, boolean success) throws IOException {
       Entry entry = editor.entry;
       if (entry.currentEditor != editor) {
           throw new IllegalStateException();
       }

       // if this edit is creating the entry for the first time, every index must have a value
       if (success && !entry.readable) {
           for (int i = 0; i < valueCount; i++) {
               if (!entry.getDirtyFile(i).exists()) {
                   editor.abort();
                   throw new IllegalStateException("edit didn't create file " + i);
               }
           }
       }

       // 上面都是异常判断

       for (int i = 0; i < valueCount; i++) {
           File dirty = entry.getDirtyFile(i);
           if (success) {
               if (dirty.exists()) {
                   File clean = entry.getCleanFile(i);
                   dirty.renameTo(clean);
                   long oldLength = entry.lengths[i];
                   long newLength = clean.length();
                   entry.lengths[i] = newLength;
                   size = size - oldLength + newLength;
               }
           } else {
               deleteIfExists(dirty);
           }
       }

       redundantOpCount++;
       entry.currentEditor = null;
       if (entry.readable | success) {
           entry.readable = true;
           journalWriter.write(CLEAN + ' ' + entry.key + entry.getLengths() + '\n');
       } else {
           lruEntries.remove(entry.key);
           journalWriter.write(REMOVE + ' ' + entry.key + '\n');
       }

       if (size > maxSize || journalRebuildRequired()) {
           executorService.submit(cleanupCallable);
       }
   }
```

* 最开始都是异常判断。
* 然后遍历每一个DIRTY文件， 我们暂定就一个， 没啥好遍历的。
* 如果success是true， 那么就把DIRTY文件重命名成CLEAN文件。然后调整size。改成CLEAN文件之后， 就意味着这个缓存文件已经稳定啦， 目前不处于编辑状态啦。记得把currentEditor置空， 这样才能状态保持一致。然后，  写一条CLEAN数据到日志文件中。
* 如果出错了， 或者是调用abort()放弃写入， success == false， 那就把所有的DIRTY文件都删除了。也要把currentEditor置空。然后，  写一条REMOVE数据到日志文件中。并且从内存中移除了这个key
* 还做了 redundantOpCount++; 这个后面用于重构日志文件的。
* 最后判断  当前容量size 是否已经超过最大容量 maxSize  或者  日志文件太大了， 需要重构？  然后submit了一个callable。（Future 和 Callable的内容， 参考AsyncTask分析。）

```java
private final Callable<Void> cleanupCallable = new Callable<Void>() {
        @Override public Void call() throws Exception {
            synchronized (DiskLruCache.this) {
                if (journalWriter == null) {
                    return null; // closed
                }
                trimToSize();
                if (journalRebuildRequired()) {
                    rebuildJournal();
                    redundantOpCount = 0;
                }
            }
            return null;
        }
    };
```

1. callable中， 先异常判断，
2. 然后调用trimToSize()调整了当前缓存的size， 使缓存大小达到一个合理的范围。注释应该一看就懂啦。。。跟LruCache的原理一样， 都是把队头最老的淘汰了。一直循环， 直到满size < maxSize 的条件为止。
```java
private void trimToSize() throws IOException {
    // 一直循环, 直到满足条件为止
    while (size > maxSize) {
        // 拿到lruEntries中最老的, 也就是队列头部的entry, 删掉.
        Map.Entry<String, Entry> toEvict = lruEntries.entrySet().iterator().next();//lruEntries.eldest();
        remove(toEvict.getKey());
    }
}
```
3. 然后判断`if (journalRebuildRequired())` 日志文件是否需要rebuild. 可以看到redundantOpCount >= 2000 是触发重构的条件
```java
private boolean journalRebuildRequired() {
        final int REDUNDANT_OP_COMPACT_THRESHOLD = 2000;
        return redundantOpCount >= REDUNDANT_OP_COMPACT_THRESHOLD
                && redundantOpCount >= lruEntries.size();
    }
```
4. 如果需要重构， 又去调用了`rebuildJournal()`进行重构。无非就是重新写入文件头， 然后遍历内存中的map，把DIRTY 和 CLEAN 写入临时日志文件。最后rename一下。  我的问题是：如果journalFileTmp 和  journalFile 都存在的话，  renameTo会失败啊！！！  测试了一下：Windows下会失败， Android下，会覆盖。  (跟文件系统有关系？？)
5.

测试代码
```java
         File file1 = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "a.txt");
          File file2 = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "b.txt");

          FileOutputStream fileOutputStream1 = new FileOutputStream(file1);
          FileOutputStream fileOutputStream2 = new FileOutputStream(file2);
          fileOutputStream1.write("aaa".getBytes());
          fileOutputStream2.write("bbb".getBytes());
          fileOutputStream1.close();
          fileOutputStream2.close();

          Log.i("zxx", "file1 MD5:" + md5(file1));
          Log.i("zxx", "file2 MD5:" + md5(file2));

          boolean b = file1.renameTo(file2);
          Log.i("zxx", "renameTo:" + b);

          Log.i("zxx", "file1 MD5:" + md5(file2));
```
Android下结果：
```
09-18 15:52:12.507 3171-3171/? I/zxx: file1 MD5:47bce5c74f589f4867dbd57e9ca9f808
                                      file2 MD5:08f8e0260c64418510cefb2b06eee5cd
                                      renameTo:true
                                      file1 MD5:47bce5c74f589f4867dbd57e9ca9f808
```
### 读取缓存

写入缓存看完了, 我们再看看读取缓存, 读取的方法要比写入简单一些，主要是借助DiskLruCache的get()方法实现的，该方法源码如下所示：

```java
    /**
     * Returns a snapshot of the entry named {@code key}, or null if it doesn't
     * exist is not currently readable. If a value is returned, it is moved to
     * the head of the LRU queue.
     */
    public synchronized Snapshot get(String key) throws IOException {
        // 检查日志文件是否关闭了. 如果已经关闭了,  抛出异常
        checkNotClosed();
        // 检测传入的参数key是否合法
        validateKey(key);
        
        // 根据key, 在内存中获取Entry
        Entry entry = lruEntries.get(key);
        
        // 内存中没有
        if (entry == null) {
            return null;
        }

        // 不可读
        if (!entry.readable) {
            return null;
        }

        /*
         * Open all streams eagerly to guarantee that we see a single published
         * snapshot. If we opened streams lazily then the streams could come
         * from different edits.
         */
        // 我们提前打开所有的流, 用来保障我们只能看到单一的snapshot对象.
        // 如果我们延迟打开流, 则可能导致来自于不同的edit的问题
        InputStream[] ins = new InputStream[valueCount];
        try {
            for (int i = 0; i < valueCount; i++) {
                ins[i] = new FileInputStream(entry.getCleanFile(i));
            }
        } catch (FileNotFoundException e) {
            // a file must have been deleted manually!
            return null;
        }

        // 访问次数统计
        redundantOpCount++;
        
        // 写入日志文件
        journalWriter.append(READ + ' ' + key + '\n');
        
        // 判断日志文件是否需要重构
        if (journalRebuildRequired()) {
            executorService.submit(cleanupCallable);
        }

        // 返回一个Snapshot对象
        return new Snapshot(ins);
    }
```

get()方法要求传入一个key来获取到相应的缓存数据，而这个key毫无疑问就是进行MD5编码后的值了。首先调用了`checkNotClosed()`用来检查日志文件是否已经关闭了。然后使用方法`validateKey()`判断了传入的参数key是否合法。

```java
    // 检查日志文件是否关闭了. 如果已经关闭了,  抛出异常
    private void checkNotClosed() {
        // 这里以 journalWriter == null 作为日志文件关闭的条件。因为在日志文件关闭的时候， 会设置 journalWriter = null
        if (journalWriter == null) {
            throw new IllegalStateException("cache is closed");
        }
    }
```

```java
    // 我们说过， 这个key就是文件名， 那么文件名中不能包含 空格、换行、制表符 这些特殊字符。
    private void validateKey(String key) {
        if (key.contains(" ") || key.contains("\n") || key.contains("\r")) {
            throw new IllegalArgumentException(
                    "keys must not contain spaces or newlines: \"" + key + "\"");
        }
    }
```

然后我们提前打开Entry中对应的CLEAN文件， 获取到文件对应的输入流（通过 `entry.getCleanFile(i)` 获取文件， 前面说过，`getCleanFile()` 是获取CLEAN数据对应的文件。就是在缓存目录下面， 创建的  key.0文件（如果一个key最多对应1个缓存的文件， 那么就是key.0文件） ）。之后用这个输入流的数组构建了一个Snapshot对象返回。在返回之前， 还做了访问次数统计， 写入日志文件（因为是获取缓存， 所以向日志文件中写入了READ记录）， 以及在必要的时候进行日志文件重构这些操作。

那么这个Snapshot 是个什么东东， 我们来看一下：

```java
    /**
     * A snapshot of the values for an entry.
     */
    public static final class Snapshot implements Closeable {
        private final InputStream[] ins;

        private Snapshot(InputStream[] ins) {
            this.ins = ins;
        }

        /**
         * Returns the unbuffered stream with the value for {@code index}.
         */
        public InputStream getInputStream(int index) {
            return ins[index];
        }

        /**
         * Returns the string value for {@code index}.
         */
        public String getString(int index) throws IOException {
            return inputStreamToString(getInputStream(index));
        }

        @Override public void close() {
            for (InputStream in : ins) {
                /*IoUtils.*/closeQuietly(in);
            }
        }
    }
```

其实没啥， 主要就是包含了 Entry中CLEAN文件的InputStream的数组。并提供了`close()`方法， 方便后续关闭。 拿到DiskLruCache.Snapshot对象后， 只需要调用它的`getInputStream()`方法就可以得到缓存文件的输入流了。同样地，`getInputStream()`方法也需要传一个index参数。有了文件的输入流之后，就可以拿到缓存的内容了， 不管是图片、数据、对象等。

### 细节补充


1. `open()`方法中， 当日志文件存在的时候  `if (cache.journalFile.exists())` 的处理流程。

源码如下：

    ```java
        // prefer to pick up where we left off
        // 先创建一个DiskLruCache
        DiskLruCache cache = new DiskLruCache(directory, appVersion, valueCount, maxSize);
        // 如果日志文件已经存在了, 那么就代表之前已经有建立好的缓存, 我们就把之前建立好的缓存加载的内存中, 继续使用
        // 这就是上面注释的意思 prefer to pick up where we left off  从哪里离开的, 我们再捡起来...
        if (cache.journalFile.exists()) {
            try {
                // 将日志文件中的内容读取到内存中
                cache.readJournal();
                // 处理日志文件
                cache.processJournal();
                cache.journalWriter = new BufferedWriter(new FileWriter(cache.journalFile, true));
                return cache;
            } catch (IOException journalIsCorrupt) {
                System.out.println("DiskLruCache " + directory + " is corrupt: "
                        + journalIsCorrupt.getMessage() + ", removing");
                // 如果日志文件存在, 但是在读取过程中异常, 这里把资源全部关掉后, 把缓存目录全部删掉了
                cache.delete();
            }
        }
    ```
如果日志文件存在的话， 那么就不是第一次使用， 就代表之前已经有建立好的缓存日志， 我们就把建立好的缓存加载到内存中继续使用。
首选调用了` cache.readJournal();`  我们看看它的实现:

```java

    // 读取日志文件
    private void readJournal() throws IOException {
        InputStream in = new BufferedInputStream(new FileInputStream(journalFile));
        try {
            // 读出日志文件的第一行   内容应该是:libcore.io.DiskLruCache
            String magic = /*Streams.*/readAsciiLine(in);
            // 读出日志文件第二行   内容应该是:1
            String version = /*Streams.*/readAsciiLine(in);
            // 读出日志文件第三行   是APP的版本号
            String appVersionString = /*Streams.*/readAsciiLine(in);
            // 读出日志文件第四行
            String valueCountString = /*Streams.*/readAsciiLine(in);
            // 第五航, 是个空行
            String blank = /*Streams.*/readAsciiLine(in);
            
            // 异常...
            if (!MAGIC.equals(magic)
                    || !VERSION_1.equals(version)
                    || !Integer.toString(appVersion).equals(appVersionString)
                    || !Integer.toString(valueCount).equals(valueCountString)
                    || !"".equals(blank)) {
                throw new IOException("unexpected journal header: ["
                        + magic + ", " + version + ", " + valueCountString + ", " + blank + "]");
            }

            // 正常走到这里, 开始读取真正的内容
            while (true) {
                try {
                    // readAsciiLine(in)每次读取一行,  然后丢在readJournalLine()方法里处理
                    readJournalLine(/*Streams.*/readAsciiLine(in));
                } catch (EOFException endOfJournal) { // 这里是用EOF这个异常来作为退出循环的条件了. 读到文件末尾了, 就报这个异常.
                                                        // 那这个循环就会把整个文件的每一行读一下, 然后丢在readJournalLine()方法里处理一下
                    break;
                }
            }
        } finally {
            /*IoUtils.*/closeQuietly(in);
        }
    }
```

`readJournal()`方法中， 先把日志文件的头读出来， `readAsciiLine(in)`方法是每次读取文件的一行。 日志文件的头部一共有五行， 读完之后， 开始读取真正的内容。 读取内容包在一个死循环中， 退出循环的条件就是`EOFException`。当读到文件末尾了, 就报`EOFException`这个异常。因此， 这个循环就会把整个文件的每一行读一下, 然后丢在`readJournalLine()`方法里处理一下。  我们下面看看`readJournalLine()`这个方法是如何进行处理的。

```java

    // 这个方法主要是处理读到的每一行内容
    // 如果valueCount == 1, 也就是在key后面能跟1个size字段,  就是  CLEAN   key  size1  
    // 如果valueCount == 2, key后面能跟valueCount个size字段 就是  CLEAN   key  size1  size2 
    // 第一个是状态  也就是CLEAN READ DIRTY REMOVE中的一个
    // 第二个是 key, 也就是缓存的文件名
    // 第三个是 大小 , 只跟在CLEAN的最后
    // 可以参考一下下面的图
    private void readJournalLine(String line) throws IOException {
        // 用空格分隔一下
        String[] parts = line.split(" ");
        // 异常
        if (parts.length < 2) {
            throw new IOException("unexpected journal line: " + line);
        }

        // key是第二个字段
        String key = parts[1];
        // 如果 状态是REMOVE, 那就把这个key从内存中移除掉
        if (parts[0].equals(REMOVE) && parts.length == 2) {
            lruEntries.remove(key);
            return;
        }

        // 走到这就不是REMOVE
        Entry entry = lruEntries.get(key);
        // 如果内存中没有, 就在内存中缓存一下
        if (entry == null) {
            entry = new Entry(key);
            lruEntries.put(key, entry);
        }

        // 如果是CLEAN的, 那么需要更新这个entry, 记录缓存的文件的大小
        // 如果valueCount == 1, 那么parts.length == 3; 就是  CLEAN   key  size1  用空格分隔, 是分隔是3个part
        // 如果valueCount == 2, 那么parts.length == 4; 就是  CLEAN   key   size1  size2  用空格分隔, 是分隔是4个part
        if (parts[0].equals(CLEAN) && parts.length == 2 + valueCount) {
            
            // 设置可读
            entry.readable = true;
            // 当前的editor, CLEAN是稳定状态, 当前没有操作它
            entry.currentEditor = null;
            // 设置长度, 直接copy数组, 因为一个key能对应多个缓存文件, 那么就要记录多个缓存文件的大小.
            // 下标从2开始,  因为下标0是CLEAN  下标1是key 所以从2开始
            entry.setLengths(/*Arrays.*/copyOfRange(parts, 2, parts.length));
        } else if (parts[0].equals(DIRTY) && parts.length == 2) { //DIRTY
            // 如果是DIRTY, 那么....代表当前正在操作它.... 具体怎么操作, 要看后面跟的是commit()还是abort()  如果commit就在DIRTY后面加CLEAN, 如果abort()就在DIRTY后面加REMOVE
            entry.currentEditor = new Editor(entry);
        } else if (parts[0].equals(READ) && parts.length == 2) { // READ
            // this work was already done by calling lruEntries.get()
            // 在get()方法中做了, 所以这里啥都不做
        } else {
            throw new IOException("unexpected journal line: " + line);
        }
        // 这个方法完了之后, 日志文件中的内容就映射到内存缓存中去了. 就是那个map
    }

```


先将读到的内容用空格分隔了一下。每一条数据至少要有 状态（CLEAN  REMOVE 之类的） 和 key， CLEAN数据还要油size。 因此length < 2 是属于异常的。后面判断 如果 状态是REMOVE, 那就把这个key从内存中移除掉（正常来讲， 内存中应该还没有这个数据呢， 保险起见吧？）。  继续走， 如果不是REMOVE状态的， 并且内存中没有， 就缓存到内存。缓存之后， 还要根据状态（是CLEAN 还是 DIRTY 还是 READ）， 来设置一下内存中entry的属性。 这样， 日志文件中的内容就被映射到内存中了， 同时， 二者的状态是一致的。（这里说的状态， 指的是日志文件中的CLEAN 数据， 在内存中对应的entry 是可读的， 稳定的， currentEditor==null的； 日志文件中的DIRTY 数据，代表正在被操作， 那么内存中对应的entry.currentEditor 一定有值的； ）

 走到这里， 已经把日志文件全部读取完毕， 并且全部映射到内存中去了。之后调用`cache.processJournal();`方法对日志文件进行处理。`processJournal()`主要就是遍历内存中的entry， 把 size 设置正确（也就是初始大小）。同时， DIRTY数据被认为是不一致的数据， 把DIRTY数据对应的文件删除掉， 并且把 entry从 map中移除。至此， 缓存系统进入了一个稳定的状态。后续可以开始写入和读取了。

 ```java
     /**
     * Computes the initial size and collects garbage as a part of opening the
     * cache. Dirty entries are assumed to be inconsistent and will be deleted.
     * 处理日志文件
     * 其实就是:计算初始大小. 同时收集垃圾作为打开缓存的一部分。
     * 脏的条目被假定为不一致的并且将被删除。
     */
    private void processJournal() throws IOException {
        // 经过前面的处理, 日志文件已经存在了, 那么就把这个临时日志文件删除掉
        deleteIfExists(journalFileTmp);
        for (Iterator<Entry> i = lruEntries.values().iterator(); i.hasNext(); ) {
            Entry entry = i.next();
            if (entry.currentEditor == null) { // entry.currentEditor == null代表的CLEAN数据
                for (int t = 0; t < valueCount; t++) {
                    size += entry.lengths[t];
                }
            } else { // 这里代表的DIRTY数据
                entry.currentEditor = null;
                for (int t = 0; t < valueCount; t++) {
                    // 从文件系统中删除掉
                    deleteIfExists(entry.getCleanFile(t));
                    deleteIfExists(entry.getDirtyFile(t));
                }
                // 从map中移除
                i.remove();
            }
        }
    }
 ``` 

 

2. `close()` 方法

首先判断 `if (journalWriter == null)` ， 如果为null， 则代表已经关闭了。
之后， 遍历内存中的所有的entry， 所有DIRTY数据（即正在编辑的）， 都调用一下`abort()`方法， 表示放弃写入。 在`abort()`方法会进行清理， 把所有的DIRTY文件都删除了。也要把`currentEditor`置空。然后，  写一条REMOVE数据到日志文件中。并且从内存中移除了这个key。 `trimToSize()`用于调整缓存的容量大小。最后， 关闭writer， 并且置null。只要能正常执行到 `journalWriter = null`,  就代表日志文件被正常关闭了。 因此， 后续总是使用 `journalWriter == null` 作为日志文件是否关闭的条件。

```java
    /**
     * Closes this cache. Stored values will remain on the filesystem.
     */
    public synchronized void close() throws IOException {
        if (journalWriter == null) {
            return; // already closed
        }
        // 每个DIRTY 后面都跟一个abort 因为要关闭了啊, 不能保证缓存成功了呀...
        for (Entry entry : new ArrayList<Entry>(lruEntries.values())) {
            if (entry.currentEditor != null) {
                entry.currentEditor.abort();
            }
        }
        trimToSize();
        // 关闭Writer
        journalWriter.close();
        journalWriter = null;
    }

```

3. `flush()` 方法


4. `delete()` 方法

```java
/**
     * Closes the cache and deletes all of its stored values. This will delete
     * all files in the cache directory including files that weren't created by
     * the cache.
     * 关闭Cache, 并且删除所有的缓存内容.
     * 它将会删除缓存目录下的所有的文件, 包括并非DiskLruCache创建的文件也会被一并删除
     */
    public void delete() throws IOException {
        // 调用了一下Closeable的close()方法
        close();
        /*IoUtils.*/deleteContents(directory);
    }
```

先调用了一下 `close()` 方法，关闭日志文件， 并进行相关的清理操作。然后删除缓存目录下的所有的文件, 包括并非DiskLruCache创建的文件也会被一并删除。

5. `remove()` 方法


6. `ExecutorService`

```java
/** This cache uses a single background thread to evict entries. */
    private final ExecutorService executorService = new ThreadPoolExecutor(0, 1,
            60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
```

这是一个单线程的线程池， 正如注释所说：这个缓存使用单一的后台线程去驱逐 entry。
使用中， 每次都调用 `executorService.submit(cleanupCallable)` 使用线程池来执行callable回调。callable中的 `trimToSize()`方法中 不断循环， 移除最老的元素， 使缓存大小达到一个合理的范围。线程池的具体参见 [线程池](../并发/线程池.md)

7. 线程安全

之所以说它是线程安全的， 是因为在几个核心方法`get()` `edit()` `size()` `remove` `flush()` `close()` 中都加了锁。


### 流程图

最后附上整个DiskLruCache 的流程图。


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/DiskLruCache_flow_chart.png)