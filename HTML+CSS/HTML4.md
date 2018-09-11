# HTML 段落

段落是通过 `<p>` 标签定义的。

```HTML
<p>This is a paragraph</p>
<p>This is another paragraph</p>
```

注释：浏览器会自动地在段落的前后添加空行。（`<p>` 是块级元素）
提示：使用空的段落标记` <p></p>` 去插入一个空行是个坏习惯。用` <br />` 标签代替它！

```HTML
<p>This is a paragraph
<p>This is another paragraph
```

注释：在未来的 HTML 版本中，不允许省略结束标签。
提示：通过结束标签来关闭 HTML 是一种经得起未来考验的 HTML 编写方法。清楚地标记某个元素在何处开始，并在何处结束，不论对您还是对浏览器来说，都会使代码更容易理解。


### HTML 折行
如果希望在不产生新段落的情况下进行换行（新行），请使用` <br />` 标签：

```HTML
<p>This is<br />a para<br />graph with line breaks</p>
```

请使用`<br />` 不要使用`<br>`


### HTML 输出 - 有用的提示
我们无法确定 HTML 被显示的确切效果。屏幕的大小，以及对窗口的调整都可能导致不同的结果。

对于 HTML，您无法通过在 HTML 代码中添加额外的空格或换行来改变输出的效果。

**当显示页面时，浏览器会移除源代码中多余的空格和空行。所有连续的空格或空行都会被算作一个空格需要注意的是，HTML 代码中的所有连续的空行（换行）也被显示为一个空格**



# HTML 样式
---


style 属性用于改变 HTML 元素的样式. style属性 提供了一种改变所有 HTML 元素的样式的通用方法.
样式是 HTML 4 引入的，它是一种新的首选的改变 HTML 元素样式的方式。


废弃的标签和属性

| 标签                        | 描述  |
|---------------------------- |----- |
| `<center>`                  |定义居中的内容|
| `<font> <basefont>`         |  定义 HTML 字体 |
| `<s> 和 <strike>`           |  定义删除线文本 |
| `<u>`                       |   定义下划线文本 |


| 属性                        | 描述  |
|---------------------------- |----- |
| align                       |文本的对齐方式|
| bgcolor                     |  背景色|
| color                       |  文本颜色 |

对于以上这些标签和属性：请使用样式代替！



### 背景颜色

```HTML
<html>

<body style="background-color:yellow">
<h2 style="background-color:red">This is a heading</h2>
<p style="background-color:green">This is a paragraph.</p>
</body>

</html>
```

### 字体、颜色和尺寸
font-family、color 以及 font-size 属性分别定义元素中文本的字体系列、颜色和字体尺寸
style中的多个属性, 用逗号分隔

```HTML
<html>

<body>
<h1 style="font-family:verdana">A heading</h1>
<p style="font-family:arial,fantasy;color:red;font-size:20px;">A paragraph.</p>
</body>

</html>

```

#### font-family
font-family 规定元素的字体系列。

font-family 可以把多个字体名称作为一个“回退”系统来保存。如果浏览器不支持第一个字体，则会尝试下一个。也就是说，font-family 属性的值是用于某个元素的字体族名称或/及类族名称的一个优先表。浏览器会使用它可识别的第一个值。

有两种类型的字体系列名称：

指定的系列名称：具体字体的名称，比如："times"、"courier"、"arial"。
通常字体系列名称：比如："serif"、"sans-serif"、"cursive"、"fantasy"、"monospace"

提示：使用逗号分割每个值，并始终提供一个类族名称作为最后的选择。

注意: 如果字体名称包含空格，它必须加上引号。在HTML中使用"style"属性时，必须使用单引号。

注意：使用某种特定的字体系列（Geneva）完全取决于用户机器上该字体系列是否可用；这个属性没有指示任何字体下载。因此，强烈推荐使用一个通用字体系列名作为后路。

#### color
提示：W3C 的 HTML 4.0 标准仅支持 16 种颜色名，它们是：aqua、black、blue、fuchsia、gray、green、lime、maroon、navy、olive、purple、red、silver、teal、white、yellow。

如果使用其它颜色的话，就应该使用十六进制的颜色值。

###  文本对齐
text-align 属性规定了元素中文本的水平对齐方式：
```HTML
<html>

<body>
<h1 style="text-align:center">This is a heading</h1>
<p>The heading above is aligned to the center of this page.</p>
</body>

</html>
```

#### text-align
text-align 属性规定元素中的文本的水平对齐方式。

该属性通过指定行框与哪个点对齐，从而设置块级元素内文本的水平对齐方式。通过允许用户代理调整行内容中字母和字之间的间隔，可以支持值

| 值    | 描述
| :---:  | ---:
| left   |   把文本排列到左边。默认值：由浏览器决定。|
| right  |   把文本排列到右边。 |
| center   | 把文本排列到中间。  |
| justify   |  实现两端对齐文本效果。 |
| inherit   | 规定应该从父元素继承 text-align 属性的值。  |

注释：任何的版本的 Internet Explorer （包括 IE8）都不支持属性值 "inherit"。
