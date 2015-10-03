package org.archive.access.feature;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Vector;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.lucene.util.Version;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.similarities.TFIDFSimilarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.archive.access.index.DocData;
import org.archive.access.index.DocData.DocStyle;
import org.archive.access.utility.SimpleTensor;
import org.archive.nicta.kernel.LDAKernel;
import org.archive.nicta.kernel.TFIDF_A1;
import org.archive.util.format.StandardFormat;
import org.archive.util.io.IOText;
import org.ejml.simple.SimpleMatrix;
import org.netlib.util.booleanW;
import org.netlib.util.intW;

import cc.mallet.pipe.SerialPipes;
import cc.mallet.topics.ParallelTopicModel;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

/**
 * Framework
 * 1. Pre-steps:
 *  (1) index document collection;
 *  (2) training LDA models (per specified fields if desired); compare pre-training & direct usage
 * **/

public class IAccessor {
	private static final boolean debug = false;
	protected static final String NEWLINE = System.getProperty("line.separator");
	protected static final String TAB = "\t";
		
	//all-queries
	private static DocStyle _docStyle = null;
	
	//////////
	//Part: for accessing docno & url based on urlList file
	//////////
	////for designing the DocNo, here it bases on "UniqueUrlInSessions_AtLeast_1Click.txt"////
	private static boolean _INIed = false;
	//private static final String _urlFile = "C:/T/WorkBench/Corpus/DataSource_Analyzed/FilteredBingOrganicSearchLog_AtLeast_1Click/UniqueUrlInSessions_AtLeast_1Click.txt"; 
	private static ArrayList<String> _urlList = new ArrayList<>();
	//mapping a given url to its sequential number in the file
	private static HashMap<String, Integer> _urlToID_Map = new HashMap<>();
		
	private static void iniIDSuits(){
		try {
			ArrayList<String> lineList = IOText.getLinesAsAList_UTF8(FRoot._urlFile);
			for(int i=0; i<lineList.size(); i++){
				String line = lineList.get(i).trim();
				_urlList.add(line);
				_urlToID_Map.put(line, i);
			}	
			
			_INIed = true;
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}
	public static String getDocNo(String urlStr){
		if(!_INIed){
			iniIDSuits();
		}
		if(null==urlStr || null==_urlToID_Map.get(urlStr)){
	    	//System.err.println(urlStr);
			return null;
	    }
		
		int ser = _urlToID_Map.get(urlStr);		
		return "GEQ_1C_"+StandardFormat.serialFormat(ser, "0000000000");
	}
	private static String getUrl(String docNo){
		if(!_INIed){
			iniIDSuits();
		}
		
		int ser = Integer.parseInt(docNo.substring(docNo.lastIndexOf("_")+1));
		return _urlList.get(ser);
	}
	
	//////////
	//Part: for subtopic diversity based on LDA
	//////////
	//private static ParallelTopicModel _ldaModel = null;
	private static boolean _fieldSpecificLDA;
	private static HashMap<String, ParallelTopicModel> _ldaMap;
	private static SerialPipes _serialPipe = null;
	
	//////////
	//Part: for accessing indexed documents based on Lucene
	//////////
	//Text: Extracted html (using a specific extractor), Web: original html (using html parser)
	// without the field of docno, since it is used as a key
	
	//url, title, text
	private static final String [] _fieldList_ClickText = 
		{DocData.ClickText_Field_2, DocData.ClickText_Field_3, DocData.ClickText_Field_4};
	
	//docno & title should be dynamically generated if required
	//url, title, html
	private static final String [] _fieldList_ClickWeb  = 
		{DocData.ClickWeb_Field_2, DocData.ClickWeb_Field_3, DocData.ClickWeb_Field_4};
	
	//corresponding different index w.r.t. collection type	
	private static IndexReader _indexReader_ClickText		= null;
	private static IndexReader _indexReader_ClickWeb		= null;
	private static IndexSearcher _indexSearcher_ClickText	= null;
	private static IndexSearcher _indexSearcher_ClickWeb	= null;
		
	private static HashMap<String, QueryParser> _qParserMap;
	//for getting the inner docid
	private static Analyzer _keyAnalyzer;
	private static QueryParser _keyParser;
	
	private static Analyzer _rawQAnalyzer;
	private static QueryParser _rawQParser;
	//field-specific _rawQParser
	private static HashMap<String, QueryParser> _rawQParserMap;	
	
	TFIDFSimilarity tfidfSim = new DefaultSimilarity();
	
	////per-query initialization////
	////batch access of docid & document w.r.t. index
	private HashMap<String, Document> _docNo2IDocMap;
	private HashMap<String, Integer> _docNo2IDocidMap;
	
	
	//buffering marginal utility features
	//private SimpleTensor _mTensor = null;
	
	
	////////
	//Feature dimensions consist of 3 parts: <1> features for relevance ; <2> tensor based features for marginal relevance; <3> context information based features for marginal relevance
	////////
	//plus 3 context feature, i.e., rankPosition, priorClicks, disToLastClick
	public static final int _FeatureLength_Context = 3;
		
	//tf, idf, tfidf, bm25, attributes w.r.t. fields, plus three context feature
	public static final int _releFeatureLength = 13 + _FeatureLength_Context;
	
	//corresponds to the slice number of the marginal tensor
	public static final int _marFeatureLength_Tensor = 6;	
	//i.e., total length w.r.t. marginal relevance
	public static final int _marFeatureLength = _marFeatureLength_Tensor + _FeatureLength_Context;
	
	//private static final int _mFeatureNum = 15;
	
	
	public IAccessor(DocStyle docStyle, boolean fieldSpecificLDA, boolean useLoadedModel){
		_docStyle = docStyle;	
		
		//1, but also necessary for lda training
		iniIndexRele();
		
		//2
		iniLDARele(fieldSpecificLDA, useLoadedModel);
	}
	
	private void iniCommon(){
		
	}
	
	private void iniIndexRele(){
		////
		//part-1
		////
		this._rawQParserMap = new HashMap<>();	
		
		_keyAnalyzer = new KeywordAnalyzer();
		_keyParser = new QueryParser("docno", _keyAnalyzer);
		
		_rawQAnalyzer = new StandardAnalyzer();
		//_rawQAnalyzer = new EnglishAnalyzer();
		
		for(String field: _fieldList_ClickText){
			if(!_rawQParserMap.containsKey(field)){
				_rawQParserMap.put(field, new QueryParser(field, _rawQAnalyzer));
			}
		}
		for(String field: _fieldList_ClickWeb){
			if(!_rawQParserMap.containsKey(field)){
				_rawQParserMap.put(field, new QueryParser(field, _rawQAnalyzer));
			}
		}		
		
		if(_docStyle == DocStyle.ClickText){
			iniIndexReaderAndSearcher_ClickText();
		}else{
			iniIndexReaderAndSearcher_ClickWeb();
		}
	}
	
	private void iniLDARele(boolean fieldSpecificLDA, boolean useLoadedModel){
		////
		//part-2
		////
		_fieldSpecificLDA = fieldSpecificLDA;
		
		iniLDAMap(useLoadedModel, fieldSpecificLDA, _docStyle);
		
		if(useLoadedModel){
			_serialPipe = LDAInterface.loadPipes();
		}else{
			_serialPipe = LDAInterface.newPipes();
		}
	}
	
	public DocStyle getDocStyle(){
		return this._docStyle;
	}
	
	//////////
	//ini w.r.t. lda
	/////////
	private void iniLDAMap(boolean load, boolean fieldSpecificLDA, DocStyle docStyle){
		_ldaMap = new HashMap<>();
		
		if(fieldSpecificLDA){
			String [] fields = getFields(docStyle);
			
			for(String field: fields){
				_ldaMap.put(field, LDAInterface.getLDAModel(load, field));
			}
		}else{
			_ldaMap.put("text", LDAInterface.getLDAModel(load, "text"));
		}
	}
	
	private static ParallelTopicModel getLDAModel(String field){
		if(_fieldSpecificLDA){
			return _ldaMap.get(field);
		}else{
			return _ldaMap.get("text");
		}
	}
	
	////////
	//ini w.r.t. index
	////////	
	private static void iniIndexReaderAndSearcher_ClickText(){
		try {
			_indexReader_ClickText = DirectoryReader.open(FSDirectory.open(Paths.get(FRoot._clickText_IndexDir)));
		    _indexSearcher_ClickText = new IndexSearcher(_indexReader_ClickText);		    
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}
	//
	private static void iniIndexReaderAndSearcher_ClickWeb(){
		try {
			_indexReader_ClickWeb = DirectoryReader.open(FSDirectory.open(Paths.get(FRoot._clickWeb_IndexDir)));
		    _indexSearcher_ClickWeb = new IndexSearcher(_indexReader_ClickWeb);		    
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}
	
	private static IndexReader getIndexReader(DocStyle docStyle){
		
		if(docStyle == DocStyle.ClickText){
			return _indexReader_ClickText;
		}else if(docStyle == DocStyle.ClickWeb){
			return _indexReader_ClickWeb;
		}else {
			System.err.println("Unaccepted type error!");
			return null;
		}
	}
	
	public IndexSearcher getIndexSearcher(DocStyle docStyle){
		if(docStyle == DocStyle.ClickText){
			return _indexSearcher_ClickText;
		}else if(docStyle == DocStyle.ClickWeb){
			return _indexSearcher_ClickWeb;
		}else {
			System.err.println("Unaccepted type error!");
			return null;
		}
	}
	
	public static String [] getFields(DocStyle docStyle){
		if(docStyle.name().equals("ClickText")){
			return _fieldList_ClickText;			
		}else if (docStyle.name().equals("ClickWeb")){
			return _fieldList_ClickWeb;
		}else{
			return null;
		}
	}
	
	private int getDocID(DocStyle docStyle, String docNo){
		try {
			Query keyQuery = _keyParser.parse(docNo);			
		    TopDocs results = getIndexSearcher(docStyle).search(keyQuery, 2);
		    //ScoreDoc[] hits = results.scoreDocs;
		    int docid = results.scoreDocs[0].doc;
		    System.out.println(docid);
		    return docid;
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		return -1;
	}
	
	private ArrayList<String> getTokens(String rawQuery){
		ArrayList<String> tokenList = new ArrayList<>();
		try {			
			TokenStream tokenStream = _rawQAnalyzer.tokenStream(null, new StringReader(rawQuery));
			//OffsetAttribute offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
			CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);

			tokenStream.reset();
			while (tokenStream.incrementToken()) {
			    //int startOffset = offsetAttribute.startOffset();
			    //int endOffset = offsetAttribute.endOffset();
			    String token = charTermAttribute.toString();
			    tokenList.add(token);			    
			}	
			tokenStream.close();
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		
		/*
		for(String token: tokenList){
			System.out.print(token+" ");
		}
		System.out.println();
		*/
		
		if(tokenList.size()>0){
			return tokenList;
		}else{
			return null;
		}		
	}

	//static Analyzer rawStrQAnalyzer = new StandardAnalyzer();
	static Analyzer rawStrQAnalyzer = new SimpleAnalyzer();
	
	public static String getTokenSequence(String rawStr, HashSet<String> stopWSet){
		
		ArrayList<String> tokenList = new ArrayList<>();
		try {			
			TokenStream tokenStream = rawStrQAnalyzer.tokenStream(null, new StringReader(rawStr));
			//OffsetAttribute offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
			CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);

			tokenStream.reset();
			while (tokenStream.incrementToken()) {
			    //int startOffset = offsetAttribute.startOffset();
			    //int endOffset = offsetAttribute.endOffset();
			    String token = charTermAttribute.toString();
			    tokenList.add(token);			    
			}	
			tokenStream.close();
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		
		/*
		for(String token: tokenList){
			System.out.print(token+" ");
		}
		System.out.println();
		*/
		
		//Vector<String> tokenList = EnAnalyzer.simpleAnalyze(rawStr);
		
		if(tokenList.size()>0){
			return toTokenSequence(tokenList, stopWSet);
		}else{
			return null;
		}		
	}
	
	private static String toTokenSequence(ArrayList<String> tokenList, HashSet<String> stopWSet){
		String tSequence = "";
		for(String token: tokenList){
			if(!stopWSet.contains(token)){
				tSequence += token;
				tSequence += " ";
			}			
		}
		
		return tSequence.trim();		
	}
	
	//////////
	//per-query initialization
	//////////
	private void iniMaps(DocStyle docStyle, ArrayList<String> docNoList){
		
		this._docNo2IDocMap = new HashMap<>();
		this._docNo2IDocidMap = new HashMap<>();
		
		for(String docNo: docNoList){
			try {
				Query keyQuery = _keyParser.parse(docNo);			
			    TopDocs results = getIndexSearcher(docStyle).search(keyQuery, 2);
			    int docid = results.scoreDocs[0].doc;			    		    
			    Document iDoc = getIndexSearcher(docStyle).doc(docid);
			    
			    this._docNo2IDocMap.put(docNo, iDoc);
			    this._docNo2IDocidMap.put(docNo, docid);
			} catch (Exception e) {
				// TODO: handle exception
				e.printStackTrace();
			}
		}		
	}
	//
	/*
	private void iniMTensor(ArrayList<String> docNoList){
		_mTensor = new SimpleTensor(docNoList.size(), docNoList.size(), _mFeatureNum);
	}
	*/
	
	//////////
	//Relevance features
	//////////
	/**
	 * Dimension
	 * (1) 3: tf (term frequency) 	w.r.t. fields
	 * (2) 3: idf (inverse document frequency) w.r.t. fields 
	 * (3) 3: tf*idf 				w.r.t. fields
	 * (4) 3: bm25 					w.r.t. fields
	 * (5) 1: length of url
	 * **/
	public ArrayList<Float> getTFList(boolean debug, DocStyle docStyle, String docNo, String rawQuery){
		ArrayList<Float> tfList = new ArrayList<>();
		
		String [] fieldArray = getFields(docStyle);		
		
		int docID = getDocID(docStyle, docNo);
		ArrayList<String> tokenList = getTokens(rawQuery);
		
		for(String field: fieldArray){
			float tfSum = 0.0f;			
			for(String token: tokenList){
				try {
					Terms terms = getIndexReader(docStyle).getTermVector(docID, field); 
					if (terms != null && terms.size() > 0) {
						//access the terms for this field
					    TermsEnum termsEnum = terms.iterator(); 
					    BytesRef refTerm = null;
					    //explore the terms for this field
					    while ((refTerm = termsEnum.next()) != null) {
					    	//desired term
					    	if(refTerm.utf8ToString().equals(token)){
					    		// enumerate through documents, in this case only one
						        DocsEnum docsEnum = termsEnum.docs(null, null);
						        
						        int docIdEnum, tFre=0;					        
						        while ((docIdEnum = docsEnum.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
						        	//get the term frequency in the document
						            //System.out.println("Fre: "+docsEnum.freq()+"\ttermStr: "+refTerm.utf8ToString()+"\t\tenumID: "+docIdEnum);
						            tFre = docsEnum.freq();
						        }
						        
						        if(tFre > 0){
						        	if(debug){						        		
						        		tfSum += tFre;
						        	}else{
						        		tfSum += tfidfSim.tf(tFre);
						        	}						        	
						        }
					    	}					    	
					    }
					}					
				} catch (Exception e) {
					// TODO: handle exception
					e.printStackTrace();
				}				
			}
			
			//per field, and use the average tfScore
			if(tfSum > 0){
				tfList.add(tfSum/tokenList.size());
			}else{
				tfList.add(tfSum);
			}			
		}
		
		if(debug){
			System.out.println("tfList w.r.t. "+rawQuery);
			for(int i=0; i<fieldArray.length; i++){
				String field = fieldArray[i];
				System.out.println(field+":\t"+"fre:\t"+tfList.get(i));
			}
		}		
		
		return tfList;
	}
	
	public ArrayList<Float> getTFList(boolean debug, DocStyle docStyle, int docid, ArrayList<String> tokenList){
		ArrayList<Float> tfList = new ArrayList<>();
		
		String [] fieldArray = getFields(docStyle);		
		
		for(String field: fieldArray){
			float tfSum = 0.0f;			
			for(String token: tokenList){
				try {
					Terms terms = getIndexReader(docStyle).getTermVector(docid, field); 
					if (terms != null && terms.size() > 0) {
						//access the terms for this field
					    TermsEnum termsEnum = terms.iterator(); 
					    BytesRef refTerm = null;
					    //explore the terms for this field
					    while ((refTerm = termsEnum.next()) != null) {
					    	//desired term
					    	if(refTerm.utf8ToString().equals(token)){
					    		// enumerate through documents, in this case only one
						        DocsEnum docsEnum = termsEnum.docs(null, null);
						        
						        int docIdEnum, tFre=0;					        
						        while ((docIdEnum = docsEnum.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
						        	//get the term frequency in the document
						            //System.out.println("Fre: "+docsEnum.freq()+"\ttermStr: "+refTerm.utf8ToString()+"\t\tenumID: "+docIdEnum);
						            tFre = docsEnum.freq();
						        }
						        
						        if(tFre > 0){
						        	if(debug){						        		
						        		tfSum += tFre;
						        	}else{
						        		tfSum += tfidfSim.tf(tFre);
						        	}						        	
						        }
					    	}					    	
					    }
					}					
				} catch (Exception e) {
					// TODO: handle exception
					e.printStackTrace();
				}				
			}
			
			//per field, and use the average tfScore
			if(tfSum > 0){
				tfList.add(tfSum/tokenList.size());
			}else{
				tfList.add(tfSum);
			}			
		}
		
		if(debug){
			System.out.println("tfList w.r.t. "+tokenList);
			for(int i=0; i<fieldArray.length; i++){
				String field = fieldArray[i];
				System.out.println(field+":\t"+"fre:\t"+tfList.get(i));
			}
		}		
		
		return tfList;
	}
	
	public ArrayList<Float> getIDFList(boolean debug, DocStyle docStyle, String docNo,  String rawQuery){
		ArrayList<Float> idfList = new ArrayList<>();
		
		String [] fieldArray = getFields(docStyle);		
		
		//int docID = getDocID(docStyle, docName);
		ArrayList<String> tokenList = getTokens(rawQuery);
		
		for(String field: fieldArray){
			float idfSum = 0.0f;			
			for(String token: tokenList){
				try {
					Term dfTerm = new Term(field, token); 
					TermStatistics termStatistics = getIndexSearcher(docStyle).termStatistics(dfTerm,
							TermContext.build(getIndexReader(docStyle).getContext(), dfTerm));
					
					if(debug){
						idfSum += termStatistics.docFreq();
					}else{
						float idf = tfidfSim.idf(termStatistics.docFreq(), getIndexReader(docStyle).numDocs());
						idfSum += idf;
					}					
				} catch (Exception e) {
					// TODO: handle exception
					e.printStackTrace();
				}
			}
			
			if(idfSum > 0.0f){
				idfList.add(idfSum/tokenList.size());
			}else{
				idfList.add(0.0f);
			}
		}
		
		if(debug){
			System.out.println("idfList w.r.t. "+rawQuery);
			for(int i=0; i<fieldArray.length; i++){
				String field = fieldArray[i];
				System.out.println(field+":\t"+"docFreq():\t"+idfList.get(i));
			}
		}		
		
		return idfList;
	}
	
	public ArrayList<Float> getIDFList(boolean debug, DocStyle docStyle, int docid, ArrayList<String> tokenList){
		ArrayList<Float> idfList = new ArrayList<>();
		
		String [] fieldArray = getFields(docStyle);		
		
		for(String field: fieldArray){
			float idfSum = 0.0f;			
			for(String token: tokenList){
				try {
					Term dfTerm = new Term(field, token); 
					TermStatistics termStatistics = getIndexSearcher(docStyle).termStatistics(dfTerm,
							TermContext.build(getIndexReader(docStyle).getContext(), dfTerm));
					
					if(debug){
						idfSum += termStatistics.docFreq();
					}else{
						float idf = tfidfSim.idf(termStatistics.docFreq(), getIndexReader(docStyle).numDocs());
						idfSum += idf;
					}					
				} catch (Exception e) {
					// TODO: handle exception
					e.printStackTrace();
				}
			}
			
			if(idfSum > 0.0f){
				idfList.add(idfSum/tokenList.size());
			}else{
				idfList.add(0.0f);
			}
		}
		
		if(debug){
			System.out.println("idfList w.r.t. "+tokenList);
			for(int i=0; i<fieldArray.length; i++){
				String field = fieldArray[i];
				System.out.println(field+":\t"+"docFreq():\t"+idfList.get(i));
			}
		}		
		
		return idfList;
	}
	
	public ArrayList<Float> getTFIDFList(ArrayList<Float> tfList, ArrayList<Float> idfList){
		ArrayList<Float> tfidfList = new ArrayList<>();
		
		for(int i=0; i<tfList.size(); i++){
			tfidfList.add(tfList.get(i)*idfList.get(i));
		}
		
		return tfidfList;
	}
	
	public ArrayList<Float> getBM25List(boolean debug, DocStyle docStyle, String docNo, String rawQuery){
		ArrayList<Float> bm25List = new ArrayList<>();
		
		String [] fieldArray = getFields(docStyle);
		
		int docID = getDocID(docStyle, docNo);
		
		float bm25Score;
		for(String field: fieldArray){			
			try {
				//QueryParser qParser = new MultiFieldQueryParser(new String[] {field}, qAnalyzer);
				//Query fieldQuery = qParser.parse(rawQuery);
				Query fieldQuery = _rawQParserMap.get(field).parse(rawQuery);
				Explanation expl = getIndexSearcher(docStyle).explain(fieldQuery, docID);
				bm25Score = expl.getValue();
				bm25List.add(bm25Score);
			} catch (Exception e) {
				// TODO: handle exception
				e.printStackTrace();
			}
		}
		
		if(debug){
			System.out.println("bm25List w.r.t. "+rawQuery);
			for(int i=0; i<fieldArray.length; i++){
				String field = fieldArray[i];
				System.out.println(field+":\t"+"bm25Score:\t"+bm25List.get(i));
			}
		}		
		
		return bm25List;
	}
	
	public ArrayList<Float> getBM25List(boolean debug, DocStyle docStyle, int docid, String rawQuery){
		ArrayList<Float> bm25List = new ArrayList<>();
		
		String [] fieldArray = getFields(docStyle);
		
		float bm25Score;
		for(String field: fieldArray){			
			try {
				//QueryParser qParser = new MultiFieldQueryParser(new String[] {field}, qAnalyzer);
				//Query fieldQuery = qParser.parse(rawQuery);
				Query fieldQuery = _rawQParserMap.get(field).parse(rawQuery);
				Explanation expl = getIndexSearcher(docStyle).explain(fieldQuery, docid);
				bm25Score = expl.getValue();
				bm25List.add(bm25Score);
			} catch (Exception e) {
				// TODO: handle exception
				e.printStackTrace();
			}
		}
		
		if(debug){
			System.out.println("bm25List w.r.t. "+rawQuery);
			for(int i=0; i<fieldArray.length; i++){
				String field = fieldArray[i];
				System.out.println(field+":\t"+"bm25Score:\t"+bm25List.get(i));
			}
		}		
		
		return bm25List;
	}
	
	public ArrayList<Float> getAttList(boolean debug, DocStyle docStyle, Document iDoc){	
		ArrayList<Float> attList = new ArrayList<>();		
		String [] fieldArray = getFields(docStyle);
		//1
		String url = iDoc.get(fieldArray[0]);
		//2
		//String title = iDoc.get(fieldArray[2]);
		
		int slashNum = getNumOfSlash(url);
		attList.add(slashNum*1.0f);
		
		//??? others
		
		if(debug || null==url){
			System.out.println("AttList:");
			for(int i=0; i<attList.size(); i++){
				System.out.println("Att-"+(i+1)+": "+attList.get(i));
			}
		}
		
		return attList;
	}
	
	private static int getNumOfSlash(String url){
		//System.out.println(url);
		String subUrl = url.substring(url.indexOf("//")+2);
		//System.out.println(subUrl);
		int num = 0;
		for(char c : subUrl.toCharArray()){
			if('/' == c){
				num++;
			}
		}
		//System.out.println(num);
		return num;
	}
	
	public RFeature getRFeature(boolean debug, DocStyle docStyle, String rawQuery, String docNo){
		RFeature rFeature = null;
		try {
			//get document & docid & tokenList
			Query keyQuery = _keyParser.parse(docNo);			
		    TopDocs results = getIndexSearcher(docStyle).search(keyQuery, 2);
		    ScoreDoc[] hits = results.scoreDocs;
		    
		    int docid = hits[0].doc;		    
		    Document iDoc = getIndexSearcher(docStyle).doc(hits[0].doc);
		    ArrayList<String> tokenList = getTokens(rawQuery);
		    
		    ArrayList<Float> tfList = getTFList(debug, docStyle, docid, tokenList);
		    ArrayList<Float> idfList = getIDFList(debug, docStyle, docid, tokenList);
		    ArrayList<Float> tfidfList = getTFIDFList(tfList, idfList);
		    ArrayList<Float> bm25List = getBM25List(debug, docStyle, docid, rawQuery);
		    ArrayList<Float> attList = getAttList(debug, docStyle, iDoc);
		    
		    rFeature = new RFeature(rawQuery, docNo);
		    rFeature.setTFList(tfList);
		    rFeature.setIDFList(idfList);
		    rFeature.setTFIDFList(tfidfList);
		    rFeature.setBM25List(bm25List);
		    rFeature.setATTList(attList);
		    
		    return rFeature;		    
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		
		return null;
	}
		
	//////////
	//Marginal relevance features based on a 3-dimensional tensor
	//////////
	/**
	 * Dimension
	 * (1) 3: topic distribution divergence w.r.t. fields
	 * (2) 3: text dissimilarity (cosine)	w.r.t. fields
	 * 
	 * **/
		
	////1
	public void setTopicDiv(boolean debug, DocStyle docStyle, ArrayList<String> docNoList, int head, SimpleTensor mTensor, TextCollection textCollection){
		String [] fields = getFields(docStyle);
		
		for(int k=0; k<fields.length; k++){
			String field = fields[k];
			
			HashMap<String, String> docNo2TxtMap = textCollection.getFieldMap(field);
			
			ArrayList<String> contentList = new ArrayList<>();			
			for(String docNo: docNoList){				
				contentList.add(docNo2TxtMap.get(docNo));
			}
			
			double [][] abTopicDivMatrix = new double[contentList.size()][contentList.size()];
			
			for(int i=0; i<contentList.size()-1; i++){
				String txtA = contentList.get(i);
				double[] aTDistribution = getTopicDistribution(txtA, field);
								
				for(int j=i+1; j<contentList.size(); j++){
					String txtB = contentList.get(j);					
					double[] bTDistribution = getTopicDistribution(txtB, field);
					
					/*
					System.out.println("topic distribution of "+docNoList.get(j));
					for(double bPro: bTDistribution){
						System.out.print(bPro+ " | ");
					}
					System.out.println();
					*/
					
					abTopicDivMatrix[i][j] = getTopicDistance(aTDistribution, bTDistribution);
					abTopicDivMatrix[j][i] = abTopicDivMatrix[i][j];
					
					if(debug){						
						System.out.println(field);
						System.out.println(docNoList.get(i)+"  -  "+docNoList.get(j)+" : "+abTopicDivMatrix[i][j]);
						System.out.println();
					}
				}
			}
			
			SimpleMatrix aMatrix = new SimpleMatrix(abTopicDivMatrix);
			
			mTensor.setSlice(k+head, aMatrix);			
		}
	}
	//
	public void setTopicDiv_Kernel(boolean debug, DocStyle docStyle, ArrayList<String> docNoList, int head, SimpleTensor mTensor, HashMap<String, LDAKernel> ldaKernelMap){
		String [] fields = getFields(docStyle);
		
		for(int k=0; k<fields.length; k++){
			String field = fields[k];
			
			LDAKernel ldaKernel = ldaKernelMap.get(field);
			
			double [][] abTopicDivMatrix = new double[docNoList.size()][docNoList.size()];
			
			for(int i=0; i<docNoList.size()-1; i++){
				String docnoA = docNoList.get(i);
				//double[] aTDistribution = getTopicDistribution(urlA, field);
				Object repA = ldaKernel.getObjectRepresentation(docnoA);
				
				for(int j=i+1; j<docNoList.size(); j++){
					String docnoB = docNoList.get(j);
					
					//double[] bTDistribution = getTopicDistribution(urlB, field);
					Object repB = ldaKernel.getObjectRepresentation(docnoB);
					
					/*
					System.out.println("topic distribution of "+docNoList.get(j));
					for(double bPro: bTDistribution){
						System.out.print(bPro+ " | ");
					}
					System.out.println();
					*/
					
					//abTopicDivMatrix[i][j] = getTopicDistance(aTDistribution, bTDistribution);
					abTopicDivMatrix[i][j] = ldaKernel.sim(repA, repB);
					abTopicDivMatrix[j][i] = abTopicDivMatrix[i][j];
					
					if(debug){						
						System.out.println(field);
						System.out.println(docNoList.get(i)+"  -  "+docNoList.get(j)+" : "+abTopicDivMatrix[i][j]);
						System.out.println();
					}
				}
			}
			
			SimpleMatrix aMatrix = new SimpleMatrix(abTopicDivMatrix);
			
			mTensor.setSlice(k+head, aMatrix);			
		}
	}
	//
	private static double getTopicDistance(double[] topicDistribution_1, double[] topicDistribution_2){
		double sum = 0.0;
		for(int i=0; i<topicDistribution_1.length; i++){
			double abs = Math.abs(topicDistribution_1[i] - topicDistribution_2[i]);
			sum += Math.pow(abs, 2);
		}
		double dis = Math.sqrt(sum);
		
		return dis;
	}
	//	
	private double[] getTopicDistribution(String rawStr, String field){
		if(debug){
			System.out.println(field+" : "+rawStr);
		}
		
		double[] topicDistribution = null;
		try {
			InstanceList targetIntList = new InstanceList(_serialPipe);
			targetIntList.addThruPipe(new Instance(rawStr, null, null, null));
			topicDistribution = getLDAModel(field).getInferencer().getSampledDistribution(targetIntList.get(0), 50, 1, 5);
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		
		return topicDistribution;
	}
	
	////2 Text Diversity (e.g., text dissimilarity, say 1-consine similarity), 
	//expanded to title, anchor text, body, whole document

	////2
	public void setTextDiv(boolean debug, DocStyle docStyle, ArrayList<String> docNoList, int head, SimpleTensor mTensor){
		String [] fields = getFields(docStyle);
		
		for(int k=0; k<fields.length; k++){
			String field = fields[k];
			
			Map<String,Integer> allTerm = getAllTerms(docStyle, field);
			
			if(debug){
				System.out.println("field-"+field+", terms size:\t"+allTerm.size());
				int min = Math.min(50, allTerm.size());
				int i=0;
				Set<Entry<String, Integer>> keys = allTerm.entrySet();
				for(Entry<String, Integer> key: keys){
					if(i < min){
						System.out.print(key.getKey()+" ");
						i++;
					}
				}
				System.out.println();				
			}
			
			ArrayList<DocTVector> docTVectorList = getDocTVectors(docStyle, allTerm, docNoList, field);
			
			double [][] ijTextDivMatrix = new double[docTVectorList.size()][docTVectorList.size()];
			
			for(int i=0; i<docTVectorList.size()-1; i++){
				DocTVector iDocTVector = docTVectorList.get(i);
				
				for(int j=i+1; j<docTVectorList.size(); j++){
					DocTVector jDocTVector = docTVectorList.get(j);
					
					//jDocTVector.getCommonTerms(iDocTVector);
					
					ijTextDivMatrix[i][j] = consineSim(iDocTVector, jDocTVector);
					ijTextDivMatrix[j][i] = ijTextDivMatrix[i][j];
					
					if(debug){						
						System.out.println(field);
						System.out.println(docNoList.get(i)+"  -  "+docNoList.get(j)+" : "+ijTextDivMatrix[i][j]);
						System.out.println();
					}
				}
			}
			
			SimpleMatrix aMatrix = new SimpleMatrix(ijTextDivMatrix);
			
			if(!debug){
				mTensor.setSlice(k+head, aMatrix);			
			}
		}
	}
	
	///
	private ArrayList<DocTVector> getDocTVectors(DocStyle docStyle, Map<String,Integer> allTerm,
			ArrayList<String> docNoList, String field){
		
		ArrayList<DocTVector> docTVectorList = new ArrayList<>();
		
		for(String docNo: docNoList){
			DocTVector docTVector = geDocTVector(docStyle, allTerm, docNo, field);
			docTVector.normalize();
			
			docTVectorList.add(docTVector);
		}
		
		return docTVectorList;
	}
	
	private DocTVector geDocTVector(DocStyle docStyle, Map<String,Integer> allTerm, String docNo, String field){
		DocTVector docTVector = new DocTVector(allTerm);
		
		int docid = _docNo2IDocidMap.get(docNo);
		try {
			Terms terms = getIndexReader(docStyle).getTermVector(docid, field);
			
			if (terms != null && terms.size() > 0) {
				//access the terms for this field
			    TermsEnum termsEnum = terms.iterator(); 
			    BytesRef refTerm = null;
			    //explore the terms for this field
			    while ((refTerm = termsEnum.next()) != null) {
			    	// enumerate through documents, in this case only one
			        DocsEnum docsEnum = termsEnum.docs(null, null);
			        
			        int docIdEnum, tFre=0;					        
			        while ((docIdEnum = docsEnum.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
			        	//get the term frequency in the document
			            tFre = docsEnum.freq();
			        }
			        
			        docTVector.setEntry(refTerm.utf8ToString(), tFre);			    	
			    }
			}			
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		
		//if(debug){
		//	System.out.println(docNo+"\t"+docTVector.toString());
		//}
		
		return docTVector;		
	}
	
	private double consineSim(DocTVector docTVector_1, DocTVector docTVector_2){
		double vDotPro = docTVector_1._docTVector.dotProduct(docTVector_2._docTVector);
		double normPro = docTVector_1._docTVector.getNorm() * docTVector_2._docTVector.getNorm();
		return vDotPro/normPro;
	}
	
	private Map<String,Integer> getAllTerms(DocStyle docStyle, String field){
		Map<String,Integer> allTerms = new HashMap<>();
		int pos = 0;
		try {			
			//del
			/*			
			int maxDocid = getIndexReader(docStyle).maxDoc();
	        for (int docid = 0; docid < maxDocid; docid++) {
	            Terms vector = getIndexReader(docStyle).getTermVector(docid, field);
	            TermsEnum termsEnum = null;
	            termsEnum = vector.iterator();
	            BytesRef refTerm = null;
	            while ((refTerm = termsEnum.next()) != null) {
	                String termStr = refTerm.utf8ToString();
	                allTerms.put(termStr, pos++);
	            }
	        }
	        */
			
			Fields fields = MultiFields.getFields(getIndexReader(docStyle));
			Terms terms = fields.terms(field);
			
			TermsEnum termItr = terms.iterator();
			BytesRef refTerm = null;
			while(null != (refTerm=termItr.next())){
				String termStr = refTerm.utf8ToString();
                allTerms.put(termStr, pos++);
                //int docFreq = iterator.docFreq();
			}
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		
		return allTerms;
	}
	
	
	////2-2
	public void setTextDiv_Kernel(boolean debug, DocStyle docStyle, ArrayList<String> docNoList, int head, SimpleTensor mTensor, HashMap<String, TFIDF_A1> tfidfKernelMap){
		String [] fields = getFields(docStyle);
		
		for(int k=0; k<fields.length; k++){
			String field = fields[k];
			
			TFIDF_A1 fieldKernel = tfidfKernelMap.get(field);
			
			double [][] ijTextDivMatrix = new double[docNoList.size()][docNoList.size()];
			
			for(int i=0; i<docNoList.size()-1; i++){
				String iDocNo = docNoList.get(i);
				
				for(int j=i+1; j<docNoList.size(); j++){
					String jDocNo = docNoList.get(j);
					
					//jDocTVector.getCommonTerms(iDocTVector);
					
					//ijTextDivMatrix[i][j] = consineSim(iDocTVector, jDocTVector);
					ijTextDivMatrix[i][j] = fieldKernel.sim(fieldKernel.getObjectRepresentation(iDocNo), fieldKernel.getObjectRepresentation(jDocNo));
					ijTextDivMatrix[j][i] = ijTextDivMatrix[i][j];
					
					if(debug){						
						System.out.println(field);
						System.out.println(docNoList.get(i)+"  -  "+docNoList.get(j)+" : "+ijTextDivMatrix[i][j]);
						System.out.println();
					}
				}
			}
			
			SimpleMatrix aMatrix = new SimpleMatrix(ijTextDivMatrix);
			
			if(!debug){
				mTensor.setSlice(k+head, aMatrix);			
			}			
		}		
	}
	
	////3
	////////////
	//Context 3
	///////////
	
	public SimpleTensor getMFeature(boolean debug, DocStyle docStyle, String rawQuery, ArrayList<String> docNoList,
			TextCollection textCollection, HashMap<String, TFIDF_A1> tfidfKernelMap, HashMap<String, LDAKernel> ldaKernelMap){
		
		SimpleTensor mTensor = new SimpleTensor(docNoList.size(), docNoList.size(), _marFeatureLength_Tensor);
		
		//per-query ini
		iniMaps(docStyle, docNoList);
		
		//1
		////using mallet
		setTopicDiv(debug, docStyle, docNoList, 0, mTensor, textCollection);
		
		////using kernel
		//setTopicDiv_Kernel(debug, docStyle, docNoList, 0, mTensor, ldaKernelMap);
		
		//2
		////using index
		setTextDiv(debug, docStyle, docNoList, 3, mTensor);
		
		////tfidfkernel
		//setTextDiv_Kernel(debug, docStyle, docNoList, 3, mTensor, tfidfKernelMap);
		
		return mTensor;
	}
	
	protected static final String QSessionLine = "QSession";
	
	public static HashMap<String, SimpleTensor> loadMarTensor(ArrayList<String> rlineList){
		int sliceNum = IAccessor._marFeatureLength_Tensor;
		
		ArrayList<ArrayList<String>> sessionList = new ArrayList<>();
		
		boolean begin = true;
		ArrayList<String> session = null;
		//System.out.println(lineList.size());
		
		for(String line: rlineList){
			if(line.indexOf(QSessionLine) >= 0){
				if(true == begin){//indicating the beginning
					session = new ArrayList<>();
					session.add(line);
					begin = false;
				}else {
					sessionList.add(session);
					session = new ArrayList<>();
					session.add(line);
				}				
			}else {
				session.add(line);
			}
		}
		//last
		sessionList.add(session);
		
		/*
		System.out.println(sessionList.size());
		for(ArrayList<String> lineSession: sessionList){
			System.out.println(Math.sqrt((lineSession.size()-1)/sliceNum));
			
		}
		*/
		
		HashMap<String, SimpleTensor> key2MarFeatureMap = new HashMap<>();
		///*		
		SimpleTensor marTensor = null;
		String key = null;		
		for(ArrayList<String> lineSession: sessionList){
			
			String stLine = lineSession.get(0);
			String [] keyParts = stLine.split(":");
			key = keyParts[1]+":"+keyParts[2];
			
			int rowNum = (int)Math.sqrt((lineSession.size()-1)/sliceNum);
			marTensor = new SimpleTensor(rowNum, rowNum, sliceNum);
			
			//i: row	j:column	k:slice
			int priorK = -1;
			double[][] simMatrix = null;
			int i, j, k;
			
			for(int c=1; c<lineSession.size(); c++){
				String aline = lineSession.get(c);
				String [] parts = aline.split(TAB);
				k = Integer.parseInt(parts[0]);
				i = Integer.parseInt(parts[1]);
				j = Integer.parseInt(parts[2]);
				
				double val = Double.parseDouble(parts[3]);				
				if(k == priorK){
					simMatrix[i][j] = val;
				}else{
					if(priorK>=0){
						marTensor.setSlice(priorK, new SimpleMatrix(simMatrix));
						priorK = k;
						simMatrix = new double [rowNum][rowNum];
						simMatrix[i][j] = val;
					}else{
						priorK = k;
						simMatrix = new double [rowNum][rowNum];
						simMatrix[i][j] = val;
					}					
				}				
			}
			marTensor.setSlice(priorK, new SimpleMatrix(simMatrix));
			
			key2MarFeatureMap.put(key, marTensor);
		}
		
		
		
		return key2MarFeatureMap;		
	}
	
	//del
	public TermContext getTermContext(String termText, String field, DocStyle docStyle) throws IOException{	
		
		Term term = buildTerm(field, termText);
		TermQuery termQuery = new TermQuery(term);
		
		TermContext termState = termQuery.getTermContext(getIndexSearcher(docStyle));
		return termState;		
	}
	//del
	private static Term buildTerm(String field, String termText){
		return new Term(field, termText);
	}
	//test
	private void test(String indexDir, String field, String termText, int sID){
		try {			
			IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexDir)));
		    //IndexReader reader = IndexReader.open(FSDirectory.open(new File(indexDir)));
		    IndexSearcher searcher = new IndexSearcher(reader);
		    
		    Similarity lmSim = new LMDirichletSimilarity();
		    searcher.setSimilarity(lmSim);
		    
		    Analyzer analyzer = new StandardAnalyzer();
		  
		    Term aTerm = buildTerm(field, termText);
			TermQuery termQuery = new TermQuery(aTerm);
			
			QueryParser queryParser = new MultiFieldQueryParser(new String[] {field}, analyzer);
			Query query = queryParser.parse(termText);
	
			//--
			// Collect enough docs to show 5 pages
		    TopDocs results = searcher.search(query, Integer.MAX_VALUE);
		    ScoreDoc[] hits = results.scoreDocs;
		    
		    int numTotalHits = results.totalHits;
		    System.out.println(numTotalHits + " total matching documents");

		    int end = Math.min(numTotalHits, 10);
		    
		    boolean raw = false;
		    
			for (int i = 0; i < end; i++) {
		        if (raw) {                              // output raw format
		          System.out.println("docid="+hits[i].doc+" score="+hits[i].score);
		          Document doc = searcher.doc(hits[i].doc);
		          System.out.println("\tTitle:\t" + doc.get("title"));
		          continue;
		        }

		        Document doc = searcher.doc(hits[i].doc);
		        String docID = doc.get("docno");
		        if (docID != null) {
		          System.out.println((i+1) + ". " + docID);
		          
		          /*
		          for(Fieldable element: doc.getFields()){
		        	  System.out.println(element.stringValue());
		          }
		          */
		          
		          String docTitle = doc.get("title");
		          if (docTitle != null) {
		            System.out.println("\tTitle:\t" + docTitle);
		          }
		          String docContent = doc.get("text");
		          if (docContent != null) {
			            System.out.println("\tContent: " + docContent);
		          }
		          System.out.println();
		        } else {
		          System.out.println((i+1) + ". " + "No ID for this document");
		        }
		                  
		      }
			//--
			System.out.println();
			int specificDocID = sID;
			Explanation explain = searcher.explain(query, specificDocID);
			float eScore = explain.getValue();
			System.out.println("Explaination w.r.t. docid="+specificDocID+":\t"+eScore);
			//--
			
			//TermContext termState = termQuery.getTermContext(searcher);			
			//System.out.println(termState.docFreq());
			//System.out.println(termState.totalTermFreq());
			
			//--method-1
			int docID = 2;
			//get terms vectors for one document and one field
			//Document doc = reader.document(docID);			
			
			Terms terms = reader.getTermVector(docID, field);
			System.out.println("Term traverse of "+docID+" w.r.t. "+field);
			if (terms != null && terms.size() > 0) {
				// access the terms for this field
			    TermsEnum termsEnum = terms.iterator(); 
			    BytesRef refTerm = null;
			 // explore the terms for this field
			    while ((refTerm = termsEnum.next()) != null) {
			    	// enumerate through documents, in this case only one
			        DocsEnum docsEnum = termsEnum.docs(null, null);
			        
			        int docIdEnum;
			        while ((docIdEnum = docsEnum.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
			        	//get the term frequency in the document
			            System.out.println("Fre: "+docsEnum.freq()+"\ttermStr: "+refTerm.utf8ToString()+"\t\tenumID: "+docIdEnum);
			        }
			    }
			}else{
				if(null == terms){
					System.out.println("null!");
				}
				System.out.println("size-0 terms!");
			}
			//--
			
			///--method-2			
			//df
			Term dfTerm = new Term(field, "education"); 
			System.out.println("Statistics of "+termText+" w.r.t. field:"+field);
			TermStatistics termStatistics = searcher.termStatistics(dfTerm, TermContext.build(reader.getContext(), dfTerm)); 
			System.out.println("education " + "\t totalTermFreq \t " + termStatistics.totalTermFreq()); 
			System.out.println("education " + "\t docFreq \t " + termStatistics.docFreq());
			//tf
			/*
			Term tfTerm = new Term("title", "education"); 
			Bits bits = MultiFields.getLiveDocs(reader); 
			
			PostingsEnum postingsEnum = MultiFields.getTermDocsEnum(r, field, term);
					//.getTermDocsEnum(reader, "title", bits); 

			if (postingsEnum == null) return; 

			int max = 0; 
			while (postingsEnum.nextDoc() != PostingsEnum.NO_MORE_DOCS) {
				final int freq = postingsEnum.freq();
				int docid = postingsEnum.docID();
			}
			*/
			///--
		    
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}		
	}
	
	
	//
	public static void main(String []args){
		//1
		/*
		IAccessor iAccessor = new IAccessor();
		String indexDir = "C:/T/WorkBench/Bench_Dataset/DataSet_ClickModel/Index_ExtractedHtml/";
		String field = "text";
		String termText = "Education";
		int specificDocID = 3;
		iAccessor.test(indexDir, field, termText, specificDocID);
		*/
		
		//2
		//
		/*
		IAccessor iAccessor = new IAccessor(DocStyle.ClickText);
				
		String docNo = "GEQ_1C_0000000059"; //http://pinterest.com/snrmag/special-education-resources/
		iAccessor.getTFList(true, DocStyle.ClickText, docNo, "education");
		iAccessor.getIDFList(true, DocStyle.ClickText, docNo, "education");
		iAccessor.getBM25List(true, DocStyle.ClickText, docNo, "education");
		*/
		
		//3
		/*
		IAccessor iAccessor = new IAccessor();
		iAccessor.getTokens("Special Education Resources on Pinterest");
		*/
		
		//4
		//System.out.println(IAccessor.getNumOfSlash("http://www.edutopia.org/groups/special-ed/102007?page=3"));
		
		//5
		/*
		IAccessor iAccessor = new IAccessor(DocStyle.ClickText);
		String docNo = "GEQ_1C_0000000008";
		iAccessor.getRFeature(true, DocStyle.ClickText, "education", docNo);
		*/
		
		//6
		/*
		1. GEQ_1C_0000000059
		Title:	Special Education Resources on Pinterest | Special education, Worksheets and Printables
		<url>http://pinterest.com/snrmag/special-education-resources/</url>

		2. GEQ_1C_0000000068
		Title:	Dual Language School | Edutopia
		<url>http://www.edutopia.org/groups/special-ed/102007?page=3</url>

		3. GEQ_1C_0000000069
		Title:	Special Education - A Work of Heart: Pinterest Addiction!

		4. GEQ_1C_0000000066
		Title:	Special Education - A Work of Heart
		*/

		/*
		IAccessor iAccessor = new IAccessor(DocStyle.ClickText);
		ArrayList<String> docNoList = new ArrayList<>();
		docNoList.add("GEQ_1C_0000000059");
		docNoList.add("GEQ_1C_0000000068");
		
		iAccessor.getMFeature(false, DocStyle.ClickText, "", docNoList);
		*/
		
		//7
		/*
		String inputStr = "1 http://www.docstoc.com/docs start a.html index&cpath mar http://web";
		HashSet<String> stopSet = new HashSet<>();
		stopSet.add("http");
		System.out.println(IAccessor.getTokenSequence(inputStr, stopSet));
		*/
		
		
	}
}
