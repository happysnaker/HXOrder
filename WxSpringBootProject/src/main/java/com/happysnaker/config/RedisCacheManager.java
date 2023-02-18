package com.happysnaker.config;

import com.happysnaker.pojo.Dish;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * REDIS缓存管理器，该类维护着一系列 redis 参数以及初始化任务和定时任务
 *
 * @author Happysnaker
 * @description
 * @date 2021/12/11
 * @email happysnaker@foxmail.com
 */
@Component
public class RedisCacheManager {
    @Qualifier("myRedisTemplate")
    @Autowired
    private RedisTemplate redis;

    /**
     * <p>前缀 A 表示这属于小程序端的 key，key 中格式包含了 key 的数据类型</p>
     * <p>首页菜品静态数据的缓存 key</p>
     */
    public static final String INDEX_DISH_INFO_CACHE_KEY = "A-redis-kv:indexDishInfo-key";
    /**
     * 店铺静态数据的缓存 key
     */
    public static final String STORE_CACHE_KEY = "A-redis-kv:stores-key";
    /**
     * 菜品喜欢数目，这是一个 Hash-Key
     *
     */
    public static final String DISH_LIKE_NUM_CACHE_KEY = "A-redis-hash:dish-like-num-key";
    /**
     * 菜品制作时间缓存，Hash-Key
     */
    public static final String DISH_MAKE_TIME_CACHE_KEY = "A-redis-hash:dish-make-time-key";
    /**
     * 菜品等待队列缓存
     */
    public static final String DISH_WAITING_QUEUE_KEY = "A-redis-list:dish-queue-key";
    /**
     * 标识某桌位上是否存在一个订单
     */
    public static final String ORDER_STATUS_KEY = "A-redis-bit:order-status-key";

    /**
     * 前缀 B 表示这属于后台的 key
     */
    public static final String COMBO_PUBLISH_STATUS_KEY = "B-redis-bitmap:combo-publish-key";
    public static final String DISH_PUBLISH_STATUS_KEY = "B-redis-bitmap:dish-publish-key";
    public static final String DISH_RECOMMEND_STATUS_KEY = "B-redis-bitmap:dish-recom-key";
    public static final String DISH_NEW_STATUS_KEY = "B-redis-bitmap:dish-new-key";
    public static final String STORE_WORKING_STATUS_KEY = "B-redis-bitmap:store-enable-key";
    public static final String STORE_SUP_TAKEOUT_STATUS_KEY = "B-redis-bitmap:store-tot-key";

    public static String getOrderMessageCacheKey(String mid) {
        return "A-redis-kv:order-msg-key?msgId=" + mid;
    }


    public static String getDishStockCacheKey(int storeId) {
        return "A-redis-hash:dish-stock-key?storeId=" + storeId;
    }

    public static String getIpCacheKey(String ip) {
        return "A-redis-kv:ip-cache?ip=" + ip;
    }

    public static String getTokenCacheKey(String token) {
        return "A-redis-kv:token-cache?tk=" + token;
    }

    public static String getUserLikeDishCacheKey(String userId) {
        return "A-redis-bitmap:user-like-dish-cache?uid=" + userId;
    }

    public static String getUserCollectedDishCacheKey(String userId) {
        return "A-redis-bitmap:user-collected-dish-cache?uid=" + userId;
    }

    public static String getUserWillBuyDishCacheKey(String userId) {
        return "A-redis-bitmap:user-will-buy-dish-cache?uid=" + userId;
    }

    public static String getUserCollectedStoreCacheKey(String userId) {
        return "A-redis-bitmap:user-collected-store-cache?uid=" + userId;
    }

    public static Date getTodayDate() {
        Date date = new Date(System.currentTimeMillis());
        date.setHours(0);
        date.setMinutes(0);
        date.setSeconds(0);
        return date;
    }

    /**
     * 获取今日 0 点的 redisKey，存储每日的销量
     */
    public static String getTodayDateKey() {
        return "redis-kv:dish-sale-log?data=" + getTodayDate().toString();
    }

    /**
     * 默认的过期时间为 1 小时，适合缓存长时间不变的数据
     */
    private static final long DEFAULT_EXPIRATION_TIME_SECONDS = 60 * 60;

    public boolean isKeyExpired(String key) {
        return redis.opsForValue().getOperations().getExpire(key) <= 0;
    }

    public boolean hasKey(String key) {
        return key != null && redis.hasKey(key) && !isKeyExpired(key);
    }


    public boolean getBit(String key, long offset) {
        return redis.opsForValue().getBit(key, offset);
    }

    public int getBit(String key, int offset) {
        return redis.opsForValue().getBit(key, offset) ? 1 : 0;
    }

    public void setBit(String key, long offset, boolean val) {
        redis.opsForValue().setBit(key, offset, val);
    }

    public void setBit(String key, long offset, int val) {
        redis.opsForValue().setBit(key, offset, val != 0);
    }

    public void addIfAbsentForValue(String key, Object val, long timeout, TimeUnit unit) {
        if (!hasKey(key)) {
            redis.opsForValue().set(key, val);
            redis.expire(key, timeout, unit);
        }
    }

    public void addIfAbsentForValue(String key, Object val) {
        this.addIfAbsentForValue(key, val, DEFAULT_EXPIRATION_TIME_SECONDS, TimeUnit.SECONDS);
    }

    public Object getForValue(String key) {
        return redis.opsForValue().get(key);
    }

    public void setForHash(String key1, Object key2, Object val) {
        redis.opsForHash().put(key1, key2, val);
    }

    public Object getForHash(String key1, Object key2) {
        return redis.opsForHash().get(key1, key2);
    }

    /**
     * 初始化 Redis bitmap 缓存，过期时间将依赖于默认的过期时间
     *
     * @param allIds 所有的 ID
     * @param ids    将要设置为 true 的 id
     * @param key    键
     */
    public void initRedisUserMarkedCache(List<Integer> allIds, List<Integer> ids, String key) {
        redis.delete(key);
        for (Integer allId : allIds) {
            if (ids.contains(allId)) {
                setBit(key, allId, true);
            } else {
                setBit(key, allId, false);
            }
        }
        redis.expire(key, DEFAULT_EXPIRATION_TIME_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 初始化菜品制作时间缓存，将使用默认的过期时间
     *
     * @param dishes 菜品
     */
    public void initRedisDishMakeTimeCache(List<Dish> dishes) {
        redis.delete(DISH_MAKE_TIME_CACHE_KEY);
        for (Dish dish : dishes) {
            setForHash(DISH_MAKE_TIME_CACHE_KEY, dish.getId(), dish.getMakeTime());
        }
        redis.expire(DISH_MAKE_TIME_CACHE_KEY, DEFAULT_EXPIRATION_TIME_SECONDS, TimeUnit.SECONDS);
    }


    /**
     * 刷新菜品库存，即使 key 已存在，缓存无过期时间，可以继续调用此方法强制刷新
     *
     * @param dishes
     */
    public synchronized void flushRedisDishStockCache(List<Dish> dishes, int storeId) {
        if (hasKey(getDishStockCacheKey(storeId))) {
            redis.delete(getDishStockCacheKey(storeId));
        }
        for (Dish dish : dishes) {
            setForHash(getDishStockCacheKey(storeId), dish.getId(), dish.getStock());
        }
    }


}
