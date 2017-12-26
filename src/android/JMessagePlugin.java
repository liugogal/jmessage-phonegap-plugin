package cn.jiguang.cordova.im;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import cn.jpush.im.android.api.ContactManager;
import cn.jpush.im.android.api.JMessageClient;
import cn.jpush.im.android.api.callback.CreateGroupCallback;
import cn.jpush.im.android.api.callback.DownloadCompletionCallback;
import cn.jpush.im.android.api.callback.GetAvatarBitmapCallback;
import cn.jpush.im.android.api.callback.GetBlacklistCallback;
import cn.jpush.im.android.api.callback.GetGroupIDListCallback;
import cn.jpush.im.android.api.callback.GetGroupInfoCallback;
import cn.jpush.im.android.api.callback.GetGroupInfoListCallback;
import cn.jpush.im.android.api.callback.GetGroupMembersCallback;
import cn.jpush.im.android.api.callback.GetNoDisurbListCallback;
import cn.jpush.im.android.api.callback.GetUserInfoCallback;
import cn.jpush.im.android.api.callback.GetUserInfoListCallback;
import cn.jpush.im.android.api.callback.IntegerCallback;
import cn.jpush.im.android.api.content.CustomContent;
import cn.jpush.im.android.api.content.FileContent;
import cn.jpush.im.android.api.content.ImageContent;
import cn.jpush.im.android.api.content.LocationContent;
import cn.jpush.im.android.api.content.TextContent;
import cn.jpush.im.android.api.content.VoiceContent;
import cn.jpush.im.android.api.enums.ContentType;
import cn.jpush.im.android.api.event.CommandNotificationEvent;
import cn.jpush.im.android.api.event.ContactNotifyEvent;
import cn.jpush.im.android.api.event.ConversationRefreshEvent;
import cn.jpush.im.android.api.event.LoginStateChangeEvent;
import cn.jpush.im.android.api.event.MessageEvent;
import cn.jpush.im.android.api.event.MessageRetractEvent;
import cn.jpush.im.android.api.event.NotificationClickEvent;
import cn.jpush.im.android.api.event.OfflineMessageEvent;
import cn.jpush.im.android.api.exceptions.JMFileSizeExceedException;
import cn.jpush.im.android.api.model.Conversation;
import cn.jpush.im.android.api.model.GroupInfo;
import cn.jpush.im.android.api.model.Message;
import cn.jpush.im.android.api.model.UserInfo;
import cn.jpush.im.android.api.options.MessageSendingOptions;
import cn.jpush.im.android.api.options.RegisterOptionalUserInfo;
import cn.jpush.im.api.BasicCallback;

import static cn.jiguang.cordova.im.JMessageUtils.getFile;
import static cn.jiguang.cordova.im.JMessageUtils.getFileExtension;
import static cn.jiguang.cordova.im.JMessageUtils.handleResult;
import static cn.jiguang.cordova.im.JMessageUtils.sendMessage;
import static cn.jiguang.cordova.im.JMessageUtils.toMessageSendingOptions;
import static cn.jiguang.cordova.im.JsonUtils.fromJson;
import static cn.jiguang.cordova.im.JsonUtils.toJson;

public class JMessagePlugin extends CordovaPlugin {

    private static String TAG = JMessagePlugin.class.getSimpleName();

    private static final int ERR_CODE_PARAMETER = 1;
    private static final int ERR_CODE_CONVERSATION = 2;
    private static final int ERR_CODE_MESSAGE = 3;
    private static final int ERR_CODE_FILE = 4;
    private static final int ERR_CODE_PERMISSION = 5;

    private static final String ERR_MSG_PARAMETER = "Parameters error";
    private static final String ERR_MSG_CONVERSATION = "Can't get the conversation";
    private static final String ERR_MSG_MESSAGE = "No such message";
    private static final String ERR_MSG_FILE = "Not find the file";
    private static final String ERR_MSG_PERMISSION_WRITE_EXTERNAL_STORAGE = "Do not have 'WRITE_EXTERNAL_STORAGE' permission";

    private Activity mCordovaActivity;

    private CallbackContext mCallback;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        mCordovaActivity = cordova.getActivity();
    }

    @Override
    public boolean execute(final String action, final JSONArray data, final CallbackContext callback)
            throws JSONException {
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Method method = JMessagePlugin.class.getDeclaredMethod(action, JSONArray.class,
                            CallbackContext.class);
                    method.invoke(JMessagePlugin.this, data, callback);
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
            }
        });
        return true;
    }

    void init(JSONArray data, CallbackContext callback) throws JSONException {
        JSONObject params = data.getJSONObject(0);

        boolean isOpenMessageRoaming = false;
        if (params.has("isOpenMessageRoaming")) {
            isOpenMessageRoaming = params.getBoolean("isOpenMessageRoaming");
        }
        JMessageClient.init(mCordovaActivity.getApplicationContext(), isOpenMessageRoaming);
        JMessageClient.registerEventReceiver(this);

        mCallback = callback;
    }

    void setDebugMode(JSONArray data, CallbackContext callback) throws JSONException {
        JSONObject params = data.getJSONObject(0);
        boolean enable = params.getBoolean("enable");
        JMessageClient.setDebugMode(enable);
    }

    // 注册，登录 - start
    void userRegister(JSONArray data, final CallbackContext callback) {
        String username, password;
        RegisterOptionalUserInfo optionalUserInfo = new RegisterOptionalUserInfo();

        try {
            JSONObject params = data.getJSONObject(0);
            username = params.getString("username");
            password = params.getString("password");

            if (params.has("address"))
                optionalUserInfo.setAddress(params.getString("address"));

            if (params.has("avatar"))
                optionalUserInfo.setAvatar(params.getString("avatar"));

            if (params.has("birthday"))
                optionalUserInfo.setBirthday(params.getLong("birthday"));

            if (params.has("extras")) {
                Map<String, String> extras = JsonUtils.fromJson(params.getJSONObject("extras"));
                optionalUserInfo.setExtras(extras);
            }

            if (params.has("gender")) {
                String gender = params.getString("gender");

                if (gender.equals("male")) {
                    optionalUserInfo.setGender(UserInfo.Gender.male);

                } else if (gender.equals("female")) {
                    optionalUserInfo.setGender(UserInfo.Gender.female);

                } else {
                    optionalUserInfo.setGender(UserInfo.Gender.unknown);
                }
            }

            if (params.has("nickname"))
                optionalUserInfo.setNickname(params.getString("nickname"));

            if (params.has("region"))
                optionalUserInfo.setRegion(params.getString("region"));

            if (params.has("signature"))
                optionalUserInfo.setSignature(params.getString("signature"));

        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        JMessageClient.register(username, password, optionalUserInfo, new BasicCallback() {
            @Override
            public void gotResult(int status, String desc) {
                handleResult(status, desc, callback);
            }
        });
    }

    public void userLogin(JSONArray data, final CallbackContext callback) {
        String username, password;

        try {
            JSONObject params = data.getJSONObject(0);
            username = params.getString("username");
            password = params.getString("password");

        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        JMessageClient.login(username, password, new BasicCallback() {
            @Override
            public void gotResult(int status, String desc) {
                handleResult(status, desc, callback);
            }
        });
    }

    void userLogout(JSONArray data, CallbackContext callback) {
        JMessageClient.logout();
    }

    // 注册，登录 - end

    // 用户信息相关 - start

    void getMyInfo(JSONArray data, final CallbackContext callback) {
        UserInfo myInfo = JMessageClient.getMyInfo();
        if (myInfo != null) {
            callback.success(toJson(myInfo));
        } else {
            callback.success(new JSONObject());
        }
    }

    void getUserInfo(JSONArray data, final CallbackContext callback) {
        String username, appKey;

        try {
            JSONObject params = data.getJSONObject(0);
            username = params.getString("username");
            appKey = params.has("appKey") ? params.getString("appKey") : "";
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        JMessageClient.getUserInfo(username, appKey, new GetUserInfoCallback() {
            @Override
            public void gotResult(int status, String desc, UserInfo userInfo) {
                if (status == 0) {
                    handleResult(toJson(userInfo), status, desc, callback);
                } else {
                    handleResult(status, desc, callback);
                }
            }
        });
    }

    void updateMyPassword(JSONArray data, final CallbackContext callback) {
        String oldPwd, newPwd;

        try {
            JSONObject params = data.getJSONObject(0);
            oldPwd = params.getString("oldPwd");
            newPwd = params.getString("newPwd");
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        JMessageClient.updateUserPassword(oldPwd, newPwd, new BasicCallback() {
            @Override
            public void gotResult(int status, String desc) {
                handleResult(status, desc, callback);
            }
        });
    }

    void updateMyAvatar(JSONArray data, final CallbackContext callback) throws JSONException {
        JSONObject params = data.getJSONObject(0);
        if (!params.has("imgPath")) {
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        String imgPath = params.getString("imgPath");
        File img = new File(imgPath);
        JMessageClient.updateUserAvatar(img, new BasicCallback() {
            @Override
            public void gotResult(int status, String desc) {
                handleResult(status, desc, callback);
            }
        });
    }

    void updateMyInfo(JSONArray data, final CallbackContext callback) {
        UserInfo myInfo = JMessageClient.getMyInfo();

        try {
            JSONObject params = data.getJSONObject(0);
            int count = params.length();

            if (params.has("birthday")) {
                myInfo.setBirthday(params.getLong("birthday"));
            }

            if (params.has("nickname")) {
                myInfo.setNickname(params.getString("nickname"));
                if (myInfo.getBirthday() == 0) {    // 防止注册用户时 birthday 默认为 0，导致最后更新用户所有信息时出错。
                    count--;
                    JMessageUtils.updateMyInfo(UserInfo.Field.nickname, myInfo, count, callback);
                }
            }

            if (params.has("signature")) {
                myInfo.setSignature(params.getString("signature"));
                if (myInfo.getBirthday() == 0) {
                    count--;
                    JMessageUtils.updateMyInfo(UserInfo.Field.signature, myInfo, count, callback);
                }
            }

            if (params.has("gender")) {
                if (params.getString("gender").equals("male")) {
                    myInfo.setGender(UserInfo.Gender.male);
                } else if (params.getString("gender").equals("female")) {
                    myInfo.setGender(UserInfo.Gender.female);
                } else {
                    myInfo.setGender(UserInfo.Gender.unknown);
                }

                if (myInfo.getBirthday() == 0) {
                    count--;
                    JMessageUtils.updateMyInfo(UserInfo.Field.gender, myInfo, count, callback);
                }
            }

            if (params.has("region")) {
                myInfo.setRegion(params.getString("region"));
                if (myInfo.getBirthday() == 0) {
                    count--;
                    JMessageUtils.updateMyInfo(UserInfo.Field.region, myInfo, count, callback);
                }
            }

            if (params.has("address")) {
                myInfo.setAddress(params.getString("address"));
                if (myInfo.getBirthday() == 0) {
                    count--;
                    JMessageUtils.updateMyInfo(UserInfo.Field.address, myInfo, count, callback);
                }
            }

            if (params.has("extras")) {
                Map<String, String> extras = fromJson(params.getJSONObject("extras"));
                myInfo.setUserExtras(extras);
                if (myInfo.getBirthday() == 0) {
                    count--;
                    JMessageUtils.updateMyInfo(UserInfo.Field.extras, myInfo, count, callback);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        if (myInfo.getBirthday() != 0) {
            JMessageClient.updateMyInfo(UserInfo.Field.all, myInfo, new BasicCallback() {
                @Override
                public void gotResult(int status, String desc) {
                    handleResult(status, desc, callback);
                }
            });
        }
    }

    void downloadThumbUserAvatar(JSONArray data, final CallbackContext callback) {
        String username, appKey;

        try {
            JSONObject params = data.getJSONObject(0);
            username = params.getString("username");
            appKey = params.has("appKey") ? params.getString("appKey") : "";
        } catch (JSONException e) {
            e.printStackTrace();
            callback.error(ERR_MSG_PARAMETER);
            return;
        }

        JMessageClient.getUserInfo(username, appKey, new GetUserInfoCallback() {
            @Override
            public void gotResult(int status, String desc, UserInfo userInfo) {
                if (status == 0) {
                    File avatarFile = userInfo.getAvatarFile();
                    JSONObject result = new JSONObject();
                    try {
                        result.put("username", userInfo.getUserName());
                        result.put("appKey", userInfo.getAppKey());
                        String avatarFilePath = (avatarFile == null ? "" : avatarFile.getAbsolutePath());
                        result.put("filePath", avatarFilePath);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    callback.success(result);
                } else {
                    handleResult(status, desc, callback);
                }
            }
        });
    }

    void downloadOriginalUserAvatar(JSONArray data, final CallbackContext callback) {
        try {
            JSONObject params = data.getJSONObject(0);

            final String username = params.getString("username");
            final String appKey = params.has("appKey") ? params.getString("appKey") : "";

            JMessageUtils.getUserInfo(params, new GetUserInfoCallback() {
                @Override
                public void gotResult(int status, String desc, final UserInfo userInfo) {
                    if (status != 0) {
                        handleResult(status, desc, callback);
                        return;
                    }

                    if (userInfo.getBigAvatarFile() == null) {  // 本地不存在头像原图，进行下载。
                        userInfo.getBigAvatarBitmap(new GetAvatarBitmapCallback() {
                            @Override
                            public void gotResult(int status, String desc, Bitmap bitmap) {
                                if (status != 0) {  // 下载失败
                                    handleResult(status, desc, callback);
                                    return;
                                }

                                String filePath = "";

                                if (bitmap != null) {
                                    filePath = userInfo.getBigAvatarFile().getAbsolutePath();
                                }

                                try {
                                    JSONObject result = new JSONObject();
                                    result.put("username", username);
                                    result.put("appKey", appKey);
                                    result.put("filePath", filePath);
                                    callback.success(result);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                    callback.error(PluginResult.Status.JSON_EXCEPTION.toString());
                                }
                            }
                        });

                    } else {
                        JSONObject result = new JSONObject();
                        try {
                            result.put("username", username);
                            result.put("appKey", appKey);
                            result.put("filePath", userInfo.getBigAvatarFile().getAbsolutePath());
                            callback.success(result);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
        }
    }

    // 用户信息相关 - end

    // 消息相关 - start

    void sendTextMessage(JSONArray data, final CallbackContext callback) {
        String text;
        Map<String, String> extras = null;
        MessageSendingOptions messageSendingOptions = null;
        Conversation conversation;

        try {
            JSONObject params = data.getJSONObject(0);

            conversation = JMessageUtils.getConversation(params);
            if (conversation == null) {
                handleResult(ERR_CODE_CONVERSATION, ERR_MSG_CONVERSATION, callback);
                return;
            }

            text = params.getString("text");

            if (params.has("extras")) {
                extras = fromJson(params.getJSONObject("extras"));
            }

            if (params.has("messageSendingOptions")) {
                messageSendingOptions = toMessageSendingOptions(params.getJSONObject("messageSendingOptions"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        TextContent content = new TextContent(text);
        if (extras != null) {
            content.setExtras(extras);
        }

        sendMessage(conversation, content, messageSendingOptions, callback);
    }

    void sendImageMessage(JSONArray data, CallbackContext callback) {
        boolean hasPermission = PermissionHelper.hasPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (!hasPermission) {
            handleResult(ERR_CODE_PERMISSION, ERR_MSG_PERMISSION_WRITE_EXTERNAL_STORAGE, callback);
            return;
        }

        String path;
        Map<String, String> extras = null;
        MessageSendingOptions messageSendingOptions = null;
        Conversation conversation;

        try {
            JSONObject params = data.getJSONObject(0);

            conversation = JMessageUtils.getConversation(params);
            if (conversation == null) {
                handleResult(ERR_CODE_CONVERSATION, ERR_MSG_CONVERSATION, callback);
                return;
            }

            path = params.getString("path");

            if (params.has("extras")) {
                extras = fromJson(params.getJSONObject("extras"));
            }

            if (params.has("messageSendingOptions")) {
                messageSendingOptions = toMessageSendingOptions(params.getJSONObject("messageSendingOptions"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        ImageContent content;
        try {
            File file = getFile(path);
            content = new ImageContent(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_FILE, ERR_MSG_FILE, callback);
            return;
        }

        if (extras != null) {
            content.setExtras(extras);
        }

        sendMessage(conversation, content, messageSendingOptions, callback);
    }

    void sendVoiceMessage(JSONArray data, CallbackContext callback) {
        boolean hasPermission = PermissionHelper.hasPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (!hasPermission) {
            handleResult(ERR_CODE_PERMISSION, ERR_MSG_PERMISSION_WRITE_EXTERNAL_STORAGE, callback);
            return;
        }

        String path;
        Map<String, String> extras = null;
        MessageSendingOptions messageSendingOptions = null;
        Conversation conversation;

        try {
            JSONObject params = data.getJSONObject(0);

            conversation = JMessageUtils.getConversation(params);
            if (conversation == null) {
                handleResult(ERR_CODE_CONVERSATION, ERR_MSG_CONVERSATION, callback);
                return;
            }

            path = params.getString("path");

            if (params.has("extras")) {
                extras = fromJson(params.getJSONObject("extras"));
            }

            if (params.has("messageSendingOptions")) {
                messageSendingOptions = toMessageSendingOptions(params.getJSONObject("messageSendingOptions"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        try {
            MediaPlayer mediaPlayer = MediaPlayer.create(mCordovaActivity, Uri.parse(path));
            int duration = mediaPlayer.getDuration() / 1000;    // Millisecond to second.

            File file = getFile(path);
            VoiceContent content = new VoiceContent(file, duration);

            mediaPlayer.release();

            if (extras != null) {
                content.setExtras(extras);
            }

            sendMessage(conversation, content, messageSendingOptions, callback);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_FILE, ERR_MSG_FILE, callback);
        }
    }

    void sendCustomMessage(JSONArray data, CallbackContext callback) {
        try {
            JSONObject params = data.getJSONObject(0);

            Conversation conversation = JMessageUtils.getConversation(params);
            if (conversation == null) {
                handleResult(ERR_CODE_CONVERSATION, ERR_MSG_CONVERSATION, callback);
                return;
            }

            JSONObject customObject = params.getJSONObject("customObject");

            MessageSendingOptions options = null;
            if (params.has("messageSendingOptions")) {
                options = toMessageSendingOptions(params.getJSONObject("messageSendingOptions"));
            }

            CustomContent content = new CustomContent();
            content.setAllValues(fromJson(customObject));
            sendMessage(conversation, content, options, callback);
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
        }
    }

    void sendLocationMessage(JSONArray data, CallbackContext callback) {
        double latitude, longitude;
        int scale;
        String address;
        Map<String, String> extras = null;
        MessageSendingOptions options = null;
        Conversation conversation;

        try {
            JSONObject params = data.getJSONObject(0);

            conversation = JMessageUtils.getConversation(params);
            if (conversation == null) {
                handleResult(ERR_CODE_CONVERSATION, ERR_MSG_CONVERSATION, callback);
                return;
            }

            latitude = params.getDouble("latitude");
            longitude = params.getDouble("longitude");
            scale = params.getInt("scale");
            address = params.getString("address");

            if (params.has("extras")) {
                extras = fromJson(params.getJSONObject("extras"));
            }

            if (params.has("messageSendingOptions")) {
                options = toMessageSendingOptions(params.getJSONObject("messageSendingOptions"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        LocationContent content = new LocationContent(latitude, longitude, scale, address);
        if (extras != null) {
            content.setExtras(extras);
        }

        sendMessage(conversation, content, options, callback);
    }

    void sendFileMessage(JSONArray data, CallbackContext callback) {
        boolean hasPermission = PermissionHelper.hasPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (!hasPermission) {
            handleResult(ERR_CODE_PERMISSION, ERR_MSG_PERMISSION_WRITE_EXTERNAL_STORAGE, callback);
            return;
        }

        String path, fileName = "";
        Map<String, String> extras = null;
        MessageSendingOptions options = null;
        Conversation conversation;

        try {
            JSONObject params = data.getJSONObject(0);

            conversation = JMessageUtils.getConversation(params);
            if (conversation == null) {
                handleResult(ERR_CODE_CONVERSATION, ERR_MSG_CONVERSATION, callback);
                return;
            }

            path = params.getString("path");

            if (params.has("fileName")) {
                fileName = params.getString("fileName");
            }

            if (params.has("extras")) {
                extras = fromJson(params.getJSONObject("extras"));
            }

            if (params.has("messageSendingOptions")) {
                options = toMessageSendingOptions(params.getJSONObject("messageSendingOptions"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        try {
            File file = getFile(path);
            FileContent content = new FileContent(file, fileName);
            if (extras != null) {
                content.setExtras(extras);
            }
            sendMessage(conversation, content, options, callback);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_FILE, ERR_MSG_FILE, callback);
        } catch (JMFileSizeExceedException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_FILE, "File size is too large", callback);
        }
    }

    void sendSingleTransCommand(JSONArray data, final CallbackContext callback) {
        String username, appKey, msg;

        try {
            JSONObject params = data.getJSONObject(0);
            username = params.getString("username");
            appKey = params.has("appKey") ? params.getString("appKey") : null;
            msg = params.getString("content");
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        JMessageClient.sendSingleTransCommand(username, appKey, msg, new BasicCallback() {
            @Override
            public void gotResult(int status, String desc) {
                handleResult(status, desc, callback);
            }
        });
    }

    void sendGroupTransCommand(JSONArray data, final CallbackContext callback) {
        long groupId;
        String msg;

        try {
            JSONObject params = data.getJSONObject(0);
            groupId = params.getLong("groupId");
            msg = params.getString("content");
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        JMessageClient.sendGroupTransCommand(groupId, msg, new BasicCallback() {
            @Override
            public void gotResult(int status, String desc) {
                handleResult(status, desc, callback);
            }
        });
    }

    void retractMessage(JSONArray data, final CallbackContext callback) {
        Conversation conversation;
        String messageId;

        try {
            JSONObject params = data.getJSONObject(0);
            conversation = JMessageUtils.getConversation(params);
            if (conversation == null) {
                handleResult(ERR_CODE_CONVERSATION, ERR_MSG_CONVERSATION, callback);
                return;
            }

            messageId = params.getString("messageId");
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        Message msg = conversation.getMessage(Integer.parseInt(messageId));
        conversation.retractMessage(msg, new BasicCallback() {
            @Override
            public void gotResult(int status, String desc) {
                handleResult(status, desc, callback);
            }
        });
    }

    void getHistoryMessages(JSONArray data, CallbackContext callback) {
        Conversation conversation;
        int from, limit;

        try {
            JSONObject params = data.getJSONObject(0);
            conversation = JMessageUtils.getConversation(params);
            if (conversation == null) {
                handleResult(ERR_CODE_CONVERSATION, ERR_MSG_CONVERSATION, callback);
                return;
            }

            from = params.getInt("from");
            limit = params.getInt("limit");

            if (from < 0 || limit < -1) {
                handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
                return;
            }
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        List<Message> messageList;

        if (limit == -1) {
            int messageCount = conversation.getAllMessage().size() - from;
            messageList = conversation.getMessagesFromNewest(from, messageCount);
        } else {
            messageList = conversation.getMessagesFromNewest(from, limit);
        }

        JSONArray messageJSONArr = new JSONArray();

        for (Message msg : messageList) {
            messageJSONArr.put(toJson(msg));
        }
        callback.success(messageJSONArr);
    }

    void getMessageById(JSONArray data, CallbackContext callback) {
        Conversation conversation;
        String messageId;

        try {
            JSONObject params = data.getJSONObject(0);
            conversation = JMessageUtils.getConversation(params);

            if (conversation == null) {
                handleResult(ERR_CODE_CONVERSATION, "Can't get conversation", callback);
                return;
            }

            messageId = params.getString("messageId");
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        Message msg = conversation.getMessage(Integer.parseInt(messageId));

        if (msg == null) {
            callback.success();
        } else {
            callback.success(toJson(msg));
        }
    }

    void deleteMessageById(JSONArray data, CallbackContext callback) {
        Conversation conversation;
        String messageId;

        try {
            JSONObject params = data.getJSONObject(0);
            conversation = JMessageUtils.getConversation(params);

            if (conversation == null) {
                handleResult(ERR_CODE_CONVERSATION, "Can't get conversation", callback);
                return;
            }

            messageId = params.getString("messageId");
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        boolean success = conversation.deleteMessage(Integer.parseInt(messageId));

        if (success) {
            callback.success();
        } else {
            JSONObject error = new JSONObject();
            try {
                error.put("code", ERR_CODE_MESSAGE);
                error.put("description", ERR_MSG_MESSAGE);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            callback.error(error);
        }
    }

    void downloadOriginalImage(JSONArray data, final CallbackContext callback) {
        Conversation conversation;
        final String messageId;

        try {
            JSONObject params = data.getJSONObject(0);
            conversation = JMessageUtils.getConversation(params);
            if (conversation == null) {
                handleResult(ERR_CODE_CONVERSATION, "Can't get conversation", callback);
                return;
            }

            messageId = params.getString("messageId");
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        Message msg = conversation.getMessage(Integer.parseInt(messageId));
        if (msg == null) {
            handleResult(ERR_CODE_MESSAGE, ERR_MSG_MESSAGE, callback);
            return;
        }

        if (msg.getContentType() != ContentType.image) {
            handleResult(ERR_CODE_MESSAGE, "Message type isn't image", callback);
            return;
        }

        ImageContent content = (ImageContent) msg.getContent();
        content.downloadOriginImage(msg, new DownloadCompletionCallback() {

            @Override
            public void onComplete(int status, String desc, File file) {
                if (status == 0) {
                    JSONObject result = new JSONObject();
                    try {
                        result.put("messageId", messageId);
                        result.put("filePath", file.getAbsolutePath());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    handleResult(result, status, desc, callback);

                } else {
                    handleResult(status, desc, callback);
                }
            }
        });
    }

    void downloadVoiceFile(JSONArray data, final CallbackContext callback) {
        Conversation conversation;
        final String messageId;

        try {
            JSONObject params = data.getJSONObject(0);
            conversation = JMessageUtils.getConversation(params);
            if (conversation == null) {
                handleResult(ERR_CODE_CONVERSATION, "Can't get conversation", callback);
                return;
            }

            messageId = params.getString("messageId");
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        Message msg = conversation.getMessage(Integer.parseInt(messageId));
        if (msg == null) {
            handleResult(ERR_CODE_MESSAGE, ERR_MSG_MESSAGE, callback);
            return;
        }

        if (msg.getContentType() != ContentType.voice) {
            handleResult(ERR_CODE_MESSAGE, "Message type isn't voice", callback);
            return;
        }

        VoiceContent content = (VoiceContent) msg.getContent();
        content.downloadVoiceFile(msg, new DownloadCompletionCallback() {

            @Override
            public void onComplete(int status, String desc, File file) {
                if (status == 0) {
                    JSONObject result = new JSONObject();
                    try {
                        result.put("messageId", messageId);
                        result.put("filePath", file.getAbsolutePath());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    handleResult(result, status, desc, callback);

                } else {
                    handleResult(status, desc, callback);
                }
            }
        });
    }

    void downloadFile(JSONArray data, final CallbackContext callback) {
        Conversation conversation;
        final String messageId;

        try {
            JSONObject params = data.getJSONObject(0);
            conversation = JMessageUtils.getConversation(params);
            if (conversation == null) {
                handleResult(ERR_CODE_CONVERSATION, "Can't get conversation", callback);
                return;
            }

            messageId = params.getString("messageId");
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        Message msg = conversation.getMessage(Integer.parseInt(messageId));
        if (msg == null) {
            handleResult(ERR_CODE_MESSAGE, ERR_MSG_MESSAGE, callback);
            return;
        }

        if (msg.getContentType() != ContentType.file) {
            handleResult(ERR_CODE_MESSAGE, "Message type isn't file", callback);
            return;
        }

        FileContent content = (FileContent) msg.getContent();
        content.downloadFile(msg, new DownloadCompletionCallback() {
            @Override
            public void onComplete(int status, String desc, File file) {
                if (status == 0) {
                    JSONObject result = new JSONObject();
                    try {
                        result.put("messageId", messageId);
                        result.put("filePath", file.getAbsolutePath());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    handleResult(result, status, desc, callback);

                } else {
                    handleResult(status, desc, callback);
                }
            }
        });
    }

    // 消息相关 - end

    // 好友关系 - start

    void sendInvitationRequest(JSONArray data, final CallbackContext callback) {
        String username, appKey, reason;

        try {
            JSONObject params = data.getJSONObject(0);
            username = params.getString("username");
            appKey = params.has("appKey") ? params.getString("appKey") : "";
            reason = params.getString("reason");
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        ContactManager.sendInvitationRequest(username, appKey, reason, new BasicCallback() {

            @Override
            public void gotResult(int status, String desc) {
                handleResult(status, desc, callback);
            }
        });
    }

    void acceptInvitation(JSONArray data, final CallbackContext callback) {
        String username, appKey;

        try {
            JSONObject params = data.getJSONObject(0);
            username = params.getString("username");
            appKey = params.has("appKey") ? params.getString("appKey") : "";
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        ContactManager.acceptInvitation(username, appKey, new BasicCallback() {

            @Override
            public void gotResult(int status, String desc) {
                handleResult(status, desc, callback);
            }
        });
    }

    void declineInvitationRequest(JSONArray data, final CallbackContext callback) {
        String username, appKey, reason;

        try {
            JSONObject params = data.getJSONObject(0);
            username = params.getString("username");
            appKey = params.has("appKey") ? params.getString("appKey") : "";
            reason = params.getString("reason");
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        ContactManager.sendInvitationRequest(username, appKey, reason, new BasicCallback() {

            @Override
            public void gotResult(int status, String desc) {
                handleResult(status, desc, callback);
            }
        });
    }

    void removeFromFriendList(JSONArray data, final CallbackContext callback) {
        String username, appKey;

        try {
            JSONObject params = data.getJSONObject(0);
            username = params.getString("username");
            appKey = params.getString("appKey");
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        JMessageClient.getUserInfo(username, appKey, new GetUserInfoCallback() {

            @Override
            public void gotResult(int status, String desc, UserInfo userInfo) {
                if (status == 0) {
                    userInfo.removeFromFriendList(new BasicCallback() {

                        @Override
                        public void gotResult(int status, String desc) {
                            handleResult(status, desc, callback);
                        }
                    });

                } else {
                    handleResult(status, desc, callback);
                }
            }
        });
    }

    void updateFriendNoteName(JSONArray data, final CallbackContext callback) {
        final String username, appKey, noteName;

        try {
            JSONObject params = data.getJSONObject(0);
            username = params.getString("username");
            appKey = params.getString("appKey");
            noteName = params.getString("noteName");
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        JMessageClient.getUserInfo(username, appKey, new GetUserInfoCallback() {

            @Override
            public void gotResult(int status, String desc, UserInfo userInfo) {
                if (status == 0) {
                    userInfo.updateNoteName(noteName, new BasicCallback() {

                        @Override
                        public void gotResult(int status, String desc) {
                            handleResult(status, desc, callback);
                        }
                    });

                } else {
                    handleResult(status, desc, callback);
                }
            }
        });
    }

    void updateFriendNoteText(JSONArray data, final CallbackContext callback) {
        final String username, appKey, noteText;

        try {
            JSONObject params = data.getJSONObject(0);
            username = params.getString("username");
            appKey = params.getString("appKey");
            noteText = params.getString("noteText");
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        JMessageClient.getUserInfo(username, appKey, new GetUserInfoCallback() {

            @Override
            public void gotResult(int status, String desc, UserInfo userInfo) {
                if (status == 0) {
                    userInfo.updateNoteText(noteText, new BasicCallback() {

                        @Override
                        public void gotResult(int status, String desc) {
                            handleResult(status, desc, callback);
                        }
                    });

                } else {
                    handleResult(status, desc, callback);
                }
            }
        });
    }

    void getFriends(JSONArray data, final CallbackContext callback) {
        ContactManager.getFriendList(new GetUserInfoListCallback() {

            @Override
            public void gotResult(int status, String desc, List list) {
                if (status == 0) {
                    handleResult(toJson(list), status, desc, callback);

                } else {
                    handleResult(status, desc, callback);
                }
            }
        });
    }

    // 好友关系 - end

    // 群组 - start

    void createGroup(JSONArray data, final CallbackContext callback) {
        String name, desc, avatarFilePath;

        try {
            JSONObject params = data.getJSONObject(0);
            name = params.getString("name");
            desc = params.getString("desc");

            JMessageClient.createGroup(name, desc, new CreateGroupCallback() {
                @Override
                public void gotResult(int status, String desc, long groupId) {
                    if (status == 0) {
                        callback.success(String.valueOf(groupId));
                    } else {
                        handleResult(status, desc, callback);
                    }
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
        }
    }

    void getGroupIds(JSONArray data, final CallbackContext callback) {
        JMessageClient.getGroupIDList(new GetGroupIDListCallback() {
            @Override
            public void gotResult(int status, String desc, List<Long> list) {
                if (status == 0) {
                    JSONArray groupIdJsonArr = new JSONArray();
                    for (Long id : list) {
                        groupIdJsonArr.put(String.valueOf(id));
                    }
                    handleResult(groupIdJsonArr, status, desc, callback);

                } else {
                    handleResult(status, desc, callback);
                }
            }
        });
    }

    void getGroupInfo(JSONArray data, final CallbackContext callback) {
        long groupId;

        try {
            JSONObject params = data.getJSONObject(0);
            groupId = Long.parseLong(params.getString("id"));

        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        JMessageClient.getGroupInfo(groupId, new GetGroupInfoCallback() {
            @Override
            public void gotResult(int status, String desc, GroupInfo groupInfo) {
                if (status == 0) {
                    handleResult(toJson(groupInfo), status, desc, callback);

                } else {
                    handleResult(status, desc, callback);
                }
            }
        });
    }

    void updateGroupInfo(JSONArray data, final CallbackContext callback) {
        final long groupId;
        final String newName, newDesc;

        try {
            JSONObject params = data.getJSONObject(0);
            groupId = Long.parseLong(params.getString("id"));
            newName = params.has("newName") ? params.getString("newName") : null;
            newDesc = params.has("newDesc") ? params.getString("newDesc") : null;

        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        JMessageClient.getGroupInfo(groupId, new GetGroupInfoCallback() {
            @Override
            public void gotResult(int status, String desc, final GroupInfo groupInfo) {
                if (!TextUtils.isEmpty(newName) && TextUtils.isEmpty(newDesc)) {
                    groupInfo.updateName(newName, new BasicCallback() {
                        @Override
                        public void gotResult(int status, String desc) {
                            handleResult(status, desc, callback);
                        }
                    });

                } else if (TextUtils.isEmpty(newName) && !TextUtils.isEmpty(newDesc)) {
                    groupInfo.updateDescription(newDesc, new BasicCallback() {
                        @Override
                        public void gotResult(int status, String desc) {
                            handleResult(status, desc, callback);
                        }
                    });

                } else {
                    groupInfo.updateName(newName, new BasicCallback() {
                        @Override
                        public void gotResult(int status, String desc) {
                            if (status == 0) {
                                groupInfo.updateDescription(newDesc, new BasicCallback() {
                                    @Override
                                    public void gotResult(int status, String desc) {
                                        handleResult(status, desc, callback);
                                    }
                                });

                            } else {
                                handleResult(status, desc, callback);
                            }
                        }
                    });
                }
            }
        });
    }

    void updateGroupAvatar(JSONArray data, final CallbackContext callback) {
        long groupId;
        final String imgPath;

        try {
            JSONObject params = data.getJSONObject(0);
            groupId = Long.parseLong(params.getString("id"));
            imgPath = params.getString("imgPath");
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        JMessageClient.getGroupInfo(groupId, new GetGroupInfoCallback() {
            @Override
            public void gotResult(int status, String desc, GroupInfo groupInfo) {
                if (status != 0) {  // error
                    handleResult(status, desc, callback);
                    return;
                }

                File file;
                try {
                    file = getFile(imgPath);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    handleResult(ERR_CODE_FILE, ERR_MSG_FILE, callback);
                    return;
                }

                String extension = getFileExtension(imgPath);

                groupInfo.updateAvatar(file, extension, new BasicCallback() {
                    @Override
                    public void gotResult(int status, String desc) {
                        handleResult(status, desc, callback);
                    }
                });
            }
        });
    }

    void addGroupMembers(JSONArray data, final CallbackContext callback) {
        long groupId;
        JSONArray usernameJsonArr;
        String appKey;
        List<String> usernameList = new ArrayList<String>();

        try {
            JSONObject params = data.getJSONObject(0);
            groupId = Long.parseLong(params.getString("id"));
            appKey = params.has("appKey") ? params.getString("appKey") : "";
            usernameJsonArr = params.getJSONArray("usernameArray");

            for (int i = 0; i < usernameJsonArr.length(); i++) {
                usernameList.add(usernameJsonArr.getString(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        JMessageClient.addGroupMembers(groupId, appKey, usernameList, new BasicCallback() {
            @Override
            public void gotResult(int status, String desc) {
                handleResult(status, desc, callback);
            }
        });
    }

    void removeGroupMembers(JSONArray data, final CallbackContext callback) {
        long groupId;
        JSONArray usernameJsonArr;
        String appKey;
        List<String> usernameList = new ArrayList<String>();

        try {
            JSONObject params = data.getJSONObject(0);
            groupId = Long.parseLong(params.getString("id"));
            appKey = params.has("appKey") ? params.getString("appKey") : "";
            usernameJsonArr = params.getJSONArray("usernameArray");

            for (int i = 0; i < usernameJsonArr.length(); i++) {
                usernameList.add(usernameJsonArr.getString(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        JMessageClient.removeGroupMembers(groupId, appKey, usernameList, new BasicCallback() {
            @Override
            public void gotResult(int status, String desc) {
                handleResult(status, desc, callback);
            }
        });
    }

    void exitGroup(JSONArray data, final CallbackContext callback) {
        long groupId;

        try {
            JSONObject params = data.getJSONObject(0);
            groupId = Long.parseLong(params.getString("id"));
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        JMessageClient.exitGroup(groupId, new BasicCallback() {
            @Override
            public void gotResult(int status, String desc) {
                handleResult(status, desc, callback);
            }
        });
    }

    void getGroupMembers(JSONArray data, final CallbackContext callback) {
        long groupId;

        try {
            JSONObject params = data.getJSONObject(0);
            groupId = Long.parseLong(params.getString("id"));
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        JMessageClient.getGroupMembers(groupId, new GetGroupMembersCallback() {
            @Override
            public void gotResult(int status, String desc, List list) {
                if (status == 0) {
                    handleResult(toJson(list), status, desc, callback);

                } else {
                    handleResult(status, desc, callback);
                }
            }
        });
    }

    void blockGroupMessage(JSONArray data, final CallbackContext callback) {
        final long groupId;
        final int isBlock;  // true: 屏蔽；false: 取消屏蔽。

        try {
            JSONObject params = data.getJSONObject(0);
            groupId = Long.parseLong(params.getString("id"));
            isBlock = params.getBoolean("isBlock") ? 1 : 0;
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        JMessageClient.getGroupInfo(groupId, new GetGroupInfoCallback() {
            @Override
            public void gotResult(int status, String desc, GroupInfo groupInfo) {
                if (status != 0) {
                    handleResult(status, desc, callback);
                    return;
                }

                groupInfo.setBlockGroupMessage(isBlock, new BasicCallback() {
                    @Override
                    public void gotResult(int status, String desc) {
                        handleResult(status, desc, callback);
                    }
                });
            }
        });
    }

    void isGroupBlocked(JSONArray data, final CallbackContext callback) {
        long groupId;

        try {
            JSONObject params = data.getJSONObject(0);
            groupId = Long.parseLong(params.getString("id"));
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        JMessageClient.getGroupInfo(groupId, new GetGroupInfoCallback() {
            @Override
            public void gotResult(int status, String desc, GroupInfo groupInfo) {
                if (status != 0) {
                    handleResult(status, desc, callback);
                    return;
                }

                boolean isBlocked = (groupInfo.isGroupBlocked() == 1);
                JSONObject result = new JSONObject();
                try {
                    result.put("isBlocked", isBlocked);
                    handleResult(result, status, desc, callback);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    void getBlockedGroupList(JSONArray data, final CallbackContext callback) {
        JMessageClient.getBlockedGroupsList(new GetGroupInfoListCallback() {
            @Override
            public void gotResult(int status, String desc, List<GroupInfo> list) {
                if (status != 0) {
                    handleResult(status, desc, callback);
                    return;
                }

                JSONArray result = new JSONArray();

                for (GroupInfo groupInfo : list) {
                    result.put(toJson(groupInfo));
                }

                handleResult(result, status, desc, callback);
            }
        });
    }

    // 群组 - end

    // 黑名单 - start

    void addUsersToBlacklist(JSONArray data, final CallbackContext callback) {
        List<String> usernameList;
        String appKey;

        try {
            JSONObject params = data.getJSONObject(0);
            JSONArray usernameJsonArr = params.getJSONArray("usernameArray");

            usernameList = new ArrayList<String>();
            for (int i = 0; i < usernameJsonArr.length(); i++) {
                usernameList.add(usernameJsonArr.getString(i));
            }

            appKey = params.has("appKey") ? params.getString("appKey") : "";
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        JMessageClient.addUsersToBlacklist(usernameList, appKey, new BasicCallback() {
            @Override
            public void gotResult(int status, String desc) {
                handleResult(status, desc, callback);
            }
        });
    }

    void removeUsersFromBlacklist(JSONArray data, final CallbackContext callback) {
        List<String> usernameList;
        String appKey;

        try {
            JSONObject params = data.getJSONObject(0);
            JSONArray usernameJsonArr = params.getJSONArray("usernameArray");

            usernameList = new ArrayList<String>();
            for (int i = 0; i < usernameJsonArr.length(); i++) {
                usernameList.add(usernameJsonArr.getString(i));
            }

            appKey = params.has("appKey") ? params.getString("appKey") : "";
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        JMessageClient.delUsersFromBlacklist(usernameList, appKey, new BasicCallback() {
            @Override
            public void gotResult(int status, String desc) {
                handleResult(status, desc, callback);
            }
        });
    }

    void getBlacklist(JSONArray data, final CallbackContext callback) {
        JMessageClient.getBlacklist(new GetBlacklistCallback() {
            @Override
            public void gotResult(int status, String desc, List list) {
                if (status == 0) {
                    handleResult(toJson(list), status, desc, callback);
                } else {
                    handleResult(status, desc, callback);
                }
            }
        });
    }

    // 黑名单 - end

    // 免打扰 - start

    void setNoDisturb(JSONArray data, final CallbackContext callback) {
        try {
            JSONObject params = data.getJSONObject(0);
            String type = params.getString("type");
            final int isNoDisturb = params.getBoolean("isNoDisturb") ? ERR_CODE_PARAMETER : 0;

            if (type.equals("single")) {
                String username = params.getString("username");
                String appKey = params.has("appKey") ? params.getString("appKey") : "";

                JMessageClient.getUserInfo(username, appKey, new GetUserInfoCallback() {

                    @Override
                    public void gotResult(int status, String desc, UserInfo userInfo) {
                        if (status == 0) {
                            userInfo.setNoDisturb(isNoDisturb, new BasicCallback() {

                                @Override
                                public void gotResult(int status, String desc) {
                                    handleResult(status, desc, callback);
                                }
                            });

                        } else {
                            handleResult(status, desc, callback);
                        }
                    }
                });

            } else if (type.equals("group")) {
                final long groupId = Long.parseLong(params.getString("groupId"));

                JMessageClient.getGroupInfo(groupId, new GetGroupInfoCallback() {

                    @Override
                    public void gotResult(int status, String desc, GroupInfo groupInfo) {
                        if (status == 0) {
                            groupInfo.setNoDisturb(isNoDisturb, new BasicCallback() {
                                @Override
                                public void gotResult(int status, String desc) {
                                    handleResult(status, desc, callback);
                                }
                            });

                        } else {
                            handleResult(status, desc, callback);
                        }
                    }
                });
            }
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
        }
    }

    void getNoDisturbList(JSONArray data, final CallbackContext callback) {
        JMessageClient.getNoDisturblist(new GetNoDisurbListCallback() {

            @Override
            public void gotResult(int status, String desc, List userInfoList, List groupInfoList) {
                if (status == 0) {
                    JSONObject result = new JSONObject();
                    try {
                        result.put("userInfoArray", toJson(userInfoList));
                        result.put("groupInfoArray", toJson(groupInfoList));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    handleResult(result, status, desc, callback);

                } else {
                    handleResult(status, desc, callback);
                }
            }
        });
    }

    void setNoDisturbGlobal(JSONArray data, final CallbackContext callback) {
        try {
            JSONObject params = data.getJSONObject(0);
            int isNoDisturbGlobal = params.getBoolean("isNoDisturb") ? ERR_CODE_PARAMETER : 0;
            JMessageClient.setNoDisturbGlobal(isNoDisturbGlobal, new BasicCallback() {

                @Override
                public void gotResult(int status, String desc) {
                    handleResult(status, desc, callback);
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
        }
    }

    void isNoDisturbGlobal(JSONArray data, final CallbackContext callback) {
        JMessageClient.getNoDisturbGlobal(new IntegerCallback() {

            @Override
            public void gotResult(int status, String desc, Integer integer) {
                if (status == 0) {
                    JSONObject result = new JSONObject();
                    try {
                        result.put("isNoDisturb", integer == 1);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    handleResult(result, status, desc, callback);

                } else {
                    handleResult(status, desc, callback);
                }
            }
        });
    }

    // 免打扰 - end

    // 聊天会话 - start

    void createConversation(JSONArray data, CallbackContext callback) {
        try {
            JSONObject params = data.getJSONObject(0);
            String type = params.getString("type");
            Conversation conversation = null;

            if (type.equals("single")) {
                String username = params.getString("username");
                String appKey = params.has("appKey") ? params.getString("appKey") : "";
                conversation = Conversation.createSingleConversation(username, appKey);

            } else if (type.equals("group")) {
                String groupId = params.getString("groupId");
                conversation = Conversation.createGroupConversation(Long.parseLong(groupId));
            }

            if (conversation != null) {
                callback.success(toJson(conversation));
            } else {
                handleResult(ERR_CODE_CONVERSATION,
                        "Can't create the conversation, please check your parameters", callback);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
        }
    }

    void deleteConversation(JSONArray data, CallbackContext callback) {
        try {
            JSONObject params = data.getJSONObject(0);
            String type = params.getString("type");

            if (type.equals("single")) {
                String username = params.getString("username");

                if (params.has("appKey") && !TextUtils.isEmpty(params.getString("appKey"))) {
                    JMessageClient.deleteSingleConversation(username, params.getString("appKey"));
                } else {
                    JMessageClient.deleteSingleConversation(username);
                }

            } else if (type.equals("group")) {
                long groupId = Long.parseLong(params.getString("groupId"));
                JMessageClient.deleteGroupConversation(groupId);

            } else {
                handleResult(ERR_CODE_PARAMETER, "Conversation type is error", callback);
                return;
            }

            callback.success();
        } catch (JSONException e) {
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
        }
    }

    void enterConversation(JSONArray data, CallbackContext callback) {
        try {
            JSONObject params = data.getJSONObject(0);
            String type = params.getString("type");

            if (type.equals("single")) {
                String username = params.getString("username");
                String appKey = params.has("appKey") ? params.getString("appKey") : "";
                JMessageClient.enterSingleConversation(username, appKey);

            } else if (type.equals("group")) {
                long groupId = Long.parseLong(params.getString("groupId"));
                JMessageClient.enterGroupConversation(groupId);

            } else {
                handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
                return;
            }

            callback.success();
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
        }
    }

    void exitConversation(JSONArray data, CallbackContext callback) {
        JMessageClient.exitConversation();
    }

    void getConversation(JSONArray data, CallbackContext callback) {
        try {
            JSONObject params = data.getJSONObject(0);
            Conversation conversation = JMessageUtils.getConversation(params);
            if (conversation != null) {
                callback.success(toJson(conversation));
            } else {
                handleResult(ERR_CODE_CONVERSATION, ERR_MSG_CONVERSATION, callback);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
        }
    }

    void getConversations(JSONArray data, CallbackContext callback) {
        List<Conversation> conversationList = JMessageClient.getConversationList();
        JSONArray jsonArr = new JSONArray();
        for (Conversation conversation : conversationList) {
            jsonArr.put(toJson(conversation));
        }
        callback.success(jsonArr);
    }

    void resetUnreadMessageCount(JSONArray data, CallbackContext callback) {
        try {
            JSONObject params = data.getJSONObject(0);
            Conversation conversation = JMessageUtils.getConversation(params);
            conversation.resetUnreadCount();
            callback.success();
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
        }
    }

    void setConversationExtras(JSONArray data, CallbackContext callback) {
        Conversation conversation;
        JSONObject extra = null;

        try {
            JSONObject params = data.getJSONObject(0);
            conversation = JMessageUtils.getConversation(params);

            if (conversation == null) {
                handleResult(ERR_CODE_CONVERSATION, ERR_MSG_CONVERSATION, callback);
                return;
            }

            if (params.has("extra")) {
                extra = params.getJSONObject("extra");
            }
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        String extraStr = extra == null ? "" : extra.toString();
        boolean isSuccess = conversation.updateConversationExtra(extraStr);
    }

    // 聊天会话 - end

    // 事件处理 - start

    /**
     * 收到消息事件。
     *
     * @param event 消息事件。
     */
    public void onEvent(MessageEvent event) {
        eventSuccess(toJson("receiveMessage", toJson(event.getMessage())));
    }

    /**
     * 触发通知栏点击事件。
     *
     * @param event 通知栏点击事件。
     */
    public void onEvent(NotificationClickEvent event) {
        eventSuccess(toJson("clickMessageNotification", toJson(event.getMessage())));

        // 点击通知启动应用。
        Intent launch = mCordovaActivity.getApplicationContext().getPackageManager()
                .getLaunchIntentForPackage(mCordovaActivity.getApplicationContext().getPackageName());
        launch.addCategory(Intent.CATEGORY_LAUNCHER);
        launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mCordovaActivity.getApplicationContext().startActivity(launch);
    }

    /**
     * 同步离线消息。
     *
     * @param event 离线消息事件。
     */
    public void onEvent(OfflineMessageEvent event) throws JSONException {
        final JSONObject json = new JSONObject();
        json.put("conversation", toJson(event.getConversation()));

        final List<Message> offlineMsgList = event.getOfflineMessageList();
        int latestMediaMessageIndex = -1;

        for (int i = offlineMsgList.size() - 1; i >= 0; i--) {
            Message msg = offlineMsgList.get(i);
            if (msg.getContentType() == ContentType.image || msg.getContentType() == ContentType.voice) {
                latestMediaMessageIndex = i;
                break;
            }
        }

        final JSONArray msgJsonArr = new JSONArray();

        if (latestMediaMessageIndex == -1) { // 没有多媒体消息
            for (Message msg : offlineMsgList) {
                msgJsonArr.put(toJson(msg));
            }
            json.put("messageArray", msgJsonArr);
            eventSuccess(toJson("syncOfflineMessage", json));

        } else {
            final int fLatestMediaMessageIndex = latestMediaMessageIndex;

            for (int i = 0; i < offlineMsgList.size(); i++) {
                Message msg = offlineMsgList.get(i);

                final int fI = i;

                switch (msg.getContentType()) {
                    case image:
                        ((ImageContent) msg.getContent()).downloadThumbnailImage(msg, new DownloadCompletionCallback() {
                            @Override
                            public void onComplete(int status, String desc, File file) {
                                if (fI == fLatestMediaMessageIndex) {
                                    for (Message msg : offlineMsgList) {
                                        msgJsonArr.put(toJson(msg));
                                    }
                                    try {
                                        json.put("messageArray", msgJsonArr);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                    eventSuccess(toJson("syncOfflineMessage", json));
                                }
                            }
                        });
                        break;
                    case voice:
                        ((VoiceContent) msg.getContent()).downloadVoiceFile(msg, new DownloadCompletionCallback() {
                            @Override
                            public void onComplete(int status, String desc, File file) {
                                if (fI == fLatestMediaMessageIndex) {
                                    for (Message msg : offlineMsgList) {
                                        msgJsonArr.put(toJson(msg));
                                    }
                                    try {
                                        json.put("messageArray", msgJsonArr);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                    eventSuccess(toJson("syncOfflineMessage", json));
                                }
                            }
                        });
                    default:
                }
            }
        }
    }

    /**
     * 漫游消息同步事件。
     *
     * @param event 漫游消息同步事件。
     */
    public void onEvent(ConversationRefreshEvent event) throws JSONException {
        if (event.getReason() == ConversationRefreshEvent.Reason.MSG_ROAMING_COMPLETE) {
            JSONObject json = new JSONObject();
            json.put("conversation", toJson(event.getConversation()));
            eventSuccess(toJson("syncRoamingMessage", json));
        }
    }

    /**
     * 用户登录状态变更事件。
     *
     * @param event 用户登录状态变更事件。
     */
    public void onEvent(LoginStateChangeEvent event) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("type", event.getReason());
        eventSuccess(toJson("loginStateChanged", json));
    }

    /**
     * 联系人相关通知事件。
     *
     * @param event 联系人相关通知事件。
     */
    public void onEvent(ContactNotifyEvent event) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("type", event.getType());
        json.put("reason", event.getReason());
        json.put("fromUsername", event.getFromUsername());
        json.put("fromUserAppKey", event.getfromUserAppKey());
        eventSuccess(toJson("contactNotify", json));
    }

    /**
     * 消息接收方收到的消息撤回事件。
     *
     * @param event 消息撤回事件。
     */
    public void onEvent(MessageRetractEvent event) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("conversation", toJson(event.getConversation()));
        json.put("retractedMessage", toJson(event.getRetractedMessage()));
        eventSuccess(toJson("retractMessage", json));
    }

    /**
     * 透传消息接收事件。
     *
     * @param event 透传消息事件。
     */
    public void onEvent(CommandNotificationEvent event) throws JSONException {
        final JSONObject result = new JSONObject();
        result.put("content", event.getMsg());

        event.getSenderUserInfo(new GetUserInfoCallback() {
            @Override
            public void gotResult(int status, String desc, UserInfo userInfo) {
                if (status == 0) {
                    try {
                        result.put("sender", toJson(userInfo));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        event.getTargetInfo(new CommandNotificationEvent.GetTargetInfoCallback() {
            @Override
            public void gotResult(int status, String desc, Object obj, CommandNotificationEvent.Type type) {
                if (status == 0) {
                    if (type == CommandNotificationEvent.Type.single) {
                        try {
                            UserInfo receiver = (UserInfo) obj;
                            result.put("receiver", toJson(receiver));
                            result.put("receiverType", "single");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                    } else {
                        try {
                            GroupInfo receiver = (GroupInfo) obj;
                            result.put("receiver", toJson(receiver));
                            result.put("receiverType", "group");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    eventSuccess(toJson("", result));
                }
            }
        });
    }

    private void eventSuccess(JSONObject value) {
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, value);
        pluginResult.setKeepCallback(true);
        mCallback.sendPluginResult(pluginResult);
    }

    // 事件处理 - end
}
