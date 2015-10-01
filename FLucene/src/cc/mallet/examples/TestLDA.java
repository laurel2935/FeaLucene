package cc.mallet.examples;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.Iterator;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.archive.access.feature.FRoot;
import org.archive.access.index.DocData;

import cc.mallet.pipe.CharSequence2TokenSequence;
import cc.mallet.pipe.CharSequenceLowercase;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.PrintInputAndTarget;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.pipe.TokenSequenceRemoveStopwords;
import cc.mallet.pipe.iterator.TarIterator;
import cc.mallet.topics.ParallelTopicModel;
import cc.mallet.topics.SimpleLDA;
import cc.mallet.topics.TopicInferencer;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.IDSorter;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelSequence;

public class TestLDA {
	
	public void compare(String dir, String field) throws Exception{
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
		pipeList.add( new TokenSequenceRemoveStopwords(new File("stoplists/en.txt"), "UTF-8", false, false, false) );
		pipeList.add( new TokenSequence2FeatureSequence() );
		// Print out the features and the label
        //pipeList.add(new PrintInputAndTarget());

		InstanceList instances = new InstanceList (new SerialPipes(pipeList));		
		instances.addThruPipe(new TarIterator(dir, null, field));
		
		int numTopics = 10;
		//(1)
		ParallelTopicModel pModel = new ParallelTopicModel(numTopics, 1.0, 0.01);
		pModel.addInstances(instances);
		// Use two parallel samplers, which each look at one half the corpus and combine
		// statistics after every iteration.
		pModel.setNumThreads(2);
		// Run the model for 50 iterations and stop (this is for testing only, 
		//  for real applications, use 1000 to 2000 iterations)
		pModel.setNumIterations(50);
		pModel.estimate();
		
		//(2)
		SimpleLDA sModel = new SimpleLDA(numTopics, 1.0, 0.01);
		sModel.addInstances(instances);
		sModel.sample(50);
		
		// Create a new instance named "test instance" with empty target and source fields.
		InstanceList testing = new InstanceList(instances.getPipe());
		testing.addThruPipe(new Instance("jesus thomas children figure lesson", null, "test instance", null));		
		
		//t-1
		TopicInferencer pInferencer = pModel.getInferencer();
		double[] testProbabilities = pInferencer.getSampledDistribution(testing.get(0), 10, 1, 5);
		System.out.println("0\t" + testProbabilities[0]);
		System.out.println();
		
		
		
	}
	
	//ParallelTopicModel
	public void testLDA(String dir, String field) throws Exception{
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
		pipeList.add( new TokenSequenceRemoveStopwords(new File("stoplists/en.txt"), "UTF-8", false, false, false) );
		pipeList.add( new TokenSequence2FeatureSequence() );
		// Print out the features and the label
        //pipeList.add(new PrintInputAndTarget());

		InstanceList instances = new InstanceList (new SerialPipes(pipeList));		
		instances.addThruPipe(new TarIterator(dir, null, field));
		
		// Create a model with 100 topics, alpha_t = 0.01, beta_w = 0.01
		//  Note that the first parameter is passed as the sum over topics, while
		//  the second is 
		int numTopics = 10;
		ParallelTopicModel curModel = new ParallelTopicModel(numTopics, 1.0, 0.01);		
		curModel.addInstances(instances);

		// Use two parallel samplers, which each look at one half the corpus and combine
		// statistics after every iteration.
		curModel.setNumThreads(4);
		// Run the model for 50 iterations and stop (this is for testing only, 
		//  for real applications, use 1000 to 2000 iterations)
		curModel.setNumIterations(50);
		curModel.estimate();
		
		//saving the model
		FileOutputStream curOutFile = new FileOutputStream("model.ser");
		ObjectOutputStream curOOS = new ObjectOutputStream(curOutFile);
		curOOS.writeObject(curModel);
		curOOS.close();
		
		////(1)Show the words and topics in the first instance
		//The data alphabet maps word IDs to strings
		Alphabet dataAlphabet = instances.getDataAlphabet();
		Formatter curOut = null;
		/*
		FeatureSequence tokens = (FeatureSequence) model.getData().get(0).instance.getData();
		LabelSequence topics = model.getData().get(0).topicSequence;
		
		out = new Formatter(new StringBuilder(), Locale.US);
		for (int position = 0; position < tokens.getLength(); position++) {
			out.format("%s-%d ", dataAlphabet.lookupObject(tokens.getIndexAtPosition(position)), topics.getIndexAtPosition(position));
		}
		System.out.println(out);
		*/
		
		////(2)Estimate the topic distribution of the first instance, given the current Gibbs state.
		System.out.println("topic distribution w.r.t. instance-0:");
		double[] topicDistribution = curModel.getTopicProbabilities(0);
		// Get an array of sorted sets of word ID/count pairs
		ArrayList<TreeSet<IDSorter>> topicSortedWords = curModel.getSortedWords();		
		// Show top 5 words in topics with proportions for the first document
		for (int topic = 0; topic < numTopics; topic++) {
			Iterator<IDSorter> iterator = topicSortedWords.get(topic).iterator();
			
			curOut = new Formatter(new StringBuilder(), Locale.US);
			curOut.format("%d\t%.3f\t", topic, topicDistribution[topic]);
			int rank = 0;
			while (iterator.hasNext() && rank < 5) {
				IDSorter idCountPair = iterator.next();
				curOut.format("%s (%.0f) ", dataAlphabet.lookupObject(idCountPair.getID()), idCountPair.getWeight());
				rank++;
			}
			System.out.println(curOut);
			System.out.println();
		}
		
		////(3) Create a new instance with high probability of topic 0
		StringBuilder topicZeroText = new StringBuilder();
		//w.r.t. topic 0
		Iterator<IDSorter> curIterator = topicSortedWords.get(0).iterator();
		int curRank = 0;
		while (curIterator.hasNext() && curRank < 5) {
			IDSorter idCountPair = curIterator.next();
			topicZeroText.append(dataAlphabet.lookupObject(idCountPair.getID()) + " ");
			curRank++;
		}		

		// Create a new instance named "test instance" with empty target and source fields.
		InstanceList testing = new InstanceList(instances.getPipe());
		testing.addThruPipe(new Instance(topicZeroText.toString(), null, "test instance", null));

		TopicInferencer curInferencer = curModel.getInferencer();
		//saving the fresh inferencer
		FileOutputStream inferOutFile = new FileOutputStream("infer.ser");
		ObjectOutputStream inferOOS = new ObjectOutputStream(inferOutFile);
		inferOOS.writeObject(curInferencer);
		inferOOS.close();

		
		System.out.println("test instance:\t"+topicZeroText.toString());
		System.out.println();
		
		System.out.println("using currently trained model:");
		double[] curTestProbabilities = curInferencer.getSampledDistribution(testing.get(0), 10, 1, 5);
		System.out.println("0\t" + curTestProbabilities[0]);
		System.out.println();
		
		//loading saved models
		//model
		/*
		FileInputStream modelInFile = new FileInputStream("model.ser");
        ObjectInputStream modelIS = new ObjectInputStream(modelInFile);
        ParallelTopicModel loadedModel = (ParallelTopicModel) modelIS.readObject();
        modelIS.close();
        */
        //or
        ParallelTopicModel loadedModel  = ParallelTopicModel.read(new File("model.ser"));
		
		//Inferencer
		FileInputStream inferInFile = new FileInputStream("infer.ser");
        ObjectInputStream inferIS = new ObjectInputStream(inferInFile);
        TopicInferencer loadedInfer = (TopicInferencer) inferIS.readObject();
        inferIS.close();
        
        Alphabet loadedModelDataAlphabet = loadedModel.getAlphabet();
		ArrayList<TreeSet<IDSorter>> loadedModelTopicSortedWords = loadedModel.getSortedWords();		
		// Show top 5 words in topics with proportions for the first document
		for (int topic = 0; topic < loadedModel.getNumTopics(); topic++) {
			Iterator<IDSorter> iterator = loadedModelTopicSortedWords.get(topic).iterator();
			
			Formatter out = new Formatter(new StringBuilder(), Locale.US);
			out.format("%d\t", topic);
			int rank = 0;
			while (iterator.hasNext() && rank < 5) {
				IDSorter idCountPair = iterator.next();
				out.format("%s (%.0f) ", loadedModelDataAlphabet.lookupObject(idCountPair.getID()), idCountPair.getWeight());
				rank++;
			}
			System.out.println(out);
		}
		System.out.println();
		
		System.out.println("using loaded model:");
		double[] loadedModelTestProbabilities = loadedModel.getInferencer().getSampledDistribution(testing.get(0), 10, 1, 5);
		System.out.println("0\t" + loadedModelTestProbabilities[0]);
		System.out.println();
		
		System.out.println("using loaded inference:");
		double[] loadedInferTestProbabilities = loadedInfer.getSampledDistribution(testing.get(0), 10, 1, 5);
		System.out.println("0\t" + loadedInferTestProbabilities[0]);
		System.out.println();			
	}
	
	public void testReload() throws Exception{	
		ParallelTopicModel loadedModel = null;
		try {
			loadedModel = ParallelTopicModel.read(new File("model.ser"));
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	
		Alphabet loadedModelDataAlphabet = loadedModel.getAlphabet();
		ArrayList<TreeSet<IDSorter>> loadedModelTopicSortedWords = loadedModel.getSortedWords();		
		// Show top 5 words in topics with proportions for the first document
		for (int topic = 0; topic < loadedModel.getNumTopics(); topic++) {
			Iterator<IDSorter> iterator = loadedModelTopicSortedWords.get(topic).iterator();
			
			Formatter out = new Formatter(new StringBuilder(), Locale.US);
			//out.format("%d\t%.3f\t", topic, topicDistribution[topic]);
			int rank = 0;
			while (iterator.hasNext() && rank < 5) {
				IDSorter idCountPair = iterator.next();
				out.format("%d\t%s (%.0f) ", topic, loadedModelDataAlphabet.lookupObject(idCountPair.getID()), idCountPair.getWeight());
				rank++;
			}
			System.out.println(out);
		}
		
		//1
		TopicInferencer loadedModelInferencer = loadedModel.getInferencer();		
		//2
		FileInputStream inferInFile = new FileInputStream("infer.ser");
        ObjectInputStream inferIS = new ObjectInputStream(inferInFile);
        TopicInferencer inferencer_2 = (TopicInferencer) inferIS.readObject();
        inferIS.close();
		//--
		
		
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
		pipeList.add( new TokenSequenceRemoveStopwords(new File("stoplists/en.txt"), "UTF-8", false, false, false) );
		pipeList.add( new TokenSequence2FeatureSequence() );
		// Print out the features and the label
        //pipeList.add(new PrintInputAndTarget());
		
		InstanceList testing = new InstanceList(new SerialPipes(pipeList));
		testing.addThruPipe(new Instance("free encyclopedia wikipedia jobs information", null, "test instance", null));
		//testing.addThruPipe(new Instance("special education blog fire children", null, "test instance", null));
		
		double[] Probabilities_1 = loadedModelInferencer.getSampledDistribution(testing.get(0), 10, 1, 5);
		System.out.println("using loaded model:");
		for(int i=0; i<Probabilities_1.length; i++){
			System.out.println(i+":\t"+Probabilities_1[i]);
		}
		System.out.println();		
		
		double[] Probabilities_2 = inferencer_2.getSampledDistribution(testing.get(0), 10, 1, 5);
		System.out.println("using loaded inferencer:");
		for(int i=0; i<Probabilities_2.length; i++){
			System.out.println(i+":\t"+Probabilities_2[i]);
		}
		System.out.println();
		
		
		/*
		Object [][] topA = model.getTopWords(5);
		for(int i=0; i<model.getNumTopics(); i++){
			for(int j=0; j<5; j++){
				System.out.print((String)topA[i][j]+" ");
			}
			System.out.println();
		}
		*/
		
	}
	
	public static void main(String [] args){
		//1
		///*
		TestLDA testLDA = new TestLDA();
		try {
			testLDA.testLDA(FRoot._textDataDir, DocData.ClickText_Field_4);
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		//null (18984)
		//using currently trained model:
		//0	0.7833333333333324
		//using loaded model:
		//0	0.7833333333333324
		//using loaded inference:
		//0	0.7833333333333324

		//
		//*/
		
		//2
		/*
		TestLDA testLDA = new TestLDA();
		try {
			testLDA.testReload();
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		*/
		
		//3
		//long rgenseed = System.currentTimeMillis();
		//System.out.println(rgenseed);
	}
}
