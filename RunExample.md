## Please do not see this page for a full run example instruction. see Downloads ##

# Introduction #
how to run the demo\_from with extended glyph ODK


# Details #

**Please see GettingStarted to checkout all files**

**right click ODK collect and Run As… Android Application. You will launch the ODK collect app. You will not have directory /sdcard/odk/forms**

**connect your android phone to your computer**

**Use adb shell to create this directory /sdcard/odk/images/**

**Do adb push to store the image files to your android phone
```
adb push gallery_photo_1.jpg /sdcard/odk/images/Demo_form/gallery_photo_1.jpg
adb push gallery_photo_2.jpg /sdcard/odk/images/Demo_form/gallery_photo_2.jpg
adb push gallery_photo_3.jpg /sdcard/odk/images/Demo_form/gallery_photo_3.jpg
adb push gallery_photo_4.jpg /sdcard/odk/images/Demo_form/gallery_photo_4.jpg
adb push gallery_photo_5.jpg /sdcard/odk/images/Demo_form/gallery_photo_5.jpg
adb push gallery_photo_6.jpg /sdcard/odk/images/Demo_form/gallery_photo_6.jpg
```**

**Do adb push to store the xforms to your android phone
```
adb push Demo_form.xml /sdcard/odk/forms/Demo_form.xml
```**

**right click ODK collect and Run As… Android Application. You will launch the ODK collect app**

**Click New Data and click Demo\_form Form**

## You can now run Glyph version of ODK collect ##