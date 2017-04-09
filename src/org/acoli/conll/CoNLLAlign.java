package org.acoli.conll;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import difflib.Delta;
import difflib.DiffUtils;

/** skeleton for alignment routines for CoNLL files, based on Myer's Diff without modifications<br/>
 * specialized sub-routines for treating alignment failures (n:m matches) are implemented in subclasses and 
 * can be enabled via flags of main() */
public class CoNLLAlign {

	protected final Vector<String[]> conll1;
	final protected Vector<String[]> conll2;
	protected final List<String> forms1;
	protected final List<String> forms2;
	protected final List<Delta> deltas;
	protected final int col1;
	protected final int col2;
	
	public CoNLLAlign(File file1, File file2, int col1, int col2) throws IOException {
		conll1=read(file1);
		conll2=read(file2);
		this.col1=col1;
		this.col2=col2;
		forms1 = getCol(conll1,col1);
		forms2 = getCol(conll2,col2);		
		deltas = DiffUtils.diff(forms1, forms2).getDeltas();		
	}

	Vector<String[]> read(File file) throws IOException {
		Vector<String[]> result = new Vector<String[]>();
		BufferedReader in = new BufferedReader(new FileReader(file));
		for(String line=in.readLine(); line!=null; line=in.readLine()) {
			if(line.trim().startsWith("#")) 
				result.add(new String[]{ line });
			else 
				result.add(line.replaceAll("[\t ]*$","").split(" *\t+ *"));
		}
		in.close();
		return result;
	}

	List<String> getCol(Vector<String[]> conll, int col) {
		List<String> result = new ArrayList<String>();
		for(int i = 0; i<conll.size(); i++)
			if(conll.get(i).length==0) result.add(""); else result.add(conll.get(i)[col]);
		return result;
	}
	
	/** given two CoNLL files, perform token-level merge using Myer's diff, adopt the tokenization of the first <br/>
	 * tokenization mismatches from the second are represented by "empty" PTB words prefixed with *RETOK*-...
	 */
	void merge(Writer out, Set<Integer> dropCols) throws IOException {
		int i = 0;
		int j = 0;
		int d = 0;
		
		boolean debug=false;
	
		Vector<String[]> left = new Vector<String[]>();
		Vector<String[]> right = new Vector<String[]>();
		while(i<conll1.size() && j<conll2.size()) {
	
			// build left and right
			Delta delta = null;
			if(d<deltas.size()) delta = deltas.get(d);
			
			if(debug) {
				if(delta!=null && delta.getOriginal().getPosition()==i) { 		// DEBUG
					left.add(new String[] { "# "+delta });
					right.add(null);
				}
			}
			
			if(delta!=null && delta.getOriginal().getPosition()==i && delta.getOriginal().size()==1 && 
				conll1.get(i).length==1 && conll1.get(i)[0].trim().equals("") && delta.getType().equals(Delta.TYPE.CHANGE)) {
				// left.add(new String[] { "# override empty line replacement"});
				// right.add(null);
				left.add(conll1.get(i++));
				right.add(null);
				for(int r = 0; r<delta.getRevised().size(); r++) {
					left.add(null);
					right.add(conll2.get(j++));
				}
				d++;
			} else if(delta!=null && delta.getOriginal().getPosition()==i && delta.getOriginal().size()==1 && 
				conll1.get(i).length>0 && conll1.get(i)[0].trim().startsWith("#") && delta.getType().equals(Delta.TYPE.CHANGE)) {
				// left.add(new String[] { "# override comment replacement"});
				// right.add(null);
				left.add(conll1.get(i++));
				right.add(null);
				for(int r = 0; r<delta.getRevised().size(); r++) {
					left.add(null);
					right.add(conll2.get(j++));
				}
				d++;
			} else if(delta==null || delta.getOriginal().getPosition()>i) {					// no change
				left.add(conll1.get(i++));
				right.add(conll2.get(j++));
			} else if (delta.getOriginal().size()*delta.getRevised().size()==1) { 		// 1:1 replacements
				left.add(conll1.get(i++));
				if(conll2.get(j)!=null && conll2.get(j).length==1 && conll2.get(j)[0].trim().startsWith("#")) {			// keep right-side comments
					right.add(null);
					left.add(conll2.get(j));
					right.add(null);
				} else if(conll2.get(j)!=null && conll2.get(j).length > col2 && !conll2.get(j)[col2].equals(""))		// drop empty lines on the right
					right.add(conll2.get(j));
				else right.add(null);
				j++;
				d++;
			} else { 														// n:m replacements
				d++;														// just written one after another, with *RETOK*-...
				
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
				
				write(left,right,dropCols,out);
				left.clear();
				right.clear();
			}
		}
	}
	
	/** internally called by merge() <br/>
    note that in addition to filling up null lines with ?, it also attempts to restore IOBES annotations
	 */
	protected void write(Vector<String[]> left, Vector<String[]> right, Set<Integer> dropCols, Writer out) throws IOException {
			int leftLength = 0;  for(String[] l : left)  if(l!=null && l.length>leftLength)  leftLength=l.length;
			int rightLength = 0; for(String[] l : right) if(l!=null && l.length>rightLength) rightLength=l.length;
			
			for(int line = 0; line<left.size(); line++) {
				
				// DEBUG
				// out.write("# ");
				// if(left.get(line)==null) out.write("null"); else out.write(Arrays.asList(left.get(line)).toString());
				// out.write(" and ");
				// if(right.get(line)==null) out.write("null"); else out.write(Arrays.asList(right.get(line)).toString());
				// out.write("\n");
				
				// keep empty lines if one on the left
				if((( left.get(line)!=null && (left.get(line).length==0 || left.get(line).length==1 && left.get(line)[0].trim().equals("")) )) && (right.get(line)==null || right.get(line).length==0 || (right.get(line).length==1 && right.get(line)[0].trim().equals("")))) {
					out.write("\n");
				} else if((( left.get(line)!=null && (left.get(line).length==0 || left.get(line).length==1 && left.get(line)[0].trim().equals("")) )) && ((right.get(line).length==1 && col2==0 && !right.get(line)[0].trim().equals("")))) {
					out.write("\n");
					out.write("# "+right.get(line)[0]+"\n");		// this is a misalignment, keep original token as comment
				} else if(left.get(line)==null && right.get(line).length==1 && right.get(line)[0].trim().equals("")) {
					// nothing (insertions of empty lines from the right)
				} else {
					
					// write left side
					for(int col = 0; col<leftLength; col++) {
						String lastValue = null;									
						for(int a = line-1; a>=0 && lastValue==null; a--)
							if(left.get(a)!=null && left.get(a).length>col) lastValue=left.get(a)[col];
						String nextValue = null;
						for(int a = line+1; a<left.size() && nextValue==null; a++)
							if(left.get(a)!=null && left.get(a).length>col) nextValue=left.get(a)[col];

						if((left.get(line)==null) ||
						   col>=left.get(line).length) {
							   if(right.get(line)==null || !right.get(line)[0].trim().startsWith("#")) {
									if(col==col1 && right.get(line)!=null && right.get(line).length>col2)
										out.write("*RETOK*-"+right.get(line)[col2]); 
									else if(lastValue!=null && lastValue.matches("^[BI]-.*") && nextValue!=null && 
									   nextValue.substring(0,1).matches("^[IE]") && nextValue.substring(1).equals(lastValue.replaceFirst("^.",""))) {
										out.write("I"+lastValue.replaceFirst("^.",""));								// IOBES inference/repair
									} else {
										out.write("?");								// default (no IOBES inference)
									};
							   }
						   } else {
							   out.write(left.get(line)[col]);
						   }
						if(left.get(line)!=null || !right.get(line)[0].trim().startsWith("#")) out.write("\t");
					}
					
					// write right side
					int col=0;
					if(right.get(line)!=null) {
						while(col<right.get(line).length) {
							if(!dropCols.contains(col)) { 
								out.write(right.get(line)[col]);
								if(col<rightLength-1) out.write("\t");
							}
							col++;
						}
					}
					if((right.get(line)!=null && right.get(line).length>0 && !right.get(line)[0].trim().startsWith("#")) || (left.get(line)!=null && left.get(line).length>0 && !left.get(line)[0].trim().startsWith("#")))
						while(col<rightLength) {
							if(!dropCols.contains(col)) {
								
								String lastValue = null;									
								for(int a = line-1; a>=0 && lastValue==null; a--)
									if(right.get(a)!=null && right.get(a).length>col) lastValue=right.get(a)[col];										
								String nextValue = null;
								for(int a = line+1; a<right.size() && nextValue==null; a++)
									if(right.get(a)!=null && right.get(a).length>col) nextValue=right.get(a)[col];										
								if(lastValue!=null && lastValue.matches("^[BI]-.*") && nextValue!=null && 
								   nextValue.substring(0,1).matches("^[IE]") && nextValue.substring(1).equals(lastValue.replaceFirst("^.",""))) {
									out.write("I"+lastValue.replaceFirst("^.",""));									// IOBES inference/repair
								} else {
									out.write("?");									// default (no IOBES inference)
								};

								if(col<rightLength-1) out.write("\t");
							}
							col++;
						}
					out.write("\n");
				}
				out.flush();
			}
	}
	
	/** run merge() or split(), remove all comments, merge *RETOK*-... tokens with preceding token (or following, if no preceding found)<br>
	Note: this is lossy for n:m matches, e.g.
	 <code>
	 a       DT      (NP (NP *       DT      (NP (NP *
	 19-month        JJ      *       ?       ?
	 cease-fire      NN      *)      CD+HYPH+NN+NN+HYPH+NN   (NML *)+*)
	</code>
	
	However, it will keep index references intact (better: provide an ID column)<br/>
	
	when merging multiple lines, we replace the (non-first) * placeholder of *RETOK* lines with the original FORM		
	 */
	public void prune(Writer out, Set<Integer> dropCols) throws IOException {
		StringWriter merged = new StringWriter();
		this.merge(merged,dropCols);
		
		// strategy: merge *RETOK*s with last token; if none available, merge with the next token
		BufferedReader in = new BufferedReader(new StringReader(merged.toString()));
		String lastLine = "";
		for(String line = in.readLine(); line!=null; line=in.readLine()) {
			if(line.trim().equals("")) {
				out.write(simplifyIOBES(lastLine)+"\n");
				lastLine=line;
			} else if(!line.trim().startsWith("#")) {
				String fields[] = line.split(" *\t+ *");
				String last[] = lastLine.split(" *\t+ *");
				if(fields[col1].startsWith("*RETOK*-")) {
					if(lastLine.trim().equals("")) {
						lastLine=line;
					} else {
						for(int i = 0; i<last.length; i++) 
							if(i!=col1 && i<fields.length) {
								if(fields[i].contains("*"))
									fields[i]=fields[i].substring(0,fields[i].indexOf("*"))+fields[col1]+fields[i].substring(fields[i].indexOf("*")+1);
								if(last[i].equals("?")) last[i]=fields[i];
								else if(!fields[i].equals("?"))
									last[i]= last[i]+"+"+fields[i];
							}
						lastLine="";
						for(String s : last)
							lastLine=lastLine+s+"\t";
						lastLine=lastLine.replaceFirst("\t$","");
					}
				} else if(last[col1].startsWith("*RETOK*-")) {	// only if sentence initial
						for(int i = 0; i<last.length; i++)
							if(i!=col1 && i<fields.length) {
								if(last[i].contains("*"))
									last[i]=last[i].substring(0,last[i].indexOf("*"))+last[col1]+last[i].substring(last[i].indexOf("*")+1);
								if(fields[i].equals("?")) 
									fields[i]=last[i];
								else if(!last[i].equals("?"))
									fields[i]=last[i]+"+"+fields[i];
							}
						lastLine="";
						for(String s : fields)
							lastLine=lastLine+s+"\t";
						lastLine=lastLine.replaceFirst("\t$","");
				} else {
					out.write(simplifyIOBES(lastLine)+"\n");
					lastLine=line;
					out.flush();
				}
			}
		}
		out.write(simplifyIOBES(lastLine)+"\n");
		out.flush();
	}

	/** simplify IOBES annotations before merging lines in prune() */
	protected static String simplifyIOBES(String line) {
		try {
			while(line.matches(".*\t[IB]-([^+]*)\\+I-\\1.*"))
				line=line.replaceAll("\t([IB])-([^+]*)\\+I-\\2","\t$1-$2");
			while(line.matches(".*\tB-([^+]*)\\+E-\\1.*"))
				line=line.replaceAll("\tB-([^+]*)\\+E-\\1","\t$1");
			while(line.matches(".*\tI-([^+]*)\\+E-\\1.*"))
				line=line.replaceAll("\tI-([^+]*)\\+E-\\1","\tE-$1");
		} catch (java.util.regex.PatternSyntaxException e) {
			e.printStackTrace();
			System.err.println("while processing \""+line+"\"");
		}
		return line;
	}
	
	public static void main(String[] argv) throws Exception {
		if(!Arrays.asList(argv).toString().toLowerCase().matches(".*[\\[,] *-silent[,\\]].*")) {
			System.err.println("synopsis: CoNLLAlign FILE1.tsv FILE2.tsv [COL1 COL2] [-silent] [-f] [-split] [-lev] [-drop none | -drop COLx..z]\n"+
				"extract the contents of the specified column, run diff\n"+
				"and integrate the content of FILE1 and FILE2 on that basis\n"+
				"(similar to sdiff, but optimized for CoNLL)");
			if(argv.length==0) System.err.println("\tFILEi.tsv tab-separated text files, e.g. CoNLL format\n"+
				"\tCOLi      column number to be used for the alignment,\n"+
				"\t          defaults to 0 (first)\n"+
				"\t-silent   suppress synopsis\n"+
				"\t-f        forced merge: mismatching FILE2 tokens are merged with last FILE1 token (lossy)\n"+
				"\t          suppresses *RETOK* nodes, thus keeping the token sequence intact\n"+
				"\t-split    by default, the tokenization of the first file is adopted for the output\n"+
				"\t          with this flag, split tokens from both files into longest common subtokens\n"+
				"\t-lev      use relative Levenshtein distance with greedy decoding to resolve n:m matches\n"+
				"\t          mutually exclusive with -split, should only be used when aligning text that is\n"+
				"\t          not identical, but rather, similar, e.g., different editions of the same text\n"+
				"\t-drop     drop specified FILE2 columns, by default, this includes COL2\n"+
				"\t          default behavior can be suppressed by defining another set of columns\n"+
				"\t          or -drop none");
		}
		
		int col1 = 0;
		int col2 = 0;
		try {
			col1 = Integer.parseInt(argv[2]);
			col2 = Integer.parseInt(argv[3]);
		} catch (Exception e) {};
	
		boolean force = Arrays.asList(argv).toString().toLowerCase().matches(".*[\\[,] *-f[,\\]].*");
		boolean split = Arrays.asList(argv).toString().toLowerCase().matches(".*[\\[,] *-split[,\\]].*");
		boolean lev = Arrays.asList(argv).toString().toLowerCase().matches(".*[\\[,] *-lev[enshti]*[,\\]].*");

		if(split && lev) {
			System.err.println("warning: flags -lev and -split should not be combined, dropping -split");
			split=false;
		}
		
		HashSet<Integer> dropCols = new HashSet<Integer>();
		if(!Arrays.asList(argv).toString().toLowerCase().matches(".*[\\[,] *-drop[,\\]].*"))
			dropCols.add(col2);
		
		int i = 0;
		while(i<argv.length && !argv[i].toLowerCase().equals("-drop"))
			i++;
		while(i<argv.length) {
			try {
				dropCols.add(Integer.parseInt(argv[i++]));
			} catch (NumberFormatException e) {}
		}
		
		CoNLLAlign me;
		
		if(split) {
			me = new CoNLLAlignSubTok(new File(argv[0]), new File(argv[1]),col1,col2);
		} else if(lev){
			me = new CoNLLAlignSimilarText(new File(argv[0]), new File(argv[1]),col1,col2);
		} else {
			me = new CoNLLAlign(new File(argv[0]), new File(argv[1]),col1,col2);
		}
		
		if(force) {
			me.prune(new OutputStreamWriter(System.out), dropCols);
		} else {
			me.merge(new OutputStreamWriter(System.out), dropCols);
		}
	}
}
