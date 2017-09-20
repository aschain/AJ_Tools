package AJ;
import ij.plugin.PlugIn;
import ij.gui.*;
import ij.*;
import java.io.*;
import java.util.ArrayList;
import java.awt.Color;
import ij.process.*;

/**
 * This is a template for a plugin that does not require one image
 * (be it that it does not require any, or that it lets the user
 * choose more than one image in a dialog).
 */
public class TwoPhoton_Import implements PlugIn {

	ImagePlus img=null,zimg=null;
	String infofile;
	String[] fltif;

	String RGBname;
	int scale=100,chs=0,sls=0,frms=0,sl=0,fr=0,locs=1,loc=0;
	double xysize=0.497d,zsize=1.0d,tsize=1.0d;
	ArrayList<Integer> slicearray;
	int cycind,slind,chind,wid,hei;
	int firsttif=0;
	boolean hasT,hasZ,hasC,cont,oifdo=false;
	boolean dotimes=false,dogamma=false,noask=false,virtual=false,dozee=false;
	LUT[] stackluts={LUT.createLutFromColor(Color.red),LUT.createLutFromColor(Color.green),LUT.createLutFromColor(Color.blue),LUT.createLutFromColor(Color.magenta),LUT.createLutFromColor(Color.cyan),LUT.createLutFromColor(Color.yellow)};

	
	String[] xylist;
	String exlocoutput="";
	
	final int MAXCHS=4;
	class FolderType{
		public boolean oif, prairie, hastifs;
		public int folders;
		public FolderType(boolean oif, boolean prairie, boolean hastifs, int folders) {
			this.oif=oif;
			this.prairie=prairie;
			this.hastifs=hastifs;
			this.folders=folders;
		}
	}
	final FilenameFilter nohidden = new FilenameFilter(){
			public boolean accept(File dir, String name){
				return !(name.startsWith(".") || name.equals("Thumbs.db"));
			}
		};
	final FilenameFilter ptyfilter = new FilenameFilter(){
		public boolean accept(File dir, String name){
			return ( !name.startsWith(".") && (name.endsWith(".pty") && !name.startsWith("s_Thumb")));
		}
	};
	/*FilenameFilter justtifs = new FilenameFilter(){
		public boolean accept(File dir, String name){
			return ( (!(name.startsWith(".") || name.equals("Thumbs.db"))) && (name.endsWith(".tif") && !name.endsWith("Reference.tif")));
		}
	};*/
	
	
	/**
	 * This method gets called by ImageJ / Fiji.
	 *
	 * @param arg can be specified in plugins.config
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@Override
	public void run(String arg) {
		//resetVars();
		if(arg.equals("updateImageSliceTimes")) {updateImageSliceTimes(); return;}
		openTwoPhoton(IJ.getDirectory(""));
	}
		
	/*
	private void resetVars(){
		img=null; zimg=null;
		locs=1;
		cont=false;
		oifdo=false;
		noask=false;
		scale=100;
		xylist=new String[0];
		RGBname=""; infofile=""; cycind=0; slind=0; chind=0; hasT=false; hasZ=false; hasC=false;
		stackluts=new LUT[]{LUT.createLutFromColor(Color.red),LUT.createLutFromColor(Color.green),LUT.createLutFromColor(Color.blue),LUT.createLutFromColor(Color.magenta),LUT.createLutFromColor(Color.cyan),LUT.createLutFromColor(Color.yellow)};
	}
	*/
	
	private FolderType folderType(File[] fl) {
		//for(int i=0;i<fl.length;i++){
		//	String name=fl[i].getName();
		//	if(name.endsWith(".xml") || name.endsWith(".cfg")) {return false;}
		//	if(name.endsWith(".tif") && name.startsWith("s_")){return true;}
		//}
		boolean prairie=false,hastifs=false, oifdo=false;
		int dcount=0;
		for(int i=0;i<fl.length;i++){
			String name=fl[i].getName();
			if(name.endsWith(".xml") || name.endsWith(".cfg")) {oifdo=false; prairie=true; if(hastifs)break;}
			if(name.endsWith(".tif")){
				hastifs=true;
				if(prairie) break;
				if(name.startsWith("s_")) {oifdo=true; break;}
			}
			if(fl[i].isDirectory()) dcount++;
		}
		return new FolderType(oifdo, prairie, hastifs, dcount);
	}
	
	public void openTwoPhoton(String dir){
		
		//test directory for oif, prairie, empty, tifs, or more folders
		if(dir==null)return;
		File f= new File(dir);
		if(!f.exists() || !f.isDirectory()) return;
		File[] fl=f.listFiles(nohidden);

		if(fl.length==0) {IJ.log("Directory is empty"); return;}
		
		FolderType ftype=folderType(fl);
		oifdo=ftype.oif;
		if(!ftype.hastifs && ftype.folders==0) {
			IJ.log("\n"+dir+" has nothing to open");
			return;
		}
		if(!ftype.oif && !ftype.prairie){
			openAllFolder(dir);
			return;
		}
		
		
		//open oif or prairie folder
		int tpstart=0,tpend=1;
		
		RGBname=f.getName();
		
		setup(fl);
		//get locations
		exLoc();
		
		updateFiles(fl);
		
		//Use open window if same name
		img=WindowManager.getImage(RGBname);
		if(img!=null && !askYesNoCancel("Use open","Use open window?"))img=null;
		if(img!=null){
			cont=true;
			dotimes=false;
			WindowManager.setCurrentWindow(img.getWindow());
			sls=img.getNSlices(); chs=img.getNChannels(); fr=img.getNFrames();
			wid=img.getWidth(); hei=img.getHeight();
			virtual=img.getStack().isVirtual();
			if(img.isComposite()){
				LUT[] luts=img.getLuts();
				for(int i=0;i<luts.length;i++){
					stackluts[i]=luts[i];
				}
			}else stackluts[0]=LUT.createLutFromColor(Color.white);
			zimg=WindowManager.getImage("MAX_"+RGBname);
			if(zimg!=null) dozee=true;
		}else{
			//Open dialog if image is not open already
			boolean onecyc=((fr==1) || (!hasZ));
			long tolf=checkTime(fl[fl.length-2]);
			if(!noask && ((!onecyc && !cont) || cont || locs>1)){
				String tpstr="1-"+fr;
				GenericDialog gd=new GenericDialog(RGBname);
				if(!onecyc){
					gd.addStringField("Timepoints", tpstr);
				}
				gd.addCheckbox("Include slice times?", dotimes);
				if(locs>1){gd.addNumericField("Location:", 1, 0, 3, "out of "+locs);}
				gd.addCheckbox("Virtual?",virtual);
				if(hasZ)gd.addCheckbox("Z-Project?", dozee);
				gd.addCheckbox("Half Gamma?", dogamma);
				if(cont) gd.addCheckbox("Continue Update?",false);
				if(tolf<60*60) gd.addMessage("Time since last file: "+ Math.round((double) tolf));
				// if(cont) gd.addCheckbox("Web",false);
				// if(cont) gd.addCheckbox("Set an event to mark",false);
				gd.showDialog();
				
				if(!onecyc) {
					tpstr=gd.getNextString();
				}
				dotimes=gd.getNextBoolean();
				if(locs>1) loc=(int) gd.getNextNumber(); else loc=1;
				loc--;
				virtual=gd.getNextBoolean();
				if(hasZ)dozee=gd.getNextBoolean();else dozee=false;
				dogamma=gd.getNextBoolean();
				if(cont) cont=gd.getNextBoolean();
				//if(cont){
				//	web=gd.getCheckbox();
				//	if(gd.getNextBoolean()){ 
				//		Dialog.create("set event");
				//		Dialog.addString("Time or time point of event","12:20:23");
				//		Dialog.addString("Event name:","CGRP");
				//		Dialog.show();
				//		eventtime=Dialog.getString();
				//		eventname=Dialog.getString();
				//	}
				if(locs>1) RGBname=RGBname+"-loc"+(loc+1);
				int hyph=tpstr.indexOf("-");
				if(hyph==-1){tpend=parseIntTP(tpstr); tpstart=tpend-1; dozee=false;}
				else {tpstart=parseIntTP(tpstr.substring(0,hyph))-1;
					tpend=parseIntTP(tpstr.substring(hyph+1,tpstr.length()));}
				if(tpstart<0)tpstart=0; if(tpend<0) tpend=fr;
				if(cont) {tpstart=0; tpend=fr; dotimes=false;}
			}
			
			if(!virtual && ((IJ.maxMemory()-IJ.currentMemory())<((fl[firsttif].length())*(tpend-tpstart)*slicearray.get(loc))))
				if(askYesNoCancel("Virtual","Maybe not enough memory, open as virtual?"))virtual=true;

			if(virtual) {
				scale=(int) IJ.getNumber("Change this from 100 to scale instead of Virtual stack",scale);
				if(scale==100){dozee=false; dogamma=false;}
			}
			
			//load the image
			if(IJ.getLog()!=null)IJ.log("");
			IJ.log(f.getParentFile().getName()+File.separator+RGBname+":");
			IJ.log(exlocoutput);
			img=loadImage(dir, tpstart,tpend);
			ImagePlus hsimg=ij.plugin.HyperStackConverter.toHyperStack(img, chs, slicearray.get(loc), tpend-tpstart);
			img.close();
			img=hsimg;
			((CompositeImage)img).setLuts(stackluts);
			img.setTitle(RGBname);
			img.show();
			 
			//revisit this- do we need zsize, tsize any more?
			img.setProperty("Info", dir+"\n"+exlocoutput);
			if(xylist.length>0) {
				String[] xys=xylist[loc].split("  ");
				if(xys.length>3) zsize=Double.parseDouble(xys[3]);
			}
			ij.measure.Calibration cal=new ij.measure.Calibration();
			cal.setUnit("microns"); cal.pixelWidth=xysize; cal.pixelHeight=cal.pixelWidth; cal.pixelDepth=zsize; cal.frameInterval=tsize;
			if(xysize!=0) img.setCalibration(cal);
			
			if(dotimes) updateImageSliceTimes(img, dir);
			//----------------
		}
		if(cont) startContinuousUpdate();
		
		return;
	}

	public void updateImageSliceTimes() {updateImageSliceTimes(null, null);}
	
	public void updateImageSliceTimes(ImagePlus imp, String dir) {
		if(imp==null)imp=WindowManager.getCurrentImage();
		if(imp==null) {IJ.error("No image"); return;}
		String directory;
		String[] ptypaths;
		
		if(imp==img && (dir!=null && !dir.isEmpty() && dir.indexOf(File.separator)>-1)) {
			directory=dir;
		}else {
			directory=imp.getInfoProperty().split("\n")[0];
		}
		
		if(!(directory!=null && !directory.isEmpty() && directory.indexOf(File.separator)>-1)){IJ.showMessage("Need original directory"); directory=IJ.getDirectory("");}
		
		ImageStack imst=imp.getStack();
		if(imst==null) {IJ.error("Not a stack"); return;}
		String[] labels=imst.getSliceLabels();
		int nSlices=imst.getSize();
		ptypaths=new String[nSlices];
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
					ptypaths[i]=directory+label[ind].replaceFirst("tif", "pty");
				}else {IJ.log("Slice "+(i+1)+" was empty"); return;}
			}
			String[] slicetimes=getSliceTimes(ptypaths);
			if(slicetimes.length==0) {IJ.error("No slice times"); return;}
			for(int i=0;i<nSlices;i++) {
				String replace;
				if(labels[i].endsWith("\n"))replace=labels[i]+slicetimes[i];
				else replace=labels[i]+"\n"+slicetimes[i];
				imp.getStack().setSliceLabel(replace,i+1);
			}
			
		}
		
	}
	
	private void setup(File[] fl){
		
		String lastfile="";
		for(int i=0;i<fl.length;i++){
			lastfile=fl[fl.length-1-i].getName();
			if(lastfile.endsWith(".tif"))i=fl.length;
		}
		if(!lastfile.endsWith(".tif")){IJ.error("No tifs in folder");}
		
		if(oifdo){
			//file info
			RGBname=RGBname.substring(0,RGBname.length()-6);
			infofile=fl[0].getParentFile().getParent()+File.separator+RGBname;
			cycind=lastfile.indexOf("T")+1; slind=lastfile.indexOf("Z")+1; chind=lastfile.indexOf("C")+3;
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
					blue=Integer.parseInt(lutstr[2].substring(9,10))==0?0:255; green=Integer.parseInt(lutstr[12].substring(9,10))==0?0:255; red=Integer.parseInt(lutstr[18].substring(9,10))==0?0:255;
					//if(blue>0){res=Color.blue; if(green>0) {res=Color.cyan; if(red>0) res=Color.white;}else if(red>0) {res=Color.magenta;}}
					//else if(green>0){res=Color.green; if(red>0) res=Color.yellow;}
					//else if(red>0) res=Color.red;
					ind = parseIntTP(fn.substring(5,6))-1;
					if(ind==-2) ind=n;
					stackluts[ind]=LUT.createLutFromColor(new Color(red,green,blue));
	 				n=ind+1;
				}
			}
		}else{
			//File info
			cycind=lastfile.lastIndexOf("Cycle")+5; slind=lastfile.length()-7; chind=lastfile.indexOf("_Ch")+3;
			while(lastfile.substring(cycind+3,cycind+4)!="_") cycind++;
			int xmlindex=0; if(!fl[xmlindex].getName().endsWith("xml")) xmlindex=1;
			infofile=fl[xmlindex].getAbsolutePath();
			//String zoom="NA";
			//if(cycind<5) oifnotime=true;
			hasT=cycind>5; hasZ=true; hasC=true;
			if(chind<3) {IJ.error("Error with tif file name, no Ch found in:\n"+lastfile); return;}
			
			//color **need to fix
			//chnums=getCfgCch(fl);
			//if(indexOfArray(preds,chnums[0])>-1) {stackluts[0]="Red"; stackluts[1]="Green"; stackluts[2]="Blue";}
			//if(indexOfArray(pgreens,chnums[0])>-1) {stackluts[0]="Green"; stackluts[1]="Blue"; stackluts[2]="Red";}
			//if(chnums[0]==-16766721) {stackluts[0]="Blue"; stackluts[1]="Red"; stackluts[2]="Green";}
		}
		
		for(int i=0;i<fl.length; i++){if(fl[i].getName().endsWith(".tif")){firsttif=i;i=fl.length;}}
		
		return;
	}

	private void updateFiles(File[] fl){
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
		int curcyc=0,curch=0,cursl=0,topprevslice=0;
		for(int i=0;i<fltif.length;i++){
			if(hasT){
				curcyc=Integer.parseInt(fltif[i].substring(cycind, cycind+3));
				if(cyca.indexOf(curcyc)==-1) cyca.add(curcyc);
				frms=cyca.size();
			}else frms=1;
			if(hasC){
				curch=Integer.parseInt(fltif[i].substring(chind, chind+1));
				if(cha.indexOf(curch)==-1)cha.add(curch);
				chs=cha.size();
			}else chs=1;
			if(hasZ){
				cursl=Integer.parseInt(fltif[i].substring(slind, slind+3));
			}else cursl=1;
			if(oifdo){
				if(hasT){
					if(curcyc==frms) sl=cursl;
				}
			}else{
				if((cursl==1 && curch==1) && slicearray.indexOf(topprevslice)==-1 && topprevslice!=0) slicearray.add(topprevslice);
				topprevslice=cursl;
			}
			sls=Math.max(sls, cursl);
		}
		if(slicearray.size()==0)slicearray.add(sls);
		locs=slicearray.size();
		if(locs<xylist.length){
			locs=xylist.length;
			//This is only for the case that all locations have the same number of slices
			for(int i=1; i<locs; i++) slicearray.add(sls);
		}
		fr=frms;
		if(frms>1 && locs>1){
			if(hasZ&&hasT && (sl!=(int)slicearray.get((fr-1)%locs))) fr--;
		}
		fr/=locs;
		if(chs==1)stackluts[0]=LUT.createLutFromColor(Color.white);
	}
	
	private ImagePlus loadImage(String dir, int tpstart, int tpend){
		ImagePlus newimg;
		String temppath=IJ.getDirectory("temp")+"AJlist.txt";
		int totalslices=0;
		for(int i=0;i<slicearray.size();i++) totalslices+=slicearray.get(i);
		int sluptoloc=0;
		for(int i=0;i<loc;i++) sluptoloc+=slicearray.get(i);
		int sls=slicearray.get(loc); int frms=tpend-tpstart;
		
		
		try{
			File f=new File(temppath);
			PrintStream ps=new PrintStream(f);
			
			String printer;
			for (int i=tpstart; i<tpend; i++) {
				for(int j=0;j<slicearray.get(loc);j++){
					for(int k=0;k<chs;k++){
						if(oifdo){
							printer="s_"+(hasC?("C"+pad(k+1,3)):"")+(hasZ?("Z"+pad(j+1,3)):"")+(hasT?("T"+pad(i+1,3)):"")+".tif";
						}else{
							printer=fltif[(i*chs*totalslices)+(sluptoloc*chs)+(k*slicearray.get(loc))+j];
						}
						ps.println(dir+printer);
					}
				}
			}
			ps.close();
		}catch (Exception e){
			IJ.error("Could not write to temp file");
		}
		
		long sms=System.currentTimeMillis();
		IJ.run("Stack From List...", "open="+temppath+(virtual?" use":""));
		double sfltime=(System.currentTimeMillis()-sms)/1000;
		sfltime=Math.max(sfltime,0.001d);
		newimg=WindowManager.getCurrentImage();
		int wid=newimg.getWidth(); int hei=newimg.getHeight();
		int bd=newimg.getBitDepth(); if(bd==24)bd=32;
		double mbps=((double)((bd/8)*wid*hei*chs*sls*frms/1024/1024))/sfltime;
		IJ.log("SFL took "+sfltime+"s, "+Math.round(mbps)+"MB/s");
		
		if(scale!=100){
			IJ.run("Size...", "width="+(scale/100*wid)+" height="+(scale/100*hei)+" constrain average interpolation=Bilinear");
			wid=newimg.getWidth(); hei=newimg.getHeight();
		}
		if(dogamma) IJ.run("Gamma...", "value=0.50 stack");
		return newimg;
	}
	
	private void startContinuousUpdate(){
		IJ.log("Start cont");
	}

	public void openAllFolder(String path){
		if(path==null)return;
		File f= new File(path);
		if(!f.exists() || !f.isDirectory()) return;
		File[] fl=f.listFiles(nohidden);

		if(fl.length==0) {IJ.log("Directory is empty"); return;}
		
		//OIF compatibility + tests for empty folders + recurse folders----------
		boolean go=true,recurse=false,dofs=false,dotifs=false,skipopen=false,hastifs=false;
        String name;
        int i,dcount=0;
		for(i=0;i<fl.length && go;i++){
			name=fl[i].getName();
			if(name.endsWith(".tif")){
				hastifs=true;
			}
			if(fl[i].isDirectory()) dcount++;
		}
		if(!hastifs && dcount==0) {
			IJ.log("\n"+path+" has nothing to open");
			return;
		}
		
		String filterstring="";
		if(dcount>0) dofs=true;
		if((dcount==1)) {
			openTwoPhoton(path); return;
		}else {
			String checktext;
			GenericDialog gd = new GenericDialog("Open all");
			if(dofs) checktext="Open all folders?";
			else checktext="Open all tifs in folder?";
			gd.addCheckbox(checktext,true);
			if(dofs) gd.addCheckbox("Recurse?",false);
			gd.addCheckbox("Skip if already open?",true);
			gd.addStringField("Filter: ","");
			gd.addMessage("Wildcard [*] at start or end of filter means");
			gd.addMessage("that it ends with or starts with the string.");
			gd.showDialog();
			if(dofs) dofs=gd.getNextBoolean();
			else dotifs=gd.getNextBoolean();
			if(dofs) recurse=gd.getNextBoolean();
			skipopen=gd.getNextBoolean();
			filterstring=gd.getNextString();
		}
		if(dofs || dotifs){
			for(i=0;i<fl.length;i++) {
				go=true; name=fl[i].getName();
				if(name.endsWith(".tif")|| fl[i].isDirectory()){
					if(filterstring!=""){
						go=false; 
						if(filterstring.startsWith("*")) {
							int mod=0; 
							String endfilterstring=filterstring.substring(1,filterstring.length());
							// if(fl[i].isDirectory()) mod=1;else
							if(!filterstring.endsWith("tif")&&name.endsWith(".tif")) mod=4;
							name=name.substring(0,name.length()-mod);
							go=name.endsWith(endfilterstring);
						} else if(filterstring.endsWith("*")) {
							String stfilterstring=filterstring.substring(0,filterstring.length()-1);
							go=name.startsWith(stfilterstring);
						} else go=(name.indexOf(filterstring)!=-1);
					}
					if(skipopen){
						if(name.endsWith(".files")){name=name.substring(0,name.length()-6);} //-7 if directories end in slash
						if(WindowManager.getImage(name)!=null) {IJ.log(i+" Skipping "+name+", already open"); go=false;}
					}
					if((go && dofs) && fl[i].isDirectory() && !name.startsWith("SingleImage-") && !name.startsWith("Projection")) {
						String sc=fl[i].getAbsolutePath();
						//don't open mosaic in folder unless specified
						if(name.startsWith("FV10_")){if(askYesNoCancel("FV10","Open "+fl[i]+"?")) openTwoPhoton(sc);}
						else {
							if(recurse) openTwoPhoton(sc);
						}
					}
					if(go && dotifs && name.endsWith(".tif"))
						IJ.openImage(fl[i].getAbsolutePath());
				}
			}
		}
	}
	
	public void continuousUpdate(String path){
		String title="Time Series Clock";
		String title2="["+title+"]";
		
	}
	
	public void printExLoc(String dir){
		
		//test directory for oif, prairie, empty, tifs, or more folders
		if(dir==null)return;
		File f= new File(dir);
		if(!f.exists() || !f.isDirectory()) return;
		File[] fl=f.listFiles(nohidden);

		if(fl.length==0) {IJ.log("Directory is empty"); return;}
		
		FolderType ftype=folderType(fl);
		oifdo=ftype.oif;
		if(!ftype.hastifs && ftype.folders==0) {
			IJ.log("\n"+dir+" has nothing to open");
			return;
		}
		if(!ftype.oif && !ftype.prairie){
			IJ.log("\n"+dir+" has nothing to open");
			return;
		}
		RGBname=f.getName();
		
		setup(fl);
		//get locations
		exLoc();
		IJ.log("");
		IJ.log(exlocoutput);
		
		
	}
	
	public void exLoc(){
		exlocoutput="";
		xylist=new String[0];
		xysize=0.497d; zsize=1.0d; tsize=1.0d;
		
		if(!infofile.endsWith("xml") && !infofile.endsWith("oif")) return;
		if(oifdo && !infofile.endsWith(".oif")) return;
		
		ArrayList<Integer> times=new ArrayList<Integer>();

		String starttime="I dunno", lwv="NA";
		String zoom="NA", lpower="NA", lpowerend="NA",objective="NA",pixels="0";
		String averaging="0", averagingtype="None";
		String[] gains=new String[MAXCHS], gainends=new String[MAXCHS];
		
		String xpart="NA",ypart="NA",zpart="NA",locstr;
		
		double xmlintervaltime=0, totaltime;
		int steps=1, tps=1, chs=0;
		boolean zstepgo=true;
		
		//String startdate="", version="NA";
		//double zpartend, xtotalsize, ytotalsize
		//boolean pchange=false;
		
 		
	 	if(oifdo){   //for olympus oifs
	 		int i=0;

	 		String[] xmlfile=openZap(infofile).split("\n");
	 		locs=1; //oifs don't have multiple locations as far as I know.
	 		
	 		i=findData(xmlfile,"ImageCap",0,50); //typo of ImageCaptureDate is currently ImageCaputreDate
	 		if(i>=50|| i==xmlfile.length) {IJ.error("Could not get data from oif file"); return;}
	 		starttime=getData(xmlfile[i]);
	 		//version=getData(xmlfile[xmlfile.length-1]);
	 		//Averaging, called IntegrationCount and IntegrationType, are 2 and 3 after Capture Date
	 		averaging=getData(xmlfile[i+2]);
	 		averagingtype=getData(xmlfile[i+3]);
	 		//Laser Wavelength   also we get it later with Laser 0 parameters
	 		i=findData(xmlfile,"LaserWavelength",i,0);
	 		lwv=""+getData(xmlfile[i]);
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
	 		chs=parseIntTP(getData(xmlfile[i]));
	 		//ZSize
	 		//it is the actual Z position (in nm)
	 		//i=findData(xmlfile,"AxisCode=\"Z",i,0);
	 		//i=findData(xmlfile,"EndPosition",i,0);
	 		//zpartend=parseDoubleTP(getData(xmlfile[i]))/1000; //oifs give z in nm not um
	 		//Z Step Interval
	 		i=findData(xmlfile,"Interval",i,0);
	 		zsize=parseDoubleTP(getData(xmlfile[i]))/1000; //oifs give z in nm not um
	 		//Number of Slices
	 		i=findData(xmlfile,"MaxSize",i,0);
			steps=Math.max(parseIntTP(getData(xmlfile[i])),1);
			//Z position
			i=findData(xmlfile,"StartPosition",i,0);
			zpart=Double.toString(parseDoubleTP(getData(xmlfile[i]))/1000); //oifs give z in nm not um
			//Time in total
			i=findData(xmlfile,"AxisCode=\"T",i,0);
			i=findData(xmlfile,"EndPosition",i,0);
			totaltime=parseDoubleTP(getData(xmlfile[i]))/1000d; //oifs give t in ms
			//Timepoints number
			i=findData(xmlfile,"GUI MaxSize",i,0);
			i=findData(xmlfile,"MaxSize",i+1,0); //+1 because we want the second MaxSize not GUI MaxSize
			tps=Math.max(parseIntTP(getData(xmlfile[i])),1);
			//Time interval
			i=findData(xmlfile,"Interval",i-2,0);
			xmlintervaltime=parseDoubleTP(getData(xmlfile[i]))/1000d;
			if(xmlintervaltime==0 || xmlintervaltime==-0.001){xmlintervaltime=(double)Math.round(totaltime/tps*1000d)/1000d;}
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
			if(i<xmlfile.length)xysize=parseDoubleTP(getData(xmlfile[i]));
			if(xysize==-1)xysize=1;

			//olympus stores xy stage data in the pty file in the oif.files directory sometimes
	 		String oifdir=infofile+".files"+File.separator;	 		
	 		
 			String startptypath,endptypath;

			for(int ch=1;ch<=chs;ch++) {
				startptypath=oifdir+"s_"+((chs>1)?"C00"+ch:"")+((steps>1)?"Z001":"")+((tps>1)?"T001":"")+".pty";
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
							if(i<ptyfile.length) lpower=Long.toString(Math.round(parseDoubleTP(getData(ptyfile[i]))*10));
							if(lpower=="-10")lpower="NA";
							i=findData(ptyfile,"AbsPositionValueX",0,0);
							if(i<ptyfile.length) xpart=Double.toString(parseDoubleTP(getData(ptyfile[i]))/1000); //oifs give position in nm
							if(xpart=="-0.001")xpart="NA";
							i=findData(ptyfile,"AbsPositionValueY",i,0);
							if(i<ptyfile.length) ypart=Double.toString(parseDoubleTP(getData(ptyfile[i]))/1000); //oifs give position in nm
							if(ypart=="-0.001")ypart="NA";
						}
						gains[ch-1]=searchAndGetData(ptyfile, "PMTVoltage");
					}
				}
				endptypath=oifdir+"s_"+((chs>1)?"C00"+ch:"")+((steps>1)?("Z"+pad(steps,3)):"")+((tps>1)?"T"+pad(tps,3):"")+".pty";
				if(!(new File(endptypath)).exists()){
					IJ.log("Can't find "+endptypath);
				}else{
					String[] ptyfile=openZap(endptypath).split("\n");
					if(ptyfile.length>0){
						if(ch==1) {
							i=findData(ptyfile,"ExcitationOutPutLevel",0,0);
							if(i<ptyfile.length) lpowerend=Long.toString(Math.round(parseDoubleTP(getData(ptyfile[i]))*10));
						}
						gainends[ch-1]=searchAndGetData(ptyfile, "PMTVoltage");
					}
				}
			}
			
			locstr=""+xpart+ "  "+ypart + "  "+ zpart+ "  "+ zsize;
			xylist=new String[1];
			xylist[0]=locstr;
			
	 	} else {
	 		//if regular Prairie not oif
	 		/*	xmlfile=split(File.openAsString(xmlfilepath), "\n");
			loxf=xmlfile.length;
			tindx=indexOf(xmlfile[1],"date=");
			if(tindx>-1) {
				starttime=substring(xmlfile[1],tindx+6,indexOf(xmlfile[1],"\" notes=")); startdate=substring(starttime,0,indexOf(starttime," "));
				vindx=indexOf(xmlfile[1],"version=");
				if(vindx>-1) {version=substring(xmlfile[1],vindx+9,indexOf(xmlfile[1],"\" date=")); }
			}
		
//			chs=0; i=0; while(indexOf(xmlfile[i], "<File channel")== -1) i++;
//			while(indexOf(xmlfile[i], "<File channel")> -1) {chs++; i++;}

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
		
		locs=xylist.length;

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
		
		exlocoutput="Started at:  "+starttime+"   Locs: "+locs+"  Zoom: "+zoom+"  Obj: "+objective+"\n";
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
		//For prairie
		if(times.size()>2) {IJ.log("1st tf / ave not last / Ave time frame / XML time:  " +firsttimeframe+" / "+ onelessavetimeframe +" / "+ avetimeframe +" / "+ xmlintervaltime);}
		else if(times.size()>1) {IJ.log("Time btn 1+2 / Ave time frame / XML time:  " +firsttimeframe+" / "+ avetimeframe +" / "+ xmlintervaltime);}

			/*
			if(pchange) print("Detected Old 2p bad size, changed based on propxy: "+propxy);
			addloc=false;
			if(printxyfile){
				title1 = "Exlocs.xy";
				title2 = "["+title1+"]";
				f = title2;
				loffset=0;
				startstr="<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<StageLocations>\n";
				addloc=false;
				if (isOpen(title1)) {
					selectWindow(title1);
					addloc=true;  //=getBoolean("OK to add to Exlocs window (no means clear it)?"); 
					if(addloc) {
						oldwindow=getInfo("window.contents");
						if(startsWith(oldwindow,startstr)) startstr=substring(oldwindow,0,indexOf(oldwindow,"</StageLocations>"));
						oldsplit=split(oldwindow,"\n");
						loffset=oldsplit.length-3;
					}
				} else {
					if (getVersion>="1.41g") run("Text Window...", "name="+title2+" width=80 height=20 menu");
					else run("New... ", "name="+title2+" type=[Text File] width=80 height=20 menu");
				}
				print(f, "\\Update:"); // clears the window
				print(f,startstr);
				for(i=0;i<locs;i++){
					parts=split(xylist[i],"  ");
					print(f,"  <StageLocation index=\""+(i+loffset)+"\" x=\""+parts[0]+"\" y=\""+parts[1]+"\" z=\""+parts[2]+"\" />\n");
				}
				print(f,"</StageLocations>");
			}
		
			if(printmap){
				if(isOpen("LocMap")){
					selectWindow("LocMap");
					currmapno=parseInt(getMetadata("Info"));
					if(!(currmapno>0)) currmapno=0;
				} else {
					newImage("LocMap", "8-bit White", 500, 500, 1);
					currmapno=0;
				}
				setForegroundColor(0,0,0);
				setJustification("center");
				drawString("Origin",250,250);
				for(i=0;i<locs;i++){
					parts=split(xylist[i],"  ");
					if(parseFloat(parts[0])==0 && parseFloat(parts[1])==0) drawString("(+"+(i+1)+")",278,250);
					else drawString(toString(i+currmapno+1)+"\n"+round(parts[0])+"  "+round(parts[1]),(-1*parseFloat(parts[0]) / 4+250),(-1*parseFloat(parts[1]) / 4+250));
				}
				setMetadata("Info", currmapno+locs);
			}
		}
		*/
	}
	
	private String[] getSliceTimes(String[] ptyfilepaths) {
		int ptyl=ptyfilepaths.length;
		String[] alltimes=new String[ptyl];
		for(int j=0;j<ptyl;j++){
			String temp=openZap(ptyfilepaths[j]);
			if(temp=="") {IJ.error("Bad OIF directory");return new String[0];}
			String[] ptyfile=temp.split("\n");
			int i=0; i=findData(ptyfile,"Axis 4 Parameters",0,0);
			i=findData(ptyfile,"AbsPositionValue",i,0);
			if(i<ptyfile.length) alltimes[j]=Double.toString(parseDoubleTP(getData(ptyfile[i]))/1000d);
			IJ.showProgress(j/(ptyl-1));
		}
		return alltimes;
	}

	
	//
	// Utility Funcitons
	//
	
	private void setZoomPosition(){
		int mposx=25, mposy=120, winw=26, winh=124, winzm=1;
		
	}
	
	//oif functions
	private String getData(String str){
		if(str.indexOf("=")==-1) return "";
		String[] a=str.split("="); String data=a[1];
		if(data=="")data="\"NA\"";
		if((data.indexOf("\'")>-1 || data.indexOf("\"")>-1)) data= data.substring(1,data.length()-1);
		return data;
	}
	
	private int findData(String[] xmlfile,String findtext,int starti,int limit){
		if(limit==0 || limit>(xmlfile.length))limit=(xmlfile.length);
		for(int i=starti;i<limit;i++){
			if(xmlfile[i].indexOf(findtext)> -1) {return i;} 
		}
		return limit;
	}

	//line from bigfile must start with or be close to starting with search
	private String searchAndGetData(String[] bigfile, String search) {
		for(int i=0;i<bigfile.length;i++) {
			int ios=bigfile[i].indexOf(search);
			if(ios>-1 && ios<3) {
				return getData(bigfile[i]);
			}
		}
		return "NA";
	}
	
	private String convtxt(String strc){
		String strn="";
		for(int in=0; in<strc.length()/2-1;in++) strn=strn+strc.substring((in*2)+1, (in*2)+2);
		return strn;
	}
	
	private String pad(int num, int digs){
		int digits=(int) Math.pow(10,(double)digs);
		if(num>=digits) return Integer.toString(num);
		return (Integer.toString(num+digits)).substring(1,digs+1);
	}
	
	private String openRaw(String path){
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
	
	private String openZap(String path){
		return zap(openRaw(path));
	}

	private String zap(String s1){
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
	
	private long checkTime(File file){
		//date last modified
		long dlm2=file.lastModified();
		long tolf=(System.currentTimeMillis()-dlm2)/1000;
		if(tolf<30) cont=true;
		return tolf;
	}
	
	private int parseIntTP(String test){
		return (int)parseDoubleTP(test);
	}

	private double parseDoubleTP(String test){
		double result=-1.0;
		try{
			result = Double.parseDouble(test);
			result=(double)Math.round(result*10000d)/10000d;
		}catch(Exception e){}
		return result;
	}
	
	private boolean askYesNoCancel(String title, String text){
		GenericDialog gb=new GenericDialog(title);
		gb.enableYesNoCancel();
		gb.hideCancelButton();
		gb.addMessage(text);
		gb.showDialog();
		if(gb.wasOKed()) return true;
		else return false;
	}
	
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
	
}
