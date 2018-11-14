/*******************************************************************************
 * Copyright (c) 2018 THALES GLOBAL SERVICES.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Thales - initial API and implementation
 *******************************************************************************/
package org.polarsys.capella.filtering.tests.ju.tests;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.diffmerge.api.IComparison;
import org.eclipse.emf.diffmerge.api.scopes.IEditableModelScope;
import org.eclipse.emf.diffmerge.api.scopes.IModelScope;
import org.eclipse.emf.diffmerge.diffdata.impl.EComparisonImpl;
import org.eclipse.emf.diffmerge.impl.policies.DefaultMatchPolicy;
import org.eclipse.emf.diffmerge.impl.scopes.FragmentedModelScope;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.workspace.util.WorkspaceSynchronizer;
import org.eclipse.sirius.business.api.helper.SiriusUtil;
import org.eclipse.sirius.business.api.session.Session;
import org.polarsys.capella.core.data.capellamodeller.Project;
import org.polarsys.capella.filtering.FilteringResult;
import org.polarsys.capella.filtering.FilteringResults;
import org.polarsys.capella.filtering.tools.actions.FilteringExtractionJob;
import org.polarsys.capella.test.framework.api.BasicTestCase;
import org.polarsys.capella.test.framework.helpers.IResourceHelpers;
import org.polarsys.capella.test.framework.helpers.TestHelper;

/**
 * Derivation test
 */
@SuppressWarnings({ "restriction", "nls" })
public class DerivationTestCase extends BasicTestCase {
	private IProgressMonitor progressMonitor = new NullProgressMonitor();

	Session session = null;
	IProject project = null;

	FilteringResult configuration = null;
	private String modelId;

	@Override
	public List<String> getRequiredTestModels() {
		return Arrays.asList("sysmodel", "sysmodel_Empty FilteringResult", "sysmodel_FilteringResult 1",
				"sysmodel_FilteringResult 2");
	}

	@Override
	public void test() {
		// We use this try/catch with a wrappedException to be able to see in
		// junit which were the involved project and configuration
		try {
			doTestDerivation();
		} catch (Exception exception) {
			throw new WrappedException(getInfoOfCurrentTest(), exception);
		}
	}

	public void doTestDerivation() throws Exception {

		//TODO: foreach required model launch a test case
		String inputModelName = getRequiredTestModels().get(0);
		project = IResourceHelpers.getEclipseProjectInWorkspace(inputModelName);

		Session session = getSessionForTestModel(inputModelName);
		session.open(progressMonitor);

		// Get the configurations element from the model
		FilteringResults configurations = getConfigurationsElement(session.getSemanticResources());

		// TODO: foreach result derive and test
		configuration = configurations.getFilteringResults().get(0);
		IProject expectedResultProject = IResourceHelpers.getEclipseProjectInWorkspace(configuration.getName());

		// Create derived project
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		String derivedProjectName = configuration.getName();
		IProject derivedProject = workspace.getRoot().getProject(derivedProjectName);
		derivedProject.create(progressMonitor);
		derivedProject.open(progressMonitor);

		// Perform derivation. The process we are interested in this test
		FilteringExtractionJob job = new FilteringExtractionJob(project, derivedProject,
				configuration.getFilteringCriteria(), configuration, modelId);
		workspace.run(job, progressMonitor);

		// Check derived project
		//
		// Open session
		IFile derivedAirdFile = findFirstMatchingFile(SiriusUtil.SESSION_RESOURCE_EXTENSION, derivedProject);

		Session sessionOfDerived = org.polarsys.capella.test.framework.helpers.TestHelper
				.openOrGetSession(derivedAirdFile);
		sessionOfDerived.open(progressMonitor);
		assertTrue("Session of derived project does not open!: " + getInfoOfCurrentTest(), sessionOfDerived.isOpen());

		// Get and open reference model
		Session expectedResultSession = getSessionForTestModel(inputModelName+"_"+configuration.getName());

		expectedResultSession.open(progressMonitor);
		assertTrue("Session of expected project does not open!: " + getInfoOfCurrentTest(),
				expectedResultSession.isOpen());

		// Check original and derived projects have same natures

		checkProjectsHaveSameNatures(project, derivedProject);

		//
		// Check derived project against expected one
		//
		// Natures
		checkProjectsHaveSameNatures(expectedResultProject, derivedProject);
		// Project's content (files, folders)
		checkProjectsContainSameIResources(expectedResultProject, derivedProject);
		// References to semantic resources
		checkReferencedSemanticResources(expectedResultSession, sessionOfDerived);
		// Compare .melodymodeller files
		checkSemanticResourcesAreEqual(TestHelper.getSemanticResource(expectedResultSession),
				TestHelper.getSemanticResource(sessionOfDerived));

		// Remove also derived
		sessionOfDerived.save(progressMonitor);
		sessionOfDerived.close(progressMonitor);
		derivedProject.close(progressMonitor);
		derivedProject.delete(IResource.FORCE | IResource.ALWAYS_DELETE_PROJECT_CONTENT, progressMonitor);



	}

	/**
	 * Find the first object of type FilteringResults in given resources.
	 */
	public static FilteringResults getConfigurationsElement(Collection<Resource> semanticResources) {
		Iterator<?> allContentsIterator = EcoreUtil.getAllContents(semanticResources);
		while (allContentsIterator.hasNext()) {
			Object object = allContentsIterator.next();
			if (object instanceof FilteringResults) {
				return (FilteringResults) object;
			}
		}
		return null;
	}

	/**
	 * Check that given Sessions have the same list of semantic resources (paths
	 * relative to the containing project are used).
	 * 
	 * @param expectedResultSession
	 * @param sessionOfDerived
	 */
	public void checkReferencedSemanticResources(Session expectedResultSession, Session sessionOfDerived) {
		Collection<Resource> expectedSemanticResources = expectedResultSession.getSemanticResources();
		Collection<Resource> derivedSemanticResources = sessionOfDerived.getSemanticResources();

		Set<IPath> expectedSemanticResourcesSet = getResourceRelativePaths(expectedSemanticResources);
		Set<IPath> derivedSemanticResourceSet = getResourceRelativePaths(derivedSemanticResources);

		assertEquals("Sessions do not have the same semantic resources list", expectedSemanticResourcesSet,
				derivedSemanticResourceSet);
	}

	// Info to display (in case of failing test)
	private String getInfoOfCurrentTest() {
		return "Project=[" + project.getName() + "] with FilteringResult=[" + configuration.getName() + "]";
	}

	/**
	 * Specific matching policy for Project element: When comparing Project
	 * elements, do not use their ids but their names.
	 */
	private class TestCaseMatchPolicy extends DefaultMatchPolicy {

		@Override
		public Object getMatchID(EObject element, IModelScope scope) {
			// Use the name as match id for projects because in case of library,
			// derived projects have different Capella id.
			if (element instanceof Project) {
				return ((Project) element).getName();
			}
			return super.getMatchID(element, scope);
		}
	}

	/**
	 * Return the first IFile under resource tree starting at 'root' that has a
	 * given extension. Returns null if no matching file is found.
	 *
	 * @param fileExtension
	 *            the file extension, may be null or the empty string
	 * @param root
	 *            where to start searching, null to start at the workspace root
	 * @see IResource.getFileExtension
	 * @throws CoreException
	 */
	public static IFile findFirstMatchingFile(final String fileExtension, IResource root) throws CoreException {
		final AtomicReference<IFile> result = new AtomicReference<IFile>();
		IResource scope = root != null ? root : ResourcesPlugin.getWorkspace().getRoot();
		scope.accept(new IResourceVisitor() {
			public boolean visit(IResource resource) {
				if (result.get() != null) {
					return false;
				}
				if (resource.getType() == IResource.FILE) {
					if (fileExtension == null) {
						if (((IFile) resource).getFileExtension() == null) {
							result.set((IFile) resource);
						}
					} else {
						if (fileExtension.equals(((IFile) resource).getFileExtension())) {
							result.set((IFile) resource);
						}
					}
				}
				return true;
			}
		});
		return result.get();
	}

	/**
	 * Compare given semantic resources using Diff/Merge.
	 * 
	 * @param expectedResource
	 * @param derivedResource
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws CoreException
	 */
	public void checkSemanticResourcesAreEqual(Resource expectedResource, Resource derivedResource)
			throws InterruptedException, IOException, CoreException {
		// Compare with the expected results (if contributed) Check
		// readme_namingConventions
		// Import expected result

		// Instantiate the scopes to compare
		IEditableModelScope targetScope = new FragmentedModelScope(derivedResource, true);
		IEditableModelScope referenceScope = new FragmentedModelScope(expectedResource, true);

		// Compare
		IComparison comparison = new EComparisonImpl(targetScope, referenceScope);
		comparison.compute(new TestCaseMatchPolicy(), null, null, progressMonitor);

		// Check if differences
		if (comparison.getNbDifferences() != 0) {
			fail(getInfoOfCurrentTest() + " expected derivation result failed.");
		}
	}

	/**
	 * Check that given IProjects have the same natures.
	 * 
	 * @param project1
	 * @param project2
	 * @throws CoreException
	 */
	public void checkProjectsHaveSameNatures(IProject project1, IProject project2) throws CoreException {
		String[] naturesProject1 = project1.getDescription().getNatureIds();
		String[] naturesProject2 = project2.getDescription().getNatureIds();

		// Check natures ignoring order.
		Set<String> naturesProject1Set = new HashSet<>(Arrays.asList(naturesProject1));
		Set<String> naturesProject2Set = new HashSet<>(Arrays.asList(naturesProject2));

		assertEquals("Projects do not have the same nature(s)", naturesProject1Set, naturesProject2Set);
	}

	/**
	 * Check (recursively) that given IContainers contain the same IResource
	 * (i.e. the same list of file/folder)
	 * 
	 * @param container1
	 * @param container2
	 * @throws CoreException
	 */
	public void checkProjectsContainSameIResources(IContainer container1, IContainer container2) throws CoreException {
		IResource[] project1Members = container1.members();
		IResource[] project2Members = container2.members();

		Set<IPath> project1MembersPaths = getProjectRelativePaths(project1Members);
		Set<IPath> project2MembersPaths = getProjectRelativePaths(project2Members);

		assertEquals("Projects do not have same content", project1MembersPaths, project2MembersPaths);

		// Look in IContainers (folders...).
		for (IResource project1IResource : project1Members) {
			if (project1IResource instanceof IContainer) {
				IResource project2Resource = container2.findMember(project1IResource.getName());
				checkProjectsContainSameIResources((IContainer) project1IResource, (IContainer) project2Resource);
			}
		}
	}

	/**
	 * Get project relative paths of given IResources.
	 * 
	 * @param projectMembers
	 * @return
	 */
	public Set<IPath> getProjectRelativePaths(IResource[] projectMembers) {
		Set<IPath> relativePaths = new HashSet<>();
		for (IResource iResource : projectMembers) {
			relativePaths.add(iResource.getProjectRelativePath());
		}
		return relativePaths;
	}

	/**
	 * Get project relative paths of given Resources.
	 * 
	 * @param projectMembers
	 * @return
	 */
	public Set<IPath> getResourceRelativePaths(Collection<Resource> resources) {
		Set<IPath> resourceRelativePaths = new HashSet<>();
		for (Resource r : resources) {
			resourceRelativePaths.add(WorkspaceSynchronizer.getFile(r).getProjectRelativePath());
		}
		return resourceRelativePaths;
	}

}