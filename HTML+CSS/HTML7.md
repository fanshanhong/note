
# HTML 链接

超链接可以是一个字，一个词，或者一组词，也可以是一幅图像，您可以点击这些内容来跳转到新的文档或者当前文档中的某个部分。

当您把鼠标指针移动到网页中的某个链接上时，箭头会变为一只小手。

我们通过使用 `<a>` 标签在 HTML 中创建链接。

有两种使用 `<a>` 标签的方式：

通过使用 href 属性 - 创建指向另一个文档的链接
通过使用 name 属性 - 创建文档内的书签

链接语法
```HTML
<a href="www.baidu.com" > this is a link </a>
```

提示："链接文本" 不必一定是文本。图片或其他 HTML 元素都可以成为链接。


```HTML
<html>

<body>
<p>
您也可以使用图像来作链接：
<a href="/example/html/lastpage.html">
<img border="0" src="/i/eg_buttonnext.gif" />
</a>
</p>

</body>
</html>
```

```HTML
<p><a href="#more_examples">可以在本页底端找到更多实例</a></p>
```

```HTML
<div class="example">
<h2><a id="more_examples">更多实例</a></h2>
<dl>
<dt><a target="_blank" href="/tiy/t.asp?f=html_link_target">在新的浏览器窗口打开链接</a></dt>
<dd>本例演示如何在新窗口打开一个页面，这样的话访问者就无需离开你的站点了。</dd>
</dl>
</div>

```

也可以通过href中指定 `#more_examples` 指定本页面中某一部分的链接. 点击后直接跳转到 id=more_examples 的地方


### HTML 链接 - target 属性
 通过target属性, 可以指定被链接的文档在何处显示
 下面的这行会在新窗口(新标签页)打开文档：

 ```HTML
 <a href="www.baidu.com"  target="_blank"> this is a link </a>


 ```



### HTML 链接 - name 属性

name 属性规定锚（anchor）的名称。
您可以使用 name 属性创建 HTML 页面中的书签。
书签不会以任何特殊方式显示，它对读者是不可见的。
当使用命名锚（named anchors）时，我们可以创建直接跳至该命名锚（比如页面中某个小节）的链接，这样使用者就无需不停地滚动页面来寻找他们需要的信息了。

```HTML
<a name="label">锚（显示在页面上的文本）</a>

```

提示：锚的名称可以是任何你喜欢的名字。

提示：您可以使用 id 属性来替代 name 属性，命名锚同样有效。


#### 实例

首先，我们在 HTML 文档中对锚进行命名（创建一个书签）：  这里一定要用标签`<a>`
```HTML
<a name='tip'>提示的锚点</a>
```
然后, 我们在同一个文档中创建指向该锚的链接：  这里一定要用 井号(#) 加 name  或者用  # 加 id也可以
```HTML
<a href="#tip" 跳转>过去看看 </a>
```

您也可以在其他页面中创建指向该锚的链接：  就是在链接后面加 #  加 name/id
```HTML
<a href="http://www.w3school.com.cn/html/html_links.asp#tips">有用的提示</a>


```


#### 有用的提示

注释：请始终将正斜杠添加到子文件夹。假如这样书写链接：href="http://www.w3school.com.cn/html"，就会向服务器产生两次 HTTP 请求。这是因为服务器会添加正斜杠到这个地址，然后创建一个新的请求，就像这样：href="http://www.w3school.com.cn/html/"。

提示：命名锚经常用于在大型文档开始位置上创建目录。可以为每个章节赋予一个命名锚，然后把链接到这些锚的链接放到文档的上部。如果您经常访问百度百科，您会发现其中几乎每个词条都采用这样的导航方式。

提示：假如浏览器找不到已定义的命名锚，那么就会定位到文档的顶端。不会有错误发生。

哦了..
