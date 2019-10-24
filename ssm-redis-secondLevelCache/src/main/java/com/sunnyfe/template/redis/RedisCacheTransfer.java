package com.sunnyfe.template.redis;

import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.stereotype.Component;


@Component
public class RedisCacheTransfer {

    @Value("${redis.cacheTTL}")
    private int redis_cacheTTL;

    @Value("${redis.switch}")
    private String redis_switch;

    @Value("${redis.cachePrefix}")
    private String redis_cachePrefix;


    /**
     * 静态注入Redis客户端连接工厂
     * @param jedisConnectionFactory
     */
    @Autowired
    public void setJedisConnectionFactory(JedisConnectionFactory jedisConnectionFactory) {
        MybatisRedisCache.setJedisConnectionFactory(jedisConnectionFactory);
    }

    /**
     * 静态注入配置参数
     */
    @Autowired
	public void setProp() {
		MybatisRedisCache.setProp(redis_cacheTTL, BooleanUtils.toBoolean(redis_switch), // 对字符串安全性处理
				redis_cachePrefix);
	}

}
