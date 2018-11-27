package com.github.edgewalk.uid.Buffer;

import java.util.List;

/**
 * buffered Uid 提供者
 * 
 */
@FunctionalInterface
public interface BufferedUidProvider {

    /**
     * 提供 uid
     * 
     * @param momentInSecond
     * @return
     */
    List<Long> provide(long momentInSecond);
}