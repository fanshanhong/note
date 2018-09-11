# HTML 简介

---

### 什么是 HTML？

* HTML 指的是超文本标记语言 (Hyper Text Markup Language)
* HTML 是一种标记语言, 用一套标记标签来描述网页

---

### HTML 标签
* HTML 标记便签, 通常被称为HTML标签(HTML Tag)
* HTML 标签是由尖括号包围的关键字, 比如 `<html>`
* HTML 标签通常是成对出现的，比如 `<b>` 和 `</b>`

----


### HTML 文档 = 网页

* HTML 文档描述网页
* HTML 文档包含 HTML 标签和纯文本
* HTML 文档也称为网页

Web浏览器的作用是读取HTML文档的内容, 并且以网页的形式展现它们. 浏览器不会显示HTML标签, 而是已使用标签来解释页面内容

```html
<html>
<body>

<h1>我的第一个标题</h1>

<p>我的第一个段落。</p>

</body>
</html>
```

# HTML 编辑器

---

* Notepad
* TextEdit
* Adobe Dreamweaver
* Microsoft Expression Web

既可以使用 .htm 也可以使用 .html 扩展名。两者没有区别，完全根据您的喜好。

# HTML 基础

---

### HTML标题

---

* HTML 标题（Heading）是通过 `<h1> - <h6>` 等标签进行定义的。h1最大  h6最小。
* 请仅仅把标题标签用于标题文本。不要仅仅为了产生粗体文本而使用它们。请使用其它标签或 CSS 代替

```html
<h1>This is Heading1<h1>
<h2>This is Heading2<h2>
<h3>This is Heading3<h3>
```

### HTML 段落

---

HTML段落是用`<p>`来定义的
```html
<p>This is a paragraph.</p>
<p>This is another paragraph.</p>
```

### HTML 链接

---

HTML链接是用`<a>`来定义的

```html
<a href="http://www.w3school.com.cn">this is a link</a>
```

注释：在 href 属性中指定链接的地址。

### HTML 图像
---

HTML图像是通过`<img>`来定义的

```html
<img src="xxx.jpg" width="300" height="300" />
```

注释：图像的名称和尺寸是以属性的形式提供的。
