package com.absint.a3;

import hudson.FilePath;

import java.io.*; 
import java.net.URL; 
import java.util.ArrayList; 
import java.util.List; 

public class A3ToolInstaller {

	private FilePath packagepath;
	private FilePath workspace;
	
	private String arch;
	private String alauncher;
	
	
	public static enum OS { 
    	WINDOWS, UNIX
    }
	
	private OS os;

	
	/** Temp File **/
    public static String tmpFile;    
    static { 
        tmpFile = System.getProperty("java.io.tmpdir"); 
    } 
	
	public A3ToolInstaller (FilePath ws, String winpck, String unixpck, OS os) {
		
		this.workspace = ws;
		this.os = os;
		
		switch (os) {
		case WINDOWS: 
			this.packagepath = new FilePath(new File(winpck));
			this.alauncher = "alauncher.exe";
			break;
		case UNIX:
			this.alauncher = "alauncher";
			this.packagepath = new FilePath(new File(unixpck));
			break;
		}
	}	
		
	public void unpackInstallerPackage() {
		
		try {
			switch (this.os) {
				case WINDOWS:				
					packagepath.unzip(workspace);
					break;
				case UNIX:
					packagepath.untar(workspace, FilePath.TarCompression.GZIP);
					break;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public String getPathToAlauncher() {
		// TODO Auto-generated method stub
		return this.alauncher;
	}
	
	
}
