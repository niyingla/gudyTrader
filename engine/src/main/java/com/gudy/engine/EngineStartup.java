package com.gudy.engine;

import com.gudy.engine.bean.EngineConfig;
import thirdpart.checksum.ByteCheckSum;
import thirdpart.codec.BodyCodec;
import thirdpart.codec.MsgCodec;

public class EngineStartup {
    public static void main(String[] args) throws Exception {
        new EngineConfig(
                "engine.properties",
                new BodyCodec(),
                new ByteCheckSum(),
                new MsgCodec()
        ).startup();
    }
}
