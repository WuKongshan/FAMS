package com.example.asset;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.asset.modules.*.mapper")
public class AssetApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssetApplication.class, args);
        System.out.println("========================================");
        System.out.println("  高校固定资产协同管理系统启动成功!");
        System.out.println("  API文档: http://localhost:9090/doc.html");
        System.out.println("========================================");
    }
}
