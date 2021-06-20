package com.gudy.engine.handler;

import com.gudy.engine.bean.command.RbCmd;
import com.lmax.disruptor.EventHandler;

public abstract class BaseHandler implements EventHandler<RbCmd> {
}
