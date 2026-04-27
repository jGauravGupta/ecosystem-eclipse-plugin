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
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.environments.IExecutionEnvironment;
import org.eclipse.jdt.launching.environments.IExecutionEnvironmentsManager;

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
		IVMInstall install = getProjectVmInstall(javaProject);
		if (install == null) {
			install = JavaRuntime.getDefaultVMInstall();
		}
		if (install == null || install.getInstallLocation() == null) {
			throw new CoreException(new Status(IStatus.ERROR, MicroConstants.PLUGIN_ID,
					"Unable to resolve a JDK for project " + project.getName()));
		}
		return install.getInstallLocation().getAbsolutePath();
	}

	private static IVMInstall getProjectVmInstall(IJavaProject javaProject) throws CoreException {
		IVMInstall jreContainerInstall = getJreContainerVmInstall(javaProject);
		if (jreContainerInstall != null) {
			return jreContainerInstall;
		}
		return JavaRuntime.getVMInstall(javaProject);
	}

	private static IVMInstall getJreContainerVmInstall(IJavaProject javaProject) throws CoreException {
		for (IClasspathEntry classpathEntry : javaProject.getRawClasspath()) {
			if (classpathEntry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
				IPath containerPath = classpathEntry.getPath();
				if (containerPath != null && containerPath.segmentCount() > 0
						&& JavaRuntime.JRE_CONTAINER.equals(containerPath.segment(0))) {
					IVMInstall install = JavaRuntime.getVMInstall(containerPath);
					if (install != null) {
						return install;
					}
					install = getExecutionEnvironmentVmInstall(containerPath);
					if (install != null) {
						return install;
					}
				}
			}
		}
		return null;
	}

	private static IVMInstall getExecutionEnvironmentVmInstall(IPath containerPath) {
		if (containerPath == null || containerPath.segmentCount() < 2) {
			return null;
		}
		IExecutionEnvironmentsManager manager = JavaRuntime.getExecutionEnvironmentsManager();
		IExecutionEnvironment environment = manager.getEnvironment(containerPath.segment(1));
		if (environment == null) {
			return null;
		}
		IVMInstall install = environment.getDefaultVM();
		if (install != null) {
			return install;
		}
		IVMInstall[] compatibleVms = environment.getCompatibleVMs();
		if (compatibleVms.length > 0) {
			return compatibleVms[0];
		}
		return null;
	}
}
