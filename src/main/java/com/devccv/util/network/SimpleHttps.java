package com.devccv.util.network;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleHttps {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36";

    public static final class Argument {
        private static final int DEFAULT_CONNECT_TIMEOUT = 5000;
        private static final int DEFAULT_READ_TIMEOUT = 5000;
        private final String url;
        private Map<String, String> requestProperty;
        private byte[] postData = null;
        private boolean needHeaderFields = false;
        private boolean needResponse = true;
        private Proxy proxy = Proxy.NO_PROXY;
        private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private int readTimeout = DEFAULT_READ_TIMEOUT;
        private boolean needOutputStream = false;
        private boolean needInputStream = false;

        public Argument(String url) {
            this.url = url;
        }

        public Argument setRequestProperty(Map<String, String> requestProperty) {
            this.requestProperty = requestProperty;
            return this;
        }

        public Argument addRequestProperty(String key, String value) {
            if (this.requestProperty == null) {
                this.requestProperty = new HashMap<>();
            }
            requestProperty.put(key, value);
            return this;
        }

        public Argument setPostData(byte[] postData) {
            this.postData = postData;
            return this;
        }

        public Argument setNeedHeaderFields(boolean needHeaderFields) {
            this.needHeaderFields = needHeaderFields;
            return this;
        }

        public Argument setNeedResponse(boolean needResponse) {
            this.needResponse = needResponse;
            return this;
        }

        public Argument setProxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public Argument setSocksProxy(String hostname, int port) {
            this.proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(hostname, port));
            return this;
        }

        public Argument setHttpProxy(String hostname, int port) {
            this.proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(hostname, port));
            return this;
        }

        public void setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Argument setReadTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public Argument setNeedOutputStream(boolean needOutputStream) {
            this.needOutputStream = needOutputStream;
            return this;
        }

        public Argument setNeedInputStream(boolean needInputStream) {
            this.needInputStream = needInputStream;
            return this;
        }
    }

    /**
     * 发送GET请求，仅返回响应body
     *
     * @param url 请求地址
     */
    public static RequestResult GET(String url) {
        return send(HTTP_METHOD.GET, url, null, null, false, true, Proxy.NO_PROXY, Argument.DEFAULT_CONNECT_TIMEOUT, Argument.DEFAULT_READ_TIMEOUT, false, false);
    }

    /**
     * 发送GET请求
     *
     * @param arg 请求参数
     */
    public static RequestResult GET(Argument arg) {
        return send(HTTP_METHOD.GET, arg.url, arg.requestProperty, arg.postData, arg.needHeaderFields, arg.needResponse, arg.proxy, arg.connectTimeout, arg.readTimeout, arg.needInputStream, arg.needOutputStream);
    }

    /**
     * 发送POST请求，无请求主体，仅返回响应body
     *
     * @param url 请求地址
     */
    public static RequestResult POST(String url) {
        return send(HTTP_METHOD.POST, url, null, null, false, true, Proxy.NO_PROXY, Argument.DEFAULT_CONNECT_TIMEOUT, Argument.DEFAULT_READ_TIMEOUT, false, false);
    }

    /**
     * 发送POST请求
     *
     * @param arg 请求参数
     */
    public static RequestResult POST(Argument arg) {
        return send(HTTP_METHOD.POST, arg.url, arg.requestProperty, arg.postData, arg.needHeaderFields, arg.needResponse, arg.proxy, arg.connectTimeout, arg.readTimeout, arg.needInputStream, arg.needOutputStream);
    }

    /**
     * @param method
     * @param url
     * @param requestProperty  @Nullable
     * @param postData         @Nullable
     * @param needHeaderFields
     * @param needResponse
     * @param proxy
     * @param connectTimeout
     * @param readTimeout
     * @return
     */
    private static RequestResult send(HTTP_METHOD method, String url, Map<String, String> requestProperty,
                                      byte[] postData, boolean needHeaderFields, boolean needResponse, Proxy proxy,
                                      int connectTimeout, int readTimeout, boolean needInputStream, boolean needOutputStream) {
        try {
            HttpsURLConnection httpsURLConnection = getHttpsURLConnection(method, url, requestProperty, proxy, connectTimeout, readTimeout);

            if (method == HTTP_METHOD.POST && postData != null) {
                if (needOutputStream) return new RequestResult(httpsURLConnection);
                try (OutputStream outputStream = httpsURLConnection.getOutputStream()) {
                    //getOutputStream() 和 getInputStream() 隐式调用 connect()
                    outputStream.write(postData);
                    outputStream.flush();
                }
            }

            Map<String, List<String>> headerFields = null;
            if (needHeaderFields) {
                headerFields = httpsURLConnection.getHeaderFields();
            }

            StringBuilder rawData = null;
            if (needResponse) {
                rawData = new StringBuilder();
                if (needInputStream) return new RequestResult(httpsURLConnection, headerFields);
                try (InputStream inputStream = httpsURLConnection.getInputStream();
                     BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    String data;
                    while ((data = bufferedReader.readLine()) != null) {
                        rawData.append(data).append('\n');
                    }
                }
            }
            String rawDataString = rawData != null ? rawData.toString() : null;

            return new RequestResult(rawDataString, headerFields);
        } catch (IOException e) {
            return new RequestResult(e);
        }
    }

    /**
     * @param method
     * @param urlString
     * @param requestProperty @Nullable
     * @param proxy
     * @param connectTimeout
     * @param readTimeout
     * @return
     * @throws IOException
     */
    private static HttpsURLConnection getHttpsURLConnection(HTTP_METHOD method, String urlString,
                                                            Map<String, String> requestProperty, Proxy proxy,
                                                            int connectTimeout, int readTimeout) throws IOException {
        URL url = new URL(urlString);
        HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection(proxy);
        httpsURLConnection.setConnectTimeout(connectTimeout);
        httpsURLConnection.setReadTimeout(readTimeout);
        if (method == HTTP_METHOD.POST) {
            httpsURLConnection.setRequestMethod("POST");
            httpsURLConnection.setDoOutput(true);
        }
        //默认请求属性，如传入新值会覆盖
        httpsURLConnection.setRequestProperty("Connection", "Keep-Alive");
        //Keep-Alive时关闭输入流不会disconnect()连接
        //https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/HttpURLConnection.html
        //每个HttpURLConnection实例用于发出单个请求，但是与HTTP服务器的基础网络连接可以由其他实例透明地共享。
        //在请求之后调用HttpURLConnection的InputStream或OutputStream上的close()方法可以释放与此实例关联的网络资源，
        // 但不会影响任何共享持久连接。
        //如果此时持久连接处于空闲状态，则调用disconnect()方法可能会关闭底层套接字。
        httpsURLConnection.setRequestProperty("User-Agent", USER_AGENT);
        if (requestProperty != null) {
            for (Map.Entry<String, String> entry : requestProperty.entrySet()) {
                httpsURLConnection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        return httpsURLConnection;
    }
}
