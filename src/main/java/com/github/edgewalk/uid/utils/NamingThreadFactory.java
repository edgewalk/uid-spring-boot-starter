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
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 线程创建工厂
 */
@Slf4j
@Getter
@Setter
public class NamingThreadFactory implements ThreadFactory {


	/*默认线程名*/
	private static final String DEFAULT_NAME = "jh-id";
	/*保存线程名的前缀*/
	private final ConcurrentHashMap<String, AtomicLong> sequences;
	/*线程名*/
	private String name;
	/*是否是守护线程*/
	private boolean daemon;
	/*线程非受检异常处理类*/
	private UncaughtExceptionHandler uncaughtExceptionHandler;


	public NamingThreadFactory() {
		this(null, false, null);
	}

	public NamingThreadFactory(String name) {
		this(name, false, null);
	}

	public NamingThreadFactory(String name, boolean daemon) {
		this(name, daemon, null);
	}

	public NamingThreadFactory(String name, boolean daemon, UncaughtExceptionHandler handler) {
		this.name = name;
		this.daemon = daemon;
		this.uncaughtExceptionHandler = handler;
		this.sequences = new ConcurrentHashMap<String, AtomicLong>();
	}

	@Override
	public Thread newThread(Runnable r) {
		Thread thread = new Thread(r);
		thread.setDaemon(this.daemon);

		//如果没有指定线程名,那么使用默认的线程名
		String name = this.name;
		if (StringUtils.isBlank(name)) {
			name = DEFAULT_NAME;
		}
		thread.setName(name + "-" + getSequence(name));
		/**
		 * 3.RuntimeException:也叫非受检异常(unchecked exception).这类异常是编程人员的逻辑问题,Java编译器不进行强制要求处理。 也就是说，这类异常再程序中，可以进行处理，也可以不处理。
		 * 4.受检异常(checked exception).这类异常是由一些外部的偶然因素所引起的。Java编译器强制要求处理。也就是说，程序必须进行对这类异常进行处理。
		 当一个线程由于发生了非受检异常而终止时，JVM会使用Thread.gerUncaughtExceptionHandler()方法查看该线程上的UncaughtExceptionHandler，并调用他的uncaughtException()方法
		 */
		if (this.uncaughtExceptionHandler != null) {
			thread.setUncaughtExceptionHandler(this.uncaughtExceptionHandler);
		} else {
			thread.setUncaughtExceptionHandler((t, e) -> log.error("unhandled exception in thread: " + t.getId() + ":" + t.getName(), e));
		}
		return thread;
	}


	/**
	 * 获取不同线程名的前缀
	 *
	 * @param name 线程名
	 * @return
	 */
	private long getSequence(String name) {
		AtomicLong r = this.sequences.get(name);
		if (r == null) {
			r = new AtomicLong(0);
			//putIfAbsent不存在则添加,key第一次存储返回null,之后都存储对应key返回已经添加的值
			AtomicLong previous = this.sequences.putIfAbsent(name, r);
			//如果不是null,那么表示对应线程名的AtomicLong已经被创建过,使用即可
			if (previous != null) {
				r = previous;
			}
		}
		return r.incrementAndGet();
	}
}
