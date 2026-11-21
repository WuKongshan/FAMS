package com.example.asset.modules.admin.dto;

import lombok.Data;

@Data
public class LoginResult {

    private String token;
    private Long userId;
    private String username;
    private String realName;
    private Long roleId;
    private Long deptId;
    private String deptName;
}
