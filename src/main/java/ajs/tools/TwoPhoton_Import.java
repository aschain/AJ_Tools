package ajs.tools;
import ij.plugin.PlugIn;
import ij.text.TextPanel;
import ij.text.TextWindow;
import ij.util.Java2;
import ij.gui.*;
import ij.io.OpenDialog;
import ij.*;

import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.ArrayList;

import javax.swing.JFileChooser;

import java.time.LocalTime;

/**
 * This is a template for a plugin that does not require one image
 * (be it that it does not require any, or that it lets the user
 * choose more than one image in a dialog).
 */
public class TwoPhoton_Import implements PlugIn {
	
	static boolean dozee=false,dogamma=Prefs.get("AJ.TwoPhoton_Import.dogamma", false),dotimes=Prefs.get("AJ.TwoPhoton_Import.dotimes", false),dopos=Prefs.get("AJ.TwoPhoton_Import.dopos", true);
	
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
	static final FilenameFilter nohidden = new FilenameFilter(){
			public boolean accept(File dir, String name){
				return !(name.startsWith(".") || name.equals("Thumbs.db"));
			}
		};
	static String webpath=Prefs.get("AJ.TwoPhoton_Import.webpath","C:\\Inetpub\\wwwroot\\");
	
	
	
	/**
	 * This method gets called by ImageJ / Fiji.
	 *
	 * @param arg can be specified in plugins.config
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@Override
	public void run(String arg) {
		if(arg.equals("updateImageSliceTimes")) {updateImageSliceTimes(); return;}
		else if(arg.equals("printexloc")) {printExLoc(IJ.getDirectory("")); return;}
		else if(arg.equals("printexlocspecial")) {printExLocSpecial(); return;}
		else if(arg.equals("updateCurrentImage")) {updateCurrentImage();return;}
		else if(arg.equals("startContinuousUpdate")) {updateCurrentImage();return;}
		else if(arg.equals("options")) {setOptions();return;}
		else if(isDirectory(arg)) {openTwoPhoton(arg); return;}
		else {
			openTwoPhoton();
		}
	}
	
	public void openTwoPhoton() {
		String[] dir=getDirectories();
		if(dir==null)return;
		if(dir.length==1)openTwoPhoton(dir[0]);
		else {
			//String other="";
			for(int i=0;i<dir.length;i++) {
				openTwoPhoton(dir[i],true);
				//String title=openTwoPhoton(dir[i],true).getTitle();
				//if(i==0)other+="  title="+title+"_CONCAT";
				//other+=" image"+(i+1)+"=["+title+"]";
			}
			//IJ.run("Concatenate...", other);
		}
	}
	
	public String[] getDirectories() {
		// Choose a directory using JFileChooser on the current thread
		String[] result=null;
		Java2.setSystemLookAndFeel();
		try {
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle("TwoPhoton Import");
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			chooser.setMultiSelectionEnabled(true);
			String defaultDir = Prefs.get("DirectoryChooser.DefaultDirectory",OpenDialog.getDefaultDirectory());
			if (defaultDir!=null) {
				File f = new File(defaultDir);
				chooser.setSelectedFile(f);
			}
			chooser.setAccessory(new PrevFolderSelector(chooser));
			chooser.setApproveButtonText("Select");
			if (chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) {
				File files[] = chooser.getSelectedFiles();
				result=new String[files.length];
				for(int i=0;i<files.length;i++) {
					String directory = files[i].getAbsolutePath();
					defaultDir=directory;
					if (!(directory.endsWith(File.separator)||directory.endsWith("/")))
						directory += "/";
					result[i]=directory;
				}
				Prefs.set("DirectoryChooser.DefaultDirectory",defaultDir);
				Prefs.savePreferences();
			}
		} catch (Exception e) {}

		return result;
	}
	
	private FolderType folderType(File[] fl) {
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
	
	static boolean isDirectory(String dir) {
		if(dir==null)return false;
		File f= new File(dir);
		if(!f.exists() || !f.isDirectory()) return false;
		return true;
	}
	
	static public void setOptions() {
		
		GenericDialog gd=new GenericDialog("Options");
		gd.addCheckbox("Set up Webpage?", false);
		gd.addCheckbox("Move images when opened", dopos);
		
		gd.showDialog();
		
		if(gd.wasCanceled())return;
		
		if(gd.getNextBoolean()) {
			if(askYesNoCancel("Set up Webpage","Change web root folder?\n"+webpath)) setUpWebpage();
		}
		dopos=gd.getNextBoolean();
		Prefs.set("AJ.TwoPhoton_Import.dopos", dopos);
		Prefs.savePreferences();
	}
	
	public ImagePlus openTwoPhoton(String dir) {
		return openTwoPhoton(dir,false,false);
	}
	
	public ImagePlus openTwoPhoton(String dir, boolean noask) {
		return openTwoPhoton(dir, noask, false);
	}
	
	public ImagePlus openTwoPhoton(String dir, boolean noask, boolean recurse){
		
		//test directory for oif, prairie, empty, tifs, or more folders
		if(dir==null)return null;
		if(dir.isEmpty())return null;
		if(!isDirectory(dir))return null;
		File f= new File(dir);
		File[] fl=f.listFiles(nohidden);

		if(fl.length==0) {IJ.log("Directory is empty"); return null;}
		
		FolderType ftype=folderType(fl);
		if(!ftype.hastifs && ftype.folders==0) {
			IJ.log("\n"+dir+" has nothing to open");
			return null;
		}
		if(!ftype.oif && !ftype.prairie){
			openAllFolder(dir,recurse);
			return null;
		}
		
		//sets up tpi and does exloc and updates from current file list
		TwoPhotonImage tpi=new TwoPhotonImage(fl);
		
		int tpstart=1,tpend=tpi.frms;

		boolean cont=tpi.cont,web=false;
		String[] eventstr=new String[0];
		
		//Use open window if same name
		ImagePlus img=WindowManager.getImage(tpi.RGBname);
		if(img!=null) {
			String imgdir= (String) img.getProperty("2p-Directory");
			if(imgdir.equals(dir) && !noask && !askYesNoCancel("Use open","Use open window?"))img=null;
			if(!imgdir.equals(dir))img=null;
		}
		if(img!=null){
			tpi.updateFromImage(img);
			cont=true;
			WindowManager.setCurrentWindow(img.getWindow());
		}else{
			//Open dialog if image is not open already
			if(((IJ.maxMemory()-IJ.currentMemory())<(tpi.tifsize*(tpend-tpstart+1)*tpi.getSlices(tpi.loc)*tpi.chs))) tpi.virtual=true;
			if(!tpi.virtual && tpi.frms==1) noask=true;
			if(!noask|| cont || tpi.locs>1){
				String tpstr="1-"+tpi.frms;
				GenericDialog gd=new GenericDialog(tpi.RGBname);
				if(tpi.frms>1 && tpi.sls>1){
					gd.addStringField("Timepoints", tpstr);
				}
				if(tpi.sls>1)gd.addCheckbox("Z-Project?", dozee);
				gd.addCheckbox("Include slice times?", dotimes);
				if(tpi.locs>1){gd.addNumericField("Location:", 1, 0, 3, "out of "+tpi.locs);}
				gd.addCheckbox("Virtual?",tpi.virtual);
				gd.addCheckbox("Half Gamma?", dogamma);
				if(cont) gd.addCheckbox("Continue Update?",false);
				long tolf=(System.currentTimeMillis()-tpi.lastfiletime)/1000;
				if(tolf<10*60) gd.addMessage("Time since last file: "+ Math.round((double) tolf));
				if(cont) gd.addCheckbox("Web",false);
				if(cont) gd.addCheckbox("Set an event to mark",false);
				gd.showDialog();
				
				if(gd.wasCanceled())return null;
				
				if(tpi.frms>1 && tpi.sls>1) {
					tpstr=gd.getNextString();
					int hyph=tpstr.indexOf("-");
					if(hyph==-1){tpend=AJ_Utils.parseIntTP(tpstr); tpstart=tpend; dozee=false;}
					else {tpstart=AJ_Utils.parseIntTP(tpstr.substring(0,hyph));
						tpend=AJ_Utils.parseIntTP(tpstr.substring(hyph+1,tpstr.length()));}
					if(tpstart<0)tpstart=1; if(tpend<0) tpend=tpi.frms;
				}
				if(tpi.sls>1)dozee=gd.getNextBoolean();else dozee=false;
				dotimes=tpi.dotimes=gd.getNextBoolean();
				Prefs.set("AJ.TwoPhoton_Import.dotimes", tpi.dotimes);
				if(tpi.locs>1) tpi.setLocation((int) gd.getNextNumber()-1);
				tpi.virtual=gd.getNextBoolean();
				dogamma=tpi.dogamma=gd.getNextBoolean();
				Prefs.set("AJ.TwoPhoton_Import.dogamma", tpi.dogamma);
				Prefs.savePreferences();
				if(cont) {
					cont=gd.getNextBoolean();
					web=gd.getNextBoolean();
					if(web && !(new File(webpath+"index.htm").exists())) web=setUpWebpage();
					if(web && gd.getNextBoolean()){ 
						gd=new GenericDialog("Set event");
						LocalTime lt=LocalTime.now();
						gd.addStringField("Time or time point of event",""+lt.getHour()+":"+lt.getMinute()+":"+lt.getSecond());
						gd.addStringField("Event name:","");
						gd.showDialog();
						if(!gd.wasCanceled()){
							eventstr=new String[2];
							eventstr[0]=gd.getNextString();
							eventstr[1]=gd.getNextString();
						}
					}
				}
				if(cont) {tpend=tpi.frms;}
			}else{
				//tpi.dotimes=false;
			}
			
			if(!tpi.virtual && ((IJ.maxMemory()-IJ.currentMemory())<(tpi.tifsize*(tpend-tpstart+1)*tpi.getSlices(tpi.loc)*tpi.chs)))
				if(askYesNoCancel("Virtual","Maybe not enough memory, open as virtual?"))tpi.virtual=true;

			if(tpi.virtual) {
				tpi.scale=(int) IJ.getNumber("Change this from 100 to scale instead of Virtual stack",100);
				if(tpi.scale==100){dozee=false; tpi.dogamma=false;}
			}
			
			//load the image
			if(IJ.getLog()!=null)IJ.log("");
			IJ.log(f.getParentFile().getName()+File.separator+tpi.RGBname+":");
			IJ.log(tpi.exlocoutput);
			img=tpi.loadFirstImage(tpstart,tpend,dopos);
			if(dozee) {tpi.zProject(dopos);}
		}
		if(cont) startContinuousUpdate(tpi,web,eventstr);
		
		return img;
	}

	public void updateImageSliceTimes() {
		 TwoPhotonImage.updateImageSliceTimes(WindowManager.getCurrentImage());
	}
	
	public static void updateCurrentImage() {
		ImagePlus img=WindowManager.getCurrentImage();
		if(img!=null){
			TwoPhotonImage tpi=new TwoPhotonImage(img);
			tpi.updateImage();
		}else {IJ.noImage(); return;}
	}
	
	public void startContinuousUpdate() {
		ImagePlus img=WindowManager.getCurrentImage();
		if(img!=null){
			String[] eventstr=new String[0];
			boolean web=false;
			TwoPhotonImage tpi=new TwoPhotonImage(img);
			tpi.exLoc();
			GenericDialog gd=new GenericDialog("Continuous Update");
			gd.addMessage("Continuous update on "+tpi.img.getTitle()+"?");
			gd.addCheckbox("Web",false);
			gd.addCheckbox("Set an event to mark",false);
			gd.showDialog();
			if(gd.wasCanceled())return;
			web=gd.getNextBoolean();
			if(gd.getNextBoolean()){ 
				gd=new GenericDialog("Set event");
				LocalTime lt=LocalTime.now();
				gd.addStringField("Time or time point of event",""+lt.getHour()+":"+lt.getMinute()+":"+lt.getSecond());
				gd.addStringField("Event name:","CGRP");
				gd.showDialog();
				if(!gd.wasCanceled()) {
					eventstr=new String[2];
					eventstr[0]=gd.getNextString();
					eventstr[1]=gd.getNextString();
				}
			}
			if(web && !(new File(webpath+"index.htm").exists())) web=setUpWebpage();
			startContinuousUpdate(tpi,web,eventstr);
		}else {IJ.noImage(); return;}
	}
	
	private void startContinuousUpdate(TwoPhotonImage tpi, boolean web, String[] eventstr){
		IJ.log("Start cont");
		String title="Time Series Clock";
		TextWindow tsc=(TextWindow) WindowManager.getWindow(title);
		if(tsc==null) {
			tsc=new TextWindow(title,"Currently "+tpi.sl+" slices of tp "+tpi.totfrms,500,190);
			tsc.setLocation(10,10);
		}
		TextPanel tscp=tsc.getTextPanel();
		boolean cont=true;
		while(cont) {
			if(tpi.img==null || !tpi.img.isVisible() || tsc==null || !tsc.isVisible())break;
			
			String[] tpstr=new String[3];
			tpi.updateFLInfo(true);
			tpstr[0]="Currently "+tpi.sl+"/"+tpi.sls+" slices of tp "+tpi.totfrms;
			long eltime=(System.currentTimeMillis()-tpi.firstfiletime);
			long deadtime=(System.currentTimeMillis()-tpi.lastfiletime);
			tpstr[1]="Running for "+AJ_Utils.textTime(eltime,"h:m:s");
			if((tpi.totaltps)>(tpi.frms)) {
				tpstr[0]=tpstr[0]+"/"+tpi.totaltps;
				tpstr[1]=tpstr[1]+" / "+AJ_Utils.textTime((long)((double)tpi.totaltps*tpi.cal.frameInterval*1000.0),"h:m:s");
			}
			tpstr[2]="Idle for "+AJ_Utils.textTime(deadtime,"h:m:s");
			tscp.clear();
			for(int i=0;i<tpstr.length;i++)
				tsc.append(tpstr[i]);
			boolean updated=false;
			if(tpi.frms>tpi.frend) {
				ImageCanvas ic=tpi.img.getCanvas();
				ImageCanvas zic=null;
				if(tpi.zimg!=null) zic=tpi.zimg.getCanvas();
				while(tpi.mousePressed || ic.getModifiers()!=0 || (zic==null?(false):(zic.getModifiers()!=0))) {
					tscp.setLine(tscp.getLineCount()-1,"Waiting for mouse release to update image...");
					IJ.wait(50);
				}
				IJ.wait(50);
				tpi.updateImage();
				updated=true;
			}
			
			if(web){
				try {
					PrintStream ps=new PrintStream(webpath+"2p-update.txt");
					ps.println(tpstr[0]);
					ps.println(tpstr[1]);
					ps.println(tpstr[2]);
					ps.close();
				}catch(Exception e) {
					 IJ.error("Could not write to web text file\n"+e.getMessage());
				}
				
				if(updated) {
					ImagePlus latestmax=tpi.getLatestMax();
					latestmax.setDimensions(tpi.img.getNChannels(), 1, 1);
					latestmax.setDisplayMode(tpi.img.getDisplayMode());
					latestmax.show();
					IJ.run("Size...", "width=230 height=230 constrain interpolation=Bilinear");
					IJ.wait(500);
					IJ.saveAs(latestmax,"Jpeg", webpath+"2p-update.jpg");
					latestmax.changes=false;
					latestmax.close();
					if( (tpi.frms>9) && tpi.frms%10==0 ){
						boolean haszimg=(tpi.zimg!=null);
						ImagePlus giffer=null;
						if(haszimg)giffer=tpi.zimg.duplicate();
						else giffer=tpi.zProject(false);
						giffer.setTitle("giffer");
						giffer.show();
						//WindowManager.setCurrentWindow(giffer.getWindow());
						//run("AVI... ", "compression=JPEG jpeg=10 frame=5 save="+webpath+"goingon.avi");
						IJ.run("Size...", "width=230 height=230 constrain interpolate"); IJ.wait(200);
						giffer=WindowManager.getImage("giffer");
						if(eventstr.length==2){
							IJ.run("Print Times", "set="+eventstr[0]+" levels=1 prefix=["+eventstr[1]+"] background label do");
						}else {
							IJ.run("Print Times", "levels=1 background label do");
						}
						IJ.run("Stack to RGB", "frames");IJ.wait(200);
						ImagePlus rgbgiffer=WindowManager.getImage("giffer");
						WindowManager.setCurrentWindow(rgbgiffer.getWindow());
						IJ.run("Animated Gif ... ", "name=giffer set_global_lookup_table_options=[Load from Current Image] optional=[] image=[No Disposal] set=100 number=0 transparency=[No Transparency] red=0 green=0 blue=0 index=0 filename="+webpath+"2p-update.gif");
						if(giffer!=null) {giffer.changes=false; giffer.close();}
						if(rgbgiffer!=null) {rgbgiffer.changes=false; rgbgiffer.close();}
						if(!haszimg) {
							tpi.zimg.close();
							tpi.zimg=null;
						}
					}
				}
			}
			IJ.wait(1000);
		}
		IJ.log("End Continuous Update");
	}

	public void openAllFolder(String path, boolean recurse){
		if(path==null)return;
		File f;
		try {
			f= new File(path);
		}catch(Exception e) {IJ.error("Could not open directory to open all files");return;}
		if(f==null || !f.exists() || !f.isDirectory()) return;
		File[] fl=f.listFiles(nohidden);

		if(fl.length==0) {IJ.log("Directory is empty"); return;}
		
		//OIF compatibility + tests for empty folders + recurse folders----------
		boolean go=true,dofs=false,dotifs=false,skipopen=false;
        String name;
        ArrayList<Integer> folders=new ArrayList<Integer>();
        ArrayList<Integer> tifs=new ArrayList<Integer>();
		for(int i=0;i<fl.length && go;i++){
			name=fl[i].getName();
			if(name.endsWith(".tif")){
				tifs.add(i);
			}
			if(fl[i].isDirectory()) {folders.add(i);}
		}
		if(tifs.size()==0 && folders.size()==0) {
			IJ.log("\n"+path+" has nothing to open");
			return;
		}
		
		String filterstring="";
		if(folders.size()>0) dofs=true;
		if(tifs.size()>0)dotifs=true;
		if(folders.size()==1 && tifs.size()==0) {
			openTwoPhoton(path+File.separator+fl[folders.get(0)]); return;
		}else {
			if(!recurse){
				GenericDialog gd = new GenericDialog("Open all");
				if(dofs) {
					gd.addCheckbox("Open all folders?",true);
					gd.addCheckbox("Recurse?",true);
				}
				if(dotifs) gd.addCheckbox("Open all tifs in folder?",true);
				gd.addCheckbox("Skip if already open?",true);
				gd.addStringField("Filter: ","");
				gd.addMessage("Wildcard [*] at start or end of filter means");
				gd.addMessage("that it ends with or starts with the string.");
				gd.showDialog();
				if(gd.wasCanceled())return;
				if(dofs) {
					dofs=gd.getNextBoolean();
					recurse=gd.getNextBoolean();
				}
				if(dotifs) dotifs=gd.getNextBoolean();
				skipopen=gd.getNextBoolean();
				filterstring=gd.getNextString();
			}
		}
		if(dofs || dotifs){
			for(int i=0;i<fl.length;i++) {
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
						//don't open mosaic in folder unless specified
						if(name.startsWith("FV10_")){if(askYesNoCancel("FV10","Open "+fl[i]+"?")) openTwoPhoton(fl[i].getAbsolutePath(),true);}
						else {openTwoPhoton(fl[i].getAbsolutePath(),true,recurse);}
					}
					if(go && dotifs && name.endsWith(".tif"))
						IJ.openImage(fl[i].getAbsolutePath());
				}
			}
		}
	}
	
	public void printExLocSpecial() {
		GenericDialog gd=new GenericDialog("Location Extraction");
		gd.addCheckbox("Print locs.xy file?", false);
		gd.addCheckbox("Print location map?", false);
		gd.showDialog();
		
		TwoPhotonImage tpi=printExLoc(IJ.getDirectory(""),false);
		if(gd.getNextBoolean()) tpi.createPrairieLocXYfile();
		if(gd.getNextBoolean()) tpi.printLocationMap();
	}
	
	public void printExLoc(String dir) {
		printExLoc(dir,false);
	}
	
	public TwoPhotonImage printExLoc(String dir, boolean recurse){
		
		//test directory for oif, prairie, empty, tifs, or more folders
		if(dir==null)return null;
		File f= new File(dir);
		if(!f.exists() || !f.isDirectory()) return null;
		File[] fl=f.listFiles(nohidden);

		if(fl.length==0) {IJ.log("Directory is empty"); return null;}
		
		FolderType ftype=folderType(fl);
		if(!ftype.hastifs && ftype.folders==0) {
			IJ.log("\n"+dir+" has nothing to open");
			return null;
		}
		if(!ftype.oif && !ftype.prairie){
			if(recurse && ftype.folders>0) {
				for(int i=0;i<fl.length;i++) {
					if(fl[i].isDirectory())printExLoc(fl[i].getAbsolutePath(),true);
				}
			}else {
				IJ.log("\n"+dir+" has nothing to open");
				return null;
			}
		}
		
		TwoPhotonImage tpi=new TwoPhotonImage(fl,false);
		if(IJ.getLog()!=null)IJ.log("");
		IJ.log(tpi.exlocoutput);
		return tpi;
		
	}
	
	static private boolean setUpWebpage(){
		IJ.showMessage("2p-Import can update files for a simple webpage.\nTo start, choose the directory of your webserver.");
		webpath=IJ.getDirectory("");
		if(webpath==null || webpath.equals("")) return false;
		if(webpath.endsWith("\\")||webpath.endsWith("/"))webpath=webpath.substring(0,webpath.length()-1);
		webpath+=File.separator;
		if(!(new File(webpath+"2p-update.txt").exists())) {
			try {
				PrintStream ps=new PrintStream(webpath+"2p-update.txt");
				ps.println("2p live data goes here");
				ps.close();
			}catch(Exception e) {
				 IJ.error("Could not write to web text file "+e.getMessage());
				 return false;
			}
		}else IJ.log("Using existing 2p-update.txt");
		if(!(new File(webpath+"index.htm").exists())) {
			try {
				PrintStream ps=new PrintStream(webpath+"index.htm");
				BufferedReader reader = new BufferedReader(new InputStreamReader(TwoPhoton_Import.class.getClassLoader().getResource("webroot/index.htm").openStream()));
				String contents="";
				String adder=reader.readLine();
				while(adder!=null) {
					contents+=adder+"\n";
					adder=reader.readLine();
				}
				ps.print(contents);
				ps.close();
			}catch(Exception e) {
				 IJ.error("Could not write to web index.html file "+e.getMessage());
				 return false;
			}
		}else IJ.log("Using existing index.htm");
		if(!(new File(webpath+"movie.htm").exists())) {
			try {
				PrintStream ps=new PrintStream(webpath+"movie.htm");
				BufferedReader reader = new BufferedReader(new InputStreamReader(TwoPhoton_Import.class.getClassLoader().getResource("webroot/movie.htm").openStream()));
				String contents="";
				String adder=reader.readLine();
				while(adder!=null) {
					contents+=adder+"\n";
					adder=reader.readLine();
				}
				ps.print(contents);
				ps.close();
			}catch(Exception e) {
				 IJ.error("Could not write to web index.html file "+e.getMessage());
				 return false;
			}
		}else IJ.log("Using existing movie.htm");
		Prefs.set("AJ.TwoPhoton_Import.webpath",webpath);
		Prefs.savePreferences();
		return true;
	}	
	
	//
	// Utility Functions
	//
	

	
	static private boolean askYesNoCancel(String title, String text){
		GenericDialog gb=new GenericDialog(title);
		gb.enableYesNoCancel();
		gb.hideCancelButton();
		gb.addMessage(text);
		gb.showDialog();
		if(gb.wasOKed()) return true;
		else return false;
	}
	
	public static void addTPPopup() {
		if(hasInstalledTPPopup())return;
		PopupMenu popup=Menus.getPopupMenu();
		MenuItem tpmi=new MenuItem("Update 2p Stack");
		tpmi.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				updateCurrentImage();
			}
		});
		popup.add(tpmi);
	}
	
	public static void removeTPPopup() {
		PopupMenu popup=Menus.getPopupMenu();
		for(int i=0;i<popup.getItemCount();i++)
			if(popup.getItem(i).getActionCommand().equals("Update 2p Stack"))popup.remove(i);
	}
	
	static boolean hasInstalledTPPopup() {
		PopupMenu popup=Menus.getPopupMenu();
		for(int i=0;i<popup.getItemCount();i++)
			if(popup.getItem(i).getActionCommand().equals("Update 2p Stack"))return true;
		return false;
	}
	
}
