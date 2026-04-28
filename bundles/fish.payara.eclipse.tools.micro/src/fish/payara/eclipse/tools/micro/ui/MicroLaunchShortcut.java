/**
 * Copyright (c) 2020-2024 Payara Foundation
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 */
package fish.payara.eclipse.tools.micro.ui;

import static fish.payara.eclipse.tools.micro.MicroConstants.ATTR_DEBUG_PORT;
import static fish.payara.eclipse.tools.micro.MicroConstants.DEFAULT_DEBUG_PORT;
import static fish.payara.eclipse.tools.micro.MicroConstants.LAUNCH_CONFIG_TYPE;
import static fish.payara.eclipse.tools.micro.MicroConstants.PLUGIN_ID;
import static org.eclipse.core.externaltools.internal.IExternalToolConstants.ATTR_BUILD_SCOPE;
import static org.eclipse.core.externaltools.internal.IExternalToolConstants.ATTR_LOCATION;
import static org.eclipse.core.externaltools.internal.IExternalToolConstants.ATTR_TOOL_ARGUMENTS;
import static org.eclipse.core.externaltools.internal.IExternalToolConstants.ATTR_WORKING_DIRECTORY;
import static org.eclipse.debug.core.ILaunchManager.ATTR_ENVIRONMENT_VARIABLES;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.ILaunchShortcut2;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;

import fish.payara.eclipse.tools.micro.BuildTool;
import fish.payara.eclipse.tools.micro.JavaEnvironmentProperty;

public class MicroLaunchShortcut implements ILaunchShortcut2 {

	private static final Logger LOG = Logger.getLogger(MicroLaunchShortcut.class.getName());

	@Override
	public void launch(ISelection selection, String mode) {
		if (selection instanceof IStructuredSelection) {
			Object element = ((IStructuredSelection) selection).getFirstElement();
			IProject project = toProject(element);
			if (project != null) {
				launch(project, mode);
			}
		}
	}

	@Override
	public void launch(IEditorPart editor, String mode) {
		IEditorInput input = editor.getEditorInput();
		IProject project = toProject(input);
		if (project != null) {
			launch(project, mode);
		}
	}

	@Override
	public ILaunchConfiguration[] getLaunchConfigurations(ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			Object element = ((IStructuredSelection) selection).getFirstElement();
			IProject project = toProject(element);
			if (project != null) {
				return findLaunchConfigurations(project);
			}
		}
		return null;
	}

	@Override
	public ILaunchConfiguration[] getLaunchConfigurations(IEditorPart editor) {
		IProject project = toProject(editor.getEditorInput());
		if (project != null) {
			return findLaunchConfigurations(project);
		}
		return null;
	}

	@Override
	public IResource getLaunchableResource(ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			Object element = ((IStructuredSelection) selection).getFirstElement();
			IProject project = toProject(element);
			if (project != null) {
				return project;
			}
		}
		return null;
	}

	@Override
	public IResource getLaunchableResource(IEditorPart editor) {
		return toProject(editor.getEditorInput());
	}

	private void launch(IProject project, String mode) {
		try {
			ILaunchConfiguration[] existing = findLaunchConfigurations(project);
			ILaunchConfiguration config;
			if (existing.length >= 1) {
				config = existing[0];
			} else {
				config = createLaunchConfiguration(project);
			}
			DebugUITools.launch(config, mode);
		} catch (CoreException e) {
			LOG.log(Level.SEVERE, "Failed to launch Payara Micro for project " + project.getName(), e);
		}
	}

	private ILaunchConfiguration[] findLaunchConfigurations(IProject project) {
		try {
			ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
			ILaunchConfigurationType type = manager.getLaunchConfigurationType(LAUNCH_CONFIG_TYPE);
			ILaunchConfiguration[] configs = manager.getLaunchConfigurations(type);
			List<ILaunchConfiguration> matching = new ArrayList<>();
			for (ILaunchConfiguration config : configs) {
				String name = config.getAttribute(ATTR_PROJECT_NAME, "");
				if (project.getName().equals(name)) {
					matching.add(config);
				}
			}
			return matching.toArray(new ILaunchConfiguration[0]);
		} catch (CoreException e) {
			LOG.log(Level.WARNING, "Failed to find Payara Micro launch configurations", e);
			return new ILaunchConfiguration[0];
		}
	}

	private ILaunchConfiguration createLaunchConfiguration(IProject project) throws CoreException {
		BuildTool buildTool = BuildTool.getToolSupport(project);
		String executableHome;
		try {
			executableHome = buildTool.getExecutableHome();
		} catch (Exception e) {
			throw new CoreException(new Status(IStatus.ERROR, PLUGIN_ID,
					"Cannot find build tool executable for project " + project.getName(), e));
		}
		String debugPort = String.valueOf(DEFAULT_DEBUG_PORT);
		List<String> startCmd = buildTool.getStartCommand(null, null, null, debugPort, false);

		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType type = manager.getLaunchConfigurationType(LAUNCH_CONFIG_TYPE);
		String configName = manager.generateLaunchConfigurationName(project.getName());
		ILaunchConfigurationWorkingCopy wc = type.newInstance(null, configName);
		wc.setAttribute(ATTR_PROJECT_NAME, project.getName());
		wc.setAttribute(ATTR_WORKING_DIRECTORY, project.getLocation().toOSString());
		wc.setAttribute(ATTR_BUILD_SCOPE, "${projects:" + project.getName() + "}");
		wc.setAttribute(ATTR_LOCATION, executableHome);
		wc.setAttribute(ATTR_TOOL_ARGUMENTS, String.join(" ", startCmd));
		wc.setAttribute(ATTR_DEBUG_PORT, debugPort);
		wc.setAttribute(DebugPlugin.ATTR_PROCESS_FACTORY_ID, "fish.payara.eclipse.tools.micro.processFactory");
		wc.setAttribute(ATTR_ENVIRONMENT_VARIABLES,
				JavaEnvironmentProperty.withProjectJavaHome(java.util.Map.of(), project));
		return wc.doSave();
	}

	private IProject toProject(Object element) {
		if (element instanceof IProject) {
			return (IProject) element;
		}
		if (element instanceof IAdaptable) {
			IResource resource = ((IAdaptable) element).getAdapter(IResource.class);
			if (resource != null) {
				return resource.getProject();
			}
			IJavaElement javaElement = ((IAdaptable) element).getAdapter(IJavaElement.class);
			if (javaElement != null) {
				return javaElement.getJavaProject().getProject();
			}
		}
		return null;
	}
}
