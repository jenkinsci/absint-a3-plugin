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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import hudson.FilePath;
import hudson.model.TaskListener;

public class XMLResultFileHandler {
		
	// Special APX Exception
	private static class XMLResultFileException extends Exception {
		XMLResultFileException(String s) {
		      super(s);
		   }
		}
	
	private TaskListener listener;	
	private Document xmldoc;
	private	FilePath inputXMLFile;
	private int build;
	
	public static final String required_a3build   = "Build: 7686572";
	public static final String required_a3version = "Version: 20.10";
	
	/**
	 * Constructor
	 * @param filename of the XML Result File
	 * @param build current build number 
	 * @param listener TaskListener for Console Output
	 */
	public XMLResultFileHandler(FilePath filename, int build, TaskListener listener){
			
		this.inputXMLFile = filename;
		this.listener = listener;
		this.build = build;
		
		/* Need a Document Builder Factory */
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

		String FEATURE = "http://apache.org/xml/features/disallow-doctype-decl";
		try {
			factory.setFeature(FEATURE, true);
		} catch (ParserConfigurationException e) {
			throw new IllegalStateException("ParserConfigurationException was thrown. The feature '"
				+ FEATURE + "' is not supported by your XML processor.", e);
		}
		
		try {
			DocumentBuilder builder = factory.newDocumentBuilder();

			xmldoc = builder.parse(inputXMLFile.read());
			xmldoc.getDocumentElement().normalize();		
		
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException | InterruptedException e) {
			listener.getLogger().println("[IOException:] XML Result File <" + filename + "> was not found!");
		} catch (SAXException e) {
			listener.getLogger().println("[SAX/IOException:] XML Result File <" + filename + "> was not found!");
		}
				
	}

	
	/***
	 * Pretty Prints all the Results as side effect to the listener Logger
	 * @param  failed_items - A Container that collects the IDs of all analysis items which failed
	 * @param  id2htmlmap - A HashMap that contains (key, value) pairs for all Analysis ID's (key) which have a local HTML Report File (value) specified in the APX Project
	 * @return boolean 
	 * 		   true  - if there was at least one failed analysis
	 * 		   false - if there was no failed analysis
	 */
	public boolean prettyPrintResultsAndCollectFailedItems(Vector<String> failed_items, HashMap<String, FilePath> id2htmlmap) {
		Element rootNode = xmldoc.getDocumentElement(); // must be results.
		NodeList resultsList = rootNode.getElementsByTagName("result");

		// resultlist must contain something!
		try {
			if (resultsList.getLength() < 1) {			
					throw new XMLResultFileException("[XML Result Structure Error:] There must be at least one 'result' Entry in the XML result file");
			}
			
			final int IDwidth = 35; // Width of the ID column, which is handled separately (because of Hyperlink feature for HTML reports)
			String formatString = "%-5s  %9s  %35s  %20s  %5s  %3s  %6s%n";

			listener.getLogger().println ("\n================");
			listener.getLogger().println ("Analysis Results");
			listener.getLogger().println ("================");
			
			listener.getLogger().format("%-" + (IDwidth + 2) +"s", "ID");
			listener.getLogger().format(formatString + "\n", "Type", "Time(sec)", "Result", "Expectation", "#Warn", "#Err", "Failed");
					   
			/* Iterate through each Result-Element */
			for (int i=0; i<resultsList.getLength(); i++) {

				String expectation   = "ok"; // per default
				String result 		 = "";
				StringBuffer result_buf;
				
				Element node = (Element) resultsList.item(i);

				String analysisType  = shortenAnalysisType(node.getAttribute("type"));
				String currentID 	 = node.getAttribute("id");
				String analysisTime  = node.getAttribute("analysis_time");
				String warning_count = node.getAttribute("warning_count");
				String error_count   = node.getAttribute("error_count");
				String failed_str    = node.getAttribute("analysis_status");
				
				// a3 XML Results File Version Check, the following three attributes require a3 version 16.04i and build > 268982
				if (warning_count.equals("") || error_count.equals("") || failed_str.equals("")) {
					throw new XMLResultFileException("[XML Result Structure Error:] This a³ Jenkins Plugin is incompatible with a³ versions prior to " + required_a3version + " " + required_a3build + "!\nRequest support@absint.com for latest a³ Version.\n");
				}
				
				boolean failed  = (!failed_str.equals("success"));
				failed_str = "";
				
				if (!failed) {
					try {							
						// Get ChildNode "expectation"
						NodeList expectationList = node.getElementsByTagName("expectation");
						if (expectationList.getLength() == 1) {
							// only if there was an expectation field check this item for status
							Element expectationElem = (Element) expectationList.item(0);
							if (!expectationElem.getTextContent().equals("success")) {
								// FAILED expectation
								// Determine the expected result there must be exactly one! TODO check stack!
								Element expectedResult = (Element) node.getElementsByTagName("expected_result").item(0);
								expectation = "FAILED (" + expectedResult.getTextContent() + ")";
								failed_str = "><";  // failed expectation overwrites analysis_status == success!
								failed_items.add(currentID);
							}
						}							
						/*else {
							failed_items.add(currentID);
							throw new XMLResultFileException("[XML Result Structure Error:] This a³ Jenkins Plugin is incompatible (no 'expectation' tag found) with a³ versions prior to " + required_a3version + " " + required_a3build + "!\n"+
									 						 "                              Write to support@absint.com to request the latest a³ version.");
							}
							*/
				
						// Depending on the Analysis Type read out different information:
						switch(analysisType) {
							case "TP":
							case "aiT": 
							case "TW":
								String cycles = ((Element) node.getElementsByTagName("cycles").item(0)).getTextContent(); // There must be a cycles sub-node
								String tunit = ((Element) node.getElementsByTagName("unit").item(0)).getTextContent(); // There must be a unit sub-node
								String time = ((Element) node.getElementsByTagName("time").item(0)).getTextContent(); // There must be a time sub-node
								result = (cycles.equals("-1")? "unbounded/infeasible" : cycles + " " + tunit + " = " + time);
								break;
							case "Stack":
								NodeList maximaList = node.getElementsByTagName("maximum");
								int maximaListLength = maximaList.getLength();
								result_buf = new StringBuffer(result); // Use StringBuffer for String appending in loops 
								for (int j=0; j<maximaListLength;j++) {
									Element elem = (Element) maximaList.item(j);
									String svalue = elem.getTextContent();
									String name  = elem.getAttribute("name");
									result_buf.append(name + "=" + svalue);
									if (j!=(maximaListLength - 1)) {result_buf.append(",");}
								}
								if (maximaListLength > 0) { result_buf.append(" bytes"); }
								result = result_buf.toString();
								break;
							case "RComb":
								// First check if we have a <values> block, then we have to dive one level deeper: <values> -> <value>
								NodeList valuesList = node.getElementsByTagName("values");
								NodeList valueList = null;
								if (valuesList.getLength() == 1) {
									Element values = (Element) valuesList.item(0);
									valueList = values.getElementsByTagName("value");
								} else if (valuesList.getLength() == 0){
									valueList = node.getElementsByTagName("value");
								} else {
									failed_items.add(currentID);
									throw new XMLResultFileException("[XML Result Structure Error:] This a³ Jenkins Plugin is incompatible (no 'value' tag found) with a³ versions prior to " + required_a3version + " " + required_a3build + "!\n"+
											 						 "                              Write to support@absint.com to request the latest a³ version.");
								}
								result_buf = new StringBuffer(result); // Use StringBuffer for String appending in loops 
								int valueListLength = valueList.getLength();
								if (valueListLength > 1) { result_buf.append("["); }
								for (int j=0; j<valueListLength;j++) {
									Element elem = (Element) valueList.item(j);
									String value = elem.getTextContent();
									String vunit  = elem.getAttribute("unit");
									result_buf.append(value + " " + vunit);
									if (j!=(valueListLength - 1)) {result_buf.append(",");}
								}
								if (valueListLength > 1) { result_buf.append("]"); }
								result = result_buf.toString();
								break;
							case "Value":
							case "CFG":
							case "TraVi":
								break;
								// ValueAnalyzer/ControlFlow and Trace Visualizer do not have further information, i.e. result stays "" and expectation gives the information.
							default:
								failed_items.add(currentID);
								throw new XMLResultFileException("[XML Result Structure Error:] Analysis Type " + analysisType + " is not supported with this version of Jenkins plugin.\n"+
										 "                              Write to support@absint.com to request an update.");
						}
					} catch (NullPointerException e)
					{
						failed_items.add(currentID);
						throw new XMLResultFileException("[XML Result Structure Error:] This a³ Jenkins Plugin is incompatible with a³ versions prior to " + required_a3version + " " + required_a3build +  "!\n"+
														 "                              Write to support@absint.com to request the latest a3 version.");
					}
				} else {
					// Analysis Status == fail (e.g. because of warnings/errors and high pedantic level!)
					failed_str = "><";
					failed_items.add(currentID);
				}				
				// Print Result Line!
				
				// If there was originally a HTML report file specified in the APX for the current analysis ID, turn it into an hyperlink
				if (id2htmlmap.containsKey(currentID)) {
					try {
						String reportHTMLinWorkspace = "../ws/absint-a3-b" + this.build + "/" + "a3-" + currentID + "-b" + this.build + "-copy.html";
						listener.hyperlink(reportHTMLinWorkspace,  currentID);
					} catch (IOException e) {
						throw new XMLResultFileException("[XML Result File Evaluation Error:] While generating HTML hyperlinks for analysis id " + currentID);
					}					
				} else listener.getLogger().print(currentID); // if no HTML was specified, there is no entry in the map, so just print the ID in a normal way
				
				fillIDwithBlanks(currentID, IDwidth + 2);
				listener.getLogger().format (formatString, analysisType, analysisTime, result, expectation, warning_count, error_count, failed_str );

			} // end of for
			
		listener.getLogger().println();
			
		} catch (XMLResultFileException e) {
				listener.getLogger().println(e.getMessage());
		}
				
		return (failed_items.size() > 0);
	} // end of member prettyPrint...

	
	private void fillIDwithBlanks(String currentID, int nrchars) {
		// TODO Auto-generated method stub
		StringBuffer sbuf = new StringBuffer();
		for (int i=0; i<nrchars-currentID.length();i++) { sbuf.append(" "); }
		listener.getLogger().print(sbuf.toString());		
	}	
	
	private String shortenAnalysisType(String type) {
		String analysisType;
		switch (type) {
			case "aiT": analysisType = "aiT"; break;
			case "TimingProfiler": analysisType = "TP"; break;
			case "TimeWeaver": analysisType = "TW"; break;
			case "StackAnalyzer": analysisType = "Stack"; break;
			case "ValueAnalyzer": analysisType = "Value"; break;
			case "ResultCombinator": analysisType = "RComb"; break;
			case "Control-Flow Visualizer": analysisType = "CFG"; break;
			case "TraceVisualizer": analysisType = "TraVi"; break;
			default: analysisType = type;		
		}
		return analysisType;
	}


	/**
	* Returns XML Result File Object
	* @return File - XML Result File Object
	*/
	public FilePath getXMLResultFile() {
		return this.inputXMLFile;
	}

}
