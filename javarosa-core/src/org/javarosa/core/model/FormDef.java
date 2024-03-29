/*
 * Copyright (C) 2009 JavaRosa
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.javarosa.core.model;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.NoSuchElementException;
import java.util.Vector;

import org.javarosa.core.model.condition.Condition;
import org.javarosa.core.model.condition.Constraint;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.condition.IConditionExpr;
import org.javarosa.core.model.condition.IFunctionHandler;
import org.javarosa.core.model.condition.Recalculate;
import org.javarosa.core.model.condition.Triggerable;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.SelectMultiData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.core.model.instance.DataModelTree;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.model.utils.QuestionPreloader;
import org.javarosa.core.services.locale.Localizable;
import org.javarosa.core.services.locale.Localizer;
import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapList;
import org.javarosa.core.util.externalizable.ExtWrapListPoly;
import org.javarosa.core.util.externalizable.ExtWrapNullable;
import org.javarosa.core.util.externalizable.PrototypeFactory;

/**
 * Definition of a form. This has some meta data about the form definition and a
 * collection of groups together with question branching or skipping rules.
 * 
 * @author Daniel Kayiwa, Drew Roos
 * 
 */
public class FormDef implements IFormElement, Localizable, Persistable, IMetaData {
	public static final String STORAGE_KEY = "FORMDEF";
	public static final int TEMPLATING_RECURSION_LIMIT = 10;

	private Vector children;// <IFormElement> 
	/** A collection of group definitions. */
	private int id;
	/** The numeric unique identifier of the form definition on the local device */
	private String title;
	/** The display title of the form. */
	private String name;
	/**
	 * A unique external name that is used to identify the form between machines
	 */
	private Localizer localizer;
	
	//images setname --> list of images in set
	private Hashtable<String, ArrayList<ImageItem>> iimageTable;
	
	public Vector triggerables; // <Triggerable>; this list is topologically ordered, meaning for any tA and tB in
	//the list, where tA comes before tB, evaluating tA cannot depend on any result from evaluating tB
	private boolean triggerablesInOrder; //true if triggerables has been ordered topologically (DON'T DELETE ME EVEN THOUGH I'M UNUSED)
	
	private DataModelTree model;
	private Vector outputFragments; // <IConditionExpr> contents of <output>
	// tags that serve as parameterized
	// arguments to captions

	public Hashtable triggerIndex; // <TreeReference, Vector<Triggerable>>
	private Hashtable conditionRepeatTargetIndex; // <TreeReference, Condition>;
	// associates repeatable
	// nodes with the Condition
	// that determines their
	// relevancy
	private EvaluationContext exprEvalContext;

	private QuestionPreloader preloader = new QuestionPreloader();

	/**
	 * 
	 */
	public FormDef() {
		setID(-1);
		setChildren(null);
		triggerables = new Vector();
		triggerablesInOrder = true;
		triggerIndex = new Hashtable();
		conditionRepeatTargetIndex = new Hashtable();
		setEvaluationContext(new EvaluationContext());
		outputFragments = new Vector();
	}

	
	// ---------- child elements
	public void addChild(IFormElement fe) {
		this.children.addElement(fe);
	}

	public IFormElement getChild(int i) {
		if (i < this.children.size())
			return (IFormElement) this.children.elementAt(i);

		throw new ArrayIndexOutOfBoundsException(
				"FormDef: invalid child index: " + i + " only "
						+ children.size() + " children");
	}

	public IFormElement getChild(FormIndex index) {
		IFormElement element = this;
		while (index != null) {
			element = element.getChild(index.getLocalIndex());
			index = index.getNextLevel();
		}
		return element;
	}

	/**
	 * Dereference the form index and return a Vector of all interstitial nodes
	 * (top-level parent first; index target last)
	 * 
	 * Ignore 'new-repeat' node for now; just return/stop at ref to
	 * yet-to-be-created repeat node (similar to repeats that already exist)
	 * 
	 * @param index
	 * @return
	 */
	public Vector explodeIndex(FormIndex index) {
		Vector indexes = new Vector();
		Vector multiplicities = new Vector();
		Vector elements = new Vector();

		collapseIndex(index, indexes, multiplicities, elements);
		return elements;
	}

	// take a reference, find the instance node it refers to (factoring in
	// multiplicities)
	/**
	 * @param index
	 * @return
	 */
	public TreeReference getChildInstanceRef(FormIndex index) {
		Vector indexes = new Vector();
		Vector multiplicities = new Vector();
		Vector elements = new Vector();

		collapseIndex(index, indexes, multiplicities, elements);
		return getChildInstanceRef(elements, multiplicities);
	}

	/**
	 * @param elements
	 * @param multiplicities
	 * @return
	 */
	public TreeReference getChildInstanceRef(Vector elements,
			Vector multiplicities) {
		if (elements.size() == 0)
			return null;

		// get reference for target element
		TreeReference ref = DataModelTree.unpackReference(
				((IFormElement) elements.lastElement()).getBind()).clone();
		for (int i = 0; i < ref.size(); i++) {
			ref.multiplicity.setElementAt(new Integer(0), i);
		}

		// fill in multiplicities for repeats along the way
		for (int i = 0; i < elements.size(); i++) {
			IFormElement temp = (IFormElement) elements.elementAt(i);
			if (temp instanceof GroupDef && ((GroupDef) temp).getRepeat()) {
				TreeReference repRef = DataModelTree.unpackReference(temp
						.getBind());
				if (repRef.isParentOf(ref, false)) {
					int repMult = ((Integer) multiplicities.elementAt(i))
							.intValue();
					ref.multiplicity.setElementAt(new Integer(repMult), repRef
							.size() - 1);
				} else {
					return null; // question/repeat hierarchy is not consistent
					// with instance model and bindings
				}
			}
		}

		return ref;
	}

	public void setLocalizer(Localizer l) {
		if (this.localizer != null) {
			this.localizer.unregisterLocalizable(this);
		}

		this.localizer = l;
		if (this.localizer != null) {
			this.localizer.registerLocalizable(this);
		}
	}
	

	public void setImageSource(Hashtable<String, ArrayList<ImageItem>> iimage){
		this.iimageTable = iimage;
		
	}

	// don't think this should ever be called(!)
	public IDataReference getBind() {
		throw new RuntimeException("method not implemented");
	}

	public void setValue(IAnswerData data, TreeReference ref) {
		setValue(data, ref, model.resolveReference(ref));
	}

	public void setValue(IAnswerData data, TreeReference ref, TreeElement node) {
		setAnswer(data, node);
		triggerTriggerables(ref);
	}

	public void setAnswer(IAnswerData data, TreeReference ref) {
		setAnswer(data, model.resolveReference(ref));
	}
	
	public void setAnswer(IAnswerData data, TreeElement node) {
		node.setAnswer(data);
	}
	
	/**
	 * Deletes the inner-most repeat that this node belongs to and returns the
	 * corresponding FormIndex. Behavior is currently undefined if you call this
	 * method on a node that is not contained within a repeat.
	 * 
	 * @param index
	 * @return
	 */
	public FormIndex deleteRepeat(FormIndex index) {
		Vector indexes = new Vector();
		Vector multiplicities = new Vector();
		Vector elements = new Vector();
		collapseIndex(index, indexes, multiplicities, elements);

		// loop backwards through the elements, removing objects from each
		// vector, until we find a repeat
		// TODO: should probably check to make sure size > 0
		for (int i = elements.size() - 1; i >= 0; i--) {
			IFormElement e = (IFormElement) elements.elementAt(i);
			if (e instanceof GroupDef && ((GroupDef) e).getRepeat()) {
				break;
			} else {
				indexes.removeElementAt(i);
				multiplicities.removeElementAt(i);
				elements.removeElementAt(i);
			}
		}

		// build new formIndex which includes everything
		// up to the node we're going to remove
		FormIndex newIndex = buildIndex(indexes, multiplicities, elements);

		TreeReference deleteRef = getChildInstanceRef(newIndex);
		TreeElement deleteElement = model.resolveReference(deleteRef);
		TreeReference parentRef = deleteRef.getParentRef();
		TreeElement parentElement = model.resolveReference(parentRef);

		int childMult = deleteElement.getMult();
		parentElement.removeChild(deleteElement);

		// update multiplicities of other child nodes
		Vector v = parentElement.getChildren();
		for (int i = 0; i < v.size(); i++) {
			TreeElement child = (TreeElement) v.elementAt(i);
			if (child.getMult() > childMult) {
				child.setMult(child.getMult() - 1);
			}
		}

		triggerTriggerables(parentRef);
		return newIndex;
	}

	public void createNewRepeat(FormIndex index) {
		TreeReference destRef = getChildInstanceRef(index);
		TreeElement template = model.getTemplate(destRef);

		model.copyNode(template, destRef);

		preloadModel(model.resolveReference(destRef));
		triggerTriggerables(destRef); // trigger conditions that depend on the
		// creation of this new node
		initializeTriggerables(destRef); // initialize conditions for the node
		// (and sub-nodes)
	}

	public boolean canCreateRepeat(TreeReference repeatRef) {
		Condition c = (Condition) conditionRepeatTargetIndex.get(repeatRef.genericize());
		if (c != null) {
			return c.evalBool(model, new EvaluationContext(exprEvalContext,	repeatRef));
		} /* else check # child constraints of parent
		
		} */

		return true;
	}

	/**
	 * Add a Condition to the form's Collection.
	 * 
	 * @param condition
	 *            the condition to be set
	 */
	public Triggerable addTriggerable(Triggerable t) {
		int existingIx = triggerables.indexOf(t);
		if (existingIx >= 0) {
			//one node may control access to many nodes; this means many nodes effectively have the same condition
			//let's identify when conditions are the same, and store and calculate it only once

			//note, if the contextRef is unnecessarily deep, the condition will be evaluated more times than needed
			//perhaps detect when 'identical' condition has a shorter contextRef, and use that one instead?
			return (Triggerable)triggerables.elementAt(existingIx);
		} else {
			triggerables.addElement(t);
			triggerablesInOrder = false;

			Vector triggers = t.getTriggers();
			for (int i = 0; i < triggers.size(); i++) {
				TreeReference trigger = (TreeReference) triggers.elementAt(i);
				if (!triggerIndex.containsKey(trigger)) {
					triggerIndex.put(trigger, new Vector());
				}
				Vector triggered = (Vector) triggerIndex.get(trigger);
				if (!triggered.contains(t)) {
					triggered.addElement(t);
				}
			}

			if (t instanceof Condition) {
				// droos 5/14: this this might be a bug? what if we encounter
				// the same condition again, but the targets
				// have since changed? we'll return the original condition
				// (above), and not update this index
				Vector targets = t.getTargets();
				for (int i = 0; i < targets.size(); i++) {
					TreeReference target = (TreeReference) targets.elementAt(i);
					if (model.getTemplate(target) != null) {
						conditionRepeatTargetIndex.put(target, (Condition) t);
					}
				}
			}

			return t;
		}
	}

	public void finalizeTriggerables () {
		Vector partialOrdering = new Vector();
		for (int i = 0; i < triggerables.size(); i++) {
			Triggerable t = (Triggerable)triggerables.elementAt(i);
			Vector deps = new Vector();
			
			if (t.canCascade()) {
				for (int j = 0; j < t.getTargets().size(); j++) {
					TreeReference target = (TreeReference)t.getTargets().elementAt(j);
					Vector triggered = (Vector)triggerIndex.get(target);
					if (triggered != null) {
						for (int k = 0; k < triggered.size(); k++) {
							Triggerable u = (Triggerable)triggered.elementAt(k);
							if (!deps.contains(u))
								deps.addElement(u);
						}
					}
				}
			}
			
			for (int j = 0; j < deps.size(); j++) {
				Triggerable u = (Triggerable)deps.elementAt(j);
				Triggerable[] edge = {t, u};
				partialOrdering.addElement(edge);
			}
		}
		
		Vector vertices = new Vector();
		for (int i = 0; i < triggerables.size(); i++)
			vertices.addElement(triggerables.elementAt(i));
		triggerables.removeAllElements();
		
		while (vertices.size() > 0) {
			//determine root nodes
			Vector roots = new Vector();
			for (int i = 0; i < vertices.size(); i++) {
				roots.addElement(vertices.elementAt(i));
			}
			for (int i = 0; i < partialOrdering.size(); i++) {
				Triggerable[] edge = (Triggerable[])partialOrdering.elementAt(i);
				roots.removeElement(edge[1]);
			}
			
			//if no root nodes while graph still has nodes, graph has cycles
			if (roots.size() == 0) {
				throw new RuntimeException("Cannot create partial ordering of triggerables due to dependency cycle. Why wasn't this caught during parsing?");
			}

			//remove root nodes and edges originating from them
			for (int i = 0; i < roots.size(); i++) {
				Triggerable root = (Triggerable)roots.elementAt(i);
				triggerables.addElement(root);
				vertices.removeElement(root);
			}			
			for (int i = partialOrdering.size() - 1; i >= 0; i--) {
				Triggerable[] edge = (Triggerable[])partialOrdering.elementAt(i);
				if (roots.contains(edge[0]))
					partialOrdering.removeElementAt(i);
			}
		}
		
		triggerablesInOrder = true;
	}
	
	public void initializeTriggerables() {
		initializeTriggerables(TreeReference.rootRef());
	}

	/**
	 * Walks the current set of conditions, and evaluates each of them with the
	 * current context.
	 */
	private void initializeTriggerables(TreeReference rootRef) {
		TreeReference genericRoot = rootRef.genericize();

		Vector applicable = new Vector();
		for (int i = 0; i < triggerables.size(); i++) {
			Triggerable t = (Triggerable)triggerables.elementAt(i);
			for (int j = 0; j < t.getTargets().size(); j++) {
				TreeReference target = (TreeReference)t.getTargets().elementAt(j);
				if (genericRoot.isParentOf(target, false)) {
					applicable.addElement(t);
					break;
				}
			}
		}
		
		evaluateTriggerables(applicable, rootRef);
	}
	
	// ref: unambiguous ref of node that just changed
	public void triggerTriggerables(TreeReference ref) {
		// turn unambiguous ref into a generic ref
		TreeReference genericRef = ref.genericize();

		// get conditions triggered by this node
		Vector triggered = (Vector)triggerIndex.get(genericRef);
		if (triggered == null)
			return;

		Vector triggeredCopy = new Vector();
		for (int i = 0; i < triggered.size(); i++)
			triggeredCopy.addElement(triggered.elementAt(i));
		evaluateTriggerables(triggeredCopy, ref);
	}

	private void evaluateTriggerables(Vector tv, TreeReference anchorRef) {
		//add all cascaded triggerables to queue
		for (int i = 0; i < tv.size(); i++) {
			Triggerable t = (Triggerable)tv.elementAt(i);
			if (t.canCascade()) {
				for (int j = 0; j < t.getTargets().size(); j++) {
					TreeReference target = (TreeReference)t.getTargets().elementAt(j);
					Vector triggered = (Vector)triggerIndex.get(target);
					if (triggered != null) {
						for (int k = 0; k < triggered.size(); k++) {
							Triggerable u = (Triggerable)triggered.elementAt(k);
							if (!tv.contains(u))
								tv.addElement(u);
						}
					}
				}
			}
		}
		
		//'triggerables' is topologically-ordered by dependencies, so evaluate the triggerables in 'tv'
		//in the order they appear in 'triggerables'
		for (int i = 0; i < triggerables.size(); i++) {
			Triggerable t = (Triggerable)triggerables.elementAt(i);
			if (tv.contains(t)) {
				evaluateTriggerable(t, anchorRef);
			}
		}
	}
	
	private void evaluateTriggerable(Triggerable t, TreeReference anchorRef) {
		TreeReference contextRef = t.contextRef.contextualize(anchorRef);
		Vector v = model.expandReference(contextRef);
		for (int i = 0; i < v.size(); i++) {
			EvaluationContext ec = new EvaluationContext(exprEvalContext, (TreeReference)v.elementAt(i));
			t.apply(model, ec, this);
		}
	}

	public boolean evaluateConstraint(TreeReference ref, IAnswerData data) {
		if (data == null)
			return true;

		TreeElement node = model.resolveReference(ref);
		Constraint c = node.getConstraint();
		if (c == null)
			return true;

		EvaluationContext ec = new EvaluationContext(exprEvalContext, ref);
		ec.isConstraint = true;
		ec.candidateValue = data;

		return c.constraint.eval(model, ec);
	}

	/**
	 * @param ec
	 *            The new Evaluation Context
	 */
	public void setEvaluationContext(EvaluationContext ec) {
		initEvalContext(ec);
		this.exprEvalContext = ec;
	}

	private void initEvalContext(EvaluationContext ec) {
		if (!ec.getFunctionHandlers().containsKey("jr:itext")) {
			final FormDef f = this;
			ec.addFunctionHandler(new IFunctionHandler() {
				public String getName() {
					return "jr:itext";
				}

				public Object eval(Object[] args) {
					String textID = (String) args[0];
					try {
						String text = f.getLocalizer().getText(textID);
						return text == null ? "[itext:" + textID + "]" : text;
					} catch (NoSuchElementException nsee) {
						return "[nolocale]";
					}
				}

				public Vector getPrototypes() {
					Class[] proto = { String.class };
					Vector v = new Vector();
					v.addElement(proto);
					return v;
				}

				public boolean rawArgs() {
					return false;
				}

				public boolean realTime() {
					return false;
				}
			});
		}
	}

	public String fillTemplateString(String template, TreeReference contextRef) {
		Hashtable args = new Hashtable();

		int depth = 0;
		Vector outstandingArgs = Localizer.getArgs(template);
		while (outstandingArgs.size() > 0) {
			for (int i = 0; i < outstandingArgs.size(); i++) {
				String argName = (String) outstandingArgs.elementAt(i);
				if (!args.containsKey(argName)) {
					int ix = -1;
					try {
						ix = Integer.parseInt(argName);
					} catch (NumberFormatException nfe) {
						System.err
								.println("Warning: expect arguments to be numeric ["
										+ argName + "]");
					}

					if (ix < 0 || ix >= outputFragments.size())
						continue;

					IConditionExpr expr = (IConditionExpr) outputFragments
							.elementAt(ix);
					String value = expr.evalReadable(this.getDataModel(),
							new EvaluationContext(exprEvalContext, contextRef));
					args.put(argName, value);
				}
			}

			template = Localizer.processArguments(template, args);
			outstandingArgs = Localizer.getArgs(template);

			depth++;
			if (depth >= TEMPLATING_RECURSION_LIMIT) {
				throw new RuntimeException(
						"Dependency cycle in <output>s; recursion limit exceeded!!");
			}
		}

		return template;
	}

	/**
	 * Note that this method doesn't yet deal with groups in any way, and will
	 * fail if this form contains any groups.
	 * 
	 * @param questionID
	 *            the unique integer ID of the question.
	 * @return A quesiton associated with the given ID.
	 */
	// public QuestionDef getQuesitonByID (int questionID) {
	// for (int i = 0; i < children.size(); i++) {
	// QuestionDef q = (QuestionDef)children.elementAt(i);
	// if (questionID == q.getID()) {
	// return q;
	// }
	// }
	//		
	// return null;
	// }
	/**
	 * @return the preloads
	 */
	public QuestionPreloader getPreloader() {
		return preloader;
	}

	/**
	 * @param preloads
	 *            the preloads to set
	 */
	public void setPreloader(QuestionPreloader preloads) {
		this.preloader = preloads;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.javarosa.core.model.utils.Localizable#localeChanged(java.lang.String,
	 * org.javarosa.core.model.utils.Localizer)
	 */
	public void localeChanged(String locale, Localizer localizer) {
		for (Enumeration e = children.elements(); e.hasMoreElements();) {
			((IFormElement) e.nextElement()).localeChanged(locale, localizer);
		}
	}

	public String toString() {
		return getTitle();
	}

	/**
	 * Preload the Data Model with the preload values that are enumerated in the
	 * data bindings.
	 */
	public void preloadModel(TreeElement node) {
		// if (node.isLeaf()) {
		IAnswerData preload = null;
		if (node.getPreloadHandler() != null) {
			preload = preloader.getQuestionPreload(node.getPreloadHandler(),
					node.getPreloadParams());
		}
		if (preload != null) { // what if we want to wipe out a value in the
			// instance?
			node.setAnswer(preload);
		}
		// } else {
		if (!node.isLeaf()) {
			for (int i = 0; i < node.getNumChildren(); i++) {
				TreeElement child = (TreeElement) node.getChildren().elementAt(
						i);
				if (child.getMult() != TreeReference.INDEX_TEMPLATE) // don't
					// preload
					// templates;
					// new
					// repeats
					// are
					// preloaded
					// as
					// they're
					// created
					preloadModel(child);
			}
		}
		// }
	}

	public boolean postProcessModel() {
		return postProcessModel(model.getRoot());
	}

	/**
	 * Iterate over the form's data bindings, and evaluate all post procesing
	 * calls.
	 * 
	 * @return true if the model was modified in any way. false otherwise.
	 */
	private boolean postProcessModel(TreeElement node) {
		// we might have issues with ordering, for example, a handler that
		// writes a value to a node,
		// and a handler that does something external with the node. if both
		// handlers are bound to the
		// same node, we need to make sure the one that alters the node executes
		// first. deal with that later.
		// can we even bind multiple handlers to the same node currently?

		// also have issues with conditions. it is hard to detect what
		// conditions are affected by the actions
		// of the post-processor. normally, it wouldn't matter because we only
		// post-process when we are exiting
		// the form, so the result of any triggered conditions is irrelevant.
		// however, if we save a form in the
		// interim, post-processing occurs, and then we continue to edit the
		// form. it seems like having conditions
		// dependent on data written during post-processing is a bad practice
		// anyway, and maybe we shouldn't support it.

		if (node.isLeaf()) {
			if (node.getPreloadHandler() != null) {
				return preloader.questionPostProcess(node, node
						.getPreloadHandler(), node.getPreloadParams());
			} else {
				return false;
			}
		} else {
			boolean modelModified = false;
			for (int i = 0; i < node.getNumChildren(); i++) {
				TreeElement child = (TreeElement) node.getChildren().elementAt(
						i);
				if (child.getMult() != TreeReference.INDEX_TEMPLATE)
					modelModified |= postProcessModel(child);
			}
			return modelModified;
		}
	}

	/**
	 * Reads the form definition object from the supplied stream.
	 * 
	 * Requires that the model has been set to a prototype of the model that
	 * should be used for deserialization.
	 * 
	 * @param dis
	 *            - the stream to read from.
	 * @throws IOException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 */
	public void readExternal(DataInputStream dis, PrototypeFactory pf) throws IOException, DeserializationException {
		setID(ExtUtil.readInt(dis));
		setName(ExtUtil.nullIfEmpty(ExtUtil.readString(dis)));
		setTitle((String) ExtUtil.read(dis, new ExtWrapNullable(String.class), pf));
		setChildren((Vector) ExtUtil.read(dis, new ExtWrapListPoly(), pf));

		model = (DataModelTree) ExtUtil.read(dis, DataModelTree.class, pf);
		model.setFormId(getID());

		setLocalizer((Localizer) ExtUtil.read(dis, new ExtWrapNullable(Localizer.class), pf));

		Vector vcond = (Vector) ExtUtil.read(dis, new ExtWrapList(Condition.class), pf);
		for (Enumeration e = vcond.elements(); e.hasMoreElements(); )
			addTriggerable((Condition) e.nextElement());
		Vector vcalc = (Vector) ExtUtil.read(dis, new ExtWrapList(Recalculate.class), pf);
		for (Enumeration e = vcalc.elements(); e.hasMoreElements();)
			addTriggerable((Recalculate) e.nextElement());
		finalizeTriggerables();
		
		outputFragments = (Vector) ExtUtil.read(dis, new ExtWrapListPoly(), pf);
	}

	/**
	 * meant to be called after deserialization and initialization of handlers
	 * 
	 * @param newInstance
	 *            true if the form is to be used for a new entry interaction,
	 *            false if it is using an existing IDataModel
	 */
	public void initialize(boolean newInstance) {
		fixSelectQuestionDeserialization();

		if (newInstance) {// only preload new forms (we may have to revisit
			// this)
			preloadModel(model.getRoot());
		}

		initializeTriggerables();

		if (getLocalizer() != null && getLocalizer().getLocale() == null) {
			getLocalizer().setToDefault();
		}
	}

	private void fixSelectQuestionDeserialization() {
		Hashtable questionMapping = new Hashtable();
		genSelectQuestionMapping(this, questionMapping);
		fixSelectQuestionDeserialization(model.getRoot(), questionMapping);
	}

	private void fixSelectQuestionDeserialization(TreeElement node,
			Hashtable questionMapping) {
		IAnswerData data = node.getValue();
		if (data == null) {
			for (int i = 0; i < node.getNumChildren(); i++)
				fixSelectQuestionDeserialization((TreeElement) node
						.getChildren().elementAt(i), questionMapping);
		} else if (data instanceof SelectOneData
				|| data instanceof SelectMultiData) {
			Vector selections;
			if (data instanceof SelectOneData) {
				selections = new Vector();
				selections.addElement((Selection) data.getValue());
			} else {
				selections = (Vector) data.getValue();
			}

			for (int i = 0; i < selections.size(); i++) {
				Selection s = (Selection) selections.elementAt(i);

				int qID = s.qID;
				QuestionDef properQ = (QuestionDef) questionMapping
						.get(new Integer(qID));
				if (properQ == null) {
					throw new RuntimeException(
							"Error: cannot find referenced question def for select answer data");
				}
				s.question = properQ;
			}
		}
	}

	private void genSelectQuestionMapping(IFormElement fe, Hashtable mapping) {
		if (fe instanceof QuestionDef) {
			QuestionDef q = (QuestionDef) fe;
			if (q.getControlType() == Constants.CONTROL_SELECT_ONE
					|| q.getControlType() == Constants.CONTROL_SELECT_MULTI) {
				mapping.put(new Integer(q.getID()), q);
			}
		} else {
			for (int i = 0; i < fe.getChildren().size(); i++) {
				genSelectQuestionMapping(fe.getChild(i), mapping);
			}
		}
	}

	/**
	 * Writes the form definition object to the supplied stream.
	 * 
	 * @param dos
	 *            - the stream to write to.
	 * @throws IOException
	 */
	public void writeExternal(DataOutputStream dos) throws IOException {
		ExtUtil.writeNumeric(dos, getID());
		ExtUtil.writeString(dos, ExtUtil.emptyIfNull(getName()));
		ExtUtil.write(dos, new ExtWrapNullable(getTitle()));
		ExtUtil.write(dos, new ExtWrapListPoly(getChildren()));
		ExtUtil.write(dos, model);
		ExtUtil.write(dos, new ExtWrapNullable(localizer));

		Vector conditions = new Vector();
		Vector recalcs = new Vector();
		for (int i = 0; i < triggerables.size(); i++) {
			Triggerable t = (Triggerable) triggerables.elementAt(i);
			if (t instanceof Condition) {
				conditions.addElement(t);
			} else if (t instanceof Recalculate) {
				recalcs.addElement(t);
			}
		}
		ExtUtil.write(dos, new ExtWrapList(conditions));
		ExtUtil.write(dos, new ExtWrapList(recalcs));

		ExtUtil.write(dos, new ExtWrapListPoly(outputFragments));
	}

	public void collapseIndex(FormIndex index, Vector indexes,
			Vector multiplicities, Vector elements) {
		if (!index.isInForm()) {
			return;
		}

		IFormElement element = this;
		while (index != null) {
			int i = index.getLocalIndex();
			element = element.getChild(i);

			indexes.addElement(new Integer(i));
			multiplicities.addElement(new Integer(
					index.getInstanceIndex() == -1 ? 0 : index
							.getInstanceIndex()));
			elements.addElement(element);

			index = index.getNextLevel();
		}
	}

	public FormIndex buildIndex(Vector indexes, Vector multiplicities,
			Vector elements) {
		FormIndex cur = null;
		for (int i = indexes.size() - 1; i >= 0; i--) {
			int ix = ((Integer) indexes.elementAt(i)).intValue();
			int mult = ((Integer) multiplicities.elementAt(i)).intValue();
			if (!(elements.elementAt(i) instanceof GroupDef && ((GroupDef) elements
					.elementAt(i)).getRepeat())) {
				mult = -1;
			}

			cur = new FormIndex(cur, ix, mult);
		}
		return cur;
	}

	public FormIndex incrementIndex(FormIndex index) {
		Vector indexes = new Vector();
		Vector multiplicities = new Vector();
		Vector elements = new Vector();

		if (index.isEndOfFormIndex()) {
			return index;
		} else if (index.isBeginningOfFormIndex()) {
			if (children == null || children.size() == 0) {
				return FormIndex.createEndOfFormIndex();
			}
		} else {
			collapseIndex(index, indexes, multiplicities, elements);
		}

		incrementHelper(indexes, multiplicities, elements);

		if (indexes.size() == 0) {
			return FormIndex.createEndOfFormIndex();
		} else {
			return buildIndex(indexes, multiplicities, elements);
		}
	}

	private void incrementHelper(Vector indexes, Vector multiplicities,
			Vector elements) {
		int i = indexes.size() - 1;
		boolean exitRepeat = false;

		if (i == -1 || elements.elementAt(i) instanceof GroupDef) {
			// current index is group or repeat or the top-level form
			boolean descend = true;

			if (i >= 0) {
				// find out whether we're on a repeat, and if so, whether the
				// specified instance actually exists
				GroupDef group = (GroupDef) elements.elementAt(i);
				if (group.getRepeat()) {
					if (model.resolveReference(getChildInstanceRef(elements,
							multiplicities)) == null) {
						descend = false; // repeat instance does not exist; do
						// not descend into it
						exitRepeat = true;
					}
				}
			}

			if (descend) {
				indexes.addElement(new Integer(0));
				multiplicities.addElement(new Integer(0));
				elements.addElement((i == -1 ? this : (IFormElement) elements
						.elementAt(i)).getChild(0));
				return;
			}
		}

		while (i >= 0) {
			// if on repeat, increment to next repeat EXCEPT when we're on a
			// repeat instance that does not exist and was not created
			// (repeat-not-existing can only happen at lowest level; exitRepeat
			// will be true)
			if (!exitRepeat && elements.elementAt(i) instanceof GroupDef
					&& ((GroupDef) elements.elementAt(i)).getRepeat()) {
				multiplicities.setElementAt(
						new Integer(((Integer) multiplicities.elementAt(i))
								.intValue() + 1), i);
				return;
			}

			IFormElement parent = (i == 0 ? this : (IFormElement) elements
					.elementAt(i - 1));
			int curIndex = ((Integer) indexes.elementAt(i)).intValue();

			// increment to the next element on the current level
			if (curIndex + 1 >= parent.getChildren().size()) {
				// at the end of the current level; move up one level and start
				// over
				indexes.removeElementAt(i);
				multiplicities.removeElementAt(i);
				elements.removeElementAt(i);
				i--;
				exitRepeat = false;
			} else {
				indexes.setElementAt(new Integer(curIndex + 1), i);
				multiplicities.setElementAt(new Integer(0), i);
				elements.setElementAt(parent.getChild(curIndex + 1), i);
				return;
			}
		}
	}

	public FormIndex decrementIndex(FormIndex index) {
		Vector indexes = new Vector();
		Vector multiplicities = new Vector();
		Vector elements = new Vector();

		if (index.isBeginningOfFormIndex()) {
			return index;
		} else if (index.isEndOfFormIndex()) {
			if (children == null || children.size() == 0) {
				return FormIndex.createBeginningOfFormIndex();
			}
		} else {
			collapseIndex(index, indexes, multiplicities, elements);
		}

		decrementHelper(indexes, multiplicities, elements);

		if (indexes.size() == 0) {
			return FormIndex.createBeginningOfFormIndex();
		} else {
			return buildIndex(indexes, multiplicities, elements);
		}
	}

	private void decrementHelper(Vector indexes, Vector multiplicities,
			Vector elements) {
		int i = indexes.size() - 1;

		if (i != -1) {
			int curIndex = ((Integer) indexes.elementAt(i)).intValue();
			int curMult = ((Integer) multiplicities.elementAt(i)).intValue();

			if (curMult > 0) {
				// set node to previous repetition of current element
				multiplicities.setElementAt(new Integer(curMult - 1), i);
			} else if (curIndex > 0) {
				// set node to previous element
				indexes.setElementAt(new Integer(curIndex - 1), i);
				multiplicities.setElementAt(new Integer(0), i);
				elements.setElementAt((i == 0 ? this : (IFormElement) elements
						.elementAt(i - 1)).getChild(curIndex - 1), i);

				if (setRepeatNextMultiplicity(elements, multiplicities))
					return;
			} else {
				// at absolute beginning of current level; index to parent
				indexes.removeElementAt(i);
				multiplicities.removeElementAt(i);
				elements.removeElementAt(i);
				return;
			}
		}

		IFormElement element = (i < 0 ? this : (IFormElement) elements
				.elementAt(i));
		while (!(element instanceof QuestionDef)) {
			int subIndex = element.getChildren().size() - 1;
			element = element.getChild(subIndex);

			indexes.addElement(new Integer(subIndex));
			multiplicities.addElement(new Integer(0));
			elements.addElement(element);

			if (setRepeatNextMultiplicity(elements, multiplicities))
				return;
		}
	}

	private boolean setRepeatNextMultiplicity(Vector elements,
			Vector multiplicities) {
		// find out if node is repeatable
		TreeReference nodeRef = getChildInstanceRef(elements, multiplicities);
		TreeElement node = model.resolveReference(nodeRef);
		if (node == null || node.repeatable) { // node == null if there are no
			// instances of the repeat
			int mult;
			if (node == null) {
				mult = 0; // no repeats; next is 0
			} else {
				String name = node.getName();
				TreeElement parentNode = model.resolveReference(nodeRef
						.getParentRef());
				mult = parentNode.getChildMultiplicity(name);
			}
			multiplicities.setElementAt(new Integer(mult), multiplicities
					.size() - 1);
			return true;
		} else {
			return false;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.javarosa.core.model.IFormElement#getDeepChildCount()
	 */
	public int getDeepChildCount() {
		int total = 0;
		Enumeration e = children.elements();
		while (e.hasMoreElements()) {
			total += ((IFormElement) e.nextElement()).getDeepChildCount();
		}
		return total;
	}

	public void registerStateObserver(FormElementStateListener qsl) {
		// NO. (Or at least not yet).
	}

	public void unregisterStateObserver(FormElementStateListener qsl) {
		// NO. (Or at least not yet).
	}

	public Vector getChildren() {
		return children;
	}

	public void setChildren(Vector children) {
		this.children = (children == null ? new Vector() : children);
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public int getID() {
		return id;
	}

	public void setID(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	//returns the iimage hashtable
	public Hashtable<String, ArrayList<ImageItem>> getImageSource()
	{
		return iimageTable;
	}
	
	
	
	public Localizer getLocalizer() {
		return localizer;
	}

	public DataModelTree getDataModel() {
		return model;
	}

	public void setDataModel(IFormDataModel model) {
		this.model = (DataModelTree) model;
	}

	public Vector getOutputFragments() {
		return outputFragments;
	}

	public void setOutputFragments(Vector outputFragments) {
		this.outputFragments = outputFragments;
	}

	public Hashtable getMetaData() {
		Hashtable metadata = new Hashtable();
		String[] fields = getMetaDataFields();
		for (int i = 0; i < fields.length; i++) {
			metadata.put(fields[i], getMetaData(fields[i]));
		}
		return metadata;
	}

	public Object getMetaData(String fieldName) {
		if (fieldName.equals("DESCRIPTOR")) {
			return name;
		} else {
			throw new IllegalArgumentException();
		}
	}

	public String[] getMetaDataFields() {
		return new String[] {"DESCRIPTOR"};
	}
	
}
