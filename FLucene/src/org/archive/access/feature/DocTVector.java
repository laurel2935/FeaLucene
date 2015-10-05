package org.archive.access.feature;

import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.commons.math3.linear.OpenMapRealVector;
import org.apache.commons.math3.linear.RealVectorFormat;

public class DocTVector {
	//private Map<String,Integer> _allTerms;
	public OpenMapRealVector _docTVector;
    
    public DocTVector(Map terms) {
        //this._allTerms = terms;
        this._docTVector = new OpenMapRealVector(terms.size());        
    }

    public void setEntry(Map<String,Integer> allTerms, String term, int freq) {
        if (allTerms.containsKey(term)) {
            int pos = allTerms.get(term);
            _docTVector.setEntry(pos, (double) freq);
        }
    }

    public void normalize() {
        double sum = _docTVector.getL1Norm();
        if(0 != sum){
        	 _docTVector = (OpenMapRealVector) _docTVector.mapDivide(sum);
        }       
    }

    @Override
    public String toString() {
        RealVectorFormat formatter = new RealVectorFormat();
        return formatter.format(_docTVector);
    }
    
    public void getCommonTerms(Map<String,Integer> allTerms, DocTVector cmpDocTVector){
    	Set<Entry<String, Integer>> keys = allTerms.entrySet();
    	
		for(Entry<String, Integer> key: keys){
			if((_docTVector.getEntry(key.getValue()) > 0) && (cmpDocTVector._docTVector.getEntry(key.getValue()) > 0)){
				System.out.print(key.getKey()+"  ");
			}
		}
		System.out.println();
    }
}
