package org.archive.access.index;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Properties;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.archive.access.feature.FRoot;
import org.archive.access.index.DocData.DocStyle;

public class TarIndexor {
		
	private final static String PANDA_ETC  = System.getProperty("panda.etc", "./etc/");
	private final static String PANDA_VAR  = System.getProperty("panda.var", "./var/");
	private final static String PANDA_HOME = System.getProperty("panda.home", "./");

	//private static final String newline = System.getProperty("line.separator");
	//private String field = "body";        
        
	public void pocess(String collectionDir, DocStyle docStyle, String [] indexedfields, String indexPath, boolean create){		
		try {
			//String indexPath=null, collectionDir=null;
			/*
			if(docStyle.name().equals(DocStyle.ClickText.toString())){
				indexPath = FRoot._clickTextIndexDir;
				collectionDir = FRoot._textDataDir;
			}else if (docStyle.name().equals(DocStyle.ClickWeb.toString())){
				indexPath = FRoot._clickWebIndexDir;
				collectionDir = FRoot._webDataDir;
			}else{
				  System.err.println("Unaccepted DocStyle Error!");
				  return;
			}
			*/
			System.out.println(collectionDir);
			System.out.println(indexPath);
			File iDir = new File(indexPath);
			File dataDir = new File(collectionDir);
			if(!iDir.exists() || !dataDir.isDirectory()){
				throw new IOException(iDir + " does not exist or is not a directory");			
			}
			
			Date start = new Date();			
			System.out.println("Indexing to directory '" + indexPath + "'...");
			
			//final Path docDir = Paths.get(collectionDir);

		    Directory dir = FSDirectory.open(Paths.get(indexPath));
		    
		    Analyzer analyzer = new StandardAnalyzer();
		    
		    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

		    if (create) {
		      // Create a new index in the directory, removing any
		      // previously indexed documents:
		      iwc.setOpenMode(OpenMode.CREATE);
		    } else {
		      // Add new documents to an existing index:
		      iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
		    }

		    // Optional: for better indexing performance, if you
		    // are indexing many documents, increase the RAM
		    // buffer.  But if you do this, increase the max heap
		    // size to the JVM (eg add -Xmx512m or -Xmx1g):
		    //
		    // iwc.setRAMBufferSizeMB(256.0);

		    IndexWriter writer = new IndexWriter(dir, iwc);
		    
		    ///*
		    //Properties appProp = new Properties();
		    //appProp.setProperty("panda.home", PANDA_HOME);
			//appProp.setProperty("panda.var", PANDA_VAR);
			//appProp.setProperty("panda.etc", PANDA_ETC);
			//Config config = new Config(appProp);
		    //docMaker.setConfig(config);
			
		    Document doc = null;
		    TarDoc docMaker = new TarDoc(collectionDir, docStyle, indexedfields);
		    
		    
		    
		    while ((doc = docMaker.makeDocument()) != null) { 
		    	 writer.addDocument(doc);// add Document to index		
		     }	
		    //*/
			
			//int numIndexed = writer.maxDoc();
			int numIndexed = writer.numDocs();
			writer.close();
			
			Date end = new Date();
			System.out.println("Indexing " + numIndexed + " files took " +
			(end.getTime() - start.getTime())+ " milliseconds");
	        
			//ExtraInformation EI= new ExtraInformation(index, field);
	        //EI.addExtraInformation();
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}         
	}

	//
	public static void main(String []args){
		//1
		//TarIndexor tarIndexor = new TarIndexor();
		//tarIndexor.pocess(DocStyle.ClickText, true);
		
		//2
		//index extracted text files w.r.t. fields: url, title, text
		///*
		TarIndexor tarIndexor = new TarIndexor();
		String [] indexedfields = {DocData.ClickText_Field_2, DocData.ClickText_Field_3, DocData.ClickText_Field_4};
		tarIndexor.pocess(FRoot._textDataDir, DocStyle.ClickText, indexedfields, FRoot._clickText_IndexDir, true);
		//*/
		
	}
}
