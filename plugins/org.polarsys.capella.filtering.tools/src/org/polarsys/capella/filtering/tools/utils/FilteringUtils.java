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
package org.polarsys.capella.filtering.tools.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.impl.InternalTransaction;
import org.eclipse.emf.transaction.impl.InternalTransactionalEditingDomain;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.ui.parts.GraphicalEditor;
import org.eclipse.gmf.runtime.diagram.ui.editparts.ConnectionEditPart;
import org.eclipse.gmf.runtime.notation.Diagram;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.sirius.business.api.dialect.DialectManager;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.business.api.session.SessionManager;
import org.eclipse.sirius.diagram.DDiagram;
import org.eclipse.sirius.diagram.business.api.refresh.CanonicalSynchronizer;
import org.eclipse.sirius.diagram.business.api.refresh.CanonicalSynchronizerFactory;
import org.eclipse.sirius.diagram.ui.business.api.view.SiriusGMFHelper;
import org.eclipse.sirius.diagram.ui.tools.api.editor.DDiagramEditor;
import org.eclipse.sirius.viewpoint.DRepresentation;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.polarsys.capella.common.data.modellingcore.ModelElement;
import org.polarsys.capella.common.ef.ExecutionManager;
import org.polarsys.capella.common.ef.ExecutionManagerRegistry;
import org.polarsys.capella.common.ef.command.AbstractNonDirtyingCommand;
import org.polarsys.capella.common.ef.command.AbstractReadWriteCommand;
import org.polarsys.capella.common.ef.command.ICommand;
import org.polarsys.capella.common.helpers.TransactionHelper;
import org.polarsys.capella.common.libraries.ILibraryManager;
import org.polarsys.capella.common.libraries.IModel;
import org.polarsys.capella.common.libraries.manager.LibraryManagerExt;
import org.polarsys.capella.common.tools.report.config.registry.ReportManagerRegistry;
import org.polarsys.capella.common.tools.report.util.IReportManagerDefaultComponents;
import org.polarsys.capella.core.data.capellacore.CapellaElement;
import org.polarsys.capella.core.data.capellacore.NamedElement;
import org.polarsys.capella.core.data.capellacore.Namespace;
import org.polarsys.capella.core.data.capellamodeller.Project;
import org.polarsys.capella.core.data.capellamodeller.SystemEngineering;
import org.polarsys.capella.core.libraries.model.ICapellaModel;
import org.polarsys.capella.core.model.handler.command.CapellaResourceHelper;
import org.polarsys.capella.core.model.handler.helpers.CapellaProjectHelper;
import org.polarsys.capella.core.model.handler.helpers.CrossReferencerHelper;
import org.polarsys.capella.core.platform.sirius.ui.commands.CapellaDeleteCommand;
import org.polarsys.capella.core.sirius.ui.actions.OpenSessionAction;
import org.polarsys.capella.core.sirius.ui.helper.SessionHelper;
import org.polarsys.capella.filtering.AssociatedFilteringCriterionSet;
import org.polarsys.capella.filtering.CreationDefaultFilteringCriterionSet;
import org.polarsys.capella.filtering.FilteringCriterion;
import org.polarsys.capella.filtering.FilteringCriterionPkg;
import org.polarsys.capella.filtering.FilteringCriterionSet;
import org.polarsys.capella.filtering.FilteringFactory;
import org.polarsys.capella.filtering.FilteringModel;
import org.polarsys.capella.filtering.FilteringResult;
import org.polarsys.capella.filtering.FilteringResultPkg;
import org.polarsys.capella.filtering.FilteringResults;
import org.polarsys.capella.filtering.tools.FilteringToolsPlugin;
import org.polarsys.kitalpha.emde.model.ElementExtension;
import org.polarsys.kitalpha.emde.model.ExtensibleElement;

public class FilteringUtils {

	private static final Logger logger = ReportManagerRegistry.getInstance()
			.subscribe(IReportManagerDefaultComponents.DEFAULT);

	public static boolean isVariantModel(EObject modelObject, boolean includeReferencedLibraries) {
		List<FilteringModel> filteringModels = getFilteringModels(modelObject, includeReferencedLibraries);
		return !filteringModels.isEmpty() && hasFilteringFeatures(filteringModels);
	}

	/**
	 * @param aird
	 * @return
	 */
	public static Session openSession(IFile aird) {
		OpenSessionAction openSessionAction = new OpenSessionAction();
		openSessionAction.setOpenActivityExplorer(false);
		openSessionAction.setRunInProgressService(false);
		openSessionAction.selectionChanged(new StructuredSelection(aird));
		// Open the session.
		openSessionAction.run();

		Session session = SessionHelper.getSession(aird);
		return session;
	}

	/**
	 * Get the FilteringResultPkgs given any eObject of the model
	 * 
	 * @param eObject
	 * @return the Feature model
	 */
	public static FilteringResultPkg getFilteringResultPkg(SystemEngineering systemEngineering) {
		if (null != systemEngineering) {
			for (ElementExtension elementExtension : systemEngineering.getOwnedExtensions()) {
				if (elementExtension instanceof CreationDefaultFilteringCriterionSet) {
					return (FilteringResultPkg) elementExtension;
				}
			}
		}
		return null;
	}

	public static Collection<Project> getMainAndReferencedVariantProjects(EObject object) {
		Collection<Project> result = new HashSet<Project>();
		Project mainPrj = CapellaProjectHelper.getProject((EObject) object);
		if (mainPrj != null) {
			List<Project> projects = FilteringUtils.getReferencedProjects(mainPrj);
			projects.add(mainPrj);
			for (Project prj : projects) {
				if (FilteringUtils.isVariantModel(prj, false)) {
					result.add(prj);
				}
			}
		}
		return result;
	}

	/**
	 * @param project
	 * @return
	 */
	public static List<Project> getReferencedProjects(Project project) {
		List<Project> res = new ArrayList<Project>();
		Session session = SessionManager.INSTANCE.getSession(project);
		TransactionalEditingDomain transactionalEditingDomain = session.getTransactionalEditingDomain();
		IModel model = ILibraryManager.INSTANCE.getModel(project);
		Collection<IModel> allReferences = LibraryManagerExt.getAllReferences(model);
		for (IModel lib : allReferences) {
			if (lib instanceof ICapellaModel) {
				res.add(((ICapellaModel) lib).getProject(transactionalEditingDomain));
			}
		}
		return res;
	}

	/**
	 * @param session
	 * @return
	 */
	public static List<Project> getReferencedProjects(Session session) {
		List<Project> res = new ArrayList<Project>();
		TransactionalEditingDomain transactionalEditingDomain = session.getTransactionalEditingDomain();
		IModel abstractModel = ILibraryManager.INSTANCE.getModel(transactionalEditingDomain);
		Collection<IModel> referencedLibraries = LibraryManagerExt.getAllReferences(abstractModel);
		for (IModel lib : referencedLibraries) {
			if (lib instanceof ICapellaModel) {
				res.add(((ICapellaModel) lib).getProject(transactionalEditingDomain));
			}
		}
		return res;
	}

	/**
	 * @param session
	 * @return
	 */
	public static List<Resource> getReferencedLibraries(Session session) {
		List<Resource> res = new ArrayList<Resource>();

		for (Project project : getReferencedProjects(session)) {
			res.add(project.eResource());
		}
		return res;
	}

	/**
	 * @param project
	 * @return
	 */
	public static List<Resource> getReferencedLibraries(Project project) {
		return getReferencedLibraries(SessionManager.INSTANCE.getSession(project));
	}

	/**
	 * Get the AssociatedFilteringCriterionSet of a Melody Element or null if it
	 * doesn't have
	 * 
	 * @param melodyElement
	 * @return
	 */
	public static AssociatedFilteringCriterionSet getAssociatedFilteringCriterionSet(EObject melodyElement) {
		if (melodyElement instanceof ExtensibleElement) {
			List<ElementExtension> ownedExtensions = ((ExtensibleElement) melodyElement).getOwnedExtensions();
			for (ElementExtension elementExtension : ownedExtensions) {
				if (elementExtension instanceof AssociatedFilteringCriterionSet) {
					return (AssociatedFilteringCriterionSet) elementExtension;
				}
			}
		}
		return null;
	}

	public static List<FilteringCriterion> getAssociatedCriteria(EObject element, List<EObject> visitedElements) {
		List<FilteringCriterion> featureList = new ArrayList<FilteringCriterion>();

		if (element instanceof CapellaElement) {
			CapellaElement capellaElement = (CapellaElement) element;

			// Add its own features. Explicit
			featureList.addAll(getExplicitAssociatedCriteria(element));

			// If the explicit is empty then add the implicit
			if (featureList.isEmpty()) {
				// Add implicit features
				List<FilteringCriterion> features = getImplicitAssociatedCriteria(capellaElement, visitedElements);
				for (FilteringCriterion feature : features) {
					if (!featureList.contains(feature)) {
						featureList.add(feature);
					}
				}
			}
		}
		return featureList;
	}

	/**
	 * Get the associated features of an EObject
	 * 
	 * @param element
	 * @return list of features
	 */
	public static List<FilteringCriterion> getAssociatedCriteria(EObject element) {
		return getAssociatedCriteria(element, new ArrayList<EObject>());
	}

	/**
	 * @param element
	 * @return
	 */
	public static List<FilteringCriterion> getExplicitAssociatedCriteria(EObject element) {
		List<FilteringCriterion> featureList = new ArrayList<FilteringCriterion>();
		if (element instanceof CapellaElement) {
			// Get the feature set
			FilteringCriterionSet featureSet = getAssociatedFilteringCriterionSet((CapellaElement) element);
			// Return empty list if no associated feature set
			if (featureSet == null) {
				return featureList;
			}
			// Get filtering features
			for (FilteringCriterion feature : featureSet.getFilteringCriteria()) {
				if (!featureList.contains(feature)) {
					featureList.add(feature);
				}
			}
		}
		return featureList;
	}

	public static List<FilteringCriterion> getImplicitAssociatedCriteria(EObject element) {
		return getImplicitAssociatedCriteria(element, new ArrayList<EObject>());
	}

	/**
	 * @param element
	 * @return
	 */
	public static List<FilteringCriterion> getImplicitAssociatedCriteria(EObject element,
			List<EObject> visitedElements) {
		List<FilteringCriterion> res = new ArrayList<FilteringCriterion>();
		List<FilteringModel> filteringModels = getFilteringModels(element, true);
		if (!FilteringUtils.hasFilteringFeatures(filteringModels)) {
			return res;
		}

		for (FilteringCriterion currentFeature : FilteringUtils.getOwnedFilteringCriteria(filteringModels)) {

			if (FilteringToolsPlugin.getImplicitImpactCache().containsKey(currentFeature)) {
				logger.debug("Cache hit: " + currentFeature.hashCode() + " on "
						+ FilteringToolsPlugin.getImplicitImpactCache().size() + " elements");
				Collection<?> expanded = FilteringToolsPlugin.getImplicitImpactCache().get(currentFeature);
				if (expanded.contains(element)) {
					res.add(currentFeature);

				}
				continue;
			}

			List<EObject> referencedElements = CrossReferencerHelper.getReferencingElements(currentFeature);
			List<CapellaElement> explictlyReferencedElements = new ArrayList<CapellaElement>();
			for (EObject referencedElement : referencedElements) {
				if (referencedElement instanceof AssociatedFilteringCriterionSet
						&& referencedElement.eContainer() instanceof CapellaElement) {
					explictlyReferencedElements.add((CapellaElement) referencedElement.eContainer());
				}
			}

			if (explictlyReferencedElements.contains(element)) {
				continue;
			}

			ExecutionManager executionManager = TransactionHelper.getExecutionManager(element);
			CapellaDeleteCommand mdc = new ModelOnlyFilteringDeleteCommand(executionManager, explictlyReferencedElements,
					true, false, false);
			Set<?> expandedElements = mdc.getAllElementsToDelete();

			if (expandedElements.contains(element)) {
				res.add(currentFeature);
			}

			FilteringToolsPlugin.getImplicitImpactCache().put(currentFeature, expandedElements);
		}
		return res;
	}

	public static Set<EObject> getImpactedElements(Collection<EObject> element) {
		Set<EObject> relatedElements = new HashSet<EObject>();

		if (element == null || element.isEmpty()) {
			return relatedElements;
		}

		ExecutionManager executionManager = TransactionHelper.getExecutionManager(element);
		CapellaDeleteCommand mdc = new ModelOnlyFilteringDeleteCommand(executionManager, element, true, false, false);
		Set<?> impactedElements = mdc.getAllElementsToDelete();
		for (Object impactedElem : impactedElements) {
			if (impactedElem instanceof EObject) {
				relatedElements.add((EObject) impactedElem);
			}
		}
		return relatedElements;
	}

	/**
	 * Get the FilteringModel given any eObject of the model
	 * 
	 * @param eObject
	 * @return the Feature model
	 */
	public static FilteringModel getFilteringModel(EObject eObject) {
		List<FilteringModel> filteringModels = getFilteringModels(eObject, false);
		return !filteringModels.isEmpty() ? filteringModels.get(0) : null;
	}

	/**
	 * 
	 * @param eObjects
	 * @param includeReferencedLibraries
	 * @return The intersection of FilteringModels for the given list of eObjects
	 */
	public static Collection<FilteringModel> getCommonFilteringModels(Collection<ModelElement> eObjects,
			boolean includeReferencedLibraries) {
		Collection<FilteringModel> filteringModels = null;
		for (EObject element : eObjects) {
			List<FilteringModel> currentElementFilteringModels = FilteringUtils.getFilteringModels(element,
					includeReferencedLibraries);
			if (filteringModels == null) { // If first iteration
				filteringModels = new HashSet<FilteringModel>();
				filteringModels.addAll(currentElementFilteringModels);
			} else {
				filteringModels.retainAll(currentElementFilteringModels);
			}
		}
		return filteringModels != null ? filteringModels : Collections.<FilteringModel>emptySet();
	}

	/**
	 * Get the list of FilteringModel given any eObject of the model
	 * 
	 * @param eObject
	 * @return the Feature model
	 */
	public static List<FilteringModel> getFilteringModels(EObject eObject, boolean includeReferencedLibraries) {
		List<FilteringModel> result = new ArrayList<FilteringModel>();
		if (null != eObject) {
			if (!includeReferencedLibraries) {
				SystemEngineering systemEngineering = getSystemEngineering(eObject);
				FilteringModel filteringModel = getFilteringModel(systemEngineering);
				if (filteringModel != null) {
					result.add(filteringModel);
				}
			} else {
				for (Project prj : FilteringUtils.getMainAndReferencedVariantProjects(eObject)) {
					FilteringModel filteringModel = getFilteringModel(prj);
					if (filteringModel != null) {
						result.add(filteringModel);
					}
				}
			}
		}
		return result;
	}

	public static List<FilteringCriterion> getOwnedFilteringCriteria(Collection<FilteringModel> filteringModels) {
		List<FilteringCriterion> result = new ArrayList<FilteringCriterion>();

		for (FilteringModel filteringModel : filteringModels) {

			filteringModel.eAllContents().forEachRemaining(eObj -> {
				if (eObj instanceof FilteringCriterion)
					result.add((FilteringCriterion) eObj);
			});

		}

		return result;
	}

	public static boolean hasFilteringFeatures(List<FilteringModel> filteringModels) {
		return !getOwnedFilteringCriteria(filteringModels).isEmpty();
	}

	public static List<EObject> getOwnedFilteringCriteriaAndPkgs(Collection<FilteringModel> filteringModels) {
		List<EObject> result = new ArrayList<EObject>();
		for (FilteringModel filteringModel : filteringModels) {
			EList<FilteringCriterion> ownedCriteria = filteringModel.getOwnedFilteringCriteria();
			EList<FilteringCriterionPkg> ownedCriteriaPkgs = filteringModel.getOwnedFilteringCriterionPkgs();
			if (ownedCriteria != null)
				result.addAll(ownedCriteria);
			if (ownedCriteriaPkgs != null)
				result.addAll(ownedCriteriaPkgs);

		}

		return result;
	}

	public static List<EObject> getOwnedFilteringCriteriaAndPkgs(FilteringCriterionPkg criterionPkg) {
		List<EObject> result = new ArrayList<EObject>();
		EList<FilteringCriterion> ownedCriteria = criterionPkg.getOwnedFilteringCriteria();
		EList<FilteringCriterionPkg> ownedCriteriaPkgs = criterionPkg.getOwnedFilteringCriterionPkgs();
		if (ownedCriteria != null)
			result.addAll(ownedCriteria);
		if (ownedCriteriaPkgs != null)
			result.addAll(ownedCriteriaPkgs);

		return result;
	}

	/**
	 * Append to a filtering result/criterion name all its parent packages (e.g.
	 * pkg1/subpkg/filteringResult)
	 * 
	 * @param filteringResult
	 * @return
	 */
	public static String formatFilteringItemName(NamedElement filteringItem) {

		Stack<String> stack = new Stack<String>();
		EObject parentPkg = filteringItem.eContainer();

		// collect parent pkg names
		while (parentPkg instanceof Namespace) {
			stack.push(((NamedElement) parentPkg).getName());
			parentPkg = parentPkg.eContainer();
		}

		if (stack.isEmpty())
			return filteringItem.getName();

		StringBuilder sb = new StringBuilder();

		while (!stack.isEmpty()) {
			sb.append(stack.pop() + "/");
		}

		sb.append(filteringItem.getName());
		return sb.toString();
	}

	/**
	 * @param systemEngineering
	 */
	public static FilteringModel getFilteringModel(SystemEngineering systemEngineering) {
		if (null != systemEngineering) {
			for (ElementExtension elementExtension : systemEngineering.getOwnedExtensions()) {
				if (elementExtension instanceof FilteringModel) {
					return (FilteringModel) elementExtension;
				}
			}
		}
		return null;
	}

	/**
	 * Get the FilteringResults given any eObject of the model
	 * 
	 * @param eObject
	 * @return the Feature model
	 */
	public static FilteringResults getFilteringResults(EObject eObject) {
		if (null != eObject) {
			SystemEngineering systemEngineering = getSystemEngineering(eObject);
			return getFilteringResults(systemEngineering);
		}
		return null;
	}

	/**
	 * Method for override if needed. For example when working with diagrams.
	 * get all filtering results in a list
	 * 
	 * @param object
	 * @return
	 */
	public static List<FilteringResult> getFilteringResults(Collection<Project> projects) {
		if (projects == null)
			return Collections.emptyList();

		List<FilteringResult> result = new ArrayList<>();
		for (Project prj : projects) {
			FilteringResults filteringResults = FilteringUtils.getFilteringResults(prj);
			if (filteringResults != null) {
				result.addAll(getAllFilteringResults(filteringResults));
			}
		}
		return result;
	}

	/**
	 * Get all contained Filtering Result (e.g. traverse all sub packages)
	 * 
	 * @param filteringResults
	 * @return
	 */
	public static List<FilteringResult> getAllFilteringResults(FilteringResults filteringResults) {
		List<FilteringResult> filteringResultList = new ArrayList<>();

		if (filteringResults != null) {

			filteringResults.eAllContents().forEachRemaining(eObj -> {
				if (eObj instanceof FilteringResult)
					filteringResultList.add((FilteringResult) eObj);
			});
		}

		return filteringResultList;
	}

	/**
	 * @param systemEngineering
	 * @return
	 */
	public static FilteringResults getFilteringResults(SystemEngineering systemEngineering) {
		if (null != systemEngineering) {
			for (ElementExtension elementExtension : systemEngineering.getOwnedExtensions()) {

				if (elementExtension instanceof FilteringResults) {
					return (FilteringResults) elementExtension;
				}
			}
		}
		return null;
	}

	/**
	 * Get the FilteringModel given any eObject of the model
	 * 
	 * @param eObject
	 * @return the Feature model
	 */
	public static CreationDefaultFilteringCriterionSet getCreationDefaultFeatureSet(EObject eObject) {
		if (null != eObject) {
			SystemEngineering systemEngineering = getSystemEngineering(eObject);
			return getCreationDefaultFeatureSet(systemEngineering);
		}
		return null;
	}

	/**
	 * @param systemEngineering
	 * @return
	 */
	public static CreationDefaultFilteringCriterionSet getCreationDefaultFeatureSet(
			SystemEngineering systemEngineering) {
		if (null != systemEngineering) {
			for (ElementExtension elementExtension : systemEngineering.getOwnedExtensions()) {
				if (elementExtension instanceof CreationDefaultFilteringCriterionSet) {
					return (CreationDefaultFilteringCriterionSet) elementExtension;
				}
			}
		}
		return null;
	}

	/**
	 * Get the SystemEngineering element given any eObject of the model
	 * 
	 * @param eObject
	 * @return
	 */
	public static SystemEngineering getSystemEngineering(EObject eObject) {
		if (eObject != null) {
			Project project = CapellaProjectHelper.getProject(eObject);
			if ((project != null) && (project.getOwnedModelRoots() != null)) {
				for (EObject modelRoot : project.getOwnedModelRoots()) {
					if (modelRoot instanceof SystemEngineering) {
						return (SystemEngineering) modelRoot;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Get a comma separated String of the associated features of an element
	 * 
	 * @param element
	 * @return
	 */
	public static String getCommaSeparatedExplicitFeatures(EObject element) {
		List<FilteringCriterion> featureList = getExplicitAssociatedCriteria(element);
		return getCommaSeparatedVariabilityFeaturesList(featureList);
	}

	/**
	 * Get a comma separated String of the given filtering features list
	 */
	public static String getCommaSeparatedVariabilityFeaturesList(List<FilteringCriterion> featureList) {
		if ((featureList == null) || featureList.isEmpty()) {
			return null;
		}
		String features = ""; //$NON-NLS-1$
		for (FilteringCriterion feature : featureList) {
			features = features + feature.getName() + ", "; //$NON-NLS-1$
		}
		// remove last comma
		if (features.length() > 0) {
			features = features.substring(0, features.length() - ", ".length()); //$NON-NLS-1$
		}
		return features;
	}

	/**
	 * Add associated features
	 * 
	 * @param melodyElement
	 * @param filteringCriterions
	 */
	public static void addAssociatedCriteria(EObject melodyElement, List<FilteringCriterion> filteringCriterions) {
		// Get associated feature set
		AssociatedFilteringCriterionSet featureSet = FilteringUtils.getAssociatedFilteringCriterionSet(melodyElement);
		// Create it if empty
		if ((featureSet == null) && !filteringCriterions.isEmpty()) {
			featureSet = FilteringFactory.eINSTANCE.createAssociatedFilteringCriterionSet();
			((ExtensibleElement) melodyElement).getOwnedExtensions().add(featureSet);
		}
		// Add the features
		for (FilteringCriterion filteringCriterion : filteringCriterions) {
			if (!featureSet.getFilteringCriteria().contains(filteringCriterion)) {
				featureSet.getFilteringCriteria().add(filteringCriterion);
			}
		}
	}

	/**
	 * Remove associated features
	 * 
	 * @param melodyElement
	 * @param filteringCriterions
	 */
	public static void removeAssociatedCriteria(EObject melodyElement, List<FilteringCriterion> filteringCriterions) {
		// Get associated feature set
		AssociatedFilteringCriterionSet featureSet = FilteringUtils.getAssociatedFilteringCriterionSet(melodyElement);
		// Remove features
		if ((featureSet != null) && !filteringCriterions.isEmpty()) {
			featureSet.getFilteringCriteria().removeAll(filteringCriterions);
		}
		// Remove associated feature set if empty
		if ((featureSet != null) && featureSet.getFilteringCriteria().isEmpty()) {
			removeAssociatedFeatureSet(melodyElement);
		}
	}

	/**
	 * Remove associated features
	 * 
	 * @param melodyElement
	 * @param filteringCriterions
	 */
	public static void removeAssociatedFeatureSet(EObject melodyElement) {
		// Get associated feature set
		AssociatedFilteringCriterionSet featureSet = FilteringUtils.getAssociatedFilteringCriterionSet(melodyElement);
		if (featureSet != null) {
			// Remove it. Other elements, as transfo links, should be removed
			// also so we call CapellaDeleteCommand to be on
			// the safe side
			ArrayList<EObject> list = new ArrayList<EObject>();
			list.add(featureSet);
			CapellaDeleteCommand mdc = new CapellaDeleteCommand(TransactionHelper.getExecutionManager(melodyElement),
					list, true, false, true);
			if (mdc.canExecute()) {
				mdc.execute();
			}
		}
	}

	/**
	 * get the eclipse project {@link IProject} corresponding to the given
	 * EObject
	 * 
	 * @param eObject
	 * @return <code>null</code> whether selection does not fit any supported
	 *         case .
	 */
	public static IProject getEclipseProject(EObject eObject) {
		// We need to get the uri through the session to have cdo uri
		// compatibility
		Session session = SessionManager.INSTANCE.getSession(eObject);
		URI resURI = session.getSessionResource().getURI();
		IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(resURI.toPlatformString(true)));
		return file.getProject();
	}

	/**
	 * Get all the elements in the hierarchy of a given eObject that contains
	 * associated features
	 * 
	 * @param root
	 * @return list
	 */
	public static List<EObject> getAllElementsWithAssociatedCriteria(EObject root) {
		List<EObject> result = new ArrayList<EObject>();

		if (null != root) {
			if (hasAssociatedCriteria(root)) {
				result.add(root);
			}
			TreeIterator<EObject> it = root.eAllContents();
			EObject current = null;
			while (it.hasNext()) {
				current = it.next();
				if (hasAssociatedCriteria(current)) {
					result.add(current);
				}
			}
		}
		return result;
	}

	/**
	 * If the element has associated features
	 * 
	 * @param eObject
	 * @return boolean
	 */
	public static boolean hasAssociatedCriteria(EObject eObject) {
		if (FilteringUtils.getAssociatedCriteria(eObject).isEmpty()) {
			return false;
		}
		return true;
	}

	/**
	 * This method verifies if there is an active transaction.<br>
	 * If such case, the command is simply run.<br>
	 * If not, the command is executed through the execution manager.<br>
	 * 
	 * @param command
	 *            the command to be executed
	 * @param editingDomain
	 */
	public static void executeCommand(ICommand command, TransactionalEditingDomain editingDomain) {
		ExecutionManager executionManager = ExecutionManagerRegistry.getInstance().getExecutionManager(editingDomain);
		if (null != executionManager) {
			if (editingDomain instanceof InternalTransactionalEditingDomain) {
				InternalTransaction activeTransaction = ((InternalTransactionalEditingDomain) editingDomain)
						.getActiveTransaction();
				if ((null != activeTransaction) && activeTransaction.isActive()) {
					command.run();
				} else {
					executionManager.execute(command);
				}
			}
		}
	}

	/**
	 * This method verifies if there is an active transaction.<br>
	 * If such case, the command is simply run.<br>
	 * If not, the command is executed through the execution manager.<br>
	 * 
	 * @param command
	 *            the command to be executed
	 * @param eObject
	 */
	public static void executeCommand(ICommand command, EObject eObject) {
		executeCommand(command, TransactionHelper.getEditingDomain(eObject));
	}

	/**
	 * This method verifies if there is an active transaction.<br>
	 * If such case, the command is simply run.<br>
	 * If not, the command is executed through the execution manager.<br>
	 * 
	 * @param command
	 * @param session
	 */
	public static void executeCommand(AbstractReadWriteCommand command, Session session) {
		executeCommand(command, session.getTransactionalEditingDomain());
	}

	/**
	 * @param command
	 * @param elements
	 */
	public static void executeCommand(AbstractReadWriteCommand command, List<ModelElement> elements) {
		executeCommand(command, TransactionHelper.getEditingDomain(elements));
	}

	/**
	 * Some classes should not have associated feature sets
	 * 
	 * @param eObj
	 * @return true if it is excluded from having associated features
	 */
	public static boolean isInstanceOfFilteringExcludedElements(EObject eObj) {
		return eObj instanceof FilteringCriterionSet || eObj instanceof FilteringModel
				|| eObj instanceof FilteringResults || eObj instanceof FilteringCriterion
				|| eObj instanceof FilteringCriterionPkg || eObj instanceof FilteringResultPkg;

	}

	/**
	 * Get the semantic models of a project
	 * 
	 * @param project
	 * @return non null list
	 * @throws CoreException
	 */
	public static List<IFile> getSemanticModels(IProject project) throws CoreException {
		List<IFile> result = new ArrayList<IFile>();
		for (IResource resource : project.members()) {
			String ext = resource.getFileExtension();
			if (CapellaResourceHelper.CAPELLA_MODEL_FILE_EXTENSION.equals(ext)) {
				result.add((IFile) resource);
			}
		}
		return result;
	}

	/**
	 * Get the aird models of a project
	 * 
	 * @param project
	 * @return non null list
	 * @throws CoreException
	 */
	public static List<IFile> getAirdModels(IProject project) throws CoreException {
		List<IFile> result = new ArrayList<IFile>();
		for (IResource resource : project.members()) {
			String ext = resource.getFileExtension();
			if (CapellaResourceHelper.AIRD_FILE_EXTENSION.equals(ext)) {
				result.add((IFile) resource);
			}
		}
		return result;
	}

	/**
	 * Get the SuperSet of a list of Feature Sets
	 * 
	 * @param featureSets
	 * @return
	 */
	public static List<FilteringCriterion> getSuperSet(List<EObject> featureSets) {
		List<FilteringCriterion> featureList = new ArrayList<FilteringCriterion>();
		for (EObject featureSet : featureSets) {
			if (featureSet instanceof FilteringCriterionSet) {
				for (FilteringCriterion feature : ((FilteringCriterionSet) featureSet).getFilteringCriteria()) {
					if (!featureList.contains(feature)) {
						featureList.add(feature);
					}
				}
			}
		}
		return featureList;
	}

	/**
	 * Check if a configuration contains a given filtering feature
	 * 
	 * @param configuration
	 * @param filteringCriterion
	 * @return if a configuration contains a given filtering feature
	 */
	public static boolean isVariabilityFeatureInConfiguration(FilteringResult configuration,
			FilteringCriterion filteringCriterion) {
		if ((configuration != null) && (configuration.getFilteringCriteria() != null)) {
			return configuration.getFilteringCriteria().contains(filteringCriterion);
		}
		return false;
	}

	/**
	 * Refresh a given diagram.
	 * 
	 * 
	 * @param graphicalEditor
	 */
	public static void forceRefreshDiagram(GraphicalEditor graphicalEditor) {
		// Set focus refresh nodes
		graphicalEditor.setFocus();
		// But then we need to do this for Edges, otherwise it won't be
		// refreshed...
		Object adaptation = graphicalEditor.getAdapter(GraphicalViewer.class);
		if (adaptation != null) {
			GraphicalViewer gv = (GraphicalViewer) adaptation;
			for (Iterator<?> ite = gv.getEditPartRegistry().values().iterator(); ite.hasNext();) {
				EditPart editpart = (EditPart) ite.next();
				if (editpart instanceof ConnectionEditPart) {
					editpart.refresh();
				}
			}
		}
	}

	/**
	 * force the Refresh of all open Diagrams
	 */
	public static void forceRefreshAllDiagrams() {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if ((window != null) && (window.getActivePage() != null)) {
			IEditorReference[] editors = window.getActivePage().getEditorReferences();
			if (editors != null) {
				for (IEditorReference editor : editors) {
					IEditorPart editorPart = editor.getEditor(true);
					if (editorPart.getEditorSite() != null) {
						if (editorPart.getEditorSite().getId().equals(DDiagramEditor.EDITOR_ID)) {
							forceRefreshDiagram((GraphicalEditor) editorPart);
						}
					}
				}
			}
		}
	}

	/**
	 * force the Refresh of active diagram
	 */
	public static void forceRefreshActiveDiagram() {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window != null) {
			IWorkbenchPage workbenchPage = window.getActivePage();
			if (workbenchPage != null) {
				IEditorPart editorPart = workbenchPage.getActiveEditor();
				if (editorPart instanceof GraphicalEditor) {
					forceRefreshDiagram((GraphicalEditor) editorPart);
				}
			}
		}
	}

	/**
	 * Refresh all representations
	 * 
	 * @param session
	 * @param executionManager
	 */
	public static void refreshAllRepresentations(final Session session, ExecutionManager executionManager) {
		// Refresh all diagrams on session
		// See RefreshDiagramsCommandHandler if this refresh implementation
		// causes problems
		Collection<DRepresentation> representationsToRefresh = DialectManager.INSTANCE.getAllRepresentations(session);
		for (final DRepresentation representation : representationsToRefresh) {
			if (DialectManager.INSTANCE.canRefresh(representation)) {
				executionManager.execute(new AbstractNonDirtyingCommand() {
					@Override
					public void run() {
						DialectManager.INSTANCE.refresh(representation, true, new NullProgressMonitor());
						if (representation instanceof DDiagram) {
							synchronizeGMFModel(session, (DDiagram) representation);
						}
					}
				});
			}
		}
	}

	private static void synchronizeGMFModel(Session session, DDiagram dDiagram) {
		Diagram gmfDiagram = SiriusGMFHelper.getGmfDiagram(dDiagram, session);
		CanonicalSynchronizer canonicalSynchronizer = CanonicalSynchronizerFactory.INSTANCE
				.createCanonicalSynchronizer(gmfDiagram);
		canonicalSynchronizer.synchronize();
	}
}
