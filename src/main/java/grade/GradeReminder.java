package grade;

import grade.util.PrintMessageUtil;
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
	public static void main(String[] args) {
		PrintMessageUtil.printConfFilePath();
		JSONObject configFile = readConfigFile();
		if (configFile == null) {
			System.out.println("Read configuration file ERROR.");
			return;
		}

		Map<String, String> header = getHeader(configFile.getString("cookie"));

		int notifyNum = 0;
		String tgBotUrl = configFile.getString("tgBotUrl");
		StringBuilder score = new StringBuilder();
		try {
			disableSSLCertCheck();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		while (true) {
			DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
			LocalDateTime now = LocalDateTime.now();
			String time = "[" + df.format(now) + "]";
			System.out.println(time);

			Document res;
			try {
				res = Jsoup.connect(configFile.getString("requestUrl")).headers(header).ignoreContentType(true).post();
			} catch (Exception e) {
				System.out.println(e);
				printDelay(configFile.getInt("checkDelay"));
				continue;
			}
			JSONObject json = new JSONObject(res.body().ownText());
			JSONArray items = json.getJSONArray("items");

			if (configFile.getInt("debug") == 1) {
				debugFileOutput(json);
			}

			double xf = 0;
			double jd = 0;
			score.delete(0, score.length());
			for (int i = 0; i < items.length(); i++) {
				JSONObject jsonObject = items.getJSONObject(i);
				score.append(jsonObject.getString("bfzcj")).append("\t");
				score.append(jsonObject.getString("kcmc")).append("\n");
				xf += Double.parseDouble(jsonObject.getString("xf"));
				jd += Double.parseDouble(jsonObject.getString("xf")) * Double.parseDouble(jsonObject.getString("jd"));
			}
			score.append("Current GPA: ").append(String.format("%.2f", jd / xf)).append("\n");
			System.out.print(score);

			if (!tgBotUrl.isBlank() && items.length() != notifyNum) {
				notifyNum = items.length();
				try {
					System.out.println("Push Notification...");
					Jsoup.connect(tgBotUrl + "&text=" + URLEncoder.encode(time + "\n" + score, StandardCharsets.UTF_8)).ignoreContentType(true).post();
				} catch (IOException e) {
					System.out.println("Notification push failed: " + e.getMessage());
				}
			}

			printDelay(configFile.getInt("checkDelay"));
		}
	}

	private static void printDelay(long millis) {
		System.out.println("=".repeat(15) + "Wait " + millis + "ms" + "=".repeat(15));
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private static JSONObject readConfigFile() {
		String filePath = GradeReminder.class.getResource("").getPath();
		File configFile = new File(filePath + "config/config.json");
		if (!configFile.exists()) {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("requestUrl", "");
			jsonObject.put("cookie", "");
			jsonObject.put("checkDelay", 10000);
			jsonObject.put("debug", 0);
			jsonObject.put("tgBotUrl", "");
			try {
				if (!configFile.getParentFile().exists()) {
					configFile.getParentFile().mkdirs();
				}
				configFile.createNewFile();
				OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(filePath + "config/config.json"), StandardCharsets.UTF_8);
				osw.write(jsonObject.toString());
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

	private static void disableSSLCertCheck() throws NoSuchAlgorithmException, KeyManagementException {
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
