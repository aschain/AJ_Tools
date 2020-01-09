package ajs.tools;

import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;
import ij.text.TextPanel;
import ij.text.TextWindow;

public class Slicelabel_Viewer implements ImageListener,PlugIn {
	ImagePlus imp;
	String[] slbls;
	TextWindow tw;
	int n=0;
	
	@Override
	public void run(String arg) {
		this.imp=WindowManager.getCurrentImage();
		if(imp==null) {IJ.noImage();return;}
		slbls=imp.getStack().getSliceLabels();
		tw=new TextWindow("Slicelabel Viewer for "+imp.getTitle(),slbls[imp.getCurrentSlice()-1],600,500);
		ImagePlus.addImageListener(this);
	}

	public void imageOpened(ImagePlus uimp) {}
	public void imageClosed(ImagePlus uimp) {
		if(uimp.equals(imp)){
			if(tw!=null)tw.dispose();
			ImagePlus.removeImageListener(this);
		}
	}
	public void imageUpdated(ImagePlus uimp){
		if(uimp.equals(imp)){
			if(tw==null){ImagePlus.removeImageListener(this);return;}
			TextPanel tp=tw.getTextPanel();
			tp.setColumnHeadings("");
			tp.append(slbls[imp.getCurrentSlice()-1]);
		}
	}
}
