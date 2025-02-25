/*******************************************************************************
 * Copyright (c) 2009, 2010 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Cloudsmith Inc - ongoing implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.repository.helpers;

import java.io.File;
import java.net.*;
import java.util.Objects;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.internal.p2.repository.Activator;
import org.eclipse.equinox.p2.repository.IRepository;
import org.eclipse.osgi.util.NLS;

public class RepositoryHelper {
	protected static final String FILE_SCHEME = "file"; //$NON-NLS-1$

	/**
	 * If the provided URI can be interpreted as representing a local address (no schema, or one letter schema) 
	 * but is missing the file schema, a new URI is created which represents the local file. 
	 * 
	 * @param location the URI to convert
	 * @return the converted URI, or the original
	 */
	public static URI localRepoURIHelper(URI location) {
		if (location == null)
			return null;
		if (location.getScheme() == null)			// Probably a local path:  /home/user/repo

			location = (new File(location.getPath())).getAbsoluteFile().toURI();
		else if (location.getScheme().length() == 1)
			// Probably a windows path:  C:\repo
			location = (new File(URIUtil.toUnencodedString(location))).toURI();
		else if (!FILE_SCHEME.equalsIgnoreCase(location.getScheme()))
			// This else must occur last!
			return location;

		// Zipped repository?
		String lowerCase = location.toString().toLowerCase();
		if (lowerCase.endsWith(".jar") || lowerCase.endsWith(".zip")) //$NON-NLS-1$//$NON-NLS-2$
			return URIUtil.toJarURI(location, null);
		return location;
	}

	/**
		 * Determine if the repository could be used as a valid destination (eg, it is modifiable)
 * @param repository the repository to test
	 * @return the repository
	 */
	public static <T> IRepository<T> validDestinationRepository(IRepository<T> repository) {
		if (!repository.isModifiable())
			throw new IllegalStateException(NLS.bind(Messages.DestinationNotModifiable, repository.getLocation()));
		return repository;
	}

	/**
	 * Determine if the location is a syntactically correct repository location.
	 * Intended to be used from the UI when checking validity of user input.
	 *
	 * @throws IllegalArgumentException if location is null
	 */
	public static IStatus checkRepositoryLocationSyntax(URI location) {
		if (location == null)
			throw new IllegalArgumentException("Location cannot be null"); //$NON-NLS-1$
		if (!location.isAbsolute())
			return new Status(IStatus.ERROR, Activator.ID, Messages.locationMustBeAbsolute);
		String scheme = location.getScheme();
		if (scheme == null) {
			return Status.error(Messages.schemeNotProvided);
		}
		try {
			new URL(scheme, "dummy.com", -1, "dummy.txt"); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (MalformedURLException e) {
			// check if the scheme is probably provided to ECF but not yet loaded
			IExtensionRegistry reg = RegistryFactory.getRegistry();
			if (reg != null) {
				IExtensionPoint handlersExtensionPoint = reg
						.getExtensionPoint("org.eclipse.ecf.filetransfer.urlStreamHandlerService"); //$NON-NLS-1$
				IConfigurationElement[] configurationElements = handlersExtensionPoint.getConfigurationElements();
				for (IConfigurationElement configurationElement : configurationElements) {
					String protocol = configurationElement.getAttribute("protocol"); //$NON-NLS-1$
					if (Objects.equals(scheme, protocol)) {
						return Status.OK_STATUS;
					}
				}
			}
			return new Status(IStatus.ERROR, Activator.ID, Messages.schemeNotSupported, e);
		}
		return Status.OK_STATUS;
	}
}