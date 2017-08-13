package com.absint.a3;

import hudson.FilePath;

import java.io.*; 

public class A3ToolInstaller {

	private FilePath packagepath;
	private FilePath workspace;
	private String target;
	private FilePath toolpath;
	
	public static enum OS { 
    	WINDOWS, UNIX
    }
	
	private OS nodeOS;



	public A3ToolInstaller (FilePath ws, String packagepath, String target, OS nodeOS) {
		
		this.workspace = ws;
		this.nodeOS = nodeOS;
		this.packagepath = new FilePath(new File(packagepath));
		this.target = target;
		
		this.toolpath = null; // TBD
		
	}	
		
	public A3ToolInstaller(FilePath ws, String launcherpath, OS nodeOS) {
		this.workspace = ws;
		this.nodeOS = nodeOS;
		String alauncherbin = "alauncher" + (nodeOS == OS.WINDOWS ? ".exe" : "");	
		
		toolpath = new FilePath(this.workspace.getChannel(), launcherpath + (nodeOS == OS.UNIX ? "/" : "\\") + alauncherbin);
	}

	public void unpackInstallerPackage() {
		
		try {
			switch (this.nodeOS) {
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
	
	public FilePath getToolFilePath() {
		// TODO Auto-generated method stub
		return this.toolpath;
	}
	
	
}
