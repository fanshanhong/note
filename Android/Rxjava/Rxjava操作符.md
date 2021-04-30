---
title: RxJava2操作符

date: 2018-07-25

categories: 
   - Rxjava

tags: 
   - Rxjava 

description: ​
---

from:
https://www.jianshu.com/p/3fdd9ddb534b

# 创建操作

* create

* from

* just

* fromArray

* range

* interval

# 合并

* concat： 按顺序连接多个Observables。需要注意的是Observable.concat(a,b)等价于a.concatWith(b)。


```java
    Observable<Integer> observable1=Observable.just(1,2,3,4);
    Observable<Integer>  observable2=Observable.just(4,5,6);

    Observable.concat(observable1,observable2)
            .subscribe(item->Log.d("JG",item.toString()));//1,2,3,4,4,5,6
```


* merge

* zip


# 过滤

* filter




# 常见请求使用的操作符

* 两个请求A 和 B, 必须两个请求都返回, 并且将两个请求返回的数据柔和再一起, 再做处理, 使用 zip. Zip会跟Merge一样，也会将两个Observable同时发送，只是在处理结果的时候会将两个发送源的结果一并返回。

kotlin flow 是用 zip

注意：同Merge一样，Zip实现并行的话一定要在o1和o2后面加上.subscribeOn(Schedulers.io())，否则就是串行了。


```java
Observable.zip(o1, o2, new BiFunction<String, String, String>() {
            @Override
            public String apply(String a, String b) throws Exception {
                return a + b;
            }
        }).compose(RxUtil.applySchedulers()).subscribe(new Consumer<String>() {
            @Override
            public void accept(String o) throws Exception {
                Log.d(TAG, o);
            }
        });

```




Merge是将两个Observable：o1和o2同时发送，然后再根据达到的结果进行处理，同理这边也用Object表示。

* 两个请求, 只要有一个返回, 就直接使用, 后面返回的数据丢弃.  使用 amb

* 两个请求A 和 B, 请求 B 需要的参数在 A 返回的数据中. 也就是串行请求, 使用flatMap 可以, 

Concat是一个聚合操作符，我们看到有两个Observable：o1和o2，将它们通过concat聚合在一起，系统会先处理o1，然后再处理o2，所以我们在subscribe接收的时候并不知道具体类型，所以用Object代替，在实际过程中进行类型判断。

kotlin flow 是用 flatMapMerge

* 两个请求, 只要有 1 个出错, 就全都出错, 用 CoroutineScope(Kotlin)


https://blog.csdn.net/ddnosh/article/details/100887838