---
title: HashMap

date: 2019-06-30

categories: 
   - Java

tags: 
   - Java 


description: ​
---


<!-- TOC -->

- [一、为什么需要HashMap?](#一为什么需要hashmap)
    - [1、线性检索：](#1线性检索)
    - [2、二分搜索：](#2二分搜索)
    - [3、Hash表中的查找：](#3hash表中的查找)
    - [4、Hash冲突解决策略](#4hash冲突解决策略)
- [二、红黑树的出现](#二红黑树的出现)
- [三、实现原理](#三实现原理)
- [四、数据结构](#四数据结构)
- [五、HashMap存取put/get](#五hashmap存取putget)
- [六、代码](#六代码)
    - [put](#put)
    - [get](#get)
    - [扩容](#扩容)
        - [如果是链结构](#如果是链结构)
        - [如果是树结构](#如果是树结构)
- [HashMap put与resize的实例图](#hashmap-put与resize的实例图)
- [8 和 6 的原因](#8-和-6-的原因)
- [其他](#其他)

<!-- /TOC -->


from:https://blog.csdn.net/qq_40645822/article/details/91139215
from:https://blog.csdn.net/weixin_52801742/article/details/114252312

# 一、为什么需要HashMap?
      在我们写程序的时候经常会遇到数据检索等操作，对于几百个数据的小程序而言，数据的存储方式或是检索策略没有太大影响，但对于大数据，效率就会差很远。

## 1、线性检索：

线性检索是最为直白的方法，把所有数据都遍历一遍，然后找到你所需要的数据。其对应的数据结构就是数组，链表等线性结构，这种方式对于大数据而言效率极低，其时间复杂度为O(n)。

## 2、二分搜索：

二分搜索算是对线性搜索的一个改进，比如说对于[1，2，3，4，5，6，7，8]，我要搜索一个数（假设是2），我先将这个数与4（这个数一般选中位数比较好）比较，小于4则在4的左边[1，2，3]中查找，再与2比较，相等，就成功找到了，这种检索方式好处在于可以省去很多不必要的检索，每次只用查找集合中一半的元素。其时间复杂度为O(logn)。但其也有限制，数排列本身就需要是有序的。

## 3、Hash表中的查找：

好了，重点来了，Hash表闪亮登场，这是一种时间复杂度为O(1)的检索，就是说不管你数据有多少只需要查一次就可以找到目标数据。大家请看下图。

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/hash1.png)


大家可以看到这个数组中的值就等于其下标，比如说我要存11，我就把它存在a[11]里面，这样我要找某个数字的时候就直接对应其下标就可以了。这其实是一种牺牲空间换时间的方法，这样会对内存占用比较大，但检索速度极快，只需要搜索一次就能查到目标数据。

      看了上面的Hash表你肯定想问，如果我只存一个数10000，那我不是要存在a[10000]，这样其他空间不是白白浪废了吗，好吧，不存在的。Hash表已经有了其应对方法，那就是Hash函数。Hash表的本质在于可以通过value本身的特征定位到查找集合的元素下标，从而快速查找。一般的Hash函数为：要存入的数 mod（求余） Hash数组长度。比如说对于上面那个长度为9的数组，12的位置为12 mod 9=3，即存在a3，通过这种方式就可以安放比较大的数据了。

## 4、Hash冲突解决策略

看了上面的讲解，有出现了一个问题，通过求余数得到的地址可能是一样的。这种我们称为Hash冲突，如果数据量比较大而Hash桶比较小，这种冲突就很严重。我们采取如下方式解决冲突问题。


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/solve_confict.png)

 我们可以看到12和0的位置冲突了，然后我们把该数组的每一个元素变成了一个链表头，冲突的元素放在了链表中，这样在找到对应的链表头之后会顺着链表找下去，至于为什么采用链表，是为了节省空间，链表在内存中并不是连续存储，所以我们可以更充分地使用内存。

     上面讲了那么多，那跟我们今天的主题HashMap有什么关系呢？进入正题。我们知道HashMap中的值都是key，value，这里的存储与上面的很像，key会被映射成数据所在的地址，而value就在以这个地址为头的链表中，这种数据结构在获取的时候就很快。

     但是又出现了一个问题：如果hash桶较小，数据量较大，就会导致链表非常的长。所以就出现了红黑树。


# 二、红黑树的出现
     在JDK1.6，JDK1.7中，HashMap采用位桶+链表实现，即使用链表处理冲突，同一hash值的元素都存储在一个链表里。但是当位于一个桶中的元素较多，即hash值相等的元素较多时，通过key值依次查找的效率较低。而JDK1.8中，HashMap采用位桶+链表+红黑树实现，当链表长度超过阈值（8）时，将链表转换为红黑树，这样大大减少了查找时间。

 JDK1.8HashMap的红黑树是这样解决的：

         如果某个桶中的记录过大的话（当前是TREEIFY_THRESHOLD = 8），HashMap会动态的使用一个专门的treemap实现来替换掉它。这样做的结果会更好，是O(logn)，而不是糟糕的O(n)。

        它是如何工作的？前面产生冲突的那些KEY对应的记录只是简单的追加到一个链表后面，这些记录只能通过遍历来进行查找。但是超过这个阈值后HashMap开始将列表升级成一个二叉树，使用哈希值作为树的分支变量，如果两个哈希值不等，但指向同一个桶的话，较大的那个会插入到右子树里。如果哈希值相等，HashMap希望key值最好是实现了Comparable接口的，这样它可以按照顺序来进行插入。这对HashMap的key来说并不是必须的，不过如果实现了当然最好。如果没有实现这个接口，在出现严重的哈希碰撞的时候，你就并别指望能获得性能提升了。



# 三、实现原理
       HashMap可以看成是一个大的数组，然后每个数组元素的类型是Node类。当添加一个元素（key-value）时，就首先计算元素key的hash值，以此确定插入数组中的位置，但是可能存在同一hash值的元素已经被放在数组同一位置了，这时就添加到同一hash值的元素的后面，他们在数组的同一位置，但是形成了链表，同一各链表上的Hash值是相同的，所以说数组存放的是链表。而当链表长度太长时，链表就转换为红黑树，这样大大提高了查找的效率。

     当链表数组的容量超过初始容量的0.75时，再散列将链表数组扩大2倍，把原链表数组的搬移到新的数组中。

# 四、数据结构

上面说过HashMap可以看成是一个大的数组，然后每个数组元素的类型是Node类型，源码里定义如下：

```java
transient Node<K,V>[] table;
```

注意Node类还有两个子类：TreeNode和Entry


```java
TreeNode <K,V> extends Entry<K,V> extends Node<K,V>
```

上图中的链表就是Node类，而红黑树正是TreeNode类。


# 五、HashMap存取put/get

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/hashmap_put2.png)

# 六、代码

## put

```java
//对外开发使用
public V put(K key, V value) {
    return putVal(hash(key), key, value, false, true);
}

```

```java
//存值的真正执行者
final V putVal(int hash, K key, V value, boolean onlyIfAbsent, boolean evict) {
    
    //定义一个数组，一个链表，n永远存放数组长度，i用于存放key的hash计算后的值，即key在数组中的索引        
    Node<K,V>[] tab; Node<K,V> p; int n, i;
    
    //判断table是否为空或数组长度为0，如果为空则通过resize()实例化一个数组并让tab作为其引用，并且让n等于实例化tab后的长度        
    if ((tab = table) == null || (n = tab.length) == 0)
        n = (tab = resize()).length;
    
    //根据key经过hash()方法得到的hash值与数组最大索引做与运算得到当前key所在的索引值，并且将当前索引上的Node赋予给p并判断是否该Node是否存在
    if ((p = tab[i = (n - 1) & hash]) == null)
        tab[i] = newNode(hash, key, value, null);//若tab[i]不存在，则直接将key-value插入该位置上。
    
        //该位置存在数据的情况  
    else {
        Node<K,V> e; K k; //重新定义一个Node，和一个k
        
	    // 该位置上数据Key计算后的hash等于要存放的Key计算后的hash并且该位置上的Key等于要存放的Key     
        if (p.hash == hash &&((k = p.key) == key || (key != null && key.equals(k))))
            e = p;	//true，将该位置的Node赋予给e
	else if (p instanceof TreeNode)  //判断当前桶类型是否是TreeNode
	    //ture，进行红黑树插值法,写入数据
            e = ((TreeNode<K,V>)p).putTreeVal(this, tab, hash, key, value); 	
        else {	
	    //false, 遍历当前位置链表
            for (int binCount = 0; ; ++binCount) {
                //查找当前位置链表上的表尾，表尾的next节点必然为null,找到表尾将数据赋给下一个节点
                if ((e = p.next) == null) {
                     p.next = newNode(hash, key, value, null);	//是，直接将数据写到下个节点
                    // 如果此时已经超过8个了, 也就是第 9 个了，还没找个表尾，那么从第 9个开始就要进行红黑树操作
		    if (binCount >= TREEIFY_THRESHOLD - 1)
                        treeifyBin(tab, hash);	//红黑树插值具体操作
                        break;
                }
                //如果当前位置的key与要存放的key的相同，直接跳出，不做任何操作   
                if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k))))
                    break;
                //将下一个给到p进行逐个查找节点为空的Node
		p = e;
            }
        }
        //如果e不为空，即找到了一个去存储Key-value的Node 
	if (e != null) { // existing mapping for key
            V oldValue = e.value;    
	    if (!onlyIfAbsent || oldValue == null)
                e.value = value;
            afterNodeAccess(e);
            return oldValue;
        }
    }
    ++modCount;
    //当最后一次调整之后Size大于了临界值，需要调整数组的容量
    if (++size > threshold)
        resize();
    afterNodeInsertion(evict);
    return null;
}
```


转红黑树
```java
    /**
     * Replaces all linked nodes in bin at index for given hash unless
     * table is too small, in which case resizes instead.
     */
    final void treeifyBin(Node<K,V>[] tab, int hash) {
        int n, index; Node<K,V> e;
        // 如果 数组 == null 或者  数组元素小于 64 个, 不需要转, 直接扩容即可
        if (tab == null || (n = tab.length) < MIN_TREEIFY_CAPACITY)
            resize();
        else if ((e = tab[index = (n - 1) & hash]) != null) {
            TreeNode<K,V> hd = null, tl = null;
            do {
                TreeNode<K,V> p = replacementTreeNode(e, null);
                if (tl == null)
                    hd = p;
                else {
                    p.prev = tl;
                    tl.next = p;
                }
                tl = p;
            } while ((e = e.next) != null);
            if ((tab[index] = hd) != null)
                hd.treeify(tab);
        }
    }
```


## get

**取值：get(key)方法时获取key的hash值，计算hash&(n-1)得到在链表数组中的位置first=tab[hash&(n-1)],先判断first的key是否与参数key相等，不等就遍历后面的链表找到相同的key值返回对应的Value值即可**


```java
//对外公开方法
public V get(Object key) {
        Node<K,V> e;
        return (e = getNode(hash(key), key)) == null ? null : e.value;
    }
 ```

 ```java
//实际逻辑控制方法
final Node<K,V> getNode(int hash, Object key) {
	//定义相关变量
    Node<K,V>[] tab; Node<K,V> first, e; int n; K k;
	//保证Map中的数组不为空，并且存储的有值，并且查找的key对应的索引位置上有值
    if ((tab = table) != null && (n = tab.length) > 0 && (first = tab[(n - 1) & hash]) != null) {
        // always check first node 第一次就找到了对应的值
	if (first.hash == hash && ((k = first.key) == key || (key != null && key.equals(k))))
            return first;
	//判断下一个节点是否存在
	if ((e = first.next) != null) {
            //true,检测是否是TreeNode
            if (first instanceof TreeNode)
                return ((TreeNode<K,V>)first).getTreeNode(hash, key); //通过TreeNode的get方法获取值
            //否，遍历链表
	    do {
		//判断下一个节点是否是要查找的对象
                if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k))))
                    return e;
            }while ((e = e.next) != null);
        }
    }//未找到，返回null
    return null;
 }
```

## 扩容

```java
final Node<K,V>[] resize() {
    Node<K,V>[] oldTab = table;
    int oldCap = (oldTab == null) ? 0 : oldTab.length;	//未扩容时数组的容量
    int oldThr = threshold;
    int newCap, newThr = 0;//定义新的容量和临界值
    //当前Map容量大于零，非第一次put值
    if (oldCap > 0) {
        if (oldCap >= MAXIMUM_CAPACITY) {	//超过最大容量:2^30
			//临界值等于Integer类型的最大值 0x7fffffff=2^31-1
            threshold = Integer.MAX_VALUE;	
            return oldTab;
        }
		//当前容量在默认值和最大值的一半之间
        else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY && oldCap >= DEFAULT_INITIAL_CAPACITY)
            newThr = oldThr << 1;	//新临界值为当前临界值的两倍
    }
	//当前容量为0，但是当前临界值不为0，让新的容量等于当前临界值
    else if (oldThr > 0) 
        newCap = oldThr;
    //当前容量和临界值都为0,让新的容量为默认值，临界值=初始容量*默认加载因子
	else {
        newCap = DEFAULT_INITIAL_CAPACITY;
        newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);
    }
	//如果新的临界值为0
    if (newThr == 0) {
        float ft = (float)newCap * loadFactor;
        newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ? (int)ft : Integer.MAX_VALUE);
    }
	//临界值赋值
    threshold = newThr;
    //扩容table
    Node<K,V>[] newTab = (Node<K,V>[])new Node[newCap];
    table = newTab;
    if (oldTab != null) {
        for (int j = 0; j < oldCap; ++j) {
            Node<K,V> e;
            if ((e = oldTab[j]) != null) { // 数组j位置的元素不为空，需要该位置上的所有元素进行rehash
                oldTab[j] = null;
                if (e.next == null)// 桶中只有一个元素，则直接rehash
                    newTab[e.hash & (newCap - 1)] = e;//此时newCap = oldCap*2
                else if (e instanceof TreeNode) 
                    //节点为红黑树，进行切割操作.  如果切割后, 数量小于等于 6, 要退化成单链表.
                    ((TreeNode<K,V>)e).split(this, newTab, j, oldCap);
                else { //链表的下一个节点还有值，但节点位置又没有超过8. 
                // 表示桶中是链表结构（JDK1.7中旧链表迁移新链表的时候，用的是头插法，如果在新表的数组索引位置相同，则链表元素会倒置；但是JDK1.8不会倒置，用的是双指针）
                    //lo就是扩容后仍然在原地的元素链表, 也就是低位链表，其桶位置不变，head和tail分别代表链表在新的数组中的首尾指针
					//hi就是扩容后的高位链表, 下标为  原位置+原容量  的元素链表，从而不需要重新计算hash。
					Node<K,V> loHead = null, loTail = null;
                    Node<K,V> hiHead = null, hiTail = null;
                    Node<K,V> next;
                    //循环链表直到链表末再无节点
					do {
                        // 开始循环的时候, e 是头, 就是链表上的第一个节点
                        next = e.next;
						//e.hash&oldCap == 0 表示元素位置还在原位置
                        if ((e.hash & oldCap) == 0) {
                            // loTail链表在新的数组中的尾指针, 尾指针=null 表示新的数组里面的链表还没有呢. 如果有的话, tail肯定不是 null
                            if (loTail == null) 
                                // 那就把这个 e 作为新的数组中的链表的头
                                loHead = e;
                            else
                                // 这个表示新的数组中的链表已经存在了, 跟在后面就好了, 也就是 tail.next = e
                                loTail.next = e;

                            // 最后记得把 e 作为尾指针
                            loTail = e;
                        }
                        else { // 这个表示要迁移到新的数组中, 下标为[原位置+原容量]  的 新的链表中去了
                            if (hiTail == null)
                                hiHead = e;
                            else
                                hiTail.next = e;
                            hiTail = e;
                        }
                    } while ((e = next) != null);
					//循环链表结束，通过判断loTail是否为空来拷贝整个链表到扩容后table
                    if (loTail != null) {
                       loTail.next = null;
                        newTab[j] = loHead;
                    }
                    if (hiTail != null) {
                        hiTail.next = null;
                        newTab[j + oldCap] = hiHead;
                    }
                }
            }
        }
    }
    return newTab;
}
```


在 Java8 中，HashMap 中的桶可能是链表结构，也可能是树结构。
从网上找来一张图，直观展示 HashMap 结构：

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/hashmap3.png)



在这几个地方触发扩容:
1. 第一次 put, table==null 的时候

2. 单链表中超过 8 个. 想要转红黑树了,  但是数组长度小于 64 , 这时也是扩容
```java
final void treeifyBin(Node<K,V>[] tab, int hash) {
        int n, index; Node<K,V> e;
        if (tab == null || (n = tab.length) < MIN_TREEIFY_CAPACITY)
            resize();
}
```

3. put 之后, 判断 size 超过临界点
```java
if (++size > threshold)
            resize();
```

扩容时候, 针对链表的迁移和红黑树的迁移

### 如果是链结构

将旧链表拆分成两条新的链表，通过 e.hash & oldCap 来计算新链表在扩容后的数组中的新下标。
当 e.hash & oldCap = 0，则节点在新数组中的索引值与旧索引值相同。
当 e.hash & oldCap = 1，则节点在新数组中的索引值为旧索引值+旧数组容量。
对 e.hash & oldCap 公式的推导见上一篇文章 《HashMap中的取模和扩容公式推导》: https://segmentfault.com/a/1190000039294622

### 如果是树结构

HashMap 对树结构的定义如下：

```java
static final class TreeNode<K,V> extends LinkedHashMap.Entry<K,V> {
	TreeNode<K,V> parent;  // red-black tree links
	TreeNode<K,V> left;
	TreeNode<K,V> right;
	TreeNode<K,V> prev;    // needed to unlink next upon deletion
	boolean red;
	TreeNode(int hash, K key, V val, Node<K,V> next) {
		super(hash, key, val, next);
	}
}	

```
需要明确的是：TreeNode 既是一个红黑树结构，也是一个双链表结构。

判断节点 e instanceof TreeNode 为 true，则调用 HashMap.TreeNode#split 方法对树进行拆分，而拆分主要用的是 TreeNode 的链表属性。
拆分代码如下：

```java
final void split(HashMap<K,V> map, Node<K,V>[] tab, int index, int bit) {
	TreeNode<K,V> b = this;
	// Relink into lo and hi lists, preserving order
	TreeNode<K,V> loHead = null, loTail = null;
	TreeNode<K,V> hiHead = null, hiTail = null;
	int lc = 0, hc = 0; // 用于决定红黑树是否要转回链表
	for (TreeNode<K,V> e = b, next; e != null; e = next) { // 对节点e进行遍历（首先明确：TreeNode既是一个红黑树结构，也是一个双链表结构）
		next = (TreeNode<K,V>)e.next;
		e.next = null; // 把e的下一个节点赋值给next后，断开e与e.next节点
		if ((e.hash & bit) == 0) { // 原索引
			if ((e.prev = loTail) == null)
				loHead = e;
			else
				loTail.next = e;
			loTail = e;
			++lc;
		}
		else { // 原索引 + oldCap
			if ((e.prev = hiTail) == null)
				hiHead = e;
			else
				hiTail.next = e;
			hiTail = e;
			++hc;
		}
	}

	if (loHead != null) {
		if (lc <= UNTREEIFY_THRESHOLD)
			tab[index] = loHead.untreeify(map); // 转为链结构
		else {
			tab[index] = loHead;
			if (hiHead != null) // (else is already treeified)
				loHead.treeify(tab); // 转换成树结构
		}
	}
	if (hiHead != null) {
		if (hc <= UNTREEIFY_THRESHOLD)
			tab[index + bit] = hiHead.untreeify(map);
		else {
			tab[index + bit] = hiHead;
			if (loHead != null)
				hiHead.treeify(tab);
		}
	}
}

```

# HashMap put与resize的实例图   

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/resize.png)



# 8 和 6 的原因


HashMap在JDK1.8及以后的版本中引入了红黑树结构，若桶中链表元素个数大于等于8时，链表转换成树结构；若桶中链表元素个数小于等于6时，树结构还原成链表。因为红黑树的平均查找长度是log(n)，长度为8的时候，平均查找长度为3，如果继续使用链表，平均查找长度为8/2=4，这才有转换为树的必要。链表长度如果是小于等于6，6/2=3，虽然速度也很快的，但是转化为树结构和生成树的时间并不会太短。

还有选择6和8，中间有个差值7可以有效防止链表和树频繁转换。假设一下，如果设计成链表个数超过8则链表转换成树结构，链表个数小于8则树结构转换成链表，如果一个HashMap不停的插入、删除元素，链表个数在8左右徘徊，就会频繁的发生树转链表、链表转树，效率会很低。


8个. 说的是不包含数组里的那个链表头, 还要有 8 个. 此时转红黑树, 也就是总共要超过 8 个. 也就是 9 个.




# 其他

MAXIMUM_CAPACITY:1073741824

Integer.MAX_VALUE:2147483647



![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/hashmap_put.png)



hashcode 和 equals 的关系:
https://www.cnblogs.com/justdojava/p/11271438.html



