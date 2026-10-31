package com.deepsea.lumenote.search.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.Objects;

@Component
@Slf4j
public class CanalClient implements DisposableBean {

    @Autowired
    private CanalProperties canalProperties;

    private CanalConnector canalConnector;

    /**
     * 实例化 Canal 连接对象
     */
    @Bean
    public CanalConnector canalConnector() {
        // Canal IP 地址
        String[] addressArr = canalProperties.getAddress().split(":");
        String host = addressArr[0];
        // 端口地址
        int port = Integer.parseInt(addressArr[1]);

        // 实例化 Canal 连接对象
        canalConnector = CanalConnectors.newSingleConnector(
                new InetSocketAddress(host, port),
                canalProperties.getDestination(),
                canalProperties.getUsername(),
                canalProperties.getPassword());

        // 连接 Canal 服务
        canalConnector.connect();
        // 订阅 Canal 数据
        canalConnector.subscribe(canalProperties.getSubscribe());
        // 回滚 Canal 消费者的位点，回滚到上次提交的消费位置
        canalConnector.rollback();
        // 返回 Canal 连接对象
        return canalConnector;
    }

    /**
     * spring 容器销毁时释放资源
     */
    @Override
    public void destroy() throws Exception {
        if (Objects.nonNull(canalConnector)) {
            // 断开 canalConnector 与 Canal 服务的连接
            canalConnector.disconnect();
        }
    }
}
