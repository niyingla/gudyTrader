/*
 * Copyright 2019 Maksim Zheravin
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
package com.gudy.engine.handler.exception;

import com.lmax.disruptor.ExceptionHandler;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.function.BiConsumer;

@Log4j2
@AllArgsConstructor
public final class DisruptorExceptionHandler<T> implements ExceptionHandler<T> {

    public final String name;
    public final BiConsumer<Throwable, Long> onException;

    /**
     * 处理事件异常
     * @param ex
     * @param sequence
     * @param event
     */
    @Override
    public void handleEventException(Throwable ex, long sequence, T event) {
        if (log.isDebugEnabled()) {
            log.debug("Disruptor '{}' seq={} caught exception: {}", name, sequence, event, ex);
        }
        //回调报错 （构造入参传入）
        onException.accept(ex, sequence);
    }

    /**
     * 启动异常
     * @param ex
     */
    @Override
    public void handleOnStartException(Throwable ex) {
        if (log.isDebugEnabled()) {
            log.debug("Disruptor '{}' startup exception: {}", name, ex);
        }
    }

    /**
     * 关闭异常
     * @param ex
     */
    @Override
    public void handleOnShutdownException(Throwable ex) {
        if (log.isDebugEnabled()) {
            log.debug("Disruptor '{}' shutdown exception: {}", name, ex);
        }
    }
}
