package ajs.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.lang.reflect.Method;
import java.util.*;

public class AJ_Utils implements PlugIn{
	
	@Override
	public void run(String arg) {
		List<String> classNames = new ArrayList<String>();
		ZipInputStream zip;
		try {
			File pdir=new File(IJ.getDirectory("plugins"));
			File[] ajsjar=pdir.listFiles(new FilenameFilter() {

				@Override
				public boolean accept(File arg0, String arg1) {
					return arg1.startsWith("AJS_Tools");
				}
				
			});
			zip = new ZipInputStream(new FileInputStream(ajsjar[0]));
			for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
			    if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
			        // This ZipEntry represents a class. Now, what class does it represent?
			        String className = entry.getName().replace('/', '.'); // including ".class"
			        if(!className.contains("$") && !className.contains("AJ_Utils"))classNames.add(className.substring(0, className.length() - ".class".length()));
			    }
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		List<String> pluginNames= new ArrayList<String>();
		for(String cls : classNames) {
			Class<?> temp=null;
			Method method=null;
			try {
				temp = Class.forName(cls);
			}catch(Exception e) {
				e.printStackTrace();
			}
			try {
				method=temp.getMethod("run", new Class[] {String.class});
			}catch(Exception e) {
				
			}
			if(method!=null) pluginNames.add(cls.replace("ajs.tools.", ""));
		}
		if(pluginNames.size()>0) {
			GenericDialog gd = new GenericDialog("Run AJS_Tools Plugin");
			gd.addMessage("Run one of the following with argument:");
			gd.addStringField("Argument:", "");
			gd.addChoice("Plugin:", (String[]) pluginNames.toArray(new String[pluginNames.size()]), pluginNames.get(0));
			gd.showDialog();
			if(gd.wasCanceled())return;
			IJ.runPlugIn("ajs.tools."+gd.getNextChoice(), gd.getNextString());
		}
	}
	
	public static int parseIntTP(String test){
		return (int)parseDoubleTP(test);
	}
	
	public static double parseDoubleTP(String test){
		return parseDoubleTP(test, 4);
	}

	public static double parseDoubleTP(String test, int digits){
		double result=-1.0;
		try{
			result = Double.parseDouble(test);
			result=(double)Math.round(result*Math.pow(10d, digits))/Math.pow(10d, digits);
		}catch(Exception e){}
		return result;
	}
	
	/**
	 * Returns milis from epoch (Jan 1 1970)
	 * 
	 * @param datetime Should be "YYYY-MM-DD HH:MM:SS[:MSEC] or "YYYY:MM:DD HH:MM:SS[:MSEC]"
	 * @return
	 */
	public static long sysTime(String datetime) {
		long res=0;
		GregorianCalendar cal=new GregorianCalendar();
		cal.setTimeInMillis(0);
		String[] dta=datetime.split(" ");
		if(dta.length>1) {
			String splc=(dta[0].indexOf("-")>0)?"-":":";
			String[] sdate=dta[0].split(splc);
			if(sdate.length==3) {
				cal.set(parseIntTP(sdate[0]),parseIntTP(sdate[1])-1,parseIntTP(sdate[2]));
				res+=cal.getTimeInMillis();
			}
			dta[0]=dta[1];
		}
		res+=timeStringToMsec(dta[0]);
		return res;
	}
	
	public static String textTime() {
		return textTime(System.currentTimeMillis());
	}
	
	public static String textTime(long tms) {
		return textTime(tms,"la-y:mo:d:h:m:s:ms");
	}
	
	public static String textTime(long tms, String format) {
		final ArrayList<String> formats=new ArrayList<String>(Arrays.asList(new String[] {"y","mo","d","h","m","s","ms"}));

		GregorianCalendar cal=new GregorianCalendar();
		cal.setTimeInMillis(tms);
		
		final long MINUTES_PER_HOUR = 60;
		final long SECONDS_PER_MINUTE = 60;
		final long MILLISECONDS_PER_SECOND = 1000;
		int msec, secs, minutes, hours, years=0,months=0,days=0;
		
		//[la-]y:mo:d:h:m:s:ms
		boolean lblall=false;
		if(format.startsWith("la-")) {lblall=true; format=format.substring(3,format.length());}
		String sign="";
		if(tms<0) {sign="-"; tms=-tms;}
		
		String[] formata=format.split(":");
		
		boolean[] tbs=new boolean[7];
		for(int i=0;i<7;i++)tbs[i]=false;
		for(int i=0;i<formata.length;i++)tbs[formats.indexOf(formata[i])]=true;

		//int millis = (int)( togo % MILLISECONDS_PER_SECOND );
		msec= (int)(tms%1000);
		tms /= MILLISECONDS_PER_SECOND;
		secs = (int)( tms % SECONDS_PER_MINUTE );
		tms /= SECONDS_PER_MINUTE;
		minutes = (int)( tms % MINUTES_PER_HOUR );
		tms /= MINUTES_PER_HOUR;
		hours=(int)tms;
		if(tbs[0]||tbs[1]||tbs[2]) {
			secs=cal.get(Calendar.SECOND);
			minutes=cal.get(Calendar.MINUTE);
			hours=cal.get(Calendar.HOUR_OF_DAY);
			days=cal.get(Calendar.DAY_OF_MONTH);
			months=cal.get(Calendar.MONTH)+1;
			years=cal.get(Calendar.YEAR);
		}

		String res="";
		if(tbs[0]){
			res+=Integer.toString(years);
			if(lblall)res+="y";
		}
		if(tbs[1]) {
			if(res.length()>0)res+=":";
			if(tbs[0])res+=String.format("%02d", months);
			else res+=Integer.toString(months);
			if(lblall)res+="mo";
		}
		if(tbs[2]) {
			if(res.length()>0)res+=":";
			if(tbs[0]||tbs[1])res+=String.format("%02d", days);
			else res+=Integer.toString(days);
			if(lblall)res+="d";
		}
		if(tbs[3]) {
			if(res.length()>0)res+=":";
			if(tbs[0]||tbs[1]||tbs[2])res+=String.format("%02d", hours);
			else res+=Integer.toString(hours);
			if(lblall)res+="h";
		}
		if(tbs[4]) {
			if(res.length()>0)res+=":";
			res+=String.format("%02d", minutes);
			if(lblall)res+="m";
		}
		if(tbs[5]) {
			if(res.length()>0)res+=":";
			res+=String.format("%02d", secs);
			if(lblall)res+="s";
		}
		if(tbs[6]) {
			if(res.length()>0)res+=":";
			res+=String.format("%03d", msec);
			if(lblall)res+="ms";
		}
		return (sign+res);
	}
	
	public static long timeStringToMsec(String timestring) {
		//ss, mm:ss, hh:mm:ss, or hh:mm:ss:msec
		String[] time=timestring.split(":");
		long result=parseIntWithin(time[Math.min(time.length-1,2)])*1000; //seconds
		if(time.length>1)result+=parseIntWithin(time[Math.min(time.length-2,1)])*60*1000; //minutes
		if(time.length>2)result+=parseIntWithin(time[0])*60*60*1000; //hours
		if(time.length>3)result+=parseIntWithin(time[time.length-1]); //msecs
		return result;
	}
	
	public static int parseIntWithin(String numstr) {
		int result;
		if(!testParseInt(numstr)) {
			int end=numstr.length(),st=end;
			boolean start=true;
			for(int i=0;i<numstr.length();i++) {
				boolean isn=-1==AJ_Utils.parseIntTP(numstr.substring(i, i+1));
				if(start&& !isn) {st=i;start=false;}
				if(!start && isn) {end=i;break;}
			}
			if(st<end)result=Integer.parseInt(numstr.substring(st,end));
			else result=0;
		}else result=Integer.parseInt(numstr);
		return result;
	}
	
	public static boolean testParseInt(String numstr) {
		boolean result=false;
		try {
			Integer.parseInt(numstr);
			result=true;
		}catch(Exception e) {
			
		}
		return result;
	}
	
	public static int[] parseRange(String range) {
		int[] res=new int[2];
		int hyph=range.indexOf("-");
		if(hyph==-1){res[1]=parseIntWithin(range); res[0]=res[1]-1;}
		else {
			res[0]=parseIntWithin(range.substring(0,hyph))-1;
			res[1]=parseIntWithin(range.substring(hyph+1,range.length()));
		}
		return res;
	}
	
	public static String deParseRange(int st, int end) {
		st++;
		if(st==end)return ""+st;
		if(st>end) {int t=st; st=end; end=t;}
		return ""+st+"-"+end;
	}
	
	public static String deParseRange(int[] range) {
		return deParseRange(range[0], range[1]);
	}
	
	public static boolean doesClassExist(String classname) {
		try {
			Class.forName(classname);
		}catch(Exception e) {
			return false;
		}
		return true;
	}
	
	public static double getMaxOfBits(int bits) {
		if(bits==16)return 65535.0;
		if(bits==32)return Integer.MAX_VALUE;
		return 255.0;
	}
	
}