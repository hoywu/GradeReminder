package grade.util;

import grade.config.ConfigManager;

public class PrintMessageUtil {
	public static void printConfFilePath() {
		System.out.println("Configuration File PATH: " + ConfigManager.CONFIG_FILE_PATH);
	}
}
