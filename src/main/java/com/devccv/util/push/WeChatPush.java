package com.devccv.util.push;

import com.devccv.util.network.SimpleHttps;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 企业微信应用消息推送<br>
 * 封装了企业微信发送应用消息的相关接口<br>
 * <br>
 * 基础频率:<br>
 * 每企业调用单个cgi/api不可超过1万次/分，15万次/小时<br>
 * 每IP调用单个cgi/api不可超过2万次/分，60万次/小时<br>
 * <br>
 * 发送应用消息频率:<br>
 * 每应用不可超过帐号上限数*200人次/天（40000人次）<br>
 * 每应用对同一个成员不可超过30次/分钟，超过部分会被丢弃不下发<br>
 * 发消息频率不计入基础频率
 */
public class WeChatPush {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss");
    private static final String GET_TOKEN_URL = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=%s&corpsecret=%s";
    private static final String PUSH_MESSAGE_URL = "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=";
    /**
     * 素材上传得到media_id，该media_id仅三天内有效<br>
     * media_id在同一企业内应用之间可以共享<br>
     * <br>
     * 所有文件size必须大于5个字节<br>
     * 图片(image)：10MB，支持JPG,PNG格式<br>
     * 语音(voice)：2MB，播放长度不超过60s，仅支持AMR格式<br>
     * 视频(video)：10MB，支持MP4格式<br>
     * 普通文件(file)：20MB
     */
    private static final String TEMPORARY_MEDIA_UPLOAD_URL = "https://qyapi.weixin.qq.com/cgi-bin/media/upload?access_token=%s&type=%s";
    private final String specificGetTokenURL;
    private final int agentID;
    private String access_token;
    private long expiresTime;
    private String pushTarget;
    private String pushTargetType;
    private int safe;
    private String lastReturn;
    private JSONObject lastReturnJson;

    /**
     * 每个应用有独立的secret，获取到的access_token只能本应用使用，所以每个应用的access_token应该分开来获取
     *
     * @param corpId     企业ID
     * @param agentID    企业应用ID
     * @param corpSecret 应用的凭证密钥
     */
    public WeChatPush(String corpId, int agentID, String corpSecret) {
        this.agentID = agentID;
        specificGetTokenURL = String.format(GET_TOKEN_URL, corpId, corpSecret);
    }

    //region Setter

    /**
     * 使用成员ID列表设置推送目标，最大支持1000个成员
     * 参数为 List.of("@all") 则向该企业应用的全部成员发送
     *
     * @param userID 成员ID列表
     */
    public void setPushTargetByUserID(List<String> userID) {
        StringBuilder to = new StringBuilder();
        for (String id : userID) {
            if (!to.isEmpty()) to.append("|");
            to.append(id);
        }
        this.pushTarget = to.toString();
        this.pushTargetType = "touser";
    }

    /**
     * 使用部门ID列表设置推送目标，最大支持100个
     *
     * @param partyID 部门ID列表
     */
    public void setPushTargetByParty(List<String> partyID) {
        StringBuilder to = new StringBuilder();
        for (String id : partyID) {
            if (!to.isEmpty()) to.append("|");
            to.append(id);
        }
        this.pushTarget = to.toString();
        this.pushTargetType = "toparty";
    }

    /**
     * 使用标签ID列表设置推送目标，最大支持100个
     *
     * @param tags 标签ID列表
     */
    public void setPushTargetByTag(List<String> tags) {
        StringBuilder to = new StringBuilder();
        for (String tag : tags) {
            if (!to.isEmpty()) to.append("|");
            to.append(tag);
        }
        this.pushTarget = to.toString();
        this.pushTargetType = "totag";
    }

    /**
     * 表示是否是保密消息，0表示可对外分享，1表示不能分享且内容显示水印，2表示仅限在企业内分享，默认为0
     * 注意仅mpnews类型的消息支持safe值为2，其他消息类型不支持
     *
     * @param safeCode safe
     */
    public void setSafeCode(int safeCode) {
        this.safe = safeCode;
    }
    //endregion

    public enum MediaFileType {
        NORMAL_FILE, JPG_FILE, PNG_FILE, BMP_FILE, AMR_FILE, MP4_FILE
    }

    /**
     * 上传临时素材，返回media_id，三天内有效，出错返回null
     *
     * @param file     要上传的文件
     * @param fileName 文件展示时的名称
     * @param fileType 要上传的文件类型
     * @return 临时media_id，三天有效，出错返回null
     */
    public String uploadTemporaryMedia(File file, String fileName, MediaFileType fileType) {
        if (!checkTokenUpdate()) return null;

        //构造上传链接
        String uploadURL = String.format(TEMPORARY_MEDIA_UPLOAD_URL, access_token, "%s");
        uploadURL = switch (fileType) {
            case NORMAL_FILE -> String.format(uploadURL, "file");
            case JPG_FILE, PNG_FILE, BMP_FILE -> String.format(uploadURL, "image");
            case AMR_FILE -> String.format(uploadURL, "voice");
            case MP4_FILE -> String.format(uploadURL, "video");
        };

        String contentType = switch (fileType) {
            case NORMAL_FILE -> "application/octet-stream";
            case JPG_FILE -> "image/jpg";
            case PNG_FILE -> "image/png";
            case BMP_FILE -> "image/bmp";
            case AMR_FILE -> "voice/amr";
            case MP4_FILE -> "video/mp4";
        };

        try {
            lastReturn = WeChatPush.postUploadMedia(uploadURL, file, fileName, contentType);
            lastReturnJson = new JSONObject(lastReturn);

            //TODO:debug
            if (!getErrMsg().equals("ok")) {
                //System.out.println(getErrMsg());
                return null;
            }

            return lastReturnJson.getString("media_id");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String postUploadMedia(String url, File file, String fileName, String contentType) throws IOException {
        String newLine = "\r\n";
        String boundaryPrefix = "--";
        String boundary = "boundary";

        //请求头
        Map<String, String> requestProperty = new HashMap<>();
        requestProperty.put("Content-Type", "multipart/form-data; boundary=" + boundary);

        //POST主体头部
        String post = newLine + boundaryPrefix + boundary + newLine +
                      "Content-Disposition: form-data; name=\"media\";filename=\"" +
                      fileName + file.getName().substring(file.getName().lastIndexOf(".")) +
                      "\"; filelength=" + file.length() + newLine +
                      "Content-Type: " + contentType + newLine +
                      newLine;
        byte[] startOfPost = post.getBytes(StandardCharsets.UTF_8);

        //文件的二进制内容
        byte[] fileAllBytes;
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            fileAllBytes = fileInputStream.readAllBytes();
        }

        //POST主体结尾
        byte[] endOfPost = (newLine + boundaryPrefix + boundary + boundaryPrefix + newLine).getBytes(StandardCharsets.UTF_8);

        ByteBuffer byteBuffer = ByteBuffer.allocate(startOfPost.length + fileAllBytes.length + endOfPost.length);
        byteBuffer.put(startOfPost);
        byteBuffer.put(fileAllBytes);
        byteBuffer.put(endOfPost);

        return SimpleHttps.POST(new SimpleHttps.Argument(url).setRequestProperty(requestProperty).setPostData(byteBuffer.array())).getResponseOrException();
    }

    /**
     * 推送文本消息
     *
     * @param content 消息内容，最长不超过2048个字节，超过将截断；支持换行、A标签打开自定义网页
     * @return 是否推送成功
     */
    public boolean pushTextMessage(String content) {
        JSONObject postBody = generateBaseJsonBody("text");
        postBody.put("text", new JSONObject().put("content", content));

        return appPush(postBody);
    }

    /**
     * 推送文本卡片消息
     * 卡片消息的展现形式非常灵活，支持使用br标签或者空格来进行换行处理，也支持使用div标签来使用不同的字体颜色
     * 目前内置了3种文字颜色：灰色(gray)、高亮(highlight)、默认黑色(normal)，将其作为div标签的class属性即可
     *
     * @param title       标题，不超过128个字节，超过会自动截断
     * @param description 描述，不超过512个字节，超过会自动截断
     * @param url         点击后跳转的链接，最长2048字节，请确保包含了协议头(http/https)
     * @param buttonText  按钮文字，传入null默认为“详情”，不超过4个文字，超过自动截断
     * @return 是否推送成功
     */
    public boolean pushTextCard(String title, String description, String url, String buttonText) {
        JSONObject postBody = generateBaseJsonBody("textcard");
        //region textCard
        JSONObject textCard = new JSONObject();
        textCard.put("title", title);
        textCard.put("description", description);
        textCard.put("url", url);
        if (buttonText != null) {
            textCard.put("btntxt", buttonText);
        }
        //endregion
        postBody.put("textcard", textCard);

        return appPush(postBody);
    }

    public boolean pushTextCardWithTime(String title, String description, String url, String buttonText) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        description = "<div class=\"gray\">" + now.format(DATE_TIME_FORMATTER) + "</div>" + description;
        return pushTextCard(title, description, url, buttonText);
    }

    /**
     * 推送图文消息
     *
     * @param title       标题，不超过128个字节，超过会自动截断
     * @param description 描述，不超过512个字节，超过会自动截断
     * @param url         点击后跳转的链接，最长2048字节，请确保包含了协议头(http/https)，小程序或者url必须填写一个
     * @param pictureUrl  图文消息的图片链接，最长2048字节，支持JPG、PNG格式，较好的效果为大图1068*455，小图150*150
     * @return 是否推送成功
     */
    public boolean pushImageText(String title, String description, String url, String pictureUrl) {
        JSONObject postBody = generateBaseJsonBody("news");
        //region articles
        JSONObject article = new JSONObject();
        article.put("title", title);
        article.put("description", description);
        article.put("url", url);
        article.put("picurl", pictureUrl);
        JSONArray articles = new JSONArray().put(article);
        //endregion
        postBody.put("news", new JSONObject().put("articles", articles));

        return appPush(postBody);
    }

    /**
     * 推送图片
     *
     * @param mediaID 图片媒体文件id，可以调用上传临时素材接口获取
     * @return 是否推送成功
     */
    public boolean pushImage(String mediaID) {
        JSONObject postBody = generateBaseJsonBody("image");
        postBody.put("image", new JSONObject().put("media_id", mediaID));

        return appPush(postBody);
    }

    /**
     * 推送语音
     *
     * @param mediaID 语音文件id，可以调用上传临时素材接口获取
     * @return 是否推送成功
     */
    public boolean pushVoice(String mediaID) {
        JSONObject postBody = generateBaseJsonBody("voice");
        postBody.put("voice", new JSONObject().put("media_id", mediaID));

        return appPush(postBody);
    }

    /**
     * 推送视频
     *
     * @param mediaID     视频媒体文件id，可以调用上传临时素材接口获取
     * @param title       视频消息的标题，不超过128个字节，超过会自动截断
     * @param description 视频消息的描述，不超过512个字节，超过会自动截断
     * @return 是否推送成功
     */
    public boolean pushVideo(String mediaID, String title, String description) {
        JSONObject postBody = generateBaseJsonBody("video");
        postBody.put("video", new JSONObject().put("media_id", mediaID).put("title", title).put("description", description));

        return appPush(postBody);
    }

    /**
     * 推送文件
     *
     * @param mediaID 文件id，可以调用上传临时素材接口获取
     * @return 是否推送成功
     */
    public boolean pushFile(String mediaID) {
        JSONObject postBody = generateBaseJsonBody("file");
        postBody.put("file", new JSONObject().put("media_id", mediaID));

        return appPush(postBody);
    }

    private JSONObject generateBaseJsonBody(String msgtype) {
        JSONObject json = new JSONObject();
        json.put(pushTargetType, pushTarget);
        json.put("msgtype", msgtype);
        json.put("agentid", agentID);
        if (safe != 0) {
            json.put("safe", safe);
        }
        return json;
    }

    private boolean appPush(JSONObject postBody) {
        if (!prepareToPush()) return false;

        try {
            lastReturn = SimpleHttps.POST(new SimpleHttps.Argument(PUSH_MESSAGE_URL + access_token).setPostData(postBody.toString().getBytes(StandardCharsets.UTF_8))).getResponseOrException();
            lastReturnJson = new JSONObject(lastReturn);
        } catch (IOException e) {
            lastReturnJson = new JSONObject("{\"errmsg\": \"" + e.getMessage() + "\"}");
        }

        //TODO:debug
        //System.out.println(lastReturn);
        //if (!getErrMsg().equals("ok")) System.out.println(getErrMsg());

        return getErrCode() == 0;
    }

    //region GetResult
    public int getErrCode() {
        try {
            //从返回信息中获取错误码，0表示成功
            return lastReturnJson.getInt("errcode");
        } catch (JSONException e) {
            return -1;
        }
    }

    public String getErrMsg() {
        try {
            return lastReturnJson.getString("errmsg");
        } catch (JSONException e) {
            return "";
        }
    }

    public String getMsgID() {
        try {
            return lastReturnJson.getString("msgid");
        } catch (JSONException e) {
            return "";
        }
    }
    //endregion

    /**
     * 推送前的检查
     * 检查access_token是否过期，推送目标是否已设置
     *
     * @return 是否可以继续推送
     */
    private boolean prepareToPush() {
        if (pushTarget == null || pushTarget.isBlank()) {
            throw new RuntimeException("调用发送方法前需要先设置发送目标");
        }
        return checkTokenUpdate();
    }

    /**
     * 检查access_token是否过期，如有需要，更新access_token
     *
     * @return 是否成功
     */
    private boolean checkTokenUpdate() {
        if (System.currentTimeMillis() > expiresTime) {
            return updateAccessToken();
        }
        return true;
    }

    /**
     * 更新access_token
     *
     * @return 是否更新成功
     */
    private boolean updateAccessToken() {
        try {
            String rawData = SimpleHttps.GET(new SimpleHttps.Argument(specificGetTokenURL)).getResponseOrException();
            JSONObject json = new JSONObject(rawData);
            if (json.getInt("errcode") != 0) {
                //System.out.println(json.getString("errmsg"));
                lastReturnJson = json;
                this.expiresTime = 0;
                return false;
            }
            this.access_token = json.getString("access_token");
            this.expiresTime = System.currentTimeMillis() + (json.getLong("expires_in") * 1000L) - (1000L);
            return true;
        } catch (Exception e) {
            lastReturnJson = new JSONObject("{\"errmsg\": \"" + e.getMessage() + "\"}");
            this.expiresTime = 0;
            return false;
        }
    }

}
