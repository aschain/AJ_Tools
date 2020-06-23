package ajs.tools;
import ij.plugin.PlugIn;

import java.awt.Point;
import java.awt.Polygon;

import ij.process.LUT;
import ij.text.TextPanel;
import ij.text.TextWindow;
import ij.*;
import ij.gui.*;
import ij.process.*;
import java.util.*;

public class Diameter_Profile implements PlugIn {

	final String[] THRESHLEVELS=new String[] {"Mean","Median","Mid","Top 1/3"};
	final int ianglemax=5;
	
	
	/**
	 * This method gets called by ImageJ / Fiji.
	 *
	 * @param arg can be specified in plugins.config
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@Override
	public void run(String arg){
		String plottype="line";
		int rfd=3;
		
		ImagePlus imp=WindowManager.getCurrentImage();
		ij.measure.Calibration cal=imp.getCalibration();
		double pw=cal.pixelWidth;
		int sl=imp.getSlice(), fr=imp.getFrame(),sls=imp.getNSlices(), frms=imp.getNFrames(),chs=imp.getNChannels();
		String title=imp.getTitle();
		
		String cpptitle="ColorPP-"+title;
		Roi roi=imp.getRoi();
		int stype=roi.getType();
		String[] tluts=getTextLUTs(imp);
		int[] chRange= {0,chs},frRange= {fr-1,fr}, slRange= {sl-1,sl};
		double strokeWidth = roi.getStrokeWidth();
		int ianglejump=1;
		String tool=IJ.getToolName();
		
		GenericDialog gd=new GenericDialog("Color plot profile");
		gd.addCheckbox("Thresh Method?", true);
		gd.addChoice("Thresh level:", THRESHLEVELS, Prefs.get("AJ.Diameter_Profile.threshlevel", "Mean"));
		gd.addCheckbox("Normalize?", false);
		gd.addCheckbox("Move selection?",false);
		gd.addCheckbox("Just Diameters?",false);
		if(stype==0)gd.addCheckbox("Vertical?",false);
		if(stype==5 ||stype==6 || stype==7)gd.addNumericField("Widen line (averaging)", strokeWidth, 0);
		if(stype==6 || stype==7) {
			gd.addNumericField("Skip every n along line", ianglejump, 0);
		}
		if(chs>1)gd.addStringField("Channels:",""+imp.getChannel());
		if(sls>1){
			gd.addStringField("Slices:",""+imp.getSlice());
			gd.addCheckbox("Do all slices?",false);
			gd.addCheckbox("Report slice?",false);
		}
		if(frms>1){
			gd.addStringField("Frames:",""+fr);
			gd.addCheckbox("Do all frames?",true);
		}
		gd.addNumericField("Diameter running ave:",rfd,0);
		//gd.addCheckbox("Show average?",false);
		gd.addCheckbox("Check?",false);
		gd.addCheckbox("CSD Summary?",false);
		gd.showDialog();
		
		if(gd.wasCanceled())return;
	  
		boolean threshMethod=gd.getNextBoolean();
		String threshLevel=gd.getNextChoice();
		Prefs.set("AJ.Diameter_Profile.threshlevel", threshLevel);
		boolean normalize=gd.getNextBoolean();
		boolean move=gd.getNextBoolean();
		boolean onlydiameters=gd.getNextBoolean();
		boolean vertical=false;
		boolean reportsls=false;
		if(stype==0)vertical=gd.getNextBoolean();
		if(stype==5||stype==6||stype==7) {
			strokeWidth=gd.getNextNumber();
			roi.setStrokeWidth(strokeWidth);
			if(stype==6||stype==7)ianglejump=(int)gd.getNextNumber();
		}
		if(chs>1){
			chRange = AJ_Utils.parseRange(gd.getNextString());
		}
		if(sls>1){
			slRange = AJ_Utils.parseRange(gd.getNextString());
			if(gd.getNextBoolean()){slRange[0]=0; slRange[1]=sls;}
			reportsls=(gd.getNextBoolean() || ((slRange[1]-slRange[0])>1));
		}
		if(frms>1){
			frRange = AJ_Utils.parseRange(gd.getNextString());
			if(gd.getNextBoolean()){frRange[0]=0; frRange[1]=frms;}
		}
		int myrfd=(int)gd.getNextNumber();
		//boolean showaverage=gd.getNextBoolean();
		boolean check=gd.getNextBoolean();
		boolean csdSummary=gd.getNextBoolean();
		Prefs.savePreferences();
	  
		double[] times=new double[frms];
		double frint=0;
		frint=imp.getCalibration().frameInterval;
		String headings="";
		if(frms>1){
			headings="Frame\t";
			if(frint>0){
				String timeunit=imp.getCalibration().getTimeUnit();
				headings+="Frame ("+timeunit+")\t";
				times=Time_Extractor.extractTimes(imp);
			}
			
		}
		if(reportsls)headings+="Slice\t";
		
		headings+="Diameter";
		if(!threshMethod)headings+="\tRise\tFall\tAdjRise\tAdjFall";
		TextWindow table=new TextWindow(title+"-diameters",headings, "",500,300);
	
		int frR=(frRange[1]-frRange[0]), slR=(slRange[1]-slRange[0]),chR=(chRange[1]-chRange[0]);
		double[][] tp=new double[frR*slR*chR][];
		int[][] diameterResult=new int[frR*slR*chR][];
		diameterResult[0]=new int[] {-1,-1};
		
		ProfilePlot tpp;
		int xMax=0,xAveMax=0;
		
		ImageProcessor[] ip=new ImageProcessor[chR];
		ImagePlus dimp=null;
		
		Polygon pline=roi.getPolygon();
		double[] angle=new double[pline.npoints];
		if(stype==6 || stype==7) {
			for(int il=ianglemax;il<pline.npoints-ianglemax;il+=ianglejump) {
				for(int ila=0;ila<ianglemax;ila++) angle[il]+=Math.atan2(pline.ypoints[il+ila]-pline.ypoints[il-ila], pline.xpoints[il+ila]-pline.xpoints[il-ila]);
				angle[il]/=ianglemax;
			}
		}
		
		for(int k=frRange[0];k<frRange[1];k++){
			for(int j=slRange[0];j<slRange[1];j++){
				for(int i=chRange[0];i<chRange[1];i++){
					imp.setPosition(i+1,j+1,k+1);
					int index=(k-frRange[0])*slR*chR+(j-slRange[0])*chR+(i-chRange[0]);
					while(IJ.spaceBarDown()) IJ.wait(300);
					if(move){
						while(!IJ.spaceBarDown())IJ.wait(100);
						while(IJ.spaceBarDown()) IJ.wait(100);
					}
	  	
					
					if(stype==0) {
						tpp=new ProfilePlot(imp,vertical);
						tp[index]=tpp.getProfile();
					} else if(stype==5) {
						tpp=new ProfilePlot(imp);
						tp[index]=tpp.getProfile();
					} else if(stype==6 || stype==7) {
						double[] temptp;
						tp[index]=new double[(int)strokeWidth];
						int ianglelen=0;
						for(int il=ianglemax;il<pline.npoints-ianglemax;il+=ianglejump) {
							int x=pline.xpoints[il], y=pline.ypoints[il];
							Line tline=new Line(x+strokeWidth/2*Math.cos(angle[il]-Math.PI/2), y+strokeWidth/2*Math.sin(angle[il]-Math.PI/2), x+strokeWidth/2*Math.cos(angle[il]+Math.PI/2), y+strokeWidth/2*Math.sin(angle[il]+Math.PI/2));
							tline.setStrokeWidth(rfd);
							imp.setRoi(tline);
							tpp=new ProfilePlot(imp);
							temptp=tpp.getProfile();
							for(int iln=0;iln<strokeWidth;iln++)tp[index][iln]+=iln<temptp.length?temptp[iln]:0;
							ianglelen++;
						}
						for(int iln=0;iln<strokeWidth;iln++)tp[index][iln]/=ianglelen;
					}
					
					if(index==0) {
						xMax=tp[0].length;
						xAveMax=xMax-myrfd+1;
						if(threshMethod) {
							dimp=IJ.createImage(imp.getTitle()+"-diameters", xAveMax, frR*slR, chR, 8);
							dimp.show();
							for(int ipi=0;ipi<chR;ipi++) ip[ipi]=dimp.getImageStack().getProcessor(ipi+1);
						}
					}
					if(threshMethod) {
						int[] thresh=getThresh(tp[index],myrfd,threshLevel);
						for(int x=0;x<thresh.length;x++) ip[i-chRange[0]].set(x,((k-frRange[0])*slR+(j-slRange[0])),thresh[x]);
					}
					if(!threshMethod && (rfd>0)){
						diameterResult[index]=diameter(diameterResult[Math.max(index-1,0)][0],diameterResult[Math.max(index-1,0)][1],tp[index],myrfd);
					}
				}
			}
		}
		
		if(stype==6 || stype==7) imp.setRoi(roi);
		
		if(threshMethod) {
			dimp.updateAndRepaintWindow();
			IJ.setTool(3); //drawSelection tool
			WindowManager.setCurrentWindow(dimp.getWindow());
			dimp.getWindow().setLocation(new Point(300,300));
			WaitForUserDialog.setNextLocation(300+dimp.getWindow().getWidth()+20, 300);
			WaitForUserDialog wfu=new WaitForUserDialog("Thresh Fixer","Fix threshes then hit OK");
			wfu.show();
			if(wfu.escPressed())return;
			if((ip[0].get(0,0)+ip[0].get(1,0)+ip[0].get(2,0)+ip[0].get(3,0)+ip[0].get(4,0))/5==255) {
				for(int i=0;i<ip.length;i++)ip[i].invert();
			}
		}


		// Plot profile
		double max=0,min=0;
		for(int i=0;i<tp.length;i++) {
			for(int j=0;j<tp[i].length;j++) {
				if(tp[i][j]>max)max=tp[i][j];
				if(tp[i][j]<min)min=tp[i][j];
				if(i==0&&j==0)min=tp[i][j];
			}
		}
		if(normalize){max=1000; min=0;}
		
		if(!onlydiameters)roi.setStrokeWidth(1);
		
		int[] thresh=new int[xAveMax];
		boolean first=true;
		ImagePlus finalimp=new ImagePlus();
		for(int k=frRange[0];k<frRange[1];k++){
			for(int j=slRange[0];j<slRange[1];j++){
				Plot plot=null;
				if(!onlydiameters) {
					plot=new Plot("Profile","X","Intensity");
					plot.setLimits(0, xMax, min, max);
				}
				for(int i=chRange[0];i<chRange[1];i++){
					String printstr="";
					int index=(k-frRange[0])*slR*chR+(j-slRange[0])*chR+(i-chRange[0]);
					int start=-1,end=-1,diameter=0;
					if(threshMethod) {
						for(int x=0;x<xAveMax;x++) {
							thresh[x]=ip[i-chRange[0]].get(x,((k-frRange[0])*slR+(j-slRange[0])));
							if(thresh[x]==255)diameter++;
							if(start==-1 && x>(myrfd+1) && (thresh[x-1]+thresh[x])/2==255)start=x-1-myrfd;
						}
						diameter+=2*myrfd;
						end=start+diameter;
						diameterResult[index]=new int[] {start,end};
					}else {
						start=diameterResult[index][0];
						end=diameterResult[index][1];
						diameter=end-start;
					}
					
					if(frms>1){
						printstr=""+(k+1)+"\t";
						if(frint>0) {
							printstr+=""+times[k]+"\t";
						}
					}
					if(reportsls)printstr+=""+(j+1)+"\t";
					printstr+=(""+diameter*pw);
					if(!threshMethod)printstr+="\t"+start*pw+"\t"+end*pw+"\t"+start*pw+"\t"+end*pw;
					table.append(printstr);
					
					if(!onlydiameters) {
						plot.setColor(tluts[i]);
						//apos=(k-stfrm)*(endsl-stsl)*(endch-stch)*xMax+(j-stsl)*(endch-stch)*xMax+(i-stch)*xMax;
						//tp=Array.slice(profile,apos,apos+xMax);
						//if(rfd>0){if(showaverage)avetp=Array.slice(aveprofile,apos,apos+xMax-rfd);}
						//print(""+k+" B   "+tp[0]);
						double[] xs=new double[xMax];
						if(normalize){
							double tpmax=0;
							for(int x=0;x<tp[index].length;x++)if(tp[index][x]>tpmax)tpmax=tp[index][x];
							for(int x=0;x<tp[index].length;x++) {tp[index][x]/=(tpmax/1000); xs[x]=x;}
						}else {
							for(int x=0;x<tp[index].length;x++) {xs[x]=x;}
						}
						plot.add(plottype,xs,tp[index]);
						if(rfd>0){
							/*
							if(showaverage){
								aprf=newArray(tp.length-floor(rfd/2)-1);
								Array.fill(aprf,0);
								for(ai=0; ai<(tp.length-rfd);ai++){
									for(aj=0;aj<rfd;aj++){aprf[ai+floor(rfd/2)]+=tp[ai+aj];}
									aprf[ai+floor(rfd/2)]/=rfd;
									if(ai<floor(rfd/2))aprf[ai]=tp[ai];
								}
								Plot.setColor("red");
								Plot.add(plottype,aprf);
							}
							*/
							plot.setColor("green");
							//apos=(k-stfrm)*(endsl-stsl)*(endch-stch)*2+(j-stsl)*(endch-stch)*2+(i-stch)*2;
							plot.drawLine((double)start, (double)min, (double)start, (double)max);
							plot.setColor("magenta");
							plot.drawLine((double)end, (double)min, (double)end, (double)max);
						}
					}
				}
				if(!onlydiameters) {
					//PlotWindow plotwin=plot.show();
					if(first) {
						finalimp.setImage(plot.getImagePlus());
						finalimp.setTitle(cpptitle);
						first=false;
						plot.dispose();
						plot=null;
					}else{
						finalimp.getImageStack().addSlice(plot.getImagePlus().getProcessor());
						plot.dispose();
						plot=null;
					}
				}
			}
		}

		finalimp.setOpenAsHyperStack(true);
		finalimp.setDimensions(1, slR, frR);
		finalimp.show();

		
		if(check){
			WindowManager.setCurrentWindow(finalimp.getWindow());
			double y1=max; double y2=min;
			boolean ok=true, left=false;
			IJ.setTool(19);
			IJ.showStatus("Checking");
			ImageCanvas ic=finalimp.getCanvas();
			while(ok){
				//do{getCursorLoc(x,y,z,flags); wait(10); print("\\Update:1st"+flags);}while(flags!=0);
				int flags;
				double x,y;
				IJ.log("\\Update:Checking ColorPP! ");
				do{
					Point ml=ic.getCursorLoc(); IJ.wait(10);
					flags=ic.getModifiers(); x=(double)ml.x; y=(double)ml.y;
					if(flags==1)ok=false; //press control to stop
				}while(flags==32||flags==0);
				Plot plot = (Plot)(finalimp.getProperty(Plot.PROPERTY_KEY));
				if(plot!=null) {
					y1=plot.scaleYtoPxl((int)(min));
					y2=plot.scaleYtoPxl((int)(max));
					x=plot.descaleX((int)(x+0.5));
				}
				sl=finalimp.getZ(); fr=finalimp.getT(); 
				IJ.log("\\Update:x"+x+" y"+y);
				int index=(fr-1)*slR*chR+(sl-1)*chR;
				int stpt=diameterResult[index][0]; int endpt=diameterResult[index][1];

				ImageProcessor ipt=finalimp.getProcessor();
				if((Math.abs(x*pw-stpt)<10)&&(Math.abs(x*pw-endpt)<10))left=!left;
				else if((Math.abs(x*pw-stpt)<10))left=true;
				else if(Math.abs(x*pw-endpt)<10)left=false;
				else left=!left;
				if(left){
					IJ.log("newline");
					ipt.snapshot(); 
					IJ.setForegroundColor(0,255,0);
					while((flags&16)!=0 || flags==48){
						ipt.reset(); ipt.snapshot();
						Point ml=ic.getCursorLoc(); IJ.wait(10);
						flags=ic.getModifiers(); x=(double)ml.x; y=(double)ml.y;
						ipt.drawLine((int)x,(int)y1,(int)x,(int)y2);
						IJ.log("\\Update:new rise x"+x+" y"+y);
						finalimp.updateAndDraw();
						IJ.wait(100);
					}
					if(plot!=null) {
						x=plot.descaleX((int)(x+0.5));
					}
					diameterResult[index][0]=(int)x;
					editTable(table,"AdjRise",index,Double.toString(((double)x)*pw));
					editTable(table,"Diameter",index,Double.toString(((double)(diameterResult[index][1]-x))*pw));
					//ok=getBoolean("Keep going?");
				}
				if(!left){
					IJ.log("newline");
					ipt.snapshot();
					IJ.setForegroundColor(255,0,255);
					while((flags&16)!=0 || flags==48){
						ipt.reset(); ipt.snapshot();
						Point ml=ic.getCursorLoc(); IJ.wait(10);
						flags=ic.getModifiers(); x=(double)ml.x; y=(double)ml.y;
						ipt.drawLine((int)x,(int)y1,(int)x,(int)y2);
						IJ.log("\\Update:new rise x"+x+" y"+y);
						finalimp.updateAndDraw();
						IJ.wait(100);
					}
					if(plot!=null) {
						x=plot.descaleX((int)(x+0.5));
					}
					diameterResult[index][1]=(int)x;
					editTable(table,"AdjFall",index,Double.toString(((double)x)*pw));
					editTable(table,"Diameter",index,Double.toString(((double)(diameterResult[index][1]-x))*pw));
					//ok=getBoolean("Keep going?");
				}
			}
		}
		
		if(frRange[1]-frRange[0]>1) {
			Plot plot=new Plot("Diameter over time","Time","Diameter");
			//plot.setLimits(0, xMax, min, max);
			TextPanel txtp=table.getTextPanel();
			String[] hds=headings.split("\t");
			int n=0;
			for(n=0;n<hds.length;n++) {
				if(hds[n].equals("Diameter"))break;
			}
			double[] diameters=new double[txtp.getLineCount()];
			for(int i=0;i<txtp.getLineCount();i++) diameters[i]=Double.parseDouble(txtp.getLine(i).split("\t")[n]);

			int dmin=-1,dmax=-1,dstart=-1, dstartConstr=-1, dendConstr=-1, dendDilation=-1;
			double dminVal=65535, dmaxVal=0, aveBase=0, aveBaseFinal=0;
			if(csdSummary) {
				for(int i=0;i<diameters.length;i++) {
					if(i<50) {aveBaseFinal+=diameters[i]; aveBase+=diameters[i];}
					if(i>diameters.length-50)aveBaseFinal+=diameters[i];
					if(diameters[i]<dminVal) {dminVal=diameters[i]; dmin=i;}
				}
				aveBase/=50; aveBaseFinal/=100;
				for(int i=dmin;i>0;i--) {
					if(diameters[i]>aveBaseFinal) {dstartConstr=i+1; break;}
				}
				for(int i=dstartConstr;i>3;i--) {
					if(( (diameters[i-3]+diameters[i-2]+diameters[i-1])/3 < (diameters[i]+diameters[i+1]+diameters[i+2])/3 ) && (Math.abs(diameters[i]-aveBase)<(0.05*aveBase))) {dstart=i; break;}
				}
				for(int i=dmin;i<diameters.length;i++) {
					if((dendConstr==-1) && (diameters[i]>aveBaseFinal)) {dendConstr=i-1;}
					if(diameters[i]>dmaxVal) {dmaxVal=diameters[i]; dmax=i;}
				}
				for(int i=dmax;i<diameters.length;i++) {
					if(diameters[i]<aveBaseFinal) {dendDilation=i;break;}
				}
				double starttime=times[dstart];
				for(int i=0;i<times.length;i++)times[i]-=starttime;
			}
			plot.setColor("black");
			plot.add(plottype,times,diameters);
			if(csdSummary) {
				plot.setColor("magenta");
				plot.drawLine(times[dstartConstr], dminVal, times[dstartConstr], dmaxVal);
				plot.drawLine(times[dendConstr], dminVal, times[dendConstr], dmaxVal);
				plot.drawLine(times[dendDilation], dminVal, times[dendDilation], dmaxVal);

				TextWindow summaryTable=new TextWindow(title+"-diametersSummary","Baseline Diameter\tBef-After Ave\tStart Constriction\tEnd Constriction\tEnd Dilation\tConstriction time\tDilation Time\tMin %\tMax %","",500,300);
				summaryTable.append(""+rnd(aveBase,3)+"\t"+rnd(aveBaseFinal,3)+"\t"+rnd(times[dstartConstr],3)+"\t"+rnd(times[dendConstr],3)+"\t"+rnd(times[dendDilation],3)+
						"\t"+rnd(times[dendConstr]-times[dstartConstr],3)+"\t"+rnd(times[dendDilation]-times[dendConstr],3)+
						"\t"+rnd(dminVal/aveBase,3)+"\t"+rnd(dmaxVal/aveBase,3));
				summaryTable.setVisible(true);
				txtp.updateColumnHeadings(headings+"\tCSDTime\tNorm Diameter");
				for(int i=0;i<txtp.getLineCount();i++) txtp.setLine(i,txtp.getLine(i)+"\t"+times[i]+"\t"+(diameters[i]/aveBase));
			}
			plot.show();
			
		}
		
		IJ.showStatus("Diameter Profile Completed");
		IJ.setTool(tool);
	}


	public static String[] getTextLUTs(ImagePlus imp){
		int chs=imp.getNChannels();
		String[] tluts=new String[chs];
		if(imp.getBitDepth()==24) {IJ.error("RGB image"); return null;}
		LUT[] luts=imp.getLuts();
		for(int i=0;i<chs;i++){
			int redv=luts[i].getRed(128);
			int greenv=luts[i].getGreen(128);
			int bluev=luts[i].getBlue(128);
			int max=Math.max(redv, Math.max(greenv, bluev));
			boolean red=(max==redv); boolean green=(max==greenv); boolean blue=(max==bluev);
			String result="";
			if(red) {
				result="red";
				if(green) result="yellow";
				if(blue) {
					result="magenta";
					if(green) result="black"; //!! Change
				}
			}
			else if(green) {
				result="green";
				if(blue) result="cyan";
			}
			else if(blue) result="blue";
			tluts[i]=result;
		}
		return tluts;
	}
	
	private int[] getThresh(double[] profile, int rfd, String threshLevel) {
		
		if(profile==null)return null;
		
		double[] aveprof=new double[profile.length-rfd+1];
		for(int i=0;i<aveprof.length;i++) {
			double ave=0;
			for(int j=0;j<rfd;j++) ave+=profile[i+j];
			aveprof[i]=ave/rfd;
		}
		double mean=aveprof[0],min=mean,max=min;
		for(int i=1;i<aveprof.length;i++) {
			mean+=aveprof[i];
			if(aveprof[i]>max)max=aveprof[i];
			if(aveprof[i]<min)min=aveprof[i];
		}
		mean/=(double)aveprof.length;
		
		double[] formedian=Arrays.copyOf(aveprof, aveprof.length);
		Arrays.sort(formedian);
		double median=(formedian.length%2==0)?((formedian[formedian.length/2]+formedian[formedian.length/2-1])/2):(formedian[formedian.length/2]);
		
		double thresh=mean;
		if(threshLevel=="Median")thresh=median;
		else if(threshLevel=="Mid")thresh=(max+min)/2;
		else if(threshLevel=="Top 1/3")thresh=(max-min)*2/3+min;
		int[] result=new int[aveprof.length];
		for(int i=0;i<aveprof.length;i++) {
			result[i]=(profile[i]>thresh)?255:0;
		}
		//despeckle
		int hapl=aveprof.length/2;
		for(int i=hapl+1;i<aveprof.length-1;i++) {
			if(result[i-1]==result[i+1])result[i]=result[i+1];
			if(result[i-hapl-1]==result[i-hapl+1])result[i-hapl]=result[i-hapl+1];
		}
		return result;
		
	}

	private int[] diameter(int prevrise,int prevfall,double[] profile, int rfd){
		double range=0.1;
		//rfd = rise/fall distance.  Originally 3 but that seemed quite sharp
		if(rfd%2==0)rfd++; //should be odd
		int fall = 1;
		int rise = 1;
		int[] result=new int[2];
		
		double mean=profile[0],min=mean,max=min;
		for(int i=1;i<profile.length;i++) {
			mean+=profile[i];
			if(profile[i]>max)max=profile[i];
			if(profile[i]<min)min=profile[i];
		}
		mean/=(double)profile.length;
		
		double[] aveprof=new double[profile.length-rfd+1];
		for(int i=0;i<aveprof.length;i++) {
			double ave=0;
			for(int j=0;j<rfd;j++) ave+=profile[i+j];
			aveprof[i]=ave/rfd;
		}
		double[] rises=new double[aveprof.length-rfd];
		for(int i=0;i<aveprof.length-rfd;i++) {rises[i]=aveprof[i+rfd]-aveprof[i];}
		double[] risesort=Arrays.copyOf(rises, rises.length);
		Arrays.sort(risesort);

		boolean ok=true; 
		
		if(prevrise==0)ok=false;
		int i,mindist=risesort.length+1,dist=0,end=risesort.length/2;
		for(i=risesort.length-1;i>end;i--) {
			rise=indexOfArray(rises,risesort[i]);
			dist=Math.abs(prevrise-rise);
			if(ok) {
				ok=range<((double)dist)/((double)profile.length);
			}
			if(!ok)break;
		}
		if(i==end) {
			for(i=risesort.length-1;i>risesort.length-9;i--) {
				int temprise=indexOfArray(rises,risesort[i]);
				dist=Math.abs(prevrise-rise);
				mindist=Math.min(mindist,dist);
				if(mindist==dist)rise=temprise;
			}
		}
		for(i=0;i>end;i++) {
			fall=indexOfArray(rises,risesort[i]);
			dist=Math.abs(prevfall-fall);
			if(ok) {
				ok=range<((double)dist)/((double)profile.length);
			}
			if(!ok)break;
		}
		if(i==end) {
			mindist=aveprof.length+1;
			for(i=0;i>8;i--) {
				int tempfall=indexOfArray(rises,risesort[i]);
				dist=Math.abs(prevfall-fall);
				mindist=Math.min(mindist,dist);
				if(mindist==dist)fall=tempfall;
			}
		}
		//return ""+(endpt-startpt)*pw+"\t"+rise*pw+"\t"+fall*pw+"\t"+startpt*pw+"\t"+endpt*pw;
		result[0]=rise; result[1]=fall;
		return result;
	}
	
	float[] toFloatArray(double[] arr) {
	  if (arr == null) return null;
	  int n = arr.length;
	  float[] ret = new float[n];
	  for (int i = 0; i < n; i++) {
	    ret[i] = (float)arr[i];
	  }
	  return ret;
	}
	
	int indexOfArray(double[] array, double key) {
		for(int i=0;i<array.length;i++) if(array[i]==key)return i;
		return -1;
	}

	private void editTable(TextWindow table,String colhead,int row,String value){
		String[] headings=table.getTextPanel().getColumnHeadings().split("\t");
		int col=-1;
		for(int i=0;i<headings.length;i++)if(headings[i].equals(colhead))col=i;
		if(col==-1) {IJ.log("Heading "+colhead+" not found"); return;}
		String[] line=table.getTextPanel().getLine(row).split("\t");
		line[col]=value;
		String replace="";
		for(int i=0;i<line.length;i++)replace+=line[i];
		table.getTextPanel().setLine(row, replace);
		table.getTextPanel().updateDisplay();
	}
	
	public static String rnd(double a, int num) {
		return Double.toString( ((double)Math.round(a*Math.pow(10,(double)num)))/Math.pow(10,(double)num));
	}
}