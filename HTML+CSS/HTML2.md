# HTML 元素
---

HTML 元素指的是从开始标签（start tag）到结束标签（end tag）的所有代码。
HTML 文档是由HTML元素组合而成.

---

HTML 元素语法
* HTML 元素以开始标签起始
* HTML 元素以结束标签终止
* 元素的内容是开始标签与结束标签之间的内容
* 某些 HTML 元素具有空内容（empty content）
* 空元素在开始标签中进行关闭（以开始标签的结束而结束）
* 大多数 HTML 元素可拥有属性

HTML 文档实例

```HTML
<html>

<body>
<p>This is my first paragraph.</p>
</body>

</html>
```

上面的例子包含三个 HTML 元素。

这个 `<p>` 元素定义了 HTML 文档中的一个段落。
这个元素拥有一个开始标签 `<p>`，以及一个结束标签 `</p>`。
元素内容是：This is my first paragraph。

`<body>` 元素定义了 HTML 文档的主体。
这个元素拥有一个开始标签 `<body>`，以及一个结束标签 `</body>`。
元素内容是另一个 HTML 元素（p 元素）。

`<html>` 元素定义了整个 HTML 文档。
这个元素拥有一个开始标签 `<html>`，以及一个结束标签 `</html>`。
元素内容是另一个 HTML 元素（body 元素）。

> `<p>` `<body>` `<html>` 这样的都称为元素节点  `<p>`中的内容 This is my first paragraph. 是一个文本节点.  至于一些属性, 被称为属性节点.

### 空的 HTML 元素
---

没有内容的 HTML 元素被称为空元素。空元素是在开始标签中关闭的。

`<br>` 就是没有关闭标签的空元素（`<br>` 标签定义换行）。

在 XHTML、XML 以及未来版本的 HTML 中，所有元素都必须被关闭。

在开始标签中添加斜杠，比如 `<br />`，是关闭空元素的正确方法，HTML、XHTML 和 XML 都接受这种方式。


### HTML 提示：使用小写标签
---

HTML 标签对大小写不敏感：<P> 等同于 <p>。许多网站都使用大写的 HTML 标签。

W3School 使用的是小写标签，因为万维网联盟（W3C）在 HTML 4 中推荐使用小写，而在未来 (X)HTML 版本中强制使用小写。


# HTML属性
---

属性为HTML元素提供附加信息

* HTML 标签可以拥有属性。属性提供了有关 HTML 元素的更多的信息。

* 属性总是以名称/值对的形式出现，比如：name="value"。

* 属性总是在 HTML 元素的开始标签中规定。


```HTML
<a href="http://www.w3school.com.cn">This is a link</a>
```

HTML链接由`<a>`标签定义. 链接的地址在href属性中指定


```HTML
<h1 align="center">Head</h1>
```

`<h1>` 定义标题的开始 属性align指定标题的对齐方式

```HTML
<body bgcolor="yellow"> 拥有关于背景颜色的附加信息。
```

`<body>`定义HTML的主体, bgcolor属性指定背景颜色

---


### 始终为属性值加引号

属性值应该始终被包括在引号内。双引号是最常用的，不过使用单引号也没有问题。

在某些个别的情况下，比如属性值本身就含有双引号，那么您必须使用单引号，例如：

```
name='Bill "HelloWorld" Gates'
```
