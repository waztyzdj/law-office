package com.lawoffice.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类
 */
@Slf4j
@Component
public class RedisUtils {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ValueOperations<String, Object> valueOps;

    @Autowired
    private HashOperations<String, String, Object> hashOps;

    @Autowired
    private ListOperations<String, Object> listOps;

    @Autowired
    private SetOperations<String, Object> setOps;

    @Autowired
    private ZSetOperations<String, Object> zSetOps;

    // ==================== 通用操作 ====================

    /**
     * 指定缓存失效时间
     *
     * @param key  键
     * @param time 时间(秒)
     * @return true-成功 false-失败
     */
    public boolean expire(String key, long time) {
        try {
            if (time > 0) {
                redisTemplate.expire(key, time, TimeUnit.SECONDS);
            }
            return true;
        } catch (Exception e) {
            log.error("设置过期时间失败", e);
            return false;
        }
    }

    /**
     * 根据key获取过期时间
     *
     * @param key 键 不能为null
     * @return 时间(秒) 返回0代表为永久有效
     */
    public long getExpire(String key) {
        Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return expire != null ? expire : 0;
    }

    /**
     * 判断key是否存在
     *
     * @param key 键
     * @return true-存在 false-不存在
     */
    public boolean hasKey(String key) {
        try {
            Boolean hasKey = redisTemplate.hasKey(key);
            return hasKey != null && hasKey;
        } catch (Exception e) {
            log.error("判断key是否存在失败", e);
            return false;
        }
    }

    /**
     * 删除缓存
     *
     * @param key 可以传一个值 或多个
     */
    @SuppressWarnings("unchecked")
    public void del(String... key) {
        if (key != null && key.length > 0) {
            if (key.length == 1) {
                redisTemplate.delete(key[0]);
            } else {
                redisTemplate.delete((Collection<String>) java.util.Arrays.asList(key));
            }
        }
    }

    // ==================== String 操作 ====================

    /**
     * 普通缓存获取
     *
     * @param key 键
     * @return 值
     */
    public Object get(String key) {
        return key == null ? null : valueOps.get(key);
    }

    /**
     * 普通缓存放入
     *
     * @param key   键
     * @param value 值
     * @return true-成功 false-失败
     */
    public boolean set(String key, Object value) {
        try {
            valueOps.set(key, value);
            return true;
        } catch (Exception e) {
            log.error("设置缓存失败", e);
            return false;
        }
    }

    /**
     * 普通缓存放入并设置时间
     *
     * @param key   键
     * @param value 值
     * @param time  时间(秒) time要大于0 如果time小于等于0 将设置无限期
     * @return true-成功 false-失败
     */
    public boolean set(String key, Object value, long time) {
        try {
            if (time > 0) {
                valueOps.set(key, value, time, TimeUnit.SECONDS);
            } else {
                set(key, value);
            }
            return true;
        } catch (Exception e) {
            log.error("设置缓存失败", e);
            return false;
        }
    }

    /**
     * 递增
     *
     * @param key   键
     * @param delta 要增加几(大于0)
     * @return 递增后的值
     */
    public long incr(String key, long delta) {
        if (delta < 0) {
            throw new RuntimeException("递增因子必须大于0");
        }
        Long increment = redisTemplate.opsForValue().increment(key, delta);
        return increment != null ? increment : 0;
    }

    /**
     * 递减
     *
     * @param key   键
     * @param delta 要减少几(小于0)
     * @return 递减后的值
     */
    public long decr(String key, long delta) {
        if (delta < 0) {
            throw new RuntimeException("递减因子必须大于0");
        }
        Long decrement = redisTemplate.opsForValue().increment(key, -delta);
        return decrement != null ? decrement : 0;
    }

    // ==================== Hash 操作 ====================

    /**
     * HashGet
     *
     * @param key  键 不能为null
     * @param item 项 不能为null
     * @return 值
     */
    public Object hget(String key, String item) {
        return hashOps.get(key, item);
    }

    /**
     * 获取hashKey对应的所有键值
     *
     * @param key 键
     * @return 对应的多个键值
     */
    @SuppressWarnings("unchecked")
    public Map<Object, Object> hmget(String key) {
        try {
            return (Map<Object, Object>) (Map<?, ?>) hashOps.entries(key);
        } catch (Exception e) {
            log.error("Hash获取失败", e);
            return null;
        }
    }

    /**
     * HashSet
     *
     * @param key 键
     * @param map 对应多个键值
     * @return true-成功 false-失败
     */
    public boolean hmset(String key, Map<String, Object> map) {
        try {
            hashOps.putAll(key, map);
            return true;
        } catch (Exception e) {
            log.error("Hash设置失败", e);
            return false;
        }
    }

    /**
     * HashSet 并设置时间
     *
     * @param key  键
     * @param map  对应多个键值
     * @param time 时间(秒)
     * @return true-成功 false-失败
     */
    public boolean hmset(String key, Map<String, Object> map, long time) {
        try {
            hashOps.putAll(key, map);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            log.error("Hash设置失败", e);
            return false;
        }
    }

    /**
     * 向一张hash表中放入数据,如果不存在将创建
     *
     * @param key   键
     * @param item  项
     * @param value 值
     * @return true-成功 false-失败
     */
    public boolean hset(String key, String item, Object value) {
        try {
            hashOps.put(key, item, value);
            return true;
        } catch (Exception e) {
            log.error("Hash设置失败", e);
            return false;
        }
    }

    /**
     * 向一张hash表中放入数据,如果不存在将创建
     *
     * @param key   键
     * @param item  项
     * @param value 值
     * @param time  时间(秒) 注意:如果已存在的hash表有时间,这里将会替换原有的时间
     * @return true-成功 false-失败
     */
    public boolean hset(String key, String item, Object value, long time) {
        try {
            hashOps.put(key, item, value);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            log.error("Hash设置失败", e);
            return false;
        }
    }

    /**
     * 删除hash表中的值
     *
     * @param key  键 不能为null
     * @param item 项 可以使多个 不能为null
     */
    public void hdel(String key, Object... item) {
        hashOps.delete(key, item);
    }

    /**
     * 判断hash表中是否有该项的值
     *
     * @param key  键 不能为null
     * @param item 项 不能为null
     * @return true-存在 false-不存在
     */
    public boolean hHasKey(String key, String item) {
        return hashOps.hasKey(key, item);
    }

    // ==================== List 操作 ====================

    /**
     * 获取list缓存的内容
     *
     * @param key   键
     * @param start 开始
     * @param end   结束 0 到 -1代表所有值
     * @return list内容
     */
    public List<Object> lGet(String key, long start, long end) {
        try {
            return listOps.range(key, start, end);
        } catch (Exception e) {
            log.error("List获取失败", e);
            return null;
        }
    }

    /**
     * 获取list缓存的长度
     *
     * @param key 键
     * @return 长度
     */
    public long lGetListSize(String key) {
        try {
            Long size = listOps.size(key);
            return size != null ? size : 0;
        } catch (Exception e) {
            log.error("List获取长度失败", e);
            return 0;
        }
    }

    /**
     * 通过索引 获取list中的值
     *
     * @param key   键
     * @param index 索引 index>=0时， 0 表头，1 第二个元素，依次类推；index<0时，-1，表尾，-2倒数第二个元素，依次类推
     * @return 值
     */
    public Object lGetIndex(String key, long index) {
        try {
            return listOps.index(key, index);
        } catch (Exception e) {
            log.error("List获取索引值失败", e);
            return null;
        }
    }

    /**
     * 将list放入缓存
     *
     * @param key   键
     * @param value 值
     * @return true-成功 false-失败
     */
    public boolean lSet(String key, Object value) {
        try {
            listOps.rightPush(key, value);
            return true;
        } catch (Exception e) {
            log.error("List设置失败", e);
            return false;
        }
    }

    /**
     * 将list放入缓存
     *
     * @param key   键
     * @param value 值
     * @param time  时间(秒)
     * @return true-成功 false-失败
     */
    public boolean lSet(String key, Object value, long time) {
        try {
            listOps.rightPush(key, value);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            log.error("List设置失败", e);
            return false;
        }
    }

    /**
     * 将list放入缓存
     *
     * @param key   键
     * @param value 值
     * @return true-成功 false-失败
     */
    public boolean lSet(String key, List<Object> value) {
        try {
            listOps.rightPushAll(key, value);
            return true;
        } catch (Exception e) {
            log.error("List批量设置失败", e);
            return false;
        }
    }

    /**
     * 将list放入缓存
     *
     * @param key   键
     * @param value 值
     * @param time  时间(秒)
     * @return true-成功 false-失败
     */
    public boolean lSet(String key, List<Object> value, long time) {
        try {
            listOps.rightPushAll(key, value);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            log.error("List批量设置失败", e);
            return false;
        }
    }

    // ==================== Set 操作 ====================

    /**
     * 根据key获取Set中的所有值
     *
     * @param key 键
     * @return Set中的所有值
     */
    public Set<Object> sGet(String key) {
        try {
            return setOps.members(key);
        } catch (Exception e) {
            log.error("Set获取失败", e);
            return null;
        }
    }

    /**
     * 根据value从一个set中查询,是否存在
     *
     * @param key   键
     * @param value 值
     * @return true-存在 false-不存在
     */
    public boolean sHasKey(String key, Object value) {
        try {
            Boolean isMember = setOps.isMember(key, value);
            return isMember != null && isMember;
        } catch (Exception e) {
            log.error("Set判断成员失败", e);
            return false;
        }
    }

    /**
     * 将数据放入set缓存
     *
     * @param key    键
     * @param values 值 可以是多个
     * @return 成功个数
     */
    public long sSet(String key, Object... values) {
        try {
            Long count = setOps.add(key, values);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("Set添加失败", e);
            return 0;
        }
    }

    /**
     * 将set数据放入缓存
     *
     * @param key    键
     * @param time   时间(秒)
     * @param values 值 可以是多个
     * @return 成功个数
     */
    public long sSetAndTime(String key, long time, Object... values) {
        try {
            Long count = setOps.add(key, values);
            if (time > 0) {
                expire(key, time);
            }
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("Set添加失败", e);
            return 0;
        }
    }

    /**
     * 获取set缓存的长度
     *
     * @param key 键
     * @return 长度
     */
    public long sGetSetSize(String key) {
        try {
            Long size = setOps.size(key);
            return size != null ? size : 0;
        } catch (Exception e) {
            log.error("Set获取长度失败", e);
            return 0;
        }
    }

    /**
     * 移除值为value的
     *
     * @param key    键
     * @param values 值 可以是多个
     * @return 移除的个数
     */
    public long setRemove(String key, Object... values) {
        try {
            Long count = setOps.remove(key, values);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("Set移除失败", e);
            return 0;
        }
    }
}
