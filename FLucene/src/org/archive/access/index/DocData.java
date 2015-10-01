package org.archive.access.index;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Date;
import java.util.Properties;

/**
 * Output of parsing (e.g. HTML parsing) of an input document.
 */

public class DocData {
	public enum DocStyle {TrecWeb, TrecText, ClickWeb, ClickText};
	
	public static final String ClickText_Field_1 = "docno";
	public static final String ClickText_Field_2 = "url";
	public static final String ClickText_Field_3 = "title";
	public static final String ClickText_Field_4 = "text";
	
	public static final String ClickWeb_Field_1 = "docno";
	public static final String ClickWeb_Field_2 = "url";
	public static final String ClickWeb_Field_3 = "title";
	public static final String ClickWeb_Field_4 = "html";
  
	////ClickText = {"docno", "url", "title", "text"};
	////ClickWeb  = {"docno", "url", "html"}; //docno & title should be dynamically generated if required	
	////Trectext = {"DOCNO", "TEXT"}
	////Trecweb  = {"DOCNO", "HTML"}
	
    private String _docno;
    //...Text
    private String _text;
    //...Html
    private String _html;
    
    private String _url;
    private String _title;
    private Date   _date;
    
    private Properties _props;
    
    //...Text
    public DocData(String docno, String text, String title, Properties props, Date date) {
      this._docno = docno;
      this._text = text;
      this._title = title;
      this._date = date;
      this._props = props;
    }
    //...Text
    public DocData(String docno, String url, String title, String text, Date date, Properties props){
	  this._docno = docno;
	  this._url   = url;
	  this._title = title;
      this._text  = text;      
      this._date  = date;
      this._props = props;	  
    }

  /**
   * @return Returns the name.
   */
  public String getDocNo() {
    return _docno;
  }

  /**
   * @param name The name to set.
   */
  public void setDocNo(String docno) {
    this._docno = docno;
  }

  /**
   * @return Returns the props.
   */
  public Properties getProps() {
    return _props;
  }

  /**
   * @param props The props to set.
   */
  public void setProps(Properties props) {
    this._props = props;
  }

  /**
   * @return Returns the body.
   */
  public String getText() {
    return _text;
  }

  /**
   * @param body The body to set.
   */
  public void setText(String text) {
    this._text = text;
  }

  /**
   * @return Returns the title.
   */
  public String getTitle() {
    return _title;
  }

  /**
   * @param title The title to set.
   */
  public void setTitle(String title) {
    this._title = title;
  }

  /**
   * @return Returns the date.
   */
  public Date getDate() {
    return _date;
  }

  /**
   * @param date The date to set.
   */
  public void setDate(Date date) {
    this._date = date;
  }
  
  public String getUrl(){
	  return this._url;
  }
  
  public String getHtml(){
	  return this._html;
  }
  public void setHtml(String html){
	  this._html = html;
  }
  
  public void sysString(){
	  System.out.println(this._title);
  }
}
