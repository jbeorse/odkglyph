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

import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


/**
 * A response to a question requesting an GeoPoint Value.
 * 
 * @author Yaw Anokwa
 * 
 */
public class GeoPointData implements IAnswerData {

    private double[] gp = new double[4];
    private int len = 2;


    /**
     * Empty Constructor, necessary for dynamic construction during
     * deserialization. Shouldn't be used otherwise.
     */
    public GeoPointData() {

    }


    public GeoPointData(double[] gp) {
        this.fillArray(gp);
    }


    private void fillArray(double[] gp) {
        len = gp.length;
        for (int i = 0; i < len; i++) {
            this.gp[i] = gp[i];
        }
    }


    public IAnswerData clone() {
        return new GeoPointData(gp);
    }


    /*
     * (non-Javadoc)
     * 
     * @see org.javarosa.core.model.data.IAnswerData#getDisplayText()
     */
    public String getDisplayText() {
        String s = "";
        for (int i = 0; i < len; i++) {
            s += gp[i] + " ";
        }
        return s.trim();

    }


    /*
     * (non-Javadoc)
     * 
     * @see org.javarosa.core.model.data.IAnswerData#getValue()
     */
    public Object getValue() {
        return gp;
    }


    public void setValue(Object o) {
        if (o == null) {
            throw new NullPointerException("Attempt to set an IAnswerData class to null.");
        }
        this.fillArray((double[]) o);
    }


    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException,
            DeserializationException {
        len = (int) ExtUtil.readNumeric(in);
        for (int i = 0; i < len; i++) {
            gp[i] = ExtUtil.readDecimal(in);
        }
    }


    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeNumeric(out, len);
        for (int i = 0; i < len; i++) {
            ExtUtil.writeDecimal(out, gp[i]);
        }
    }
}
