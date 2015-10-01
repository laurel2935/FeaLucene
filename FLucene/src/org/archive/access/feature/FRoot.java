package org.archive.access.feature;

public class FRoot {
	private static final String _sysTag = "MAC";
	
	////index

	public static String _clickText_IndexDir;

	public static String _clickWeb_IndexDir;
	
	
	////lda
	public static String _stopWListDir;
	public static String _ldaBufferDir;
	
	////data
	public static String _textDataDir;
	public static String _webDataDir;
	
	////!!! for designing the DocNo
	public static String _urlFile; 
	
	////buffer
	public static String _bufferDir;
	
	//the pre-filtered set of urls based on all the downloaded htmls w.r.t. sessionsAtLeast2Clicks
	public static String _file_UrlsFromExtractedTexts;
	
	//used subset of search log file
	public static String _file_UsedSearchLog;
	
	
	
	
	
	static{
		if(_sysTag.equals("WIN")){
			//_clickTextIndexDir = "C:/T/WorkBench/Bench_Dataset/DataSet_ClickModel/Index_ExtractedHtml/";
			//_clickWebIndexDir = "C:/T/WorkBench/Bench_Dataset/DataSet_ClickModel/Index_Html";
			
			_textDataDir = "C:/T/WorkBench/Bench_Dataset/DataSet_ClickModel/ExtractedHtml/";
			_webDataDir = "C:/T/WorkBench/Bench_Dataset/DataSet_ClickModel/Html/";
			
			_urlFile = "C:/T/WorkBench/Corpus/DataSource_Analyzed/"
					+ "FilteredBingOrganicSearchLog_AtLeast_1Click/UniqueUrlInSessions_AtLeast_1Click.txt"; 
			
			_bufferDir = "C:/T/WorkBench/Bench_Dataset/DataSet_ClickModel/Buffer/";
			
		}else{
			
			//_file_AcceptedUrlAndWithAvailableHtml = "/Users/dryuhaitao/WorkBench/Corpus/DataSource_Raw/WebPage/Collection_BasedOnAtLeast2Clicks/AcceptedUrlsWithAvailableHtmls_BasedOnAtLeast2Clicks.txt";
			_file_UrlsFromExtractedTexts = "/Users/dryuhaitao/WorkBench/Corpus/DataSource_Raw/WebPage/TxtCollection_BasedOnAtLeast2Clicks/UrlsFromExtractedTexts_BasedOnAtLeast2Clicks.txt";
			//
			_file_UsedSearchLog = "/Users/dryuhaitao/WorkBench/Corpus/DataSource_Analyzed/WeekOrganicLog/AtLeast_2Clicks/AcceptedSessionData_AtLeast_2Clicks.txt";
			
			_textDataDir = "/Users/dryuhaitao/WorkBench/Corpus/DataSource_Raw/WebPage/TxtCollection_BasedOnAtLeast2Clicks/";
			_webDataDir  = "/Users/dryuhaitao/WorkBench/Corpus/DataSource_Raw/WebPage/Collection_BasedOnAtLeast2Clicks/";
			
			_clickText_IndexDir = "/Users/dryuhaitao/WorkBench/CodeBench/Bench_Output/DataSet_ClickModel/Index_ClickText/";
			_clickWeb_IndexDir  = "/Users/dryuhaitao/WorkBench/CodeBench/Bench_Output/DataSet_ClickModel/Index_ClickWeb/";
			
			_urlFile = "/Users/dryuhaitao/WorkBench/Corpus/DataSource_Raw/WebPage/TxtCollection_BasedOnAtLeast2Clicks/UrlsFromExtractedTexts_BasedOnAtLeast2Clicks.txt";
			
			_bufferDir = "/Users/dryuhaitao/WorkBench/CodeBench/Bench_Output/DataSet_ClickModel/Buffer/";
			
			_ldaBufferDir = "/Users/dryuhaitao/WorkBench/CodeBench/Bench_Output/DataSet_ClickModel/LDABuffer/";
			
			_stopWListDir = "/Users/dryuhaitao/git/FLucene/FLucene/stoplists/";
		}
	}
}
