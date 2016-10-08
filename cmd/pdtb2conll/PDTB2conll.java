import java.io.*;
import java.util.*;

public class PDTB2conll {

	private Vector<String> toks = new Vector<String>();
	private Vector<Integer> poss = new Vector<Integer>();
	private Vector<String> annos = new Vector<String>();
	private int annoCount = 0;

	void process(String buffer) {
			annoCount++;
			String[] lines = buffer.split("\n");
			String type="";
			if(lines.length>2) type = lines[0].replaceAll("_","");
			//System.err.println(type);
			for(int i=0; i<lines.length; i++)
				if(lines[i].contains("#### Text ####") && i>=2 && i+1<lines.length) {
					String text = lines[i+1].trim();
					int pos = Integer.parseInt(lines[i-2].replaceFirst("[^0-9].*",""));
					String anno = "";
					if(i>=3) anno=(annoCount+":")+lines[i-3].replaceAll("_","");
					anno=anno+" ("+type;
					if(type.matches(".*plicit|AltLex")) {
						int j=0;
						while(!lines[j].matches(".*(Sup|Arg)1.*") && j<lines.length) j++;
						if(j<lines.length) 
							anno=anno+" "+lines[j-1];
					}
					anno=anno+")";
					if(lines[i-2].contains(";")) { // insert spaces for unknown tokens, these won't be written
						while(lines[i-2].contains(";")) {
							int start=Integer.parseInt(lines[i-2].replaceFirst("[^0-9].*",""));
							int end1=Integer.parseInt(lines[i-2].replaceFirst(";.*","").replaceAll(".*[^0-9]",""));
							int start2=Integer.parseInt(lines[i-2].replaceFirst("^.*;","").replaceAll("[^0-9].*",""));
							String remainder=lines[i-2].replaceFirst("^.*;","").replaceAll("^[0-9]*","");
							while(end1<start2-1) {
								text=text.substring(0,end1-start)+" "+text.substring(end1-start);
								end1++;
							}
							lines[i-2]=start+remainder;
						}
					}
					for(String tok : text.split("\\s")) {
						int offset=tok.length();
						if(!tok.trim().equals("")) {	// inserted spaces for missing text parts
							if(poss.contains(pos)) {
								annos.setElementAt(annos.get(poss.indexOf(pos))+"; "+anno, poss.indexOf(pos)); // also when warnings
								// sample check confirmed that these are mostly adjacent tokens
								if(!toks.get(poss.indexOf(pos)).equals(tok) && 		// strict check fails because punctuation might not be split
									!toks.get(poss.indexOf(pos)).startsWith(tok) && // loose check might produce incomplete text or duplicate lines for punctuation
									!tok.startsWith(toks.get(poss.indexOf(pos)))) {
									System.err.println("warning: trying to overwrite token \""+toks.get(poss.indexOf(pos))+"\" with \""+tok+"\" at "+pos);
									anno=anno+" <"+tok+" >";
								}
							} else {
								try {
									int j = 0;
									while(poss.get(j)<pos)
										j++;
									poss.insertElementAt(pos,j);
									toks.insertElementAt(tok,j);
									annos.insertElementAt(anno,j);
								} catch (ArrayIndexOutOfBoundsException e) {
									poss.add(pos);
									toks.add(tok);
									annos.add(anno);
								}
							}
						}
						pos=pos+1+offset;
					}
			}
	}
	
	void read(Reader reader) throws IOException {
		BufferedReader in = new BufferedReader(reader);
		String buffer = "";
		for(String line="";line!=null; line=in.readLine()) {
			buffer=(buffer+line).trim()+"\n";
			if(line.equals("________________________________________________________")) {
				if(!buffer.replaceAll("_","").trim().equals(""))
					process(buffer);
				buffer="";
			}
		}
		process(buffer);
	}
	
	void write(Writer out) throws IOException {
		int lastPos = 0;
		for(int i = 0; i<toks.size(); i++) {
			int pos=poss.get(i);
			String tok = toks.get(i);
			if(lastPos<pos) System.out.println("[...]");
			System.out.println(tok+"\t"+pos+".."+(pos+tok.length()+1)+"\t"+annos.get(i));
			lastPos=pos+tok.length()+1;
		}
		System.out.println("[...]");
	}

	public static void main(String[] argv) throws Exception {
		System.err.println(
			"PDTB2conll\nreads PDTB file from stdin and produces conll-alike column format\n"+
			"note that the text may be incomplete, as the original PDTB file does not contain the full text"
		);
		PDTB2conll me = new PDTB2conll();
		me.read(new InputStreamReader(System.in));
		me.write(new OutputStreamWriter(System.out));
	}
}