package com.lawoffice.framework.req;

import lombok.Data;
import java.io.Serializable;

/**
 * 请求对象基类
 */
@Data
public class BaseReq implements Serializable {
    private static final long serialVersionUID = 1L;
    String id;
}
