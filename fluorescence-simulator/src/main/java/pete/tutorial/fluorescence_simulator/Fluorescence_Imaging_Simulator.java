package pete.tutorial.fluorescence_simulator;

/*
 * Copyright 2016 Peter Bankhead
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

import java.awt.*;
import ij.*;
import ij.plugin.filter.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;


/**
 * A simple plugin to simulate the blur and noise of a fluorescence image,
 * and also the impact of adjusting several microscope parameters.
 * 
 * To install it, simply put the Jar file inside ImageJ or Fiji's plugins folder.
 * 
 * Alternatively, drag the Java file onto Fiji and run directly from the script editor.
 * Note: You might need to delete 2 files, as documented at the end of this
 * https://github.com/fiji/fiji/issues/119
 * 
 * For teaching purposes, it can be helpful to generate a histogram and/or 
 * profile plot on the image prior to running the plugin, and set these to
 * 'Live' mode.  Then running the plugin with 'Preview' turned on enables
 * interactive exploration of different parameters.
 * 
 * For more background information, see Chapter 18 of the handbook at
 * http://go.qub.ac.uk/imagej-intro
 * 
 * @author Pete Bankhead
 * 
 */
public class Fluorescence_Imaging_Simulator implements ExtendedPlugInFilter, DialogListener {

	protected ImagePlus imp;
	protected Roi roi;
	protected int flags = DOES_8G | DOES_16 | DOES_32 | KEEP_PREVIEW | FINAL_PROCESSING | CONVERT_TO_FLOAT;

	protected double gaussSigma = 2;
	protected double exposureTime = 50;
	protected double gainFactor = 1;
	protected double offset = 0;
	protected double readNoiseStdDev = 10;
	protected double bitDepth = 8;

	protected double minInitial, maxInitial;

	protected boolean resetMinAndMax = false;

	protected GenericDialog gd;

	protected GaussianBlur gb = new GaussianBlur();

	protected int counter = 0;

	protected FloatProcessor fpNoise;

	protected double ppFixedMin, ppFixedMax;

	/**
	 * This method gets called by ImageJ / Fiji to determine
	 * whether the current image is of an appropriate type.
	 *
	 * @param arg can be specified in plugins.config
	 * @param image is the currently opened image
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	public int setup(String arg, ImagePlus imp) {
		if (imp == null) {
			IJ.noImage();
			return DONE;
		}
		if (arg.equals("final")) {
			imp.setRoi(roi);
			ProfilePlot.setMinAndMax(ppFixedMin, ppFixedMax);
			return DONE;
		}

		if (imp.getType() != ImagePlus.GRAY32) {
			if (!IJ.showMessageWithCancel("Convert to 32-bit", "I will need to convert the image to 32-bit - is this ok?"))
				return DONE;
			IJ.run(imp, "32-bit", "");
		}

		ppFixedMin = ProfilePlot.getFixedMin();
		ppFixedMax = ProfilePlot.getFixedMax();

		minInitial = imp.getProcessor().getMin();
		maxInitial = imp.getProcessor().getMax();
		this.imp = imp;
		this.roi = imp.getRoi();

		// Create an image with Gaussian noise, std. dev. = 1
		fpNoise = new FloatProcessor(imp.getWidth(), imp.getHeight());
		fpNoise.noise(1.0);

		return flags;
	}

	/**
	 * This method is run when the current image was accepted.
	 *
	 * @param ip is the current slice (typically, plugins use
	 * the ImagePlus set above instead).
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	public void run(ImageProcessor ip) {

		FloatProcessor ip2 = ip.convertToFloatProcessor();
		// Normalize so max value is 1
		ImageStatistics stats = ImageStatistics.getStatistics(ip2, Measurements.MIN_MAX, null);
		ip2.multiply(1.0/stats.max);
		// Could ensure in the range 0-1, but we might want to keep the original background
		//		ip2.subtract(stats.min);
		//		ip2.multiply(1.0/(stats.max - stats.min));

		// Simulate blurring of PSF with a simple Gaussian filter
		if (gaussSigma > 0)
			gb.blurGaussian(ip2, gaussSigma, gaussSigma, 0.0002);

		// Add Poisson noise, after scaling by exposure time but before multiplying by gain & adding offset
		ip2.multiply(exposureTime);
		addPoissonNoise(ip2);
		ip2.multiply(gainFactor);
		ip2.add(offset);

		// Add read noise, if required
		if (readNoiseStdDev != 0) {
			ImageProcessor fpNoise2 = fpNoise.duplicate();
			fpNoise2.multiply(readNoiseStdDev);
			ip2.copyBits(fpNoise2, 0, 0, Blitter.ADD);
		}

		// Clip values according to bit-depth
		ip2.min(0);
		ip2.max(Math.pow(2, bitDepth)-1);
		
		// Update the contents of the original image
		ip.insert(ip2, 0, 0);

		// Re-adjust brightness and contrast
		ip.resetMinAndMax();

		// It's nice for live plots to show the full scale for the bit-depth as the y-axis
		ProfilePlot.setMinAndMax(0, Math.pow(2, bitDepth)-1);

		// Upadte preview, if needed
		if (gd.getPreviewCheckbox().getState())
			imp.updateAndDraw();
	}



	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
		gd = new GenericDialog(command);

		gd.addMessage("Choose some acquisition settings to generate a simulated fluorescence image.\n"+
				"You can think of the image changing in a sequence of stages, from top to bottom.");

		gd.addMessage("1. Diffraction causes the image to be blurred, to an extent dependent on the objective lens NA.");
		gd.addSlider("Blur_sigma", 0, 4.99, gaussSigma);
		gd.addMessage("2. The randomness of photon emission leads to randomness in the final image, i.e. photon noise.\n"+
				"This depends on how much light is detected, i.e. fewer photons means more noise.\n" +
				"Increasing the exposure time to allow more photons to be detected decreases the noise.");
		gd.addSlider("Exposure_time", 1, 1000, exposureTime);
		gd.addMessage("3. If a photon is detected for a pixel, this produces an electron.\n"+
				"A gain factor can then be used to amplify the number of electrons per pixel.\n"+
				"This is useful in low-light images to overcome the read-noise introduced soon.\n"+
				"[Here, we simplify a bit by treating the gain as a perfectly noise-free multiplication - \n" +
				"in reality, it has its own randomness that acts like making photon noise a bit worse. \n" +
				"Also, to make the implementation easier, the multiplication here is by EXP(2 * Gain Factor).]");
		//gd.addMessage("though in reality, the gain probably has makes the noise a bit worse");
		//		gd.addSlider("Gain_factor", 0.5, 250, gainFactor);
		gd.addSlider("Gain_factor", -0.99, 4, gainFactor);
		gd.addMessage("4. Read noise is an error connected to quantifying exactly how many electrons per pixel we now have\n" +
				"after detection & gain amplification.");
		gd.addSlider("Read_noise_standard_deviation", 0, 25, readNoiseStdDev);
		gd.addMessage("5. Many images include a (sometimes variable) offset added to each pixel value.");
		gd.addSlider("Offset", -500, 500, offset);

		gd.addMessage("6. The bit-depth of the image determines the range of values it can contain.\n"+
				"If this is low, there is a good chance the final image will be clipped.");
		gd.addSlider("Bit-depth", 1, 16, bitDepth);

		gd.addPreviewCheckbox(pfr);
		gd.addDialogListener(this);
		gd.showDialog();
		if (gd.wasCanceled()) {
			imp.getProcessor().setMinAndMax(minInitial, maxInitial);
			ProfilePlot.setMinAndMax(ppFixedMin, ppFixedMax);
			return DONE;
		}
		return flags;
	}


	public void setNPasses(int nPasses) {}


	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		if (gd.getPreviewCheckbox() != null && !gd.getPreviewCheckbox().getState()) {
			imp.getProcessor().setMinAndMax(minInitial, maxInitial);
			imp.getProcessor().setMinAndMax(minInitial, maxInitial);
			imp.updateAndDraw();
			imp.setRoi(roi);
			//			return true;
		}

		gaussSigma = gd.getNextNumber();
		exposureTime = gd.getNextNumber();
		//		gainFactor = gd.getNextNumber();
		gainFactor = Math.exp(2 * gd.getNextNumber());
		readNoiseStdDev = gd.getNextNumber();
		offset = gd.getNextNumber();
		bitDepth = gd.getNextNumber();
		//		resetMinAndMax = gd.getNextBoolean();

		return true;
	}

	/**
	 * Add Poisson noise (simulating photon noise) to an image.
	 * 
	 * @param ip
	 */
	public static void addPoissonNoise(final ImageProcessor ip) {
		for (int i = 0; i < ip.getWidth()*ip.getHeight(); i++) {
			float val = ip.getf(i);
			ip.setf(i, createPoissonVariable(val));
		}
	}
	
	/**
	 * Generate a Poisson-distributed random variable.
	 * 
	 * Method is based on https://en.wikipedia.org/wiki/Poisson_distribution#Generating_Poisson-distributed_random_variables
	 * 
	 * @param lambda
	 * @return
	 */
	public static int createPoissonVariable(final double lambda) {
		double L = Math.exp(-lambda);
		int k = 0;
		double p = 1;
		do {
			k++;
			p = p * Math.random();
		} while(p > L);
		return k-1;
	}

}