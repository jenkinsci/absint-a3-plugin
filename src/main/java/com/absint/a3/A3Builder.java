/*
 * The MIT License
 *
 * Copyright (c) 2022, AbsInt Angewandte Informatik GmbH
 * Author: Christian Huembert
 * Email: huembert@absint.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.absint.a3;
import hudson.Proc;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Extension;
import hudson.FilePath;
import hudson.util.FormValidation;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

import com.absint.a3.A3ToolInstaller.OS;

import org.kohsuke.stapler.QueryParameter;
import jakarta.servlet.ServletException;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author AbsInt Angewandte Informatik GmbH
 */
public class A3Builder extends Builder implements SimpleBuildStep {
    private static final String PLUGIN_NAME = "AbsInt a³ Jenkins PlugIn";
    private static final String BUILD_NR    = "1.2.0";

    //private String project_file, analysis_ids, pedantic_level, a3toolmode, export_a3apxworkspace;
    private String project_file, analysis_ids, pedantic_level, export_a3apxworkspace, concurrency;
    private boolean copy_report_file, copy_result_file, skip_a3_analysis;
    
    private String toolpath;
    private String project_file_expanded;
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public A3Builder(String project_file, String analysis_ids, String pedantic_level, String export_a3apxworkspace, String concurrency, boolean copy_report_file, boolean copy_result_file, boolean skip_a3_analysis)
    {
        this.project_file   		= project_file;
        this.analysis_ids    		= analysis_ids;
        this.pedantic_level 		= pedantic_level;
  //      this.a3toolmode            	= a3toolmode;
        this.export_a3apxworkspace 	= export_a3apxworkspace;
        this.concurrency 			= concurrency;
        this.copy_report_file = copy_report_file;
        this.copy_result_file = copy_result_file;
        this.skip_a3_analysis = skip_a3_analysis;
    }

    /*
     * Interface to <tt>config.jelly</tt>.
     */

    /**
     * Returns the currently set path to the configuration file used for the analysis run.
     *
     * @return java.lang.String
     */
    public String getProject_file() {
        return project_file;
    }

    /**
     * Returns the currently set analysis ID  used for the analysis run.
     *
     * @return java.lang.String
     */
    public String getAnalysis_ids() {
        return analysis_ids;
    }

    /**
     * Returns the currently set pedantic level used for the analysis run.
     *
     * @return java.lang.String
     */
    public String getPedantic_level() {
        return pedantic_level;
    }
    
    /**
     * Returns the currently set pedantic level used for the analysis run.
     *
     * return java.lang.String
     *
     * public String getA3toolmode() {
     *     return a3toolmode;
     *  }
     */

    /**
     * Returns the currently set pedantic level used for the analysis run.
     *
     * @return java.lang.String
     */
    public String getExport_a3apxworkspace() {
        return export_a3apxworkspace;
    }

    /**
     * Returns the currently set pedantic level used for the analysis run.
     *
     * @return java.lang.String
     */
    public String getConcurrency() {
		return concurrency;
    }


    /**
     * Checks if "Copy Report File to Jenkins Workspace" option is set
     *
     * @return boolean
     */
    public boolean isCopy_report_file() {
        return copy_report_file;
    }

    /**
     * Checks if "Copy XML Result File to Jenkins Workspace" is set
     *
     * @return boolean
     */
    public boolean isCopy_result_file() {
        return copy_result_file;
    }

    /**
     * Checks if "Skip a3 analysis run" option is set
     *
     * @return boolean
     */
    public boolean isSkip_a3_analysis() {
        return skip_a3_analysis;
    }

    
    /*
     *  end interface to <tt>config.jelly</tt>.
     */
    
    
    /**
     * Small helper routine
     * @param listener TaskListener for Output in Jenkins Console
     * @param trace    Exception Trace to output for debugging
     */
    public static void printStackTracetoLogger(TaskListener listener, StackTraceElement[] trace) {
    	 for(int i=0; i<trace.length;i++) {
    		 listener.getLogger().println("[Exception Backtrace: ] " + trace[i].toString());
    	 }
    }


    /**
     *
     * @param reportFile Report File Name
     * @param resultFile XML Result File Name
     * @param apxWorkspacePath a3 Workspace Path Name
     * @return String CommandLine String
     */
    public String builda3CmdLine(String reportFile, String resultFile, String apxWorkspacePath) {
    	//File alauncherObj = new File(a3installer.getPathToAlauncher());
    	String batch_param = "-b";
    	String pedanticLevel = (!this.pedantic_level.equals("apx") ? "--pedantic-level " + this.pedantic_level : "");
    	String apxWorkspacePath_param = (!apxWorkspacePath.equals("") ? "--export-workspace " + apxWorkspacePath : "");
    	String concurrency_param = (!this.concurrency.equals("default") ? "-j " + this.concurrency: "");
    	StringBuffer cmd_buf = new StringBuffer(this.toolpath + " " + this.project_file_expanded + " " + batch_param + " " + reportFile + " " + resultFile + " " + pedanticLevel + " " + apxWorkspacePath_param + " " + concurrency_param + " ");
    	
    	// The Formvalidator guarantees a correct naming of the IDs
    	String[] analyses = analysis_ids.split(",");
    	for (String id: analyses) {
    		if(!id.trim().equals("")) cmd_buf.append("-i " + id + " ");
    	}
    	return cmd_buf.toString();
    }



	/**
     * Builds the command line for invocation of a3 interactively and limited to just the problematic Items
     * @param failedItems - Vector<String> of failed items from XML Result File
     * @return String CommandLine String
     */
    private String builda3CmdLineInteractive(Vector<String> failedItems) {
    	//File alauncherObj = new File(a3installer.getPathToAlauncher());
    	StringBuffer cmd_buf = new StringBuffer(this.toolpath + " " + this.project_file_expanded);
    	if (failedItems.size() > 0) {
    		String batch_param = "-B";
        	String pedanticHigh = "--pedantic-level warning";
        	cmd_buf.append(" " + pedanticHigh + " " + batch_param + " ");
        	Iterator<String> iter = failedItems.iterator();
        	while(iter.hasNext())
        		cmd_buf.append("-i " + iter.next() + " ");    		
    	}    	
		return cmd_buf.toString();
	}

    
	/**
     * Builds the command line for invocation of a3 interactively opening an a3 workspace
     * @param apzWorkspacePath_str - Workspace Path String 
     * @return String CommandLine String
     */
    private String builda3CmdLineWorkspace(String apzWorkspacePath_str) {
    	String cmd = this.toolpath + " " + apzWorkspacePath_str;
		return cmd;
	}
    
    
    
    /**
     * Expands environment variables of the form 
     *       ${VAR_NAME}
     * by their current value.
     *
     * @param cmdln	the java.lang.String, usually a command line, 
     *                    in which to expand variables
     * @param envMap	a java.util.Map containing the environment variables 
     *                    and their current values
     * @return the input String with environment variables expanded to their current value
     */
     private static final String expandEnvironmentVarsHelper(
                                    String cmdln, Map<String,String> envMap, A3ToolInstaller.OS nodeOS ) {
    	if (cmdln == null) return ""; // null safe
        final String pattern = "\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}";
        final Pattern expr = Pattern.compile(pattern);
        Matcher matcher = expr.matcher(cmdln);
        String  envValue;
        Pattern subexpr;
        while (matcher.find()) {
           envValue = envMap.get(matcher.group(1).toUpperCase());
           if (envValue == null) {
              envValue = "";
           } else {
             envValue = envValue.replace("\\", "\\\\");
           }
           subexpr = Pattern.compile(Pattern.quote(matcher.group(0)));
           cmdln = subexpr.matcher(cmdln).replaceAll(envValue);
        } 
        
        if(nodeOS == A3ToolInstaller.OS.UNIX || nodeOS == A3ToolInstaller.OS.MACOS) {
            return cmdln.replace('\\','/');
        } else {
            return cmdln.replace('/','\\');
        }
     }
    
     /**
      * Quotes a Path String depending on the OS  
      *
      * @param s	the java.lang.String, path name typically
      * @param nodeOS	UNIX or WINDOWS
      * @return the quoted String in case nodeOS != UNIX
      */
     private String quoteIt(String s, A3ToolInstaller.OS nodeOS) {
         String ret;
         switch (nodeOS) {
            case WINDOWS:    
                ret = "\"" + s + "\"";
                break;
            case UNIX:
            case MACOS:
            default:
                ret = s;
                break;            
         }
    	 return ret;
     }
     
    
    @Override
    public void perform(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) {
    	// Analysis run started. ID plugin in Jenkins output.
        listener.getLogger().println("\nThis is " + PLUGIN_NAME + " in version " + BUILD_NR);
        // Perform some preliminary checks
        if(this.skip_a3_analysis) {
        	listener.getLogger().println("[A3 Builder Note:] a³ analysis run has been (temporarily) deactivated. Skipping analysis run.\n");
        	return; // nothing to do, exit method.
        }
         
        try {
        	
        	/*  ********************************
        	 *  Bind some useful local variables
        	 *  ********************************
        	 */
            
            // The actual environment is in local variable "env"
            Map<String,String> env = build.getEnvironment(listener);

            /* The plugin currently can distinguish node architectures Unix or not Unix. 
               This is insufficient for macOS nodes. 
               => Important Convention: macOS Nodes must have a NODE_LABEL defined containing the string "macos" !
            */

            A3ToolInstaller.OS nodeOS = null;
            if (!launcher.isUnix())
                   nodeOS = OS.WINDOWS;
            else if (env.get("NODE_LABELS").toLowerCase().contains("macos"))
                   nodeOS = OS.MACOS;
            else 
                   nodeOS = OS.UNIX;
  	
        	String almserver = getDescriptor().getAlmserver();
        	String almport   = getDescriptor().getAlmport();
        	
        	String a3packages = expandEnvironmentVarsHelper(getDescriptor().getA3packages(), env, nodeOS);
        	String alauncher  = expandEnvironmentVarsHelper(getDescriptor().getAlauncher(),  env, nodeOS);
       	

        	/*  **************************************************************************
        	 *  Extend Environment Map with ALM Server and Port Configuration if specified
        	 *  ************************************************************************** */
        	
            // Extend environment by setting AI_LICENSE environement variable if ALM Server Host was specified in global configuration
         	if (almserver != null && !almserver.isEmpty()){
         		String alm_server_address = almserver + "@" + almport;
         		
     			String old_ai_license = env.put("AI_LICENSE", alm_server_address);
     			if (old_ai_license != null) {
     				listener.getLogger().println("[A3 Builder Info:] Overwriting AI_LICENSE environment variable (content: " + old_ai_license + ") with value: " + alm_server_address );
     			}
             }	 
       	
        	
        	/*  **********************************
        	 *   APX Project File Handling
        	 *  **********************************
        	 */
        	project_file_expanded = expandEnvironmentVarsHelper(project_file, env, nodeOS);   				// String expanded_project_file !
        	FilePath fpproject_file = new FilePath(workspace.getChannel(), project_file_expanded);
        	project_file_expanded = quoteIt(fpproject_file.toString(), nodeOS);
            
            // Let's parse the a3 project file
	        listener.getLogger().println("[A3 Builder Note:] a³ Project File     : " + fpproject_file);
	        APXFileHandler apx;
			try {
				apx = new APXFileHandler(fpproject_file, listener);
			} catch (IOException e) {
	        	listener.getLogger().println("[A3 Builder Error:] IOException while accessing a³ .apx Project File. Check your project configuration 'Configure -> a³ Analysis Run -> Basic Settings -> Project File (APX).\nAborting Build.\n");
	        	build.setResult(hudson.model.Result.FAILURE);
	         	return;
			}
	        
			// Extract Target Architecture from APX file
			String target = apx.getTarget();
            listener.getLogger().println("[A3 Builder Note:] Extracted a³ project target: " + target);
			
			/*  *************************************
        	 *  Now determine the tool execution mode
  			 *   (1) use pre installed alauncher
			 *   (2) installer packages unpacked to ws and execution from there 
        	 *  *************************************
        	 */

			A3ToolInstaller a3installer = null;
			if (a3packages != null && !a3packages.isEmpty()) {
				/* We are in unpacking mode (2) */
				a3installer = new A3ToolInstaller(workspace, a3packages, target, nodeOS, listener);
				
				if (a3installer.getToolFilePath() == null) {
					/* Something went wrong during installer unpacking, use fall back mode (1)	 */
					a3installer = new A3ToolInstaller(workspace, alauncher, nodeOS, listener);
				}				
			} else {
				/* We are in standard mode (1) */
				a3installer = new A3ToolInstaller(workspace, alauncher, nodeOS, listener);
			}			
			
			//finally set the right toolpath
			FilePath fptoolpath = a3installer.getToolFilePath();
			
//			if (!fptoolpath.exists()) {
//				listener.getLogger().println("[A3 Builder Error:] " + fptoolpath + " does not exist!\n         Check a³ Configuration in Jenkins Configuration.");
//	         	build.setResult(hudson.model.Result.FAILURE);
//	         	return;
//			}
			
			this.toolpath = quoteIt(fptoolpath.toString(), nodeOS); // surround the tool path by "..." for the case there are empty spaces in the path
					
	        // Generate an absint_a3 subdirectory in the Jenkins workspace
	        FilePath absint_a3_dir = new FilePath(workspace, "absint-a3-b" + build.getNumber());
	        try {
	        	absint_a3_dir.mkdirs();
			} catch (IOException | InterruptedException e1) {
				// Subdirectory a3 workspace could not be created, use workspace
				listener.getLogger().println("[A3 Builder Warning:] a3 workspace directory could not be created in Jenkins workspace. Output will be written to Jenkins workspace instead.");
				absint_a3_dir = workspace;
			}  			
	        
			/*  ***************************************************
        	 *   Perform compatibility Check: Jenkins Plugin and a3
        	 *  ***************************************************
        	 */

	        long extractedBuild = 0; 	        
	        if (fptoolpath.getName().startsWith("alauncher")) {
	            // The more difficult case to determine currently used a3 build number
	            FilePath a3versionFileInfo = new FilePath(absint_a3_dir, "a3-"+target+"-version-b"+build.getNumber()+".info");
	            listener.getLogger().println("[A3 Builder Note:] Perform a³ Compatibility Check ... ");
	          
	            String checkcmd = toolpath + " -b " + target + " --version-file \"" + a3versionFileInfo + "\"";

	            // Prepare start of the analysis process for version checking
	            ProcStarter procstarter = launcher.new ProcStarter();
		            procstarter.cmdAsSingleString(checkcmd);
		            procstarter.envs(env);
		            procstarter.stdout(listener.getLogger());
		            procstarter.pwd(workspace);
		            
		        Proc check = launcher.launch(procstarter);
		        check.join();          // wait for alauncher to finish
		        
		        extractedBuild = extractBuildNrFromVersionFile(a3versionFileInfo); 
	        } else {
	        	// the easy way, take it from the installer package file name :)
	        	listener.getLogger().println("[A3 Builder Note:] Extract a³ version info from installer package name");
	        	extractedBuild = a3installer.getBuildNr();        	
	        }		        
		             
	        // The actual compatibility check:
	        if (extractedBuild < extractBuildNumber(XMLResultFileHandler.required_a3build)) {
	        	listener.getLogger().println("[A3 Builder Error:] This version of the " + PLUGIN_NAME + " requires an a³ for " + target + " " + XMLResultFileHandler.required_a3version + " " + XMLResultFileHandler.required_a3build + " or newer!\n" 
	        								 + "Your version of a³ for " + target + " is: " + extractedBuild + "\n"
	        								 + "Please contact support@absint.com to request an updated a³ for " + target + " version.");
	        	listener.getLogger().println("\na³ Compatibility check failed.");
	         	build.setResult(hudson.model.Result.FAILURE);
	         	return;        	 
	        } else {
	        	listener.getLogger().println("[A3 Builder Note:] Compatibility Check OK, using an a³ for " + target + " Build: " + extractedBuild);
	        }

	        
			/*  ***************************************************
        	 *   Determine Report/Result/a3Workspace File Locations
        	 *  ***************************************************
        	 */
	        
			// Get the report/XML result file locations
			FilePath reportfile, resultfile;
			String reportfileParam, resultfileParam, reportfile_str, resultfile_str ;
			reportfile  = apx.getReportFile();
			resultfile  = apx.getResultFile();
	
			//Generate temporary report/result file only if no report/result file entry in apx found
			if (reportfile == null) {
				reportfile = new FilePath(absint_a3_dir, "a3-report-b" + build.getNumber()+".txt");
				reportfile_str = quoteIt(reportfile.toString(), nodeOS); 
				reportfileParam = "--report-file " + reportfile_str;
			} else {
				reportfileParam = "";
			}
			if (resultfile == null) {
				resultfile = new FilePath(absint_a3_dir, "a3-xml-result-b" + build.getNumber()+".xml");
				resultfile_str = quoteIt(resultfile.toString(), nodeOS); 
				resultfileParam = "--xml-result-file " + resultfile_str;
			} else {
				resultfileParam = "";
			}
			
			listener.getLogger().println("                   Textual Report File : " + reportfile);
			listener.getLogger().println("                   XML Result File     : " + resultfile);
	
			
			/* Determine the directory where to store a3 apx workspace, if any shall be exported */
			String apzWorkspacePath_str = (apx.getAPXFile().getBaseName()) + "-workspace-jb" + build.getNumber() + ".apz";
			switch(this.export_a3apxworkspace) {
				case ("apx_dir"): 
					apzWorkspacePath_str = quoteIt(new FilePath(apx.getAPXFile().getParent(), apzWorkspacePath_str).toString(), nodeOS);
					break;
				case ("jenkins_workspace"):
					apzWorkspacePath_str = quoteIt(new FilePath(absint_a3_dir, apzWorkspacePath_str).toString(), nodeOS);
					break;
				default: // disabled case
					apzWorkspacePath_str = ""; 
			}
			
			if (!this.export_a3apxworkspace.equals("disabled")) {
				listener.getLogger().println("                   a³ Workspace File   : " + apzWorkspacePath_str);
			}

			/* Prepare the <Analysis ID, HTML Report File> Map */
			HashMap<String, FilePath> id2htmlreportMap = new HashMap<String, FilePath>();
			apx.fillIDtoHTMLReportMap(id2htmlreportMap);
						
			/*  ***************************************************
        	 *  The actual analysis run happens here
        	 *  ***************************************************
        	 */
			
			int exitCode = -1;
			String cmd = builda3CmdLine(reportfileParam, resultfileParam, apzWorkspacePath_str);
            //listener.getLogger().println("[A3 Builder Note:] DEBUG cmd line: " + cmd);
            
            // Prepare start of the analysis process
            ProcStarter procstarter = launcher.new ProcStarter();
	            procstarter.cmdAsSingleString(cmd);
	            procstarter.envs(env);
	            procstarter.stdout(listener.getLogger());
	            procstarter.pwd(workspace);
			
	        FilePath timebase = absint_a3_dir.createTempFile("time", null);

	        Proc proc = launcher.launch(procstarter);
			exitCode = proc.join();          // wait for a3 to finish

			/*  ************************************************************************************
        	 *  Postprocessing:
        	 *  -Copy Report, Result and all available local HTML Report Files to Jenkins a3workspace
        	 *  -Prettyprint analysis results (from XML Result file)
        	 *  -Evaluate Tool Exit Code
        	 *  
        	 *  ************************************************************************************
        	 */

            if (this.copy_report_file){
            	listener.getLogger().println("[A3 Builder Note:] Copy a³ report file to Jenkins a3workspace ...");
            	copyReportFileToWorkspace(reportfile, absint_a3_dir, build.getNumber(), listener);
            }
            
            if (this.copy_result_file){
                listener.getLogger().println("[A3 Builder Note:] Copy a³ XML result file to Jenkins a3workspace ...");
                copyXMLResultFileToWorkspace(resultfile, absint_a3_dir, build.getNumber(), listener);
            }
             
            // Copy all the HTML Report Files
            if (!id2htmlreportMap.isEmpty()) {
                listener.getLogger().println("[A3 Builder Note:] Copy a³ HTML report file(s) to Jenkins a3workspace ...");
               	
                for (Map.Entry<String, FilePath> entry : id2htmlreportMap.entrySet()) {
                	String id = entry.getKey();
              		FilePath html_report_file = entry.getValue();
               		copyHTMLReportFileToWorkspace(html_report_file, absint_a3_dir, id, build.getNumber(), listener);
               	}
            }
            
            /* Pretty Print XML Result File */
            XMLResultFileHandler xml = new XMLResultFileHandler(resultfile, build.getNumber(), listener);
         
            boolean xmlfailed = false;
            Vector<String> failedItems = new Vector<String>();
            
            // Check if the XML Result File has been written by the analysis at all
            if (xml.getXMLResultFile().lastModified() >= timebase.lastModified()) { //time_before_launch) {
            	// If yes: evaluate its results
            	xmlfailed = xml.prettyPrintResultsAndCollectFailedItems(failedItems, id2htmlreportMap);
            } else {
            	listener.getLogger().println("[A3 Builder Info:] The XML Result File has not been updated by the a³ analysis run. ");
            	// If not updated, the analysis did not run and the success code MUST NOT be 0 (=success)!
            	if (exitCode == 0) {
            		listener.getLogger().println("                    Check the project maually:\n");
              		cmd = builda3CmdLineInteractive(failedItems);
               		listener.getLogger().println(cmd + "\n");
            	}
            }            
            
            // delete the timebase temp file again
            timebase.delete();
            
        	// Check Exit Code and determine if Build was failed or successful
            if(exitCode == 0 && !xmlfailed) {
            	listener.getLogger().println("\nAnalysis run succeeded.");
           	} else {
                listener.getLogger().println("\nAnalysis run failed.");
            	build.setResult(hudson.model.Result.FAILURE);

           		if (xmlfailed) {
        			listener.getLogger().println("The following analysis items failed:");
        			java.util.Iterator<String> iter = failedItems.iterator();

        		    while(iter.hasNext())
        		    	listener.getLogger().println(" - " + iter.next());

        		    listener.getLogger().println("\n[A3 Builder Info:] You might want to rerun the analyes of failed items in interactive mode. Use the command:");
           		} else {
           			listener.getLogger().println("\n[A3 Builder Warning:] The a³ returned a failure code but there was no failed analysis found.\n"+
           										 "                      Probably something in your .apx project configuration is wrong. To check, use a³ interactively:");
           			// Then don't use the exported workspace to investigate.
           			this.export_a3apxworkspace="disabled";
           		}
          		
           		if (!this.export_a3apxworkspace.equals("disabled")) {
           			// Then we have a workspace file
           			cmd = builda3CmdLineWorkspace(apzWorkspacePath_str);           			
           		} else {
           			cmd = builda3CmdLineInteractive(failedItems);
           		}
           		listener.getLogger().println(cmd + "\n");
            }

            // Remove a3 workspace sub directory again if it is empty
            if (absint_a3_dir.list().isEmpty() && !absint_a3_dir.equals(workspace)) {
            	absint_a3_dir.delete();
            }

         } catch (IOException e) {
        	listener.getLogger().println("IOException caught during analysis run.");
        	// e.printStackTrace();
        	printStackTracetoLogger(listener, e.getStackTrace());
        	build.setResult(hudson.model.Result.FAILURE);
         } catch (InterruptedException e) {
            // e.printStackTrace();
            listener.getLogger().println("InterruptedException caught during analysis run.");
            printStackTracetoLogger(listener, e.getStackTrace());
        	build.setResult(hudson.model.Result.FAILURE);
         }


    }

	/* Small Helper: Checks if line contains Build number */
    private boolean lineContainsBuildNumber(String n) {
   		String buildstrs[] = n.split(" ");
        for (String elem:buildstrs){
        	if (elem.toLowerCase().startsWith("build")) return true;
        }
        return false;
	}
    
    /* Small Helper: Extracts Build Number in long from a string line ending with the build number */
    private long extractBuildNumber(String n) {
    	try {
    		String buildstrs[] = n.split(" ");
        	String buildstr = buildstrs[buildstrs.length-1];  // The build number itself is always the last number in the row
        	return Integer.parseInt(buildstr);    	
    	} catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
    		return -1;
    	}
	}
    
    /* Small Helper:
     * Parses the version-file output from alauncher, extracts the current a3 build number and returns it.
     * Returns the extracted build number, 0 otherwise
     */
	private long extractBuildNrFromVersionFile(FilePath a3versionFileInfo) {
		try {
            BufferedReader br = new BufferedReader(
                    				new InputStreamReader(
                    					a3versionFileInfo.read(), "UTF-8" ));
            
            for (String line = br.readLine(); line != null; line = br.readLine()) {
            	if (lineContainsBuildNumber(line)) {  // line with build looks like this: "This is a3 build 123456" (older alauncher versions)  or  "Build: 123456" (newer alauncher versions)
            		br.close();
            		return extractBuildNumber(line);
            	}
            }
			br.close();

			return 0;
						
		} catch (IOException | InterruptedException e) {
			return 0;
		}
	}

	// Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    // Available Elements for copy Element procedure
    private enum Element { 
    	REPORT, XML_RESULT, HTML
    }
        
    /* Small Helper Copy Functions */
    private void copyElementFileToWorkspace(FilePath src, FilePath workspace, Element elem, String id, int build, TaskListener listener) {
    	
    	 String suffix = "";

    	 switch(elem) {
    	 	case REPORT:
    	 		suffix = ".txt";
    	 		break;
    	 	case XML_RESULT: 
    	 		suffix = ".xml";
    	 		break;
    	 	case HTML:
    	 //extract file from src path first
    	 		suffix = ".html";
    	 		break;
    	 	default: 
    	 		listener.getLogger().println("[A3 Builder ElementFile Copy Note:] " + id + " not a defined element.");
    	 }
    	    	 
    	 String dest =  "a3-" + id + "-b" + build + "-copy" + suffix;
    	 FilePath destfile = new FilePath(workspace, dest);

//    	 FilePath parentDest = destfile.getParentFile();
    	 FilePath parentSourceFile = src.getParent();
    	 
//    	 if (parentDestFile == null) { // Critical issue because this means no subdirectory for absint-a3-b<NR> was created
//    		 listener.getLogger().println("[A3 Builder ElementFile Copy Exception:] Destination file has no parent directory. (Inconsistent state) => Copy process is aborted.");
//    		 return;
//    	 }

    	 if (parentSourceFile != null && parentSourceFile.equals(workspace)) {
    		 listener.getLogger().println("[A3 Builder ElementFile Copy Note:] " + id + " source and destination directory are the same. No copy needed.");
    		 return; // Then src and dest are the same file, don't copy
    	 }

    	 // Now start copy process
    	 try {
    		 // Input File
	    	 BufferedReader br = new BufferedReader(
	    			 				new InputStreamReader(				
	    			 						src.read(),
	    			 						"UTF-8"
	    			 						)
	    			 				);
	    			 				//new FileInputStream(src), "UTF-8"));
	    	 
	    	 // Output File
	    	 BufferedWriter bw = new BufferedWriter(
	    			 				new OutputStreamWriter(
	    			 						destfile.write(),
	    			 						"UTF-8"
	    			 						)
	    			 				);
	    			 				//new FileOutputStream(destfile.getAbsoluteFile()), "UTF-8"));
	    			 
	    	 // Copy Content
	    	 while(br.ready()) {
	    		 bw.write(br.readLine() + "\n");
	    	 }
	    	 bw.close();
	    	 br.close();
    	 } catch (FileNotFoundException e) {
    		 listener.getLogger().println("[A3 Builder FileNotFound Exception:] Source file " + src + " could not be found! Aborting copy process to Jenkins a3 workspace.");
    	 } catch (IOException e) {
    		 listener.getLogger().println("[A3 Builder IOException:] Destination file " + dest + " could not be written! Aborting copy process to Jenkins a3 workspace.");
    	 } catch (InterruptedException e) {
    		 listener.getLogger().println("[A3 Builder InterruptedException:] Destination file " + dest + " could not be written! Aborting copy process to Jenkins a3 workspace.");
		}
    }

    private void copyReportFileToWorkspace(FilePath src, FilePath workspace, int build, TaskListener listener) {
    	copyElementFileToWorkspace(src, workspace,  Element.REPORT, "report", build, listener);
    }

    private void copyXMLResultFileToWorkspace(FilePath src, FilePath workspace, int build, TaskListener listener) {
    	copyElementFileToWorkspace(src, workspace, Element.XML_RESULT, "xml-result", build, listener);
    }

    private void copyHTMLReportFileToWorkspace(FilePath src, FilePath workspace, String id, int build, TaskListener listener) {
    	copyElementFileToWorkspace(src, workspace, Element.HTML, id, build, listener);
    }
    
    /**
     * Descriptor for {@link A3Builder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <br>
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /*
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         *
         * If you don't want fields to be persisted, use "transient".
         */

        /*
         * Properties set by the TimingProfiler configuration mask:
         *     Jenkins~~~Manage Jenkins~~~Configure System
         */
		private String alauncher;
		private String a3packages;
        private String almserver;
        private String almport;


        private static final String default_almport = "42424";

        /**
         * Constructor.
         * <br>
         * Constructs a new object of this class and
         * loads the persisted global configuration.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Return the human readable name  used in the configuration screen.
         *
         * @return java.lang.String
         */
        public String getDisplayName() {
            return "a³ Analysis Run";
        }


/**
 * Performs on-the-fly validation of the form field 'timingprofiler_dir'.
 *
 * @param value           The value that the user has typed.
 * @return
 *      Indicates the outcome of the validation. This is sent to the browser.
 *      <br>
 *      Note that returning {@link FormValidation#error(String)} does not
 *      prevent the form from being saved. It just means that a message
 *      will be displayed to the user.
 * @throws IOException             as super class
 * @throws ServletException        as super class
 **/
        public FormValidation doCheckAnalysis_ids(@QueryParameter String value)
                throws IOException, ServletException {
        	if (value != null && !value.trim().equals("")) {
        		if (containsEnvVars(value)){
        			return FormValidation.error("Analysis IDs must not contain system environment variables ${...}!");
        		}
           		// The analysis IDs must be a comma-separated (or differently) list of analysis IDs
        		String[] itemList = value.split(",");
        		// Check for each item that it follows the a3 analysis ID naming scheme
        		for (String item:itemList){
        			item = item.trim();
        			if(!Pattern.matches("[a-zA-Z0-9_]+", item)) return FormValidation.error("Analysis ID '" + item + "' must adhere to the a³ analysis ID conventions: only letters, numbers or underscores allowed!");
        		}
        	}
            return FormValidation.ok();
        }


/**
 * Helper method to check whether a string contains an environment variable of form
 * "$IDENTIFYER"
 *
 * @param   s    String to scan for environment variable expressions
 * @return  Outcome of the check as a boolean (true if such an expression
 *          was found, otherwise false).
 */
       public static final boolean containsEnvVars(String s)
       {
           final String pattern = "\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}";
           final Pattern expr = Pattern.compile(pattern);
           Matcher matcher = expr.matcher(s);
           return matcher.find();
       } 


       /**
        * Performs on-the-fly validation of the form field 'alauncher'.
        *
        * @param value           The value that the user has typed.
        * @return
        *      Indicates the outcome of the validation. This is sent to the browser.
        *      <br>
        *      Note that returning {@link FormValidation#error(String)} does not
        *      prevent the form from being saved. It just means that a message
        *      will be displayed to the user.
        * @throws IOException             as super class
        * @throws ServletException        as super class
        **/
               public FormValidation doCheckAlauncher(@QueryParameter String value)
                       throws IOException, ServletException {

               	if(value == null || value.trim().equals("") )
                       return FormValidation.warning("No path specified. Make sure that you provide a \"Path to a³ installation packages\" below OR that the \"alauncher[.exe]\" program is in the host PATH environment!");
               	
                   if(containsEnvVars(value)) {
                       return FormValidation.warning("The specified path contains an environment variable, please make sure that the constructed path is correct.");
                    }
               	
               	File ftmp = new File(value);
            	if (!ftmp.exists())
                    return FormValidation.error("Specified path not found.");
            	if (ftmp.isFile())
            		return FormValidation.error("You specified a file but not a path. (Probably remove \"alauncher[.exe]\" from the specification)!");
                return FormValidation.ok();
               }

        /**
         * Performs on-the-fly validation of the form field 'a3packages'.
         *
         * @param value           The value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         *      <br>
         *      Note that returning {@link FormValidation#error(String)} does not
         *      prevent the form from being saved. It just means that a message
         *      will be displayed to the user.
         * @throws IOException             as super class
         * @throws ServletException        as super class
         **/
        public FormValidation doCheckA3packages(@QueryParameter String value)
                throws IOException, ServletException {

        	if(value == null || value.trim().equals("") )
                return FormValidation.ok("No path to installer packages specified. Make sure that an installed \"alauncher\" program will be found.");
        	
            if(containsEnvVars(value)) {
                return FormValidation.warning("The specified path contains an environment variable, please make sure that the constructed path is correct.");
             }
        	
        	File ftmp = new File(value);
        	if (!ftmp.exists())
                return FormValidation.error("Specified path not found.");
        	if (ftmp.isFile())
        		return FormValidation.error("You specified a file but not the path containing the install packages!");
            return FormValidation.ok();
        }
        
/**
 * Performs on-the-fly validation of the form field 'project_file'.
 *
 * @param value
 *      This parameter receives the value that the user has typed.
 * @return
 *      Indicates the outcome of the validation. This is sent to the browser.
 *      <br>
 *      Note that returning {@link FormValidation#error(String)} does not
 *      prevent the form from being saved. It just means that a message
 *      will be displayed to the user.
 * @throws IOException               as super class
 * @throws ServletException          as super class
 **/
        public FormValidation doCheckProject_file(@QueryParameter String value)
                throws IOException, ServletException {

        	if(value == null || value.trim().equals("") )
                return FormValidation.warning("No file specified.");
        	
            if(containsEnvVars(value)) {
                return FormValidation.warning("The specified path contains an environment variable, please make sure that the constructed path is correct.");
             }
        	
        	File ftmp = new File(value);
            if (!ftmp.exists())
                return FormValidation.error("Specified file not found.");
            if (!ftmp.canRead())
                return FormValidation.error("Specified file cannot be read.");
            if (!value.endsWith(".apx"))
                return FormValidation.warning("The specified file exists, but does not have the expected suffix (.apx).");
            return FormValidation.ok();
        }

        
/**
 * Indicates that this builder can be used with all kinds of project types.
 *
 * @return boolean
 */
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

/**
 * Sets a new configuration.
 *
 * @throws FormException           as super class
 */
        @Override
        public boolean configure(StaplerRequest2 req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            this.alauncher    = formData.getString("alauncher");
            this.a3packages   = formData.getString("a3packages");
            this.almserver    = formData.getString("almserver");
            this.almport 	  = formData.getString("almport");
            // ... data set, so call save():
            save();
            return super.configure(req,formData);
        }

        /**
         * Returns the currently configured a3 windows installer package.
         *
         * @return java.lang.String
         */
        public String getAlauncher() {
            return this.alauncher;
        }
    
        /**
         * Returns the build number a3 installation is restricted to.
         *
         * @return java.lang.String
         */
        public String getA3packages() {
            return this.a3packages;
        }
    
    	/**
    	 * Returns the currently configured alm (AbsInt Licenseserver manager) server name
    	 *
    	 * @return java.lang.String
    	 */
         public String getAlmserver() {
            return this.almserver;
         }
         
         /**
     	 * Returns the currently configured alm (AbsInt Licenseserver manager) server name
     	 *
     	 * @return java.lang.String
     	 */
         public String getAlmport() {
             if (this.almport == null || this.almport.trim().equals("")) this.almport = DescriptorImpl.default_almport;
        	 return this.almport;
         }
     }
}
