package grade;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.net.ssl.*;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class GradeReminder {
    private static final String CONFIG_FILE_PATH = GradeReminder.class.getResource("").getPath() + "config/config.json";
    /**
     * 默认只返回10个科目的成绩，添加这个参数获取所有科目成绩
     */
    private static final String POST_DATA = "&queryModel.showCount=5000&queryModel.currentPage=1";

    public static void main(String[] args) {
        //System.out.println("Configuration File PATH: " + CONFIG_FILE_PATH);

        /*读取配置文件*/
        JSONObject configFile = readConfigFile();
        if (configFile == null) {
            System.out.println("Read configuration file ERROR.");
            return;
        }

        /*生成请求头*/
        /*为了支持同时查询不同学号，cookie改为在下面读取，故此处留空*/
        Map<String, String> header = getHeader("");

        //notifyNum存储已有成绩的科目数量，在有变化时推送到tgBot，为了支持多用户改为数组
        int[] notifyNum = new int[configFile.getJSONArray("studentID").length()];
        String tgBotUrl = configFile.getString("tgBotUrl");
        StringBuilder score = new StringBuilder();
        try {
            disableSSLCertCheck();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime now;
        while (true) {
            now = LocalDateTime.now();
            String time = "[" + df.format(now) + "]";
            System.out.println(time);

            //循环查询配置文件中所有学号的成绩
            for (int i = 0; i < configFile.getJSONArray("studentID").length(); i++) {
                /*从教务系统接口获得原始数据*/
                Document res;
                try {
                    //替换请求头中的cookie为对应学号的
                    header.replace("cookie", configFile.getJSONArray("cookie").getString(i));
                    //发送POST请求，这个接口应该返回一个JSON数据
                    res = Jsoup.connect(configFile.getString("requestURL") + configFile.getJSONArray("studentID").getString(i) + POST_DATA).headers(header).ignoreContentType(true).post();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    //可能出现网络错误，延迟后下一轮重新查询
                    printDelay(configFile.getInt("checkDelay"));
                    continue;
                }
                JSONObject json = new JSONObject(res.body().ownText());
                JSONArray items = json.getJSONArray("items");

                if (configFile.getInt("debug") == 1) {
                    debugFileOutput(json);
                }

                /*从JSON中取出每个科目的成绩*/
                double xf = 0; //学分
                double jd = 0; //绩点
                score.delete(0, score.length());
                score.append("[").append(items.getJSONObject(0).getString("xm")).append("]\n"); //学生姓名
                for (int j = 0; j < items.length(); j++) {
                    JSONObject jsonObject = items.getJSONObject(j);
                    score.append(jsonObject.getString("bfzcj")).append("\t"); //百分制成绩
                    score.append(jsonObject.getString("kcmc")).append("\n"); //课程名称
                    xf += Double.parseDouble(jsonObject.getString("xf"));
                    jd += Double.parseDouble(jsonObject.getString("xf")) * Double.parseDouble(jsonObject.getString("jd"));
                }
                score.append("Current GPA: ").append(String.format("%.2f", jd / xf)).append("\n");
                System.out.print(score);

                /*推送成绩更新到tgBot*/
                if (!tgBotUrl.isBlank() && items.length() != notifyNum[i]) {
                    notifyNum[i] = items.length();
                    try {
                        System.out.println("Push Notification...");
                        Jsoup.connect(tgBotUrl + "&text=" + URLEncoder.encode(time + "\n" + score, StandardCharsets.UTF_8)).ignoreContentType(true).post();
                    } catch (IOException e) {
                        System.out.println("Notification push failed: " + e.getMessage());
                    }
                }

                /*等待1s后查询下一个学号的成绩*/
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            //等待进入下一轮查询
            printDelay(configFile.getInt("checkDelay"));
        }
    }

    private static JSONObject readConfigFile() {
        File configFile = new File(CONFIG_FILE_PATH);
        if (!configFile.exists()) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("requestURL", "https://*****.*****.edu.cn/cjcx/cjcx_cxXsgrcj.html?doType=query&gnmkdm=N******&su=");
            jsonObject.put("cookie", new JSONArray().put(0, "route=; JSESSIONID=").put(1, "route=; JSESSIONID="));
            jsonObject.put("studentID", new JSONArray().put(0, "0000000001").put(1, "0000000002"));
            jsonObject.put("checkDelay", 10000);
            jsonObject.put("debug", 0);
            jsonObject.put("tgBotUrl", "");
            try {
                if (!configFile.getParentFile().exists()) {
                    configFile.getParentFile().mkdirs();
                }
                configFile.createNewFile();
                OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(CONFIG_FILE_PATH), StandardCharsets.UTF_8);
                osw.write(jsonObject.toString(4));
                osw.flush();
                osw.close();
            } catch (IOException e) {
                //写出空配置文件失败
            }
            return null;
        }

        try (InputStream inputStream = GradeReminder.class.getResourceAsStream("config/config.json")) {
            byte[] bytes = inputStream.readAllBytes();
            String s = new String(bytes);
            return new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static void debugFileOutput(JSONObject jsonObject) {
        String filePath = GradeReminder.class.getResource("").getPath();
        File debugFile = new File(filePath + "debug.json");
        if (!debugFile.exists()) {
            try {
                debugFile.createNewFile();
            } catch (IOException e) {
                //创建文件失败
                return;
            }
        }

        try {
            OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(filePath + "debug.json"), StandardCharsets.UTF_8);
            osw.write(jsonObject.toString());
            osw.flush();
            osw.close();
        } catch (IOException e) {
            //写出文件失败
        }
    }

    private static Map<String, String> getHeader(String cookie) {
        Map<String, String> header = new HashMap<>();
        header.put("Accept", "application/json, text/javascript, */*; q=0.01");
        header.put("Accept-Encoding", "gzip, deflate, br");
        header.put("Accept-Language", "zh-cn,zh;q=0.5");
        header.put("Cache-Control", "no-cache");
        header.put("Connection", "keep-alive");
        header.put("Content-Length", "148");
        header.put("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        header.put("cookie", cookie);
        header.put("DNT", "1");
        //header.put("Host", "*****.*****.edu.cn");
        //header.put("Origin", "https://*****.*****.edu.cn");
        header.put("Pragma", "no-cache");
        //header.put("Referer", "https://*****.*****.edu.cn/cjcx/cjcx_cxDgXscj.html?gnmkdm=N******&layout=default&su=**********");
        header.put("sec-ch-ua", "\" Not;A Brand\";v=\"99\", \"Microsoft Edge\";v=\"103\", \"Chromium\";v=\"103\"");
        header.put("sec-ch-ua-mobile", "?0");
        header.put("sec-ch-ua-platform", "\"Windows\"");
        header.put("Sec-Fetch-Dest", "empty");
        header.put("Sec-Fetch-Mode", "cors");
        header.put("Sec-Fetch-Site", "same-origin");
        header.put("sec-gpc", "1");
        header.put("Upgrade-Insecure-Requests", "1");
        header.put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/103.0.5060.66 Safari/537.36 Edg/103.0.1264.44");
        header.put("X-Requested-With", "XMLHttpRequest");
        return header;
    }

    private static void printDelay(long millis) {
        System.out.println("=".repeat(15) + "Wait " + millis + "ms" + "=".repeat(15));
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void disableSSLCertCheck() throws NoSuchAlgorithmException, KeyManagementException {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        }};

        // Install the all-trusting trust manager
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        // Create all-trusting host name verifier
        HostnameVerifier allHostsValid = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };

        // Install the all-trusting host verifier
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
    }
}
