import java.io.*;
import java.util.*;
import org.xml.sax.*;
import javax.xml.xpath.*;
import org.w3c.dom.*;

public class SRL2Conll {
	public static void main(String[] argv) throws Exception {
		System.err.println("SRL2Conll SRL CoNLL [-rem-link]\n"+
			"read SRL data (as produced by normalize-propbank.sh+splitter.sh, etc.) from SRL, apply it to (PTB-)CoNLL files\n"+
			"both files have to refer to the same text\n"+
			"note that the CoNLL file *must* be a PTB (mrg) conll file incl. PTB parse, PTB empty elements, \n"+
			"original tokenization, with FORM/WORD not preceded by a numerical index\n");
		
		System.err.print("retrieve parse trees .."); // as XML nodes
		String doc = "";
		String lastLine = "";
		int sent = 0;
		int word = 0;
		BufferedReader in = new BufferedReader(new FileReader(argv[1]));
		for(String line = in.readLine(); line!=null; line=in.readLine()) {
			if(!line.trim().startsWith("#")) {
				if(line.trim().equals("") && !lastLine.trim().equals("")) { sent++; word=0; }
				if(!line.trim().equals("")) {
					// we assume parses to be in the last column
					String parse=line.replaceAll(".*\t","").replaceAll("[^\\)\\(\\*]","");
					parse=parse.replaceAll("\\*","<word sent=\""+sent+"\" nr=\""+word+"\"/>").replaceAll("\\(","<node>").replaceAll("\\)","</node>");
					doc=doc+parse;
					word++;
				}
			}
			lastLine=line;
		}
		int opennode = doc.replaceAll("<node>","%").replaceAll("[^%]","").length();
		int closenode = doc.replaceAll("</node>","%").replaceAll("[^%]","").length();
		if(opennode!=closenode) System.err.println("warning: PTB lisp format mismatch (recoverable error)");
		if(opennode<closenode) System.out.println("# warning: added "+(closenode-opennode)+" opening brackets in PTB parse");
		if(opennode>closenode) System.out.println("# warning: added "+(opennode-closenode)+" closing brackets in PTB parse");
		while(opennode<closenode) { doc="<node>"+doc; opennode++;}
		while(opennode>closenode) { doc=doc+"</node>"; closenode++; }
		doc="<doc>"+doc+"</doc>";
		in.close();
		System.err.println(". ok");
		
		XPath xpath = XPathFactory.newInstance().newXPath();
		Node root = (Node)xpath.evaluate("/*[1]", new InputSource(new StringReader(doc)), XPathConstants.NODE);
		
		System.err.print("read SRL ..");
		Hashtable<Integer,Hashtable<Integer,String>> sent2pred2anno = new Hashtable<Integer,Hashtable<Integer,String>>();
		Hashtable<Integer,Hashtable<Integer,Hashtable<Integer,String>>> sent2word2pred2anno = new Hashtable<Integer,Hashtable<Integer,Hashtable<Integer,String>>>();
			// pred is word id of first word of the predicate of the frame instance, we assume this to be unambiguous
		in = new BufferedReader(new FileReader(argv[0]));
		for(String line = ""; line!=null; line=in.readLine()) {
			line=line.trim();
			if(!line.equals("")) {
				String[] anno = line.split(" ");
				sent = Integer.parseInt(anno[0]);
				if(sent2word2pred2anno.get(sent)==null) sent2word2pred2anno.put(sent, new Hashtable<Integer,Hashtable<Integer,String>>());
				if(sent2pred2anno.get(sent)==null) sent2pred2anno.put(sent, new Hashtable<Integer,String>());
				int pred = Integer.parseInt(anno[1]);
				sent2pred2anno.get(sent).put(pred,anno[2]);
				
				
				//***START Completely remove LINK-PCR/SLC and replace them by actual ARG
				//***      e.g. 6:0-ARG1 0:2*6:0-LINK-PCR  --> 6:0-ARG1 0:2*6:0-ARG1
				if (argv.length > 2) if (argv[2].equals("-rem-link")) {
					for(int i = 3; i<anno.length; i++) {
						String role1=anno[i].replaceFirst("^[^\\-]*\\-","");
						if (role1.equals("LINK-PCR") || role1.equals("LINK-SLC")) {
							String searchString=anno[i].replaceFirst(".*\\*","").replaceFirst("\\-.*","");
							for(int j = 3; j<anno.length; j++) {
								String role2=anno[j].replaceFirst("^[^\\-]*\\-","");
								if (!role2.equals("LINK-PCR") && !role2.equals("LINK-SLC")
									&& anno[j].contains(searchString)) {
									anno[i] = anno[i].replaceFirst(role1, role2);
								}
							}
						}
					}
				}
				
				//***END 
				
				
				for(int i = 3; i<anno.length; i++) {
					String role=anno[i].replaceFirst("^[^\\-]*\\-","");
					String[] addresses=anno[i].replaceFirst("\\-.*","").split("[\\*,]"); // we annotate empty elements and discontinuous spans
					for(String address : addresses) {
						int start = Integer.parseInt(address.replaceFirst(":.*",""));	// def.: ID of the 1st terminal node in this argument
						int height = Integer.parseInt(address.replaceFirst(".*:",""));	// def.: height of this argument phrase from its 1st terminal node
						NodeList span;
						if(height==0) span=(NodeList)xpath.evaluate("//word[@sent=\""+sent+"\" and @nr=\""+start+"\"][1]/@nr", root, XPathConstants.NODESET);
						else span = (NodeList)xpath.evaluate("//word[@sent=\""+sent+"\" and @nr=\""+start+"\"][1]/ancestor::*["+height+"]//word/@nr", root, XPathConstants.NODESET);
						for(int n = 0; n<span.getLength(); n++) {
							word=Integer.parseInt(span.item(n).getNodeValue());
							if(sent2word2pred2anno.get(sent).get(word)==null) sent2word2pred2anno.get(sent).put(word, new Hashtable<Integer,String>());
							String role_old = sent2word2pred2anno.get(sent).get(word).get(pred);
							//prioritize other ARGs over LINK-PCR/SLC
							if (role_old == null) {
								sent2word2pred2anno.get(sent).get(word).put(pred,role);
							} else if (role_old.equals("LINK-PCR") || role_old.equals("LINK-SLC")) {
								sent2word2pred2anno.get(sent).get(word).put(pred,role);
							} else if (role.equals("LINK-PCR") || role.equals("LINK-SLC")) {
								//do nothing. do not overwrite existing ARGs
							}
							
							System.err.print(".");
						}
					}
				}
			}
		}
		in.close();
		System.err.println(". ok");
		
		System.err.print("process CoNLL ..");
		sent = 0;
		word = 0;
		in= new BufferedReader(new FileReader(argv[1]));
		lastLine = "";
		for(String line = in.readLine(); line!=null; line=in.readLine()) {
			if(line.trim().startsWith("#")) System.out.println(line);
			else {
				if(line.trim().equals("") && !lastLine.trim().equals("")) { sent++; word=0; }
				System.out.print(line);
				if(!line.trim().equals("")) {
					System.out.print("\t");
					try {
						System.out.print((sent2pred2anno.get(sent).get(word)+"").replaceAll("^null$","_"));
						System.err.print("!");
					} catch (Exception e) {
						System.out.print("_");
					}
					try {
						Vector<Integer> preds = new Vector<Integer>(sent2pred2anno.get(sent).keySet());
						Collections.sort(preds);
						for(Integer pred : preds) {
							System.out.print("\t");
							try {
								System.out.print((sent2word2pred2anno.get(sent).get(word).get(pred)+"").replaceAll("^null$","_"));
								System.err.print(".");
							} catch (Exception e) {
								System.out.print("_");
							}
						}
					} catch (NullPointerException e) {}
					word++;
				}
				System.out.println();
				lastLine=line;
			}
		}
		in.close();
		System.err.println(". ok");
	}
}
		