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

package org.javarosa.model.xform;

import java.io.IOException;

import org.javarosa.core.api.IModule;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.instance.DataModelTree;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.model.util.restorable.IXFormyFactory;
import org.javarosa.core.model.util.restorable.RestoreUtils;
import org.javarosa.core.services.PrototypeManager;
import org.javarosa.core.services.transport.IDataPayload;
import org.javarosa.xform.parse.XFormParser;
import org.javarosa.xform.util.XFormAnswerDataParser;
import org.javarosa.xpath.XPathParseTool;

public class XFormsModule implements IModule {

	public void registerModule() {
		String[] classes = {
				"org.javarosa.model.xform.XPathReference",
				"org.javarosa.xpath.XPathConditional"
		};
		
		PrototypeManager.registerPrototypes(classes);
		PrototypeManager.registerPrototypes(XPathParseTool.xpathClasses);
		RestoreUtils.xfFact = new IXFormyFactory () {
			public TreeReference ref (String refStr) {
				return DataModelTree.unpackReference(new XPathReference(refStr));
			}
			
			public IDataPayload serializeModel (DataModelTree dm) {
				try {
					return (new XFormSerializingVisitor()).createSerializedPayload(dm);
				} catch (IOException e) {
					return null;
				}
			}

			public DataModelTree parseRestore(byte[] data, Class restorableType) {
				return XFormParser.restoreDataModel(data, restorableType);
			}
			
			public IAnswerData parseData (String textVal, int dataType, TreeReference ref, FormDef f) {
				return XFormAnswerDataParser.getAnswerData(textVal, dataType, XFormParser.ghettoGetQuestionDef(dataType, f, ref));
			}
		};
	}

}
