package org.archive.access.feature;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.regex.Pattern;

import cc.mallet.pipe.CharSequence2TokenSequence;
import cc.mallet.pipe.CharSequenceLowercase;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.pipe.TokenSequenceRemoveStopwords;
import cc.mallet.pipe.iterator.TarIterator;
import cc.mallet.topics.ParallelTopicModel;
import cc.mallet.topics.TopicInferencer;
import cc.mallet.types.InstanceList;

public class LDAInterface {
		
	private static final String _modelPathPrefix = FRoot._ldaBufferDir+"ldaModel_";
	private static final String _inferencerPathPrefix = FRoot._ldaBufferDir+"ldaInfer_";
	private static final String _suffix = ".ser";
	//common
	private static final String _pipePath = FRoot._ldaBufferDir+"ldaPipes.ser";

	
	public static ParallelTopicModel getLDAModel(boolean reLoad, String field){
		if(reLoad){
			File modelFile = new File(_modelPathPrefix+field+_suffix);
			try {
				if(modelFile.exists()){
					System.out.println("Loading model file...");
					return ParallelTopicModel.read(modelFile);
				}else{
					return train(FRoot._textDataDir, field);
				}
			} catch (Exception e) {
				// TODO: handle exception
				e.printStackTrace();
				return null;
			}			
		}else{
			return train(FRoot._textDataDir, field);
		}		
	}
	
	
	
	public static TopicInferencer getLDAInferencer(boolean reLoad, String field){
		if(reLoad){
			File inferFile = new File(_inferencerPathPrefix+field+_suffix);
			try {
				if(inferFile.exists()){
					return TopicInferencer.read(inferFile);
				}else{
					ParallelTopicModel ldaModel = train(FRoot._textDataDir, field);
					return ldaModel.getInferencer();
				}				
			}catch(Exception e){
				e.printStackTrace();
				return null;
			}
		}else{
			ParallelTopicModel ldaModel = train(FRoot._textDataDir, field);
			return ldaModel.getInferencer();
		}
	}
	
	public static SerialPipes newPipes(){
		// Begin by importing documents from text to feature sequences
		ArrayList<Pipe> pipeList = new ArrayList<Pipe>();

		// Pipes: lowercase, tokenize, remove stopwords, map to features
		pipeList.add( new CharSequenceLowercase() );
		
		//(1) For non-English text, a good choice is --token-regex '[\p{L}\p{M}]+',
		//which means Unicode letters and marks (required for Indic scripts).
		//MALLET currently does not support Chinese or Japanese word segmentation.
		//(2) If you would like to include punctuation inside tokens (for example contractions like "don't" and internet addresses),
		//you might use --token-regex '[\p{L}\p{P}]*\p{L}', which means any sequence of letters or punctuation marks that ends in a letter.
		//Note that this will include quotation marks at the beginning of words.
		pipeList.add( new CharSequence2TokenSequence(Pattern.compile("\\p{L}[\\p{L}\\p{P}]+\\p{L}")) );
		pipeList.add( new TokenSequenceRemoveStopwords(new File(FRoot._stopWListDir+"en.txt"), "UTF-8", false, false, false) );
		pipeList.add( new TokenSequence2FeatureSequence() );
		// Print out the features and the label
        //pipeList.add(new PrintInputAndTarget());
		
		SerialPipes serialPipes = new SerialPipes(pipeList);
		
		return serialPipes; 
	}
	
	public static SerialPipes loadPipes(){
		File pipeFile = new File(_pipePath);
		if(!pipeFile.exists()){
			return null;
		}
		SerialPipes serialPipes = null;
		try {
			FileInputStream pipeInFile = new FileInputStream(_pipePath);
	        ObjectInputStream pipeIS = new ObjectInputStream(pipeInFile);
	        serialPipes = (SerialPipes) pipeIS.readObject();
	        pipeIS.close();
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		
		return serialPipes;
	}
	
	private static int getPredefinedTopicNum(String field){
		if(field.equals("url")){
			return 20;
		}else if(field.equals("title")){
			return 30;
		}else if(field.equals("text")){
			return 50;
		}else{
			System.err.println("Unaccepted field string error!");
			return -1;
		}
	}
	
	public static ParallelTopicModel train(String collectionDir, String field){
		int threadNum = 4;
		int iterNum = 1000;
		
		//1
		SerialPipes serialPipes = loadPipes();
		if(null == serialPipes){
			serialPipes = newPipes();
		}

		InstanceList instances = new InstanceList (serialPipes);		
		instances.addThruPipe(new TarIterator(collectionDir, null, field));
		
		// Create a model with 100 topics, alpha_t = 0.01, beta_w = 0.01
		//  Note that the first parameter is passed as the sum over topics, while the second is 
		int numTopics = getPredefinedTopicNum(field);
		
		ParallelTopicModel curModel = new ParallelTopicModel(numTopics, 1.0, 0.01);		
		curModel.addInstances(instances);

		// Use two parallel samplers, which each look at one half the corpus and combine
		// statistics after every iteration.
		curModel.setNumThreads(threadNum);
		// Run the model for 50 iterations and stop (this is for testing only, for real applications, use 1000 to 2000 iterations)
		curModel.setNumIterations(iterNum);
		
		try {
			curModel.estimate();
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
			return null;
		}
		
		
		//saving
		
		try {
			//(1) saving the model
			FileOutputStream modelOutFile = new FileOutputStream(_modelPathPrefix+field+_suffix);
			ObjectOutputStream modelOOS = new ObjectOutputStream(modelOutFile);
			modelOOS.writeObject(curModel);
			modelOOS.close();
			//(2) saving the fresh inferencer
			TopicInferencer curInferencer = curModel.getInferencer();
			FileOutputStream inferOutFile = new FileOutputStream(_inferencerPathPrefix+field+_suffix);
			ObjectOutputStream inferOOS = new ObjectOutputStream(inferOutFile);
			inferOOS.writeObject(curInferencer);
			inferOOS.close();		
			//(3) saving the pipes
			File pipeFile = new File(_pipePath);
			if(!pipeFile.exists()){
				FileOutputStream pipeOutFile = new FileOutputStream(_pipePath);
			    ObjectOutputStream popeOOS = new ObjectOutputStream(pipeOutFile);
			    popeOOS.writeObject(serialPipes);
			    popeOOS.close();
			}	
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
			return null;
		}		
		
		return curModel;
	}
	
	
	
	//
	public static void main(String []args){
		//1
		LDAInterface.getLDAModel(true, "text");
	}
}
