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


import java.util.concurrent.atomic.AtomicLong;

/**
 * 解决操作系统的伪共享问题(FalseSharing)
 *
 * java8之前解决方案:
 * CPU cache line 通常为 64个字节,我们可以把64个字节填充满来防止该问题
 *  64 bytes = 8 bytes (object reference) + 6 * 8 bytes (padded long) + 8 bytes (a long value)
 *
 * Java8中官方的解决方案
 * 加上这个注解 {@link sun.misc.Contended} 的类或者字段会自动补齐缓存行,同时需要在jvm启动时设置-XX:-RestrictContended才会生效
 *<p>
 * public final static class VolatileLong {
 *     @sun.misc.Contended
 *     public volatile long value = 0L;
 * }
 * </p>
 */
public class PaddedAtomicLong extends AtomicLong {
    private static final long serialVersionUID = -3415778863941386253L;

    /** Padded 6 long (48 bytes) */

    public volatile long p1, p2, p3, p4, p5, p6 = 7L;


    public PaddedAtomicLong() {
        super();
    }

    public PaddedAtomicLong(long initialValue) {
        super(initialValue);
    }

    /**
     * 防止GC优化以清除未使用的填充引用
     * @return
     */
    public long sumPaddingToPreventOptimization() {
        return p1 + p2 + p3 + p4 + p5 + p6;
    }

}