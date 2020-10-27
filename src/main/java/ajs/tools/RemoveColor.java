package ajs.tools;

import ij.plugin.*;
import ij.process.*;
import ij.*;
import ij.gui.*;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * This is a template for a plugin that does not require one image
 * (be it that it does not require any, or that it lets the user
 * choose more than one image in a dialog).
 */
public class RemoveColor implements PlugIn {
	
	/**
	 * This method gets called by ImageJ / Fiji.
	 *
	 * @param arg can be specified in plugins.config
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@Override
	public void run(String arg) {
		
		GenericDialog gd=new GenericDialog("Remove Color Ratio");
		gd.addNumericField("Biggest factor to divide out", 0.8, 2);
		gd.addNumericField("Ideal Ratio to be removed", 0.6, 3);
		gd.addNumericField("Max ratio difference", 0.5, 3);
		gd.addNumericField("Channel to be modified (Numerator)", 2, 0);
		gd.addNumericField("Channel to compare (denominator)", 3, 0);
		gd.showDialog();
		
		if(gd.wasCanceled())return;

		float bfac=(float)gd.getNextNumber();
		float idealRatio=(float)gd.getNextNumber();
		float ratdiff=(float)gd.getNextNumber();
		int ch1=(int)gd.getNextNumber();
		int ch2=(int)gd.getNextNumber();
		
		ImagePlus imp=WindowManager.getCurrentImage();
		ImageStack st=imp.getStack();
		int w=imp.getWidth(), h=imp.getHeight(), frms=imp.getNFrames(), sls=imp.getNSlices();
		double frmsd=(double) frms, slsd=(double)sls;
		//int sl=imp.getZ(), fr=imp.getT();
		for(int fr=1;fr<=frms;fr++) {
			for(int sl=1; sl<=sls; sl++) {
				IJ.showStatus("Removing by color ratio...");
				IJ.showProgress(((double)fr*slsd+(double)sl)/(frmsd*slsd));
				ShortProcessor ip1=(ShortProcessor)(st.getProcessor(imp.getStackIndex(ch1,sl,fr))), ip2=(ShortProcessor)st.getProcessor(imp.getStackIndex(ch2,sl,fr));
				for(int y=0;y<h;y++) {
					for(int x=0; x<w; x++){
						float g=ip1.getPixelValue(x,y), r=ip2.getPixelValue(x, y), rat=r/g;
						float factor=1.0f-(bfac-(bfac*Math.min(Math.abs(rat-idealRatio),ratdiff)/ratdiff));
						ip1.set(x, y, (int)(g*factor));
					}
				}
			}
		}
		
	}
}
