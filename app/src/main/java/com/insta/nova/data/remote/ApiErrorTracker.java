package com.insta.nova.data.remote;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * In-memory FIFO ring-buffer recording the last 5 API errors for the Diagnostics screen.
 */
public class ApiErrorTracker {

    public static class ErrorRecord {
        public final String timestamp;
        public final String method;
        public final String url;
        public final int statusCode;
        public final String errorBody;

        public ErrorRecord(String method, String url, int statusCode, String errorBody) {
            this.timestamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            this.method = method;
            this.url = url;
            this.statusCode = statusCode;
            this.errorBody = errorBody;
        }

        @Override
        public String toString() {
            return "[" + timestamp + "] " + method + " " + url + " -> " + statusCode + "\n" + errorBody;
        }
    }

    private static final int MAX_ERRORS = 5;
    private static final LinkedList<ErrorRecord> errorList = new LinkedList<>();

    public static synchronized void recordError(String method, String url, int statusCode, String errorBody) {
        if (errorList.size() >= MAX_ERRORS) {
            errorList.removeFirst();
        }
        errorList.addLast(new ErrorRecord(method, url, statusCode, errorBody));
    }

    public static synchronized List<ErrorRecord> getLastErrors() {
        List<ErrorRecord> list = new ArrayList<>(errorList);
        Collections.reverse(list);
        return list;
    }

    public static synchronized void clear() {
        errorList.clear();
    }
}
