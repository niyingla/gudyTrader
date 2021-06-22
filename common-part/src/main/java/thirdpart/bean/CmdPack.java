package thirdpart.bean;

import lombok.*;
import thirdpart.order.OrderCmd;

import java.io.Serializable;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class CmdPack implements Serializable {
    /**
     * 发送包 序号
     */

    private long packNo;

    /**
     * 下单命令
     */
    private List<OrderCmd> orderCmds;

}
