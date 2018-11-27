package com.github.edgewalk.uid.generator.impl;

import com.github.edgewalk.uid.Properties.UidProperties;
import com.github.edgewalk.uid.exception.UidGenerateException;
import com.github.edgewalk.uid.generator.UidGenerator;
import com.github.edgewalk.uid.utils.BitsAllocator;
import com.github.edgewalk.uid.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * 默认id生成器
 * Twitter_Snowflake(雪花算法)
 * 生成的id用一个long类型来表示
 * SnowFlake的结构如下(每部分用-分开)
 * 0 - 0000000000 0000000000 0000000000 0000000000 0 - 0000000000 -000000000000
 * <pre>{@code
 * +------+----------------------+----------------+-----------+
 * | sign |     timestramp       | worker node id | sequence  |
 * +------+----------------------+----------------+-----------+
 *   1bit          41bits              10bits         12bits
 * }</pre>
 * <p>
 * * 1位标识，由于long基本类型在Java中是带符号的，最高位是符号位，正数是0，负数是1，所以id一般是正数，最高位是0
 * * 41位时间截的差值（当前时间截 - 开始时间截),开始时间截一般是我们的id生成器开始使用的时间，由我们程序来指定的,
 * 41位的时间截，可以使用69年，年T = (1L << 41) /(1000L * 60 * 60 * 24 * 365) = 69,超过69年就会导致long类型存不下
 * *10位的数据机器位，可以部署在1024个节点 <br>
 * * 12位序列，毫秒内的计数，12位的计数顺序号支持每个节点每毫秒(同一机器，同一时间截)产生4096个ID序号,如果当前毫秒序列号用完,阻塞线程到下一毫秒获取
 * <p>
 * 加起来刚好64位，为一个Long型(4个字节)
 * SnowFlake的优点是，整体上按照时间自增排序，并且整个分布式系统内不会产生ID碰撞(由机器ID作区分)，并且效率较高，经测试，SnowFlake每秒能够产生100万ID左右
 */
@Slf4j
public class DefaultUidGenerator implements UidGenerator {

	//bit位分配器
	protected BitsAllocator bitsAllocator;
	//时间戳占位数
	protected int timestampBits;
	//机器id所占的位数
	protected int workerIdBits;
	//12位序列所占的位数
	protected int sequenceBits;
	//开始时间 (2015-01-01) ,一般设置成id生成器,开始运行时候,这个参数很重要,如果服务器down机重启,lastTimestamp会变成0
	//但是只要不调整系统时间,那么时间差值就会不重复,生成的id就不会重复
	protected long twepoch;
	//机器ID
	protected long workerId;


	//保存毫秒内序列
	protected long sequence = 0L;
	//上次生成ID的时间截
	protected long lastTimestamp = -1L;


	public DefaultUidGenerator(UidProperties uidProperties) {
		this.timestampBits = uidProperties.getTimestampBits();
		this.workerIdBits = uidProperties.getWorkerIdBits();
		this.sequenceBits = uidProperties.getSequenceBits();
		bitsAllocator = new BitsAllocator(timestampBits, workerIdBits, sequenceBits);
		this.workerId = uidProperties.getWorkId();
		if (workerId > bitsAllocator.getMaxWorkerId()) {
			throw new RuntimeException("Worker id " + workerId + " exceeds the max " + bitsAllocator.getMaxWorkerId());
		}
		this.twepoch = DateUtils.parseByDayPattern(uidProperties.getEpochStr()).getTime();
	}

	public static void main(String[] args) {
		DefaultUidGenerator defaultIdGenerator = new DefaultUidGenerator(new UidProperties());
		long uid = defaultIdGenerator.getUid();
		System.out.println(uid);
		System.out.println(defaultIdGenerator.parseUid(uid));
	}

	@Override
	public long getUid() throws UidGenerateException {
		try {
			return nextId();
		} catch (Exception e) {
			log.error("Generate unique id exception. ", e);
			throw new UidGenerateException(e);
		}
	}

	@Override
	public String parseUid(long uid) {
		long totalBits = BitsAllocator.TOTAL_BITS;
		long signBits = bitsAllocator.getSignBits();
		long timestampBits = bitsAllocator.getTimestampBits();
		long workerIdBits = bitsAllocator.getWorkerIdBits();
		long sequenceBits = bitsAllocator.getSequenceBits();

		//
		long sequence = (uid << (totalBits - sequenceBits)) >>> (totalBits - sequenceBits);
		long workerId = (uid << (timestampBits + signBits)) >>> (totalBits - workerIdBits);
		long deltaSeconds = uid >>> (workerIdBits + sequenceBits);

		Date thatTime = new Date(twepoch + deltaSeconds);
		String thatTimeStr = DateUtils.formatByDateTimePattern(thatTime);
		// format as string
		return String.format("{\"UID\":\"%d\",\"timestamp\":\"%s\",\"workerId\":\"%d\",\"sequence\":\"%d\"}",
				uid, thatTimeStr, workerId, sequence);
	}


	/**
	 * 获得下一个ID (该方法是线程安全的)
	 *
	 * @return SnowflakeId
	 */
	public synchronized long nextId() {
		long timestamp = getCurrentMilliSecond();
		// 如果当前时间小于上一次ID生成的时间戳，说明系统时钟回退过这个时候应当抛出异常
		if (timestamp < lastTimestamp) {
			/*
			扩展配置,如果系统时间回退在可以忍受的范围内,可以等待,让线程睡眠
			long offset = lastTimestamp - timestamp;
			if (offset <= 10) {//如果发生时钟回退在可接受的范围以内(10ms)
				try {
					Thread.sleep(offset + 1);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				timestamp = currentMilliSecond();//再次获取时间戳
			}
			if (timestamp < lastTimestamp) {//如果仍然小于
				throw new RuntimeException(String.format("Clock moved backwards.  Refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
			}
			*/
			log.error(String.format("clock is moving backwards. Rejecting requests until %d.", lastTimestamp));
			throw new RuntimeException(String.format("Clock moved backwards.  Refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
		} else if (timestamp == lastTimestamp) {// 如果是同一时间生成的，则进行毫秒内序列
			sequence = (sequence + 1) & bitsAllocator.getMaxSequence();//相等为0
			// 毫秒内序列溢出
			if (sequence == 0L) {
				// 阻塞到下一个毫秒,获得新的时间戳
				timestamp = tilNextMillis(lastTimestamp);
			}
		} else {// 时间戳改变(当前时间大于上一次id生成时间)，毫秒内序列重置
			//TODO 如果为了id能均匀分配(根据id尾数hash分库存储),此处需要设置毫秒内初始序列号为0-9的随机数
			//sequence=new Random().nextInt(10);
			sequence = 0L;
		}

		// 上次生成ID的时间截
		lastTimestamp = timestamp;
		// 移位并通过或运算拼到一起组成64位的ID
		return bitsAllocator.allocate(timestamp - twepoch, workerId, sequence);
	}

	/**
	 * 阻塞到下一个毫秒，直到获得新的时间戳
	 *
	 * @param lastTimestamp 上次生成ID的时间截
	 * @return 当前时间戳
	 */
	protected long tilNextMillis(long lastTimestamp) {
		long timestamp = getCurrentMilliSecond();
		//如果当前时间小于上一次生成id的时间,那么就自旋到下一毫秒
		while (timestamp <= lastTimestamp) {
			timestamp = System.currentTimeMillis();
		}
		return timestamp;
	}

	private long getCurrentMilliSecond() {
		long currentSecond = System.currentTimeMillis();
		if (currentSecond - twepoch > bitsAllocator.getMaxTimestamp()) {
			//时间戳用尽了
			throw new RuntimeException("timeBits is exhausted. Refusing UID generate. Now: " + DateUtils.formatByDateTimePattern(new Date(currentSecond)));
		}
		return currentSecond;
	}
}
