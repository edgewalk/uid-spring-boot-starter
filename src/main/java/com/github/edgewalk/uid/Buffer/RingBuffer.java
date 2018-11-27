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
package com.github.edgewalk.uid.Buffer;

import com.github.edgewalk.uid.utils.PaddedAtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于数组的环形缓存区
 * 使用数组可以提高读取元素的性能，因为 CPU cache line
 * 为了防止False Sharing,在"tail"和"cursor"上使用{@link PaddedAtomicLong}
 * 环形缓冲区组成:
 * slot     : 数组中的每个元素都是一个slot，这个slot是用来存放uid的
 * flags    : flag数组与 slot数组对应的索引相同，表示是否可以取槽或放槽
 * tail     :序列的最大槽位生产
 * cursor   :序列的最小槽位置消费
 */
@Slf4j
public class RingBuffer {

	//默认填充百分比
	public static final int DEFAULT_PADDING_PERCENT = 50;

	//初始标记
	private static final int START_POINT = -1;
	//是否可放
	private static final long CAN_PUT_FLAG = 0L;
	//是否可取
	private static final long CAN_TAKE_FLAG = 1L;
	//buffer长度
	private final int bufferSize;
	//buffer最大索引,例如: buffersize=8 ,indexMask=7
	private final long indexMask;

	//保存uid
	private final long[] slots;
	//存储Uid状态(是否可填充、是否可消费)
	private final PaddedAtomicLong[] flags;

	//Tail: 表示Producer生产的最大序号
	private final AtomicLong tail = new PaddedAtomicLong(START_POINT);
	//Cursor: consumer当前消费到的位置
	private final AtomicLong cursor = new PaddedAtomicLong(START_POINT);
	//剩余未消费uid阈值 =bufferSize * (paddingFactor/100)
	private final int paddingThreshold;

	//拒绝策略处理器
	private RejectedPutBufferHandler rejectedPutHandler = this::discardPutBuffer;
	private RejectedTakeBufferHandler rejectedTakeHandler = this::exceptionRejectedTakeBuffer;

	//填充 buffer的执行器
	private BufferPaddingExecutor bufferPaddingExecutor;

	/**
	 * TODO 当不是2的倍数时,自动处理
	 *
	 * @param bufferSize 正数并且是2的倍数
	 */
	public RingBuffer(int bufferSize) {
		this(bufferSize, DEFAULT_PADDING_PERCENT);
	}

	/**
	 * @param bufferSize    buffer大小:正数并且是2的倍数
	 * @param paddingFactor
	 */
	public RingBuffer(int bufferSize, int paddingFactor) {
		Assert.isTrue(bufferSize > 0L, "RingBuffer size must be positive");
		Assert.isTrue(paddingFactor > 0 && paddingFactor < 100, "RingBuffer size must be positive");
		this.bufferSize = bufferSize;
		this.indexMask = bufferSize - 1;
		this.slots = new long[bufferSize];
		this.flags = initFlags(bufferSize);
		this.paddingThreshold = bufferSize * paddingFactor / 100;
	}


	/**
	 * 添加uid到槽位
	 * 线程安全操作: 只有添加完全完成之后,才能被消费
	 *
	 * @param uid
	 * @return 当buffer放置满后, 返回false, 同时会执行拒绝策略 {@link RejectedPutBufferHandler}
	 */
	public synchronized boolean put(long uid) {
		long currentTail = tail.get();
		long currentCursor = cursor.get();

		//如果当前生产位置 = 当前消费位置,那么就表示 Ringbuffer慢了,不能再放置了
		long distance = currentTail - (currentCursor == START_POINT ? 0 : currentCursor);
		//distance 表示当前未消费的元素
		if (distance == bufferSize - 1) {
			rejectedPutHandler.rejectPutBuffer(this, uid);
			return false;
		}
		// 1. 检查flag数组下一个元素,是否可放
		int nextTailIndex = calSlotIndex(currentTail + 1);
		if (flags[nextTailIndex].get() != CAN_PUT_FLAG) {
			rejectedPutHandler.rejectPutBuffer(this, uid);
			return false;
		}
		//2. 放置uid到下一个槽位
		slots[nextTailIndex] = uid;
		//3. 设置flag数组下一个槽位为可取
		flags[nextTailIndex].set(CAN_TAKE_FLAG);
		// 4. 更新生产位置+1
		tail.incrementAndGet();
		return true;
	}

	/**
	 * @return
	 */
	public long take() {
		//获取当前消费的位置
		long currentCursor = cursor.get();
		//获取下一个cursor位置,保证 next cursor <= current cursor
		long nextCursor = cursor.updateAndGet(old -> old == tail.get() ? old : old + 1);

		// current cursor == current tail :uid被消费完
		if (nextCursor == currentCursor) {
			rejectedTakeHandler.rejectTakeBuffer(this);
		}
		Assert.isTrue(nextCursor >= currentCursor, "Curosr can't move back");
		long currentTail = tail.get();
		//如果剩余未消费uid,小于设置的阈值,将触发填充操作
		if (currentTail - nextCursor < paddingThreshold) {
			log.info("Reach the padding threshold:{}. tail:{}, cursor:{}, rest:{}", paddingThreshold, currentTail,
					nextCursor, currentTail - nextCursor);
			bufferPaddingExecutor.asyncPadding();
		}
		// 1. 获取数组索引
		int nextCursorIndex = calSlotIndex(nextCursor);
		Assert.isTrue(flags[nextCursorIndex].get() == CAN_TAKE_FLAG, "Curosr not in can take status");

		// 2. 获取 下一个槽位的uid
		long uid = slots[nextCursorIndex];
		// 3. 设置下一个槽位的标记为 可放
		flags[nextCursorIndex].set(CAN_PUT_FLAG);
		// Note that: Step 2,3 can not swap. If we set flag before get value of slot, the producer may overwrite the
		// slot with a new UID, and this may cause the consumer take the UID twice after walk a round the ring
		return uid;
	}

	/**
	 * 根据消费或生产序号计算数组的索引位置
	 * Calculate slot index with the slot sequence (sequence % bufferSize)
	 */
	protected int calSlotIndex(long sequence) {
		return (int) (sequence & indexMask);

	}

	/**
	 * 当tail= cursor之后的拒绝策略
	 */
	protected void discardPutBuffer(RingBuffer ringBuffer, long uid) {
		log.warn("Rejected putting buffer for uid:{}. {}", uid, ringBuffer);
	}

	/**
	 * 当 cursor =tail后的拒绝策略
	 */
	protected void exceptionRejectedTakeBuffer(RingBuffer ringBuffer) {
		log.warn("Rejected take buffer. {}", ringBuffer);
		throw new RuntimeException("Rejected take buffer. " + ringBuffer);
	}

	/**
	 * 初始化 RingBuffer of flags,默认是0,表示可放置
	 */
	private PaddedAtomicLong[] initFlags(int bufferSize) {
		PaddedAtomicLong[] flags = new PaddedAtomicLong[bufferSize];
		for (int i = 0; i < bufferSize; i++) {
			flags[i] = new PaddedAtomicLong(CAN_PUT_FLAG);
		}

		return flags;
	}

	/**
	 * Getters
	 */
	public long getTail() {
		return tail.get();
	}

	public long getCursor() {
		return cursor.get();
	}

	public int getBufferSize() {
		return bufferSize;
	}

	/**
	 * Setters
	 */
	public void setBufferPaddingExecutor(BufferPaddingExecutor bufferPaddingExecutor) {
		this.bufferPaddingExecutor = bufferPaddingExecutor;
	}

	public void setRejectedPutHandler(RejectedPutBufferHandler rejectedPutHandler) {
		this.rejectedPutHandler = rejectedPutHandler;
	}

	public void setRejectedTakeHandler(RejectedTakeBufferHandler rejectedTakeHandler) {
		this.rejectedTakeHandler = rejectedTakeHandler;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("RingBuffer [bufferSize=").append(bufferSize)
				.append(", tail=").append(tail)
				.append(", cursor=").append(cursor)
				.append(", paddingThreshold=").append(paddingThreshold).append("]");

		return builder.toString();
	}

}
