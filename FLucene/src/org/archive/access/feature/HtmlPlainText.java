package org.archive.access.feature;

import org.archive.access.index.DocData;

public class HtmlPlainText {
	public String _url;
	public String _title;
	public String _plainText;
	
	public HtmlPlainText(String url, String title, String plainText){
		this._url = url;
		this._title = title;
		this._plainText = plainText;
	}
	
	public String getUrl(){
		return this._url;
	}
	public String getTitle(){
		return this._title;
	}
	public String getPlainText(){
		return this._plainText;
	}
	public void sysOutput(){
		System.out.println(this._url);
		System.out.println(this._title);
		System.out.println(this._plainText);
	}
	
	public String getFieldText(String fieldName){
		//DocData.ClickText_Field_2, DocData.ClickText_Field_3, DocData.ClickText_Field_4
		if(fieldName.equals(DocData.ClickText_Field_2)){
			return this._url;
		}else if(fieldName.equals(DocData.ClickText_Field_3)){
			return this._title;
		}else if(fieldName.equals(DocData.ClickText_Field_4)){
			return this._plainText;
		}else{
			System.err.println("Unaccepted field error!");
			return null;
		}		
	}
}
