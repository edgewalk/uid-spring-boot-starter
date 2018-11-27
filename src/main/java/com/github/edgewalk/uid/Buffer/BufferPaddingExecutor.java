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

import com.github.edgewalk.uid.utils.NamingThreadFactory;
import com.github.edgewalk.uid.utils.PaddedAtomicLong;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 填充 {@link RingBuffer} 的填充器
 *  2种执行器: 调度式填充器,普通填充器
 *
 */
public class BufferPaddingExecutor {
	private static final Logger LOGGER = LoggerFactory.getLogger(RingBuffer.class);

	//普通模式线程名
	private static final String WORKER_NAME = "RingBuffer-Padding-Worker";
	//调度默认线程名
	private static final String SCHEDULE_NAME = "RingBuffer-Padding-Schedule";
	//默认调度间隔 (5分钟)
	private static final long DEFAULT_SCHEDULE_INTERVAL = 5 * 60L; // 5 minutes
	//标记当前是否在padding操作
	@Getter
	private final AtomicBoolean running;

	//最后一次消费uid的时间(可能是未来时间)
	private final PaddedAtomicLong lastSecond;

	private final RingBuffer ringBuffer;
	//buffered uid 提供者
	private final BufferedUidProvider uidProvider;

	//普通模式线程
	private final ExecutorService bufferPadExecutors;

	//调度默认线程
	private final ScheduledExecutorService bufferPadSchedule;

	//调度间隔时间
	@Setter
	private long scheduleInterval = DEFAULT_SCHEDULE_INTERVAL;


	public BufferPaddingExecutor(RingBuffer ringBuffer, BufferedUidProvider uidProvider) {
		this(ringBuffer, uidProvider, true);
	}


	public BufferPaddingExecutor(RingBuffer ringBuffer, BufferedUidProvider uidProvider, boolean usingSchedule) {
		this.running = new AtomicBoolean(false);
		this.lastSecond = new PaddedAtomicLong(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));
		this.ringBuffer = ringBuffer;
		this.uidProvider = uidProvider;

		//初始化线程池
		int cores = Runtime.getRuntime().availableProcessors();
		bufferPadExecutors = Executors.newFixedThreadPool(cores * 2, new NamingThreadFactory(WORKER_NAME));

		// initialize schedule thread
		if (usingSchedule) {
			bufferPadSchedule = Executors.newSingleThreadScheduledExecutor(new NamingThreadFactory(SCHEDULE_NAME));
		} else {
			bufferPadSchedule = null;
		}
	}

	/**
	 * 开始调度
	 */
	public void start() {
		if (bufferPadSchedule != null) {
			bufferPadSchedule.scheduleWithFixedDelay(() -> paddingBuffer(), scheduleInterval, scheduleInterval, TimeUnit.SECONDS);
		}
	}

	/**
	 * 提交padding任务到线程池
	 */
	public void asyncPadding() {
		bufferPadExecutors.submit(this::paddingBuffer);
	}

	/**
	 * 关闭线程池
	 */
	public void shutdown() {
		if (!bufferPadExecutors.isShutdown()) {
			bufferPadExecutors.shutdownNow();
		}

		if (bufferPadSchedule != null && !bufferPadSchedule.isShutdown()) {
			bufferPadSchedule.shutdownNow();
		}
	}


	/**
	 * 填充buffer直到赶上 current cursor
	 */
	public void paddingBuffer() {
		LOGGER.info("Ready to padding buffer lastSecond:{}. {}", lastSecond.get(), ringBuffer);

		//同时只能有一个线程在运行,当上次的padding操作还没有完成时,本次不做操作,直接返回
		if (!running.compareAndSet(false, true)) {
			LOGGER.info("Padding buffer is still running. {}", ringBuffer);
			return;
		}

		//是否填充满标记
		boolean isFullRingBuffer = false;
		while (!isFullRingBuffer) {
			//生产uid
			List<Long> uidList = uidProvider.provide(lastSecond.incrementAndGet());
			for (Long uid : uidList) {
				isFullRingBuffer = !ringBuffer.put(uid);
				if (isFullRingBuffer) {
					break;
				}
			}
		}
		//修改标记为false
		running.compareAndSet(true, false);
		LOGGER.info("End to padding buffer lastSecond:{}. {}", lastSecond.get(), ringBuffer);
	}
}
