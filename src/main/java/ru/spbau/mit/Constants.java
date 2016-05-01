package ru.spbau.mit;

public final class Constants {
    public static final int SERVER_PORT = 8081;

    public static final int LIST_QUERY = 1;
    public static final int UPLOAD_QUERY = 2;
    public static final int SOURCES_QUERY = 3;
    public static final int UPDATE_QUERY = 4;

    public static final int STAT_QUERY = 1;
    public static final int GET_QUERY = 2;
    public static final int UPDATE_INTERVAL = 1000;
    public static final long UPDATE_TIMEOUT = 60000;

    public static final String NAME_OF_CLIENT_STATE_FILE = ".torrentClientState";
    public static final String NAME_OF_SERVER_STATE_FILE = ".torrentServerState";
    public static final String NAME_DOWNLOAD_DIR = "downloads";

    public static final int MIN_PORT = 2000;
    public static final int MAX_PORT = 32000;

    private Constants() {
    }
}
