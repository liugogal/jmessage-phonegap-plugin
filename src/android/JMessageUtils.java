package cn.jiguang.cordova.im;


import android.graphics.Bitmap;
import android.os.Environment;

import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;

import cn.jpush.im.android.api.JMessageClient;
import cn.jpush.im.android.api.callback.GetUserInfoCallback;
import cn.jpush.im.android.api.content.MessageContent;
import cn.jpush.im.android.api.model.Conversation;
import cn.jpush.im.android.api.model.Message;
import cn.jpush.im.android.api.model.UserInfo;
import cn.jpush.im.android.api.options.MessageSendingOptions;
import cn.jpush.im.api.BasicCallback;

class JMessageUtils {

    private static JSONObject getErrorObject(int code, String description) throws JSONException {
        JSONObject error = new JSONObject();
        error.put("code", code);
        error.put("description", description);
        return error;
    }

    static void handleResult(int status, String desc, CallbackContext callback) {
        if (status == 0) {
            callback.success();
        } else {
            try {
                callback.error(getErrorObject(status, desc));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    static void handleResult(JSONObject returnObject, int status, String desc,
                             CallbackContext callback) {
        if (status == 0) {
            callback.success(returnObject);
        } else {
            try {
                callback.error(getErrorObject(status, desc));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    static void handleResult(JSONArray returnObject, int status, String desc,
                             CallbackContext callback) {
        if (status == 0) {
            callback.success(returnObject);
        } else {
            try {
                callback.error(getErrorObject(status, desc));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    static MessageSendingOptions toMessageSendingOptions(JSONObject json) throws JSONException {
        MessageSendingOptions messageSendingOptions = new MessageSendingOptions();

        if (json.has("isShowNotification") && !json.isNull("isShowNotification")) {
            messageSendingOptions.setShowNotification(json.getBoolean("isShowNotification"));
        }

        if (json.has("isRetainOffline") && !json.isNull("isRetainOffline")) {
            messageSendingOptions.setRetainOffline(json.getBoolean("isRetainOffline"));
        }

        if (json.has("isCustomNotificationEnabled") && !json.isNull("isCustomNotificationEnabled")) {
            messageSendingOptions.setCustomNotificationEnabled(json.getBoolean("isCustomNotificationEnabled"));
        }

        if (json.has("notificationTitle") && !json.isNull("notificationTitle")) {
            messageSendingOptions.setNotificationTitle(json.getString("notificationTitle"));
        }

        if (json.has("notificationText") && !json.isNull("notificationText")) {
            messageSendingOptions.setNotificationText(json.getString("notificationText"));
        }

        return messageSendingOptions;
    }

    static void getUserInfo(JSONObject params, GetUserInfoCallback callback) throws JSONException {
        String username, appKey;

        username = params.getString("username");
        appKey = params.has("appKey") ? params.getString("appKey") : "";

        JMessageClient.getUserInfo(username, appKey, callback);
    }

    static void updateMyInfo(UserInfo.Field field, UserInfo myInfo, final int count, final CallbackContext callback) {
        JMessageClient.updateMyInfo(field, myInfo, new BasicCallback() {
          @Override
          public void gotResult(int status, String desc) {
              if (count != 0) {  // 还有参数要更新，只执行错误回调。
                  if (status != 0) {
                      handleResult(status, desc, callback);
                  }
              } else {   // 最后一个要修改的参数，可以响应成功回调。
                  handleResult(status, desc, callback);
              }
          }
        });
    }

    static Conversation getConversation(JSONObject params) throws JSONException {
        String type = params.getString("type");
        Conversation conversation = null;

        if (type.equals("single")) {
            String username = params.getString("username");
            String appKey = params.has("appKey") ? params.getString("appKey") : "";
            conversation = JMessageClient.getSingleConversation(username, appKey);

        } else if (type.equals("group")) {
            String groupId = params.getString("groupId");
            conversation = JMessageClient.getGroupConversation(Long.parseLong(groupId));
        }
        return conversation;
    }

    static void sendMessage(Conversation conversation, MessageContent content,
                            MessageSendingOptions options, final CallbackContext callback) {
        final Message msg = conversation.createSendMessage(content);
        msg.setOnSendCompleteCallback(new BasicCallback() {
            @Override
            public void gotResult(int status, String desc) {
                if (status == 0) {
                    JSONObject json = JsonUtils.toJson(msg);
                    handleResult(json, status, desc, callback);
                } else {
                    handleResult(status, desc, callback);
                }
            }
        });

        if (options == null) {
            JMessageClient.sendMessage(msg);
        } else {
            JMessageClient.sendMessage(msg, options);
        }
    }

    static String storeImage(Bitmap bitmap, String filename, String pkgName) {
        File avatarFile = new File(getAvatarPath(pkgName));
        if (!avatarFile.exists()) {
            avatarFile.mkdirs();
        }

        String filePath = getAvatarPath(pkgName) + filename + ".png";
        try {
            FileOutputStream fos = new FileOutputStream(filePath);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);
            bos.flush();
            bos.close();
            return filePath;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return "";
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    static String getFilePath(String pkgName) {
        return Environment.getExternalStorageDirectory() + "/" + pkgName;
    }

    static String getAvatarPath(String pkgName) {
        return getFilePath(pkgName) + "/images/avatar/";
    }

    static String getFileExtension(String path) {
        return path.substring(path.lastIndexOf("."));
    }

    /**
     * 根据绝对路径或 URI 获得本地图片。
     * @param path 文件路径或者 URI。
     * @return 文件对象。
     */
    static File getFile(String path) throws FileNotFoundException {
        File file = new File(path); // if it is a absolute path

        if (!file.isFile()) {
            URI uri = URI.create(path); // if it is a uri.
            file = new File(uri);
        }

        if (!file.exists() || !file.isFile()) {
            throw new FileNotFoundException();
        }

        return file;
    }
}
