package io.hashmatrix.governance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 数据治理分系统启动类。
 *
 * <p>工程基座：分层骨架（api/app/domain/infra）+ 多租户上下文路由 + PG(JSONB)/ES 连通，
 * 公共能力经 libs-java starter 复用。自研元模型引擎业务在 #1 之上落地。
 */
@SpringBootApplication
public class GovernanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GovernanceApplication.class, args);
    }
}
