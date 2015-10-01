package org.archive.access.feature;

import java.util.HashMap;
import java.util.HashSet;

import org.archive.access.index.DocData;

public class TextCollection {
	public HashSet<String> _docNoSet;
	public HashMap<String, String> _docNo2UrlMap;
	public HashMap<String, String> _docNo2TitleMap;
	public HashMap<String, String> _docNo2ContentMap;
	
	public TextCollection(HashSet<String> docNoSet, HashMap<String, String> docNo2UrlMap, 
			HashMap<String, String> docNo2TitleMap, HashMap<String, String> docNo2ContentMap){
		
		_docNoSet = docNoSet;
		_docNo2UrlMap = docNo2UrlMap;
		_docNo2TitleMap = docNo2TitleMap;
		_docNo2ContentMap = docNo2ContentMap;		
	}
	
	public HashMap<String, String> getFieldMap(String fieldName){
		if(fieldName.equals(DocData.ClickText_Field_2)){
			return this._docNo2UrlMap;
		}else if(fieldName.equals(DocData.ClickText_Field_3)){
			return this._docNo2TitleMap;
		}else if(fieldName.equals(DocData.ClickText_Field_4)){
			return this._docNo2ContentMap;
		}else{
			System.err.println("Unaccepted field error!");
			return null;
		}
	}	

}
