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

package org.javarosa.core.services.transport;

import org.javarosa.core.util.externalizable.Externalizable;

/**
 * A Transport Destination contains all of the information required to
 * deliver a message to another device. In the case of a URL, this
 * might be as simple as a String, but may contain more structured
 * information in the case of an SMS (Number/Port) or bluetooth 
 * partnership.
 * 
 * 
 * @author Clayton Sims
 *
 */
public interface ITransportDestination extends Externalizable {

}
