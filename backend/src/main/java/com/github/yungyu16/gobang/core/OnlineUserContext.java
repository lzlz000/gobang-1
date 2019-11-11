package com.github.yungyu16.gobang.core;

import cn.xiaoshidai.common.toolkit.base.StringTools;
import com.alibaba.fastjson.JSONObject;
import com.github.yungyu16.gobang.base.WebSockOperationBase;
import com.github.yungyu16.gobang.core.entity.UserInfo;
import com.github.yungyu16.gobang.dao.entity.UserRecord;
import com.github.yungyu16.gobang.domain.UserDomain;
import com.github.yungyu16.gobang.event.SessionTokenEvent;
import com.github.yungyu16.gobang.web.websocket.msg.MsgTypes;
import com.github.yungyu16.gobang.web.websocket.msg.OutputMsg;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Yungyu
 * @description Created by Yungyu on 2019/11/10.
 */
@Component
public class OnlineUserContext extends WebSockOperationBase implements InitializingBean, ApplicationListener<SessionTokenEvent> {

    @Autowired
    private UserDomain userDomain;
    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder().setNameFormat("refresh-user-th-%s").build());

    private Map<Integer, String> userIdTokenMappings = Maps.newConcurrentMap();
    private Map<String, UserInfo> sessionUserMappings = Maps.newConcurrentMap();
    private Map<String, LocalDateTime> activeTokenMappings = Maps.newConcurrentMap();
    private Map<WebSocketSession, String> sessionTokenMappings = Maps.newConcurrentMap();

    public void sendMsg2User(Integer userId, OutputMsg msg) {
        if (userId == null) {
            return;
        }
        if (msg == null) {
            return;
        }
        getUserInfoByUserId(userId)
                .map(it -> {
                    UserRecord userRecord = it.getUserRecord();
                    WebSocketSession socketSession = it.getSocketSession();
                    log.info("开始发送消息给 {}", userRecord.getUserName());
                    sendMsg(socketSession, msg);
                    return 1;
                })
                .orElseGet(() -> {
                    log.info("用户ws会话不存在...{}", userId);
                    return 1;
                });
    }

    public Optional<WebSocketSession> getWsSessionByUserId(Integer userId) {
        return getUserInfoByUserId(userId)
                .map(UserInfo::getSocketSession);
    }

    public Optional<UserInfo> getUserInfoByUserId(Integer userId) {
        Optional<String> sessionTokenOpt = getSessionTokenByUserId(userId);
        if (!sessionTokenOpt.isPresent()) {
            return Optional.empty();
        }
        String sessionToken = sessionTokenOpt.get();
        return getUserInfoBySessionToken(sessionToken);
    }

    public Optional<String> getSessionTokenByUserId(Integer userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(userIdTokenMappings.get(userId));
    }

    public Optional<UserInfo> getUserInfoBySessionToken(String token) {
        if (StringTools.isBlank(token)) {
            return Optional.empty();
        }
        UserInfo userInfo = sessionUserMappings.get(token);
        return Optional.of(userInfo);
    }

    public Optional<String> getSessionTokenByWsSession(WebSocketSession session) {
        if (session == null) {
            return Optional.empty();
        }
        String token = sessionTokenMappings.get(session);
        return Optional.ofNullable(token);
    }

    @Override
    public void afterPropertiesSet() {
        executorService.scheduleAtFixedRate(this::refreshActiveUser, 0, 10, TimeUnit.SECONDS);
    }

    public void newUserInfo(WebSocketSession session, String sessionToken) {
        getSessionUserId(sessionToken)
                .map(userId -> {
                    UserRecord userDomainById = userDomain.getById(userId);
                    if (userDomainById == null) {
                        log.info("用户不存在...{} {}", userId, sessionToken);
                        return 1;
                    }
                    //log.info("收到 {} 用户的心跳...", userDomainById.getUserName());
                    UserInfo newUserInfo = new UserInfo(session, userDomainById);
                    sessionUserMappings.put(sessionToken, newUserInfo);
                    activeTokenMappings.put(sessionToken, LocalDateTime.now());
                    userIdTokenMappings.put(userId, sessionToken);
                    sessionTokenMappings.put(session, sessionToken);
                    return 1;
                })
                .orElseGet(() -> {
                    log.info("session已失效...");
                    return 1;
                });
    }

    public void discardUserInfo(String sessionToken) {
        UserInfo userInfo = sessionUserMappings.get(sessionToken);
        if (userInfo == null) {
            sessionUserMappings.remove(sessionToken);
            activeTokenMappings.remove(sessionToken);
            return;
        }
        WebSocketSession session = userInfo.getSocketSession();
        sendMsg(session, OutputMsg.of("error", "会话过期..."));
        doInEventLoop(session, () -> {
            UserRecord userRecord = userInfo.getUserRecord();
            log.info("会话过期,移除当前会话....{}", userRecord.getUserName());
            Integer id = userRecord.getId();
            userIdTokenMappings.remove(id);
            sessionUserMappings.remove(sessionToken);
            activeTokenMappings.remove(sessionToken);
            sessionTokenMappings.remove(session);
        });
        close(session);
    }

    @Override
    public void onApplicationEvent(SessionTokenEvent event) {
        log.info("用户退出,删除失效session...");
        String type = event.getType();
        if (StringTools.equalsIgnoreCase(SessionTokenEvent.TYPE_REMOVE, type)) {
            String token = event.getToken();
            if (StringTools.isBlank(token)) {
                return;
            }
            discardUserInfo(token);
        }
    }

    private void refreshActiveUser() {
        //log.info("开始刷新连接列表...");
        LocalDateTime now = LocalDateTime.now();
        activeTokenMappings.forEach((token, value) -> {
            long seconds = Duration.between(now, value).abs().getSeconds();
            if (seconds >= 10) {
                discardUserInfo(token);
            }
        });
    }

    public List<JSONObject> getOnlineUsers(String sessionToken) {
        List<JSONObject> userList = sessionUserMappings.values()
                .stream()
                .map(it -> {
                    JSONObject userInfo = new JSONObject();
                    UserRecord userRecord = it.getUserRecord();
                    String userName = userRecord.getUserName();
                    String status = "空闲";
                    userInfo.put("userId", userRecord.getId());
                    userInfo.put("userName", userName);
                    userInfo.put("status", status);
                    return userInfo;
                }).collect(Collectors.toList());
        //log.info("开始推送在线用户列表...{}", userList.size());
        if (StringTools.isNotBlank(sessionToken)) {
            UserInfo userInfo = sessionUserMappings.get(sessionToken);
            if (userInfo != null) {
                userList = userList.stream()
                        .filter(it -> !Objects.equals(it.getInteger("userId"), userInfo.getUserRecord().getId()))
                        .collect(Collectors.toList());
            }
        }
        //log.info("当前用户列表：{}", JSON.toJSONString(userList));
        return userList;
    }

    public void ping(WebSocketSession webSocketSession, String sessionToken) {
        if (StringTools.isBlank(sessionToken)) {
            log.info("sessionToken为空");
            return;
        }
        touch(sessionToken);
        UserInfo userInfo = sessionUserMappings.get(sessionToken);
        if (userInfo != null) {
            //log.info("收到 {} 用户的心跳...", userInfo.getUserRecord().getUserName());
            return;
        }
        newUserInfo(webSocketSession, sessionToken);
    }

    public void pushOnlineUsers(WebSocketSession webSocketSession, String sessionToken) {
        List<JSONObject> onlineUsers = getOnlineUsers(sessionToken);
        //log.info("当前用户列表：{}", JSON.toJSONString(onlineUsers));
        sendMsg(webSocketSession, OutputMsg.of(MsgTypes.USER_MSG_USER_LIST, onlineUsers));
    }

    private void touch(String sessionToken) {
        if (StringTools.isBlank(sessionToken)) {
            return;
        }
        activeTokenMappings.put(sessionToken, LocalDateTime.now());
    }
}
