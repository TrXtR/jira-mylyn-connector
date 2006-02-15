/*******************************************************************************
 * Copyright (c) 2006 - 2006 Mylar eclipse.org project and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Mylar project committers - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylar.jira.tests;

import java.net.URL;

import junit.framework.TestCase;

import org.eclipse.mylar.internal.jira.JiraFilter;
import org.eclipse.mylar.internal.jira.JiraFilterHit;
import org.eclipse.mylar.internal.jira.JiraServerFacade;
import org.eclipse.mylar.internal.jira.JiraTask;
import org.eclipse.mylar.internal.jira.MylarJiraPlugin;
import org.eclipse.mylar.internal.tasklist.AbstractRepositoryClient;
import org.eclipse.mylar.internal.tasklist.MylarTaskListPlugin;
import org.eclipse.mylar.internal.tasklist.TaskRepository;
import org.tigris.jira.core.model.NamedFilter;

/**
 * @author Wesley Coelho (initial integration patch)
 */
public class JiraTaskArchiveTest extends TestCase {

	private static final String LABEL = "Label";

	private static final String HANDLE1 = "Handle1";

	private final static String USER = "mylartest";

	private final static String PASSWORD = "mylartest";

	private final static String SERVER_URL = "http://developer.atlassian.com/jira";

	private JiraServerFacade jiraFacade = null;

	private TaskRepository jiraRepo = null;

	protected void setUp() throws Exception {
		super.setUp();
		URL repoURL = new URL(SERVER_URL);
		jiraRepo = new TaskRepository(MylarJiraPlugin.JIRA_REPOSITORY_KIND,
				repoURL);
		jiraRepo.setAuthenticationCredentials(USER, PASSWORD);
		MylarTaskListPlugin.getRepositoryManager().addRepository(jiraRepo);
		jiraFacade = JiraServerFacade.getDefault();
	}

	protected void tearDown() throws Exception {
		AbstractRepositoryClient client = MylarTaskListPlugin
				.getRepositoryManager().getRepositoryClient(
						MylarJiraPlugin.JIRA_REPOSITORY_KIND);
		assertNotNull(client);
		client.clearArchive();
		MylarTaskListPlugin.getTaskListManager().getTaskList().clear();
		MylarTaskListPlugin.getRepositoryManager().removeRepository(jiraRepo);
		jiraFacade.logOut();
		super.tearDown();
	}

	public void testJiraTaskRegistry() {
		AbstractRepositoryClient client = MylarTaskListPlugin
				.getRepositoryManager().getRepositoryClient(
						MylarJiraPlugin.JIRA_REPOSITORY_KIND);
		assertNotNull(client);

		JiraTask task1 = new JiraTask(HANDLE1, LABEL, true);
		JiraTask task2 = new JiraTask(HANDLE1, LABEL, true);

		client.addTaskToArchive(task1);
		client.addTaskToArchive(task2);

		assertTrue(client.getArchiveTasks().size() == 1);
		assertEquals(client.getTaskFromArchive(HANDLE1), task1);
	}

	public void testJiraTaskRegistryIntegration() {
		AbstractRepositoryClient client = MylarTaskListPlugin
				.getRepositoryManager().getRepositoryClient(
						MylarJiraPlugin.JIRA_REPOSITORY_KIND);
		assertNotNull(client);
		client.clearArchive();

		assertTrue(client.getArchiveTasks().size() == 0);

		NamedFilter[] namedFilters = jiraFacade.getJiraServer()
				.getNamedFilters();
		JiraFilter filter = new JiraFilter(namedFilters[0], true);
		MylarTaskListPlugin.getTaskListManager().addQuery(filter);

		while (filter.isRefreshing()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		assertTrue(filter.getHits().size() > 0);
		JiraFilterHit jHit = (JiraFilterHit) filter.getHits().get(0);

		assertNotNull(client.getTaskFromArchive(jHit.getHandleIdentifier()));
	}
}