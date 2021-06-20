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


import lombok.NoArgsConstructor;
import lombok.ToString;
import thirdpart.hq.MatchData;
import thirdpart.order.OrderStatus;

//不能命名为TradeEvnet ，因为除了成交事件 还有撤单等其他event
@NoArgsConstructor
@ToString
public final class MatchEvent {

    public long timestamp;

    public short mid;

    public long oid;

    public OrderStatus status = OrderStatus.NOT_SET;

    public long tid;

    //撤单数量 成交数量
    public long volume;

    public long price;


    public MatchData copy() {
        return MatchData.builder()
                .timestamp(this.timestamp)
                .mid(this.mid)
                .oid(this.oid)
                .status(this.status)
                .tid(this.tid)
                .volume(this.volume)
                .price(this.price)
                .build();

    }



}
