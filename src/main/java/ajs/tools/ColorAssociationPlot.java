package ajs.tools;

import ij.plugin.*;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.*;
import ij.gui.*;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * This is a template for a plugin that does not require one image
 * (be it that it does not require any, or that it lets the user
 * choose more than one image in a dialog).
 */
public class ColorAssociationPlot implements PlugIn {
	/**
	 * This method gets called by ImageJ / Fiji.
	 *
	 * @param arg can be specified in plugins.config
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@Override
	public void run(String arg) {
		boolean showplot=false;
		boolean showhisto=true;
		
		ImagePlus imp=WindowManager.getCurrentImage();
		Roi roi=imp.getRoi();
		if(roi==null) {IJ.run("Select All"); roi=imp.getRoi();}
		Point[] ps=roi.getContainedPoints();
		Rectangle b=roi.getBounds();
		double[] xs=new double[ps.length], ys=new double[ps.length];
		float[][] rg=new float[b.height][b.width];
		ImageStack st=imp.getStack();
		int sl=imp.getZ(), fr=imp.getT();
		ImageProcessor ip2=st.getProcessor(imp.getStackIndex(2,sl,fr)), ip3=st.getProcessor(imp.getStackIndex(3,sl,fr));
		for(int i=0; i<ps.length; i++){
			xs[i]=ip2.getPixelValue(ps[i].x,ps[i].y);
			ys[i]=ip3.getPixelValue(ps[i].x,ps[i].y);
			rg[ps[i].y-ps[0].y][ps[i].x-ps[0].x]=(float)(ys[i]/xs[i]);
		}

		if(showplot){
			Plot plot=new Plot("Color Assoc Plot", "Green", "Red/Green");
			plot.addPoints(xs, ys, Plot.DOT);
			plot.show();
		}else {
			ImagePlus histimp=new ImagePlus("temp");
			histimp.setProcessor(new FloatProcessor(rg));
			HistogramWindow hist=new HistogramWindow("Color Assoc Hist", histimp, 256);
			hist.show();
		}

		
		
	}
}
