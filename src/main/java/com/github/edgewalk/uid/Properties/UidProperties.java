package com.github.edgewalk.uid.Properties;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;

/**
 * 属性注入bean
 * Created by: edgewalk
 * 2018-11-26 15:53
 */
@ConfigurationProperties("spring.uid")
@Data
public class UidProperties {

	/**
	 * 是否开启
	 */
	private boolean enable = true;

	/**
	 * 机器id,不能重复
	 */
	private int workId=1;

	/**
	 * 时间戳分配位数
	 */
	private byte timestampBits =41;

	/**
	 * 机器id分配位数
	 */
	private byte workerIdBits=10;

	/**
	 * 序列所占位置
	 */
	private byte sequenceBits=12;

	/**
	 * 开始生成uid的时间
	 */
	private String epochStr= "2018-11-11";
}
