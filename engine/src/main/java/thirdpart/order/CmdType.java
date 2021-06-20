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
package thirdpart.order;

import lombok.Getter;


/**
 * 指令类型
 */
@Getter
public enum CmdType {


    ///////////////委托类//////////////
    NEW_ORDER(0),
    CANCEL_ORDER(1),

    //////权限类(交易所几乎没有用过,都是盘后改，初始化的时候载入新数据)////////
    SUSPEND_USER(2),
    RESUME_USER(3),

    ///////////////状态类//////////////
    SHUTDOWN_ENGINE(4),

    ///////////////查询类//////////////
    BINARY_DATA(5),
    ORDER_BOOK_REQUEST(6),

    ///////////////行情类//////////////
    HQ_PUB(7),


    ///////////////资金类//////////////
    BALANCE_ADJUSTMENT(8);


    private short type;

    CmdType(int type) {
        this.type = (short) type;
    }



    public static CmdType of(short type) {
        //柜台只能处理三种
        switch (type) {
            case 0:
                return NEW_ORDER;
            case 1:
                return CANCEL_ORDER;
            case 8:
                return BALANCE_ADJUSTMENT;
            default:
                throw new IllegalArgumentException("unknown CmdType:" + type);
        }
    }
}
