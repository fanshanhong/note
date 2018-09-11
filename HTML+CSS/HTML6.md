# 样式表
---


### 外部样式表
当样式需要被应用到很多页面的时候，外部样式表将是理想的选择。使用外部样式表，你就可以通过更改一个文件来改变整个站点的外观。

```HTML
<head>
<link rel="stylesheet" type="text/css" href="mystyle.css">
</head>
```

### 内部样式表
当单个文件需要特别样式时，就可以使用内部样式表。你可以在 head 部分通过 `<style>` 标签定义内部样式表。

```HTML
<head>

<style type="text/css">
body {background-color: red}
p {margin-left: 20px}
</style>
</head>
```

style中指定 `<body>` 的通用样式 和 `<p>`的通用样式


### 内联样式
当特殊的样式需要应用到个别元素时，就可以使用内联样式。 使用内联样式的方法是在相关的标签中使用样式属性。样式属性可以包含任何 CSS 属性。以下实例显示出如何改变段落的颜色和左外边距。
```HTML
<p style="color: red; margin-left: 20px">
This is a paragraph
</p>
```


| 标签	| 描述
| ---   | ---
| `<style>`	|定义样式定义。
| `<link>`	|定义资源引用。
| `<div>`	|定义文档中的节或区域（块级）。
| `<span>`	|定义文档中的行内的小块或区域。
| `<font>`	|规定文本的字体、字体尺寸、字体颜色。不赞成使用。请使用样式。
| `<basefont>`	|定义基准字体。不赞成使用。请使用样式。
| `<center>`	|对文本进行水平居中。不赞成使用。请使用样式。
