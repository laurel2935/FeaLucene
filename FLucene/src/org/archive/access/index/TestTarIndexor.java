package org.archive.access.index;

import java.io.BufferedReader;
import java.util.Properties;

import org.archive.access.index.DocData.DocStyle;

public class TestTarIndexor {
	
	private final static String PANDA_ETC  = System.getProperty("panda.etc", "./etc/");
	private final static String PANDA_VAR  = System.getProperty("panda.var", "./var/");
	private final static String PANDA_HOME = System.getProperty("panda.home", "./");
	
	protected String newline = System.getProperty("line.separator");
	protected String fileseparator = System.getProperty("file.separator");
	
	protected BufferedReader buf = null;
	
	private static void setproperties(Properties appProp) {
		appProp.setProperty("panda.home", PANDA_HOME);
		appProp.setProperty("panda.var", PANDA_VAR);
		appProp.setProperty("panda.etc", PANDA_ETC);
	}
	
	public void test() throws Exception{
		Properties appProp = new Properties();
		setproperties(appProp);
		
		buf = FileReader.openFileReader(appProp.getProperty("panda.etc")
				+ fileseparator + "IndexDir.config");
        String indexPath = buf.readLine();

		buf = FileReader.openFileReader(appProp.getProperty("panda.etc")
				+ fileseparator + "DataDir.config");
		
        String dataDir = buf.readLine();
        
        TarIndexor myIndexor = new TarIndexor();
		// System.out.println(appProp);
		// System.s(0);
		//trecindex.pocess(INDEX, DATA, appProp);
        
        //myIndexor.pocess(DocStyle.ClickText, true);
	}
	
	public static void main(String []args){
		//1
		TestTarIndexor testMyIndexor = new TestTarIndexor();
		try {
			testMyIndexor.test();

		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

}
