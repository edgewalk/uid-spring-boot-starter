package com.github.edgewalk.uid.Buffer;

/**
 * 指定拒绝策略
 * 如果tail赶上cursor，则意味着循环缓冲区已满，任何缓冲区put请求都将被拒绝。
 *
 * @author yutianbao
 */
@FunctionalInterface
public interface RejectedPutBufferHandler {

	/**
	 * 拒绝put buffer 请求
	 *
	 * @param ringBuffer
	 * @param uid
	 */
	void rejectPutBuffer(RingBuffer ringBuffer, long uid);
}