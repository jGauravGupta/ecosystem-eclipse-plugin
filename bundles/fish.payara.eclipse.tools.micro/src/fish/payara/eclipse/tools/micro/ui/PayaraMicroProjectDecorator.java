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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.plugin.AbstractUIPlugin;

public class PayaraMicroProjectDecorator extends LabelProvider implements ILabelDecorator {

	private static final String PLUGIN_ARTIFACT_ID = "<artifactId>payara-micro-maven-plugin</artifactId>"; //$NON-NLS-1$
	private static final String POM_XML = "pom.xml"; //$NON-NLS-1$
	private static final String ICON_PATH = "icons/payara-micro.png"; //$NON-NLS-1$

	private final Image image;
	private final Map<IProject, CacheEntry> cache = new ConcurrentHashMap<>();

	public PayaraMicroProjectDecorator() {
		ImageDescriptor imageDescriptor = AbstractUIPlugin.imageDescriptorFromPlugin(
				"fish.payara.eclipse.tools.micro", ICON_PATH); //$NON-NLS-1$
		image = imageDescriptor != null ? imageDescriptor.createImage() : null;
	}

	@Override
	public Image decorateImage(Image baseImage, Object element) {
		return matches(element) ? image : null;
	}

	@Override
	public String decorateText(String text, Object element) {
		return null;
	}

	@Override
	public void dispose() {
		cache.clear();
		if (image != null && !image.isDisposed()) {
			image.dispose();
		}
		super.dispose();
	}

	private boolean matches(Object element) {
		if (!(element instanceof IProject project)) {
			return false;
		}

		IFile pomFile = project.getFile(POM_XML);
		if (!pomFile.exists()) {
			cache.remove(project);
			return false;
		}

		long stamp = pomFile.getModificationStamp();
		CacheEntry cached = cache.get(project);
		if (cached != null && cached.modificationStamp == stamp) {
			return cached.matches;
		}

		boolean matches = containsPlugin(pomFile);
		cache.put(project, new CacheEntry(stamp, matches));
		return matches;
	}

	private boolean containsPlugin(IFile pomFile) {
		try (InputStream inputStream = pomFile.getContents()) {
			return new String(inputStream.readAllBytes(), UTF_8).contains(PLUGIN_ARTIFACT_ID);
		} catch (IOException | RuntimeException | org.eclipse.core.runtime.CoreException ex) {
			return false;
		}
	}

	private static final class CacheEntry {

		private final long modificationStamp;
		private final boolean matches;

		private CacheEntry(long modificationStamp, boolean matches) {
			this.modificationStamp = modificationStamp;
			this.matches = matches;
		}
	}
}
