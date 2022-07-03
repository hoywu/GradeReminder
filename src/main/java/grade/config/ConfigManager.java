package grade.config;

import grade.GradeReminder;

public class ConfigManager {
	public static final String CONFIG_FILE_PATH = GradeReminder.class.getResource("").getPath() + "config/config.json";
}
