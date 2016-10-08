import java.io.*;
import java.util.*;

public class PDGB2conll {

	public static void main(String[] argv) throws Exception {
		System.err.println("PDGB2conll TXT ANNO [-xml]\n"+
		"\tTXT  PDGB-segmented text file\n"+
		"\tANNO PDGB relation annotation\n"+
		"write CoNLL to stdout");
		
		// coindexing
		Hashtable<Integer,Hashtable<String,Integer>> src2rel2id = new Hashtable<Integer,Hashtable<String,Integer>>();
		Hashtable<Integer,Hashtable<String,Integer>> tgt2rel2id = new Hashtable<Integer,Hashtable<String,Integer>>();
		
		System.err.print("read "+argv[1]+" ..");
		BufferedReader in = new BufferedReader(new FileReader(argv[1]));
		int relid=0;
		for(String line =""; line!=null; line=in.readLine()) 
			if(!line.trim().equals("")) {
				String fields[] = line.split(" ");
				try {
					for(int seg = Integer.parseInt(fields[0]); seg <= Integer.parseInt(fields[1]); seg++) {
						if(src2rel2id.get(seg)==null) src2rel2id.put(seg,new Hashtable<String,Integer>());
						src2rel2id.get(seg).put(fields[4], relid);
					}
					for(int seg = Integer.parseInt(fields[2]); seg <= Integer.parseInt(fields[3]); seg++) {
						if(tgt2rel2id.get(seg)==null) tgt2rel2id.put(seg,new Hashtable<String,Integer>());
						tgt2rel2id.get(seg).put(fields[4], relid);
					}
				} catch (Exception e) {
					e.printStackTrace();
					System.err.println("while processing annotation \""+line+"\"");
				}
				relid++;
				System.err.print(".");
			}
		System.err.println(". ok");
		in.close();
		
		System.err.print("process "+argv[0]+" .");
		in = new BufferedReader(new FileReader(argv[0]));
		int seg = 0;
		String lastLine = null;
		for(String line=""; line!=null; line=in.readLine()) {
			if(line.equals("")) {
				if(lastLine!=null)
					System.out.println("<P>");				// RST-alike pseudo markup
			} else {
				String anno = "";
				if(src2rel2id.get(seg)!=null)
					for(String rel : src2rel2id.get(seg).keySet())
						anno=anno.trim()+"\t"+src2rel2id.get(seg).get(rel)+":"+"Arg2:"+rel;
				if(tgt2rel2id.get(seg)!=null)
					for(String rel : tgt2rel2id.get(seg).keySet())
						anno=anno.trim()+"\t"+tgt2rel2id.get(seg).get(rel)+":"+"Arg1:"+rel;
				anno=anno.replaceAll("\t",";").trim();
				if(anno.equals("")) anno="_";
				for(String tok : tokenize(line))
					System.out.println(tok+"\t"+anno);
				System.out.println();
				seg++;
			}
		}
		in.close();
		System.err.println(". done");
	}
		
	/** heuristic, coarse-grained tokenization, will overgenerate */
	public static String[] tokenize(String str) {
		return str.replaceAll("\\s+"," ").replaceAll("([^a-zA-Z0-9]+)"," $1 ").replaceAll(" +"," ").trim().split(" ");
	}
}