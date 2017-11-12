package org.acoli.conll.merge;

import java.util.*;
import java.io.*;

/** EXPERIMENTAL: split CoNLL files, then feed them into CoNLLAlign <br/>
	note that this uses heuristic techniques and may lead to losses at the intersection between alignment windows
	accordingly, these are marked explicitly as comments
	
	also note that alignment quality increases with comments being dropped, so, this is done by default
*/
public class CoNLLStreamMerger {
	
	public static void main(String[] argv) throws Exception {

		boolean DEBUG=false;
	
		int window = 10000;
		boolean keepComments=false;
		if(!Arrays.asList(argv).toString().toLowerCase().matches(".*[\\[,] *-silent[,\\]].*"))
			System.err.println("synopsis: CoNLLStreamMerger FILE1.tsv FILE2.tsv ALIGN_ARGS [-window=INT] [-keep-comments]\n"+
				"\tFILEi.tsv      tab-separated text files, e.g. CoNLL format\n"+
				"\tALIGN_ARGS     other arguments passed on to CoNLLAlign, see there\n"+
				"\t-window=INT    number of lines for the alignment window, by default "+window+", larger windows increase runtime and quality\n"+
				"\t-keep-comments skipping comments facilitates alignment and is thus default, use this to keep comments\n"+
				"break argument files into segments, and apply CoNLLAlign iteratively");

		if(argv[argv.length-1].toLowerCase().equals("-keep-comments")) {
			keepComments=true;
			argv=Arrays.copyOfRange(argv,0,argv.length-1);
		}
				
		if(argv[argv.length-1].toLowerCase().startsWith("-window=")) {
			window=Integer.parseInt(argv[argv.length-1].replaceFirst("^.*=",""));
			argv=Arrays.copyOfRange(argv,0,argv.length-1);
		}

		if(!Arrays.asList(argv).toString().toLowerCase().matches(".*[\\[,] *-silent[,\\]].*")) {
			argv=Arrays.copyOfRange(argv,0,argv.length+1);
			argv[argv.length-1]="-silent";
		}
		
		int col1 = 0;
		int col2 = 0;
		try {
			col1 = Integer.parseInt(argv[2]);
			col2 = Integer.parseInt(argv[3]);
		} catch (Exception e) {};
		
		HashSet<Integer> dropCols = new HashSet<Integer>();
		if(!Arrays.asList(argv).toString().toLowerCase().matches(".*[\\[,] *-drop[,\\]].*"))
			dropCols.add(col2);
		
		int l = 0;
		while(l<argv.length && !argv[l].toLowerCase().equals("-drop"))
			l++;
		while(l<argv.length) {
			try {
				dropCols.add(Integer.parseInt(argv[l++]));
			} catch (NumberFormatException e) {}
		}

		
		BufferedReader file1 = new BufferedReader(new FileReader(argv[0]));
		BufferedReader file2 = new BufferedReader(new FileReader(argv[1]));
		
		Vector<String> buffer1 = null; 
		Vector<String> buffer2 = null;
		int buffer1Width=-1;
		int buffer2Width=-1;
		
		File tmp1 = File.createTempFile("CoNLLStreamMerger",".tmp");
		if(DEBUG) tmp1 = File.createTempFile("CoNLLStreamMerger",".tmp",new File("."));
		tmp1.createNewFile();
		if(!DEBUG) tmp1.deleteOnExit();

		File tmp2 = File.createTempFile("CoNLLStreamMerger",".tmp");
		if(DEBUG) tmp2 = File.createTempFile("CoNLLStreamMerger",".tmp",new File("."));
		tmp2.createNewFile();
		if(!DEBUG) tmp2.deleteOnExit();
		argv[0]=tmp1.toString();
		argv[1]=tmp2.toString();

		while(buffer1==null || buffer2==null || buffer1.size()>0 || buffer2.size()>0) {

			// create buffers
			if(buffer1==null) buffer1=new Vector<String>();
			if(buffer2==null) buffer2=new Vector<String>();

			// fill buffers
			try {
				while(buffer1.size()<window) {
					buffer1.add(file1.readLine());
					buffer1Width=Math.max(buffer1Width,buffer1.get(buffer1.size()-1).split("\t").length);
					if(!keepComments && buffer1.get(buffer1.size()-1).trim().startsWith("#")) buffer1.remove(buffer1.size()-1); // comments interfere with alignment
				}
			} catch (IOException e) {}
			
			try {
				while(buffer2.size()<window) {
					buffer2.add(file2.readLine());
					buffer2Width=Math.max(buffer2Width,buffer2.get(buffer2.size()-1).split("\t").length);
					if(!keepComments && buffer2.get(buffer2.size()-1).trim().startsWith("#")) buffer2.remove(buffer2.size()-1); // comments interfere with alignment
				}
			} catch (IOException e) {}
			
			// write buffers to tmp files
			tmp1.delete();
			tmp1.createNewFile();
			FileWriter out1 = new FileWriter(tmp1);
			for(String s : buffer1)
				out1.write(s+"\n");
			out1.close();

			tmp2.delete();
			tmp2.createNewFile();			
			FileWriter out2 = new FileWriter(tmp2);
			for(String s : buffer2)
				out2.write(s+"\n");
			out2.close();
			
			// CoNLLAlign and write to String
			ByteArrayOutputStream merged = new ByteArrayOutputStream();
			PrintStream out = System.out;
			System.setOut(new PrintStream(merged));
			
			if(DEBUG) System.err.println("CoNLLAlign "+Arrays.asList(argv).toString().replaceAll("[\\[\\],]"," ").trim());
			CoNLLAlign.main(argv); // align
			
			System.setOut(out);
			String[] m = merged.toString().split("\n");
			
			// find last aligned row
			int lastAligned = -1;
			for(int i = 0; i<m.length; i++) {
				String[] fields = m[i].split("\t");
				if(fields.length>=buffer1Width && 
				   !Arrays.asList(Arrays.copyOfRange(fields,buffer1Width,fields.length)).toString().replaceAll("\\*[^,\\]]*","").matches("^\\[[\\?, \"]+\\]$") &&
				   !Arrays.asList(Arrays.copyOfRange(fields,0,buffer1Width)).toString().replaceAll("\\*[^,\\]]*","").matches("^\\[[\\?, \"]+\\]$"))
				   lastAligned=i;
			}			
			
			if(lastAligned<0 && buffer1.size()>0 && buffer2.size()>0) {
				window=window*2;
				System.err.println("warning: did not find an alignment, doubling window to "+window+" lines");
			}
			
			// write output until last aligned row
			for(int i = 0; i<=lastAligned; i++)
				System.out.println(m[i]);
			
			// @todo: row1 calculation seems to be ok, but row2 is too small
			System.out.println("###################### end of alignment window #########");
			
			// find out the number of unaligned *content* rows on both sides
			int rows1 = 0;
			int rows2 = 0;
			for(int i = lastAligned+1; i<m.length; i++) {
				String[] fields = m[i].split("\t");
				if(fields.length>=buffer1Width && !Arrays.asList(Arrays.copyOfRange(fields,buffer1Width,fields.length)).toString().replaceAll("\\*[^,\\]]*","").matches("^\\[[\\?, \"]+\\]$")) rows2++;
				if(fields.length>=buffer1Width && !Arrays.asList(Arrays.copyOfRange(fields,0,buffer1Width)).toString().replaceAll("\\*[^,\\]]*","").matches("^\\[[\\?, \"]+\\]$")) rows1++;
			}
			
			// remove alined lines from buffers
			lastAligned = buffer1.size();
			while(rows1>0) {
				lastAligned--;
				if(!buffer1.get(lastAligned).replaceFirst("#.*","").trim().equals("")) rows1--;
			}
			while(lastAligned>0 && buffer1.size()>0) {
				buffer1.remove(0);
				lastAligned--;
			}
			lastAligned =  buffer2.size();
			while(rows2>0) {
				lastAligned--;
				if(!buffer2.get(lastAligned).replaceFirst("#.*","").trim().equals("")) rows2--;
			}
			while(lastAligned>0 &&  buffer2.size()>0) {
				 buffer2.remove(0);
				lastAligned--;
			}
			
			// fill buffers
			try {
				while(buffer1.size()<window) {
					buffer1.add(file1.readLine());
					buffer1Width=Math.max(buffer1Width,buffer1.get(buffer1.size()-1).split("\t").length);
					if(!keepComments && buffer1.get(buffer1.size()-1).trim().startsWith("#")) buffer1.remove(buffer1.size()-1); // comments interfere with alignment
				}
			} catch (IOException e) {}
			
			try {
				while(buffer2.size()<window) {
					buffer2.add(file2.readLine());
					buffer2Width=Math.max(buffer2Width,buffer2.get(buffer2.size()-1).split("\t").length);
					if(!keepComments && buffer2.get(buffer2.size()-1).trim().startsWith("#")) buffer2.remove(buffer2.size()-1); // comments interfere with alignment
				}
			} catch (IOException e) {}

		}
		
		// write unaligned tail
		while(buffer1!=null && buffer1.size()>0) {
			System.out.print(buffer1.get(0));
			if(!buffer1.get(0).replaceFirst("#.*","").trim().equals(""))
				for(int i = 0; i<buffer2Width-dropCols.size(); i++)
					System.out.print("\t?");
			System.out.println();
			buffer1.remove(0);
		}
		
		while(buffer2!=null && buffer2.size()>0) {
			if(!buffer2.get(0).replaceFirst("#.*","").trim().equals("")) {
				for(int i = 0; i<buffer1Width; i++)
					System.out.print("?\t");
				String[] fields = buffer2.get(0).split("\t");
				for(int i = 0; i<fields.length; i++) 
					if(!dropCols.contains(i)) {
						System.out.print(fields[i]);
						if(i<fields.length-1)
							System.out.print("\t");
					}
			} else
				System.out.print(buffer2.get(0));
			System.out.println();
			buffer2.remove(0);
		}
		
		file1.close();
		file2.close();
	}
}