package com.github.edgewalk.uid.generator;


import com.github.edgewalk.uid.exception.UidGenerateException;

/**
 * 唯一id生成器
 */
public interface UidGenerator {

	/**
	 * 获取一个 唯一的ID
	 *
	 * @return id
	 * @throws UidGenerateException
	 */
	long getUid() throws UidGenerateException;

	/**
	 * 解析uid获得原始信息
	 * 例如: 时间戳,机器id,序列号
	 *
	 * @param uid
	 * @return
	 */
	String parseUid(long uid);
}
