package thirdpart.bus;

import thirdpart.bean.CommonMsg;

public interface IBusSender {

    void startup();

    void publish(CommonMsg commonMsg);

}
