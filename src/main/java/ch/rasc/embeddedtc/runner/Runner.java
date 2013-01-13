/**
 * Copyright 2013 Ralph Schaer <ralphschaer@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.rasc.embeddedtc.runner;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.servlet.ServletException;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.deploy.ApplicationParameter;
import org.apache.catalina.deploy.ContextEnvironment;
import org.apache.catalina.deploy.ContextResource;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.Tomcat;
import org.yaml.snakeyaml.Yaml;

import ch.rasc.embeddedtc.runner.CheckConfig.CheckConfigOptions;
import ch.rasc.embeddedtc.runner.ObfuscateUtil.ObfuscateOptions;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

public class Runner {

	public static final String TIMESTAMP_FILENAME = "WAR_TIMESTAMP";

	private static Tomcat tomcat;

	/**
	 * This method is called from the procrun service
	 */
	public static void start(String... args) throws Exception {
		main(args);
	}

	/**
	 * This method is called from the procrun service
	 */
	public static void stop(@SuppressWarnings("unused") String... args) throws LifecycleException {
		tomcat.stop();
	}

	public static void main(String... args) throws Exception {
		String[] arguments;
		if (args.length == 0) {
			arguments = new String[] { "start" };
		} else {
			if ("obfuscate".equals(args[0]) || "start".equals(args[0]) || "checkConfig".equals(args[0])) {
				arguments = args;
			} else {
				List<String> argumentsList = new ArrayList<>(Arrays.asList(args));
				argumentsList.add(0, "start");
				arguments = argumentsList.toArray(new String[argumentsList.size()]);
			}
		}

		JCommander commander = new JCommander();
		ObfuscateOptions obfuscateOptions = new ObfuscateOptions();
		commander.addCommand("obfuscate", obfuscateOptions);

		StartOptions startOptions = new StartOptions();
		commander.addCommand("start", startOptions);

		CheckConfigOptions checkConfigOptions = new CheckConfigOptions();
		commander.addCommand("checkConfig", checkConfigOptions);

		commander.parse(arguments);

		switch (commander.getParsedCommand()) {
		case "start":
			startTc(startOptions);
			break;
		case "obfuscate":
			ObfuscateUtil.obfuscate(obfuscateOptions);
			break;
		case "checkConfig":
			CheckConfig.check(checkConfigOptions);
			break;
		default:
			commander.usage();
			break;
		}

	}

	private static void startTc(StartOptions startOptions) throws URISyntaxException, IOException, Exception,
			ServletException, LifecycleException {

		URL myJarLocationURL = Runner.class.getProtectionDomain().getCodeSource().getLocation();
		Path myJar = Paths.get(myJarLocationURL.toURI());
		Path myJarDir = myJar.getParent();

		Path configFile;
		String pathToConfigFile = startOptions.configFile != null && !startOptions.configFile.isEmpty() ? startOptions.configFile
				.get(0) : null;

		if (pathToConfigFile != null) {
			configFile = Paths.get(pathToConfigFile);
			if (!configFile.isAbsolute()) {
				configFile = myJarDir.resolve(pathToConfigFile);
			}
		} else {
			configFile = myJarDir.resolve("config.yaml");
		}

		final Config config;
		if (Files.exists(configFile)) {
			try (InputStream is = Files.newInputStream(configFile)) {
				Yaml yaml = new Yaml();
				config = yaml.loadAs(is, Config.class);
			}
		} else {
			config = new Config();
		}

		// System.out.println(config);

		for (Map.Entry<String, Object> entry : config.getSystemProperties().entrySet()) {
			String value = entry.getValue().toString();
			value = ObfuscateUtil.toPlaintext(value, startOptions.password);
			System.setProperty(entry.getKey(), value);
		}

		Path configuredPathToExtractDir = Paths.get(config.getExtractDirectory());
		final Path extractDir;
		if (!configuredPathToExtractDir.isAbsolute()) {
			extractDir = myJarDir.resolve(configuredPathToExtractDir);
		} else {
			extractDir = configuredPathToExtractDir;
		}

		boolean extractWar = true;

		if (Files.exists(extractDir)) {
			Path timestampFile = extractDir.resolve(TIMESTAMP_FILENAME);
			if (Files.exists(timestampFile)) {
				byte[] extractTimestampBytes = Files.readAllBytes(timestampFile);
				String extractTimestamp = new String(extractTimestampBytes, StandardCharsets.UTF_8);

				String timestamp = null;
				try (InputStream is = Runner.class.getResourceAsStream("/" + TIMESTAMP_FILENAME);
						ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

					copy(is, bos);
					timestamp = new String(bos.toByteArray(), StandardCharsets.UTF_8);
				}

				if (Long.valueOf(timestamp) <= Long.valueOf(extractTimestamp)) {
					extractWar = false;
				}

			}
		}

		boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");

		Path loggingPropertyFile = extractDir.resolve("logging.properties");
		Path loggingDir = extractDir.resolve("logs");
		Path tempDir = extractDir.resolve("temp");
		final Path defaultWebxmlFile = extractDir.resolve("web.xml");

		if (extractWar) {
			if (Files.exists(extractDir)) {
				Files.walkFileTree(extractDir, new DeleteDirectory());
			}

			Files.createDirectories(extractDir);
			Files.createDirectory(tempDir);
			Files.createDirectory(loggingDir);

			CodeSource src = Runner.class.getProtectionDomain().getCodeSource();
			List<String> warList = new ArrayList<>();

			if (src != null) {
				URL jar = src.getLocation();
				ZipInputStream zip = new ZipInputStream(jar.openStream());
				ZipEntry ze = null;

				while ((ze = zip.getNextEntry()) != null) {
					String entryName = ze.getName();
					if (entryName.endsWith(".war")) {
						warList.add(entryName);
					}
				}
			}

			for (String war : warList) {
				Path warFile = extractDir.resolve(war);
				try (InputStream is = Runner.class.getResourceAsStream("/" + war)) {
					Files.copy(is, warFile);
				}
			}

			try (InputStream is = Runner.class.getResourceAsStream("/conf/web.xml")) {
				Files.copy(is, defaultWebxmlFile);
			}

			try (InputStream is = Runner.class.getResourceAsStream("/conf/logging.properties")) {
				Files.copy(is, loggingPropertyFile);
			}

			Path timestampFile = extractDir.resolve(TIMESTAMP_FILENAME);
			try (InputStream is = Runner.class.getResourceAsStream("/" + TIMESTAMP_FILENAME)) {
				Files.copy(is, timestampFile);
			}

			if (isWin) {
				if (System.getProperty("os.arch").contains("64")) {
					try (InputStream is = Runner.class.getResourceAsStream("/tcnative-1.dll.64")) {
						if (is != null) {
							Files.copy(is, extractDir.resolve("tcnative-1.dll"));
						}
					}
				} else {
					try (InputStream is = Runner.class.getResourceAsStream("/tcnative-1.dll.32")) {
						if (is != null) {
							Files.copy(is, extractDir.resolve("tcnative-1.dll"));
						}
					}
				}
			}

		}

		if (isWin) {
			String libraryPath = System.getProperty("java.library.path");
			libraryPath = extractDir.toString() + ";" + libraryPath;
			System.setProperty("java.library.path", libraryPath);

			Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
			fieldSysPath.setAccessible(true);
			fieldSysPath.set(null, null);
		}

		List<String> absolutePathsToEmbeddedWars = new ArrayList<>();

		try (DirectoryStream<Path> wars = Files.newDirectoryStream(extractDir, "*.war")) {
			for (Path war : wars) {
				absolutePathsToEmbeddedWars.add(war.toAbsolutePath().toString());
			}
		}

		System.setProperty("java.io.tmpdir", tempDir.toAbsolutePath().toString());
		System.setProperty("log.dir", loggingDir.toAbsolutePath().toString());
		System.setProperty("java.util.logging.config.file", loggingPropertyFile.toAbsolutePath().toString());
		System.setProperty("java.util.logging.manager", "org.apache.juli.ClassLoaderLogManager");

		List<Connector> connectors = config.createConnectorObjects();
		for (Connector connector : connectors) {
			try {
				try (ServerSocket srv = new ServerSocket(connector.getPort())) {
					// nothing here
				}
			} catch (IOException e) {
				getLogger().severe("PORT " + connector.getPort() + " ALREADY IN USE");
				return;
			}
		}

		tomcat = new Tomcat() {

			@Override
			public Context addWebapp(@SuppressWarnings("hiding") Host host, String url, String name, String path) {
				String base = "org.apache.catalina.core.ContainerBase.[default].[";
				if (host == null) {
					base += getHost().getName();
				} else {
					base += host.getName();
				}
				base += "].[";
				base += url;
				base += "]";
				if (config.isSilent()) {
					Logger.getLogger(base).setLevel(Level.WARNING);
				} else {
					Logger.getLogger(base).setLevel(Level.INFO);
				}

				Context ctx = new StandardContext();
				ctx.setName(name);
				ctx.setPath(url);
				ctx.setDocBase(path);

				ContextConfig ctxCfg = new ContextConfig();
				ctx.addLifecycleListener(ctxCfg);
				ctxCfg.setDefaultWebXml(defaultWebxmlFile.toAbsolutePath().toString());
				getHost().addChild(ctx);

				return ctx;
			}

		};

		tomcat.setBaseDir(extractDir.toAbsolutePath().toString());
		tomcat.setSilent(config.isSilent());

		for (String s : new String[] { "org.apache.coyote.http11.Http11NioProtocol",
				"org.apache.tomcat.util.net.NioSelectorPool", Runner.class.getName() }) {
			if (config.isSilent()) {
				Logger.getLogger(s).setLevel(Level.WARNING);
			} else {
				Logger.getLogger(s).setLevel(Level.INFO);
			}
		}

		// Create all server objects;
		tomcat.getHost();

		// Install the listeners
		for (String listenerClassName : config.getListeners()) {
			@SuppressWarnings("unchecked")
			Class<LifecycleListener> listener = (Class<LifecycleListener>) Class.forName(listenerClassName);
			tomcat.getServer().addLifecycleListener(listener.newInstance());
		}

		for (Connector connector : connectors) {
			tomcat.setConnector(connector);
			tomcat.getService().addConnector(connector);
		}

		if (config.getJvmRoute() != null && !config.getJvmRoute().isEmpty()) {
			tomcat.getEngine().setJvmRoute(config.getJvmRoute());
		}

		for (Valve valve : config.createValveObjects()) {
			tomcat.getHost().getPipeline().addValve(valve);
		}

		if (config.isEnableNaming()) {
			tomcat.enableNaming();
		}

		// No contexts configured. Create a default context.
		if (config.getContexts().isEmpty()) {
			ch.rasc.embeddedtc.runner.Context ctx = new ch.rasc.embeddedtc.runner.Context();
			ctx.setContextPath("");
			ctx.setEmbeddedWar(absolutePathsToEmbeddedWars.iterator().next());
			config.setContexts(Collections.singletonList(ctx));
		}

		List<Context> contextsWithoutSessionPersistence = new ArrayList<>();
		for (ch.rasc.embeddedtc.runner.Context configuredContext : config.getContexts()) {
			configuredContext.decryptPasswords(startOptions.password);

			String contextPath = configuredContext.getContextPath();
			if (contextPath == null) {
				contextPath = "";
			}

			String warPath = null;

			if (configuredContext.getEmbeddedWar() != null) {
				if (configuredContext.getEmbeddedWar().contains("*")) {
					String regex = ".*?"
							+ configuredContext.getEmbeddedWar().replace("\\", "\\\\").replace(".", "\\.")
									.replace("*", ".*?") + "$";
					Pattern pattern = Pattern.compile(regex);

					for (String warAbsolutePath : absolutePathsToEmbeddedWars) {
						Matcher matcher = pattern.matcher(warAbsolutePath);
						if (matcher.matches()) {
							warPath = warAbsolutePath;
							break;
						}
					}
				} else {
					warPath = configuredContext.getEmbeddedWar();
				}

				if (warPath == null) {
					warPath = absolutePathsToEmbeddedWars.iterator().next();
				}
			} else if (configuredContext.getExternalWar() != null) {
				Path externWarPath = Paths.get(configuredContext.getExternalWar());
				if (externWarPath.isAbsolute()) {
					warPath = configuredContext.getExternalWar();
				} else {
					warPath = myJarDir.resolve(configuredContext.getExternalWar()).toString();
				}
			} else {
				// As a default, if no war is specified, take the first war
				// that's embedded in our jar
				warPath = absolutePathsToEmbeddedWars.iterator().next();
			}

			Context ctx = tomcat.addWebapp(contextPath, warPath);
			ctx.setSwallowOutput(true);

			for (ContextEnvironment env : configuredContext.getEnvironments()) {
				ctx.getNamingResources().addEnvironment(env);
			}

			for (ContextResource res : configuredContext.createContextResourceObjects()) {
				ctx.getNamingResources().addResource(res);
			}

			for (ApplicationParameter param : configuredContext.getParameters()) {
				ctx.addApplicationParameter(param);
			}

			if (configuredContext.getContextFile() != null) {
				Path contextFilePath = Paths.get(configuredContext.getContextFile());
				if (Files.exists(contextFilePath)) {
					try {
						URL contextFileURL = contextFilePath.toUri().toURL();
						ctx.setConfigFile(contextFileURL);
					} catch (Exception e) {
						getLogger().severe("Problem with the context file: " + e.getMessage());
					}
				}
			} else {
				URL contextFileURL = getContextXml(warPath);
				if (contextFileURL != null) {
					ctx.setConfigFile(contextFileURL);
				}
			}

			// Shutdown tomcat if a failure occurs during startup
			ctx.addLifecycleListener(new LifecycleListener() {
				@Override
				public void lifecycleEvent(LifecycleEvent event) {
					if (event.getLifecycle().getState() == LifecycleState.FAILED) {
						((StandardServer) tomcat.getServer()).stopAwait();
					}
				}
			});

			if (!configuredContext.isSessionPersistence()) {
				contextsWithoutSessionPersistence.add(ctx);
			}
		}

		tomcat.start();

		// Disable session persistence support
		for (Context ctx : contextsWithoutSessionPersistence) {
			((StandardManager) ctx.getManager()).setPathname(null);
		}

		tomcat.getServer().await();
	}

	private static URL getContextXml(String warPath) throws IOException {
		String urlStr = "jar:file:" + warPath + "!/META-INF/context.xml";
		URL url = new URL(urlStr);
		try (InputStream is = url.openConnection().getInputStream()) {
			if (is != null) {
				return url;
			}
		} catch (FileNotFoundException e) {
			// ignore this exception
		}

		return null;
	}

	public static Logger getLogger() {
		return Logger.getLogger(Runner.class.getName());
	}

	private static void copy(InputStream source, OutputStream sink) throws IOException {
		byte[] buf = new byte[8192];
		int n;
		while ((n = source.read(buf)) > 0) {
			sink.write(buf, 0, n);
		}
	}

	@Parameters(commandDescription = "Starts the Tomcat")
	private static class StartOptions {
		@Parameter(required = false, arity = 1, description = "absolutePathToConfigFile")
		private List<String> configFile;

		@Parameter(names = { "-p", "--password" }, description = "The password")
		private final String password = null;
	}
}
