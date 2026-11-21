package com.example.asset.modules.admin.service;

import com.example.asset.modules.admin.dto.LoginDTO;
import com.example.asset.modules.admin.dto.LoginResult;
import com.example.asset.modules.admin.dto.RegisterDTO;

public interface AuthService {
    LoginResult login(LoginDTO loginDTO);

    LoginResult register(RegisterDTO registerDTO);
}
