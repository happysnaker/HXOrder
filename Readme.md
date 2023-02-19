[toc]

# XOrder

**2023-2-19 更新：这玩意大一大二写的，现在来看似乎有些过时了，并且代码写的略显青涩，但是勉强也能用。目前在求职暑期实习，没有精力重构了......如果秋招上岸了，一定要把这个项目使用 SpringCloud 重构一遍。**

PS：贴一个我简历上对这个项目的描述

> **技术选型：Vue、VueRoute、SpringBoot、SpringSecurity、JWT、Mybatis、Mysql、Redis、RabbitMQ**
>
> **项目介绍：可分为四个独立项目，前后端独立部署，包括点餐小程序前后端和后台管理系统前后端，小程序端为客户点餐主程序，支持点餐、加餐、取餐与外卖等核心功能；管理系统端用于管理员管理菜品、处理订单以及上新优惠等功能。**
>
> - 通过 Spring AOP 技术打印请求参数与响应结果，便于调试；利用 Redis 缓存首页静态数据以及频繁变更数据，利用高级数据结构 HyperLogLog 统计日活跃量。
> - 小程序端基于建造者模式生成 JWT Token，自定义拦截链对用户 Token 进行访问安全校验，将 UserID 透明地添加至请求参数中；后台管理系统端基于 SpringSecurity + JWT + VueRoute 进行安全验证与权限管理。
> - 基于策略模式在下单时提供多种策略，在高并发条件下通过预减 Redis + 内存标记 + 数据库乐观锁安全地进行库存扣减，后续任务由 MQ 分发消费者执行，业务解耦，前端轮询下单结果；在另一种策略下，使用 Redisson 分布式锁对商品加锁进行下单处理。
> - 小程序端通过利用 RabbitMq 死信队列，完成未支付订单超时自动取消，通知用户并补偿 Redis 库存；管理系统端基于观察者模式，实现订单状态改变，MQ 发送消息自动通知用户。 

对于实习与学习而言，也略微够格吧，可能还需要另一个轮子项目，可以看看：[happysnaker/HRpc: HRpc 是一款基于 Netty 的 RPC 框架，主要的功能有：服务注册、服务发现、服务订阅、心跳检测、自动重连、复用连接、负载均衡策略、SPI扩展、注解化动态代理注入等 (github.com)](https://github.com/happysnaker/HRpc)

## 前言

该项目分为小程序前后端和后台管理系统前后端，无论小程序端还是管理系统均为 SpringBoot + Vue / Wx 前后端分离项目，项目的定位是一个 Java 入门项目，在开始做此项目之前，我没有任何的前端基础，也没有使用过任何的 Java 开发框架，学习与这个项目是同步开始的，这个项目从头到尾都是一个人完成的，也没有看网课提前学习(导致了很多架构不合理，无奈重构)，也算是边学边实践了。

这个项目并未涉及到分布式相关，我的下一个学习计划就是学习分布式相关，或许到时候回来重构项目，希望你我一起努力。

- [小程序前端项目](./WxAppProject)
- [小程序后端项目](./WxSpringBootProject)
- [管理系统前端项目](./AdminSysVueProject)
- [管理系统后端项目](./AdminSpringBootProject)
- [SQL文件(不包含建库语句，项目数据库为：restaurant)](./SQLFiles)

## 更新日志

- 2022-3-15 更新，原先版本已建立 tag 0.0，更新版本 tag 为 1.0，这次更新主要更改了一下小程序端点单的流程，现在把购物车放到后端去了，点菜减菜都是在后端做的逻辑，允许同一个用户使用多台设备同时点餐，但最终只有一个能下单，下单时由前端轮询是否完成。

  然后优化了下下单时的架构，现在以策略模式的形式支持多种策略，只需在配置文件中设置策略即可。

  具体的细节放在文末更新日志(续)。

## 环境

### 微信开发者工具

下载开发者工具直接导入项目即可。

<img src="https://happysnaker-1306579962.cos.ap-nanjing.myqcloud.com/img/typora/image-20211219194322695.png" alt="image-20211219194322695" style="zoom:30%;" />

### Java开发环境

IDEA2021.2 

JDK11

MySql 8.0

### Vue开发环境

不知道，对前端不熟，自己乱搞的，前往[介绍 | vue-element-admin (panjiachen.github.io)](https://panjiachen.github.io/vue-element-admin-site/zh/guide/)自行了解。

## 成果展示

后台管理系统：[XOrder后台管理系统](http://101.34.154.119:8090/)

小程序端由于未申请企业账号不允许上线，可自行下载代码体验，部分照片：



<img src="https://happysnaker-1306579962.cos.ap-nanjing.myqcloud.com/img/typora/image-20211219195024142.png" alt="image-20211219195024142" style="zoom: 33%;" />

<img src="https://happysnaker-1306579962.cos.ap-nanjing.myqcloud.com/img/typora/image-20211219194955578.png" alt="image-20211219194955578" style="zoom:33%;" />

<img src="https://happysnaker-1306579962.cos.ap-nanjing.myqcloud.com/img/typora/image-20211219195107144.png" alt="image-20211219195107144" style="zoom:33%;" />

<img src="https://happysnaker-1306579962.cos.ap-nanjing.myqcloud.com/img/typora/image-20211219195132857.png" alt="image-20211219195132857" style="zoom:33%;" />

<img src="https://happysnaker-1306579962.cos.ap-nanjing.myqcloud.com/img/typora/image-20211219195158492.png" alt="image-20211219195158492" style="zoom:33%;" />

## 下载安装

如果需要下载的话，提一些需要注意的点吧：

- 最重要的是各个开发工具版本得兼容此项目。
- 给定的 SQL 文件好像是没有包含建库语句的，要执行 SQL 语句，首先得自己建库 restaurant。
- 小程序端的话 app.js 是所有 API 接口，在 app.js 最底部有后端地址，这个得填你自己的。
- SpringBoot 的话这个就不多说了，配置文件里服务地址还有一些要密码的配置改成你自己的。有一点要注意，生产模式下用的是 HTTPS，这个请自己配置。
- 特别注意，小程序后端 LoginService 里的小程序 ID 和密匙改成你自己的。
- Vue 端的话我当初好像一直报个啥需要 Python 环境，这个请自己百度解决，我是一点都不懂 Vue 的，当初也被折磨了好久（寄）。
- 腾讯云 COS 的话如果没有的话是不影响的，只不过管理系统端上传图片不管用了。
- 最后，原先数据库中的图片貌似很多都过期了（都是我百度乱找的），这就会导致很多图片显示不出来。而且过于复杂的菜品图片 URL 会导致菜品详情页解析 JSON 出错。

## 小程序端

### 小程序前端

#### 扫码点餐

在小程序首页点击扫码点餐，前往[草料二维码生成器 (cli.im)](https://cli.im/)生成，文本内容为 `storeId=XXX&table=XXXX`，例如：

<img src="https://happysnaker-1306579962.cos.ap-nanjing.myqcloud.com/img/typora/image-20211219195343883.png" alt="image-20211219195343883" style="zoom:33%;" />

#### 文件树

```bash
├─component  		    	# 自定义的组件
├─dist						# 引用的组件库
├─images				    # 存放静态图片
└─pages						# 主页面
    ├─address				# 收货地址页面
    ├─cancel-reason	 		# 退货原因页面 
    ├─collection			# 收藏菜品页面
    ├─dish-detail		   	# 订单详情
    ├─dish-order		   	# 点餐主页面
    ├─index					# 首页
    ├─integral 			    # 会员积分(待实现)
    ├─member				# 会员(待实现)
    ├─message				# 我的消息
    ├─order					# 订单页面
    ├─order-detail			# 订单详情
    ├─order-meal			# 点餐页面
    ├─order-ok				# 订单已确认
    ├─order-sure			# 确认订单
    └─user					# 我的
├─app.json					# 配置 tarbar，引入 dist 组件库
├─app.js					# 配置全局遍历
├─app.wxss					# 配置全局样式
```

#### 技术选型

- Html
- Css
- JavaScript

#### 鸣谢

[VanApp 轻量、可靠的小程序 UI 组件库](https://github.com/youzan/vant-weapp)

### 小程序后端

这算是我的第一个 SpringBoot 项目了，很青涩，甚至参数还得从 httpServletRequest 取

#### 文件树

```bash
├─logs
├─src
│  ├─main
│  │  ├─java
│  │  │  └─com
│  │  │      └─happysnaker 
│  │  │          ├─api  		# 引入百度地图 API 进行经纬度解析
│  │  │          ├─config		# 配置文件
│  │  │          ├─controller	# 
│  │  │          │  └─base		#
│  │  │          ├─exception	# 自定义异常
│  │  │          ├─filter		# 自定义过滤器
│  │  │          ├─mapper		# dao 层
│  │  │          ├─Observer		# 观察者
│  │  │          │  └─impl
│  │  │          ├─pojo			# POJO
│  │  │          ├─service		# 业务层
│  │  │          │  └─impl
│  │  │          └─utils		# 实用类
│  │  └─resources
│  │      ├─com
│  │      │  └─happysnaker
│  │      │      └─mapper
│  │      ├─static
│  │      └─templates
│  └─test
│      └─java
│          └─com
│              └─happysnaker
```

#### 技术选型

| 技术       | 说明             | 官网                                           |
| ---------- | ---------------- | ---------------------------------------------- |
| SpringBoot | 容器+MVC框架     | https://spring.io/projects/spring-boot         |
| MyBatis    | ORM框架          | http://www.mybatis.org/mybatis-3/zh/index.html |
| RabbitMQ   | 消息队列         | https://www.rabbitmq.com/                      |
| Redis      | 分布式缓存       | https://redis.io/                              |
| Lombok     | 简化对象封装工具 | https://github.com/rzwitserloot/lombok         |
| Swagger-UI | 文档生成工具     | https://github.com/swagger-api/swagger-ui      |

#### 项目总结

- 锻炼了项目开发、**排错能力**、**重构能力**，提高了逻辑思维能力，提升了原本菜的抠脚 **SQL 水平**，这是最基本也是最重要的内力提升了。
- Wx官方定义用户openid为敏感数据不应该在网上传输，所以我对原先的项目架构进行了重构，我没有去更改原先的架构，而是采用利用了 token 思想，让小程序带着 token 访问，定义**过滤器辅以责任链模式**，在控制层之前进行拦截验证，**透明的设置参数**，使得原先的代码无须任何改变。

​		在此，我学到了如何去生成(手动) token，jwt 形式的 token 是由 头部、载荷和签名构成的，头部是一些标识信息，如 jwt 形式、签名算法等；载荷是要承载的数据，如用户ID、过期时间、发行人等等；头部和载荷采用 base64 进行 ascii 编码，它们仍然是明文的，因此不应承载敏感数据；签名就是利用私匙对头部和载荷进行加密，这就是计网里面的 MAC(报文完整性)加密，通过对头部和载荷加密我们确定这个 token 有没有被篡改，因为私匙只有我们知道，token 生成在 utils 包下的 token 实用类生成，此外我还定义了 TokenBuilder 类，以**建造者模式**去生成 token。

​		抽象过滤器如下所示，子类继承抽象类并实现抽象方法即可，具体的 doFilter 方法由抽象类完成，子类做自己要做的即可，这利用了**模板方法**的设计模式。

```java
public abstract class AbstractFilterChain implements Filter {
    AbstractFilterChain myFilterChain;
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (isRequired((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse)) {
            if (!doFilter((HttpServletRequest)servletRequest, (HttpServletResponse)servletResponse)) {
                return;
            }
        }
        if (myFilterChain != null) {
            myFilterChain.doFilter(servletRequest, servletResponse, filterChain);
            return;
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    public abstract boolean isRequired(HttpServletRequest request, HttpServletResponse response);

    public abstract boolean doFilter(HttpServletRequest request, HttpServletResponse response);
}

```

- 静态热数据利用 redis 缓存，如首页的菜品信息、店铺信息等。
- 频繁变更数据利用 Redis 缓存，例如用户对菜品的喜欢、收藏等，Redis 初始化时读取全部 ID，利用 bitmap 做一个简单的布隆过滤器，防止缓存穿透。在本项目中，我对访问的每一个用户都维护了一个 bitmap，当用户数量增多时，必须要考虑内存问题。从理论上说，菜品的喜欢数量理应使用 redis 缓存，然后读取时以 redis 同步，但我并没有这么做，理由很简单，在我想到这一点时业务层已经完成了，我不想在去改业务层代码，我想到可以利用**代理模式**进行控制，但最终我选择了在控制层实现，每个控制层维护菜品的喜欢数量，当增减达到一定阈值时，通知观察者将它们写入。

> 我尝试利用 jdk 动态代理生成Service代理，但这样做会一直报错(xxx proxy$104 无法转化 xx)

- 下单时处理是整个项目最繁琐的地方，在这个地方，我一共重构了五次：

  - 第一次架构很朴素，符合我的思想，这里一定要注意 SELECT 要 **FOR UPDATE**，不能读版本快照：

    <img src="https://happysnaker-1306579962.cos.ap-nanjing.myqcloud.com/img/typora/image-20211214200406345.png" alt="image-20211214200406345" style="zoom: 50%;" />

  - 第一次的性能不好，因为每个是串行化的，第二次我尝试将锁细化，**即对菜品 ID 上锁而不是对整个方法上锁**，利用 `redis setnx`可以很容易的实现一个**分布式锁**（Redisson 框架），将锁细化后性能明显提升。

  - 第三次我在 SQL 层做了乐观锁，怎么做呢？其实就是在执行SQL时判断库存是否大于需要下单的数目，就像下面这样：
  
    ```sql
    UPDATE `dish` SET stock = stock + #{val} WHERE id = #{id} AND stock > #{val};
    ```
  
    当然是乐观锁就会存在 ABA 问题，不过版本对这里没有影响。SQL返回的是影响的行数，我们可以根据影响的行数判断是否插入成功，然后让SpringBoot帮我们**回滚**。现在的架构是:

    <img src="https://happysnaker-1306579962.cos.ap-nanjing.myqcloud.com/img/typora/image-20211214200958524.png" alt="image-20211214200958524" style="zoom:50%;" />

  - 好吧，明眼人都能看出来，插入订单、增加积分、添加等待队列、减少折扣次数等等可以异步去做，所以我的第四次重构就是将这些任务用**线程池去异步完成**。这个时候效率已经很优秀了，经过模拟，确实也没啥问题，不过并发量太小了，测试也没啥意义。

  - 上面的架构的问题乐观锁在并发小的时候工作很好，但是在高并发下，例如如果用户下单多个商品，一些库存足，一些不足，那么足的肯定扣了库存，不足的又会抛出异常，读写都是 update 操作，会锁定数据库，效率比较低。因此考虑引入缓存进行提前判断，先判断缓存是否充足，充足的话减缓存，最后利用数据库乐观锁重新判断一次，同时，这个时候我把线程池换成了**消息队列**，线程池和消息队列都可以实现异步，但消息队列可以实现完全解耦，更适用于分布式，现在的架构是：
  
  ![image-20211214213619441](https://happysnaker-1306579962.cos.ap-nanjing.myqcloud.com/img/typora/image-20211214213619441.png)

​				现在它已经足够优秀了，我的重构之路就到此结束了，但我们还有一个问题没有解决：**Redis中没有 key 怎么办？**这个问题没有那么简单，主要是两个问题：	

1. 考虑 A，B 同时察觉到没有缓存，都尝试更新，假设我们已经对更新操作上锁，那么可能会出现 A 更新了缓存为数据库中的 XX，然后立马扣减了缓存，此时 redis 为 XX1，这个时候 B 才姗姗来迟，B又将缓存更新数据库中的 XX，造成不可重复读。
2. 即使只有一个更改者 A 想要更新缓存，但是此时可能 MQ 中有消费者 B 正准备扣减缓存，A先入，更新为 XX，此时 redis 为 XX，而 B 马上对数据库更改了，数据库变成了 XX1，数据不一致。

其实 1 和 2 本质上是一个问题，解决1可以加一个乐观锁，在更新方法中重新判断 key 是否存在，令整个方法成为原子方法(或利用分布式锁)。

解决方法 2 则要保证更新缓存时，没有消费者，所以我们需要在 1 的原子方法内去等待消费队列为空。

- 利用MQ延迟性质与死信队列完成定时取消未支付订单，设置消息延迟时间，消息过期后被投递到死信队列，让死信消费者去取消订单。

- 利用 AOP 打印 request 与 response，方便调试。

### 未完成的任务

优惠券、会员没有实现，设计数据库都考虑到了。

emmm，写着写着才发现，购物车这玩意，应该让后端去处理数据，我全在前端做了，一大坨，包括判断折扣，处理精度问题，整麻了....如果在前端做，那么后端必须对发过来的订单进行重复的验证，麻了，写不动了，好吧我觉得前后端都要做...

没有完成真正的支付功能，个人小程序不让接入支付，涉及到金额相关的实现的没有那么严谨，甚至可能被攻击...

如果我还能记得起来这个项目的话，我会进行又一次重构。



## 管理系统端

### 管理系统前端

项目基于[mall-admin-web是一个电商后台管理系统的前端项目，基于Vue+Element实现](https://github.com/macrozheng/mall-admin-web)二次开发，对于整体的架构与部署，可以参考：[介绍 | vue-element-admin (panjiachen.github.io)](https://panjiachen.github.io/vue-element-admin-site/zh/guide/)，我基本上只修改了 src 里和 api 文件下的内容。

遇到所有部署问题，基本上都可以在[介绍 | vue-element-admin (panjiachen.github.io)](https://panjiachen.github.io/vue-element-admin-site/zh/guide/)找到解决方法。

#### 文件树

```bash
├─src
  ├──views
  ├─authority-ms   		# 权限控制
  │  ├─admin
  │  └─role
  ├─dish-ms				# 菜品模块
  │  ├─combo
  │  │  └─components
  │  └─dish
  │      └─components
  ├─home				# 首页
  ├─layout				# 整体路由布局
  │  ├─components
  │  │  └─Sidebar
  │  └─mixin
  ├─login				# 登录
  ├─marketing-ms		# 营销
  │  ├─coupon
  │  │  └─components
  │  ├─discount
  │  ├─new
  │  └─recommend
  ├─order-ms			# 订单模块
  │  └─order
  └─store-ms			# 店铺模块
     └─store 
     └─component
```

#### 技术选型

| 技术              | 说明                  | 官网                                            |
| ----------------- | --------------------- | ----------------------------------------------- |
| Vue               | 前端框架              | https://vuejs.org/                              |
| Vue-router        | 路由框架              | https://router.vuejs.org/                       |
| Vuex              | 全局状态管理框架      | https://vuex.vuejs.org/                         |
| Element           | 前端UI框架            | https://element.eleme.io/                       |
| Axios             | 前端HTTP框架          | https://github.com/axios/axios                  |
| v-charts          | 基于Echarts的图表框架 | https://v-charts.js.org/                        |
| Js-cookie         | cookie管理工具        | https://github.com/js-cookie/js-cookie          |
| nprogress         | 进度条控件            | https://github.com/rstacruz/nprogress           |
| vue-element-admin | 项目脚手架参考        | https://github.com/PanJiaChen/vue-element-admin |

### 管理系统后端

#### 文件树

```bash
├─java
│  └─com
│      └─happysnaker
│          ├─configuration	# 配置包
│          │  ├─inspecter	# 拦截器包
│          │  └─security	# SpringSecurity 相关
│          ├─controller		# 控制层
│          │  └─base
│          ├─exception		# 异常类
│          ├─mapper			# dao 层
│          ├─pojo			# 实体类层
│          ├─service		# 服务层
│          │  ├─base			
│          │  ├─impl		
│          │  ├─proxy		# 服务类代理
│          │  └─security	# SpringSecurity 相关
│          └─utils			# 实用类
└─resources
    ├─com.happysnaker.mapper
    ├─static
    └─templates
```

#### 技术选型

| 技术           | 说明             | 官网                                           |
| -------------- | ---------------- | ---------------------------------------------- |
| SpringBoot     | 容器+MVC框架     | https://spring.io/projects/spring-boot         |
| SpringSecurity | 认证和授权框架   | https://spring.io/projects/spring-security     |
| JWT            | JWT登录支持      | https://github.com/jwtk/jjwt                   |
| MyBatis        | ORM框架          | http://www.mybatis.org/mybatis-3/zh/index.html |
| RabbitMQ       | 消息队列         | https://www.rabbitmq.com/                      |
| Redis          | 分布式缓存       | https://redis.io/                              |
| Lombok         | 简化对象封装工具 | https://github.com/rzwitserloot/lombok         |
| Swagger-UI     | 文档生成工具     | https://github.com/swagger-api/swagger-ui      |
| COS            | 对象存储         |                                                |

#### 项目总结

- 这个项目基本上是一个落地的 CRUD 了，因为要做分页，所以对SQL的优化查询体会更深了点，使用 MySql 的 limit 语句可以很容易实现分页查询：

```sql
SELECT * FROM `table` LIMIT offset, size;
# offset 是偏移，size 是大小，这个语句表示选取从 offset 开始的 size 条记录
```

但是这条语句效率低下，例如 offset = 100000, size = 1，我们只想选取 1 条语句，但是 Mysql 却会顺序查询 1到 100001 条记录，然后抛弃掉前面的记录，只选取一条，这样效率十分低下，如果可以知道 offset = 100000 的主键id就好了，这样就可以利用主键的排序性快速定位：

```sql
SELECT * FROM `table` WHERE id >= 100000 LIMIT 0, 1;
```

但可惜 Mysql 的主键一般不会严格连续，即使你设置成自增也没用，因此，另一个优化是不要选取那么多列，少一点IO：

```sql
SELECT * FROM `table` WHERE id >= (
	SELECT id FROM `table` LIMIT 100000, 1
) 
LIMIT 0, 1;
```

- 利用 **SpringSecurity + JWT + BCR  + VueRouter** 做权限管理，具体思想是维护 menu、api、role、user 四张表，用户与角色关联，角色与菜单关联，菜单与api(还可以有很多，例如Get or Post)资源(控制层uri)关联，用户登陆后我们返回菜单树给前端，前端只显示这些菜单而隐蔽其他菜单，同时用户发起的访问 API 必须要动态鉴权，看看是否有权限访问...JWT框架相较于我们自己写的生成token，无非就是更严谨了些，携带参数更丰富了些，我们可以直接在 token 上设置过期时间、用户ID，从而无须使用 redis，这就是去中心化的思想，不过缺点是无法改变过期时间等信息(貌似我们自己也能实现....)。
- 利用 AOP 实现日志打印。利用AOP维护与小程序端的缓存一致性。
- 利用 **观察者模式** 实现对订单类变更的消息通知，观察者观察订单状态，对更改状态的订单发送消息通知用户，发送消息利用了**线程池**去异步处理。

> 嗯，没啥了，剩下的就是磨练基本功了。



#### 未完成的任务

嗯，还有很多细节没有实现，很多也偷懒了，直接在数据库写死了....

问题不大，毕竟主要目的是学习。

## 总结

这一个多月下来，基本上对 Java 开发、项目开发有了个清晰的认识，对主流的开发框架也有了更进一步的理解，项目中基本上都用上了，用过一番之后，接下来就得去看看它们的底层实现了。



## 更新日志（续）

### v0.0 --> v1.0

- [x] 喜欢数量由 redis 缓存，及时反应给用户，降低 Redis Key 过期时间。
- [x] 购物车由后端做，用户 ID + 消费类型 + 店铺 + 桌号 构成唯一标识，存储在内存中，超过一小时未操作自动过期，同一用户 ID + 消费类型 + 店铺 + 桌号 相同时，可在不同设备上同步购物车。
- [x] 点餐时，会查询该 店铺+桌号 标识上是否有一个已存在的订单，如果有则提醒用户桌位忙或者提醒用户点击继续加餐。
- [x] 下单时，后端采用消息队列，由前端轮询，小程序将显示正在排队中。
- [x] 前端登陆后实现自动刷新，不在需要用户手动下拉。
- [x] 支持悲观锁，下单时锁商品 ID，可配置是否开启秒杀模式或乐观模式或悲观模式，这里可以用策略模式。
- [ ] 支持以 POST 方法修改策略。
- [ ] 修复店长推荐列表中显示的 BUG。
- [ ] 管理系统前端由于未清除缓存导致权限列表显示 BUG。
- [x] 管理系统前端处理待支付时的金额错误 BUG。

> 以上都是我更新之前制定的目标，基本数貌似都完成了，还有些计划没能实现，这是因为我实在太懒了，回过头看，这个项目结构确实不是那么清晰，改了两天代码，把最主要的逻辑实现后其他的就懒的改了。
>
> 注意，这个版本没经历过具体的测试，因此可能会有一些隐藏的 BUG...
>
> 再说一句，其实如果在后端做购物车的话，扣减库存就有两种逻辑，一种是用户点一个菜扣一个菜，另一种是最后确认下单时再减库存，后者在正常情况下性能要明显优于前者，但在高并发情况下后者发生冲突的概率较大，此时不如采用第一种悲观的策略。









































































































