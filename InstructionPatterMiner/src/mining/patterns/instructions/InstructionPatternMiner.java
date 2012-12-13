package mining.patterns.instructions;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.frequentpatterns.fpgrowth.Database;
import ca.pfv.spmf.frequentpatterns.fpgrowth_saveToFile.AlgoFPGrowth;
import ca.pfv.spmf.tests.MainTestFPGrowth_saveToFile;

public class InstructionPatternMiner {

	public static void main(String args[]) throws FileNotFoundException, IOException
	{
		if(args == null)
			System.out.println("Wrong usage - Input file missing");
		
		if(args.length != 3)
			System.out.println("Wrong number of inputs");
		
		String inFile = args[0];
		String outFile = args[1];
		float minSup = Float.parseFloat(args[2]);
		
//		URL url = InstructionPatternMiner.class.getResource(inFile);
//		String inFilePath = java.net.URLDecoder.decode(url.getPath(),"UTF-8");
		
		// Applying the FPGROWTH algorithmMainTestFPGrowth.java
		AlgoFPGrowth algo = new AlgoFPGrowth();
		algo.runAlgorithm(inFile, outFile, minSup);
		algo.printStats();
	 
	}
}
