---
title: Flutter绘制流程

date: 2019-03-18

categories: 
   - Flutter

tags: 
   - Flutter 


description: ​
---


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/UI_flow.jpg)

从上图我们可以看到，更进一步描述了开始的渲染流程图，用户输入是驱动视图更新的信号，如滑动屏幕等。然后会触发动画进度更新，框架开始build抽象视图数据，在之后，视图会进行布局、绘制、合成（渲染过程的三个步骤），最后进行光栅化处理把数据生成一个个真正的像素填充数据。在Flutter中，构建视图数据结构、布局、绘制、合成、与Engine的数据同步和通信放到了Framework层，而光栅化则放在了Engine层中。

视图数据结构：

上面说到UI线程使用Dart来构建抽象的视图结构，无论是比较底层的框架，还是上层的应用代码，在向绘制引擎提供视图数据时，都需要一份结构化的视图数据，类似抽象语法树，也就是上面所讲到的 Layer Tree，Flutter的视图数据抽象分为3部分，分别是Widget、Element、RenderObject。

Widget

Widget里面存储了一个视图的配置信息，包括布局、属性等。它是一份轻量的数据结构，在构建时是结构树，它不参与直接的绘制，所以说Widget仅仅是配置文件，Flutter团队对它做了优化，频繁的创建/销毁它们，都不会存在明显的性能问题。

Element

Element是Widget的抽象，当一个Widget首次被创建的时候，那么这个Widget会通过Widget.createElement，创建一个element，挂载到Element Tree遍历视图树。在attachRootWidget函数中，把 widget交给了 RenderObjectToWidgetAdapter这座桥梁，Element创建的同时还持有 Widget和 RenderObject的引用。构建系统通过遍历Element Tree来创建RenderObject，每一个Element都具有一个唯一的key，当触发视图更新时，只会更新标记的需变化的Element。类似react中setState后虚拟dom树的更新。

RenderObject

RenderObject作为UI视图的描述方式，其中含有4个重用的属性和方法，

constraints： 从 parent 传递过来的约束。
parentData： 这里面携带的是 parent 渲染 child 的时候所用到的数据。
performLayout()：此方法用于布局所有的 child。
paint()：这个方法用于绘制自己或者 child。

在 RenderObject树中会发生 Layout、Paint的绘制事件（下面会具体讲到），大部分绘图性能优化发生在这里，RenderObject Tree构建为Canvas的所需描述数据，加入到 Layer Tree中，最终在Flutter Engine中进行视图合成并光栅化交给GPU。

接下来看下Flutter Widget渲染的三个阶段：

1. Layout（布局的计算）：确定每个子widget大小和在屏幕中的位置。
2. Paint（视图的绘制）：为每个子widget提供canvas，让他们绘制自己。
3. Composite（合成）：所有widget合成到一起，交给GPU处理。

Layout 布局

父控件(parent)将布局约束传递给子控件(child)，父控件通过传递Containers参数，告诉子控件自己的大小（布局约束），以此来决定子控件的位置。

子控件将布局详情上传给父控件，并继续向下约束子控件，子控件的位置不存储在自己的容器（布局详情）中，而是存储在自己的parentData字段里，所以当他的位置发生变化时，并不需要重新布局或绘制。

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/contrains.jpg)

例如：parent会给child一个约束，最大宽度500px(布局约束)、最大高度500px(布局约束)，child会说我只用100px(布局详情)，并将其传递给parent，parent会继续向上传递，直到root widget为止。所以布局约束数据的传递顺序是自上而下，和web一样，布局约束条件和布局详情都取决于盒子模型协议和滑动布局协议。




性能优化（布局）

在上面的布局过程中，视图会不断更新，也就不断的触发布局和绘制，这会很损耗性能，所以这里也就到了之前说的大部分绘图性能优化的发生地方。Flutter可以在某些节点设置布局边界 Relayout boundary（Paint过程同样可以设置 Repaint boundary 进行优化），需要开发人员自己设置，边界内的控件发生重新布局或绘制时，不会影响边界外的控件。


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/relayout_boundary.jpg)

Paint 绘制

布局完成后，每个节点就会有各自的位置和大小，然后Flutter会把所有Widget绘制到不同的图层上

![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/repaint1.jpg)

以上图为例，在进行绘制时会自上而下，先绘制自身，然后向下绘制子节点，原本会统一绘制在绿色图层上，当绘制到节点“4”时，由于节点“4”可能是视频，需要单独占据一个图层（黄色图层），这样就会导致节点“2”的前景部分需要重绘，而影响到了后面的节点“6”一起重绘，占据到蓝色图层。



性能优化（绘制）

为避免这种情况，Flutter提供了重绘边界 Repaint boundary，设置了重绘边界后，Flutter会强制切换到新的图层，避免之间的相互影响，节点“6”会换到红色图层中。


![](https://cdn.jsdelivr.net/gh/fanshanhong/note-image/repaint2.jpg)


---


在Flutter中和Widgets一起协同工作的还有另外两个伙伴：Elements和RenderObjects；由于它们都是有着树形结构，所以经常会称它们为三棵树。

* Widget：Widget是Flutter的核心部分，是用户界面的不可变描述。做Flutter开发接触最多的就是Widget，可以说Widget撑起了Flutter的半边天；
* Element：Element是实例化的 Widget 对象，通过 Widget 的 createElement() 方法，是在特定位置使用 Widget配置数据生成；
* RenderObject：用于应用界面的布局和绘制，保存了元素的大小，布局等信息；

```dart
class ThreeTree extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      color: Colors.red,
      child: Container(color: Colors.blue)
    );
  }
}

```

上面这个例子很简单，它由三个Widget组成：ThreeTree、Container、Container。那么当Flutter的runApp()方法被调用时会发生什么呢？
当runApp()被调用时，第一时间会在后台发生以下事件：

1. Flutter会构建包含这三个Widget的Widgets树；
2. Flutter遍历Widget树，然后根据其中的Widget调用createElement()来创建相应的Element对象，最后将这些对象组建成Element树；
接下来会创建第三个树，这个树中包含了与Widget对应的Element通过createRenderObject()创建的RenderObject；



作者：CrazyCodeBoy
链接：https://juejin.cn/post/6916113193207070734
来源：掘金
著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。