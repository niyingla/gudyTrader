package com.gudy.engine.bean;

import com.google.common.collect.Lists;
import com.gudy.engine.bean.command.CmdResultCode;
import com.gudy.engine.bean.command.RbCmd;
import com.lmax.disruptor.EventFactory;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;

public class RbCmdFactory implements EventFactory<RbCmd> {
    @Override
    public RbCmd newInstance() {
        return RbCmd.builder()
                .resultCode(CmdResultCode.SUCCESS)
                .matchEventList(Lists.newArrayList())
                .marketDataMap(new IntObjectHashMap<>())
                .build();
    }
}
