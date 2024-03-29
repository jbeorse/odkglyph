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

package org.javarosa.core.model.data;

import org.javarosa.core.util.externalizable.Externalizable;

/**
 * An IAnswerData object represents an answer to a question
 * posed to a user.
 * 
 * IAnswerData objects should never in any circumstances contain
 * a null data value. In cases of empty or non-existent responses,
 * the IAnswerData reference should itself be null.
 *  
 * @author Drew Roos
 *
 */
public interface IAnswerData extends Externalizable {
	/**
	 * @param o the value of this answerdata object. Cannot be null.
	 * Null Data will not overwrite existing values.
	 * @throws NullPointerException if o is null
	 */
	void setValue (Object o); //can't be null
	/**
	 * @return The value of this answer, will never
	 * be null
	 */
	Object getValue ();       //will never be null
	/**
	 * @return Gets a string representation of this 
	 * answer
	 */
	String getDisplayText ();
	
	IAnswerData clone ();
}
