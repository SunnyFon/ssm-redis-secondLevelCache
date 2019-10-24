# ssm集成redis做二级缓存

准备工作:
	1: 搭建好的ssm项目(maven)
	2:pom依赖redis相应jar包
```java
<dependency>
  <groupId>org.springframework.data</groupId>
  <artifactId>spring-data-redis</artifactId>
  <version>1.8.13.RELEASE</version>
</dependency>
<dependency>
  <groupId>redis.clients</groupId>
  <artifactId>jedis</artifactId>
  <version>2.9.0</version>
</dependency>

```
3:添加spring-redis.xml, redis.properties, 具体详细配置根据需要配置

redis.properties
````java
#####redis配置文件#####
#*****************jedis连接参数设置*********************#
#单机版配置
redis.hostName=127.0.0.1
redis.port=6379

## Redis Cluster Config 集群版
#redis.cluster.host1=address1
#redis.cluster.host2=address2
#redis.cluster.host3=address3
#redis.cluster.host4=address4
#redis.cluster.host5=address5
#redis.cluster.host6=address6
#redis.cluster.port1=port1
#redis.cluster.port2=port2
#redis.cluster.port3=port3
#redis.cluster.port4=port4
#redis.cluster.port5=port5
#redis.cluster.port6=port6

#重试次数，在执行失败后，进行的重试次数，默认是5
redis.cluster.maxRedirects=3

#************************jedis池参数设置*******************#
jedis.pool.maxActive=1000
jedis.pool.maxIdle=500
jedis.pool.maxWait=1000
jedis.pool.testOnBorrow=true
jedis.pool.testOnReturn=true
jedis.pool.testWhileIdle=true
jedis.pool.blockWhenExhausted=false

#是否启用Redis作为二级缓存 (PRO 2-2)
#redis.switch=true
redis.switch=true
#存入Redis中的key的过期时间（秒）
redis.cacheTTL=3600
#缓存数据存入Redis中的数据库索引值
redis.database=0
#缓存key前缀
redis.cachePrefix=sunnyfe-ssm-redis

````

spring-redis.xml
````java
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:p="http://www.springframework.org/schema/p"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
		http://www.springframework.org/schema/beans/spring-beans-4.3.xsd">

    <!--redis数据源-->
    <bean id="jedisPoolConfig" class="redis.clients.jedis.JedisPoolConfig">
        <!-- 最大连接数 -->
        <property name="maxTotal" value="${jedis.pool.maxActive}"/>
        <!-- 最大空闲连接数 -->
        <property name="maxIdle" value="${jedis.pool.maxIdle}"/>
        <!-- 获取连接时的最大等待毫秒数,小于零:阻塞不确定的时间,默认-1 -->
        <property name="maxWaitMillis" value="${jedis.pool.maxWait}"/>
        <!-- 在获取连接的时候检查有效性, 默认false -->
        <property name="testOnBorrow" value="${jedis.pool.testOnBorrow}"/>
        <!-- 在返回的时候检查有效性, 默认false -->
        <property name="testOnReturn" value="${jedis.pool.testOnReturn}"/>
        <!-- 在空闲时检查有效性, 默认false -->
        <property name="testWhileIdle" value="${jedis.pool.testWhileIdle}" />
        <!-- 连接耗尽时是否阻塞, false报异常,ture阻塞直到超时, 默认true -->
        <property name="blockWhenExhausted" value="${jedis.pool.blockWhenExhausted}" />
    </bean>

    <!-- Redis集群配置 -->
    <bean id="redisClusterConfig" class="org.springframework.data.redis.connection.RedisClusterConfiguration">
        <property name="maxRedirects" value="${redis.cluster.maxRedirects}"></property>
        <property name="clusterNodes">
            <set>
                <bean class="org.springframework.data.redis.connection.RedisNode">
                    <constructor-arg name="host" value="${redis.cluster.host1}"></constructor-arg>
                    <constructor-arg name="port" value="${redis.cluster.port1}"></constructor-arg>
                </bean>

                <bean class="org.springframework.data.redis.connection.RedisNode">
                    <constructor-arg name="host" value="${redis.cluster.host2}"></constructor-arg>
                    <constructor-arg name="port" value="${redis.cluster.port2}"></constructor-arg>
                </bean>
                <bean class="org.springframework.data.redis.connection.RedisNode">
                    <constructor-arg name="host" value="${redis.cluster.host3}"></constructor-arg>
                    <constructor-arg name="port" value="${redis.cluster.port3}"></constructor-arg>
                </bean>
                <bean class="org.springframework.data.redis.connection.RedisNode">
                    <constructor-arg name="host" value="${redis.cluster.host4}"></constructor-arg>
                    <constructor-arg name="port" value="${redis.cluster.port4}"></constructor-arg>
                </bean>
                <bean class="org.springframework.data.redis.connection.RedisNode">
                    <constructor-arg name="host" value="${redis.cluster.host5}"></constructor-arg>
                    <constructor-arg name="port" value="${redis.cluster.port5}"></constructor-arg>
                </bean>
                <bean class="org.springframework.data.redis.connection.RedisNode">
                    <constructor-arg name="host" value="${redis.cluster.host6}"></constructor-arg>
                    <constructor-arg name="port" value="${redis.cluster.port6}"></constructor-arg>
                </bean>
            </set>
        </property>
    </bean>

    <!--Spring-redis连接池管理工厂-->
    <bean id="jedisConnectionFactory" class="org.springframework.data.redis.connection.jedis.JedisConnectionFactory">
        <!-- 集群配置 (呼池生产PRO 1-1) -->
        <!-- <constructor-arg name="clusterConfig" ref="redisClusterConfig"/> -->
        
        <!-- 单机版配置 -->
        <property name="hostName" value="${redis.hostName}"/>
        <property name="port" value="${redis.port}"/>
        <property name="database" value="${redis.database}"/>
        
        <!-- 连接池配置参数 -->
        <property name="poolConfig" ref="jedisPoolConfig"/>
    </bean>

</beans>
````
4:编写缓存相关支持类
````java
package com.sunnyfe.template.redis;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MybatisRedisCache implements Cache {

	private static final Logger LOG = LoggerFactory.getLogger(MybatisRedisCache.class);

	private static JedisConnectionFactory jedisConnectionFactory;

	private static boolean redis_switch; // 二级缓存开关
	private static int redis_cacheTTL; // 键的过期时间（秒）
	private static String redis_cachePrefix; // 缓存key前缀

	private RedisSerializer<Object> serializer;
	private StringRedisSerializer keySerializer;

	/**
	 * cache id cache对象的id，一个Mapper对应一个cache对象，该id字段为Mapper的namespace
	 */
	private final String id;
	private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);

	public MybatisRedisCache(final String id) {
		if (StringUtils.isBlank(id)) {
			throw new IllegalArgumentException("Cache instances require an ID");
		}
		this.id = id;
		this.serializer = new JdkSerializationRedisSerializer();
		this.keySerializer = new StringRedisSerializer();
		LOG.debug("Redis Cache id " + id);
	}

	public static void setJedisConnectionFactory(final JedisConnectionFactory jedisConnectionFactory) {
		MybatisRedisCache.jedisConnectionFactory = jedisConnectionFactory;
	}

	public static void setProp(final int seconds, final boolean redis_switch, final String redis_cachePrefix) {
		MybatisRedisCache.redis_cacheTTL = seconds;
		MybatisRedisCache.redis_switch = redis_switch;
		MybatisRedisCache.redis_cachePrefix = redis_cachePrefix;
	}

	@Override
	public String getId() {
		return this.id;
	}

	private String toKey(Object key) {
		return key.toString();
	}

	@Override
	public void putObject(Object key, Object value) {
		if (!redis_switch || null == key) {
			return;
		}
		key = toKey(key);

		LOG.debug("---------mybatis redis cache put. K=[{}] value=[{}]", key, value);
		RedisConnection connection = null;
		try {
			String newk = redis_cachePrefix + key;
			byte[] skey = keySerializer.serialize(newk);

			connection = jedisConnectionFactory.getConnection();
			connection.set(skey, serializer.serialize(value));
			connection.expire(skey, redis_cacheTTL); // 设置key的过期时间，key_TTL为秒数

			// 将key保存到redis.set中
			Long lPush = connection.sAdd(keySerializer.serialize(id), skey);
			LOG.debug("往list[{}]里面添加value[{}]：{}", id, newk, lPush);
		} catch (JedisConnectionException e) {
			LOG.error("mybatis redis cache put exception. K=" + key + " V=" + value + "", e);
			e.printStackTrace();
		} finally {
			if (null != connection) {
				connection.close();
			}
		}
	}

	@Override
	public Object getObject(Object key) {
		if (!redis_switch || null == key) {
			return null;
		}
		key = toKey(key);

		LOG.debug("===> mybatis redis cache get. K={}", key);
		RedisConnection connection = null;
		Object result = null;
		try {
			String newk = redis_cachePrefix + key;
			byte[] skey = keySerializer.serialize(newk);

			connection = jedisConnectionFactory.getConnection();
			result = serializer.deserialize(connection.get(skey));
		} catch (JedisConnectionException e) {
			LOG.error("mybatis redis cache get exception. K=" + key + " V=" + result + "", e);
			e.printStackTrace();
		} finally {
			if (null != connection) {
				connection.close();
			}
		}
		return result;
	}

	@Override
	public Object removeObject(Object key) {
		if (!redis_switch || null == key) {
			return null;
		}
		key = toKey(key);

		LOG.debug("<===mybatis redis cache remove. K={}", key);
		RedisConnection connection = null;
		Object result = null;
		try {
			String newk = redis_cachePrefix + key;
			byte[] skey = keySerializer.serialize(newk);

			connection = jedisConnectionFactory.getConnection();
			// 讲key设置为立即过期
			result = connection.expire(skey, 0);

			// 将key从redis.set中删除
			connection.sRem(keySerializer.serialize(id), skey);
		} catch (JedisConnectionException e) {
			LOG.error("mybatis redis cache remove exception. " + key + " V=" + result + "", e);
			e.printStackTrace();
		} finally {
			if (null != connection) {
				connection.close();
			}
		}
		return result;
	}

	@Override
	public void clear() {
		LOG.debug("===mybatis redis cache clear... ===>");
		if (!redis_switch) {
			return;
		}
		RedisConnection connection = null;
		try {
			LOG.info("===>clear cache id [{}]", id);
			byte[] bkey = keySerializer.serialize(id);

			connection = jedisConnectionFactory.getConnection();
			// TODO 方案：以下是清空Redis指定范围内的缓存数据
			// 用redis.set结构来存储归cache对象管理的缓存数据，set的key为cacheid也即(mapper的namespace)
			Long length = connection.sCard(bkey);
			if (0 == length) {
				return;
			}
			Set<byte[]> keyList = connection.sMembers(bkey);
			for (byte[] key : keyList) { // 循环清除列表中的缓存
				// LOG.debug("clear cache [{}]", key);
				connection.expire(key, 0);
			}
			connection.expire(bkey, 0); // 删除
			keyList.clear();
		} catch (JedisConnectionException e) {
			e.printStackTrace();
		} finally {
			if (null != connection) {
				connection.close();
			}
		}
	}

	@Override
	public int getSize() {
		if (!redis_switch) {
			return 0;
		}
		int result = 0;
		RedisConnection connection = null;
		try {
			connection = jedisConnectionFactory.getConnection();
			result = Integer.valueOf(connection.dbSize().toString());
		} catch (JedisConnectionException e) {
			e.printStackTrace();
		} finally {
			if (null != connection) {
				connection.close();
			}
		}
		return result;
	}

	@Override
	public ReadWriteLock getReadWriteLock() {
		return this.readWriteLock;
	}
}
````
````java
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

````
5:在编写sql的xml文件中添加cache标签使用缓存, 指向实现ibatis cache的类
````java
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.sunnyfe.template.dao.UserDao">

	<!--开始二级缓存-->
	<cache type="com.sunnyfe.template.redis.MybatisRedisCache" />

	<resultMap id="BaseResultMap" type="com.sunnyfe.template.pojo.User">
		<id column="id" jdbcType="VARCHAR" property="id" />
		<result column="user_name" jdbcType="VARCHAR" property="userName" />
		<result column="password" jdbcType="VARCHAR" property="password" />
	</resultMap>

	<sql id="Base_Column_List">
    id, user_name, password
  </sql>
    <select id="findAll"  resultMap="BaseResultMap">
		SELECT
		<include refid="Base_Column_List" />
		FROM
			user
	</select>


</mapper>
````