/*
 * Copyright (C) 2009 University of Washington
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

package org.odk.collect.android.widgets;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.FrameLayout.LayoutParams;

import org.javarosa.core.model.ImageItem;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.core.util.OrderedHashtable;
import org.odk.collect.android.logic.GlobalConstants;
import org.odk.collect.android.logic.PromptElement;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;

/**
 * SelectOneWidgets handles select-one fields using radio buttons.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class SelectOneWidget extends RadioGroup implements IQuestionWidget {

    private int mRadioChecked = -1;
    OrderedHashtable mItems;
    private String mFormPath;


    public SelectOneWidget(Context context, String mFormPath) {
        super(context);
        this.mFormPath = mFormPath;
    }


    public void clearAnswer() {
        clearCheck();
    }


    public IAnswerData getAnswer() {
        int i = getCheckedRadioButtonId();
        if (i == -1) {
            return null;
        } else {
            String s = (String) mItems.elementAt(i - 1);
            return new SelectOneData(new Selection(s));
        }
    }


    @SuppressWarnings("unchecked")
    public void buildView(final PromptElement prompt) {
    	mItems = prompt.getSelectItems();
    	setOnCheckedChangeListener(new OnCheckedChangeListener() {
    		public void onCheckedChanged(RadioGroup group, int checkedId) {
    			if (mRadioChecked != -1 && prompt.isReadOnly()) {
    				SelectOneWidget.this.check(mRadioChecked);
    			}
    		}
    	});
    	String s = null;
    	if (prompt.getAnswerValue() != null) {
    		s = ((Selection) prompt.getAnswerObject()).getValue();
    	}

    	if (prompt.getSelectItems() != null) {    
    		OrderedHashtable h = prompt.getSelectItems();
    		//*****Jeff added code*****//
    		OrderedHashtable images = prompt.getSelectItemImages();
    		OrderedHashtable imagesRef = prompt.getSelectItemImagesRef();
    		//*****end*****//
    		Enumeration e = h.keys();
    		String k = null;
    		String v = null;

    		// android radio ids start at 1, not 0
    		int i = 1;
    		while (e.hasMoreElements()) {
    			k = (String) e.nextElement();
    			v = (String) h.get(k);

    			RadioButton r = new RadioButton(getContext());
    			r.setText(k);
    			r.setTextSize(TypedValue.COMPLEX_UNIT_PT, GlobalConstants.APPLICATION_FONTSIZE);
    			r.setId(i);
    			r.setEnabled(!prompt.isReadOnly());
    			r.setFocusable(!prompt.isReadOnly());
    			addView(r);

    			//****ODK Glyph Jeff/Hedy added code***//
    			if(images != null && images.containsKey(k)){
    				
    				String imagePath = (String) images.get(k);
    				AddOneImage(imagePath);
    			}else if (imagesRef!=null && imagesRef.containsKey(k)){
    				String imageSetRef = (String)imagesRef.get(k);
    				ArrayList<ImageItem> imgSet = prompt.getSelectItemImageSet(imageSetRef);
    				
    				if(imgSet != null){
    		    		for(int l=0; l< imgSet.size();l++){
    		    			String imagePath = imgSet.get(l).getPath();
    		    			AddOneImage(imagePath);
    		    		}
    		    	}               
    			}
    			//****END
    			
    			if (v.equals(s)) {
    				r.setChecked(true);
    				mRadioChecked = i;
    			}

    			i++;
    		}
    	}
    }

    //private method to set one single image
    private void AddOneImage(final String imagePath){
    	if(imagePath == null)
    	{
    		return;
    	}

    	String delims = "/+";
		String[] pathPieces = mFormPath.split(delims);
		String fullImagePath = "";
		for(int i = 0; i< pathPieces.length-2; i++){
			fullImagePath = fullImagePath.concat(pathPieces[i]+"/");
		}
		
		fullImagePath = fullImagePath.concat("images/");
		delims = ".xml";
		
		pathPieces = pathPieces[pathPieces.length-1].split(delims);
		
		fullImagePath = fullImagePath.concat(pathPieces[0] + "/" + imagePath);
		File f = new File(fullImagePath);

    	Bitmap bm = null;
    	try {
    		bm = android.provider.MediaStore.Images.Media.getBitmap(getContext().getContentResolver(), Uri.fromFile(f));
    	} catch (FileNotFoundException e) {
    		Log.i("Hedy","file not found");
    		e.printStackTrace();
    	} catch (IOException e) {
    		Log.i("Hedy","IO Exception found");
    		e.printStackTrace();
    	}


    	ImageView iv = new android.widget.ImageView(getContext());

    	iv.setAdjustViewBounds(true);
    	//image maxHeigh and maxWidth
    	iv.setMaxHeight(200);
    	iv.setMaxWidth(200);

    	iv.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
    			LayoutParams.WRAP_CONTENT));

    	iv.setPadding(0, 10, 0, 0);



    	iv.setImageBitmap(bm);


    	addView(iv);

    	//add click listener for larger view (????)
    	// on play, launch the appropriate viewer
    	iv.setOnClickListener(new View.OnClickListener() {
    		public void onClick(View v) {
    			Intent i = new Intent("android.intent.action.VIEW");
    			File f = new File(imagePath);
    			i.setDataAndType(Uri.fromFile(f), "image" + "/*");
    			((Activity) getContext()).startActivity(i);

    		}
    	});

    }


    public void setFocus(Context context) {
    	// Hide the soft keyboard if it's showing.
    	InputMethodManager inputManager =
    		(InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
    	inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }

}
