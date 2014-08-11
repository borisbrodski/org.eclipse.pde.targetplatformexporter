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

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.pde.core.target.ITargetDefinition;
import org.eclipse.pde.core.target.ITargetHandle;
import org.eclipse.pde.core.target.ITargetLocation;
import org.eclipse.pde.core.target.ITargetPlatformService;
import org.eclipse.pde.internal.core.PDECore;
import org.eclipse.pde.internal.core.target.ExportTargetJob;
import org.eclipse.pde.internal.core.target.IUBundleContainer;
import org.eclipse.pde.internal.core.target.P2TargetUtils;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.PDEPluginImages;
import org.eclipse.pde.targetplatformexporter.MirrorException;
import org.eclipse.pde.targetplatformexporter.P2MirrorTool;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IExportWizard;
import org.eclipse.ui.IWorkbench;

/**
 *
 *
 * @author Boris Brodski
 */
@SuppressWarnings("restriction")
public class TargetPlatformExporterWizard extends Wizard implements
		IExportWizard {

	private static final String SETTINGS_SECTION = "targetplatformexporter";
	private TargetDefinitionFileSelectionWizardPage targetDefinitionFileSelectionWizardPage;

	public TargetPlatformExporterWizard() {
		setNeedsProgressMonitor(true);
		setWindowTitle("Export platform target definition to a p2 repository");
		setDefaultPageImageDescriptor(PDEPluginImages.DESC_TARGET_WIZ);
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {

	}

	@Override
	public void addPages() {
		IDialogSettings settings = PDEPlugin.getDefault().getDialogSettings().getSection(SETTINGS_SECTION);
		if (settings == null) {
			settings = PDEPlugin.getDefault().getDialogSettings().addNewSection(SETTINGS_SECTION);
		}
		setDialogSettings(settings);

		targetDefinitionFileSelectionWizardPage = new TargetDefinitionFileSelectionWizardPage();
		addPage(targetDefinitionFileSelectionWizardPage);
	}

	@Override
	public boolean performFinish() {
		final String repoPath = targetDefinitionFileSelectionWizardPage.getRepoPath();
		final boolean p2Mirror= targetDefinitionFileSelectionWizardPage.getP2Mirror();
		final AtomicReference<MultiStatus> multiStatusReference = new AtomicReference<>();

		final Set<URI> repoURIs = new HashSet<>();
		final Set<IInstallableUnit> installableUnitSet = new HashSet<>();

		ProgressMonitorDialog dialog = new ProgressMonitorDialog(getShell()) {
			protected void configureShell(Shell shell) {
				super.configureShell(shell);
				shell.setText("Resolving and exporting target platform(s)");
			}
		};

		try {
			dialog.run(true, true, new IRunnableWithProgress() {
				@SuppressWarnings("unchecked")
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					int combinations = targetDefinitionFileSelectionWizardPage.getTargetFiles().size() * targetDefinitionFileSelectionWizardPage.getConfigurations().size();
					SubMonitor subMonitor = SubMonitor.convert(monitor, 2 * combinations);
					try {
						subMonitor.setTaskName("Resolving and exporting target platform(s)");

						ITargetPlatformService service = (ITargetPlatformService) PDECore.getDefault().acquireService(ITargetPlatformService.class.getName());
						for (IFile file : targetDefinitionFileSelectionWizardPage.getTargetFiles()) {
							for (ExportConfiguration config : targetDefinitionFileSelectionWizardPage.getConfigurations()) {
								try {
									ITargetHandle fileHandle = service.getTarget(file);
									ITargetDefinition targetDefinition;
										targetDefinition = fileHandle.getTargetDefinition();
									targetDefinition.setArch(config.getArch());
									targetDefinition.setWS(config.getWs());
									targetDefinition.setOS(config.getOs());

									P2TargetUtils.deleteProfile(fileHandle);

									// Resolve the target
									targetDefinition.resolve(subMonitor.newChild(1));

									//TODO Check targetDefinition.getStatus()
									if (monitor.isCanceled()) {
										throw new InterruptedException();
									}
									if (p2Mirror) {
										IQueryResult<?> iUs = P2TargetUtils.getIUs(targetDefinition, subMonitor.newChild(1));
										installableUnitSet.addAll((Set<IInstallableUnit>)iUs.toSet());
										ITargetLocation[] targetLocations = targetDefinition.getTargetLocations();
										for (ITargetLocation targetLocation : targetLocations) {
											IUBundleContainer bundleContainer = (IUBundleContainer) targetLocation;
											for (URI uri : bundleContainer.getRepositories()) {
												repoURIs.add(uri);
											}
										}
									} else {
										subMonitor.subTask("Exporting target configuration");
										Job job = new ExportTargetJob(targetDefinition, new File(repoPath).toURI(), false);
										job.schedule();
										job.join();
										subMonitor.worked(1);
									}
								} catch (CoreException e) {
									e.printStackTrace();
									throw new InterruptedException("Error");
								}
							}
						}

					} finally {
						monitor.done();
					}
					if (p2Mirror) {
						P2MirrorTool p2MirrorTool = new P2MirrorTool(repoURIs, installableUnitSet, repoPath);
						try {
							MultiStatus status = p2MirrorTool.mirror(monitor);
							multiStatusReference.set(status);
						} catch (MirrorException e) {
							throw new InvocationTargetException(e);
						}
					}
				}
			});


			if (multiStatusReference.get() != null) {
				StringBuilder sb = new StringBuilder();
				getMessage(multiStatusReference.get(), sb);
				if (sb.length() > 0) {
					MessageDialog.openError(getShell(), "Error", sb.toString());
					return false;
				}

			}
			MessageDialog.openInformation(getShell(), "Mirror p2 repository", "Operation successful");
			return true;
		} catch (InvocationTargetException e) {
			if (e.getCause() instanceof MirrorException) {
				MessageDialog.openError(getShell(), "Error", e.getCause().getMessage());
			} else {
				e.printStackTrace();
			}
		} catch (InterruptedException e) {
		}
		return false;
	}

	private void getMessage(IStatus multiStatus, StringBuilder builder) {
		if (multiStatus.getSeverity() > IStatus.INFO) {
			builder.append(multiStatus.getMessage());
			builder.append(System.getProperty("line.separator"));
		}
		if (multiStatus.isMultiStatus()) {
			for(IStatus ms : multiStatus.getChildren()) {
				getMessage(ms, builder);
			}

		}
	}
}
