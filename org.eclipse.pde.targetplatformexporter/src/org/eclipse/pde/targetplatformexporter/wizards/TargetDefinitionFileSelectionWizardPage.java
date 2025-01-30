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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ComboBoxViewerCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.eclipse.pde.internal.ui.editor.feature.Choice;
import org.eclipse.pde.internal.ui.editor.site.PortabilitySection;
import org.eclipse.pde.internal.ui.util.FileExtensionFilter;
import org.eclipse.pde.internal.ui.util.FileValidator;
import org.eclipse.pde.internal.ui.util.SWTUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;

/**
 * 
 *
 * @author Boris Brodski
 */
@SuppressWarnings("restriction")
public class TargetDefinitionFileSelectionWizardPage extends WizardPage {

	private abstract class StringEditingSupport extends EditingSupport {
		private ColumnLabelProvider columnLabelProvider = new ColumnLabelProvider() {
			public String getText(Object element) {
				return getStringValue((ExportConfiguration)element);
			};
		};
		private String[] items;
		private ComboBoxViewerCellEditor editor;

		public StringEditingSupport(Choice[] choices) {
			super(configurationTable);
			List<String> valueList = new ArrayList<>();
			for (Choice choice : choices) {
				valueList.add(choice.getValue());
			}
			this.items = valueList.toArray(new String[0]);
		}

		@Override
		protected void setValue(Object element, Object value) {
			if (value == null) {
				value = ((CCombo)editor.getControl()).getText();
			}
			if (value instanceof String) {
				setStringValue((ExportConfiguration)element, (String) value);
				configurationTable.refresh(element);
				controlChanged();
			}
		}
		
		@Override
		protected Object getValue(Object element) {
			return columnLabelProvider.getText(element);
		}
		
		@Override
		protected CellEditor getCellEditor(Object element) {
			editor = new ComboBoxViewerCellEditor((Composite)configurationTable.getControl(), SWT.BORDER);
			editor.setContentProvider(new ArrayContentProvider());
			editor.setLabelProvider(new LabelProvider());
			editor.setInput(items);
			return editor;
		}
		
		@Override
		protected boolean canEdit(Object element) {
			return true;
		}
		CellLabelProvider getLabelProvider() {
			return columnLabelProvider;
		}
		abstract void setStringValue(ExportConfiguration configuration, String value);
		abstract String getStringValue(ExportConfiguration configuration);
	}

	private static final String SETTINGS_P2_MIRROR = "p2.mirror";
	private static final String SETTINGS_REPO_LOCATION = "repo.location";
	private static final String SETTINGS_CONFIG_TABLE = "config.table";

	private Text targetDefinitionsText;
	private Object[] targetSelection;
	private TableViewer configurationTable;
	private ArrayList<ExportConfiguration> input;
	private Text repositoryLocationText;
	private Button p2MirrorCheckbox;

	protected TargetDefinitionFileSelectionWizardPage() {
		super("Select targets");
	}

	@Override
	public void createControl(Composite parent) {
		createTargetPlatformSelection(parent);
		initTargetDefinitionsFromSelection();
	}

	private Control createTargetPlatformSelection(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout(3, false));
		composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Label label = new Label(composite, SWT.NONE);
		label.setText("Target definitions:");

		targetDefinitionsText = new Text(composite, SWT.BORDER | SWT.READ_ONLY);
		targetDefinitionsText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Button browse = new Button(composite, SWT.PUSH);
		browse.setText("Browse");
		browse.setLayoutData(new GridData());
		browse.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				handleTargetBrowse();
			}
		});
		SWTUtil.setButtonDimensionHint(browse);
		
		configurationTable = new TableViewer(composite, SWT.FULL_SELECTION | SWT.BORDER);
		createColumns();
	    final Table table = configurationTable.getTable();
	    table.setHeaderVisible(true);
	    table.setLinesVisible(true);
	    GridData layoutData = new GridData(SWT.FILL, SWT.FILL, true, true);
	    layoutData.horizontalSpan = 2;
	    layoutData.verticalSpan = 2;
	    table.setLayoutData(layoutData);

		configurationTable.setContentProvider(new ArrayContentProvider());

		
		input = new ArrayList<>();
		input.add(ExportConfiguration.getDefault());
		configurationTable.setInput(input);

		Button addButton = new Button(composite, SWT.PUSH);
		addButton.setText("Add");
		addButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
		SWTUtil.setButtonDimensionHint(addButton);
		addButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ExportConfiguration exportConfiguration = ExportConfiguration.getDefault();
				input.add(exportConfiguration);
				configurationTable.add(exportConfiguration);
				controlChanged();
			}
		});

		Button removeButton = new Button(composite, SWT.PUSH);
		removeButton.setText("Remove");
		removeButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, true));
		SWTUtil.setButtonDimensionHint(removeButton);
		removeButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ISelection selection = configurationTable.getSelection();
				if (selection instanceof IStructuredSelection) {
					Object element = ((IStructuredSelection) selection).getFirstElement();
					if (element != null) {
						input.remove(element);
						configurationTable.remove(element);
						controlChanged();
					}
				}
			}
		});

		label = new Label(composite, SWT.NONE);
		label.setText("p2 repository:");

		repositoryLocationText = new Text(composite, SWT.BORDER);
		repositoryLocationText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		repositoryLocationText.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				controlChanged();
			}
		});

		Button repoBrowse = new Button(composite, SWT.PUSH);
		repoBrowse.setText("Browse");
		repoBrowse.setLayoutData(new GridData());
		repoBrowse.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				handleRepoBrowse();
			}
		});
		SWTUtil.setButtonDimensionHint(repoBrowse);
		
		p2MirrorCheckbox = new Button(composite, SWT.CHECK);
		p2MirrorCheckbox.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 3, 0));
		p2MirrorCheckbox.setText("Create p2 mirror repository instead of exporting plugins");
		p2MirrorCheckbox.setSelection(true);

		setControl(composite);
		
		return composite;
	}

	private void loadState() {
		String selectionBasedSection = getTargetFilesAsString();
		IDialogSettings section = getWizard().getDialogSettings().getSection(selectionBasedSection);
		if (section != null) {
			p2MirrorCheckbox.setSelection(section.getBoolean(SETTINGS_P2_MIRROR));
			repositoryLocationText.setText(section.get(SETTINGS_REPO_LOCATION));
			String[] parts = section.getArray(SETTINGS_CONFIG_TABLE);
			input.clear();
			for (String string : parts) {
				ExportConfiguration configuration = ExportConfiguration.fromString(string);
				if (configuration != null) {
					input.add(configuration);
				}
			}
			configurationTable.refresh();
			controlChanged();
		}
	}
	
	public void saveState() {
		String selectionBasedSection = getTargetFilesAsString();
		IDialogSettings section = getWizard().getDialogSettings().getSection(selectionBasedSection);
		if (section == null) {
			section = getWizard().getDialogSettings().addNewSection(selectionBasedSection);
		}
		
		section.put(SETTINGS_REPO_LOCATION, repositoryLocationText.getText());
		section.put(SETTINGS_P2_MIRROR, p2MirrorCheckbox.getSelection());
		
		String [] lines = new String[input.size()];
		for (int i = 0; i < lines.length; i++) {
			lines[i] = input.get(i).toString();
		}
		section.put(SETTINGS_CONFIG_TABLE, lines);
	}

	private void createColumns() {
		createColumn("OS", new StringEditingSupport(PortabilitySection.getOSChoices()) {
			void setStringValue(ExportConfiguration configuration, String value) {
				configuration.setOs(value);
			}
			String getStringValue(ExportConfiguration configuration) {
				return configuration.getOs();
			}
			
		});

		createColumn("WS", new StringEditingSupport(PortabilitySection.getWSChoices()) {
			void setStringValue(ExportConfiguration configuration, String value) {
				configuration.setWs(value);
			}
			String getStringValue(ExportConfiguration configuration) {
				return configuration.getWs();
			}
		});

		createColumn("Arch", new StringEditingSupport(PortabilitySection.getArchChoices()) {
			void setStringValue(ExportConfiguration configuration, String value) {
				configuration.setArch(value);
			}
			String getStringValue(ExportConfiguration configuration) {
				return configuration.getArch();
			}
		});
		
	}
	
	private void createColumn(String text, StringEditingSupport editingSupport) {
		TableViewerColumn column1 = new TableViewerColumn(configurationTable, SWT.NONE);
		column1.getColumn().setText(text);
		column1.getColumn().setWidth(110);
		column1.setLabelProvider(editingSupport.getLabelProvider());
		column1.setEditingSupport(editingSupport);
		controlChanged();
	}
	private void handleRepoBrowse() {
		DirectoryDialog dialog = new DirectoryDialog(getShell());
		dialog.setText(PDEUIMessages.ExportTargetSelectDestination);
		dialog.setMessage(PDEUIMessages.ExportTargetSpecifyDestination);
		String dir = repositoryLocationText.getText();
		dialog.setFilterPath(dir);
		dir = dialog.open();
		if (dir == null || dir.equals("")) { //$NON-NLS-1$
			return;
		}
		repositoryLocationText.setText(dir);
		controlChanged();
	}

	private void controlChanged() {
		setPageComplete(validate());
	}

	private boolean validate() {
		setMessage(null);

		if (targetDefinitionsText.getText().trim().equals("")) { //$NON-NLS-1$
			setErrorMessage("Select one or more target platform definitions");
			return false;
		} 

		if (!isValidLocation(repositoryLocationText.getText().trim())) {
			setErrorMessage("Select valid p2 directory");
			return false;
		}

		for (ExportConfiguration configuration : input) {
			if (configuration.getArch().trim().length() == 0 ) {
				setErrorMessage("Fill missing 'arch' configuration");
				return false;
			}
			if (configuration.getOs().trim().length() == 0) {
				setErrorMessage("Fill missing 'OS' configuration");
				return false;
			}
			if (configuration.getWs().trim().length() == 0) {
				setErrorMessage("Fill missing 'WS' configuration");
				return false;
			}
		}
		if (input.size() == 0) {
			setErrorMessage("Add at least one configuration line");
			return false;
		}
		setErrorMessage(null);

		return true;
	}
	private boolean isValidLocation(String location) {
		if (location.trim().length() == 0) {
			return false;
		}
		try {
			String destinationPath = new File(location).getCanonicalPath();
			if (destinationPath == null || destinationPath.length() == 0)
				return false;
		} catch (IOException e) {
			return false;
		}

		return true;
	}

	private void handleTargetBrowse() {
		ElementTreeSelectionDialog dialog = new ElementTreeSelectionDialog(
				getShell(), new WorkbenchLabelProvider(),
				new WorkbenchContentProvider());

		dialog.setValidator(new FileValidator());
		dialog.setAllowMultiple(false);
		dialog.setTitle(PDEUIMessages.ProductExportWizardPage_fileSelection);
		dialog.setMessage(PDEUIMessages.ProductExportWizardPage_productSelection);
		dialog.addFilter(new FileExtensionFilter("target")); //$NON-NLS-1$
		dialog.setInput(PDEPlugin.getWorkspace().getRoot());
		dialog.setAllowMultiple(true);
		if (targetSelection != null) {
			dialog.setInitialSelections(targetSelection);
		}
		dialog.create();
		if (dialog.open() == Window.OK) {
			Object[] results = dialog.getResult();
			if (results != null) {
				updateSelectedDefinitionsTargets(results);
			}
		}
	}

	private void initTargetDefinitionsFromSelection() {
		List<IFile> result = new ArrayList<IFile>();
		IWorkbenchWindow activeWorkbenchWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (activeWorkbenchWindow != null) {
			ISelection selection = activeWorkbenchWindow.getSelectionService().getSelection();
			if (selection instanceof IStructuredSelection) {
				for (Object object : ((IStructuredSelection) selection).toList()) {
					if (object instanceof IFile && ((IFile) object).getName().endsWith(".target")) {
						result.add((IFile) object);
					}
				}
			}
		}
		updateSelectedDefinitionsTargets(result.toArray());
	}
	
	private void updateSelectedDefinitionsTargets(Object[] results) {
		targetSelection = results;
		if (results.length == 0) {
			targetDefinitionsText.setText("");
		} else if (results.length == 1) {
			targetDefinitionsText.setText(((IFile)results[0]).getFullPath().toString());
		} else {
			targetDefinitionsText.setText("" + results.length + " files selected");
		}
		loadState();
		controlChanged();
	}

	public List<IFile> getTargetFiles() {
		List<IFile> result = new ArrayList<>();
		for (Object file : targetSelection) {
			result.add((IFile)file);
		}
		return result;
	}

	private String getTargetFilesAsString() {
		StringBuilder stringBuilder = new StringBuilder();
		for (IFile file : getTargetFiles()) {
			stringBuilder.append(file.getFullPath().toPortableString());
			stringBuilder.append(File.pathSeparatorChar);
		}
		return stringBuilder.toString();
	}
	
	public List<ExportConfiguration> getConfigurations() {
		List<ExportConfiguration> result = new ArrayList<>();
		for (ExportConfiguration exportConfiguration : input) {
			result.add(exportConfiguration);
		}
		return result;
	}

	public String getRepoPath() {
		return repositoryLocationText.getText();
	}

	public boolean getP2Mirror() {
		return p2MirrorCheckbox.getSelection();
	}


}
