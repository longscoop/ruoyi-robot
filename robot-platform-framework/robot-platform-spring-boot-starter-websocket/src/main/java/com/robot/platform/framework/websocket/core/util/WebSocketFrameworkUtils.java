package com.robot.platform.framework.websocket.core.util;

import com.robot.platform.framework.security.core.LoginUser;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * 专属于 web 包的工具类
 *
 * @author 芋道源码
 */
public class WebSocketFrameworkUtils {

    public static final String ATTRIBUTE_LOGIN_USER = "LOGIN_USER";
    public static final String ATTRIBUTE_TENANT_ID = "TENANT_ID";
    public static final String ATTRIBUTE_SUBJECT_ID = "SUBJECT_ID";
    public static final String ATTRIBUTE_SUBJECT_TYPE = "SUBJECT_TYPE";

    /**
     * 设置当前用户
     *
     * @param loginUser 登录用户
     * @param attributes Session
     */
    public static void setLoginUser(LoginUser loginUser, Map<String, Object> attributes) {
        attributes.put(ATTRIBUTE_LOGIN_USER, loginUser);
        // Freeze routing identity at handshake time; do not rely on a later request thread context.
        attributes.put(ATTRIBUTE_TENANT_ID, loginUser.getTenantId());
        attributes.put(ATTRIBUTE_SUBJECT_ID, loginUser.getId());
        attributes.put(ATTRIBUTE_SUBJECT_TYPE, loginUser.getUserType());
    }

    /**
     * 获取当前用户
     *
     * @return 当前用户
     */
    public static LoginUser getLoginUser(WebSocketSession session) {
        return (LoginUser) session.getAttributes().get(ATTRIBUTE_LOGIN_USER);
    }

    /**
     * 获得当前用户的编号
     *
     * @return 用户编号
     */
    public static Long getLoginUserId(WebSocketSession session) {
        LoginUser loginUser = getLoginUser(session);
        return loginUser != null ? loginUser.getId() : null;
    }

    /**
     * 获得当前用户的类型
     *
     * @return 用户编号
     */
    public static Integer getLoginUserType(WebSocketSession session) {
        LoginUser loginUser = getLoginUser(session);
        return loginUser != null ? loginUser.getUserType() : null;
    }

    /**
     * 获得当前用户的租户编号
     *
     * @param session Session
     * @return 租户编号
     */
    public static Long getTenantId(WebSocketSession session) {
        Object tenant = session.getAttributes().get(ATTRIBUTE_TENANT_ID);
        return tenant instanceof Long ? (Long) tenant : null;
    }

}
