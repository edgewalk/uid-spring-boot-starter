package com.github.edgewalk.uid.Buffer;


/**
 * 指定 拒接策略
 * 如果cursor 赶上 tail,这意味着环缓冲区为空，任何缓冲区接收请求都将被拒绝。
 */
@FunctionalInterface
public interface RejectedTakeBufferHandler {

	/**
	 * 拒绝 take buffer 请求
	 *
	 * @param ringBuffer
	 */
	void rejectTakeBuffer(RingBuffer ringBuffer);
}
