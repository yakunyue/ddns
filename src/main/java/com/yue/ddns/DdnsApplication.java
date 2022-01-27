package com.yue.ddns;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@EnableScheduling
@SpringBootApplication
public class DdnsApplication {

	/**
	 * 配置文件路径, 以','分割的字符串. 配置采用覆盖式, 当有多个配置路径, 且包含相同配置属性时, 后者会覆盖前者.
	 * (windows环境下 /home/...以当前磁盘为根目录)
	 */
	public final static String CONFIG_FILES_PATH = "configFilesPath";
	private static final String WINDOWS = "windows";
	private static final String LINUX = "linux";

	public static void main(String[] args) {
		// 默认配置位置
		String configFilesPath = "classpath:application.yaml";
		String os = System.getProperty("os.name")
				.toLowerCase();
		if (os.contains(WINDOWS)) {
			// 开发环境配置文件位置，优先级比类路径下的高
			String userDir = System.getProperty("user.home");//系统用户目录
			String devPath = userDir + "\\my-localhost-config-center\\ddns\\application.yaml";
			devPath = devPath.replaceAll("\\\\", "/");
			configFilesPath = String.join(",", configFilesPath, devPath);
		} else {
			// 生产环境配置文件位置，优先级比类路径下的高
			String prodPath = "file:/mycloud/ddns/application.yaml";
			configFilesPath = String.join(",", configFilesPath, prodPath);
		}

		System.setProperty(CONFIG_FILES_PATH, configFilesPath);//这个好像没用吧？@yuexiaobing

		String[] newArgs = new String[args.length+1];
		for (int i = 0; i < args.length; i++) {
			newArgs[i] = args[i];
		}
		newArgs[args.length] = "--spring.config.location=" + configFilesPath;

		// 非web方式启动
		new SpringApplicationBuilder()
                .sources(DdnsApplication.class).run(newArgs);

    }
}
