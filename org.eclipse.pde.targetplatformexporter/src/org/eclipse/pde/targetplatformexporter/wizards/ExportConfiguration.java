/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Boris Brodski - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.targetplatformexporter.wizards;

import org.eclipse.core.runtime.Platform;

/**
 *
 * @author Boris Brodski
 */
public class ExportConfiguration {
	String os = "";
	String ws = "";
	String arch = "";
	
	public ExportConfiguration() {
	}
	
	/**
	 * Reverse {@link #toString()} method
	 * @param string string representation
	 * @return <code>null</code> - invalid format
	 */
	public static ExportConfiguration fromString(String string) {
		String[] parts = string.split("/");
		if (parts.length != 3) {
			return null;
		}
		ExportConfiguration exportConfiguration = new ExportConfiguration();
		exportConfiguration.os = parts[0];
		exportConfiguration.ws = parts[1];
		exportConfiguration.arch = parts[2];
		return exportConfiguration;
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
	
	@Override
	public String toString() {
		return os + "/" + ws + "/" + arch;
	}
}
