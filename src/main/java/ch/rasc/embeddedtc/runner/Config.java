package ch.rasc.embeddedtc.runner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.IntrospectionUtils;

public class Config {

	private String extractDirectory = "tc";

	private String jvmRoute;

	private boolean silent = false;

	private Set<String> listeners = new HashSet<>(Arrays.asList("org.apache.catalina.core.AprLifecycleListener",
			"org.apache.catalina.core.JasperListener", "org.apache.catalina.core.JreMemoryLeakPreventionListener",
			"org.apache.catalina.mbeans.GlobalResourcesLifecycleListener",
			"org.apache.catalina.core.ThreadLocalLeakPreventionListener"));

	private Map<String, Object> systemProperties = Collections.emptyMap();

	private Map<String, Object> connector;

	private List<Map<String, Object>> connectors = Collections.emptyList();

	private Context context;

	private List<Context> contexts = Collections.emptyList();

	public String getJvmRoute() {
		return jvmRoute;
	}

	public void setJvmRoute(String jvmRoute) {
		this.jvmRoute = jvmRoute;
	}

	public boolean isSilent() {
		return silent;
	}

	public void setSilent(boolean silent) {
		this.silent = silent;
	}

	public Set<String> getListeners() {
		return listeners;
	}

	public void setListeners(Set<String> listeners) {
		this.listeners = listeners;
	}

	public void setConnectors(List<Map<String, Object>> connectors) {
		this.connectors = connectors;
	}

	public void setConnector(Map<String, Object> connector) {
		this.connector = connector;
	}

	public List<Context> getContexts() {
		if (context != null) {
			if (contexts.isEmpty()) {
				return Collections.singletonList(context);
			}

			List<Context> combinedContexts = new ArrayList<>(contexts);
			combinedContexts.add(context);
			return combinedContexts;
		}
		return contexts;
	}

	public void setContexts(List<Context> contexts) {
		this.contexts = contexts;
	}

	public void setContext(Context context) {
		this.context = context;
	}

	public Map<String, Object> getSystemProperties() {
		return systemProperties;
	}

	public void setSystemProperties(Map<String, Object> systemProperties) {
		this.systemProperties = systemProperties;
	}

	public String getExtractDirectory() {
		return extractDirectory;
	}

	public void setExtractDirectory(String extractDirectory) {
		this.extractDirectory = extractDirectory;
	}

	private static final String CONNECTOR_PROTOCOL = "protocol";

	public List<Connector> createConnectorObjects() throws Exception {
		List<Connector> conObjects = new ArrayList<>();

		if (connector != null) {
			if (connectors.isEmpty()) {
				setConnectors(Collections.singletonList(connector));
			} else {
				connectors.add(connector);
			}
		}

		for (Map<String, Object> con : connectors) {
			Object protocol = con.get(CONNECTOR_PROTOCOL);
			if (protocol == null) {
				protocol = "HTTP/1.1";
			}
			Connector tcConnector = new Connector(protocol.toString());

			for (Map.Entry<String, Object> entry : con.entrySet()) {
				if (!entry.getKey().equals(CONNECTOR_PROTOCOL)) {
					IntrospectionUtils.setProperty(tcConnector, entry.getKey(), entry.getValue().toString());
				}
			}

			conObjects.add(tcConnector);
		}

		return conObjects;
	}

	public boolean isEnableNaming() {
		for (Context ctx : getContexts()) {
			if (ctx.hasEnvironmentsOrResources() || ctx.getContextFile() != null) {
				return true;
			}
		}

		return false;
	}

	@Override
	public String toString() {
		return "Config [jvmRoute=" + jvmRoute + ", silent=" + silent + ", listeners=" + listeners
				+ ", systemProperties=" + systemProperties + ", connectors=" + connectors + ", context=" + context
				+ ", contexts=" + contexts + "]";
	}

}
