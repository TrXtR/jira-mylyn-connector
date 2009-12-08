/*******************************************************************************
 * Copyright (c) 2009 Atlassian and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Atlassian - initial API and implementation
 ******************************************************************************/

package com.atlassian.connector.eclipse.team.ui;

import com.atlassian.connector.eclipse.team.ui.exceptions.UnsupportedTeamProviderException;
import com.atlassian.theplugin.commons.crucible.api.model.VersionedComment;
import com.atlassian.theplugin.commons.util.MiscUtil;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.CompareEditorInput;
import org.eclipse.compare.CompareUI;
import org.eclipse.compare.contentmergeviewer.ContentMergeViewer;
import org.eclipse.compare.contentmergeviewer.IMergeViewerContentProvider;
import org.eclipse.compare.contentmergeviewer.TextMergeViewer;
import org.eclipse.compare.internal.MergeSourceViewer;
import org.eclipse.compare.structuremergeviewer.ICompareInput;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.text.source.LineRange;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.mylyn.commons.core.StatusHandler;
import org.eclipse.mylyn.internal.provisional.commons.ui.WorkbenchUtil;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

/**
 * A utility class for doing UI related operations for team items
 * 
 * @author Shawn Minto
 */
@SuppressWarnings("restriction")
public final class TeamUiUtils {

	public static final String TEAM_PROVIDER_ID_CVS_ECLIPSE = "org.eclipse.team.cvs.core.cvsnature";

	public static final String TEAM_PROV_ID_SVN_SUBCLIPSE = "org.tigris.subversion.subclipse.core.svnnature";

	private static DefaultTeamUiResourceConnector defaultConnector = new DefaultTeamUiResourceConnector();

	private TeamUiUtils() {
	}

	public static DefaultTeamUiResourceConnector getDefaultConnector() {
		return defaultConnector;
	}

	@Nullable
	public static IEditorPart openFile(String repoUrl, String filePath, String otherRevisionFilePath,
			String revisionString, String otherRevisionString, IProgressMonitor monitor) {
		// TODO if the repo url is null, we should probably use the task repo host and look at all repos

		assert (filePath != null);
		assert (revisionString != null);
		if (!checkTeamConnectors()) {
			return null;
		}

		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}

		TeamUiResourceManager teamResourceManager = AtlassianTeamUiPlugin.getDefault().getTeamResourceManager();

		for (ITeamUiResourceConnector connector : teamResourceManager.getTeamConnectors()) {
			if (connector.isEnabled() && connector.canHandleFile(repoUrl, filePath, monitor)) {
				try {
					IEditorPart editor = connector.openFile(repoUrl, filePath, otherRevisionFilePath, revisionString,
							otherRevisionString, monitor);
					if (editor == null) {
						StatusHandler.log(new Status(IStatus.INFO, AtlassianTeamUiPlugin.PLUGIN_ID,
								"Requested file opened in external editor"));
					}
					return editor;
				} catch (CoreException e) {
					StatusHandler.log(e.getStatus());
					//ignore and try with default
				}
			}
		}

		// try a backup solution
		try {
			IEditorPart editor = defaultConnector.openFile(repoUrl, filePath, otherRevisionFilePath, revisionString,
					otherRevisionString, monitor);
			if (editor == null) {
				StatusHandler.log(new Status(IStatus.INFO, AtlassianTeamUiPlugin.PLUGIN_ID,
						"Requested file opened in external editor"));
			}
			return editor;
		} catch (UnsupportedTeamProviderException e) {
			TeamUiMessageUtils.openUnsupportedTeamProviderErrorMessage(e);
			return null;
		} catch (CoreException e) {
			TeamUiMessageUtils.openCouldNotOpenFileErrorMessage(repoUrl, filePath, revisionString);
			return null;
		}
	}

	public static Collection<String> getSupportedTeamConnectors() {
		Collection<String> res = MiscUtil.buildArrayList();
		TeamUiResourceManager teamResourceManager = AtlassianTeamUiPlugin.getDefault().getTeamResourceManager();
		for (ITeamUiResourceConnector connector : teamResourceManager.getTeamConnectors()) {
			if (connector.isEnabled()) {
				res.add(connector.getName());
			}
		}
		res.add(defaultConnector.getName());
		return res;
	}

	public static boolean hasNoTeamConnectors() {
		return AtlassianTeamUiPlugin.getDefault().getTeamResourceManager().getTeamConnectors().size() == 0;
	}

	public static boolean checkTeamConnectors() {
		if (hasNoTeamConnectors()) {
			handleMissingTeamConnectors();
			return false;
		}
		return true;
	}

	public static void openCompareEditor(String repoUrl, String filePath, String otherRevisionFilePath,
			String oldRevisionString, String newRevisionString, ICompareAnnotationModel annotationModel,
			IProgressMonitor monitor) {
		assert (filePath != null);
		assert (oldRevisionString != null);
		assert (newRevisionString != null);
		if (!checkTeamConnectors()) {
			return;
		}

		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}

		TeamUiResourceManager teamResourceManager = AtlassianTeamUiPlugin.getDefault().getTeamResourceManager();

		for (ITeamUiResourceConnector connector : teamResourceManager.getTeamConnectors()) {
			if (connector.isEnabled() && connector.canHandleFile(repoUrl, filePath, monitor)) {
				try {
					if (connector.openCompareEditor(repoUrl, filePath, otherRevisionFilePath, oldRevisionString,
							newRevisionString, annotationModel, monitor)) {
						return;
					}
				} catch (CoreException e) {
					StatusHandler.log(e.getStatus());
					//ignore and try with default
				}
			}
		}

		try {
			if (!defaultConnector.openCompareEditor(repoUrl, filePath, otherRevisionFilePath, oldRevisionString,
					newRevisionString, annotationModel, monitor)) {
				TeamUiMessageUtils.openUnableToCompareErrorMessage(repoUrl, filePath, oldRevisionString,
						newRevisionString, null);
			}
		} catch (UnsupportedTeamProviderException e) {
			TeamUiMessageUtils.openUnsupportedTeamProviderErrorMessage(e);
		} catch (CoreException e) {
			TeamUiMessageUtils.openUnableToCompareErrorMessage(repoUrl, filePath, oldRevisionString, newRevisionString,
					e);
		}
	}

	public static LineRange getSelectedLineNumberRangeFromEditorInput(IEditorPart editor, IEditorInput editorInput) {

		if (editor instanceof ITextEditor && editor.getEditorInput() == editorInput) {
			ISelection selection = ((ITextEditor) editor).getSelectionProvider().getSelection();
			if (selection instanceof TextSelection) {
				TextSelection textSelection = ((TextSelection) selection);
				return new LineRange(textSelection.getStartLine() + 1, textSelection.getEndLine()
						- textSelection.getStartLine());
			}
		}
		return null;
	}

	/**
	 * Opens editor for specified resource
	 * 
	 * @param resource
	 *            resource/file to open
	 * @return editor reference or null if external editor was open
	 * @throws CoreException
	 *             thrown in case there was a problem when opening editor
	 */
	public static IEditorPart openLocalResource(final IResource resource) throws CoreException {
		if (Display.getCurrent() != null) {
			return openLocalResourceInternal(resource);
		} else {
			final IEditorPart[] part = new IEditorPart[1];
			final CoreException[] exception = new CoreException[1];
			PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
				public void run() {
					try {
						part[0] = openLocalResourceInternal(resource);
					} catch (CoreException e) {
						exception[0] = e;
					}
				}
			});

			if (exception[0] != null) {
				throw exception[0];
			}
			return part[0];
		}
	}

	/**
	 * Opens editor for specified resource
	 * 
	 * @param resource
	 *            resource/file to open
	 * @return editor reference or null if external editor was open
	 * @throws CoreException
	 *             thrown in case there was a problem when opening editor
	 */
	private static IEditorPart openLocalResourceInternal(IResource resource) throws CoreException {
		// the local revision matches the revision we care about and the file is in sync
		try {
			return IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(),
					(IFile) resource, true);
		} catch (PartInitException e) {
			StatusHandler.log(new Status(IStatus.ERROR, AtlassianTeamUiPlugin.PLUGIN_ID, e.getMessage(), e));
			throw new CoreException(new Status(IStatus.ERROR, AtlassianTeamUiPlugin.PLUGIN_ID, NLS.bind(
					"Could not open editor for {0}.", resource.getName())));
		}
	}

	@Nullable
	public static ScmRepository getApplicableRepository(@NotNull IResource resource) {
		TeamUiResourceManager teamResourceManager = AtlassianTeamUiPlugin.getDefault().getTeamResourceManager();

		for (ITeamUiResourceConnector connector : teamResourceManager.getTeamConnectors()) {
			if (connector.isEnabled()) {
				try {
					ScmRepository res = connector.getApplicableRepository(resource);
					if (res != null) {
						return res;
					}
				} catch (CoreException e) {
					StatusHandler.log(new Status(IStatus.WARNING, AtlassianTeamUiPlugin.PLUGIN_ID, e.getMessage(), e));
					// and try the next connector
				}
			}
		}
		return null;

	}

	public static LocalStatus getLocalRevision(@NotNull IResource resource) throws CoreException {
		ITeamUiResourceConnector connector = AtlassianTeamUiPlugin.getDefault()
				.getTeamResourceManager()
				.getTeamConnector(resource);

		if (connector != null && connector.isEnabled()) {
			LocalStatus res = connector.getLocalRevision(resource);
			if (res != null) {
				return res;
			}
		}
		return null;
	}

	public static void selectAndReveal(final ITextEditor textEditor, int startLine, int endLine) {
		IDocumentProvider documentProvider = textEditor.getDocumentProvider();
		IEditorInput editorInput = textEditor.getEditorInput();
		if (documentProvider != null) {
			IDocument document = documentProvider.getDocument(editorInput);
			if (document != null) {
				try {
					final int offset = document.getLineOffset(startLine);
					final int length = document.getLineOffset(endLine) - offset;
					if (Display.getCurrent() != null) {
						internalSelectAndReveal(textEditor, offset, length);
					} else {
						PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
							public void run() {
								internalSelectAndReveal(textEditor, offset, length);
							}
						});
					}

				} catch (BadLocationException e) {
					StatusHandler.log(new Status(IStatus.ERROR, AtlassianTeamUiPlugin.PLUGIN_ID, e.getMessage(), e));
				}
			}
		}
	}

	public static void openCompareEditorForInput(final CompareEditorInput compareEditorInput) {
		if (Display.getCurrent() != null) {
			internalOpenCompareEditorForInput(compareEditorInput);
		} else {
			PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
				public void run() {
					internalOpenCompareEditorForInput(compareEditorInput);
				}
			});
		}
	}

	private static void internalOpenCompareEditorForInput(CompareEditorInput compareEditorInput) {
		IWorkbench workbench = AtlassianTeamUiPlugin.getDefault().getWorkbench();
		IWorkbenchPage page = workbench.getActiveWorkbenchWindow().getActivePage();
		CompareUI.openCompareEditorOnPage(compareEditorInput, page);
	}

	private static void internalSelectAndReveal(ITextEditor textEditor, final int offset, final int length) {
		textEditor.selectAndReveal(offset, length);
	}

	public static Viewer findContentViewer(Viewer contentViewer, ICompareInput input, Composite parent,
			ICompareAnnotationModel annotationModel) {

		// FIXME: hack
		if (contentViewer instanceof TextMergeViewer) {
			TextMergeViewer textMergeViewer = (TextMergeViewer) contentViewer;
			try {
				Class<TextMergeViewer> clazz = TextMergeViewer.class;
				Field declaredField = clazz.getDeclaredField("fLeft");
				declaredField.setAccessible(true);
				final MergeSourceViewer fLeft = (MergeSourceViewer) declaredField.get(textMergeViewer);

				declaredField = clazz.getDeclaredField("fRight");
				declaredField.setAccessible(true);
				final MergeSourceViewer fRight = (MergeSourceViewer) declaredField.get(textMergeViewer);

				annotationModel.attachToViewer(fLeft, fRight);
				annotationModel.focusOnComment();
				annotationModel.registerContextMenu();

				hackGalileo(contentViewer, textMergeViewer, fLeft, fRight);
			} catch (Throwable t) {
				StatusHandler.log(new Status(IStatus.WARNING, AtlassianTeamUiPlugin.PLUGIN_ID,
						"Could not initialize annotation model for " + input.getName(), t));
			}
		}
		return contentViewer;
	}

	private static void hackGalileo(Viewer contentViewer, TextMergeViewer textMergeViewer,
			final MergeSourceViewer fLeft, final MergeSourceViewer fRight) {
		// FIXME: hack for e3.5
		try {
			Method getCompareConfiguration = ContentMergeViewer.class.getDeclaredMethod("getCompareConfiguration");
			getCompareConfiguration.setAccessible(true);
			CompareConfiguration cc = (CompareConfiguration) getCompareConfiguration.invoke(textMergeViewer);

			Method getMergeContentProvider = ContentMergeViewer.class.getDeclaredMethod("getMergeContentProvider");
			getMergeContentProvider.setAccessible(true);
			IMergeViewerContentProvider cp = (IMergeViewerContentProvider) getMergeContentProvider.invoke(textMergeViewer);

			Method getSourceViewer = MergeSourceViewer.class.getDeclaredMethod("getSourceViewer");

			Method configureSourceViewer = TextMergeViewer.class.getDeclaredMethod("configureSourceViewer",
					SourceViewer.class, boolean.class);
			configureSourceViewer.setAccessible(true);
			configureSourceViewer.invoke(contentViewer, getSourceViewer.invoke(fLeft), cc.isLeftEditable()
					&& cp.isLeftEditable(textMergeViewer.getInput()));
			configureSourceViewer.invoke(contentViewer, getSourceViewer.invoke(fRight), cc.isRightEditable()
					&& cp.isRightEditable(textMergeViewer.getInput()));

			Field isConfiguredField = TextMergeViewer.class.getDeclaredField("isConfigured");
			isConfiguredField.setAccessible(true);
			isConfiguredField.set(contentViewer, true);
		} catch (Throwable t) {
			// ignore as it may not exist in other versions
		}
	}

	public static void selectAndRevealComment(ITextEditor textEditor, VersionedComment comment, CrucibleFile file) {

		int startLine = comment.getToStartLine();
		if (file.isOldFile()) {
			startLine = comment.getFromStartLine();
		}

		int endLine = comment.getToEndLine();
		if (file.isOldFile()) {
			endLine = comment.getFromEndLine();
		}
		if (endLine == 0) {
			endLine = startLine;
		}
		if (startLine != 0) {
			startLine--;
		}
		selectAndReveal(textEditor, startLine, endLine);

	}

	public static void handleMissingTeamConnectors() {
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				new MessageDialog(
						WorkbenchUtil.getShell(),
						"No Atlassian SCM Integration installed",
						null,
						"In order to access this functionality you need to install an Atlassian SCM Integration feature.\n\n"
								+ "You may install them by opening: Help | Install New Software, selecting 'Atlassian Connector for Eclipse' Update Site "
								+ "and chosing one or more integation features in 'Atlassian Integrations' category.",
						MessageDialog.INFORMATION, new String[] { IDialogConstants.OK_LABEL }, 0).open();
			}
		});
	}

	/**
	 * @param monitor
	 *            progress monitor
	 * @return all supported repositories configured in current workspace
	 */
	@NotNull
	public static Collection<ScmRepository> getRepositories(IProgressMonitor monitor) {
		TeamUiResourceManager teamResourceManager = AtlassianTeamUiPlugin.getDefault().getTeamResourceManager();
		Collection<ScmRepository> res = MiscUtil.buildArrayList();

		for (ITeamUiResourceConnector connector : teamResourceManager.getTeamConnectors()) {
			if (connector.isEnabled()) {
//				try {
				res.addAll(connector.getRepositories(monitor));
//				} catch (CoreException e) {
//					StatusHandler.log(new Status(IStatus.WARNING, AtlassianUiPlugin.PLUGIN_ID,
//							"Cannot get repositories for a connector"));
//					// ignore and try other connector(s)
//				}
			}
		}
		res.addAll(defaultConnector.getRepositories(monitor));
		return res;

	}

}