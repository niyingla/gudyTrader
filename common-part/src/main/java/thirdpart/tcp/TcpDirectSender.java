package thirdpart.tcp;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import lombok.NonNull;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

@Log4j2
@RequiredArgsConstructor
public class TcpDirectSender {

    /**
     * tcp目标发送工具  （发送到网关 ）
     */

    @NonNull
    private String ip;

    @NonNull
    private int port;

    @NonNull
    private Vertx vertx;

    //////////////////////////////////////////////////////////////////////////////

    /**
     * 取最新的（重连会重置）
     */
    private volatile NetSocket socket;

    public void startup() {
        //开启与目标的链接
        vertx.createNetClient().connect(port, ip, new ClientConnHandler());

        //单独启动一个线程取阻塞队列中的数据写入网关
        new Thread(() -> {
            while (true) {
                try {
                    //从BlockingQueue取出一个队首的对象，如果在指定时间内，
                    //队列一旦有数据可取，则立即返回队列中的数据。否则知道时间,超时还没有数据可取，返回失败。
                    Buffer msgBuffer = sendCache.poll(5, TimeUnit.SECONDS);
                    if (msgBuffer != null && msgBuffer.length() > 0 && socket != null) {
                        //远程链接写入
                        socket.write(msgBuffer);
                    }
                } catch (Exception e) {
                    log.error("msg send fail,continue");
                }
            }
        }).start();

    }

    /**
     * 队列作用 已解耦代码
     * socket自己去缓存中取数据进行发送
     * 客户请求写入队列
     * 单独线程去读队列 写到网关
     */
    private final BlockingQueue<Buffer> sendCache = new LinkedBlockingDeque<>();


    /**
     * 发送消息方法
     * @param bufferMsg
     * @return
     */
    public boolean send(Buffer bufferMsg) {
        //表示如果可能的话,将对象加到BlockingQueue里,即如果BlockingQueue可以容纳,则返回true,否则返回false
        return sendCache.offer(bufferMsg);
    }


    /**
     * 处理链接类
     */
    private class ClientConnHandler implements Handler<AsyncResult<NetSocket>> {

        /**
         * 重新了解
         */
        private void reconnect() {
            //5s 重试一次链接
            vertx.setTimer(1000 * 5, r -> {
                log.info("try reconnect to server to {}:{} failed", ip, port);
                //重新创建链接
                vertx.createNetClient().connect(port, ip, new ClientConnHandler());
            });
        }

        @Override
        public void handle(AsyncResult<NetSocket> result) {
            //链接创建成功 设置处理器
            if (result.succeeded()) {
                log.info("connect success to remote {}:{}", ip, port);
                socket = result.result();

                //链接关闭
                socket.closeHandler(close -> {
                    log.info("connect to remote {} closed", socket.remoteAddress());
                    //重连
                    reconnect();
                });

                //链接保存
                socket.exceptionHandler(ex -> { log.error("error exist", ex.getCause()); });
            } else {
            }
        }
    }
}
