package com.happysnaker.strategy.impl;

import com.happysnaker.config.OrderRabbitMqConfig;
import com.happysnaker.config.RedisCacheManager;
import com.happysnaker.exception.OrderAddException;
import com.happysnaker.exception.ReadWriterLockException;
import com.happysnaker.pojo.Order;
import com.happysnaker.pojo.OrderMessage;
import com.happysnaker.service.OrderService;
import com.happysnaker.utils.VerifyUtils;
import io.swagger.models.auth.In;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>高并发秒杀场景下的架构</p>
 * <P>首先提前用 Redis 缓存菜品库存，下单时先查询 Redis 缓存是否足够，如果足够则扣减 Redis 缓存，一旦扣减成功，立即返回。
 * 如果不足则进行内存标记，再次访问直接返回，
 * 后续的工作交由消息队列做，在消息队列中，利用数据库乐观锁扣减库存，完成后续工作，如果消息队列工作失败，除了数据库回滚之外，他还必须对 Redis 中架构进行补偿</P>
 * <p>在高并发期间，管理员不得更改菜品库存，这可能造成缓存不一致，考虑可以使用一个定时任务或者 api 进行缓存预热，并清理内存标记，暂未实现</p>
 * @author Happysnaker
 * @description
 * @date 2022/3/14
 * @email happysnaker@foxmail.com
 */
@Service
@Transactional(rollbackFor = Exception.class)
@Configuration
@EnableRabbit
public class HighConcurrencyPlaceOrderStrategy extends AbstractPlaceOrderStrategy {
    /**
     * 内存标记，在高并发期间不得清除，高并发后需要手动清除，这里未实现清除逻辑
     */
    private Map<Integer, BitSet> map = new HashMap<>();

    /**
     * 刷新 Redis 缓存，准备进入高并发模式
     */
    @Override
    public void initMethod() {
        System.out.println("执行初始化方法");
        for (Integer sid : storeMapper.queryAllStoreId()) {
            redisManager.flushRedisDishStockCache(dishMapper.queryDishInfo(sid), sid);
        }
    }

    @Override
    public Map doPlaceOrder(Order order) throws Exception {
        return addUserOrder(order);
    }

    public boolean checkStock(Map<Integer, Integer> m, int storeId) {
        try {
            // 被标记了，直接返回
//            for (var it : m.entrySet()) {
//                int id = it.getKey();
//                if (map.get(storeId) != null && map.get(storeId).get(id)) {
//                    return false;
//                }
//            }


            for (var it : m.entrySet()) {
                int id = it.getKey(), stock = it.getValue();
                int nowStock = (int) redisManager.getForHash(RedisCacheManager.getDishStockCacheKey(storeId), id);
                if (nowStock == 0) {
                    // 内存标记，这里暂时没实现删除的逻辑，因此这里暂时不实现
//                    map.putIfAbsent(storeId, new BitSet());
//                    map.get(storeId).set(id);
                }
                if (nowStock < stock) {
                    return false;
                }
            }
            return true;
        } catch (NullPointerException e) {
            redisManager.flushRedisDishStockCache(
                    dishMapper.queryDishInfo(storeId), storeId);
            return checkStock(m, storeId);
        }
    }

    /**
     * <p>这是原先的代码，基本上没有改变</p>
     * 添加订单要做的事有<br/>
     * <ul>
     *  <li> 计算订单ID<br/></li>
     *  <li>扣减库存，扣减库存必须保证库存充足<br/></li>
     * <li> 增加用户使用的折扣数目<br/></li>
     * <li>增加用户会员积分<br/></li>
     * <li> 扣减优惠券，目前不支持<br/></li>
     * <li>向 order 表 和 order_dish 表写订单数据<br/></li>
     *  <li>增加对应菜品销量<br/></li>
     * <li> 将菜品写入等待队列，以便计算新用户下单后需要等待的时常<br/></li>
     * </ul>
     */
    public Map addUserOrder(Order order) throws OrderAddException, ReadWriterLockException {
        if (order == null) {
            return null;
        }
        String userId = order.getUserId();

        if (!order.getIsNew()) {
            order.setIsNew(false);
            //这是一个已经在表中存在的订单，由用户继续加餐而来
            handleOldOrder(order);
            return null;
        }
        order.setIsNew(true);

        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        order.setCreateTime(timestamp);

        //生成订单编号
        String orderId = UUID.randomUUID().toString();


        order.setId(orderId);
        order.setUserId(userId);


        // 测试用，删除 key 以便更新 redis
        redis.delete(RedisCacheManager.getDishStockCacheKey(order.getStoreId()));

        if (order.getConsumeType() == 2) {
            // 以当前时间戳生产取餐码，确保取餐吗唯一
            String code = VerifyUtils.BaseConversion(System.currentTimeMillis(), 32);
            order.setFetchMealCode(code);
        }


        //m 保存的是菜品ID与要下单的数量，主要是将套餐中的每个菜品与单点菜品合并
        Map<Integer, Integer> dishNumMap = getDishNumMap(order.getDishOrders());
        if (!checkStock(dishNumMap, order.getStoreId())) {
            throw new OrderAddException("库存不足");
        }
        // 扣减库存
        boolean hasDeduction = false;
        try {
            // 扣减 redis，发送消息，数据库层面会进行乐观锁判断
            for (var it : dishNumMap.entrySet()) {
                int id = it.getKey(), stock = it.getValue();
                redis.opsForHash().increment(RedisCacheManager.getDishStockCacheKey(order.getStoreId()), id, -stock);
            }
            hasDeduction = true;
            // 订单算是生成成功，产生随机支付单号，发起支付
            order.setPayId(UUID.randomUUID().toString().replace("-", ""));
            OrderMessage om = new OrderMessage(dishNumMap, order);

            System.out.println("扣减库存成功，上锁，发送消息到消息队列！");

            rabbit.convertAndSend(OrderRabbitMqConfig.ORDER_ADD_ROUTEING_KEY, om);

        } catch (NullPointerException e) {
            // 缓存不存在，不能更新，高并发下更新会造成缓存不一致
            e.printStackTrace();
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            if (hasDeduction) {
                // 如果已经减了 redis 缓存，那么要补偿
                dishNumMap.put(-1, order.getStoreId());
                rabbit.convertAndSend(OrderRabbitMqConfig.ROLL_BACK_STOCK_ROUTEING_KEY, dishNumMap);
            }
            throw e;
        }
        // 返回支付单号给前端
        Map map = new HashMap(2);
        map.put("payId", order.getPayId());
        map.put("orderId", order.getId());
        return map;
    }

    /**
     * 处理旧订单
     *
     * @param order
     * @throws OrderAddException
     */
    public void handleOldOrder(Order order) throws OrderAddException, ReadWriterLockException {
        // 筛选出添加的菜
        List<Map<String, Object>> dishOrders = order.getDishOrders().stream().filter((item -> {
            return (Boolean) item.getOrDefault("isAdd", false);
        })).collect(Collectors.toList());
        Map<Integer, Integer> dishNumMap = getDishNumMap(dishOrders);

        order.setDishOrders(dishOrders);

        if (!checkStock(dishNumMap, order.getStoreId())) {
            throw new OrderAddException("库存不足");
        }
        // 扣减库存
        boolean hasDeduction = false;
        try {
            // 扣减库存
            for (var it : dishNumMap.entrySet()) {
                int id = it.getKey(), stock = it.getValue();
                redis.opsForHash().increment(
                        RedisCacheManager.getDishStockCacheKey(
                                order.getStoreId()), id, -stock);
            }
            hasDeduction = true;
            // 库存扣减成功，订单算完成，发布消息
            order.setOrderType(OrderService.CONFIRMING_STATUS);
            OrderMessage om = new OrderMessage(dishNumMap, order);

            rabbit.convertAndSend(OrderRabbitMqConfig.ORDER_ADD_ROUTEING_KEY, om);
        } catch (NullPointerException e) {
            // 缓存不存在
            e.printStackTrace();
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            if (hasDeduction) {
                dishNumMap.put(-1, order.getStoreId());
                rabbit.convertAndSend(OrderRabbitMqConfig.ROLL_BACK_STOCK_ROUTEING_KEY, dishNumMap);
            }
            throw e;
        }
    }
}
