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

package org.javarosa.core.model.util.restorable;

import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.instance.DataModelTree;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.transport.IDataPayload;

public interface IXFormyFactory {
	TreeReference ref (String refStr);
	IDataPayload serializeModel (DataModelTree dm);
	DataModelTree parseRestore (byte[] data, Class restorableType);
	IAnswerData parseData (String textVal, int dataType, TreeReference ref, FormDef f);
}