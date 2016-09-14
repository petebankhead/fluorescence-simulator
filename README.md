# Fluorescence imaging simulator
This is a simple ImageJ plugin to simulate blur and noise from fluorescence imaging, and also the impact of adjusting several microscope parameters.

### Installation
First you will need to install either [ImageJ](https://imagej.nih.gov/ij/) for [Fiji](www.fiji.sc).

The plugin is then installed using the normal approach for ImageJ, as documented [here](http://imagej.net/Installing_3rd_party_plugins).

In summary, this means simply put the Jar file (fluorescence-simulator-1.0.0.jar) inside ImageJ's plugins folder.


### Usage
To run the plugin from within ImageJ, simply open a (single-channel, grayscale) image and run the *Plugins &rarr; Simulation &rarr; Fluorescence Imaging Simulator* command.

For teaching purposes, it can be helpful to generate a histogram and/or profile plot on the image prior to running the plugin, and set these to 'Live' mode.  Then running the plugin with 'Preview' turned on enables interactive exploration of different parameters.


### Further information
For more background information, see Chapter 18 of my [fluorescence microscopy image analysis handbook](http://go.qub.ac.uk/imagej-intro).

[![Analyzing Fluorescence Microscopy Images with ImageJ](http://blogs.qub.ac.uk/ccbg/files/2014/05/2014-05-Analyzing_fluorescence_cover.jpg)](http://go.qub.ac.uk/imagej-intro)
