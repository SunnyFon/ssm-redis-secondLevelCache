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