package ajs.tools;

import ij.plugin.PlugIn;
import ij.gui.*;
import ij.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import ij.process.*;
import ij.text.*;

/**
 * This is a template for a plugin that does not require one image
 * (be it that it does not require any, or that it lets the user
 * choose more than one image in a dialog).
 */
public class Thresh_Cell_Transfer2 implements PlugIn, MouseListener, KeyListener, MouseWheelListener {

	boolean done=false, gotspot=false, acceptedROI=false, gocellcomplete=false, spacepress=false, buttonpress=false;
	boolean drawCellLabel=false;
	int x,y;
	int goback=2, loglength=0, labelsl=0, celln=1;
	double prevthresh=250;
	int wheelfactor = 10;
	int MYLUT=ImageProcessor.RED_LUT;
	ImagePlus simp;
	ImagePlus timp;
	String title;
	String endtitle;
	ArrayList<String> cellLabels=new ArrayList<String>();
	String cellLabel="Unlabeled";
	Roi[] curWand, tsel;
	Point[] xys;
	Point[] centroids;
	double[] areas;
	//Wand w;

	
	/**
	 * This method gets called by ImageJ / Fiji.C
	 *
	 * @param arg can be specified in plugins.config
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@Override
	public void run(String arg) {
		IJ.log("\\Clear");
		IJ.log("TCT ver 0.1.7");
		WindowManager.setWindow(WindowManager.getWindow("Log"));
        int sl=0,fr=0,frms=0,lastfr=-1;
		Roi sel;
		String[] output;
		String slsleft="";
		GenericDialog gd;

		IJ.setForegroundColor(255,255,255);
		simp = WindowManager.getCurrentImage();
		if (simp==null) {IJ.log("noImage"); return;}
		title=simp.getTitle();
		String basetitle=title.endsWith(".tif")?title.substring(0,title.length()-4):title;
		if(title.endsWith("-AJTCT") || title.endsWith("-AJTCT.tif")){
			endtitle=title;
			int indfromend;
			if(title.endsWith("-AJTCT")) indfromend=6; else indfromend=10;
			title=endtitle.substring(0,endtitle.length()-indfromend);
			timp=simp;
			simp=WindowManager.getImage(title);
			if(simp==null)simp=WindowManager.getImage(title+".tif");
			if(simp!=null){basetitle=title; title=title+".tif";}
			else{IJ.error("Can't find original image: "+title); return;}
		}
		if(timp==null){
			endtitle=basetitle+"-AJTCT";
			timp=WindowManager.getImage(endtitle);
			if(timp==null) {
				timp=WindowManager.getImage(endtitle+".tif");
				if(timp!=null)endtitle=endtitle+".tif";
			}
		}
        ImageWindow siw=simp.getWindow();
		double psize=simp.getCalibration().pixelWidth;

		TctTextWindow results=new TctTextWindow(basetitle);
		if(IJ.getLog()!=null) loglength = IJ.getLog().split("\n").length;
		if(loglength>25) {IJ.log("\\Clear"); loglength=0;}
    	sl=simp.getSlice(); fr=simp.getFrame();
    	frms=simp.getNFrames();
        
        if(timp==null) {
			timp=IJ.createImage(endtitle,"8-bit composite", simp.getWidth(),simp.getHeight(), 2, 1, frms);
			if(timp==null) IJ.log("null");
			timp.show();
			//timp.getProcessor().invertLut();
			//Rectangle tbounds=timp.getWindow().getBounds();
        	Rectangle sbounds=siw.getBounds();
        	//double sizefactor=sbounds.getWidth()/tbounds.getWidth();
			timp.getWindow().setLocationAndSize((int) (sbounds.getX()+sbounds.getWidth()+5),(int) sbounds.getY(),(int) sbounds.getWidth(),65535);
		}
		if(timp==simp){IJ.log("Same window exiting"); return;}
		if(timp==null){IJ.log("No target window"); return;}

    	askCellLabel();
		
		double cthresh=simp.getProcessor().getMinThreshold();
		if(cthresh>0)prevthresh=cthresh;

        WindowManager.setWindow(timp.getWindow());
        WindowManager.setWindow(siw);
        preStrip();
        ImageCanvas ic = simp.getCanvas();
        ic.disablePopupMenu(true);
        ic.addMouseListener(this);
        ic.addKeyListener(this);
        siw.removeMouseWheelListener(siw);
        siw.addMouseWheelListener(this);
        
		int prevsl=sl, prevfr=fr;
		int prevcelln=-1;
		int xprev=x, yprev=y;
        boolean justfirst, postFirstAccept=false;
        output=new String[frms];
        curWand=new Roi[frms];
        tsel=new Roi[frms];
        xys=new Point[frms];
        centroids=new Point[frms];
        areas=new double[frms];

        //-------loop-------------
        
        while(!done){
        	simp.setT(fr);
        	if(celln!=prevcelln){
				slsleft=""; for(int i=0;i<frms;i++) slsleft=slsleft+(i+1)+" ";
        		IJ.log("\\Update"+(loglength+0)+":Slices left:"+slsleft);
	            output=new String[frms];
	            xys=new Point[frms];
	            centroids=new Point[frms];
	            x=-1; y=-1; xprev=x;yprev=y; 
	            lastfr=-1;
	            tsel=new Roi[frms];
	            curWand=new Roi[frms];
	            areas=new double[frms];
	            postFirstAccept=false;
	            simp.getProcessor().setThreshold(prevthresh, (double) 65535, MYLUT);
	            prevsl=simp.getSlice(); prevfr=simp.getFrame();
        		prevcelln=celln;
        	}
			acceptedROI=false;
			justfirst=true;
			while(!acceptedROI && !gocellcomplete) {
				do{
	        		fr=simp.getFrame(); sl=simp.getSlice();
	        		timp.setPosition(1,labelsl,fr);
	        		if(sl!=prevsl || fr!=prevfr) {
		        		if(postFirstAccept && curWand[fr-1]!=null) {
		        			if(simp.getRoi()!=curWand[fr-1]){
		        				simp.setRoi(curWand[fr-1]);
		        				timp.setRoi((Roi)curWand[fr-1].clone());
		        			}
		        		}
	        			justfirst=true;
	        		}
	        		prevsl=sl; prevfr=fr;
	        		IJ.wait(10);
	        	}while(sl!=prevsl || fr!=prevfr);
	        	
		        if(((x!=xprev||y!=yprev) /* || justfirst*/) && x!=-1){
			        xprev=x;yprev=y;
	        		sel=doWandRoi();
	        		
	        		if(justfirst &&lastfr!=-1 && xys[lastfr-1]!=null ){
	        			//test if new center is farther than the area's equivalent radius away from the old center
	        			sel=doWandRoiByPt(fr);
	        		}
	        		curWand[fr-1]=sel;
	        		justfirst=false;
	        		IJ.log("\\Update"+(loglength+2)+":x:"+x+" y:"+y+" z:"+sl+" fr:"+fr);
		        }
		        if(WindowManager.getWindow(title)==null || WindowManager.getWindow(endtitle)==null) {IJ.log("Window Closed"); done=true;}
    			if(done) {cleanup(); return;}
				//if((auto||!firsthit) && x!=-1 && y!=-1 && !firsttime)acceptedROI=true; else IJ.wait(200);
			}
			
			if(WindowManager.getCurrentImage()!=simp){
				gd = new GenericDialog("Cancel");
		        gd.addMessage("Cancel?");
		        gd.enableYesNoCancel("Yes", "No");
		        gd.showDialog();
		        if (gd.wasCanceled()){
		            cleanup(); return;}
		        else if (gd.wasOKed()){
		            cleanup();return;}
			}
			IJ.log("\\Update"+(loglength+0)+":Slices left: "+slsleft);

			sel=simp.getRoi();
    		curWand[fr-1]=simp.getRoi();
			if(sel==null){
				IJ.log("No selection");
			}else if(!gocellcomplete){
				ImageStatistics imgstat=simp.getStatistics(127);
				double perimeter,circularity;
				perimeter=simp.getRoi().getLength();
				circularity = perimeter==0.0?0.0:4.0*Math.PI*(imgstat.area/(perimeter*perimeter));
				IJ.log("\\Update"+(loglength+1)+":Lbl: "+cellLabel+" Cell: "+celln+" Sl: "+fr+" Area: "+IJ.d2s(imgstat.area)+" Mean: "+IJ.d2s(imgstat.mean)+" X: "+IJ.d2s(imgstat.xCentroid)+" Y: "+IJ.d2s(imgstat.yCentroid)+" thresh: "+prevthresh);
				output[fr-1]=""+cellLabel+"\t"+results.roin+"\t"+celln+"\t"+imgstat.area+"\t"+perimeter+"\t"+circularity+"\t"+imgstat.xCentroid+"\t"+imgstat.yCentroid+"\t"+imgstat.mean+"\t"+sl+"\t"+fr+"\t"+prevthresh;

				//x=(int)(imgstat.roiX/psize);
				//y=(int)(imgstat.roiY/psize);
				centroids[fr-1]=new Point((int)(imgstat.xCentroid/psize),(int)(imgstat.yCentroid/psize));
				areas[fr-1]=imgstat.area;
				//WindowManager.setWindow(timp.getWindow());
				timp.setPosition(1,labelsl,fr);
				if(tsel[fr-1]!=null){ //erase old roi
					timp.setRoi(tsel[fr-1]);
					ImageProcessor tip=timp.getProcessor();
					tip.setColor(0);
					tip.fill(tsel[fr-1]);
					IJ.wait(100);
					timp.deleteRoi();
					tsel[fr-1]=null;
				}
				tsel[fr-1]=(ij.gui.Roi) sel.clone();
				tsel[fr-1].setImage(timp);
				timp.setRoi(tsel[fr-1]);
				timp.setPosition(1,labelsl,fr);
				ImageProcessor tip=timp.getProcessor();
				tip.setColor(255);
				tip.fill(tsel[fr-1]);
				tsel[fr-1]=ij.plugin.RoiEnlarger.enlarge(tsel[fr-1],1);
				tip.setColor(0);
				tip.draw(tsel[fr-1]);
				tip.setColor(255);
				timp.updateAndRepaintWindow();
				
				if(!postFirstAccept) {
					postFirstAccept=true;
					curWand[fr-1]=sel;
					updateCurWand();
				}
			}
			//long presstime=System.nanoTime();
			while(spacepress && !gocellcomplete) {
				IJ.wait(20);
				//presstime=System.nanoTime()-presstime;
				//waittime=800;
				//if(!firsthit)waittime=100;
				//IJ.log("wait: "+presstime);
				//if((presstime/1000000)>waittime){firsthit=false; break;}
			}
			boolean cellcomplete=true;
			slsleft="";
			int lowsl=1;
			for(int i=frms;i>0;i--) {if(output[i-1]==null) {cellcomplete=false; slsleft=(i)+" "+slsleft; lowsl=i;}}
			if(cellcomplete || gocellcomplete){
				gocellcomplete=false;
				//firsthit=true;
				gd = new GenericDialog("Cancel");
		        gd.addMessage("All done with cell #"+celln+"?");
		        gd.enableYesNoCancel("Yes", "No");
		        gd.showDialog();
		        if (gd.wasOKed()){
		            for(int i=0;i<frms;i++) {
		            	if(output[i]!=null){
		            		results.append(output[i]);
			            	if(centroids[i]!=null){
								timp.setPosition(2,labelsl,(i+1));
								ImageProcessor tip=timp.getProcessor();
								String cl=""+celln;
								if(drawCellLabel)cl=cl.concat(" "+cellLabel);
								Roi textr=new TextRoi(centroids[i].x-10,centroids[i].y-20,cl);
								timp.setColor(Color.white);
								tip.setColor(255);
								timp.setRoi(textr);
								tip.draw(textr);
			            	}
		            	}else{
							results.append(cellLabel+"\t0\t"+celln+"\tNA\tNA\tNA\tNA\tNA\tNA\tNA\t"+(i+1)+"\t"+prevthresh);
		            	}
		            }
		            timp.setPosition(1,labelsl,fr);
		            timp.updateAndRepaintWindow();
		            celln++; fr=0;
	            	sel=null; simp.deleteRoi();
	            	timp.deleteRoi();
		        }
			}
	        lastfr=fr;
			if(fr<frms) fr++; else fr=lowsl;
			results.roin++;
		}
        cleanup();
	}

	private void addLabel(String newlabel){
		if(!cellLabels.contains(newlabel)){
			cellLabels.add(newlabel);
			celln=1;
		}
        labelsl=cellLabels.indexOf(newlabel)+1;
		while(labelsl>timp.getNSlices()){
			WindowManager.setCurrentWindow(timp.getWindow());
       		timp.setZ(timp.getNSlices());
        	IJ.run("Add Slice", "add=slice");IJ.wait(200);
		}
    	timp.setPosition(1,labelsl,1);
    	timp.getStack().setSliceLabel(newlabel,timp.getCurrentSlice());
	}

	private void askCellLabel(){
		GenericDialog gd = new GenericDialog("Add new cell label");
		ArrayList<String> askLabels=new ArrayList<String>(cellLabels);
		if(!askLabels.contains("Dura"))askLabels.add(0, "Dura");
		if(!askLabels.contains("Pia"))askLabels.add(1, "Pia");
    	gd.addChoice("Labels: ",askLabels.toArray(new String[askLabels.size()]),cellLabels.isEmpty()?askLabels.get(0):cellLabels.get(cellLabels.size()-1));
    	gd.addStringField("Or new label: ", "");
		gd.addCheckbox("Draw Label on AJTCT stack?", drawCellLabel);
        gd.showDialog();
        if (gd.wasCanceled()) return;
        cellLabel=gd.getNextChoice();
        String temp=gd.getNextString();
        if(!temp.equals(""))cellLabel=temp;
    	addLabel(cellLabel);
		drawCellLabel=gd.getNextBoolean();
	}

	private void cleanup(){
		if(simp!=null){
			ImageCanvas ic=simp.getCanvas();
			if(ic!=null){
				ImageWindow siw=simp.getWindow();
	        	ic.removeKeyListener(this);
	        	ic.removeMouseListener(this);
	       		siw.removeMouseWheelListener(this);
	        	siw.addMouseWheelListener(siw);
			}
		}
        IJ.log("Thresh Cell Tranfer complete");
	}

	private void preStrip(){
		if(simp==null)return;
		ImageCanvas ic=simp.getCanvas();
		ImageWindow siw=simp.getWindow();
		KeyListener[] kls=ic.getKeyListeners();
		for(int i=0;i<kls.length;i++) {
			if(kls[i].getClass().getName().startsWith("Thresh_Cell")){
				ic.removeKeyListener(kls[i]);
				IJ.log("Removed old keyL"+kls[i]);
			}
		}
		MouseListener[] mls=ic.getMouseListeners();
		for(int i=0;i<mls.length;i++) {
			if(mls[i].getClass().getName().startsWith("Thresh_Cell")){
				ic.removeMouseListener(mls[i]);
				IJ.log("Removed old mL"+mls[i]);
			}
		}
		MouseWheelListener[] mwls=siw.getMouseWheelListeners();
		boolean hasit=false;
		for(int i=0;i<mwls.length;i++) {
			if(mwls[i].getClass().getName().startsWith("Thresh_Cell")){
				siw.removeMouseWheelListener(mwls[i]);
				IJ.log("Removed old mwL"+mwls[i]);
			}
			if(mwls[i]==siw)hasit=true;
		}
		if(!hasit) {
			IJ.log("Replacing ImageWindow MouseWheel Listener");
			siw.addMouseWheelListener(siw);
		}
	}
	
	class TctTextWindow{
		TextWindow rtw;
		public int roin;
		
		public TctTextWindow(String basetitle) {
			final String heading="Label"+"\t"+"ROI"+"\t"+"Cell#"+"\t"+"Area"+"\t"+"Perimeter"+"\t"+"Circularity"+"\t"+"X"+"\t"+"Y"+"\t"+"Mean"+"\t"+"Slice"+"\t"+"Frame"+"\t"+"Thresh";
			String restitle="ThreshCellTransfer-"+basetitle+".txt";
			rtw=(TextWindow) WindowManager.getWindow(restitle);
			if(rtw==null){
				restitle="ThreshCellTransfer-"+basetitle+".csv";
				rtw=(TextWindow) WindowManager.getWindow(restitle);
				if(rtw==null){restitle="ThreshCellTransfer-"+basetitle+".xls"; rtw=(TextWindow) WindowManager.getWindow(restitle);}
			}
			if(rtw!=null){
				TextPanel panel=rtw.getTextPanel();
				//IJ.log(""+panel.getLineCount());
				if(!panel.getColumnHeadings().startsWith(heading) || panel.getLineCount()==0){
					rtw.close();
					rtw=null; panel=null;
				}else{
					int troin=0;
					celln=0;
					String[] oldline;
					for(int i=0;i<panel.getLineCount();i++) {
						oldline=panel.getLine(i).split("\t");
						if(i==0){cellLabels.add(oldline[0]);}
						else{ if(!cellLabels.contains(oldline[0]))cellLabels.add(oldline[0]); }
						if(!oldline[1].startsWith("NA"))troin=(int)Float.parseFloat(oldline[1]);
						if(!oldline[2].startsWith("NA"))celln=(int) Float.parseFloat(oldline[2]);
						if(troin>roin)roin=troin;
						//newline=""+troin+"\t"+celln+"\t"+oldline[2]+"\t"+oldline[3]+"\t"+oldline[4]+"\t"+oldline[5]+"\t"+(int)Float.parseFloat(oldline[6])+"\t"+(int)Float.parseFloat(oldline[7]);
						//panel.setLine(i,newline);
					}
					celln++;roin++;
					IJ.log("\\Update1:Using open window, roin: "+roin+" celln: "+celln);
				}
			}
			if(rtw==null){
				rtw=new TextWindow(restitle,heading,"",800,400);
			}
		}
		
		public void append(String text) {rtw.append(text);}
	}
	
	private Roi doWandRoi() {
		Roi sel=doWandRoi(simp.getProcessor(),x,y);
		simp.setRoi(sel);
		timp.setRoi((Roi)sel.clone());
		xys[simp.getFrame()-1]=new Point(x,y);
		return sel;
	}

	private Roi doWandRoi(ImageProcessor sip, int xw, int yw){
		if(xw==-1 || yw==-1) return null;
		
		Wand w=new Wand(sip);
		w.autoOutline(xw,yw,prevthresh,(double)65535);
		Roi sel=new ShapeRoi(new PolygonRoi(w.xpoints, w.ypoints, w.npoints, Roi.POLYGON));

		Rectangle sbs=sel.getBounds();
		//String temp="sgw: "+simp.getWidth()+" selw: "+sbs.getWidth()+" selx: "+sbs.getX();
		if(sel!=null && simp.getWidth()-sbs.getWidth()>20 && simp.getHeight()-sbs.getHeight()>20 && sbs.getX()>0 && sbs.getY()>0 && sbs.getWidth()>5 && sbs.getHeight()>5){
			sel=ij.plugin.RoiEnlarger.enlarge(sel,3);
			sel=ij.plugin.RoiEnlarger.enlarge(sel,-3);
			//IJ.log("Enlarged "+temp);
		}//else IJ.log(temp);
		return sel;
	}
	
	private Roi doWandRoiByPt(int frame) {
		ImageStatistics imgstat;
		ImageProcessor ip=simp.getStack().getProcessor(simp.getStackIndex(simp.getC(), simp.getZ(), frame));
		ij.measure.Calibration cal=simp.getCalibration();
		double prevarea=simp.getStatistics(127).area;
		Roi sel=null;
		double psize=cal.pixelWidth;
		int cx=x,cy=y;
		Point pt=null;
		for(int i=((frame>1)?(frame-2):0);i>=0;i--) {
			if(centroids[i]!=null) {pt=centroids[i]; prevarea=areas[i]; break;}
		}
		if(pt==null){
			for(int i=frame-1;i<simp.getNFrames();i++) {
				if(centroids[i]!=null) {pt=centroids[i]; prevarea=areas[i]; break;}
			}
		}
		if(pt==null)return doWandRoi(ip,cx,cy);
		for(int attempts=0; attempts<6; attempts++){
			boolean isok=false;
			sel=doWandRoi(ip,cx,cy);
			if(sel!=null){
				ip.setRoi(sel);
				imgstat=ImageStatistics.getStatistics(ip, 127, cal);
				if(Math.hypot((pt.x-imgstat.xCentroid/psize),(pt.y-imgstat.yCentroid/psize))<(Math.sqrt(prevarea/Math.PI)/psize)) isok=true;
			}
			if(!isok) {
				if(attempts==0){cx=pt.x; cy=pt.y;}
				else {cx=x-(attempts*10); cy=y+(attempts*10);}
				sel=doWandRoi(ip,cx,cy);
			}else break;
		}

		xys[frame-1]=new Point(cx,cy);
		return sel;
	}
	
	private void updateCurWand() {
		int fr=simp.getFrame();
		for(int i=0; i<simp.getNFrames(); i++) {
			if(i==(fr-1))continue;
			if(tsel[i]!=null) {
			}else curWand[i]=doWandRoiByPt(i+1);
			//IJ.log("curWand"+i+" "+curWand[i]);
		}
	}
	
	private void simpleUpdateCurWand() {
		int cx=x,cy=y;
		int cfr=simp.getFrame();
		for(int i=0;i<simp.getNFrames();i++) {
			if(tsel[i]==null || (i==(cfr-1)) ) {
				if(xys[i]!=null) {cx=xys[i].x; cy=xys[i].y;}
				curWand[i]=doWandRoi(simp.getStack().getProcessor(simp.getStackIndex(simp.getC(), simp.getZ(), i+1)),cx,cy);
			}
		}
	}

	public void finish(){
		done=true;
	}
	
    public synchronized void mouseWheelMoved(MouseWheelEvent e) {
		int rotation = e.getWheelRotation();
		int amount = e.getScrollAmount();
		boolean ctrl = (e.getModifiers()&Event.CTRL_MASK)!=0;
		
		/*
			IJ.log("mouseWheelMoved: "+e);
			IJ.log("  type: "+e.getScrollType());
			IJ.log("  ctrl: "+ctrl);
			IJ.log("  rotation: "+rotation);
			IJ.log("  amount: "+amount);
		*/
		if (!ctrl) {
			//if (amount<1) amount=1;
			amount=1;
			if (rotation==0)
				return;
			ImageProcessor sip=simp.getProcessor();
			if(prevthresh<20)wheelfactor=1;
			prevthresh+=(rotation*amount*wheelfactor);
			sip.setThreshold(prevthresh, (double) 65535, MYLUT);
	        IJ.log("\\Update"+(loglength+3)+":Thresh: "+simp.getProcessor().getMinThreshold());
			simp.updateAndRepaintWindow();
			simpleUpdateCurWand();
		} else{
			simp.getWindow().mouseWheelMoved(e);
		}
    }

	
    public void actionPerformed(ActionEvent event){ 
            IJ.log("Button pressed"); 
    } 

    public void mousePressed(MouseEvent e) { 
    	if(IJ.altKeyDown() || IJ.shiftKeyDown() || IJ.spaceBarDown()) return;
    	if(spacepress)buttonpress=true;
        int flags = e.getModifiers();
        //IJ.log("flags: "+flags+" Mask1: "+InputEvent.BUTTON1_MASK+" Mask2: "+InputEvent.BUTTON2_MASK+" Mask3: "+InputEvent.BUTTON3_MASK);
        if((flags & InputEvent.BUTTON1_MASK) !=0){
        	Point xy = simp.getCanvas().getCursorLoc(); //because x,y position isn't exactly x,y in image
        	x=xy.x; y=xy.y;
			gotspot=true;
        }
    }
    public void mouseReleased(MouseEvent e) {
    	if(IJ.altKeyDown() || IJ.shiftKeyDown()) return;
    	int flags = e.getModifiers();
        if((flags & InputEvent.BUTTON3_MASK) !=0 ){
			if(goback==1){IJ.run("Previous Slice [<]");}
			else{acceptedROI=true;}
        }
        if((flags & InputEvent.BUTTON2_MASK) !=0 ){
			IJ.run("Previous Slice [<]");
        }
	} 
    public void mouseExited(MouseEvent e) {} 
    public void mouseClicked(MouseEvent e) {}	
    public void mouseEntered(MouseEvent e) {} 
     
    
    public void keyPressed(KeyEvent e) {
    	int keyCode = e.getKeyCode();
        //char keyChar = e.getKeyChar();
        //int flags = e.getModifiers();
        //IJ.log("keyPressed: keyCode=" + keyCode + " (" + KeyEvent.getKeyText(keyCode) + ")");
       	if(keyCode==32) { //space key
       		spacepress=true;
       	}
   	}
    public void keyReleased(KeyEvent e) {
        int keyCode = e.getKeyCode();
        //char keyChar = e.getKeyChar();
        if(keyCode==32) { //space key
        	if(!buttonpress){
       			if(goback==2){IJ.run("Previous Slice [<]");}
       			else{acceptedROI=true;}
        	}
       		spacepress=false; buttonpress=false;
        }
       	if(keyCode==KeyEvent.VK_F1){
       		MYLUT++;
       		if(MYLUT>3)MYLUT=0;
       		if(MYLUT==2) {
       			ImageProcessor sip=simp.getProcessor();
       			sip.setMinAndMax(sip.getMin(),sip.getMax());
       		}
       	}
        if(keyCode==KeyEvent.VK_F2){
        	goback++; if(goback>2)goback=0;
        	if(goback==1) IJ.log("\\Update"+(loglength+4)+":Rightclick now goes back a frame");
        	else if(goback==2)IJ.log("\\Update"+(loglength+4)+":Space key now goes back a frame");
        	else IJ.log("\\Update"+(loglength+4)+":No back button");
        }
        if(keyCode==KeyEvent.VK_F3){
        	askCellLabel();
        }
        if(keyCode==KeyEvent.VK_F4){gocellcomplete=true;}
        if(keyCode==KeyEvent.VK_UP || keyCode==KeyEvent.VK_DOWN){
        	if(keyCode==KeyEvent.VK_UP){
	        	if(wheelfactor==1) wheelfactor=10;
	        	else if(wheelfactor==10)wheelfactor=30;
        	}else{
	        	if(wheelfactor==30) wheelfactor=10;
	        	else if(wheelfactor==10)wheelfactor=1;
        		
        	}
        	
			/*
			 * int rotation=1;
			 * if(keyCode==KeyEvent.VK_DOWN) rotation=-1;
			 * ImageProcessor sip=simp.getProcessor();
			 * prevthresh+=rotation;
			 * sip.setThreshold(prevthresh, (double) 65535, MYLUT);
	         * IJ.log("\\Update"+(loglength+3)+":MinThresh: "+simp.getProcessor().getMinThreshold());
			 * simp.updateAndRepaintWindow();
			 * doWandRoi(sip);
			*/
        }
       	if(keyCode==27) {done=true;}
   	}
   	
    public void keyTyped(KeyEvent e) {}
}
