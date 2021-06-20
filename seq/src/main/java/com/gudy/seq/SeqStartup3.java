package com.gudy.seq;

import com.gudy.seq.bean.SeqConfig;
import thirdpart.codec.BodyCodec;

public class SeqStartup3 {

    /**
     * C 一致性 A 可用性 P 分区容错性
     * 分布式 只能选 A P 和 C P
     * 保障一致性 放弃高可用 CP 例子 zookeeper redis
     * 保障可用性 Eureka NACOS
     *
     * raft 也是强一致性 各个节点均完成才会执行下一步
     * 主节点选举
     * 日志复制
     *
     * @param args
     * @throws Exception
     */

    public static void main(String[] args) throws Exception {
        String configName = "seq3.properties";
        new SeqConfig(configName,new BodyCodec()).startup();
    }

}
