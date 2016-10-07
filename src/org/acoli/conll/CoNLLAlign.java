package org.acoli.conll;

import java.io.*;
import java.util.*;
import difflib.*;


/** Runs Myers Diff on the FORM column of two CoNLL files to establish an alignment
	1:1 alignment => append second line to the first
	n:0 alignment => fill up missing columns with ?
	0:m alignment => create new empty elements *RETOK*-<FORM>
	Keeps the tokenization of the first file. Tokenization mismatches are reduced to insertions and deletions.
	*RETOK*: alternative tokenization can be restored
	however, adding empty elements may interfere with word offsets used for dependency annotation =>
	optionally forced pruning: annotations of *RETOK* appended to last regular token, concatenated by + (lossy)
	alternatively, an explicit word ID column may be used
	
	Can be applied to non-CoNLL data, e.g. XML standoff or NIF, if sequentially ordered tokens are assigned their XML or NIF identifiers 
	as CoNLL annotation

	cf. Eugene W. Myers (1986). "An O(ND) Difference Algorithm and Its Variations". Algorithmica: November 1986
	http://xmailserver.org/diff2.pdf (implementation from java diff utils, https://github.com/dnaumenko/java-diff-utils by Dmitry Naumenko)
	
	for improved readability of the output, columns of the second file may be dropped. In particular, the second FORM column is dropped
*/ 
public class merged.New {

	final private List<String[]> conll1;
	final private List<String[]> conll2;
	final private List<String> forms1;
	final private List<String> forms2;
	final private List<Delta> deltas;
	
	final private int col1;
	final private int col2;
	
	public merged.New(File file1, File file2, int col1, int col2) throws IOException {
		conll1=read(file1);
		conll2=read(file2);
		this.col1=col1;
		this.col2=col2;
		forms1 = getCol(conll1,col1);
		forms2 = getCol(conll2,col2);		
		deltas = DiffUtils.diff(forms1, forms2).getDeltas();		
	}
	
	/** run merge(), remove all comments, merge *RETOK*-... tokens with preceding token (or following, if no preceding found)<br>
		Note: this is lossy for n:m matches, e.g.
		 <code>
		 a       DT      (NP (NP *       DT      (NP (NP *
		 19-month        JJ      *       ?       ?
		 cease-fire      NN      *)      CD+HYPH+NN+NN+HYPH+NN   (NML *)+*)
		</code>
		
		However, it will keep index references intact (better: provide an ID column)
	*/
	public void mergeAndPrune(Writer out, Set<Integer> dropCols) throws IOException {
		StringWriter merged = new StringWriter();
		merge(merged,dropCols);
		
		// strategy: merge *RETOK*s with last token; if none available, merge with the next token
		BufferedReader in = new BufferedReader(new StringReader(merged.toString()));
		String lastLine = "";
		for(String line = in.readLine(); line!=null; line=in.readLine()) {
			if(line.trim().equals("")) {
				out.write(lastLine+"\n");
				lastLine=line;
			} else if(!line.trim().startsWith("#")) {
				String fields[] = line.split(" *\t+ *");
				String last[] = lastLine.split(" *\t+ *");
				if(fields[col1].startsWith("*RETOK*-")) {
					if(lastLine.trim().equals("")) {
						lastLine=line;
					} else if(last.length!=fields.length) {	// shouldn't happen
						out.write(lastLine+"\n");
						out.write("# last: "+last.length+" "+Arrays.asList(last)+" line: "+fields.length+" "+Arrays.asList(fields)+"\n");
						lastLine=line;
					} else {
						for(int i = 0; i<last.length; i++) 
							if(i!=col1) {
								if(last[i].equals("?")) last[i]=fields[i];
								else if(!fields[i].equals("?")) last[i]=(last[i]+"+"+fields[i]).replaceAll("\\*\\+\\*","*");
							}
						lastLine="";
						for(String s : last)
							lastLine=lastLine+s+"\t";
						lastLine=lastLine.replaceFirst("\t$","");
					}
				} else if(last[col1].startsWith("*RETOK*-")) {	// only if sentence initial
						if(last.length!=fields.length) {		// shouldn't happen
							out.write(lastLine+"\n");
							out.write("# last: "+last.length+" "+Arrays.asList(last)+" line: "+fields.length+" "+Arrays.asList(fields)+"\n");
							lastLine=line;
						} else {
							for(int i = 0; i<last.length; i++)
								if(i!=col1) {
									if(fields[i].equals("?")) fields[i]=last[i];
									else if(!last[i].equals("?")) fields[i]=(last[i]+"+"+fields[i]).replaceAll("\\*\\+\\*","*");
								}
						}
						lastLine="";
						for(String s : fields)
							lastLine=lastLine+s+"\t";
						lastLine=lastLine.replaceFirst("\t$","");
				} else {
					out.write(lastLine+"\n");
					lastLine=line;
					out.flush();
				}
			}
		}
		out.write(lastLine+"\n");
		out.flush();
	}
	
	public void merge(Writer out, Set<Integer> dropCols) throws IOException {
		int i = 0;
		int j = 0;
		int d = 0;

		List<String[]> left = new Vector<String[]>();
		List<String[]> right = new Vector<String[]>();
		while(i<conll1.size() && j<conll2.size()) {

			// build left and right
			Delta delta = null;
			if(d<deltas.size()) delta = deltas.get(d);
			
			// if(delta!=null && delta.getOriginal().getPosition()==i) { 		// debug
				// left.add(new String[] { "# "+delta });
				// right.add(null);
			// }
			
			if(delta==null || delta.getOriginal().getPosition()>i) {					// no change
				left.add(conll1.get(i++));
				right.add(conll2.get(j++));
				// out.write(Arrays.asList(conll1.get(i++))+"\t|\t"+Arrays.asList(conll2.get(j++))+"\n");
			} else if (delta.getOriginal().size()*delta.getRevised().size()==1) { 		// 1:1 replacements
				left.add(conll1.get(i++));
				right.add(conll2.get(j++));
				// out.write(Arrays.asList(conll1.get(i++))+"\t|\t"+Arrays.asList(conll2.get(j++))+"\n");
				d++;
			} else { 																	// n:m replacements
				d++;
				for(int o=0; o<delta.getOriginal().size(); o++) {
					left.add(conll1.get(i++));
					right.add(null);
					// out.write(">"+Arrays.asList(conll1.get(i++))+"\n");
				}
				for(int r = 0; r<delta.getRevised().size(); r++) {
					left.add(null);
					right.add(conll2.get(j++));
					//out.write("<"+Arrays.asList(conll2.get(j++))+"\n");
				}
			}
			
			// write left and right if at sentence break or at end
			if(i>=conll1.size() || j>=conll2.size() || (forms1.get(i).trim().equals("") && forms2.get(j).trim().equals(""))) {
				int leftLength = 0;  for(String[] l : left)  if(l!=null && l.length>leftLength)  leftLength=l.length;
				int rightLength = 0; for(String[] l : right) if(l!=null && l.length>rightLength) rightLength=l.length;
				for(int line = 0; line<left.size(); line++) {
					
					// keep empty lines if one on the left
					if((left.get(line)!=null && left.get(line).length==1 && left.get(line)[0].trim().equals("")) && (right.get(line)==null || (right.get(line).length==1 && right.get(line)[0].trim().equals("")))) {
						out.write("\n");
					} else if(left.get(line)==null && right.get(line).length==1 && right.get(line)[0].trim().equals("")) {
						// nothing (insertions of empty lines from the right)
					} else {
						
						// write left side
						if(left.get(line)==null) {
							if(right.get(line)!=null && !right.get(line)[0].trim().startsWith("#"))
								for(int col=0; col<leftLength; col++)
									if(col==col1) out.write("*RETOK*-"+right.get(line)[col2]+"\t"); 
									else out.write("?\t");
						} else {
							int col = 0;
							while(col<left.get(line).length)
								out.write(left.get(line)[col++]+"\t");
							while(col<leftLength) {
								out.write("?\t");
								col++;
							}
						}
						
						// write right side
						int col=0;
						if(right.get(line)!=null) {
							while(col<right.get(line).length) {
								if(!dropCols.contains(col)) out.write(right.get(line)[col]+"\t");
								col++;
							}
						}
						if((right.get(line)!=null && !right.get(line)[0].trim().startsWith("#")) || (left.get(line)!=null && !left.get(line)[0].trim().startsWith("#")))
							while(col<rightLength) {
								if(!dropCols.contains(col)) out.write("?\t");
								col++;
							}
						out.write("\n");
					}
					out.flush();
				}
				left.clear();
				right.clear();
			}
		}
	}

	public static void main(String[] argv) throws Exception {
		System.err.println("synopsis: merged. FILE1.tsv FILE2.tsv [COL1 COL2] [-f] [-drop COLx..z]\n"+
			"\tFILEi.tsv tab-separated text files, e.g. CoNLL format\n"+
			"\tCOLi      column number to be used for the alignment,\n"+
			"\t          defaults to 0 (first)\n"+
			"\t-f        force mismatching FILE2 token to be merged with last FILE1 token (lossy)\n"+
			"\t          suppresses *RETOK* nodes to keep the token sequence intact\n"+
			"\t-drop     drop specified FILE2 columns, by default, this includes COL2\n"+
			"extract the contents of the specified column, run diff\n"+
			"and integrate the content of FILE1 and FILE2 on that basis\n"+
			"(similar to sdiff, but optimized for CoNLL)");
		
		int col1 = 0;
		int col2 = 0;
		try {
			col1 = Integer.parseInt(argv[2]);
			col2 = Integer.parseInt(argv[3]);
		} catch (Exception e) {};
	
		boolean force = Arrays.asList(argv).toString().toLowerCase().matches(".*[\\[,] *-f[,\\]].*");
		
		HashSet<Integer> dropCols = new HashSet<Integer>();
		dropCols.add(col2);
		int i = 0;
		while(i<argv.length && !argv[i].toLowerCase().equals("-drop"))
			i++;
		while(i<argv.length) {
			try {
				dropCols.add(Integer.parseInt(argv[i++]));
			} catch (NumberFormatException e) {}
		}
		
		merged.New me = new merged.New(new File(argv[0]), new File(argv[1]),col1,col2);
		if(force) {
			me.mergeAndPrune(new OutputStreamWriter(System.out), dropCols);
		} else 
			me.merge(new OutputStreamWriter(System.out),dropCols);
	}
	
	List<String[]> read(File file) throws IOException {
		List<String[]> result = new ArrayList<String[]>();
		BufferedReader in = new BufferedReader(new FileReader(file));
		for(String line=in.readLine(); line!=null; line=in.readLine()) {
			if(line.trim().startsWith("#")) 
				result.add(new String[]{ line });
			else 
				result.add(line.split(" *\t+ *"));
		}
		return result;
	}
	
	List<String> getCol(List<String[]> conll, int col) {
		List<String> result = new ArrayList<String>();
		for(int i = 0; i<conll.size(); i++)
			if(conll.get(i).length==0) result.add(""); else result.add(conll.get(i)[col]);
		return result;
	}
	
}