/**
 * Copyright (c) 2020-2024 Payara Foundation
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 */
package fish.payara.eclipse.tools.micro;

import static fish.payara.eclipse.tools.micro.MicroConstants.JAVA_HOME_ENV_VAR;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;

public final class JavaEnvironmentProperty {

	private JavaEnvironmentProperty() {
	}

	public static Map<String, String> withProjectJavaHome(Map<String, String> environment, IProject project)
			throws CoreException {
		Map<String, String> updatedEnvironment = new HashMap<>(environment);
		updatedEnvironment.put(JAVA_HOME_ENV_VAR, getJavaHome(project));
		return updatedEnvironment;
	}

	public static String getJavaHome(IProject project) throws CoreException {
		IJavaProject javaProject = JavaCore.create(project);
		IVMInstall install = JavaRuntime.getVMInstall(javaProject);
		if (install == null) {
			install = JavaRuntime.getDefaultVMInstall();
		}
		if (install == null || install.getInstallLocation() == null) {
			throw new CoreException(new Status(IStatus.ERROR, MicroConstants.PLUGIN_ID,
					"Unable to resolve a JDK for project " + project.getName()));
		}
		return install.getInstallLocation().getAbsolutePath();
	}
}
