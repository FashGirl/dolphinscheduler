/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.plugin.alert.dingtalk;

import org.apache.dolphinscheduler.alert.api.AlertResult;
import org.apache.dolphinscheduler.common.utils.JSONUtils;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 *     https://open.dingtalk.com/document/robots/custom-robot-access
 *     https://open.dingtalk.com/document/robots/customize-robot-security-settings
 * </p>
 */
public final class DingTalkSender {

    private static final Logger logger = LoggerFactory.getLogger(DingTalkSender.class);
    private final String url;
    private final String keyword;
    private final String secret;
    private String msgType;

    private final String atMobiles;
    private final String atUserIds;
    private final Boolean atAll;

    private final Boolean enableProxy;

    private String proxy;

    private Integer port;

    private String user;

    private String password;

    DingTalkSender(Map<String, String> config) {
        url = config.get(DingTalkParamsConstants.NAME_DING_TALK_WEB_HOOK);
        keyword = config.get(DingTalkParamsConstants.NAME_DING_TALK_KEYWORD);
        secret = config.get(DingTalkParamsConstants.NAME_DING_TALK_SECRET);
        msgType = config.get(DingTalkParamsConstants.NAME_DING_TALK_MSG_TYPE);

        atMobiles = config.get(DingTalkParamsConstants.NAME_DING_TALK_AT_MOBILES);
        atUserIds = config.get(DingTalkParamsConstants.NAME_DING_TALK_AT_USERIDS);
        atAll = Boolean.valueOf(config.get(DingTalkParamsConstants.NAME_DING_TALK_AT_ALL));

        enableProxy = Boolean.valueOf(config.get(DingTalkParamsConstants.NAME_DING_TALK_PROXY_ENABLE));
        if (Boolean.TRUE.equals(enableProxy)) {
            port = Integer.parseInt(config.get(DingTalkParamsConstants.NAME_DING_TALK_PORT));
            proxy = config.get(DingTalkParamsConstants.NAME_DING_TALK_PROXY);
            user = config.get(DingTalkParamsConstants.NAME_DING_TALK_USER);
            password = config.get(DingTalkParamsConstants.NAME_DING_TALK_PASSWORD);
        }
    }

    private static HttpPost constructHttpPost(String url, String msg) {
        HttpPost post = new HttpPost(url);
        StringEntity entity = new StringEntity(msg, StandardCharsets.UTF_8);
        post.setEntity(entity);
        post.addHeader("Content-Type", "application/json; charset=utf-8");
        return post;
    }

    private static CloseableHttpClient getProxyClient(String proxy, int port, String user, String password) {
        HttpHost httpProxy = new HttpHost(proxy, port);
        CredentialsProvider provider = new BasicCredentialsProvider();
        provider.setCredentials(new AuthScope(httpProxy), new UsernamePasswordCredentials(user, password));
        return HttpClients.custom().setDefaultCredentialsProvider(provider).build();
    }

    private static CloseableHttpClient getDefaultClient() {
        return HttpClients.createDefault();
    }

    private static RequestConfig getProxyConfig(String proxy, int port) {
        HttpHost httpProxy = new HttpHost(proxy, port);
        return RequestConfig.custom().setProxy(httpProxy).build();
    }

    private AlertResult checkSendDingTalkSendMsgResult(String result) {
        AlertResult alertResult = new AlertResult();
        alertResult.setStatus("false");

        if (null == result) {
            alertResult.setMessage("send ding talk msg error");
            logger.info("send ding talk msg error,ding talk server resp is null");
            return alertResult;
        }
        DingTalkSendMsgResponse sendMsgResponse = JSONUtils.parseObject(result, DingTalkSendMsgResponse.class);
        if (null == sendMsgResponse) {
            alertResult.setMessage("send ding talk msg fail");
            logger.info("send ding talk msg error,resp error");
            return alertResult;
        }
        if (sendMsgResponse.errcode == 0) {
            alertResult.setStatus("true");
            alertResult.setMessage("send ding talk msg success");
            return alertResult;
        }
        alertResult.setMessage(String.format("alert send ding talk msg error : %s", sendMsgResponse.getErrmsg()));
        logger.info("alert send ding talk msg error : {}", sendMsgResponse.getErrmsg());
        return alertResult;
    }

    /**
     * send dingtalk msg handler
     *
     * @param title title
     * @param content content
     * @return
     */
    public AlertResult sendDingTalkMsg(String title, String content) {
        AlertResult alertResult;
        try {
            String resp = sendMsg(title, content);
            return checkSendDingTalkSendMsgResult(resp);
        } catch (Exception e) {
            logger.info("send ding talk alert msg  exception : {}", e.getMessage());
            alertResult = new AlertResult();
            alertResult.setStatus("false");
            alertResult.setMessage("send ding talk alert fail.");
        }
        return alertResult;
    }

    private String sendMsg(String title, String content) throws IOException {

        String msg = generateMsgJson(title, content);

        HttpPost httpPost = constructHttpPost(
                org.apache.commons.lang3.StringUtils.isBlank(secret) ? url : generateSignedUrl(), msg);

        CloseableHttpClient httpClient;
        if (Boolean.TRUE.equals(enableProxy)) {
            httpClient = getProxyClient(proxy, port, user, password);
            RequestConfig rcf = getProxyConfig(proxy, port);
            httpPost.setConfig(rcf);
        } else {
            httpClient = getDefaultClient();
        }

        try {
            CloseableHttpResponse response = httpClient.execute(httpPost);
            String resp;
            try {
                HttpEntity entity = response.getEntity();
                resp = EntityUtils.toString(entity, "UTF-8");
                EntityUtils.consume(entity);
            } finally {
                response.close();
            }
            logger.info("Ding Talk send msg :{}, resp: {}", msg, resp);
            return resp;
        } finally {
            httpClient.close();
        }
    }

    /**
     * extract executor phone numbers from alert content JSON
     */
    private List<String> extractExecutorPhones(String content) {
        List<String> phones = new ArrayList<>();
        try {
            List<Map> dataList = JSONUtils.toList(content, Map.class);
            if (dataList != null) {
                for (Map<?, ?> data : dataList) {
                    Object phone = data.get("taskExecutorPhone");
                    if (phone != null && org.apache.commons.lang3.StringUtils.isNotBlank(phone.toString())) {
                        String phoneStr = phone.toString().trim();
                        if (!phones.contains(phoneStr)) {
                            phones.add(phoneStr);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore parse errors, fall back to static config
        }
        return phones;
    }

    /**
     * generate msg json
     *
     * @param title title
     * @param content content
     * @return msg
     */
    private String generateMsgJson(String title, String content) {
        if (org.apache.commons.lang3.StringUtils.isBlank(msgType)) {
            msgType = DingTalkParamsConstants.DING_TALK_MSG_TYPE_TEXT;
        }
        Map<String, Object> items = new HashMap<>();
        items.put("msgtype", msgType);
        Map<String, Object> text = new HashMap<>();
        items.put(msgType, text);

        List<String> dynamicPhones = extractExecutorPhones(content);

        if (DingTalkParamsConstants.DING_TALK_MSG_TYPE_MARKDOWN.equals(msgType)) {
            generateMarkdownMsg(title, content, text, dynamicPhones);
        } else {
            generateTextMsg(title, content, text);
        }

        setMsgAt(items, dynamicPhones);
        return JSONUtils.toJsonString(items);

    }

    /**
     * generate text msg
     *
     * @param title title
     * @param content content
     * @param text text
     */
    private void generateTextMsg(String title, String content, Map<String, Object> text) {
        StringBuilder builder = new StringBuilder(title);
        builder.append("\n");
        builder.append(content);
        if (org.apache.commons.lang3.StringUtils.isNotBlank(keyword)) {
            builder.append(" ");
            builder.append(keyword);
        }
        byte[] byt = StringUtils.getBytesUtf8(builder.toString());
        String txt = StringUtils.newStringUtf8(byt);
        text.put("content", txt);
    }

    /**
     * generate markdown msg
     *
     * @param title title
     * @param content content
     * @param text text
     * @param dynamicPhones executor phone numbers extracted from alert content
     */
    private void generateMarkdownMsg(String title, String content, Map<String, Object> text,
                                     List<String> dynamicPhones) {
        // StringBuilder builder = new StringBuilder(content);

        StringBuilder builder = new StringBuilder();

        try {
            List<Map> dataList = JSONUtils.toList(content, Map.class);
            List<Map> failureTasks = dataList.stream()
                    .filter(map -> "FAILURE".equals(map.get("taskState")))
                    .collect(Collectors.toList());
            if (!failureTasks.isEmpty()) {
                dataList = failureTasks;
            }

            for (Map<String, Object> data : dataList) {
                String taskState = (String) data.getOrDefault("taskState", "");
                if (org.apache.commons.lang3.StringUtils.isNotBlank(taskState)) {
                    taskState = convertStateString(taskState);
                    data.put("taskState", taskState);
                }
                String processState = (String) data.getOrDefault("processState", "");
                if (org.apache.commons.lang3.StringUtils.isNotBlank(processState)) {
                    processState = convertStateString(processState);
                    data.put("processState", processState);
                }

                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    builder.append("- **").append(entry.getKey()).append("**：").append(entry.getValue()).append("\n");
                }
                builder.append("\n---\n\n"); // 分隔线，支持多条任务
                // if ("SERVER_DOWN".equals(data.get("event"))){
                // builder.append("- **服务名称**：").append(data.getOrDefault("serverName", "")).append("\n");
                // } else if (data.get("projectName") != null && data.get("taskName") != null && data.get("projectCode")
                // != null) {
                // String taskState = (String) data.getOrDefault("taskState", "");
                // switch (taskState) {
                // case "SUCCESS":
                // taskState = "✅ SUCCESS";
                // break;
                // case "FAILURE":
                // taskState = "❌ FAILURE";
                // break;
                // case "KILL":
                // taskState = "⛔ KILL";
                // break;
                // }
                //
                // builder.append("- **项目名称**：").append(data.getOrDefault("projectName", "")).append("\n");
                // builder.append("- **任务名称**：").append(data.getOrDefault("taskName", "")).append("\n");
                // builder.append("- **任务类型**：").append(data.getOrDefault("taskType", "")).append("\n");
                // builder.append("- **任务状态**：").append(taskState).append("\n");
                // builder.append("- **工作流名称**：").append(data.getOrDefault("processName", "")).append("\n");
                // builder.append("- **工作流PID**：").append(data.getOrDefault("processId", "")).append("\n");
                // builder.append("- **执行时间**：")
                // .append(data.getOrDefault("taskStartTime", "")).append(" ~ ")
                // .append(data.getOrDefault("taskEndTime", "")).append("\n");
                // builder.append("- **执行机器**：").append(data.getOrDefault("taskHost", "")).append("\n");
                // builder.append("- **日志文件**：").append(data.getOrDefault("logPath", "")).append("\n");
                //
                // builder.append("\n---\n\n"); // 分隔线，支持多条任务
                // } else {
                // builder.append(content);
                // break;
                // }
            }

        } catch (Exception e) {
            // fallback: 内容不是 JSON，原样输出
            builder.append(content);
        }

        if (builder.length() == 0) {
            builder.append(content);
        }
        builder.insert(0, "### 🐬 DolphinScheduler 任务告警\n\n");

        if (org.apache.commons.lang3.StringUtils.isNotBlank(keyword)) {
            builder.append(" ");
            builder.append(keyword);
        }
        builder.append("\n\n");
        // prefer dynamic executor phones; fall back to static config phones
        if (!dynamicPhones.isEmpty()) {
            dynamicPhones.forEach(phone -> builder.append("@").append(phone).append(" "));
        } else if (org.apache.commons.lang3.StringUtils.isNotBlank(atMobiles)) {
            Arrays.stream(atMobiles.split(",")).forEach(value -> builder.append("@").append(value).append(" "));
        }
        if (org.apache.commons.lang3.StringUtils.isNotBlank(atUserIds)) {
            Arrays.stream(atUserIds.split(",")).forEach(value -> builder.append("@").append(value).append(" "));
        }

        byte[] byt = StringUtils.getBytesUtf8(builder.toString());
        String txt = StringUtils.newStringUtf8(byt);
        text.put("title", title);
        text.put("text", txt);
    }

    private String convertStateString(String processState) {
        switch (processState) {
            case "SUCCESS":
                processState = "✅ SUCCESS";
                break;
            case "FAILURE":
                processState = "❌ FAILURE";
                break;
            case "KILL":
                processState = "⛔ KILL";
                break;
        }
        return processState;
    }

    /**
     * configure msg @person
     *
     * @param items items
     * @param dynamicPhones executor phone numbers extracted from alert content
     */
    private void setMsgAt(Map<String, Object> items, List<String> dynamicPhones) {
        Map<String, Object> at = new HashMap<>();

        // merge static config phones with dynamic executor phones (deduplicated)
        List<String> allPhones = new ArrayList<>();
        if (org.apache.commons.lang3.StringUtils.isNotBlank(atMobiles)) {
            Arrays.stream(atMobiles.split(","))
                    .map(String::trim)
                    .filter(org.apache.commons.lang3.StringUtils::isNotBlank)
                    .forEach(allPhones::add);
        }
        dynamicPhones.stream()
                .filter(p -> !allPhones.contains(p))
                .forEach(allPhones::add);

        String[] atUserArray =
                org.apache.commons.lang3.StringUtils.isNotBlank(atUserIds) ? atUserIds.split(",")
                        : new String[0];
        boolean isAtAll = Objects.isNull(atAll) ? false : atAll;

        at.put("atMobiles", allPhones.toArray(new String[0]));
        at.put("atUserIds", atUserArray);
        at.put("isAtAll", isAtAll);

        items.put("at", at);
    }

    /**
     * generate sign url
     *
     * @return sign url
     */
    private String generateSignedUrl() {
        Long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;
        String sign = org.apache.commons.lang3.StringUtils.EMPTY;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes("UTF-8"));
            sign = URLEncoder.encode(new String(Base64.encodeBase64(signData)), "UTF-8");
        } catch (Exception e) {
            logger.error("generate sign error, message:{}", e);
        }
        return url + "&timestamp=" + timestamp + "&sign=" + sign;
    }

    static final class DingTalkSendMsgResponse {

        private Integer errcode;
        private String errmsg;

        public DingTalkSendMsgResponse() {
        }

        public Integer getErrcode() {
            return this.errcode;
        }

        public void setErrcode(Integer errcode) {
            this.errcode = errcode;
        }

        public String getErrmsg() {
            return this.errmsg;
        }

        public void setErrmsg(String errmsg) {
            this.errmsg = errmsg;
        }

        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof DingTalkSendMsgResponse)) {
                return false;
            }
            final DingTalkSendMsgResponse other = (DingTalkSendMsgResponse) o;
            final Object this$errcode = this.getErrcode();
            final Object other$errcode = other.getErrcode();
            if (this$errcode == null ? other$errcode != null : !this$errcode.equals(other$errcode)) {
                return false;
            }
            final Object this$errmsg = this.getErrmsg();
            final Object other$errmsg = other.getErrmsg();
            if (this$errmsg == null ? other$errmsg != null : !this$errmsg.equals(other$errmsg)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            final Object $errcode = this.getErrcode();
            result = result * PRIME + ($errcode == null ? 43 : $errcode.hashCode());
            final Object $errmsg = this.getErrmsg();
            result = result * PRIME + ($errmsg == null ? 43 : $errmsg.hashCode());
            return result;
        }

        @Override
        public String toString() {
            return "DingTalkSender.DingTalkSendMsgResponse(errcode=" + this.getErrcode() + ", errmsg="
                    + this.getErrmsg() + ")";
        }
    }
}
