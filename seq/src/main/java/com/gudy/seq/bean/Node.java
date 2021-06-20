package com.gudy.seq.bean;

import com.alipay.sofa.jraft.rhea.LeaderStateListener;
import com.alipay.sofa.jraft.rhea.client.DefaultRheaKVStore;
import com.alipay.sofa.jraft.rhea.client.RheaKVStore;
import com.alipay.sofa.jraft.rhea.options.RheaKVStoreOptions;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.atomic.AtomicLong;

@Log4j2
@RequiredArgsConstructor
public class Node {

    @NonNull
    private final RheaKVStoreOptions options;

    /**
     * 分布式存储节点 要求强一致性
     */
    @Getter
    private RheaKVStore rheaKVStore;

    private final AtomicLong leaderTerm = new AtomicLong(-1);

    /**
     * 是否leader节点
     * @return
     */
    public boolean isLeader(){
        return leaderTerm.get() > 0;
    }

    /**
     * 停止Node
     */
    public void stop(){
        rheaKVStore.shutdown();
    }

    /**
     * 启动Node
     */
    public void start(){
        //初始化kvstore
        rheaKVStore = new DefaultRheaKVStore();
        rheaKVStore.init(this.options);
        //监听节点状态
        rheaKVStore.addLeaderStateListener(-1, new LeaderStateListener() {
            /**
             * 成为主节点
             * @param newTerm
             */
            @Override
            public void onLeaderStart(long newTerm) {
                log.info("node become leader");
                //设置状态到
                leaderTerm.set(newTerm);
            }

            /**
             * 去掉主节点
             * @param oldTerm
             */
            @Override
            public void onLeaderStop(long oldTerm) {
                //去除本地 leader状态
                leaderTerm.set(-1);
            }
        });

    }





}

