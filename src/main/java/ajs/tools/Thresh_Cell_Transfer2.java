package ajs.tools;

import ij.plugin.*;
import ij.gui.*;
import ij.measure.Calibration;
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
	
	/**TODO
	 *  ucwFrs[frame-1]
	 *  auto function where acceptedROI goes through until ucwFrs is false
	 */
	
	final static String version="1.2.0";
	final static boolean DEBUG=false;
	boolean done=false, acceptedROI=false, gocellcomplete=false, spacepress=false;
	boolean drawCellLabel=false;
	boolean[] buttonpress=new boolean[3];
	int x,y;
	int goback=2, labelsl=0, celln=1;
	double prevthresh=250;
	int wheelfactor = 10;
	int MYLUT=ImageProcessor.RED_LUT;
	private ImagePlus simp;
	private ImagePlus timp;
	String title, endtitle, basetitle;
	ArrayList<String> cellLabels=new ArrayList<String>();
	String cellLabel="Unlabeled";
	Roi[] curWand, tsel;
	Point[] xys;
	Point[] centroids;
	double[] areas;
	final int[] whfacs= new int[] {1,10,30};
	int[] threshPredictions;
	TCTPanel tctpanel;
	MyThread updateCurWandThread=null;
	boolean[] ucwFrs;
	//Wand w;
	
	public void run(String arg) {
		TCT(WindowManager.getCurrentImage());
	}
	
	public void TCT(ImagePlus imp) {
		simp=imp;
		if (simp==null) {IJ.log("noImage"); return;}
		setup();

        tctpanel=new TCTPanel();
        tctpanel.setVisible(true);

		Calibration cal=simp.getCalibration();

    	int sl=simp.getSlice(), fr=simp.getFrame();
    	int frms=simp.getNFrames();
		int prevsl=sl, prevfr=fr;
		int prevcelln=-1;
		int xprev=x, yprev=y;
        boolean justfirst, postFirstAccept=false;
        String[] output=new String[frms];
		String slsleft="";
		TctTextWindow results=new TctTextWindow(basetitle);
		if("Unlabeled".equals(cellLabel)) askCellLabel();
		int lastfr=-1;

        //-------loop-------------
        
        while(!done){
        	simp.setT(fr);
        	if(celln!=prevcelln){
				slsleft=""; for(int i=0;i<frms;i++) slsleft+=(i+1)+" ";
        		tctpanel.setTextLine(4, "Slices left:"+slsleft);
	            output=new String[frms];
	            x=-1; y=-1; xprev=x;yprev=y; 
	            lastfr=-1;
	            curWand=new Roi[frms];
	            tsel=new Roi[frms];
	            xys=new Point[frms];
	            centroids=new Point[frms];
	            areas=new double[frms];
	            ucwFrs=new boolean[frms];
	            postFirstAccept=false;
	            simp.getProcessor().setThreshold(prevthresh, (double) 65535, MYLUT);
	            prevsl=simp.getSlice(); prevfr=simp.getFrame();
        		prevcelln=celln;
        	}
			acceptedROI=false;
			justfirst=true;
			Roi sel;
			while(!acceptedROI && !gocellcomplete) {
				do{
	        		fr=simp.getFrame(); sl=simp.getSlice();
	        		timp.setPosition(1,labelsl,fr);
	        		if(sl!=prevsl || fr!=prevfr) {
	        			tctpanel.printOutput(output[fr-1]);
	        			//tctpanel.setTextLine(5, "Sl"+sl+" psl"+prevsl+" fr"+fr+" pfr"+prevfr);
		        		if(curWand[fr-1]!=null) {
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
			        
			        sel=doWandRoi(simp.getProcessor(),x,y);
					simp.setRoi(sel);
					timp.setRoi((Roi)sel.clone());
					xys[simp.getFrame()-1]=new Point(x,y);
			        if(!postFirstAccept)updateCurWand();
	        		
	        		if(justfirst && lastfr!=-1 && xys[lastfr-1]!=null ){
	        			//test if new center is farther than the area's equivalent radius away from the old center
	        			sel=doWandRoiByPt(fr);
	        		}
	        		curWand[fr-1]=sel;
	        		justfirst=false;
	        		tctpanel.setTextLine(2, "x:"+x+" y:"+y+" z:"+sl+" fr:"+fr);
		        }
		        if(WindowManager.getWindow(title)==null || WindowManager.getWindow(endtitle)==null) {IJ.log("Window Closed"); done=true;}
    			if(done) {cleanup(); return;}
    			if(((spacepress && goback!=2) || buttonpress[2]&&goback!=1))IJ.wait(10);
				if(x!=-1 && y!=-1 && ((spacepress && goback!=2) || buttonpress[2]&&goback!=1)){acceptedROI=true;}
    			//if((auto||!firsthit) && x!=-1 && y!=-1 && !firsttime)acceptedROI=true; else IJ.wait(200);
    			
			}
			
			if(WindowManager.getCurrentImage()!=simp){
				GenericDialog gd = new GenericDialog("Cancel");
		        gd.addMessage("Cancel?");
		        gd.enableYesNoCancel("Yes", "No");
		        gd.showDialog();
		        if (gd.wasCanceled()){
		            cleanup(); return;}
		        else if (gd.wasOKed()){
		            cleanup();return;}
			}
			tctpanel.setTextLine(4, "Slices left: "+slsleft);

    		curWand[fr-1]=simp.getRoi();
			if(curWand[fr-1]==null){
				IJ.log("No selection");
			}else if(!gocellcomplete){
				ImageProcessor ip=simp.getProcessor();
				ip.setRoi(curWand[fr-1]);
				ImageStatistics imgstat=ImageStatistics.getStatistics(ip, 127, cal);
				double perimeter,circularity;
				perimeter=simp.getRoi().getLength();
				circularity = perimeter==0.0?0.0:4.0*Math.PI*(imgstat.area/(perimeter*perimeter));
				output[fr-1]=""+cellLabel+"\t"+results.roin+"\t"+celln+"\t"+imgstat.area+"\t"+perimeter+"\t"+circularity+"\t"+imgstat.xCentroid+"\t"+imgstat.yCentroid+"\t"+imgstat.mean+"\t"+sl+"\t"+fr+"\t"+prevthresh;
				tctpanel.printOutput(output[fr-1]);
				
				//x=(int)(imgstat.roiX/psize);
				//y=(int)(imgstat.roiY/psize);
				centroids[fr-1]=new Point((int)(imgstat.xCentroid/cal.pixelWidth),(int)(imgstat.yCentroid/cal.pixelHeight));
				areas[fr-1]=imgstat.area;
				//WindowManager.setWindow(timp.getWindow());
				timp.setPosition(1,labelsl,fr);
				Overlay tov=timp.getOverlay();
				if(tov==null) {tov=new Overlay(); tov.setFillColor(new Color(255,100,0)); timp.setOverlay(tov);}
				if(tsel[fr-1]!=null){ //erase old roi
					tov.remove(tsel[fr-1]);
					timp.deleteRoi();
					tsel[fr-1]=null;
				}
				tsel[fr-1]=(ij.gui.Roi) curWand[fr-1].clone();
				tsel[fr-1].setImage(timp);
				tsel[fr-1].setPosition(1, labelsl, fr);
				tsel[fr-1].setFillColor(new Color(255,100,0));
				timp.setPosition(1,labelsl,fr);
				tov.add(tsel[fr-1]);
				timp.updateAndRepaintWindow();
				postFirstAccept=true;
			}
			
			long startpresstime=System.nanoTime();
			long waittime=200;
			while(spacepress && !gocellcomplete) {
				IJ.wait(20);
				long presstime=System.nanoTime()-startpresstime;
				//if(!firsthit)waittime=100;
				//IJ.log("wait: "+presstime);
				if((presstime/1000000)>waittime){break;}
			}
			
			boolean cellcomplete=true;
			slsleft="";
			int lowsl=1;
			for(int i=frms;i>0;i--) {
				if(output[i-1]==null) {cellcomplete=false; slsleft=(i)+" "+slsleft; lowsl=i;}
				else slsleft=" ̸"+(i)+((slsleft.startsWith(" ")||slsleft.startsWith(" ̸"))?"":" ")+slsleft;
			}
			if(cellcomplete || gocellcomplete){
				gocellcomplete=false;
				//firsthit=true;
				GenericDialog gd = new GenericDialog("Cancel");
		        gd.addMessage("All done with cell #"+celln+"?");
		        gd.enableYesNoCancel("Yes", "No");
		        gd.showDialog();
		        spacepress=false; buttonpress[0]=false; buttonpress[1]=false; buttonpress[2]=false;
		        if (gd.wasOKed()){
		        	ImageStack ist=timp.getImageStack();
					timp.setColor(Color.white);
		            for(int i=0;i<frms;i++) {
	    				ImageProcessor tip=ist.getProcessor(timp.getStackIndex(1, labelsl, i+1));
		            	if(tsel[i]!=null) {
		            		//timp.setPosition(1,labelsl,i+1);
		    				//timp.setRoi(tsel[i]);
		    				tip.setColor(255);
		    				tip.fill(tsel[i]);
		    				tip.setColor(0);
		    				tip.draw(ij.plugin.RoiEnlarger.enlarge(tsel[i],1));
		    				tip.setColor(255);
		            	}
		            	if(output[i]!=null){
		            		results.append(output[i]);
			            	if(centroids[i]!=null){
								//timp.setPosition(2,labelsl,(i+1));
								//ImageProcessor tip=timp.getProcessor();
								String cl=""+celln;
								if(drawCellLabel)cl=cl.concat(" "+cellLabel);
								Roi textr=new TextRoi(centroids[i].x-10,centroids[i].y-20,cl);
								tip.setColor(255);
								//timp.setRoi(textr);
								tip.draw(textr);
			            	}
		            	}else{
							results.append(cellLabel+"\t0\t"+celln+"\tNA\tNA\tNA\tNA\tNA\tNA\tNA\t"+(i+1)+"\t"+prevthresh);
		            	}
		            }
		            timp.setProperty("Info", results.getText());
		            timp.setPosition(1,labelsl,fr);
		            Overlay tov=timp.getOverlay();
		            if(tov!=null)tov.clear();
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
	
	private void setup() {

		IJ.setForegroundColor(255,255,255);
		title=simp.getTitle();
		basetitle=title.endsWith(".tif")?title.substring(0,title.length()-4):title;
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
        
        if(timp==null) {
			timp=IJ.createImage(endtitle,"8-bit composite", simp.getWidth(),simp.getHeight(), 2, 1, simp.getNFrames());
			if(timp==null) IJ.log("Error - timp did not get created");
			timp.show();
			//timp.getProcessor().invertLut();
			//Rectangle tbounds=timp.getWindow().getBounds();
        	Rectangle sbounds=siw.getBounds();
        	//double sizefactor=sbounds.getWidth()/tbounds.getWidth();
			timp.getWindow().setLocationAndSize((int) (sbounds.getX()+sbounds.getWidth()+5),(int) sbounds.getY(),(int) sbounds.getWidth(),65535);
		}
		if(timp==simp){IJ.log("Same window exiting"); done=true; return;}
		if(timp==null){IJ.log("No target window"); done=true; return;}
		
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
        tctpanel.dispose();
		if(simp!=null){
			ImageCanvas ic=simp.getCanvas();
			if(ic!=null){
				ImageWindow siw=simp.getWindow();
	        	ic.removeKeyListener(this);
	        	ic.removeMouseListener(this);
	       		siw.removeMouseWheelListener(this);
	        	siw.addMouseWheelListener(siw);
	            ic.disablePopupMenu(false);
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
			if(rtw==null){
				rtw=new TextWindow(restitle,heading,"",800,400);
				if(timp!=null && timp.getInfoProperty()!=null) {
					String tresults=timp.getInfoProperty();
					if(tresults!=null && !"".contentEquals(tresults)) {
						rtw.append(tresults);
					}
				}
			}
			TextPanel panel=rtw.getTextPanel();
			//IJ.log(""+panel.getLineCount());
			if(!panel.getColumnHeadings().startsWith(heading)){
				rtw.close();
				rtw=new TextWindow(restitle,heading,"",800,400);
			}else if(panel.getLineCount()>0){
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
				if(cellLabels.size()>0)cellLabel=cellLabels.get(cellLabels.size()-1);
				celln++;roin++;
				IJ.log("Using open window, roin: "+roin+" celln: "+celln+" Label: "+cellLabel);
			}
		}
		
		public void append(String text) {rtw.append(text);}
		public String getText() {
			String[] temp=rtw.getTextPanel().getText().split("\n");
			String result="";
			if(temp.length>1)result=temp[1];
			for(int i=2; i<temp.length;i++)result+="\n"+temp[i];
			return result;
		}
	}
	
	class TCTPanel extends Frame{

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		TextArea ta=null;
		
		public TCTPanel() {
			super("TCT Control Panel version "+version);
			/*
			 addWindowListener(new WindowAdapter(){
				public void windowClosing(WindowEvent we){
					recorder.stop();
					dispose();
				}
			});
			 */
			this.setFocusable(false);
			setLayout(new GridBagLayout());
			GridBagConstraints c = new GridBagConstraints();
			String[] backstr=new String[] {"Back - None", "Back - RightClick", "Back - Space"};
			
			ActionListener l=new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					switch(e.getActionCommand()){
					case "LUT":
						changeLutType();
						break;
					case "goback":
						changeGoBack();
						((Button)e.getSource()).setLabel(backstr[goback]);
						break;
					case "askCellLabel":
						askCellLabel();
						break;
					case "gocellcomplete":
						gocellcomplete=true;
						break;
					case "changeWheelFactor":
						changeWheelFactor(1);
						((Button)e.getSource()).setLabel("Wheel - "+wheelfactor);
						break;
					case "updateCurWand":
						updateCurWand();
						break;
					case "done":
						done=true;
						break;
					default:
						break;
					}
					
				}
				
			};

			//c.fill=GridBagConstraints.HORIZONTAL;
			c.weightx=1; c.weighty=1;
			c.gridx=0;c.gridy=0;
			c.gridwidth=1; c.gridheight=1;
			Button b=new Button();
			b.setFocusable(false);
			b.setActionCommand("LUT");
			b.setLabel("LUT");
			b.addActionListener(l);
			if(simp.isComposite())b.setEnabled(false);
			add(b,c);
			c.gridx=1;
			b=new Button();
			b.setFocusable(false);
			b.setActionCommand("goback");
			b.setLabel(backstr[goback]);
			b.addActionListener(l);
			add(b,c);
			c.gridx=2;
			b=new Button();
			b.setFocusable(false);
			b.setActionCommand("askCellLabel");
			b.setLabel("New Cell Label");
			b.addActionListener(l);
			add(b,c);
			c.gridx=3;
			b=new Button();
			b.setFocusable(false);
			b.setActionCommand("gocellcomplete");
			b.setLabel("Cell complete");
			b.addActionListener(l);
			add(b,c);
			c.gridx=4;
			b=new Button();
			b.setFocusable(false);
			b.setActionCommand("changeWheelFactor");
			b.setLabel("Wheel - "+wheelfactor);
			b.addActionListener(l);
			add(b,c);
			c.gridx=5;
			b=new Button();
			b.setFocusable(false);
			b.setActionCommand("updateCurWand");
			b.setLabel("Update all current Rois");
			b.addActionListener(l);
			add(b,c);
			c.gridx=6;
			b=new Button();
			b.setFocusable(false);
			b.setActionCommand("done");
			b.setLabel("Stop TCT");
			b.addActionListener(l);
			add(b,c);
			
			c.gridx=0; c.gridy=2; c.gridwidth=7;
			ta=new TextArea("",15,90,TextArea.SCROLLBARS_NONE);
			ta.setEditable(false);
			ta.setFocusable(false);
			add(ta,c);
			
			pack();
		}
		
		/**
		 * Set (0-index) line of text area with text
		 * @param line
		 * @param text
		 */
		public void setTextLine(int line, String text) {
			String[] tatext=ta.getText().split("\n");
			String out="";
			final int max=Math.max(line,tatext.length);
			for(int i=0;i<=max;i++) {
				if(i==line)out+=text+"\n";
				else if(i<tatext.length)out+=tatext[i]+"\n";
				else out+="\n";
			}
			ta.setText(out);
			
		}
		
		public void printOutput(String output) {
			if(output==null)return;
			String[] tl=output.split("\t");
			if(tl.length<12)return;
			setTextLine(1, "Lbl: "+tl[0]+" Cell: "+tl[2]+" Fr: "+tl[10]+" Area: "+AJ_Utils.parseDoubleTP(tl[3],2)+" Mean: "+AJ_Utils.parseDoubleTP(tl[8],2)+
					" X: "+AJ_Utils.parseDoubleTP(tl[6],2)+" Y: "+AJ_Utils.parseDoubleTP(tl[7],2)+" thresh: "+tl[11]);
		}
		
	}

	private Roi doWandRoi(ImageProcessor sip, int xw, int yw){
		if(xw==-1 || yw==-1) return null;
		
		Wand w=new Wand(sip);
		w.autoOutline(xw,yw,prevthresh,(double)65535);
		Roi sel=new ShapeRoi(new PolygonRoi(w.xpoints, w.ypoints, w.npoints, Roi.POLYGON));

		Rectangle sbs=sel.getBounds();
		//String temp="sgw: "+simp.getWidth()+" selw: "+sbs.getWidth()+" selx: "+sbs.getX();
		if(sel!=null && sbs.getWidth()<(simp.getWidth()/2) && sbs.getHeight()<(simp.getHeight()/2) && sbs.getX()>0 && sbs.getY()>0 && sbs.getWidth()>5 && sbs.getHeight()>5){
			sel=smoothRoi(sel);
			//IJ.log("Enlarged "+temp);
		}//else IJ.log(temp);
		return sel;
	}
	
	private Roi smoothRoi(Roi roi) {
		roi=ij.plugin.RoiEnlarger.enlarge(roi,3);
		return ij.plugin.RoiEnlarger.enlarge(roi,-3);
	}
	
	private Roi doWandRoiByPt(int frame) {
		ImageStatistics imgstat;
		ImageProcessor ip=simp.getStack().getProcessor(simp.getStackIndex(simp.getC(), simp.getZ(), frame));
		Calibration cal=simp.getCalibration();
		double prevarea=simp.getStatistics(127).area;
		Roi sel=null;
		double psize=cal.pixelWidth;
		int cx=x,cy=y;
		Point pt=null;
		for(int i=((frame>1)?(frame-2):0);i>=0;i--) {
			if(centroids[i]!=null) {pt=centroids[i]; prevarea=areas[i]; break;}
		}
		if(pt==null){
			for(int i=frame;i<simp.getNFrames();i++) {
				if(centroids[i]!=null) {pt=centroids[i]; prevarea=areas[i]; break;}
			}
		}

        ucwFrs[frame-1]=false;
		if(pt==null)return doWandRoi(ip,cx,cy); 
		for(int attempts=0; attempts<6; attempts++){
			boolean isok=false;
			sel=doWandRoi(ip,cx,cy);
			if(sel!=null){
				ip.setRoi(sel);
				imgstat=ImageStatistics.getStatistics(ip, 127, cal);
				if(Math.hypot((pt.x-imgstat.xCentroid/psize),(pt.y-imgstat.yCentroid/psize))<(Math.sqrt(prevarea/Math.PI)/psize)) {
					isok=true;
					 ucwFrs[frame-1]=true;
				}
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
		final int fr=simp.getFrame();
		if(updateCurWandThread!=null)updateCurWandThread.stopThis();
		updateCurWandThread=new MyThread() {
			
			public void run() {
				for(int i=0; i<simp.getNFrames(); i++) {
					if(shouldStop)break;
					if(i==(fr-1))continue;
					if(tsel[i]!=null) {
						//curWand[i]=tsel[i];
					}else curWand[i]=doWandRoiByPt(i+1);
					//IJ.log("curWand"+i+" "+curWand[i]);
				}
			}
		};
		updateCurWandThread.run();
	}
	
	class MyThread extends Thread{
		boolean shouldStop=false;
		public void stopThis() {
			shouldStop=true;
		}
	}
	
	private void simpleUpdateCurWand() {
		if(x==-1 && y==-1)return;
		int cx=x,cy=y;
		int cfr=simp.getFrame();
		for(int i=0;i<simp.getNFrames();i++) {
			if(tsel[i]==null || (i==(cfr-1)) ) {
				if(xys[i]!=null) {cx=xys[i].x; cy=xys[i].y;}
				curWand[i]=doWandRoi(simp.getStack().getProcessor(simp.getStackIndex(simp.getC(), simp.getZ(), i+1)),cx,cy);
				if(i==(cfr-1)) {
					simp.setRoi(curWand[i]);
					timp.setRoi((Roi)curWand[i].clone());
				}
			}
		}
	}
	
	public void finish(){
		done=true;
	}
	
    public synchronized void mouseWheelMoved(MouseWheelEvent e) {
		int rotation = e.getWheelRotation();
		int amount = e.getScrollAmount();
		boolean ctrl = (e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK)!=0;
		
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
			tctpanel.setTextLine(3, "Thresh: "+simp.getProcessor().getMinThreshold());
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
        int flags = e.getModifiersEx();
        if(DEBUG) IJ.log("flags: "+flags+" Mask1: "+InputEvent.BUTTON1_DOWN_MASK+" Mask2: "+InputEvent.BUTTON2_DOWN_MASK+" Mask3: "+InputEvent.BUTTON3_DOWN_MASK);
        if(DEBUG) IJ.log("buttonPressed: "+(((flags&InputEvent.BUTTON1_DOWN_MASK)!=0)?"1":"")+(((flags&InputEvent.BUTTON2_DOWN_MASK)!=0)?"2":"")+(((flags&InputEvent.BUTTON3_DOWN_MASK)!=0)?"3":""));
        if((flags & InputEvent.BUTTON1_DOWN_MASK) !=0){
        	Point xy = simp.getCanvas().getCursorLoc(); //because x,y position isn't exactly x,y in image
        	x=xy.x; y=xy.y;
        	if(DEBUG) IJ.log("Position: "+xy.x+" "+xy.y);
        	buttonpress[0]=true;
        }
        if((flags & InputEvent.BUTTON2_DOWN_MASK) !=0)buttonpress[1]=true;
        if((flags & InputEvent.BUTTON3_DOWN_MASK) !=0)buttonpress[2]=true;
    }
    public void mouseReleased(MouseEvent e) {
    	if(IJ.altKeyDown() || IJ.shiftKeyDown()) return;
    	if(DEBUG) IJ.log("buttonReleased: "+e.getModifiersEx()+" button: "+e.getButton());
    	if(e.getButton()==MouseEvent.BUTTON1)buttonpress[0]=false;
    	if(e.getButton()==MouseEvent.BUTTON2) {
			IJ.run("Previous Slice [<]");
    		buttonpress[1]=false;
    	}
    	if(e.getButton()==MouseEvent.BUTTON3) {
    		buttonpress[2]=false;
    		if(goback==1){IJ.run("Previous Slice [<]");}
			else{acceptedROI=true;}
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
        	if(!buttonpress[0]){ //mouse button while spacebar pressed
       			if(goback==2){IJ.run("Previous Slice [<]");}
       			else{acceptedROI=true;}
        	}
       		spacepress=false;
        }
       	if(keyCode==KeyEvent.VK_F1){
       		changeLutType();
       	}
        if(keyCode==KeyEvent.VK_F2){
        	changeGoBack();
        }
        if(keyCode==KeyEvent.VK_F3){
        	askCellLabel();
        }
        if(keyCode==KeyEvent.VK_F4){gocellcomplete=true;}
        if(keyCode==KeyEvent.VK_UP || keyCode==KeyEvent.VK_DOWN){
        	changeWheelFactor((keyCode==KeyEvent.VK_UP)?1:-1);
        }
       	if(keyCode==27) {done=true;}
   	}
   	
    public void keyTyped(KeyEvent e) {}
    
    private void changeLutType() {
    	MYLUT++;
   		if(MYLUT>3)MYLUT=0;
   		if(MYLUT==2) {
   			ImageProcessor sip=simp.getProcessor();
   			sip.setMinAndMax(sip.getMin(),sip.getMax());
   		}
    }
    
    private void changeGoBack() {
    	goback++; if(goback>2)goback=0;
    	if(goback==1) tctpanel.setTextLine(0, "Rightclick now goes back a frame");
    	else if(goback==2)tctpanel.setTextLine(0, "Space key now goes back a frame");
    	else tctpanel.setTextLine(0, "No back button");
    }
    
    /**
     * upOrDown must be 1 or -1
     * @param upOrDown
     */
    private void changeWheelFactor(int upOrDown) {
    	int i=0;
    	for(;i<whfacs.length; i++) {
    		if(wheelfactor<=whfacs[i]) break;
    	}
    	int newi=i+upOrDown;
    	if(newi>whfacs.length)newi=0;
    	if(newi<0)newi=whfacs.length-1;
    	wheelfactor=whfacs[newi];
    }
    
}
