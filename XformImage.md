# Introduction #

We allow two methods to specify images.

# Details #
We support images by specifying img tag in the label element

## First ##

The image is specified with the image filename in attribute img

```
<label img="imagefile">question content?</label>
```

In the ODK collect app, the imagefile is stored in path /sdcard/odk/images/form\_name/imagefile.
The same path is used to find the image.

For example:
In our demo\_form.xml,
```
<label img="gallery_photo_4.jpg">What do you think about this pic?</label>
```

The image will be stored in /sdcard/odk/images/demo\_form/gallery\_photo\_4.jpg

## Second ##

We can have multi-images using iimage framework inside model.
```
<iimage>
<imageSet setName="setname">
<image id="id" caption="caption">image_filename1</image>			
<image id="id" caption="caption">image_filename2 </image>	
…		
</imageSet></iimage>
</model>
```

The image\_filename1 and image\_filename2 are stored in the location as described in _FIRST_ part

In your question label, you can link to the image set with setname
```
<label img="jr:iimage('setname')">question content?</label>
```

For example:
In our demo\_form.xml, we have image set
```
<iimage>
<imageSet setName="Q1">
<image id="img_1 "caption="Figure 1">gallery_photo_1.jpg</image>			
<image id="img_2 "caption="Figure 2" >gallery_photo_2.jpg</image>	
<image id="img_3 "caption="Figure 3">gallery_photo_3.jpg</image>		
</imageSet>
</iimage>
```

the set can be linked using
```
<label img="jr:iimage('Q1')">What do you think about these photos?</label>
```