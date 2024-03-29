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

package org.javarosa.xpath.expr;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import me.regexp.RE;

import org.javarosa.core.model.IFormDataModel;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.condition.IFunctionHandler;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapListPoly;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.xpath.IExprDataType;
import org.javarosa.xpath.XPathTypeMismatchException;
import org.javarosa.xpath.XPathUnhandledException;

public class XPathFuncExpr extends XPathExpression {
	public XPathQName id;
	public XPathExpression[] args;

	public XPathFuncExpr () { } //for deserialization
	
	public XPathFuncExpr (XPathQName id, XPathExpression[] args) {
		this.id = id;
		this.args = args;
	}
	
	public String toString () {
		StringBuffer sb = new StringBuffer();
		
		sb.append("{func-expr:");	
		sb.append(id.toString());
		sb.append(",{");
		for (int i = 0; i < args.length; i++) {
			sb.append(args[i].toString());
			if (i < args.length - 1)
				sb.append(",");
		}
		sb.append("}}");
		
		return sb.toString();
	}
	
	public boolean equals (Object o) {
		if (o instanceof XPathFuncExpr) {
			XPathFuncExpr x = (XPathFuncExpr)o;
			
			//Shortcuts for very easily comprable values
			if(!id.equals(x.id) || args.length != x.args.length) {
				return false;
			}
			
			return ExtUtil.arrayEquals(args, x.args);
		} else {
			return false;
		}
	}
	
	public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
		id = (XPathQName)ExtUtil.read(in, XPathQName.class);
		Vector v = (Vector)ExtUtil.read(in, new ExtWrapListPoly(), pf);
		
		args = new XPathExpression[v.size()];
		for (int i = 0; i < args.length; i++)
			args[i] = (XPathExpression)v.elementAt(i);		
	}

	public void writeExternal(DataOutputStream out) throws IOException {
		Vector v = new Vector();
		for (int i = 0; i < args.length; i++)
			v.addElement(args[i]);

		ExtUtil.write(out, id);
		ExtUtil.write(out, new ExtWrapListPoly(v));
	}
	
	public Object eval (IFormDataModel model, EvaluationContext evalContext) {
		String name = id.toString();
		Object[] argVals = new Object[args.length];
		
		Hashtable funcHandlers = evalContext.getFunctionHandlers();
		
		for (int i = 0; i < args.length; i++) {
			argVals[i] = args[i].eval(model, evalContext);
		}
		
		if (name.equals("true") && args.length == 0) {
			return Boolean.TRUE;
		} else if (name.equals("false") && args.length == 0) {
			return Boolean.FALSE;
		} else if (name.equals("boolean") && args.length == 1) {
			return toBoolean(argVals[0]);
		} else if (name.equals("number") && args.length == 1) {
			return toNumeric(argVals[0]);
		} else if (name.equals("string") && args.length == 1) {
			return toString(argVals[0]);			
		} else if (name.equals("date") && args.length == 1) { //non-standard
			return toDate(argVals[0]);				
		} else if (name.equals("not") && args.length == 1) {
			return boolNot(argVals[0]);
		} else if (name.equals("boolean-from-string") && args.length == 1) {
			return boolStr(argVals[0]);
		} else if (name.equals("if") && args.length == 3) { //non-standard
			return ifThenElse(argVals[0], argVals[1], argVals[2]);	
		} else if ((name.equals("selected") || name.equals("is-selected")) && args.length == 2) { //non-standard
			return multiSelected(argVals[0], argVals[1]);
		} else if (name.equals("count-selected") && args.length == 1) { //non-standard
			return countSelected(argVals[0]);		
		} else if (name.equals("count") && args.length == 1) {
			return count(argVals[0]);
		} else if (name.equals("sum") && args.length == 1) {
			return sum(model, argVals[0]);
		} else if (name.equals("today") && args.length == 0) {
			return DateUtils.roundDate(new Date());
		} else if (name.equals("now") && args.length == 0) {
			return new Date();
		} else if (name.equals("concat")) {
			return concat(argVals);
		} else if (name.equals("checklist") && args.length >= 2) { //non-standard
			return checklist(argVals);
		} else if (name.equals("weighted-checklist") && args.length >= 2 && args.length % 2 == 0) { //non-standard
			return checklistWeighted(argVals);
		} else if (name.equals("regex") && args.length == 2) {
			return regex(argVals[0], argVals[1]);
		} else {
			IFunctionHandler handler = (IFunctionHandler)funcHandlers.get(name);
			if (handler != null) {
				return evalCustomFunction(handler, argVals);
			} else {
				throw new XPathUnhandledException("function \'" + name + "\'");
			}
		}
	}
	
	private Object evalCustomFunction (IFunctionHandler handler, Object[] args) {
		Vector prototypes = handler.getPrototypes();
		Enumeration e = prototypes.elements();
		Object[] typedArgs = null;

		while (typedArgs == null && e.hasMoreElements()) {
			typedArgs = matchPrototype(args, (Class[])e.nextElement());
		}

		if (typedArgs != null) {
			return handler.eval(typedArgs);
		} else if (handler.rawArgs()) {
			return handler.eval(args);
		} else {
			throw new XPathTypeMismatchException("for function \'" + handler.getName() + "\'");
		}
	}
	
	private Object[] matchPrototype (Object[] args, Class[] prototype) {
		Object[] typed = null;

		if (prototype.length == args.length) {
			typed = new Object[args.length];

			for (int i = 0; i < prototype.length; i++) {
				typed[i] = null;

				//how to handle type conversions of custom types?
				if (prototype[i].isAssignableFrom(args[i].getClass())) {
					typed[i] = args[i];
				} else {
					try {
						if (prototype[i] == Boolean.class) {
							typed[i] = toBoolean(args[i]);
						} else if (prototype[i] == Double.class) {
							typed[i] = toNumeric(args[i]);
						} else if (prototype[i] == String.class) {
							typed[i] = toString(args[i]);
						} else if (prototype[i] == Date.class) {
							typed[i] = toDate(args[i]);
						}
					} catch (XPathTypeMismatchException xptme) { /* swallow type mismatch exception */ }
				}

				if (typed[i] == null)
					return null;
			}
		}

		return typed;
	}
	
	public static Boolean toBoolean (Object o) {
		Boolean val = null;
		
		if (o instanceof Boolean) {
			val = (Boolean)o;
		} else if (o instanceof Double) {
			double d = ((Double)o).doubleValue();
			val = new Boolean(Math.abs(d) > 1.0e-12 && !Double.isNaN(d));
		} else if (o instanceof String) {
			String s = (String)o;
			val = new Boolean(s.length() > 0);
		} else if (o instanceof Date) {
			val = Boolean.TRUE;
		} else if (o instanceof Vector) {
			return new Boolean(count(o).doubleValue() > 0);
		} else if (o instanceof IExprDataType) {
			val = ((IExprDataType)o).toBoolean();
		}
		
		if (val != null) {
			return val;
		} else {
			throw new XPathTypeMismatchException("converting to boolean");
		}
	}
	
	public static Double toNumeric (Object o) {
		Double val = null;
		
		if (o instanceof Boolean) {
			val = new Double(((Boolean)o).booleanValue() ? 1 : 0);
		} else if (o instanceof Double) {
			val = (Double)o;
		} else if (o instanceof String) {
			/* annoying, but the xpath spec doesn't recognize scientific notation, or +/-Infinity
			 * when converting a string to a number
			 */
			
			String s = (String)o;
			double d;
			try {
				s = s.trim();
				for (int i = 0; i < s.length(); i++) {
					char c = s.charAt(i);
					if (c != '-' && c != '.' && (c < '0' || c > '9'))
						throw new NumberFormatException();
				}
				
				d = Double.parseDouble(s);
				val = new Double(d);
			} catch (NumberFormatException nfe) {
				val = new Double(Double.NaN);
			}
		} else if (o instanceof Date) {
			val = new Double(DateUtils.daysSinceEpoch((Date)o));
		} else if (o instanceof IExprDataType) {
			val = ((IExprDataType)o).toNumeric();
		}
		
		if (val != null) {
			return val;
		} else {
			throw new XPathTypeMismatchException("converting to numeric");
		}
	}

	public static String toString (Object o) {
		String val = null;
		
		if (o instanceof Boolean) {
			val = (((Boolean)o).booleanValue() ? "true" : "false");
		} else if (o instanceof Double) {
			double d = ((Double)o).doubleValue();
			if (Double.isNaN(d)) {
				val = "NaN";
			} else if (Math.abs(d) < 1.0e-12) {
				val = "0";
			} else if (Double.isInfinite(d)) {
				val = (d < 0 ? "-" : "") + "Infinity";
			} else if (Math.abs(d - (int)d) < 1.0e-12) {
				val = String.valueOf((int)d);
			} else {
				val = String.valueOf(d);
			}
		} else if (o instanceof String) {
			val = (String)o;
		} else if (o instanceof Date) {
			val = DateUtils.formatDate((Date)o, DateUtils.FORMAT_ISO8601);
		} else if (o instanceof IExprDataType) {
			val = ((IExprDataType)o).toString();
		}
			
		if (val != null) {
			return val;
		} else {
			throw new XPathTypeMismatchException("converting to string");
		}
	}

	public static Date toDate (Object o) {
		if (o instanceof Double) {
			double d = ((Double)o).doubleValue();
			if (Math.abs(d - (int)d) > 1.0e-12) {
				throw new XPathTypeMismatchException("converting non-integer to date");
			}
			
			Date dt = DateUtils.getDate(1970, 1, 1);
			dt.setTime(dt.getTime() + (long)d * 86400000l + 43200000l); //43200000 offset (0.5 day in ms) is needed to handle differing DST offsets!
			return DateUtils.roundDate(dt);
		} else if (o instanceof String) {
			Date d = DateUtils.parseDate((String)o);
			if (d == null) {
				throw new XPathTypeMismatchException("converting to date");
			} else {
				return d;
			}
		} else if (o instanceof Date) {
			return DateUtils.roundDate((Date)o);
		} else {
			throw new XPathTypeMismatchException("converting to date");
		}
	}

	public static Boolean boolNot (Object o) {
		boolean b = toBoolean(o).booleanValue();
		return new Boolean(!b);
	}
	
	public static Boolean boolStr (Object o) {
		String s = toString(o);
		if (s.equalsIgnoreCase("true") || s.equals("1"))
			return Boolean.TRUE;
		else
			return Boolean.FALSE;
	}

	public static Object ifThenElse (Object o1, Object o2, Object o3) {
		boolean b = toBoolean(o1).booleanValue();
		return (b ? o2 : o3);
	}
	
	//return whether a particular choice of a multi-select is selected
	//arg1: XML-serialized answer to multi-select question (space-delimited choice values)
	//arg2: choice to look for
	public static Boolean multiSelected (Object o1, Object o2) {
		String s1 = (String)o1;
		String s2 = ((String)o2).trim();
		
		return new Boolean((" " + s1 + " ").indexOf(" " + s2 + " ") != -1);
	}
	
	public static Double countSelected (Object o) {
		String s = (String)o;

		return new Double(DateUtils.split(s, " ", true).size());
	}
	
	public static Double count (Object o) {
		if (o instanceof Vector) {
			return new Double(((Vector)o).size());
		} else {
			throw new XPathTypeMismatchException("not a nodeset");
		}	
	}

	public static Double sum (IFormDataModel model, Object o) {
		if (o instanceof Vector) {
			Vector v = (Vector)o;
			double sum = 0.0;
			for (int i = 0; i < v.size(); i++) {
				TreeReference ref = (TreeReference)v.elementAt(i);
				sum += toNumeric(XPathPathExpr.getRefValue(model, ref)).doubleValue();
			}
			return new Double(sum);
		} else {
			throw new XPathTypeMismatchException("not a nodeset");
		}	
	}

	public static String concat (Object[] argVals) {
		StringBuffer sb = new StringBuffer();
		
		for (int i = 0; i < argVals.length; i++) {
			sb.append(toString(argVals[i]));
		}
		
		return sb.toString();
	}
	
	/*
	 * arg_0 = min, negative if no min
	 * arg_1 = max, negative if no max
	 * arg_2 .. arg_n = true/false values
	 * 
	 * returns: true if the number of true values is between min and max
	 */	
	public static Boolean checklist (Object[] argVals) {
		int min = toNumeric(argVals[0]).intValue();
		int max = toNumeric(argVals[1]).intValue();
		
		int count = 0;
		for (int i = 2; i < argVals.length; i++) {
			if (toBoolean(argVals[i]).booleanValue())
				count++;
		}
		
		return new Boolean((min < 0 || count >= min) && (max < 0 || count <= max));
	}

	/*
	 * arg_0 = min
	 * arg_1 = max
	 * arg_2, arg_4, arg_6, ... arg_(n - 1) = true/false values
	 * arg_3, arg_5, arg_7, ... arg_n = floating point weights corresponding to arg_(i - 1)
	 * 
	 * returns: true if the sum of the weights corresponding to the true values is between min and max
	 */	
	public static Boolean checklistWeighted (Object[] argVals) {
		double min = toNumeric(argVals[0]).doubleValue();
		double max = toNumeric(argVals[1]).doubleValue();
		
		double sum = 0.;
		for (int i = 2; i < argVals.length; i += 2) {
			boolean flag = toBoolean(argVals[i]).booleanValue();
			double weight = toNumeric(argVals[i + 1]).doubleValue();
			
			if (flag)
				sum += weight;
		}
		
		return new Boolean(sum >= min && sum <= max);
	}
	
	public static Boolean regex (Object o1, Object o2) {
		String str = toString(o1);
		String re = toString(o2);
		
		RE regexp = new RE(re);
		boolean result = regexp.match(str);
		return new Boolean(result);
	}

}
