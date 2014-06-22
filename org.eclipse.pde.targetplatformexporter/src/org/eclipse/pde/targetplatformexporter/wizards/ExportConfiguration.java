package org.eclipse.pde.targetplatformexporter.wizards;

import org.eclipse.core.runtime.Platform;

public class ExportConfiguration {
	String os = "";
	String ws = "";
	String arch = "";
	
	public ExportConfiguration() {
	}
	
	public String getOs() {
		return os;
	}
	public void setOs(String os) {
		this.os = os;
	}
	public String getArch() {
		return arch;
	}
	public void setArch(String arch) {
		this.arch = arch;
	}
	public String getWs() {
		return ws;
	}
	public void setWs(String ws) {
		this.ws = ws;
	}

	public static ExportConfiguration getDefault() {
		ExportConfiguration exportConfiguration = new ExportConfiguration();
		exportConfiguration.setArch(Platform.getOSArch());
		exportConfiguration.setOs(Platform.getOS());
		exportConfiguration.setWs(Platform.getWS());
		return exportConfiguration;
	}
}
