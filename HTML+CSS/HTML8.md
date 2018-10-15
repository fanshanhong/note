
# 图像

### `<img>` 和 `src`

```HTML
<!DOCTYPE HTML>
<html>

<body>

<p>
一幅图像：
<img src="/i/eg_mouse.jpg" width="128" height="128" />
</p>

<p>
一幅动画图像：
<img src="/i/eg_cute.gif" width="50" height="50" />
</p>

<p>请注意，插入动画图像的语法与插入普通图像的语法没有区别。</p>

</body>
</html>
```

语法:  `<img src="url"  width="100  height="100`
`<img>`标签, 然后指定属性 src  witdth height ,  属性中间用空格分隔 so easy
要在页面上显示图像，你需要使用源属性（src）。src 指 "source"。源属性的值是图像的 URL 地址。


### 替换文本属性（`Alt`）

alt 属性用来为图像定义一串预备的可替换的文本。替换文本属性的值是用户定义的。
```HTML
 <img src="boat.gif" alt="Big Boat">
```
在浏览器无法载入图像时，替换文本属性告诉读者她们失去的信息。此时，浏览器将显示这个替代性的文本而不是图像。为页面上的图像都加上替换文本属性是个好习惯，这样有助于更好的显示信息，并且对于那些使用纯文本浏览器的人来说是非常有用的。
类似于Android中的加载失败时候的占位图

### 背景图片

```HTML

<html>

<body background="/i/eg_background.jpg">

<h3>图像背景</h3>

<p>gif 和 jpg 文件均可用作 HTML 背景。</p>

<p>如果图像小于页面，图像会进行重复。</p>

</body>
</html>

```


### 排列图片
```HTML
<html>

<body>

<h2>未设置对齐方式的图像：</h2>

<p>图像 <img src ="/i/eg_cute.gif"> 在文本中</p>

<h2>已设置对齐方式的图像：</h2>

<p>图像 <img src="/i/eg_cute.gif" align="bottom"> 在文本中</p>

<p>图像 <img src ="/i/eg_cute.gif" align="middle"> 在文本中</p>

<p>图像 <img src ="/i/eg_cute.gif" align="top"> 在文本中</p>

<p>请注意，bottom 对齐方式是默认的对齐方式。</p>

</body>
</html>

```

默认是bottom对齐的


### 制作图片链接
```HTML
<html>

<body>
<p>
您也可以把图像作为链接来使用：
<a href="/example/html/lastpage.html">
<img border="0" src="/i/eg_buttonnext.gif" />
</a>
</p>

</body>
</html>
```

首先他是一个链接 , 链接的内容是一个图片
