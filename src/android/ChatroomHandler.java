package cn.jiguang.cordova.im;

import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.jpush.im.android.api.ChatRoomManager;
import cn.jpush.im.android.api.JMessageClient;
import cn.jpush.im.android.api.callback.RequestCallback;
import cn.jpush.im.android.api.model.ChatRoomInfo;
import cn.jpush.im.android.api.model.Conversation;
import cn.jpush.im.api.BasicCallback;

import static cn.jiguang.cordova.im.JMessagePlugin.ERR_CODE_PARAMETER;
import static cn.jiguang.cordova.im.JMessagePlugin.ERR_MSG_PARAMETER;
import static cn.jiguang.cordova.im.JMessageUtils.handleResult;
import static cn.jiguang.cordova.im.JsonUtils.toJson;

/**
 * 处理聊天室相关 API。
 */

class ChatroomHandler {

    static void getChatroomInfoOfApp(JSONArray data, final CallbackContext callback) {
        int start, count;
        try {
            JSONObject params = data.getJSONObject(0);
            start = params.getInt("start");
            count = params.getInt("count");
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        ChatRoomManager.getChatRoomListByApp(start, count, new RequestCallback<List<ChatRoomInfo>>() {
            @Override
            public void gotResult(int status, String desc, List<ChatRoomInfo> chatRoomInfos) {
                if (status != 0) {
                    handleResult(status, desc, callback);
                    return;
                }

                JSONArray jsonArr = new JSONArray();
                try {
                    for (ChatRoomInfo chatroomInfo : chatRoomInfos) {
                        jsonArr.put(toJson(chatroomInfo));
                    }
                } catch (JSONException e) { // 因为转 json 的操作一定没有问题，所以这里不做多余处理。
                    e.printStackTrace();
                }
                callback.success(jsonArr);
            }
        });
    }

    static void getChatroomInfoOfUser(JSONArray data, final CallbackContext callback) {
        ChatRoomManager.getChatRoomListByUser(new RequestCallback<List<ChatRoomInfo>>() {
            @Override
            public void gotResult(int status, String desc, List<ChatRoomInfo> chatRoomInfos) {
                if (status != 0) {
                    handleResult(status, desc, callback);
                    return;
                }

                JSONArray jsonArr = new JSONArray();
                try {
                    for (ChatRoomInfo chatroomInfo : chatRoomInfos) {
                        jsonArr.put(toJson(chatroomInfo));
                    }
                } catch (JSONException e) { // 因为转 json 的操作一定没有问题，所以这里不做多余处理。
                    e.printStackTrace();
                }
                callback.success(jsonArr);
            }
        });
    }

    static void getChatroomInfoById(JSONArray data, final CallbackContext callback) {
        Set<Long> roomIds = new HashSet<Long>();

        try {
            JSONObject params = data.getJSONObject(0);
            JSONArray roomIdArr = params.getJSONArray("roomIds");

            for (int i = 0; i < roomIdArr.length(); i++) {
                roomIds.add(roomIdArr.getLong(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        ChatRoomManager.getChatRoomInfos(roomIds, new RequestCallback<List<ChatRoomInfo>>() {
            @Override
            public void gotResult(int status, String desc, List<ChatRoomInfo> chatRoomInfos) {
                if (status != 0) {
                    handleResult(status, desc, callback);
                    return;
                }

                JSONArray jsonArr = new JSONArray();
                try {
                    for (ChatRoomInfo chatroomInfo : chatRoomInfos) {
                        jsonArr.put(toJson(chatroomInfo));
                    }
                } catch (JSONException e) { // 因为转 json 的操作一定没有问题，所以这里不做多余处理。
                    e.printStackTrace();
                }
                callback.success(jsonArr);
            }
        });
    }

    static void enterChatroom(JSONArray data, final CallbackContext callback) {
        final long roomId;

        try {
            JSONObject params = data.getJSONObject(0);
            roomId = params.getLong("roomId");
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        ChatRoomManager.enterChatRoom(roomId, new RequestCallback<Conversation>() {
            @Override
            public void gotResult(int status, String desc, Conversation conversation) {
                if (status != 0) {
                    handleResult(status, desc, callback);
                    return;
                }

                JSONObject result = new JSONObject();
                try {
                    result.put("roomId", roomId);
                    result.put("conversation", toJson(conversation));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                callback.success(result);
            }
        });
    }

    static void exitChatroom(JSONArray data, final CallbackContext callback) {
        final long roomId;

        try {
            JSONObject params = data.getJSONObject(0);
            roomId = params.getLong("roomId");
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        ChatRoomManager.leaveChatRoom(roomId, new BasicCallback() {
            @Override
            public void gotResult(int status, String desc) {
                if (status == 0) {  // success
                    callback.success();
                } else {
                    handleResult(status, desc, callback);
                }
            }
        });
    }

    static void getChatroomConversation(JSONArray data, final CallbackContext callback) {
        final long roomId;

        try {
            JSONObject params = data.getJSONObject(0);
            roomId = params.getLong("roomId");
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        Conversation conversation = JMessageClient.getChatRoomConversation(roomId);
        callback.success(toJson(conversation));
    }

    static void getChatroomConversationList(JSONArray data, final CallbackContext callback) {
        List<Conversation> conversations = JMessageClient.getChatRoomConversationList();
        JSONArray result = new JSONArray();

        for (Conversation con : conversations) {
            result.put(toJson(con));
        }
        callback.success(result);
    }

    static void createChatroomConversation(JSONArray data, CallbackContext callback) {
        long roomId;

        try {
            JSONObject params = data.getJSONObject(0);
            roomId = params.getLong("roomId");
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        Conversation conversation = Conversation.createChatRoomConversation(roomId);
        callback.success(toJson(conversation));
    }

    static void deleteChatroomConversation(JSONArray data, CallbackContext callback) {
        long roomId;

        try {
            JSONObject params = data.getJSONObject(0);
            roomId = params.getLong("roomId");
        } catch (JSONException e) {
            e.printStackTrace();
            handleResult(ERR_CODE_PARAMETER, ERR_MSG_PARAMETER, callback);
            return;
        }

        boolean isSuccess = JMessageClient.deleteChatRoomConversation(roomId);
        if (isSuccess) {
            callback.success();
        } else {
            JSONObject error = new JSONObject();
            try {
                error.put("code", 10);
                error.put("description", "Delete failed.");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            callback.error(error);
        }
    }
}
