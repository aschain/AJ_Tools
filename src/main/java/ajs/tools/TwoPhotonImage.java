package ajs.tools;
import ij.*;
import java.io.*;
import java.util.ArrayList;

import ij.process.*;
import ij.text.TextWindow;

import java.awt.*;
import java.awt.event.*;
import ij.gui.*;

public class TwoPhotonImage implements AdjustmentListener{
	
	static final int screenwidth=GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode().getWidth();
	static final String[] zmethnames= {"AVG_","MAX_","MIN_","SUM_","STD_","MED_"};
	
	boolean updating=false;
	ImagePlus img=null,zimg=null;
		
	String dir, infofile, RGBname, exlocoutput, starttimestr;
	int zmethod=1;
	ij.measure.Calibration cal=new ij.measure.Calibration();
	int scale=100;
	int chs=1,sls=1,frms=1,totfrms=1,locs=1,loc=0,totaltps=0;
	String[] xylist=new String[0];
	ArrayList<Integer> slicearray;
	int cycind,slind,chind;
	boolean hasT,hasZ,hasC,virtual=false,oifdo=false,cont=false,dogamma=false,dotimes=true,mousePressed=false, hasAJZ=false;
	LUT[] stackluts={LUT.createLutFromColor(Color.red),LUT.createLutFromColor(Color.green),LUT.createLutFromColor(Color.blue),LUT.createLutFromColor(Color.magenta),LUT.createLutFromColor(Color.cyan),LUT.createLutFromColor(Color.yellow)};
	long firstfiletime, lastfiletime, tifsize;
	
	//Updating variables
	int sl=0,frstart,frend;
	File[] fl;
	String[] fltif;
	
	final int MAXCHS=4;

	
	public TwoPhotonImage(String ndir) {
		dir=ndir;
		if(dir.endsWith("\\")||dir.endsWith("/"))dir=dir.substring(0,dir.length()-1);
		dir+=File.separator;
	}
	
	public TwoPhotonImage(File[] fl) {
		this(fl,true);
	}
	
	public TwoPhotonImage(File[] fl, boolean doSetup) {
		this.fl=fl;
		this.dir=fl[0].getParentFile().getAbsolutePath();
		if(!this.dir.endsWith(File.separator))this.dir+=File.separator;
		setup();
	}
	
	public TwoPhotonImage(ImagePlus imp) {
		this.img=imp;
		updateFromImage();
		addThisAdjustmentListener();
	}
	
	void setFrameRange(int start, int end) {
		frstart=start;
		frend=end;
		img.setProperty("2p-FrameStart", start);
		img.setProperty("2p-FrameEnd", end);
	}
	
	public void setup() {
		setup(true,false);
	}
	
	public void setup(boolean updateFromFL, boolean updateFileList){
		
		String lastfilename="";
		for(int i=0;i<fl.length;i++){
			lastfilename=fl[fl.length-1-i].getName();
			if(lastfilename.endsWith(".tif")) {
				lastfiletime=fl[fl.length-1-i].lastModified();
				if(lastfilename.startsWith("s_"))oifdo=true;
				tifsize=fl[fl.length-1-i].length();
				break;
			}
		}
		if(!lastfilename.endsWith(".tif")){IJ.error("No tifs in folder");}
		RGBname=fl[0].getParentFile().getName();
		for(int i=0;i<fl.length;i++){
			if(fl[i].getName().endsWith(".tif")) {
				firstfiletime=fl[fl.length-1-i].lastModified();
				break;
			}
		}
		
		if(oifdo){
			//file info
			RGBname=RGBname.substring(0,RGBname.length()-6);
			infofile=fl[0].getParentFile().getParent()+File.separator+RGBname;
			cycind=lastfilename.indexOf("T")+1; slind=lastfilename.indexOf("Z")+1; chind=lastfilename.indexOf("C")+3;
			hasT=(cycind>0);
			hasZ=(slind>0);
			hasC=(chind>2);
			
			//color
			int ind, n=0;
			for(int i=0;i<fl.length;i++){
				String fn=fl[i].getName();
				if(fn.startsWith("Saving")) cont=true;
				if(fn.endsWith(".lut")){
					int blue=255,green=255,red=255;
					String[] lutstr=openZap(fl[i].getAbsolutePath()).split("\n");
					if(lutstr.length>18) {
						blue=Integer.parseInt(lutstr[2].substring(9,10))==0?0:255; green=Integer.parseInt(lutstr[12].substring(9,10))==0?0:255; red=Integer.parseInt(lutstr[18].substring(9,10))==0?0:255;
						//if(blue>0){res=Color.blue; if(green>0) {res=Color.cyan; if(red>0) res=Color.white;}else if(red>0) {res=Color.magenta;}}
						//else if(green>0){res=Color.green; if(red>0) res=Color.yellow;}
						//else if(red>0) res=Color.red;
						ind = AJ_Utils.parseIntTP(fn.substring(5,6))-1;
						if(ind==-2) ind=n;
						stackluts[ind]=LUT.createLutFromColor(new Color(red,green,blue));
		 				n=ind+1;
					}
				}
			}
		}else{
			//File info
			cycind=lastfilename.lastIndexOf("Cycle")+5; slind=lastfilename.length()-7; chind=lastfilename.indexOf("_Ch")+3;
			while(lastfilename.substring(cycind+3,cycind+4)!="_") cycind++;
			int xmlindex=0; if(!fl[xmlindex].getName().endsWith("xml")) xmlindex=1;
			infofile=fl[xmlindex].getAbsolutePath();
			//String zoom="NA";
			//if(cycind<5) oifnotime=true;
			hasT=cycind>5; hasZ=true; hasC=true;
			if(chind<3) {IJ.error("Error with tif file name, no Ch found in:\n"+lastfilename); return;}
			
			//color **need to fix
			//chnums=getCfgCch(fl);
			//var pgreens=newArray(-15728896,-13172992,-11600128,-16711913,-10813696,-10551552,-8257792);
			//var preds=newArray(-65536,-57088);
			//if(indexOfArray(preds,chnums[0])>-1) {stackluts[0]="Red"; stackluts[1]="Green"; stackluts[2]="Blue";}
			//if(indexOfArray(pgreens,chnums[0])>-1) {stackluts[0]="Green"; stackluts[1]="Blue"; stackluts[2]="Red";}
			//if(chnums[0]==-16766721) {stackluts[0]="Blue"; stackluts[1]="Red"; stackluts[2]="Green";}
		}
		if(updateFromFL)updateFLInfo(updateFileList);
		exLoc();
	}
	
	void updateFromImage(ImagePlus imp) {
		this.img=imp;
		updateFromImage();
	}
	
	void updateFromImage() {
		if(img==null) return;
		RGBname=img.getTitle();
		cal=img.getCalibration();
		sls=img.getNSlices(); chs=img.getNChannels();
		frstart=(int)img.getProperty("2p-FrameStart");
		frstart=frstart>0?frstart:1;
		frend=(int)img.getProperty("2p-FrameEnd");
		starttimestr=(String)img.getProperty("2p-starttime");
		frms=frend>0?frend:img.getNFrames();
		virtual=img.getStack().isVirtual();
		if(img.isComposite()){
			LUT[] luts=img.getLuts();
			for(int i=0;i<Math.min(stackluts.length, luts.length);i++){
				stackluts[i]=luts[i];
			}
		}else stackluts[0]=LUT.createLutFromColor(Color.white);
		if(zimg==null) {
			for(int i=0;i<zmethnames.length;i++) {
				zimg=WindowManager.getImage(zmethnames[i]+RGBname);
				if(zimg!=null) {zmethod=i; break;}
			}
		}
		String directory=(String)img.getProperty("2p-Directory");
		if(!(directory!=null && !directory.isEmpty() && directory.indexOf(File.separator)>-1)) {
			directory=img.getInfoProperty().split("\n")[0];
		}
		if((directory!=null && !directory.isEmpty() && directory.indexOf(File.separator)>-1))dir=directory;
		if(dir!=null && !dir.endsWith(File.separator)) {
			if(dir.endsWith("/"))dir=dir.substring(0, dir.length()-1);
			dir=dir+File.separator;
		}
		fl=(new File(dir)).listFiles(TwoPhoton_Import.nohidden);
		String[] locstr=((String)img.getProperty("2p-Location")).split("/");
		loc=AJ_Utils.parseIntTP(locstr[0]);
		if(locstr.length>1)locs=AJ_Utils.parseIntTP(locstr[1]);
		String[]boos=((String)img.getProperty("2p-booleans")).split("/");
		dotimes=AJ_Utils.parseIntTP(boos[0])>0;
		if(boos.length>1)dogamma=AJ_Utils.parseIntTP(boos[1])>0;
	}
	
	ImagePlus zProject(ImagePlus imp, int start, int stop) {
		return zProject(imp,start,stop,true);
	}
	
	ImagePlus zProject(ImagePlus imp, int start, int stop, boolean allFrames) {
		ij.plugin.ZProjector zprojector=new ij.plugin.ZProjector(imp);
		zprojector.setStartSlice(start);
		zprojector.setStopSlice(stop);
		zprojector.setMethod(zmethod);
		zprojector.doHyperStackProjection(allFrames);
		ImagePlus projImage=zprojector.getProjection();
		projImage.setCalibration(imp.getCalibration());
		ajs.tools.Slicelabel_Transfer.transferSliceLabels(imp, projImage);
		return projImage;
	}
	
	ImagePlus zProject(ImagePlus imp) {
		return zProject(imp,1,imp.getNSlices()==1?imp.getNFrames():imp.getNSlices());
	}
	
	ImagePlus zProject(boolean dopos) {
		if(img!=null) {
			zimg=zProject(img);
			zimg.show();
			if(dopos) zimg.getWindow().setLocation(new Point(screenwidth/2+5,200));
			return zimg;
		}else return null;
	}
	
	void setLocation(int newloc) {
		if(locs>1) {
			this.loc=Math.min(locs-1, loc);
			RGBname=RGBname+"-loc"+(loc+1);
			sls=slicearray.get(loc);
		}
	}
	
	int getSlices(int loc) {
		return slicearray.get(loc);
	}
	
	public void updateFileList() {
		fl=(new File(dir)).listFiles(TwoPhoton_Import.nohidden);
		cont=(new File(dir+File.separator+"Saving")).exists();
	}

	public void updateFLInfo(boolean updatefilelist){
		if(updatefilelist) {
			updateFileList();
		}
		int lasti=0;
		ArrayList<String> altif=new ArrayList<String>();
		for(int i=0;i<fl.length; i++){
			String name=fl[i].getName();
			if(name.endsWith(".tif") && !name.endsWith("Reference.tif")){
				altif.add(name);
			}
		}
		altif.sort(null);
		fltif=new String[altif.size()];
		for(int i=0;i<altif.size();i++) fltif[i]=altif.get(i);
		altif=null;
		lasti=fltif.length-1;
		if(hasZ) sl=Integer.parseInt(fltif[lasti].substring(slind,slind+3));
		
		ArrayList<Integer> cha=new ArrayList<Integer>();
		ArrayList<Integer> cyca=new ArrayList<Integer>();
		slicearray=new ArrayList<Integer>();
		int curcyc=0,curch=0,cursl=0,topprevslice=0,lastfilei=-1;
		for(int i=0;i<fltif.length;i++){
			if(hasT){
				curcyc=Integer.parseInt(fltif[i].substring(cycind, (oifdo?fltif[i].indexOf("."):cycind+3)));
				if(!cyca.contains(curcyc)) {cyca.add(curcyc);}
				frms=cyca.size();
			}else frms=1;
			if(hasC){
				curch=Integer.parseInt(fltif[i].substring(chind, chind+1));
				if(!cha.contains(curch))cha.add(curch);
				chs=cha.size();
			}else chs=1;
			if(hasZ){
				cursl=Integer.parseInt(fltif[i].substring(slind, slind+3));
			}else cursl=1;
			if(oifdo){
				if((hasT&&curcyc==frms) || !hasT) {sl=cursl; lastfilei=i;}
			}else{
				if((cursl==1 && curch==1) && topprevslice!=0 && frms<(locs+1)) slicearray.add(topprevslice);
				topprevslice=cursl;
			}
			sls=Math.max(sls, cursl);
		}
		if(oifdo) {lastfiletime=fl[lastfilei].lastModified();}
		else {lastfiletime=fl[fl.length-1].lastModified();}
		if(oifdo) {
			String ajzpath=dir.replace(".files"+File.separator, ".ajz");
			hasAJZ=(new File(ajzpath)).exists();
			if(hasAJZ) {
				String[] ajz=openRaw(ajzpath).split("\n");
				int stmp=0;
				for(int i=0;i<ajz.length;i++)if(ajz[i].startsWith("zps:"))stmp++;
				sl=Math.min(1, frms%stmp);
				if(frms<stmp) {sls=frms;frms=1;}
				else{sls=stmp; frms/=sls;}
				cal.frameInterval*=stmp;
			}
		}
		if(oifdo||slicearray.size()==0 ||frms<=xylist.length)slicearray.add(sls);
		if(!oifdo) frms/=locs;
		//locs=slicearray.size();
		/*  Don't need because of adjustment for slices above I think
		if(locs<xylist.length){
			// could get rid of this if prairie slicearray made sure to add to slicearray as long as frame was 1
			locs=xylist.length;
			//This is only for the case that all locations have the same number of slices
			for(int i=1; i<locs; i++) slicearray.add(sls);
		}
		*/
		totfrms=frms;
		if(hasZ&&hasT && (sl!=(int)slicearray.get((locs-1)))) frms--;
	}
	
	ImagePlus loadImage(int tpstart, int tpend){
		return loadImage(tpstart, tpend, dotimes);
	}
	
	ImagePlus loadImage(int tpstart, int tpend, boolean reallydotimes){
		tpstart--;
		//String temppath=IJ.getDirectory("temp")+"AJlist.txt";
		int totalslices=0;
		for(int i=0;i<slicearray.size();i++) totalslices+=slicearray.get(i);
		int sluptoloc=0;
		for(int i=0;i<loc;i++) sluptoloc+=slicearray.get(i);
		int slsl=slicearray.get(loc), frms=tpend-tpstart;
		if(hasAJZ) {slsl=1;tpstart*=this.sls;tpend*=this.sls;}
		
		String[] paths=new String[(tpend-tpstart)*slsl*chs];
		String printer;
		int n=0;
		for(int i=tpstart; i<tpend; i++) {
			for(int j=0;j<slsl;j++){
				for(int k=0;k<chs;k++){
					if(oifdo){
						printer="s_"+(hasC?("C"+String.format("%03d",k+1)):"")+(hasZ?("Z"+String.format("%03d",j+1)):"")+(hasT?("T"+String.format("%03d",i+1)):"")+".tif";
					}else{
						printer=fltif[(i*chs*totalslices)+(sluptoloc*chs)+(k*slsl)+j];
					}
					paths[n++]=dir+printer;
				}
			}
		}
		ImagePlus newimg=new ImagePlus(paths[0]);
		int bd=newimg.getBitDepth(); if(bd==24)bd=32;
		ImageStack newimgst=new ImageStack(newimg.getWidth(),newimg.getHeight(),LUT.createLutFromColor(Color.white));
		IJ.showStatus("Loading 2P Image");
		long sms=System.currentTimeMillis();
		for(int i=0;i<paths.length;i++) {
			ImagePlus adderimg;
			if(i==0) adderimg=newimg;
			else  adderimg=IJ.openImage(paths[i]);
			newimgst.addSlice((new File(paths[i])).getName()+"\n"+(String)adderimg.getProperty("Info"), adderimg.getProcessor());
			adderimg.changes=false; adderimg.close();
			if(i%100==0) {
				double sfltime=((double)(System.currentTimeMillis()-sms))/1000.0d; sfltime=Math.max(sfltime,0.001d);
				double mbps=((double)((bd/8)*newimg.getWidth()*newimg.getHeight()*newimg.getStackSize()))/1024.0d/1024.0d/sfltime;
				IJ.showStatus("Loading 2P Image "+(int)mbps+"MB/s");
			}
			IJ.showProgress((double)i/(double)paths.length);
		} 
		IJ.showProgress(1.0);
		newimg= new ImagePlus(RGBname, newimgst);
		newimg.setDimensions(chs, slsl, frms);
		
		
		if(scale!=100){
			newimg.show();
			IJ.run("Size...", "width="+(scale/100*newimg.getWidth())+" height="+(scale/100*newimg.getHeight())+" constrain average interpolation=Bilinear");
			newimg=WindowManager.getCurrentImage();
		}


		if(chs*sls*frms>sls) {
			ImagePlus hsimg=ij.plugin.HyperStackConverter.toHyperStack(newimg, chs, slicearray.get(loc), frms);
			newimg.changes=false;
			newimg.close();
			newimg=hsimg;
		}
		if(dogamma) {
			newimg.show();
			IJ.run("Gamma...", "value=0.50 stack");
		}
		if(reallydotimes) {newimg.show(); updateImageSliceTimes(newimg,dir,starttimestr);}
		return newimg;
	}
	
	ImagePlus loadFirstImage(int tpstart, int tpend, boolean dopos) {
		long sms=System.currentTimeMillis();
		img=loadImage(tpstart,tpend,false);
		double sfltime=((double)(System.currentTimeMillis()-sms))/1000.0d;
		sfltime=Math.max(sfltime,0.001d);
		int bd=img.getBitDepth(); if(bd==24)bd=32;
		double mbps=((double)((bd/8)*img.getWidth()*img.getHeight()*img.getStackSize()))/1024.0d/1024.0d/sfltime;
		IJ.log("LoadImage took "+sfltime+"s, "+Math.round(mbps)+"MB/s");
		
		if(chs==1)stackluts[0]=LUT.createLutFromColor(Color.white);
		for(int i=0;i<stackluts.length;i++) {
			if(bd==16) {stackluts[i].max=4095;stackluts[i].min=0;}
			else if(bd==8){stackluts[i].max=256;stackluts[i].min=0;}
		}
		if(chs>1)((CompositeImage)img).setLuts(stackluts);
		img.setTitle(RGBname);
		img.setCalibration(cal);
		img.setProperty("Info", dir+"\n"+exlocoutput);
		img.setProperty("2p-Directory", dir);
		img.setProperty("2p-Location",""+loc+"/"+locs);
		img.setProperty("2p-booleans", ""+(dotimes?1:0)+"/"+(dogamma?1:0));
		img.setProperty("2p-hasAJZ",""+(hasAJZ?"true":"false"));
		img.setProperty("2p-starttime", starttimestr);
		setFrameRange(tpstart,tpend);
		img.show();
		if(cont) addUpdateButton();
		addThisAdjustmentListener();
		if(dopos) {
			img.getWindow().setLocation(new Point(Math.max(10,screenwidth/2-img.getWindow().getWidth()-5),200));
		}
		if(dotimes)updateImageSliceTimes(img,dir,starttimestr);
		return img;
		
	}
	
	
	
	void updateImage(){
		updateFLInfo(true);
		if(img.isLocked())return;
		if(img.getWindow()==null)return;
		IJ.showStatus("Updating "+RGBname+"...");
		if(frms<=frend || img==null) {IJ.showStatus(RGBname+" has no new frames."); if(!cont)removeUpdateButton(); return;}
		Window currw=WindowManager.getActiveWindow();
		ImagePlus newimg=imageUpdater(img,null);
		setFrameRange(frstart,frms);
		preStrip(img,true);
		if(zimg!=null && zimg.getWindow()!=null) {
			ImagePlus newz=imageUpdater(zimg,newimg);
			newz.changes=false;
			newz.close();
			preStrip(zimg,true);
		}
		newimg.changes=false;
		newimg.close();
		WindowManager.setWindow(currw);
	}
	
	private ImagePlus imageUpdater(ImagePlus imp, ImagePlus imptoz) {
		if(imp==null) {IJ.log("Imp was null");return null;}
		imp.lock();
		ImageCanvas ic=imp.getCanvas();
		int dcw=0;
		if(!ic.getClass().getName().startsWith("ajs.joglcanvas"))dcw=0;
		else if(ic.getClass().getName().equals("ajs.joglcanvas.JOGLImageCanvas"))dcw=1;
		else {
			for(WindowListener wl:imp.getWindow().getWindowListeners()) {if(wl.getClass().getName().equals("ajs.joglcanvas.JOGLImageCanvas")) {dcw=2;break;}}
		}
		Dimension wd=imp.getWindow().getSize();
		Point wl=imp.getWindow().getLocation();
		int ch=imp.getC(),sl=imp.getZ(),fr=imp.getT();
		double zoom=imp.getCanvas().getMagnification();
		Rectangle sr=imp.getCanvas().getSrcRect();
		boolean isAni= ((StackWindow)imp.getWindow()).getAnimate();
		if(isAni) {WindowManager.setCurrentWindow(imp.getWindow()); IJ.runPlugIn("ij.plugin.Animator", "stop");}
		
		ImagePlus newimg;
		if(imptoz==null)newimg=loadImage(frend+1,frms);
		else newimg=zProject(imptoz);
		
		stackConcatenator(imp, newimg);
		imp.updateAndRepaintWindow();
		imp.unlock();
		if(dcw>0 && imp.getWindow().getClass().getName().equals("ij.gui.StackWindow")) {
			if(dcw==1)IJ.run("Convert to JOGL Canvas");
			else if(dcw==2)IJ.run("Open JOGL Canvas Mirror");
			IJ.wait(1000);
		}
		imp.getWindow().setSize(wd);
		imp.getWindow().setLocationAndSize(wl.x,wl.y,wd.width,wd.height);
		if(sr.width!=imp.getWidth() && sr.height!=imp.getHeight()) {
			imp.getCanvas().setSourceRect(sr);
			imp.getCanvas().setMagnification(zoom);
		}
		imp.setPosition(ch, sl, fr);
		imp.updateAndRepaintWindow();
		if(isAni) {WindowManager.setCurrentWindow(imp.getWindow()); IJ.runPlugIn("ij.plugin.Animator", "start");}
		if(cont)addUpdateButton();
		return newimg;
	}
	
	void stackConcatenator(ImagePlus img, ImagePlus newimg) {
		ImageStack imgst=img.getImageStack();
		ImageStack newimgst=newimg.getImageStack();
		int newsize=newimgst.getSize();
		String[] labels=newimgst.getSliceLabels();
		int slices=img.getNSlices();
		int channels=img.getNChannels();
		int newfrms=img.getNFrames()+newimg.getNFrames();
		for(int i=0;i<newsize;i++) {
			imgst.addSlice(labels[i],newimgst.getProcessor(i+1));
		}
		img.setDimensions(channels, slices, newfrms);
		
	}
	
	ImagePlus getLatestMax() {
		ImagePlus res=null;
		boolean hadzimg=(zimg!=null);
		if(!hadzimg) {
			int fr=img.getT();
			img.setPosition(img.getC(), img.getZ(), img.getNFrames());
			res=zProject(img,1,img.getNSlices(),false);
			img.setPosition(img.getC(), img.getZ(), fr);
		}else res= zProject(zimg,zimg.getNFrames(),zimg.getNFrames());
		return res;
	}
	
	void exLoc() {
		exlocoutput=exLoc(infofile);
	}
	
	String exLoc(String infofilepath){
		String exlocoutput="";
		xylist=new String[0];
		double xysize=1.0d, zsize=1.0d, tsize=1.0d;
		
		if(!infofilepath.endsWith("xml") && !infofilepath.endsWith("oif")) return "";
		if(infofilepath.endsWith(".oif")) oifdo=true;
		
		ArrayList<Integer> times=new ArrayList<Integer>();

		starttimestr="I dunno";
		String lwv="NA";
		String zoom="NA", lpower="NA", lpowerend="NA",objective="NA",pixels="0";
		String averaging="0", averagingtype="None";
		String[] gains=new String[MAXCHS], gainends=new String[MAXCHS];
		for(int i=0;i<MAXCHS;i++) {
			gains[i]="NA";gainends[i]="NA";
		}
		
		String xpart="NA",ypart="NA",zpart="NA",locstr;
		
		double xmlintervaltime=0, totaltime;
		int steps=1, tps=1, chs=0;
		boolean zstepgo=true;
		
		//String startdate="", version="NA";
		//double zpartend, xtotalsize, ytotalsize
		//boolean pchange=false;
		
 		
	 	if(oifdo){   //for olympus oifs
	 		int i=0;

	 		String[] xmlfile=openZap(infofilepath).split("\n");
	 		locs=1; //oifs don't have multiple locations as far as I know.
	 		
	 		i=findData(xmlfile,"ImageCap",0,50); //typo of ImageCaptureDate is currently ImageCaputreDate
	 		if(i>=50|| i==xmlfile.length) {IJ.error("Could not get data from oif file"); return "";}
	 		starttimestr=getData(xmlfile[i]);
	 		if(xmlfile[i+1].contains("MilliSec"))starttimestr+=":"+getData(xmlfile[i+1]);
	 		//version=getData(xmlfile[xmlfile.length-1]);
	 		//Averaging, called IntegrationCount and IntegrationType, are 2 and 3 after Capture Date
	 		if(xmlfile[i+2].contains("IntegrationCount"))averaging=getData(xmlfile[i+2]);
	 		if(xmlfile[i+3].contains("IntegrationType"))averagingtype=getData(xmlfile[i+3]);
	 		//Laser Wavelength   also we get it later with Laser 0 parameters
	 		i=findData(xmlfile,"LaserWavelength",i,0);
	 		lwv=getData(xmlfile[i]);
	 		//Zoom
	 		i=findData(xmlfile,"ZoomValue",i,0);
	 		zoom=getData(xmlfile[i]);
	 		//xysize
			//oif does not contain xy stage data but rather image xy dimension size data
	 		//i=findData(xmlfile,"AxisCode=\"X",i,0);
	 		//i=findData(xmlfile,"EndPosition",i,0);
	 		//xtotalsize=parseDoubleTP(getData(xmlfile[i]));
	 		//Ysize
			//oif does not contain xy stage data but rather image xy dimension size data
	 		//i=findData(xmlfile,"AxisCode=\"Y",i,0);
	 		//i=findData(xmlfile,"EndPosition",i,0);
	 		//ytotalsize=parseDoubleTP(getData(xmlfile[i]));
	 		//Chs
	 		//number of chs
	 		i=findData(xmlfile,"AxisCode=\"C",i,0);
	 		i=findData(xmlfile,"EndPosition",i,0);
	 		chs=AJ_Utils.parseIntTP(getData(xmlfile[i]));
	 		//ZSize
	 		//it is the actual Z position (in nm)
	 		//i=findData(xmlfile,"AxisCode=\"Z",i,0);
	 		//i=findData(xmlfile,"EndPosition",i,0);
	 		//zpartend=AJ_Utils.parseDoubleTP(getData(xmlfile[i]))/1000; //oifs give z in nm not um
	 		//Z Step Interval
	 		i=findData(xmlfile,"Interval",i,0);
	 		zsize=AJ_Utils.parseDoubleTP(getData(xmlfile[i]))/1000; //oifs give z in nm not um
	 		if(zsize<0)zsize=-1d;
	 		//Number of Slices
	 		i=findData(xmlfile,"MaxSize",i,0);
			steps=Math.max(AJ_Utils.parseIntTP(getData(xmlfile[i])),1);
			//Z position
			i=findData(xmlfile,"StartPosition",i,0);
			zpart=Double.toString(AJ_Utils.parseDoubleTP(getData(xmlfile[i]))/1000); //oifs give z in nm not um
			//Time in total
			i=findData(xmlfile,"AxisCode=\"T",i,0);
			i=findData(xmlfile,"EndPosition",i,0);
			totaltime=AJ_Utils.parseDoubleTP(getData(xmlfile[i]))/1000d; //oifs give t in ms
			//Total Timepoints number
			i=findData(xmlfile,"GUI MaxSize",i,0); //No we DO want GUI MaxSize
			//i=findData(xmlfile,"MaxSize",i+1,0); //+1 because we want the second MaxSize not GUI MaxSize
			tps=Math.max(AJ_Utils.parseIntTP(getData(xmlfile[i])),1);
			//Time interval
			i=findData(xmlfile,"Interval",i-2,0);
			xmlintervaltime=AJ_Utils.parseDoubleTP(getData(xmlfile[i]))/1000d;
			if(xmlintervaltime<=0){xmlintervaltime=(double)Math.round(totaltime/tps*1000d)/1000d;}
			//i=findData(xmlfile,"StartPosition",i+1,0);
			//startsecs=getData(xmlfile[i])/1000; //oifs give t in ms
			//probably startsecs=0
			int starti=i; int limit=findData(xmlfile,"Corr Bright",i,0);
			for(int j=0;j<MAXCHS;j++) {
				i=findData(xmlfile,"[Channel "+(j+1),starti,limit);
				if(i<=limit){
					i=findData(xmlfile,"AnalogPMTVoltage",i,limit);
					if(i<limit)gains[j]=getData(xmlfile[i]);
					else gains[j]="NA";
				}else gains[j]="NA";
			}
			
			i=findData(xmlfile,"[Laser 0",i,0);
			if(i<=limit){
				i=findData(xmlfile,"LaserTrans",i,0);
				lpower=getData(xmlfile[i]);
				i=findData(xmlfile,"LaserWavelength",i,0);
				lwv=getData(xmlfile[i]);
			}
			i=findData(xmlfile,"ImageWidth",i,0);
			if(i<xmlfile.length)pixels=getData(xmlfile[i]);
			i=findData(xmlfile,"WidthConvertValue",i,0);
			if(i<xmlfile.length)xysize=AJ_Utils.parseDoubleTP(getData(xmlfile[i]));

			//olympus stores xy stage data in the pty file in the oif.files directory sometimes
	 		String oifdir=infofilepath+".files"+File.separator;	 		
	 		
 			String startptypath,endptypath;

			for(int ch=1;ch<=chs;ch++) {
				startptypath=oifdir+"s_"+((chs>1)?"C"+String.format("%03d",ch):"")+((steps>1)?"Z001":"")+((tps>1)?"T001":"")+".pty";
				if(startptypath.equals(oifdir+"s_.pty"))startptypath=oifdir+"s_C001.pty";
				if(!(new File(startptypath)).exists()){
					IJ.log("Can't find "+startptypath);
				}else{
					//start pty file
					String[] ptyfile=openZap(startptypath).split("\n");
					if(ptyfile.length>0){
						if(ch==1) {
							objective=searchAndGetData(ptyfile, "ObjectiveLens Name");
							if(objective.equals("XLPLN      25X W  NA:1.05")) objective="25X W NA:1.05";
							i=findData(ptyfile,"ExcitationOutPutLevel",0,0);
							if(i<ptyfile.length) lpower=Long.toString(Math.round(AJ_Utils.parseDoubleTP(getData(ptyfile[i]))*10));
							if(lpower=="-10")lpower="NA";
							i=findData(ptyfile,"AbsPositionValueX",0,0);
							if(i<ptyfile.length) xpart=Double.toString(AJ_Utils.parseDoubleTP(getData(ptyfile[i]))/1000); //oifs give position in nm
							if(xpart=="-0.001")xpart="NA";
							i=findData(ptyfile,"AbsPositionValueY",i,0);
							if(i<ptyfile.length) ypart=Double.toString(AJ_Utils.parseDoubleTP(getData(ptyfile[i]))/1000); //oifs give position in nm
							if(ypart=="-0.001")ypart="NA";
						}
						gains[ch-1]=searchAndGetData(ptyfile, "PMTVoltage");
					}
				}
				endptypath=oifdir+"s_"+((chs>1)?"C"+String.format("%03d",ch):"")+((steps>1)?("Z"+String.format("%03d",steps)):"")+((tps>1)?"T001":"")+".pty";
				if(endptypath.equals(oifdir+"s_.pty"))endptypath=oifdir+"s_C001.pty";
				if(!(new File(endptypath)).exists()){
					IJ.log("Can't find "+endptypath);
				}else{
					String[] ptyfile=openZap(endptypath).split("\n");
					if(ptyfile.length>0){
						if(ch==1) {
							i=findData(ptyfile,"ExcitationOutPutLevel",0,0);
							if(i<ptyfile.length) lpowerend=Long.toString(Math.round(AJ_Utils.parseDoubleTP(getData(ptyfile[i]))*10));
						}
						gainends[ch-1]=searchAndGetData(ptyfile, "PMTVoltage");
					}
				}
			}
			
			locstr=""+xpart+ "  "+ypart + "  "+ zpart+ "  "+ zsize;
			xylist=new String[1];
			xylist[0]=locstr;
			
	 	} else {
	 		IJ.log("No prairie yet");
	 		//if regular Prairie not oif
	 		/*	xmlfile=split(File.openAsString(xmlfilepath), "\n");
					loxf=xmlfile.length;
					tindx=indexOf(xmlfile[1],"date=");
					if(tindx>-1) {
						starttimestr=substring(xmlfile[1],tindx+6,indexOf(xmlfile[1],"\" notes=")); startdate=substring(starttimestr,0,indexOf(starttimestr," "));
						vindx=indexOf(xmlfile[1],"version=");
						if(vindx>-1) {version=substring(xmlfile[1],vindx+9,indexOf(xmlfile[1],"\" date=")); }
					}
				
//					chs=0; i=0; while(indexOf(xmlfile[i], "<File channel")== -1) i++;
//					while(indexOf(xmlfile[i], "<File channel")> -1) {chs++; i++;}

					fc=-1;
					i=0;
					while(indexOf(xmlfile[i], "cycle=")== -1) {i++; if(i>50||i==xmlfile.length) {if(report) showMessage("Could not get data from xml file"); return xylist;}}
					fc=i;
					firstcyclenum=parseInt(substring(xmlfile[fc],indexOf(xmlfile[i], "cycle=")+7,indexOf(xmlfile[i], "cycle=")+8));

					if(firstcyclenum==0) n=0;
					while(indexOf(xmlfile[i], "positionCurrent_XAxis")== -1) {i++; if(i==xmlfile.length) {if(report) showMessage("Could not get data from xml file"); return xylist;}}
					fxac=i-fc;  //first Xaxis position after each cycle
					xaxispos=i;
					while(indexOf(xmlfile[i], "PVStateShard")== -1) i++;
					restlen=i-xaxispos;  //length of the rest of the values after Xaxis
				

					//set values to zero used to be here.  fz=0;
					for(i=fc; i<(fxac+fc+restlen);i++){
						if(indexOf(xmlfile[i],"opticalZoom")!=-1) zm=getXmlValue(xmlfile[i],-1);
						else if(indexOf(xmlfile[i],"pmtGain_0")!=-1) gains[0]=parseFloat(getXmlValue(xmlfile[i],0));
						else if(indexOf(xmlfile[i],"pmtGain_1")!=-1) gains[1]=parseFloat(getXmlValue(xmlfile[i],0));
						else if(indexOf(xmlfile[i],"pmtGain_2")!=-1) gains[2]=parseFloat(getXmlValue(xmlfile[i],0));
						else if(indexOf(xmlfile[i],"pmtGain_3")!=-1) gains[3]=parseFloat(getXmlValue(xmlfile[i],0));
						else if(indexOf(xmlfile[i],"laserPower_0")!=-1) lpower=getXmlValue(xmlfile[i],0);
						else if(indexOf(xmlfile[i],"objectiveLens\"")!=-1) objective=getXmlValue(xmlfile[i],0);
						else if(indexOf(xmlfile[i],"micronsPerPixel_XAxis")!=-1) xysize=parseFloat(getXmlValue(xmlfile[i],-1));
						else if(indexOf(xmlfile[i],"pixelsPerLine")!=-1) pixels=parseFloat(getXmlValue(xmlfile[i],-1));
						//else if(indexOf(xmlfile[i],"positionCurrent_ZAxis")!=-1) fz=getXmlValue(xmlfile[i],-1);
					}
					zoom=zm;

				
					badnumstr="0.1689 0.1953 1.0135"; pchange=false;
					if(indexOf(badnumstr,d2s(xysize*zm*pixels/512,4))!=-1) {xysize=propxy*512/pixels/zm; pchange=true;}
					if(version=="4.3.1.17") xysize/=parseFloat(zm);
				
					ni=i; 
					while((indexOf(xmlfile[ni], "cycle=\"")== -1 && indexOf(xmlfile[ni], "PVScan")== -1) && (ni<(xmlfile.length-1))) ni++;
					steps="1"; endofcyc=ni-1;
			 //lz=0;
					goback=true;
					while(goback){
						if(indexOf(xmlfile[ni],"pmtGain_0")!=-1) gainends[0]=parseFloat(getXmlValue(xmlfile[ni],0));
						else if(indexOf(xmlfile[ni],"pmtGain_1")!=-1) gainends[1]=parseFloat(getXmlValue(xmlfile[ni],0));
						else if(indexOf(xmlfile[ni],"pmtGain_2")!=-1) gainends[2]=parseFloat(getXmlValue(xmlfile[ni],0));
						else if(indexOf(xmlfile[ni],"pmtGain_3")!=-1) gainends[3]=parseFloat(getXmlValue(xmlfile[ni],0));
						else if(indexOf(xmlfile[ni],"laserPower_0")!=-1) lpowerend=getXmlValue(xmlfile[ni],0);
						//else if(indexOf(xmlfile[i],"positionCurrent_ZAxis")!=-1) lz=getXmlValue(xmlfile[i],-1);
						else if(indexOf(xmlfile[ni],"index=")!=-1) {
							ioindex=indexOf(xmlfile[ni],"index="); iolabel=indexOf(xmlfile[ni],"\" label=");
							steps=substring(xmlfile[ni],ioindex+7,iolabel);
							goback=false;
						}
						ni--;
					}

					while((indexOf(xmlfile[i],"positionCurrent_ZAxis")==-1) && ((i+2)<xmlfile.length)) i++;
					nzp=i-fxac-fc;
					zstepgo=true;
					if(i==xmlfile.length-1)zstepgo=false;


					timecounter=false;
					tps=1;

					for(i=0;i<xmlfile.length;i++){
						if(indexOf(xmlfile[i],"cycle=\"")>-1){
							if((indexOf(xmlfile[i],"cycle=\"")>-1)&&(i+fxac+maxOf(2,nzp)<xmlfile.length)){
								if(!timecounter) {
									xylisttemp=newArray(xylist.length+1);
								}
								xpart=getXmlValue(xmlfile[i+fxac],2);
								ypart=getXmlValue(xmlfile[i+fxac+1],2);
								zpart=getXmlValue(xmlfile[i+fxac+2],2);
								zstep="";  if(zstepgo) zstep=abs(parseFloat(getXmlValue(xmlfile[i+fxac+nzp],2))-parseFloat(zpart));
								xystring=xpart+ "  "+ypart + "  "+ zpart+ "  "+ zstep;
								if(!timecounter){
									if(xylist.length>0) if(xylist[0]==xystring)  {timecounter=true; tps++;}
									if(!timecounter) {
										xylist=addArray(xylist, xystring);
									}
								} else if(xylist[0]==xystring) tps++;
								timestemp=newArray(times.length+1);
								for(j=0;j<times.length;j++) timestemp[j]=times[j];
								timestemp[j]=parseFloat(substring(xmlfile[i+1],indexOf(xmlfile[i+1],"absolute")+14,indexOf(xmlfile[i+1],"\" index=")));
								times=timestemp;
								
							}
						}
					}
					xmlintervaltime=0;
					locs=xylist.length;
					
			 	*/
	 	}//if oifdo else
	 	int adder=0,i;
	 	double avetimeframe=0,onelessavetimeframe=0,firsttimeframe=0;
	 	if(times.size()>1){
			for(i=0;i<tps-1;i++) adder = adder + (times.get((i+1)*locs)) - times.get(i*locs);
			if(tps>2) {i--; double onelesstp=adder-(times.get((i+1)*locs) - times.get(i*locs)); onelessavetimeframe=(onelesstp/(tps-2));}
			if(tps>1) {i=0; firsttimeframe=(times.get(((i+1)*locs)) - times.get(i*locs));}
			avetimeframe=((double)adder/(double)(tps-1));
			tsize=avetimeframe;
		}else{tsize=xmlintervaltime;}
		for(i=0;i<xylist.length;i++){
			xylist[i]=xylist[i]+"  "+tsize+"  "+tps;
		}
		

		String gtxt="";
		for(i=0;i<chs;i++){
			if(!gains[i].equals("NA")){
				gtxt=gtxt+"Gain"+(i+1)+": "+gains[i];
				if(!gainends[i].equals("NA") && !gains[i].equals(gainends[i]))gtxt=gtxt+"-"+gainends[i];
				gtxt=gtxt+"    ";
			}
		}
		String lwvtxt=""; if(lwv!="NA")lwvtxt="  Wv: "+lwv;
		String avetext="";
		if(!averaging.equals("0")){avetext="  Ave:"+averaging+" "+averagingtype;}

		
		// set up output and TPI variables
		locs=xylist.length;
		
		exlocoutput="Started at:  "+starttimestr+"   Locs: "+locs+"  Zoom: "+zoom+"  Obj: "+objective+"\n";
		exlocoutput=exlocoutput+"XY: "+pixels+" x "+xysize+"um   Z: "+steps+" x "+zsize+"um   T: "+tps+" x "+tsize+"s"+avetext+"\n";
		exlocoutput=exlocoutput+gtxt+"Laser: "+lpower+((!lpowerend.equals("NA")&&!lpower.equals(lpowerend))?("-"+lpowerend):"")+lwvtxt+"\n";
		
		String[] xys;
		for(i=0;i<xylist.length;i++) {
			xys=xylist[i].split("  ");
			String step1="Location "+(i+1)+":   "+xys[0]+"  "+xys[1]+"  "+xys[2];
			String step2="  Single Image";
			if(zstepgo) step2="  Zstep: "+xys[3];
			exlocoutput=exlocoutput+step1+step2+"\n";
		}

		if(xysize>0) {cal.setUnit("microns"); cal.pixelWidth=xysize; cal.pixelHeight=cal.pixelWidth;}
		if(zsize>0) {cal.setUnit("microns"); cal.pixelDepth=zsize;}
		if(tsize>0)cal.frameInterval=tsize;
		if(hasAJZ)tps/=sls;
		if(tps>frms)totaltps=tps;
		
		//For prairie
		if(times.size()>2) {IJ.log("1st tf / ave not last / Ave time frame / XML time:  " +firsttimeframe+" / "+ onelessavetimeframe +" / "+ avetimeframe +" / "+ xmlintervaltime);}
		else if(times.size()>1) {IJ.log("Time btn 1+2 / Ave time frame / XML time:  " +firsttimeframe+" / "+ avetimeframe +" / "+ xmlintervaltime);}

			
		return exlocoutput;
	}
	
	//Prairie location utilities
	void createPrairieLocXYfile() {
		boolean addloc=false;
		String title = "Exlocs.xy";
		int loffset=0;
		String startstr="<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<StageLocations>\n";
		TextWindow tw;
		tw=(TextWindow)WindowManager.getWindow(title);
		if(tw==null) {
			tw=new TextWindow(title, 80, 20);
		} else {
			addloc=true;  //=getBoolean("OK to add to Exlocs window (no means clear it)?"); 
			if(addloc) {
				String oldwindow=tw.getTextPanel().getText();
				if(oldwindow.startsWith(startstr)) startstr=oldwindow.substring(0,oldwindow.indexOf("</StageLocations>"));
				String[] oldsplit=oldwindow.split("\n");
				loffset=oldsplit.length-3;
			}
		}
		tw.getTextPanel().clear();
		tw.append(startstr);
		for(int i=0;i<locs;i++){
			String[] parts=xylist[i].split("  ");
			tw.append("  <StageLocation index=\""+(i+loffset)+"\" x=\""+parts[0]+"\" y=\""+parts[1]+"\" z=\""+parts[2]+"\" />\n");
		}
		tw.append("</StageLocations>");
	}
	
	void printLocationMap() {
	
		ImagePlus lm=WindowManager.getImage("LocMap");
		int currmapno;
		if(lm==null) {
			IJ.newImage("LocMap", "8-bit White", 500, 500, 1);
			currmapno=0;
		}else{
			currmapno=AJ_Utils.parseIntTP(lm.getInfoProperty());
			if(currmapno<0) currmapno=0;
		}
		IJ.setForegroundColor(0,0,0);
		lm.getProcessor().setJustification(ImageProcessor.CENTER_JUSTIFY);
		lm.getProcessor().drawString("Origin",250,250);
		for(int i=0;i<locs;i++){
			String[] parts=xylist[i].split("  ");
			double x=AJ_Utils.parseDoubleTP(parts[0]);
			double y=AJ_Utils.parseDoubleTP(parts[1]);
			if(x==0 && y==0) lm.getProcessor().drawString("(+"+(i+1)+")",278,250);
			else lm.getProcessor().drawString(Integer.toString(i+currmapno+1)+"\n"+Math.round(x)+"  "+Math.round(y),(int)(-1*x/4+250),(int)(-1*y/4+250));
		}
		lm.setProperty("Info", currmapno+locs);
	}
	
	void updateImageSliceTimes() {
		updateImageSliceTimes(img,dir,starttimestr);
	}
	
	public static void updateImageSliceTimes(ImagePlus imp) {
		updateImageSliceTimes(imp,null,null);
	}
	
	public static void updateImageSliceTimes(ImagePlus imp, String dir, String starttimestr) {
		IJ.showStatus("Loading Image Slice Times");
		long sms=System.currentTimeMillis();
		if(imp==null)imp=WindowManager.getCurrentImage();
		if(imp==null) {IJ.error("No image"); return;}
		String directory;
		String[] ptypaths;
		
		if((dir!=null && !dir.isEmpty() && dir.indexOf(File.separator)>-1)) {
			directory=dir;
		}else {
			directory=imp.getInfoProperty().split("\n")[0];
		}
		if(!(directory!=null && !directory.isEmpty() && directory.indexOf(File.separator)>-1)){IJ.showMessage("Need original directory"); directory=IJ.getDirectory("");}
		if(directory==null)return;
		
		long starttime=0;
		if(starttimestr==null || starttimestr.isEmpty())starttimestr=(String)imp.getProperty("2p-starttime");
		if(starttimestr!=null && !starttimestr.isEmpty())starttime=AJ_Utils.sysTime(starttimestr);
		
		ImageStack imst=imp.getStack();
		if(imst==null) {IJ.error("Not a stack"); return;}
		String[] labels=imst.getSliceLabels();
		int nSlices=imst.getSize();
		//ptypaths=new String[nSlices];
		if(labels!=null && labels.length>0) {
			for(int i=0;i<nSlices;i++) {
				String sllabel=labels[i];
				if(sllabel!=null){
					String[] label=sllabel.split("\n");
					int ind=-1;
					for(int j=0;j<label.length;j++) {
						if(label[j].startsWith("s_")&&label[j].endsWith(".tif")) {ind=j; break;}
					}
					if(ind==-1) {IJ.error("Not an oif file (with slices labeled)"); return;}
					String slicetime=getSliceTime(directory+label[ind].replaceFirst("tif", "pty"));
					if("".contentEquals(slicetime)){IJ.log("No time for slice "+(i+1));}
					if(!sllabel.endsWith("\n") && !"".contentEquals(sllabel))sllabel+="\n";
					sllabel+="ptytime: "+slicetime;
					if(starttime>0)sllabel+="\nStarttime: "+starttime;
					if(!imp.isVisible())return;//image closed
					imst.setSliceLabel(sllabel,i+1);
				}else {IJ.log("Slice "+(i+1)+" was empty"); return;}
				IJ.showProgress((double)i/(double)(nSlices-1));
			}
			IJ.showStatus("Completed addition of pty slice times!");
			String log=IJ.getLog();
			if(log!=null) {
				String[] logs=log.split("\n");
				String ll=logs[logs.length-1];
				if(ll.startsWith("LoadImage"))IJ.log("\\Update:"+ll+"  STs took: "+(System.currentTimeMillis()-sms)/1000.0);
			}else IJ.log("STs took: "+(System.currentTimeMillis()-sms)/1000.0);
		}else IJ.error("Oif file needs slice labels");
		
	}
	
	/*
	private static String[] getSliceTimes(String[] ptyfilepaths) {
		int ptyl=ptyfilepaths.length;
		String[] alltimes=new String[ptyl];
		for(int j=0;j<ptyl;j++){
			alltimes[j]=getSliceTime(ptyfilepaths[j]);
			IJ.showProgress((double)j/(double)(ptyl-1));
		}
		IJ.showProgress(1.0);
		return alltimes;
	}
	*/
	
	private static String getSliceTime(String ptyfilepath) {
		String temp=openRaw(ptyfilepath);
		if(temp=="") {IJ.error("Bad OIF directory");return "";}
		String[] ptyfile=temp.split("\n");
		int i=0; i=findData(ptyfile,"Axis 4 Parameters",0,0,true);
		i=findData(ptyfile,"AbsPositionValue",i,0,true);
		if(i<ptyfile.length) return Double.toString(AJ_Utils.parseDoubleTP(getData(ptyfile[i]))/1000d);
		return "";
	}

	
	//
	// Utility Funcitons
	//
	
	//oif functions
	static String getData(String str){
		if(str.indexOf("=")==-1) return "";
		String[] a=str.split("="); String data=a[1];
		if(data=="")data="\"NA\"";
		if((data.indexOf("\'")>-1 || data.indexOf("\"")>-1)) data= data.substring(1,data.length()-1);
		return data;
	}
	
	static int findData(String[] xmlfile,String findtext,int starti,int limit){
		return findData(xmlfile,findtext,starti,limit,false);
	}
	
	static int findData(String[] xmlfile,String findtext,int starti,int limit,boolean zap){
		if(limit<1 || limit>(xmlfile.length))limit=(xmlfile.length);
		for(int i=starti;i<limit;i++){
			if(zap)xmlfile[i]=zap(xmlfile[i]);
			if(xmlfile[i].indexOf(findtext)> -1) {return i;} 
		}
		return limit;
	}

	//line from bigfile must start with or be close to starting with search
	static String searchAndGetData(String[] bigfile, String search) {
		for(int i=0;i<bigfile.length;i++) {
			int ios=bigfile[i].indexOf(search);
			if(ios>-1 && ios<3) {
				return getData(bigfile[i]);
			}
		}
		return "NA";
	}
	
	static String openRaw(String path){
		if(path==null)return "";
		File file=new File(path);
		if (!file.exists()){
			IJ.error("File not found");
			return "";
		}
		try {
			int len = (int) file.length();
			InputStream in = new BufferedInputStream(new FileInputStream(path));
			DataInputStream dis = new DataInputStream(in);
			byte[] buffer = new byte[len];
			dis.readFully(buffer);
			dis.close();
			char[] buffer2 = new char[buffer.length];
			for (int i=0; i<buffer.length; i++)
				buffer2[i] = (char)(buffer[i]&255);
			return new String(buffer2);
		}
		catch (Exception e) {
			IJ.error("File open error \n\""+e.getMessage()+"\"\n");
		}
		return "";
	}
	
	static String openZap(String path){
		return zap(openRaw(path));
	}

	static String zap(String s1){
		//this is from ZapGremlins macro on ImageJ website
	  int LF=10, TAB=9;
	  String s2="";
	  for (int i=0; i<s1.length(); i++) {
	      int c = (int) s1.codePointAt(i);
	      if (c==LF)
	          s2=s2+"\n";
	      else if (c==TAB)
	    	  s2=s2+" ";
	      else if (c>=32 && c<=127)
	    	  s2=s2+((char) c);
	  }
	  return s2;
	}
	
	/*
	private Object getXmlValue(String line, int dec){

		float val=0;
		int lioq=line.lastIndexOf("\"");
		if(lioq==-1)lioq=line.length();
		int iov=java.lang.Math.max(line.indexOf("value="),1);
		String valstr=line.substring(iov+7, lioq);
		try{
			val=Float.parseFloat(valstr);
		}catch(Exception e){
			return valstr;
		}
		if(dec>-1) val=(float) (Math.round(val*(float)Math.pow(10, dec)) / Math.pow(10, dec));
		return val;
	}
	
	private int[] getCfgCch(File[] fl){			//requires indexOfArray() as well
		int li=0; while(!fl[li].getName().endsWith("Config.cfg") && li<fl.length-1)li++;
		int ch1num=0, ch2num=0, ch1temp=0, ch2temp=0, adj=-1;
		if(fl[li].getName().endsWith("Config.cfg")){
			String cfgfilestr=openRaw(fl[li].getAbsolutePath());
			String[] cfgfile=cfgfilestr.split("\n");
			for(int i=0;i<9;i++){
				if(cfgfile[i].startsWith("    <PVWindow x")){
					if(adj==-1){
						String[] line=cfgfile[i].split(" "); String[] part=line[3].split("\"");
						if(part[0]=="width=") adj=2; else adj=0;
					}
					if(cfgLine(cfgfile[i],4+adj)==1) ch1temp=cfgLine(cfgfile[i],3+adj);
					if(cfgLine(cfgfile[i],6+adj)==1) ch2temp=cfgLine(cfgfile[i],5+adj);
					if(cfgLine(cfgfile[i],4+adj)==1 && cfgLine(cfgfile[i],6+adj)==1){ch1num=cfgLine(cfgfile[i],3+adj); ch2num=cfgLine(cfgfile[i],5+adj);}
				}
			}
		}
		if(ch1num==0 || ch1num==-16711423) ch1num=ch1temp;
		if(ch2num==0 || ch2num==-16711423) ch2num=ch2temp;
		return new int[]{ch1num,ch2num};
	}
	private int cfgLine(String linestr, int ind){
		int result=-1;
		String[] line=linestr.split(" ");
		String[] part=line[ind].split("\"");
		if(part.length>0) {
			if(part[1].equals("True")) result=1;
			else if(part[1]=="False") result=0;
			else result=AJ_Utils.parseIntTP(part[1]);
		}
		return result;
	}
	*/
	
	void cleanUp() {
		preStrip(false);
	}
	
	void addThisAdjustmentListener() {
		preStrip(true);
	}
	
	void preStrip(boolean add){
		preStrip(img, add);
		if(zimg!=null)preStrip(zimg,add);
	}
	
	void preStrip(ImagePlus imp, boolean add) {
		if(imp==null)return;
		ImageWindow imgwin=imp.getWindow();
		Component[] cps=((Container) imgwin).getComponents();
		for(int j=0;j<cps.length;j++) {
			if(cps[j].getClass().getName().contains("Scroll")) {
				AdjustmentListener[] mls=(AdjustmentListener[]) cps[j].getListeners(AdjustmentListener.class);
				for(int i=0;i<mls.length;i++) {
					if(mls[i].getClass().getName().startsWith(this.getClass().getName())){
						((ScrollbarWithLabel)cps[j]).removeAdjustmentListener(mls[i]);
						IJ.log("Removed old aL"+mls[i]);
					}
				}
				if(add) {
					((ScrollbarWithLabel)cps[j]).addAdjustmentListener(this);
					//IJ.log("added "+cps[j].getClass().getName());
				}
			}
		}
	}
	
	public synchronized void adjustmentValueChanged(AdjustmentEvent e){
		//IJ.log("\\Update:source: "+e.getSource()+" type:"+e.getAdjustmentType()+" val:"+e.getValue()+" isadj:"+e.getValueIsAdjusting()+" mp:"+mousePressed);
		if(e.getValueIsAdjusting()) this.mousePressed=true;
		else this.mousePressed=false;
	}

	/*
    public void mouseExited(MouseEvent e) {} 
    public void mouseClicked(MouseEvent e) {}	
    public void mouseEntered(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {
    	this.mousePressed=false;
    }	
    public void mousePressed(MouseEvent e) {
    	this.mousePressed=true;
    	IJ.log("MP");
    } 
    */
	
	public void addUpdateButton() {
		ImagePlus imp=img;
		if(imp.getWindow()==null)return;
		if(!(imp.getWindow() instanceof StackWindow))return;
		StackWindow stwin=(StackWindow) imp.getWindow();
		ScrollbarWithLabel scr=getLastSBWL(stwin);
		removeUpdateButton(stwin,scr);
		
		if(scr!=null) {
			Button updateButton= new Button("U");
			updateButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					Thread thread=new Thread() {
						public void run() {
							updating=true;
							String[] upt=new String[] {"U","P","D","A","T","I","N","G"};
							int i=0;
							while(updating) {
								updateButton.setLabel(upt[i]);
								updateButton.repaint();
								i++;
								if(i==upt.length)i=0;
								IJ.wait(100);
							}
						}
					};
					thread.start();
					updateImage();
					updating=false;
				}
			});
			scr.add(updateButton,BorderLayout.EAST);
			stwin.pack();
		}
	}
	
	public void removeUpdateButton() {
		if(img==null)return;
		if(img.getWindow()==null)return;
		if(!(img.getWindow() instanceof StackWindow))return;
		removeUpdateButton((StackWindow) img.getWindow(),null);
	}
	
	private ScrollbarWithLabel getLastSBWL(StackWindow stwin) {
		Component[] comps=stwin.getComponents();
		ScrollbarWithLabel scr=null;
		for(int i=0;i<comps.length;i++) {
			if(comps[i] instanceof ij.gui.ScrollbarWithLabel) {
				scr=(ScrollbarWithLabel)comps[i];
			}
		}
		return scr;
	}
	
	private void removeUpdateButton(StackWindow stwin, ScrollbarWithLabel scr) {
		if(scr==null) scr=getLastSBWL(stwin);
		if(scr==null) return;
		Component[] comps=scr.getComponents();
		for(int i=0;i<comps.length;i++) {
			if(comps[i] instanceof Button) {
				if(((Button)comps[i]).getLabel().equals("U")) {
					scr.remove(comps[i]);
					stwin.pack();
				}
			}
		}
	}
	    

}