package com.devccv.util.network;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * HTTP请求结果
 */
public class RequestResult {
    public boolean debugMode = false;
    private HttpsURLConnection connection;
    /**
     * 请求是否成功，为假时仅保证errorMsg和exception不为null
     */
    private final boolean isSucceed;
    private String response;
    private Map<String, List<String>> headerFields;
    private Exception exception = new RuntimeException("Unknown Error!");
    private String errorMsg;

    public RequestResult(HttpsURLConnection httpsURLConnection) {
        this.isSucceed = true;
        this.connection = httpsURLConnection;
    }

    public RequestResult(HttpsURLConnection httpsURLConnection, Map<String, List<String>> headerFields) {
        this.isSucceed = true;
        this.connection = httpsURLConnection;
        this.headerFields = headerFields;
    }

    public RequestResult(Exception ex) {
        this.isSucceed = false;
        this.exception = ex;
        this.errorMsg = ex.getMessage();
    }

    public RequestResult(String response) {
        this.isSucceed = true;
        this.response = response;
    }

    public RequestResult(String response, Map<String, List<String>> headerFields) {
        this.isSucceed = true;
        this.response = response;
        this.headerFields = headerFields;
    }

    public boolean isSucceed() {
        return isSucceed;
    }

    public InputStream getInputStream() throws IOException {
        return connection.getInputStream();
    }

    public OutputStream getOutputStream() throws IOException {
        return connection.getOutputStream();
    }

    public String getResponse() {
        return getResponseOrElse(null);
    }

    public String getResponseOrElse(String other) {
        if (response == null) {
            if (debugMode) exception.printStackTrace();
            return other;
        }
        return response;
    }

    public String getResponseOrException() throws IOException {
        if (response == null) {
            if (exception != null) throw new IOException(exception);
            else throw new IOException("No Response or Exception!");
        }
        return response;
    }

    public Map<String, List<String>> getHeaderFields() {
        return getHeaderFieldsOrElse(null);
    }

    public Map<String, List<String>> getHeaderFieldsOrElse(Map<String, List<String>> other) {
        if (headerFields == null) {
            if (debugMode) exception.printStackTrace();
            return other;
        }
        return headerFields;
    }

    public Exception getException() {
        return exception;
    }

    public String getErrorMsg() {
        return errorMsg;
    }
}
