package cc.mallet.pipe.iterator;

import java.util.HashSet;
import java.util.Iterator;

import org.archive.access.feature.IAccessor;
import org.archive.access.index.DocData;
import org.archive.access.index.DocData.DocStyle;
import org.archive.access.index.TarDoc;

import cc.mallet.types.Instance;

public class TarIterator extends TarDoc implements Iterator<Instance> {
	private static HashSet<String> _stopWSet = new HashSet<>();
	static{
		_stopWSet.add("http");
		_stopWSet.add("https");
		_stopWSet.add("www");
	}
	
	
	DocData _currentDoc = null;
	String _ldaField = null;
		
	public TarIterator(String dir, String [] indexedFieldArray, String ldaField){
		super(dir, DocStyle.ClickText, indexedFieldArray);
		_ldaField = ldaField;
		
		try {
			this._currentDoc = getNextDocData();			
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
			
			this._currentDoc = null;
		}
	}
	
	public Instance next ()
	{	
		//System.out.println(this._currentDoc.getDocNo()+": "+this._currentDoc.getUrl());
		
		String ldaStr = null;
		if(_ldaField.equals("url")){
			ldaStr = this._currentDoc.getUrl();
			//segmenting urls
			ldaStr = IAccessor.getTokenSequence(ldaStr, _stopWSet);
		}else if(_ldaField.equals("title")){
			ldaStr = this._currentDoc.getTitle();
		}else if(_ldaField.equals("text")){
			ldaStr = this._currentDoc.getText();
		}else{
			System.err.println("Unaccepted lda field error!");
			return null;
		}
		
		Instance carrier = new Instance (ldaStr, null, this._currentDoc.getDocNo(), null);
		
		try {
			this._currentDoc = getNextDocData();
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
			
			this._currentDoc = null;
		}
		
		return carrier;
	}
	
	public boolean hasNext ()	{ return this._currentDoc != null; }
}
