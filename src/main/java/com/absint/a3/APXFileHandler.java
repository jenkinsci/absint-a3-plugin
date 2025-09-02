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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import hudson.FilePath;
import hudson.model.TaskListener;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.HashMap;

public class APXFileHandler {

	// Special APX Exception
	private static class APXFileException extends Exception {
		APXFileException(String s) {
		      super(s);
		   }
		}
	
	private TaskListener listener;
	private FilePath currentAPX;	
	private Document xmldoc;
		
	
	/**
	 * Constructor
	 * @param apx name of the APX File
	 * @param listener TaskListener for Console Output
	 * @throws IOException Constructor may throw an IO Exception if apx project file was not found
	 */
	public APXFileHandler(FilePath apx, TaskListener listener) throws IOException{
			
		this.listener = listener;
		this.currentAPX = apx;
		
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
						
			xmldoc = builder.parse(currentAPX.read());
		
			xmldoc.getDocumentElement().normalize();		

		} catch (SAXException e) {
			listener.getLogger().println("[APX Structure Error:] APX file " + currentAPX + " could not be parsed. Make sure that you provided an a³ APX project file instead of an a³ APX workspace file.\nContact support@absint.com for further information and provide the apx file if in doubt.\n");
			throw (new IOException()); // Throw IOException to catch the incomplete APXFileHanlder object			
		} catch (ParserConfigurationException e) {
			listener.getLogger().println("[APX Structure Error:] Serious SAX XML-Parser configuration issue. Contact support@absint.com and provide the apx file please.\n");
			throw (new IOException()); // Throw IOException to catch the incomplete APXFileHanlder object			
		} catch (InterruptedException e) {
			listener.getLogger().println("[APX Structure Error:] Interrupted Exception during APX.read(). Contact support@absint.com and provide the apx file please.\n");
			throw (new IOException()); // Throw IOException to catch the incomplete APXFileHanlder object			

		}
	}

	
	/**
	 * Helper Class for getReportFile() and getResultFile()
	 * since both actions share the same XML Tree Searching
	 * @param element must be "report" or "result"
	 * @return TextContent of "report" or "result" node
	 */
	private String getElementTextContent(String element){
		String foundElementText = null;
		Element rootNode = xmldoc.getDocumentElement();
		NodeList filesList = rootNode.getElementsByTagName("files");
		try {
			if (filesList.getLength() != 1) { 
				throw new APXFileException("[APX Structure Error:] There must be at least one 'files' node in the APX.");
			}
			
			Element filesElement = (Element) filesList.item(0);
			// Either search for "report" or "result" nodes 
			NodeList elementList = filesElement.getElementsByTagName(element);
			
			switch (elementList.getLength()) {
				case 0: foundElementText = null;
						break;
				case 1: Element elem = (Element) elementList.item(0);
						foundElementText = elem.getTextContent();
						break;
				default: throw new APXFileException("[APX Structure Error:] There is more than one " + element + "file section in the APX file!");
			}
		} catch (APXFileException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();				
				listener.getLogger().println(e.getMessage());
				
		} 
		return foundElementText;
	}
	
	
	private FilePath extractRealPathFromElementText(String element) {
		FilePath elementPath = null;
		String elementFileEntryInAPX = getElementTextContent(element);
		// If there was no element (report/result) Section in APX, return null
		if (elementFileEntryInAPX != null) {
			
				FilePath workingDir = currentAPX.getParent();
				elementPath = new FilePath(workingDir, elementFileEntryInAPX);
				
				/* Explanation: The FilePath constructor checks if elementFileEntryInAPX is an absolute path name.
				 *  If yes, it just takes the channel of the workingDir and uses the absolute path name as new FilePath
				 *  If not, it converts the "/" or "\" characters to the machine type (UNIX or others) and puts the rel path behind the workingDir.
				 */
		} 
		return elementPath;
	}
	
	

	/***
	 * ========================================================================================
	 *       The following members are public and can be used in the Jenkins plugin 
	 * ========================================================================================
	 */	
	
	/**
	 * Extracts all analysis items from APX project file and fills the given Map along (AnalysisID, PathToHTMLReportFile)
	 * @param map is the instance of an HashMap filled in this member routine
	 */
	public void fillIDtoHTMLReportMap(HashMap<String, FilePath> map) { 
		
		Element rootNode = xmldoc.getDocumentElement();
		NodeList analysesList = rootNode.getElementsByTagName("analyses");
		try {
			if (analysesList.getLength() == 0){
				// There is no analysis specified in APX => map stays empty.
				return;
			} else if (analysesList.getLength() > 1) { 
				throw new APXFileException("[APX Structure Error:] There must be at most one 'analyses' section in the APX.");
			} 
			
			Element analysesElement = (Element) analysesList.item(0);
			
			// Either search for "analysis" sub-nodes 
			NodeList analysisList = analysesElement.getElementsByTagName("analysis");
			
			/* Iterate through each analysis-Element in the list */
			for (int i=0; i<analysisList.getLength(); i++) {
				
				Element currentAnalysisElement = (Element) analysisList.item(i);
				String currentID 	 = currentAnalysisElement.getAttribute("id");
							
				NodeList currentHTMLreportList = currentAnalysisElement.getElementsByTagName("html_report");
				
				if (currentHTMLreportList.getLength() == 1) {
					Element currentHTMLreportfileElement = (Element) currentHTMLreportList.item(0);
					//extract the html-report file name
					String htmlreportfile_str = currentHTMLreportfileElement.getTextContent();
					
					// Here we know there was at least something specified in the report/result Section in the APX
					// If it is an absolute/relative path, that will be found out during htmlReportfile object creation. Magic!
					
					FilePath workingDir = currentAPX.getParent();
					FilePath htmlReportfile = new FilePath(workingDir, htmlreportfile_str);
										
					// finally fill the map with <ID, HTML File String> pair
					map.put(currentID, htmlReportfile);
				}
				
			}
		} catch (APXFileException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();				
				listener.getLogger().println(e.getMessage());
				
		} 
		return;
	}
	
	/** 
	 * 
	 * @return Report File extracted from APX. If not specified there, a new report file section will
	 *         be created in the internal APX tree. 
	 */
	public FilePath getReportFile() {
		FilePath reportFile = extractRealPathFromElementText("report");
		if (reportFile == null) { 
			listener.getLogger().println("[APX Structure Note:] No report file Section found in APX.");
		}		
		return reportFile;
	}
	
	/** 
	 * 
	 * @return XML Result File Name extracted from APX. If not specified there, return null
	 */
	public FilePath getResultFile() {
		FilePath resultFile = extractRealPathFromElementText("xml_results");
		if (resultFile == null) { 
			listener.getLogger().println("[APX Structure Note:] No result file Section found in APX.");
		}		
		return resultFile;
	}

	/**
	 * reads Pedantic Level from  APX File
	 * @return Pedantic Level: 0 (=fatal), 1 (=error), 2 (=warning) are valid values
	 */
	public int getPedanticLevel() {
		int pedanticLevel = 0;
		Element rootNode = xmldoc.getDocumentElement();
		NodeList optionsList = rootNode.getElementsByTagName("options");
		if (optionsList.getLength() == 0) { listener.getLogger().println("[APX Structure Note:] No 'options' tag found in APX. Using default."); }
		else {
			NodeList analysesOptionsList = ((Element) optionsList.item(0)).getElementsByTagName("analyses_options");
			if (analysesOptionsList.getLength() == 0) { 
				listener.getLogger().println("[APX Structure Note:] No 'analysis_options' tag found in APX. Using default.");
			}
			else {
				NodeList pedanticLevelList = ((Element) analysesOptionsList.item(0)).getElementsByTagName("pedantic_level");
				if (pedanticLevelList.getLength() == 0) { 
					listener.getLogger().println("[APX Structure Note:] No 'pedantic_level' tag found in APX. Using default.");
				} 
				else { 
					Element pedanticLevelElement = (Element) pedanticLevelList.item(0);
					pedanticLevel = Integer.parseInt(pedanticLevelElement.getTextContent());
				}
			}
		}
		
		listener.getLogger().println("[APX FileHandler Note:] Pedantic Level in APX: " + pedanticLevel);
		return pedanticLevel;
	}

	/**
	 * Returns the a3 target in the given apx project
	 * @return a3 Target
	 */
	public String getTarget() {
		Element rootNode = xmldoc.getDocumentElement(); // rootNode == project node
		String target = rootNode.getAttribute("target");
		return target;
	}
	
	/** 
	 * Returns the apx incl. path information  
	 * @return Path+APX File name (original APX) as a File Object
	 */
	public FilePath getAPXFile() {				
		return currentAPX;
	}

}
