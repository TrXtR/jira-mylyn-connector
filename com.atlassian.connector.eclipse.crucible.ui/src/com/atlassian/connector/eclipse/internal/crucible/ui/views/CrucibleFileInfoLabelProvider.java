package com.atlassian.connector.eclipse.internal.crucible.ui.views;

import com.atlassian.connector.eclipse.internal.crucible.ui.CrucibleImages;
import com.atlassian.theplugin.commons.VersionedVirtualFile;
import com.atlassian.theplugin.commons.crucible.api.model.Comment;
import com.atlassian.theplugin.commons.crucible.api.model.CommitType;
import com.atlassian.theplugin.commons.crucible.api.model.CrucibleFileInfo;
import com.atlassian.theplugin.commons.crucible.api.model.FileType;
import com.atlassian.theplugin.commons.crucible.api.model.RepositoryType;
import com.atlassian.theplugin.commons.crucible.api.model.Comment.ReadState;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.mylyn.internal.provisional.commons.ui.CommonFonts;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.PlatformUI;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * A simple label provider
 */
public class CrucibleFileInfoLabelProvider extends ColumnLabelProvider implements IStyledLabelProvider {

	public String getText(Object element) {
		return getStyledText(element).toString();
	}

	@Override
	public Font getFont(Object element) {
		if (element instanceof Comment) {
			if (((Comment) element).getReadState().equals(ReadState.UNREAD)
					|| ((Comment) element).getReadState().equals(ReadState.LEAVE_UNREAD)) {
				return CommonFonts.BOLD;
			}
		}
		return super.getFont(element);
	}

	public StyledString getStyledText(Object element) {
		if (element instanceof Comment) {
			Comment comment = (Comment) element;
			final String headerText;

			String msg = comment.getMessage();
			if (msg.length() > ExplorerView.COMMENT_PREVIEW_LENGTH) {
				headerText = msg.substring(0, ExplorerView.COMMENT_PREVIEW_LENGTH) + "...";
			} else {
				headerText = msg;
			}
			return new StyledString(headerText);
		}
		if (element instanceof CrucibleFileInfo) {
			CrucibleFileInfo file = (CrucibleFileInfo) element;
			StyledString styledString = new StyledString();
			styledString.append(file.getFileDescriptor().getUrl());
			StringBuilder revisionString = getRevisionInfo(file);

			if (revisionString.length() > 0) {
				styledString.append(" ");
				styledString.append(revisionString.toString(), StyledString.DECORATIONS_STYLER);
			}
			return styledString;
		}

		return element == null ? new StyledString("") : new StyledString(element.toString());
	}

	@Override
	public Image getImage(Object element) {
		if (element instanceof CrucibleFileInfo) {
			final CrucibleFileInfo cfi = (CrucibleFileInfo) element;
			IEditorRegistry fEditorRegistry = PlatformUI.getWorkbench().getEditorRegistry();
			final ImageDescriptor imageDescriptor = fEditorRegistry.getImageDescriptor(cfi.getFileDescriptor()
					.getName());

			return CrucibleImages.getImage(imageDescriptor);
			//return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_FILE);
		}
		if (element instanceof Comment) {
			final Comment comment = (Comment) element;

			final String avatarUrl = comment.getAuthor().getAvatarUrl();
			if (avatarUrl != null) {
				// this stuff will be actually used only when avatar URLs are served by Crucible to all authorized users
				// who see given review
				try {
					return CrucibleImages.getImage(ImageDescriptor.createFromURL(new URL(avatarUrl + "%3Fs%3D16&s=16")));
				} catch (MalformedURLException e) {
					return CrucibleImages.getImage(CrucibleImages.DEFAULT_AVATAR);
				}
			} else {
				return CrucibleImages.getImage(CrucibleImages.DEFAULT_AVATAR);
			}
		}
		return null;
	}

	private StringBuilder getRevisionInfo(CrucibleFileInfo file) {

		final VersionedVirtualFile oldFileDescriptor = file.getOldFileDescriptor();
		final VersionedVirtualFile newFileDescriptor = file.getFileDescriptor();

		boolean oldFileHasRevision = oldFileDescriptor != null && oldFileDescriptor.getRevision() != null
				&& oldFileDescriptor.getRevision().length() > 0;
		boolean oldFileHasUrl = oldFileDescriptor != null && oldFileDescriptor.getUrl() != null
				&& oldFileDescriptor.getUrl().length() > 0;

		boolean newFileHasRevision = newFileDescriptor != null && newFileDescriptor.getRevision() != null
				&& newFileDescriptor.getRevision().length() > 0;
		boolean newFileHasUrl = newFileDescriptor != null && newFileDescriptor.getUrl() != null
				&& newFileDescriptor.getUrl().length() > 0;

		FileType filetype = file.getFileType();

		StringBuilder revisionString = new StringBuilder();

		//if repository type is uploaded or patch, display alternative for now since we cannot open the file yet
		if (file.getRepositoryType() == RepositoryType.PATCH) {
			revisionString.append("Part of a Patch");
		} else if (file.getRepositoryType() == RepositoryType.UPLOAD) {
			revisionString.append("Pre-commit");
		} else {
			//if file is deleted or not a file, do not include any revisions 
			//   (we need a local resource to retrieve the old revision from SVN, which we do not have)
			if (file.getCommitType() == CommitType.Deleted || filetype != FileType.File) {
				revisionString.append("N/A ");
			} else {
				if (oldFileHasUrl && oldFileHasRevision) {
					revisionString.append(oldFileDescriptor.getRevision());
				}
				if (oldFileHasRevision) {
					if (newFileHasRevision) {
						revisionString.append("-");
					}
				}

				if (newFileHasUrl && newFileHasRevision && file.getCommitType() != CommitType.Deleted) {
					revisionString.append(newFileDescriptor.getRevision());
				}
			}
		}
		return revisionString;
	}
}