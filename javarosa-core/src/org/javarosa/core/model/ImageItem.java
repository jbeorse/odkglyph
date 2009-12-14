package org.javarosa.core.model;

/**
 * 
 * 
 * @author - Jeff Beorse, Wei-Ting Liao, Shirley Liou
 * 
 */

public class ImageItem {

	private String imageID;    /* should be unique name per instance of 
								  an image, even if image is shown twice in a form */
	private String imagePath;  /* the file name */
	private String imageCaption;
	
	
	public ImageItem() {
		this(null, null, null);
	}
	
	public ImageItem(String id, String path, String caption){
		setID(id);
		setPath(path);
		setCaption(caption);
	}
	
	public String getID(){
		return imageID;
	}

	public void setID(String id){
		imageID = id;
	}
	public String getPath(){
		return imagePath;
	}

	public void setPath(String path){
		imagePath = path;
	}
	public String getCaption(){
		return imageCaption;
	}

	public void setCaption(String caption){
		imageCaption = caption;
	}
	
}