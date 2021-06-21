package com.gudy.counter;

import com.gudy.counter.config.CounterConfig;
import com.gudy.counter.util.DbUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import thirdpart.uuid.GudyUuid;

import javax.annotation.PostConstruct;

//localhost:8080  localhost:8090
@SpringBootApplication
public class CounterApplication {

    @Autowired
    private DbUtil dbUtil;

    @Autowired
    private CounterConfig counterConfig;

    @PostConstruct
    private void init(){
        //初始化 uuid 雪花生成器
        GudyUuid.getInstance().init(counterConfig.getDataCenterId(), counterConfig.getWorkerId());
    }

    public static void main(String[] args) {
        SpringApplication.run(CounterApplication.class, args);
    }

}
