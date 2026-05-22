# 认证接口

## 登录

- 方法：`POST`
- 路径：`/auth/login`
- 说明：用户名密码登录，返回 JWT token 和默认租户 ID。

请求示例：

```json
{
  "username": "admin",
  "password": "Admin@123"
}
```

响应示例：

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "token": "...",
    "tenantId": "0"
  }
}
```

## 登出

- 方法：`POST`
- 路径：`/auth/logout`
- 说明：清理当前 token。

## 修改密码

- 方法：`POST`
- 路径：`/auth/changePassword`
- 说明：校验旧密码后修改当前用户密码。

请求字段：

- `oldPassword`
- `newPassword`

## 当前用户信息

- 方法：`GET`
- 路径：`/user/info`
- 说明：返回当前用户基本信息、角色、权限和首页路径。

## 前端关联

- `frontend/src/api/system/auth.ts`
- `frontend/src/api/system/user.ts`
- `frontend/src/store/auth.ts`
