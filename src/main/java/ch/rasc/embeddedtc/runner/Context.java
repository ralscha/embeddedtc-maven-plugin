package ch.rasc.embeddedtc.runner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.catalina.deploy.ApplicationParameter;
import org.apache.catalina.deploy.ContextEnvironment;
import org.apache.catalina.deploy.ContextResource;
import org.apache.tomcat.util.IntrospectionUtils;

public class Context {
	private String embeddedWar;

	private String externalWar;

	private String contextPath;

	private String contextFile;

	private boolean sessionPersistence = false;

	private List<Map<String, Object>> resources = Collections.emptyList();

	private List<ContextEnvironment> environments = Collections.emptyList();

	private List<ApplicationParameter> parameters = Collections.emptyList();

	private Map<String, Object> resource;

	private ContextEnvironment environment;

	private ApplicationParameter parameter;

	public String getEmbeddedWar() {
		return embeddedWar;
	}

	public void setEmbeddedWar(String embeddedWar) {
		this.embeddedWar = embeddedWar;
	}

	public String getExternalWar() {
		return externalWar;
	}

	public void setExternalWar(String externalWar) {
		this.externalWar = externalWar;
	}

	public String getContextPath() {
		return contextPath;
	}

	public void setContextPath(String contextPath) {
		this.contextPath = contextPath;
	}

	public void setResources(List<Map<String, Object>> resources) {
		this.resources = resources;
	}

	public List<ContextEnvironment> getEnvironments() {

		if (environment != null) {
			if (environments.isEmpty()) {
				return Collections.singletonList(environment);
			}

			List<ContextEnvironment> combinedEnvironments = new ArrayList<>(environments);
			combinedEnvironments.add(environment);
			return combinedEnvironments;
		}

		return environments;
	}

	public void setEnvironments(List<ContextEnvironment> environments) {
		this.environments = environments;
	}

	public boolean isSessionPersistence() {
		return sessionPersistence;
	}

	public void setSessionPersistence(boolean sessionPersistence) {
		this.sessionPersistence = sessionPersistence;
	}

	public String getContextFile() {
		return contextFile;
	}

	public void setContextFile(String contextFile) {
		this.contextFile = contextFile;
	}

	public List<ApplicationParameter> getParameters() {
		if (parameter != null) {
			if (parameters.isEmpty()) {
				return Collections.singletonList(parameter);
			}

			List<ApplicationParameter> combinedParameters = new ArrayList<>(parameters);
			combinedParameters.add(parameter);
			return combinedParameters;
		}

		return parameters;
	}

	public void setParameters(List<ApplicationParameter> parameters) {
		this.parameters = parameters;
	}

	public void setResource(Map<String, Object> resource) {
		this.resource = resource;
	}

	public void setEnvironment(ContextEnvironment environment) {
		this.environment = environment;
	}

	public void setParameter(ApplicationParameter parameter) {
		this.parameter = parameter;
	}

	public boolean hasEnvironmentsOrResources() {
		return !environments.isEmpty() || !resources.isEmpty() || environment != null || resource != null;
	}

	public List<ContextResource> createContextResourceObjects() {
		List<ContextResource> crObjects = new ArrayList<>();

		if (resource != null) {
			if (resources.isEmpty()) {
				setResources(Collections.singletonList(resource));
			} else {
				resources.add(resource);
			}
		}

		for (Map<String, Object> res : resources) {
			ContextResource contextResource = new ContextResource();

			for (Map.Entry<String, Object> entry : res.entrySet()) {
				IntrospectionUtils.setProperty(contextResource, entry.getKey(), entry.getValue().toString());
			}

			crObjects.add(contextResource);
		}

		return crObjects;
	}

	public void decryptPasswords(String password) {
		if (parameter != null) {
			parameter.setValue(ObfuscateUtil.toPlaintext(parameter.getValue(), password));
		}

		if (resource != null) {
			Map<String, Object> newResource = new HashMap<>();
			for (Map.Entry<String, Object> entry : resource.entrySet()) {
				if (entry.getValue() instanceof String) {
					newResource.put(entry.getKey(), ObfuscateUtil.toPlaintext((String) entry.getValue(), password));
				} else {
					newResource.put(entry.getKey(), entry.getValue());
				}
			}
			resource = newResource;
		}

		if (environment != null) {
			environment.setValue(ObfuscateUtil.toPlaintext(environment.getValue(), password));
		}

		List<Map<String, Object>> newResources = new ArrayList<>();
		for (Map<String, Object> res : resources) {
			Map<String, Object> newResource = new HashMap<>();
			for (Map.Entry<String, Object> entry : res.entrySet()) {
				if (entry.getValue() instanceof String) {
					newResource.put(entry.getKey(), ObfuscateUtil.toPlaintext((String) entry.getValue(), password));
				} else {
					newResource.put(entry.getKey(), entry.getValue());
				}
			}
			newResources.add(newResource);
		}
		resources = newResources;

		for (ContextEnvironment ce : environments) {
			ce.setValue(ObfuscateUtil.toPlaintext(ce.getValue(), password));
		}

		for (ApplicationParameter ap : parameters) {
			ap.setValue(ObfuscateUtil.toPlaintext(ap.getValue(), password));
		}

	}

	@Override
	public String toString() {
		return "Context [embeddedWar=" + embeddedWar + ", externalWar=" + externalWar + ", contextPath=" + contextPath
				+ ", contextFile=" + contextFile + ", sessionPersistence=" + sessionPersistence + ", resources="
				+ resources + ", environments=" + environments + ", parameters=" + parameters + ", resource="
				+ resource + ", environment=" + environment + ", parameter=" + parameter + "]";
	}

}