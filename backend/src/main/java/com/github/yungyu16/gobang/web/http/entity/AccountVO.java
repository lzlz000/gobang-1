package com.github.yungyu16.gobang.web.http.entity;

import lombok.Data;

/**
 * @author Yungyu
 * @description Created by Yungyu on 2019/11/10.
 */
@Data
public class AccountVO {

    private String sessionToken;

    private String userName;

    private String mobile;
}
