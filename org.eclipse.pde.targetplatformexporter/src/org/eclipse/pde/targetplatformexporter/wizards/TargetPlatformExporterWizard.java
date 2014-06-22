package org.eclipse.pde.targetplatformexporter.wizards;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.pde.core.target.ITargetDefinition;
import org.eclipse.pde.core.target.ITargetHandle;
import org.eclipse.pde.core.target.ITargetPlatformService;
import org.eclipse.pde.internal.core.PDECore;
import org.eclipse.pde.internal.core.target.ExportTargetJob;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.PDEPluginImages;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IExportWizard;
import org.eclipse.ui.IWorkbench;

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

		ProgressMonitorDialog dialog = new ProgressMonitorDialog(getShell()) {
			protected void configureShell(Shell shell) {
				super.configureShell(shell);
				shell.setText("Resolving and exporting target platform(s)");
			}
		};

		try {
			dialog.run(true, true, new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
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
								
								// Resolve the target
								targetDefinition.resolve(monitor);
								
								if (monitor.isCanceled()) {
									throw new InterruptedException();
								}
								Job job = new ExportTargetJob(targetDefinition, new File(repoPath).toURI(), false);
								job.schedule();
								job.join();
							} catch (CoreException e) {
								e.printStackTrace();
								throw new InterruptedException("Error");
							}
						}
					}
				}
			});
		} catch (InvocationTargetException | InterruptedException e) {
			e.printStackTrace();
			return false;
		}
//			
//			
//			
//			try {
//				dialog.run(true, true, new IRunnableWithProgress() {
//					public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
//						if (monitor.isCanceled()) {
//							throw new InterruptedException();
//						}
//						// Resolve the target
//						targetDefinition.resolve(monitor);
//						if (monitor.isCanceled()) {
//							throw new InterruptedException();
//						}
//						Job job = new ExportTargetJob(targetDefinition, new File("/home/boris/tmp/export1").toURI(), true);
//						job.schedule(200);
//					}
//				});
//			} catch (Exception e) {
//				e.printStackTrace();
//			}


		return true;
	}

}
