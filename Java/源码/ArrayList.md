---
title: ArrayList

date: 2019-06-30

categories: 
   - Java

tags: 
   - Java 


description: ​
---



# 初始化

```java
    public ArrayList() {
        this.elementData = DEFAULTCAPACITY_EMPTY_ELEMENTDATA;
    }
```

```java
    // 空数组
    private static final Object[] DEFAULTCAPACITY_EMPTY_ELEMENTDATA = {};

    transient Object[] elementData; // non-private to simplify nested class access
```


static修饰的变量，常驻于方法区，我们不需要new，JVM会提前给我们初始化好.

初始化就是指向一个 static 的空的数组.


# add()

```java
    public boolean add(E e) {
        // size是逻辑长度
        // 第一次, size=0
        ensureCapacityInternal(size + 1);  // Increments modCount!!
        elementData[size++] = e;
        return true;
    }
```

```java

    private void ensureCapacityInternal(int minCapacity) { // 第一次, minCapacity=1
        ensureExplicitCapacity(calculateCapacity(elementData, minCapacity));
    }

    private static int calculateCapacity(Object[] elementData, int minCapacity) {
        if (elementData == DEFAULTCAPACITY_EMPTY_ELEMENTDATA) {
            // 第一次会进入, 返回 DEFAULT_CAPACITY=10
            return Math.max(DEFAULT_CAPACITY, minCapacity);
        }
        return minCapacity;
    }

    private void ensureExplicitCapacity(int minCapacity) {// 这里就传入 10
        modCount++;

        // overflow-conscious code
        if (minCapacity - elementData.length > 0)// 第一次, elementData.length=0, 因为elementData 是空数组
            grow(minCapacity);// 扩容
    }
```

```java
    private void grow(int minCapacity) {// minCapacity=10
        int oldCapacity = elementData.length; // 第一次, 这里是 0, 因为elementData 是空数组
        int newCapacity = oldCapacity + (oldCapacity >> 1); // newCapacity = 0
        if (newCapacity - minCapacity < 0)// 不满足
            newCapacity = minCapacity;
        if (newCapacity - MAX_ARRAY_SIZE > 0)// 不满足
            newCapacity = hugeCapacity(minCapacity);
        // minCapacity is usually close to size, so this is a win:
        elementData = Arrays.copyOf(elementData, newCapacity);// 内部调用 System.arrayCopy 进行数组复制
    }
```

因此, 首次 add() 方法调用, 只是创建一个默认长度为10的Object[]数组

然后把新的元素放在 index=0 的位置


# 扩容原理

ArrayList 底层由 Object[]数组 来维护, Object[] 的长度是不能动态变化的.

因此只能创建一个新的更大的数组, 然后通过 System.arrayCopy() 方法把原有内容复制到新的数组, 以达到扩容的效果.


代码说明:
加入初始化的时候, ArrayList 的容量就是默认的 10. 当我们 add 第 11 个元素的时候, 调用 add 方法
```java
    public boolean add(E e) {
        // size是逻辑长度
        // 此时, size=10
        ensureCapacityInternal(size + 1);  // Increments modCount!!
        elementData[size++] = e;
        return true;
    }
```

```java

    private void ensureCapacityInternal(int minCapacity) { //  此时, minCapacity=11
        ensureExplicitCapacity(calculateCapacity(elementData, minCapacity));
    }

    private static int calculateCapacity(Object[] elementData, int minCapacity) {
        if (elementData == DEFAULTCAPACITY_EMPTY_ELEMENTDATA) {
            return Math.max(DEFAULT_CAPACITY, minCapacity);
        }
        // 走这里返回11
        return minCapacity;
    }

    private void ensureExplicitCapacity(int minCapacity) {// 这里就传入 11
        modCount++;

        if (minCapacity - elementData.length > 0)// 11  已经大于原有的数组长度了, 装不下了, 因此需要扩容
            grow(minCapacity);// 扩容
    }
```


```java
    private void grow(int minCapacity) {
        int oldCapacity = elementData.length;// 10
        int newCapacity = oldCapacity + (oldCapacity >> 1);// 这里相当于 newCapacity=oldCapacity*1.5
        if (newCapacity - minCapacity < 0)
            newCapacity = minCapacity;
        if (newCapacity - MAX_ARRAY_SIZE > 0)
            newCapacity = hugeCapacity(minCapacity);
        // minCapacity is usually close to size, so this is a win:
        elementData = Arrays.copyOf(elementData, newCapacity);
    }
```


在 Arrays.copyOf 中, 会创建一个新的数组, 容量是 newCapacity, 然后把老的数组中的内容复制到新的数组中, 完成扩容工作

```java
    public static <T,U> T[] copyOf(U[] original, int newLength, Class<? extends T[]> newType) {
        @SuppressWarnings("unchecked")
        T[] copy = ((Object)newType == (Object)Object[].class)
            ? (T[]) new Object[newLength]
            : (T[]) Array.newInstance(newType.getComponentType(), newLength);
        System.arraycopy(original, 0, copy, 0,
                         Math.min(original.length, newLength));
        return copy;
    }
```


扩容完了, 把要添加的数据放在数组的指定下标处即可


# remove

按照下标来删除

```java
    public E remove(int index) {
        rangeCheck(index);

        modCount++;
        // 拿到要删除的元素
        E oldValue = elementData(index);
        // 计算一下有多少元素要移动. 正常来讲, 要删除的元素 后面的所有元素向前挪动一个位置.
        int numMoved = size - index - 1;
        if (numMoved > 0)
            // 使用 System.arraycopy  在同一数组中进行 copy
            System.arraycopy(elementData, index+1, elementData, index,
                             numMoved);
        // 这个是把最后一个元素置 null
        elementData[--size] = null; // clear to let GC do its work

        // 返回已经删除的元素
        return oldValue;
    }

```

按照元素来删除

```java
    public boolean remove(Object o) {
        if (o == null) {
            for (int index = 0; index < size; index++)
                if (elementData[index] == null) {
                    fastRemove(index);
                    return true;
                }
        } else {
            // 走这里, 注意是调用了对象的 equals 方法!!! 与 hashCode 无关
            // 只有涉及到 HashMap HashSet 这样的哈希表, 才与 hashCode 有关
            for (int index = 0; index < size; index++)
                if (o.equals(elementData[index])) {
                    fastRemove(index);
                    return true;
                }
        }
        return false;
    }
```

```java
    private void fastRemove(int index) {
        modCount++;
        int numMoved = size - index - 1;
        if (numMoved > 0)
            System.arraycopy(elementData, index+1, elementData, index,
                             numMoved);
        elementData[--size] = null; // clear to let GC do its work
    }
```

