/*
 *****************************************************************************
 * Copyright (C) 2000-2004, International Business Machines Corporation and  *
 * others. All Rights Reserved.                                              *
 *****************************************************************************
 */
package com.ibm.rbm;

import java.io.*;
import javax.swing.*;
import java.util.*;

import org.apache.xerces.dom.*;
import org.apache.xml.serialize.*;
import org.w3c.dom.*;

/**
 * This class is a plug-in to RBManager that allows the user to export Resource Bundles
 * along with some of the meta-data associated by RBManager to the XLIFF specification.
 * For more information on XLIFF visit the web site <a href="http://www.lisa.org/xliff/">http://www.lisa.org/xliff/</a>
 * 
 * @author George Rhoten
 * @see com.ibm.rbm.RBManager
 */
public class RBxliffExporter extends RBExporter {
    private static final String VERSION = "0.7";
	
    /**
     * Default constructor for the XLIFF exporter.
     */
        
    public RBxliffExporter() {
        super();
		
        // Initialize the file chooser if necessary
        if (chooser == null) {
            chooser = new JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileFilter(){
                public String getDescription() {
                    return "XLIFF Files";
                }
                public boolean accept(File f) {
                    return (f.isDirectory() || f.getName().endsWith(".xlf"));
                }
            });
        }
    }
	
    private String convertToISO(Date d) {
        GregorianCalendar gc = new GregorianCalendar();
        gc.setTime(d);
        return convertToISO(gc);
    }
	
    private String convertToISO(GregorianCalendar gc) {
        StringBuffer buffer = new StringBuffer();
        buffer.append(String.valueOf(gc.get(Calendar.YEAR)));
        int month = gc.get(Calendar.MONTH)+1;
        buffer.append(((month < 10) ? "0" : "") + String.valueOf(month));
        int day = gc.get(Calendar.DAY_OF_MONTH);
        buffer.append(((day < 10) ? "0" : "") + String.valueOf(day));
        buffer.append("T");
        int hour = gc.get(Calendar.HOUR_OF_DAY);
        buffer.append(((hour < 10) ? "0" : "") + String.valueOf(hour));
        int minute = gc.get(Calendar.MINUTE);
        buffer.append(((minute < 10) ? "0" : "") + String.valueOf(minute));
        int second = gc.get(Calendar.SECOND);
        buffer.append(((second < 10) ? "0" : "") + String.valueOf(second));
        buffer.append("Z");
        return buffer.toString();
    }
	
    private String getLocale(Bundle item) {
        String language = item.getLanguageEncoding();
        if (language != null && !language.equals("")) {
            //language = language.toUpperCase();
            String country = item.getCountryEncoding();
            if (country != null && !country.equals("")) {
                //country = country.toUpperCase();
                String variant = item.getVariantEncoding();
                if (variant != null && !variant.equals("")) {
                    //variant = variant.toUpperCase();
                    return language + "-" + country + "-" + variant;
                }
                return language + "-" + country;
            }
            return language;
        }
        return "";
    }

/*    private String getLocale(BundleItem item) {
        if (item != null && item.getParentGroup() != null && item.getParentGroup().getParentBundle() != null) {
        	return getLocale(item.getParentGroup().getParentBundle());
        }
        return "";
    }*/
    
    private String getParentLocale(String locale) {
    	
    	int truncIndex = locale.lastIndexOf('-');
    	if (truncIndex > 0) {
    		locale = locale.substring(0, truncIndex);
    	}
    	else {
    		locale = "";
    	}
    	return locale;
    }
	
    private void addTransUnit(DocumentImpl xml, Element groupElem, BundleItem item, BundleItem parent_item) {
        Element transUnit = xml.createElement("trans-unit");
        //tuv.setAttribute("lang", convertEncoding(item));
        //tuv.setAttribute("creationdate",convertToISO(item.getCreatedDate()));
        //tuv.setAttribute("creationid",item.getCreator());
        transUnit.setAttribute("date",convertToISO(item.getModifiedDate()));
        transUnit.setAttribute("id",item.getKey());
		
        String sourceOrTarget = "target";
        if (parent_item == null) {
        	sourceOrTarget = "source";
        }
        else {
            Element source = xml.createElement("source");
            source.setAttribute("xml:space","preserve");
            source.appendChild(xml.createTextNode(parent_item.getTranslation()));
            transUnit.appendChild(source);
        }
        Element target = xml.createElement(sourceOrTarget);
        target.setAttribute("xml:space","preserve");
    	// This is different from the translate attribute
        if (item.isTranslated()) {
        	// TODO Handle the other states in the future.
        	transUnit.setAttribute("state", "translated");
        }
        target.appendChild(xml.createTextNode(item.getTranslation()));
        transUnit.appendChild(target);
		
        if (item.getComment() != null && item.getComment().length() > 1) {
	        Element comment_prop = xml.createElement("note");
	        comment_prop.setAttribute("xml:space","preserve");
	        comment_prop.appendChild(xml.createTextNode(item.getComment()));
	        transUnit.appendChild(comment_prop);
        }
        
        if ((item.getCreator() != null && item.getCreator().length() > 1)
        	|| (item.getModifier() != null && item.getModifier().length() > 1))
        {
            Element transUnit_prop_group_elem = xml.createElement("prop-group");

            if (item.getCreator() != null && item.getCreator().length() > 1) {
	            Element creator_prop = xml.createElement("prop");
	            creator_prop.setAttribute("prop-type","creator");
	            creator_prop.appendChild(xml.createTextNode(item.getCreator()));
		        transUnit_prop_group_elem.appendChild(creator_prop);
            }
	        
        	if (item.getModifier() != null && item.getModifier().length() > 1) {
		        Element modifier_prop = xml.createElement("prop");
		        modifier_prop.setAttribute("prop-type","modifier");
		        modifier_prop.appendChild(xml.createTextNode(item.getModifier()));
		        transUnit_prop_group_elem.appendChild(modifier_prop);
        	}
	        
	        transUnit.appendChild(transUnit_prop_group_elem);
        }

        groupElem.appendChild(transUnit);
    }
	
    public void export(RBManager rbm) throws IOException {
        if (rbm == null)
        	return;
        // Open the Save Dialog
        int ret_val = chooser.showSaveDialog(null);
        if (ret_val != JFileChooser.APPROVE_OPTION)
        	return;
        // Retrieve basic file information
        File file = chooser.getSelectedFile();                  // The file(s) we will be working with
        File directory = new File(file.getParent());            // The directory we will be writing to
        String base_name = file.getName();                      // The base name of the files we will write
        if (base_name == null || base_name.equals(""))
        	base_name = rbm.getBaseClass();
        if (base_name.endsWith(".xlf"))
        	base_name = base_name.substring(0,base_name.length()-4);
		
        String file_name = base_name + ".xlf";
        
        Vector bundle_v = rbm.getBundles();
        Enumeration bundleIter = bundle_v.elements();
        while (bundleIter.hasMoreElements()) {
        	exportFile(rbm, directory, base_name, (Bundle)bundleIter.nextElement());
        }
    }
    
    private void exportFile(RBManager rbm, File directory, String base_name, Bundle main_bundle)
    	throws IOException
    {
        Bundle parent_bundle = null;
        String parent_bundle_name = null;
        if (!getLocale(main_bundle).equals("")) {
        	// If this isn't the root locale, find the parent
            parent_bundle_name = getParentLocale(getLocale(main_bundle));
	        do {
	        	parent_bundle = rbm.getBundle(parent_bundle_name);
	        	if (parent_bundle != null) {
	        		break;
	        	}
	            parent_bundle_name = getParentLocale(parent_bundle_name);
	        } while (!parent_bundle_name.equals(""));
        }
        
        DocumentImpl xml = new DocumentImpl();
        Element root = xml.createElement("xliff");
        root.setAttribute("version", "1.1");
        xml.appendChild(root);
        Element file_elem = xml.createElement("file");
        String mainLocale = getLocale(main_bundle);
        Bundle parentBundle = null;
        if (mainLocale.equals("")) {
        	file_elem.setAttribute("source-language", getLocale(main_bundle));
        }
        else {
        	file_elem.setAttribute("source-language", parent_bundle_name);
        	file_elem.setAttribute("target-language", getLocale(main_bundle));
        }
        file_elem.setAttribute("datatype", "plaintext");
        file_elem.setAttribute("date", convertToISO(new Date()));
        root.appendChild(file_elem);
		
        Element header = xml.createElement("header");
        Element tool = xml.createElement("tool");
        tool.setAttribute("tool-name", "RBManager");
        tool.setAttribute("tool-id", "RBManager");
        tool.setAttribute("tool-version", VERSION);
        // TODO Add file attribute
        //header.setAttribute("file", "");
        header.appendChild(tool);
        if (main_bundle.comment != null && main_bundle.comment.length() > 0) {
            Element note = xml.createElement("note");
        	header.appendChild(note);
            note.appendChild(xml.createTextNode(main_bundle.comment));
            note.setAttribute("xml:space","preserve");
        }
        file_elem.appendChild(header);
		
        Element body = xml.createElement("body");
        file_elem.appendChild(body);
		
        Vector group_v = main_bundle.getGroupsAsVector();
        Vector parent_group_v = null;
        if (parent_bundle != null) {
        	parent_group_v = parent_bundle.getGroupsAsVector();
        }
        // Loop through each bundle group in main_bundle
        for (int i=0; i < group_v.size(); i++) {
            BundleGroup curr_group = (BundleGroup)group_v.elementAt(i);
            BundleGroup parent_group = null;
            if (parent_group_v != null) { 
	            Enumeration parentGroupIter = parent_group_v.elements();
	            
	            while (parentGroupIter.hasMoreElements()) {
	            	BundleGroup groupToFind = (BundleGroup)parentGroupIter.nextElement();
	            	if (groupToFind.getName().equals(curr_group.getName())) {
	            		parent_group = groupToFind;
	            		break;
	            	}
	            }
            }
            Element group_elem = xml.createElement("group");
            group_elem.setAttribute("id", curr_group.getName());
            if (curr_group.getComment() != null && curr_group.getComment().length() > 1) {
    	        Element comment_prop = xml.createElement("note");
    	        comment_prop.setAttribute("xml:space","preserve");
    	        comment_prop.appendChild(xml.createTextNode(curr_group.getComment()));
    	        group_elem.appendChild(comment_prop);
            }
            
            Vector group_items = curr_group.getItemsAsVector();
            for (int j=0; j < group_items.size(); j++) {
            	BundleItem main_item = (BundleItem)group_items.get(j);
            	BundleItem parent_item = null;
            	if (parent_group != null) {
	            	Enumeration parentIter = parent_group.getItemsAsVector().elements();
	            	BundleItem itemToFind = null;
	                while (parentIter.hasMoreElements()) {
	                	itemToFind = (BundleItem)parentIter.nextElement();
	                	if (itemToFind.getKey().equals(main_item.getKey())) {
	                		parent_item = itemToFind;
	                		break;
	                	}
	                }
            	}
                addTransUnit(xml, group_elem, main_item, parent_item);
                //group_elem.appendChild(tu);
            }
            body.appendChild(group_elem);
        } // end for - i
        String suffix = mainLocale;
        if (!suffix.equals("")) {
        	suffix = '_' + suffix;
        }
        char array[] = suffix.toCharArray();
        for (int k=0; k < array.length; k++) {
            if (array[k] == '-')
                array[k] = '_';
        }
        suffix = String.valueOf(array);
        
        OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(new File(directory,base_name + suffix + ".xlf")), "UTF-8");
        OutputFormat of = new OutputFormat(xml);
        of.setIndenting(true);
        of.setEncoding("UTF-8");
        XMLSerializer serializer = new XMLSerializer(osw, of);
        serializer.serialize(xml);
        osw.close();
    }
}
