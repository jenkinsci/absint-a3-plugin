/*
 * The MIT License
 *
 * Copyright (c) 2016, AbsInt Angewandte Informatik GmbH
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
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;
import javax.servlet.ServletException;
import java.io.*;
import java.net.URI;
import java.nio.file.CopyOption;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author AbsInt Angewandte Informatik GmbH
 */
public class A3Builder extends Builder implements SimpleBuildStep {
    private final String PLUGIN_NAME = "AbsInt a³ Jenkins PlugIn";
    private final String BUILD_NR    = "1.0.0";

    private String project_file, analysis_ids, pedantic_level;
    private boolean copy_report_file, copy_result_file;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public A3Builder(String project_file, String analysis_ids, String pedantic_level, boolean copy_report_file, boolean copy_result_file)
    {
        this.project_file   = project_file;
        this.analysis_ids    = analysis_ids;
        this.pedantic_level = pedantic_level;
        this.copy_report_file = copy_report_file;
        this.copy_result_file = copy_result_file;
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

    /*
     * Returns the currently set pedantic level used for the analysis run.
     *
     * @return java.lang.String
     */
    public String getPedantic_level() {
        return pedantic_level;
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
     * @return String CommandLine String
     */
    public String builda3CmdLine(String reportFile, String resultFile) {
    	File alauncherObj = new File(getDescriptor().getAlauncher());
    	String batch_param = "-b";
    	String pedanticLevel = (!this.pedantic_level.equals("apx") ? "--pedantic-level " + this.pedantic_level : "");
    	String cmd = alauncherObj.toString() + " " + this.project_file + " " + batch_param + " " + reportFile + " " + resultFile + " " + pedanticLevel + " ";
    	// The Formvalidator guarantees a correct naming of the IDs
    	String[] analyses = analysis_ids.split(",");
    	for (String id: analyses) {
    		if(!id.equals("")) cmd += "-i " + id + " ";
    	}
    	return cmd;
    }



	/**
     * Builds the command line for invocation of a3 interactively and limited to just the problematic Items
     * @param failedItems - Vector<String> of failed items from XML Result File
     * @return String CommandLine String
     */
    private String builda3CmdLineInteractive(Vector<String> failedItems) {
    	File alauncherObj = new File(getDescriptor().getAlauncher());
    	String cmd = alauncherObj.toString() + " " + this.project_file;
    	if (failedItems.size() > 0) {
    		String batch_param = "-B";
        	String pedanticHigh = "--pedantic-level warning";
        	cmd +=  " " + pedanticHigh + " " + batch_param + " ";
        	Iterator<String> iter = failedItems.iterator();
        	while(iter.hasNext())
        		cmd += "-i " + iter.next() + " ";    		
    	}    	
		return cmd;
	}


    /*
     *  end interface to <tt>config.jelly</tt>.
     */

    @Override
    public void perform(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) {
    	// Analysis run started. ID plugin in Jenkins output.
        listener.getLogger().println("\nThis is " + PLUGIN_NAME + " in version " + BUILD_NR);

        // Let's parse the a3 project file
        listener.getLogger().println("[A3 Builder Note:] a³ Project File     : " + project_file);
        APXFileHandler apx = new APXFileHandler(project_file, listener);

        try {        
            // Perform compatibility Check: Jenkins Plugin and a3
            String target = apx.getTarget();
            File a3versionFileInfo = new File(workspace.toString() + "/" + "a3-"+target+"-version-b"+build.getNumber()+".info");
            listener.getLogger().println("[A3 Builder Note:] Perform a³ Compatibility Check ... ");
            String checkcmd = (new File(getDescriptor().getAlauncher())).toString() + " -b " + target + " --version-file " + a3versionFileInfo.toString();
        	Proc check = launcher.launch(checkcmd, build.getEnvVars(), listener.getLogger(), workspace);
	        int exitcode = check.join();          // wait for alauncher to finish
	        boolean checkOK = checkA3Compatibility(XMLResultFileHandler.required_a3version, a3versionFileInfo); 
	        listener.getLogger().println(checkOK ? "[A3 Builder Note:] Compatibility Check [OK]" : "");
	        // Delete the temporary generated version info file again.
	        try { a3versionFileInfo.delete(); } catch (Exception e) {}; // If the deleting fails, just ignore it.
	        if (!checkOK) {
	        	listener.getLogger().println("[A3 Builder Error:] This version of the " + PLUGIN_NAME + " requires an a³ for " + target + " version newer than " + XMLResultFileHandler.required_a3version + "!\n" +
	        								 "                    Please contact support@absint.com to request an updated a³ for " + target + " version.");
	        	listener.getLogger().println("\na³ Compatibility check failed.");
	         	build.setResult(hudson.model.Result.FAILURE);
	         	return;        	 
	        }
	        
			// Get the report/XML result file locations
			String reportfile, resultfile;
			String reportfileParam, resultfileParam;
			reportfile  = apx.getReportFile();
			resultfile  = apx.getResultFile();
	
			//Generate temporary report/result file only if no report/result file entry in apx found
			if (reportfile == null) {
				reportfile = (new File(workspace.toString() + "/" + "a3-report-b" + build.getNumber()+".txt")).toString();
				reportfileParam = "--report-file \"" + reportfile + "\"" ;
			} else {
				reportfileParam = "";
			}
			if (resultfile == null) {
				resultfile = (new File(workspace.toString() + "/" + "a3-xml-result-b" + build.getNumber()+".xml")).toString();
				resultfileParam = "--xml-result-file \"" + resultfile + "\"";
			} else {
				resultfileParam = "";
			}
			
			listener.getLogger().println("                   Textual Report File : " + reportfile);
			listener.getLogger().println("                   XML Result File     : " + resultfile);
	
			/*
			 * Prepare start of a3 in batch mode
			 */
			
			int exitCode = -1;
			String cmd = builda3CmdLine(reportfileParam, resultfileParam);

        	long time_before_launch = System.currentTimeMillis();
        	
        	Proc proc = launcher.launch( cmd, // command line call to a3
                                         build.getEnvVars(),
                                         listener.getLogger(),
                                         workspace );
            exitCode = proc.join();          // wait for a3 to finish

            /* Pretty Print XML Result File */
            XMLResultFileHandler xml = new XMLResultFileHandler(resultfile, listener);
         
            boolean xmlfailed = false;
            Vector<String> failedItems = new Vector<String>();
            
            // Check if the XML Result File has been written by the analysis at all
            if (xml.getXMLResultFile().lastModified() >= time_before_launch) {
            	// If yes: evaluate its results
             	xmlfailed = xml.prettyPrintResultsAndCollectFailedItems(failedItems);
            } else {
            	listener.getLogger().println("[A3 Builder Info:] The XML Result File has not been updated by the a³ analysis run. ");
            	// If not updated, the analysis did not run and the success code MUST NOT be 0 (=success)!
            	if (exitCode == 0) {
            		listener.getLogger().println("                    Check the project maually:\n");
              		cmd = builda3CmdLineInteractive(failedItems);
               		listener.getLogger().println(cmd + "\n");
            	}
            }            
            
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
           		}
          		cmd = builda3CmdLineInteractive(failedItems);
           		listener.getLogger().println(cmd + "\n");
            }

            // Copy Report and Result Files to Jenkins Workspace

            if (this.copy_report_file){
            	listener.getLogger().println("[A3 Builder Note:] Copy a³ report file to Jenkins Workspace ...");
            	copyReportFileToWorkspace(reportfile, workspace.toString(), build.getNumber(), listener);
            }

            if (this.copy_result_file){
            	listener.getLogger().println("[A3 Builder Note:] Copy a³ XML result file to Jenkins Workspace ...");
            	copyXMLResultFileToWorkspace(resultfile, workspace.toString(), build.getNumber(), listener);
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

    /* Small Helper: Extracts Build Number in long from a string line ending with the build number */
    private long extractBuildNumber(String n) {
    	try {
    		String buildstrs[] = n.split(" ");
        	String buildstr = buildstrs[buildstrs.length-1];
        	return Integer.parseInt(buildstr);    	
    	} catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
    		return -1;
    	}
	}
    
    /* Small Helper:
     * Parses the version-file output from alauncher, extracts the current a3 build number and compares it with the required build.
     * Returns true  - if the alauncher would call a build >= the required build
     * Returns false - otherwise
     */
	private boolean checkA3Compatibility(String required_a3version, File a3versionFileInfo) {
		try {
			FileReader     fr = new FileReader(a3versionFileInfo);
			BufferedReader br = new BufferedReader(fr);

			String a3buildLine = br.readLine();  // read first line, looks like this: "This is a3 build 123456"
			br.close(); fr.close();

			return (extractBuildNumber(a3buildLine) >= extractBuildNumber(required_a3version));
			
		} catch (IOException e) {
			return false;
		}
	}

	// Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /* Small Helper Copy Functions */
    private void copyElementFileToWorkspace(String src, String workspace, int build, String element, TaskListener listener) {
    	 // Open Source File (Report/XML result File)
    	 File sourcefile = new File(src);
    	 String dest     = workspace + "/" + "a3-" + element + "-b" + build + "-copy" + (element.equals("report") ? ".txt" : ".xml");
    	 File destfile = new File(dest);


    	 if (sourcefile.compareTo(destfile) == 0) {
    		 listener.getLogger().println("[A3 Builder ElementFile Copy Note:] " + element + " source and destination file are the same. No copy needed.");
    		 return; // Then src and dest are the same file, don't copy
    	 }

    	 // Now start copy process
    	 try {
    		 // Input File
	    	 FileReader     fr = new FileReader(sourcefile);
	    	 BufferedReader br = new BufferedReader(fr);

	    	 // Output File
	    	 FileWriter fw = new FileWriter(destfile.getAbsoluteFile());
	    	 BufferedWriter bw = new BufferedWriter(fw);

	    	 // Copy Content
	    	 while(br.ready()) {
	    		 bw.write(br.readLine() + "\n");
	    	 }
	    	 bw.close();fr.close();
	    	 br.close();fw.close();
    	 } catch (FileNotFoundException e) {
    		 listener.getLogger().println("[A3 Builder FileNotFound Exception:] Source file " + src + " could not be found! Aborting copy process to Jenkins workspace.");
    	 } catch (IOException e) {
    		 listener.getLogger().println("[A3 Builder IOException:] Destination file " + dest + " could not be written! Aborting copy process to Jenkins workspace.");
    	 }

    }

    private void copyReportFileToWorkspace(String src, String workspace, int build, TaskListener listener) {
    	copyElementFileToWorkspace(src, workspace, build, "report", listener);
    }

    private void copyXMLResultFileToWorkspace(String src, String workspace, int build, TaskListener listener) {
    	copyElementFileToWorkspace(src, workspace, build, "xml-result", listener);
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
        	if (!value.equals("")) {
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
            File ftmp = new File(value);
            if (!ftmp.exists())
                return FormValidation.error("Specified file not found.");
            if (!ftmp.canExecute())
                return FormValidation.error("Specified file has no rights for execution.");
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
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            this.alauncher    = formData.getString("alauncher");
            // ... data set, so call save():
            save();
            return super.configure(req,formData);
        }



/**
 * Returns the currently configured a3 directory.
 *
 * @return java.lang.String
 */
        public String getAlauncher() {
            return this.alauncher;
        }
    }
}
