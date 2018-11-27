/*
 * Copyright (c) 2017 Baidu, Inc. All Rights Reserve.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.edgewalk.uid.utils;

import lombok.Getter;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.springframework.util.Assert;

/**
 * 64位uid分配器
 * sign (1bit) -> timestamp -> workerId -> sequence
 */
@Getter
public class BitsAllocator {
	/**
	 * 总共64位长度
	 */
	public static final int TOTAL_BITS = 1 << 6;

	/**
	 * 标识位长度
	 * [sign-> second-> workId-> sequence]
	 */
	private final int signBits = 1;
	private final int timestampBits;
	private final int workerIdBits;
	private final int sequenceBits;
	/**
	 * 标识位最大值
	 */
	private final long maxTimestamp;
	private final long maxWorkerId;
	private final long maxSequence;
	/**
	 * 标识位偏移量
	 */
	private final int timestampShift;
	private final int workerIdShift;



	public BitsAllocator(int timestampBits, int workerIdBits, int sequenceBits) {
		// 所有标识位总数为64位
		int allocateTotalBits = signBits + timestampBits + workerIdBits + sequenceBits;
		Assert.isTrue(allocateTotalBits == TOTAL_BITS, "allocate not enough 64 bits");

		this.timestampBits = timestampBits;
		this.workerIdBits = workerIdBits;
		this.sequenceBits = sequenceBits;

		// ~(-1L << timestampBits);
		this.maxTimestamp =(1L << timestampBits) - 1;
		this.maxWorkerId = (1L << workerIdBits) - 1;
		this.maxSequence = (1L << sequenceBits) - 1;

		this.timestampShift = workerIdBits + sequenceBits;
		this.workerIdShift = sequenceBits;
	}

	/**
	 * 移位并通过或运算拼到一起组成64位的ID
	 *
	 * @param deltaSeconds 当前时间戳-起始时间戳
	 * @param workerId     机器id
	 * @param sequence     序列号
	 * @return
	 */
	public long allocate(long deltaSeconds, long workerId, long sequence) {
		return (deltaSeconds << timestampShift) | (workerId << workerIdShift) | sequence;
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}

}