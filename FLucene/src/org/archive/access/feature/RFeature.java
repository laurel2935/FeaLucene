package org.archive.access.feature;

import java.util.ArrayList;

/**
 * 
 * **/

public class RFeature {
	private static final String SEP = ","; 
	String _qID;
	String _docName;
	//CollectionType _cType;
	
	//w.r.t. whole query
	ArrayList<Float> _tfList;
	ArrayList<Float> _idfList;
	ArrayList<Float> _tfidfList;
	ArrayList<Float> _bm25List;
	ArrayList<Float> _dlList;
	ArrayList<Float> _attList;
	
	RFeature(String qID, String docName){
		this._qID = qID;
		this._docName = docName;
		//this._cType = cType;
	}
	
	public void setTFList(ArrayList<Float> tfList){
		this._tfList = tfList;
	}
	public ArrayList<Float> getTFList(){
		return this._tfList;
	}
	public void setIDFList(ArrayList<Float> idfList){
		this._idfList = idfList;
	}
	public ArrayList<Float> getIDFList(){
		return this._idfList;
	}
	public void setTFIDFList(ArrayList<Float> tfidfList){
		this._tfidfList = tfidfList;
	}
	public ArrayList<Float> getTFIDFList(){
		return this._tfidfList;
	}
	public void setBM25List(ArrayList<Float> bm25List){
		this._bm25List = bm25List;
	}
	public ArrayList<Float> getBM25List(){
		return this._bm25List;
	}
	public void setDLList(ArrayList<Float> dlList){
		this._dlList = dlList;
	}
	public ArrayList<Float> getDLList(){
		return this._dlList;
	}
	public void setATTList(ArrayList<Float> attList){
		this._attList = attList;
	}
	public ArrayList<Float> getATTList(){
		return this._attList;
	}
	
	//
	public String toVectorString(){
		StringBuffer strBuffer = new StringBuffer();
		
		strBuffer.append(toVectorString(_tfList));
		strBuffer.append(toVectorString(_idfList));
		strBuffer.append(toVectorString(_tfidfList));
		strBuffer.append(toVectorString(_bm25List));
		//strBuffer.append(toVectorString(_dlList));
		strBuffer.append(toVectorString(_attList));
		strBuffer.deleteCharAt(strBuffer.length()-1);
		
		return strBuffer.toString();
	}
	private String toVectorString(ArrayList<Float> fList){
		String line = "";
		for(Float f: fList){
			line += f.toString();
			line += SEP;
		}
		return line;
	}
}
