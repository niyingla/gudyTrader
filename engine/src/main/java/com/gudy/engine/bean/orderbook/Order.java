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
package com.gudy.engine.bean.orderbook;

import lombok.*;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import thirdpart.order.OrderDirection;

//只在OrderBook内部使用
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@ToString
public final class Order {

    /**
     * 会员ID
     */
    private short mid;

    /**
     * 用户ID
     */
    private long uid;

    /**
     * 代码
     */
    private int code;

    /**
     * 方向
     */
    private OrderDirection direction;

    /**
     * 价格
     */
    private long price;

    /**
     * 量
     */
    private long volume;

    /**
     * 已成交量
     */
    private long tvolume;

    /**
     * 委托编号
     */
    private long oid;

    /**
     * 时间戳
     */
    private long timestamp;

    /**
     * 内部排序顺序
     */
    private long innerOid;

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(mid)
                .append(uid)
                .append(code)
                .append(direction)
                .append(price)
                .append(volume)
                .append(tvolume)
                .append(oid)
//                .append(timestamp)
                .toHashCode();
    }

    /**
     * timestamp is not included into hashCode() and equals() for repeatable results
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        Order order = (Order) o;

        return new EqualsBuilder()
                .append(mid, order.mid)
                .append(uid, order.uid)
                .append(code, order.code)
                .append(price, order.price)
                .append(volume, order.volume)
                .append(tvolume, order.tvolume)
                .append(oid, order.oid)
//                .append(timestamp, order.timestamp)
                .append(direction, order.direction)
                .isEquals();
    }
}
