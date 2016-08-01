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
import org.w3c.dom.*;
import org.xml.sax.SAXException;
import hudson.model.TaskListener;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import java.io.*;

public class APXFileHandler {

	// Special APX Exception
	private class APXFileException extends Exception {
		APXFileException(String s) {
		      super(s);
		   }
		}
	
	private TaskListener listener;
	private File currentAPX;	
	private Document xmldoc;
		
	
	/**
	 * Constructor
	 * @param filename of the APX File
	 * @param listener TaskListener for Console Output
	 */
	public APXFileHandler(String filename, TaskListener listener){
			
		this.listener = listener;
		
		/* Need a Document Builder Factory */
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		
		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
		
			/* Create a Document from an input file name */
			currentAPX = new File(filename);

			xmldoc = builder.parse(currentAPX);
		
			xmldoc.getDocumentElement().normalize();		
		
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			listener.getLogger().println("[IOException:] APX File <" + filename + "> was not found!");
		} catch (SAXException e) {
			listener.getLogger().println("[SAX/IOException:] APX File <" + filename + "> was not found!");
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
	
	
	private String extractRealPathFromElementText(String element) {
		String elementRealPath = null;
		String elementFileEntryInAPX = getElementTextContent(element);
		// If there was no element (report/result) Section in APX, return null
		if (elementFileEntryInAPX != null) {
			
			// Here we know there was at least something specified in the report/result Section in the APX
			// Let's find out if it is an absolute/relative path
			
			File elementFile = new File(elementFileEntryInAPX);
			if (!elementFile.isAbsolute()) {
				// The report/result file entry in APX is relative, make an absolute path out of it for Jenkins
				String workingDir = currentAPX.getParent();
				elementFile = new File(workingDir + "/" + elementFile.toString());
			} 
			
			// Check if the path to the report/result is valid
			if (!elementFile.getParentFile().isDirectory()) {
				listener.getLogger().println("[APX Structure Error:] Path to " + element + " file in apx invalid. Using a temporary " + element + " file instead.");
				// if not valid, return null ( as if no report/result file found )
				return null;
			}

			elementRealPath = elementFile.toString();
		} 
		return elementRealPath;
	}
	
	

	/***
	 * ========================================================================================
	 *       The following members are public and can be used in the Jenkins plugin 
	 * ========================================================================================
	 */	
	
	
	/** 
	 * 
	 * @return Report File Name extracted from APX. If not specified there, a new report file section will
	 *         be created in the internal APX tree. 
	 */
	public String getReportFile() {
		String reportFile = extractRealPathFromElementText("report");
		if (reportFile == null) { 
			listener.getLogger().println("[APX Structure Note:] No report file Section found in APX.");
		}		
		return reportFile;
	}
	
	/** 
	 * 
	 * @return XML Result File Name extracted from APX. If not specified there, return null
	 */
	public String getResultFile() {
		String resultFile = extractRealPathFromElementText("xml_results");
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
	 * @return Path+APX File name (original APX)
	 */
	public String getOutputAPX() {				
		return currentAPX.toString();
	}
		
}
