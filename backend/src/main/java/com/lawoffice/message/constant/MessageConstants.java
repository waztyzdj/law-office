package com.lawoffice.message.constant;

/**
 * 站内消息枚举常量。
 */
public final class MessageConstants {

    public static final int CONTENT_TYPE_TEXT = 1;
    public static final int CONTENT_TYPE_RICH_TEXT = 2;

    public static final int MESSAGE_TYPE_NORMAL = 1;
    public static final int MESSAGE_TYPE_NOTICE = 2;
    public static final int MESSAGE_TYPE_TODO = 3;
    public static final int MESSAGE_TYPE_FILE = 4;
    public static final int MESSAGE_TYPE_SYSTEM = 9;

    public static final int PRIORITY_NORMAL = 1;
    public static final int PRIORITY_IMPORTANT = 2;
    public static final int PRIORITY_URGENT = 3;

    public static final int SEND_STATUS_DRAFT = 0;
    public static final int SEND_STATUS_SENT = 1;
    public static final int SEND_STATUS_RECALLED = 2;

    public static final int READ_STATUS_UNREAD = 0;
    public static final int READ_STATUS_READ = 1;

    public static final int FLAG_NO = 0;
    public static final int FLAG_YES = 1;

    public static final int ACTION_TYPE_INTERNAL_ROUTE = 1;
    public static final int ACTION_TYPE_EXTERNAL_URL = 2;
    public static final int ACTION_TYPE_TODO = 3;
    public static final int ACTION_TYPE_FILE_PREVIEW = 4;
    public static final int ACTION_TYPE_CUSTOM = 99;

    public static final int OPEN_TYPE_CURRENT = 1;
    public static final int OPEN_TYPE_NEW_WINDOW = 2;

    public static final int SEND_RECORD_STATUS_PENDING = 0;
    public static final int SEND_RECORD_STATUS_SENDING = 1;
    public static final int SEND_RECORD_STATUS_SUCCESS = 2;
    public static final int SEND_RECORD_STATUS_PARTIAL_FAILED = 3;
    public static final int SEND_RECORD_STATUS_FAILED = 4;
    public static final int SEND_RECORD_STATUS_CANCELED = 5;

    private MessageConstants() {
    }
}
