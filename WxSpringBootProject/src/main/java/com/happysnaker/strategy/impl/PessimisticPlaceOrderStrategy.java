package com.happysnaker.strategy.impl;

import com.happysnaker.config.OrderRabbitMqConfig;
import com.happysnaker.exception.OrderAddException;
import com.happysnaker.exception.ReadWriterLockException;
import com.happysnaker.pojo.Order;
import com.happysnaker.pojo.OrderMessage;
import com.happysnaker.service.OrderService;
import com.happysnaker.utils.VerifyUtils;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 悲观扣减库存，适用于并发情况较大的情况
 * @author Happysnaker
 * @description
 * @date 2022/3/15
 * @email happysnaker@foxmail.com
 */
@Component
@Transactional(rollbackFor = Exception.class)
@Configuration
@EnableRabbit
public class PessimisticPlaceOrderStrategy extends AbstractPlaceOrderStrategy {

    @Override
    public Map doPlaceOrder(Order order) throws Exception {
        return addUserOrder(order);
    }

    @Autowired
    RedissonClient redisson;

    private synchronized RLock getLock(int did, int sid) {
        long key = (did << 32) | sid;
        return redisson.getLock(String.valueOf(key));
    }

    /**
     * <p>这是原先的代码，增加了检查扣减库存的逻辑</p>
     * <p>在单机模式下，如果查询加了 FOR UPDATE 语句，根本不需要对商品 ID 上锁，这是因为我们标注了此方法开启事务，而数据库查询库存语句被标注为 FOR UPDATE，这意味着查询也会上写锁，而在同一个事务内锁是不会被释放的，其他事务都无法获取到锁</p>
     * <p>采用 FOR UPDATE 是在数据库层面做悲观锁，这里查询单个菜品库存没有加 FOR UPDATE，因此我们需要自己加锁</p>
     * <p>在多机模式下，需要引入 redisson 分布式锁</p>
     */
    @Transactional(rollbackFor = Exception.class)
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



        if (order.getConsumeType() == 2) {
            // 以当前时间戳生产取餐码，确保取餐吗唯一
            String code = VerifyUtils.BaseConversion(System.currentTimeMillis(), 32);
            order.setFetchMealCode(code);
        }


        //dishNumMap 保存的是菜品ID与要下单的数量，主要是将套餐中的每个菜品与单点菜品合并
        Map<Integer, Integer> dishNumMap = getDishNumMap(order.getDishOrders());

        try {
            // 上锁 - 检查 - 扣减 - 释放
            boolean check = true;
            for (Map.Entry<Integer, Integer> it : dishNumMap.entrySet()) {
                getLock(it.getKey(), order.getStoreId()).lock();
                Integer v = dishMapper.getTheDishInventory(order.getStoreId(), it.getKey());
                if (v == null || v < it.getValue()) {
                    // 检查失败
                    check = false;
                }
            }
            if (!check) {
                throw new OrderAddException("库存不足");
            }
            for (Map.Entry<Integer, Integer> it : dishNumMap.entrySet()) {
                dishMapper.optimisticDeductInventory(order.getStoreId(), it.getKey(), it.getValue());
            }
        } finally {
            for (Map.Entry<Integer, Integer> it : dishNumMap.entrySet()) {
                getLock(it.getKey(), order.getStoreId()).unlock();
            }
        }
        
        try {
            // 数据库层面会进行乐观锁判断
            // 订单算是生成成功，产生随机支付单号，发起支付
            order.setPayId(UUID.randomUUID().toString().replace("-", ""));
            // dishStockMap 设置为 null
            OrderMessage om = new OrderMessage(null, order);

            rabbit.convertAndSend(OrderRabbitMqConfig.ORDER_ADD_ROUTEING_KEY, om);

        } catch (Exception e) {
            e.printStackTrace();
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

        try {
            // 上锁 - 检查 - 扣减 - 释放
            boolean check = true;
            for (Map.Entry<Integer, Integer> it : dishNumMap.entrySet()) {
                getLock(it.getKey(), order.getStoreId()).lock();
                Integer v = dishMapper.getTheDishInventory(order.getStoreId(), it.getKey());
                if (v == null || v < it.getValue()) {
                    // 检查失败
                    check = false;
                }
            }
            if (!check) {
                throw new OrderAddException("库存不足");
            }
            for (Map.Entry<Integer, Integer> it : dishNumMap.entrySet()) {
                dishMapper.optimisticDeductInventory(order.getStoreId(), it.getKey(), it.getValue());
            }
        } finally {
            for (Map.Entry<Integer, Integer> it : dishNumMap.entrySet()) {
                getLock(it.getKey(), order.getStoreId()).unlock();
            }
        }

        order.setDishOrders(dishOrders);

        // 扣减库存
        boolean hasDeduction = false;
        try {
            // 库存扣减成功，订单算完成，发布消息
            order.setOrderType(OrderService.CONFIRMING_STATUS);
            OrderMessage om = new OrderMessage(null, order);

            rabbit.convertAndSend(OrderRabbitMqConfig.ORDER_ADD_ROUTEING_KEY, om);
        }  catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }


}
