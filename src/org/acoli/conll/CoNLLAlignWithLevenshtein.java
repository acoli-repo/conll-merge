package org.acoli.conll;

import java.io.*;
import java.util.*;
import difflib.*;


/** adaptation of CoNLLAlign when working with massively restructured text, e.g., compiling
	editions of multiple manuscript versions of a medieval tale
	Here, identical strings are an exception rather than a rule, so after performing regular CoNLLAlign,
	we use the established segments and add new links with the most similar unaligned string

	Runs Myers Diff on the FORM column of two CoNLL files to establish an alignment
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
	
	for improved readability of the output, columns of the second file may be dropped. In particular, the second FORM column is dropped<br/>
	
	@TODO: 
	- don't add columns to comments
	- consolidate IOBES repair routines
	- integrate prune() into write()?
*/ 
public class CoNLLAlignWithLevenshtein extends CoNLLAlign {

	public CoNLLAlignWithLevenshtein(File file1, File file2, int col1, int col2) throws IOException {
		super(file1, file2, col1, col2);
	}

	/** shorthand for merge with token level alignment */	
	public void merge(Writer out, Set<Integer> dropCols) throws IOException {
		merge(out,dropCols, false);
	}
	
	/** calculate Levenshtein distance, cf. https://en.wikibooks.org/wiki/Algorithm_Implementation/Strings/Levenshtein_distance#Java, CC-BY-SA */
	public int levenshteinDistance (CharSequence lhs, CharSequence rhs) {                          
		int len0 = lhs.length() + 1;                                                     
		int len1 = rhs.length() + 1;                                                     
																						
		// the array of distances                                                       
		int[] cost = new int[len0];                                                     
		int[] newcost = new int[len0];                                                  
																						
		// initial cost of skipping prefix in String s0                                 
		for (int i = 0; i < len0; i++) cost[i] = i;                                     
																						
		// dynamically computing the array of distances                                  
																						
		// transformation cost for each letter in s1                                    
		for (int j = 1; j < len1; j++) {                                                
			// initial cost of skipping prefix in String s1                             
			newcost[0] = j;                                                             
																						
			// transformation cost for each letter in s0                                
			for(int i = 1; i < len0; i++) {                                             
				// matching current letters in both strings                             
				int match = (lhs.charAt(i - 1) == rhs.charAt(j - 1)) ? 0 : 1;             
																						
				// computing cost for each transformation                               
				int cost_replace = cost[i - 1] + match;                                 
				int cost_insert  = cost[i] + 1;                                         
				int cost_delete  = newcost[i - 1] + 1;                                  
																						
				// keep minimum cost                                                    
				newcost[i] = Math.min(Math.min(cost_insert, cost_delete), cost_replace);
			}                                                                           
																						
			// swap cost/newcost arrays                                                 
			int[] swap = cost; cost = newcost; newcost = swap;                          
		}                                                                         
                                                                                    
		// the distance is the cost for transforming all letters in both strings        
		return cost[len0 - 1];                                                          
	}		
	
	/** given two CoNLL files, perform token-level merge, no subtokens, here
	
		boolean split<br/>
		<ul>
		<li> if split=false, then merge two CoNLL files and adopt the tokenization of the first<br/>
		tokenization mismatches from the second are represented by "empty" PTB words prefixed with *RETOK*-...
		</li>
		<li>
		if split=true, then merge CoNLL files by splitting tokens into maximal common subtokens:
	    instead of enforcing one tokenization over another, split both tokenizations to minimal common strings and add this as a new first column
	    to the output<br/>
		annotations are split according to IOBES (this may lead to nested IOBES prefixes that should be post-processed)
		</li>
		</ul>		*/
	void merge(Writer out, Set<Integer> dropCols, boolean split) throws IOException {
		int i = 0;
		int j = 0;
		int d = 0;
		
		if(split) System.err.println("split operation not supported");
		
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
			} else { 																	// n:m replacements
				d++;																	

				// perform Levenshtein-based alignment among deltas
				int[][] levMatrix = new int[delta.getOriginal().size()][delta.getRevised().size()];
				double[][] relMatrix = new double[delta.getOriginal().size()][delta.getRevised().size()];;
				
				for(int o=0; o<delta.getOriginal().size(); o++)
					System.err.print(forms1.get(i+o)+"\t");
				System.err.println();
				for(int r = 0; r<delta.getRevised().size(); r++)
					System.err.print(forms2.get(j+r)+"\t");
				System.err.println();
				
				for(int o=0; o<delta.getOriginal().size(); o++)
					for(int r = 0; r<delta.getRevised().size(); r++) {
						String form1 = forms1.get(i+o);
						String form2 = forms2.get(j+r);
						int lev = levenshteinDistance(form1,form2);
						levMatrix[o][r] = lev;
						relMatrix[o][r] = ((double)lev)/((double)Math.min(form1.length(), form2.length()));
					}
				
				int[] weights1 = new int[delta.getOriginal().size()];
				int[] weights2 = new int[delta.getRevised().size()];

				relMatrix = decode(relMatrix, weights1, weights2);
				
				int[] o2r = new int[delta.getOriginal().size()];
				for(int o = 0; o<relMatrix.length; o++) {
					o2r[o] = -1;
					for(int r = 0; r<relMatrix[o].length; r++)
						if(relMatrix[o][r]>0.0)
							o2r[o] = r;
				}
				int[] r2o = new int[delta.getRevised().size()];
				for(int r = 0; r<delta.getRevised().size(); r++) {
					r2o[r] = -1;
					for(int o = 0; o<relMatrix.length; o++)
						if(relMatrix[o][r]>0.0)
							r2o[r]=o;
				}
				int o = 0;
				int r = 0;
				while(o<o2r.length && r<r2o.length) {
					while(o2r[o]>r) { // && conll2.size()>j) {
						left.add(null);
						right.add(conll2.get(j++));
						r++;
					}
					while(r2o.length>r && r2o[r]>o) { // && conll1.size()>i) {
						right.add(null);
						left.add(conll1.get(i++));
						o++;
					}
					//if(conll1.size()>i && conll2.size()>j) 
					{
						left.add(conll1.get(i++));
						right.add(conll2.get(j++));
					}
					r++;
					o++;
				}
				while(r<delta.getRevised().size()) {
					left.add(null);
					right.add(conll2.get(j++));
					r++;
				}
				while(o<relMatrix.length) {
					right.add(null);
					left.add(conll1.get(i++));
					o++;
				}

				
				
				//if(!split) 
/*				{															// (A) regular token-level merge
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
				}*/ 
					
			}
			
			// write left and right if at sentence break or at end
			if(i>=conll1.size() || j>=conll2.size() || (forms1.get(i).trim().equals("") && forms2.get(j).trim().equals(""))) {
				
				if(split) {
					left=undoIOBES4syntax(left);
					right=undoIOBES4syntax(right);
					
					left=repairIOBES(left);
					right=repairIOBES(right);
				}
				
				write(left,right,dropCols,out);
				left.clear();
				right.clear();
			}
		}
	}

	/** greedy non-crossing alignment decoding for a distance matrix: 
	 * 	- find the alignment pair with minimal distance 
	 *    (if equal, with larger Math.min(weights1,weights2), if equal, with larger Math.max(weights1, weights2), if equal, using geometric alignment)
	 *  - eliminate crossing alignment candidates
	 *  - iterate for the remaining rectangles */
	protected double[][] decode(double[][] relMatrix, int[] weights1, int[] weights2) {
		if(relMatrix.length<1 || relMatrix[0].length<1) return relMatrix;
		
		
		System.err.println("decode("+relMatrix.length+"x"+relMatrix[0].length+", "+weights1.length+", "+weights2.length+")");

		System.err.println("decode() in ");
		for(int i = 0; i<relMatrix.length; i++) {
			for(int j = 0; j<relMatrix[i].length; j++)
				System.err.print(relMatrix[i][j]+"\t");
			System.err.println();
		}
		
		int maxI = 0;
		int maxJ = 0;
		
		// find maximum alignment
		for(int i = 0; i<relMatrix.length; i++)
			for(int j = 0;j<relMatrix[i].length; j++) {
				if( relMatrix[i][j]<relMatrix[maxI][maxJ]
				|| (relMatrix[i][j]==relMatrix[maxI][maxJ] && Math.min(weights1[i],weights2[j]) > Math.min(weights1[maxI],weights2[maxJ]))
				|| (relMatrix[i][j]==relMatrix[maxI][maxJ] && Math.min(weights1[i],weights2[j]) == Math.min(weights1[maxI],weights2[maxJ]) 
				                                           && Math.max(weights1[i],weights2[j]) > Math.max(weights1[maxI],weights2[maxJ]))
				|| (relMatrix[i][j]==relMatrix[maxI][maxJ] && Math.min(weights1[i],weights2[j]) == Math.min(weights1[maxI],weights2[maxJ]) 
				                                           && Math.max(weights1[i],weights2[j]) == Math.max(weights1[maxI],weights2[maxJ]) 
				                                           && Math.abs(((double)i)/(double)relMatrix.length - ((double)j)/(double)relMatrix[0].length) < Math.abs(((double)maxI)/(double)relMatrix.length - ((double)maxJ)/(double)relMatrix[0].length))) {
					maxI=i;
					maxJ=j;
				}
			}

		System.err.println("max "+maxI+" "+maxJ);
		
		// remove crossing edges
		for(int i = 0; i<relMatrix.length; i++)
			for(int j = 0; j<relMatrix[i].length; j++)
				if((i<maxI && j>maxJ) || (i>maxI && j<maxJ) || (i==maxI&& j!=maxJ) || (i!=maxI && j==maxJ))
					relMatrix[i][j]=0.0;
		
		// iterate on remaining rectangles
		if(maxI>0 && maxJ>0) {
			double[][] rectangle = new double[maxI][maxJ];
			for(int i = 0; i<maxI; i++)
				for(int j= 0; j<maxJ; j++)
					rectangle[i][j]=relMatrix[i][j];
			rectangle = decode(rectangle, weights1, weights2);
			for(int i = 0; i<maxI; i++)
				for(int j= 0; j<maxJ; j++)
					relMatrix[i][j]=rectangle[i][j];
		}
		if(maxI<relMatrix.length-1 && maxJ<relMatrix[maxI].length-1) {
			double[][] rectangle = new double[relMatrix.length-maxI-1][relMatrix[0].length-maxJ-1];
			for(int i = maxI+1; i<relMatrix.length; i++) 
				for(int j= maxJ+1; j<relMatrix[i].length; j++)
					rectangle[i-maxI-1][j-maxJ-1]=relMatrix[i][j];
			rectangle = decode(rectangle, 
					Arrays.copyOfRange(weights1, maxI+1, weights1.length), 
					Arrays.copyOfRange(weights2, maxJ+1, weights2.length));
			for(int i = maxI+1; i<relMatrix.length; i++) 
				for(int j= maxJ+1; j<relMatrix[i].length; j++)
					relMatrix[i][j]=rectangle[i-maxI-1][j-maxJ-1];
 		}
		
		System.err.println("decode() out ");
		for(int i = 0; i<relMatrix.length; i++) {
			for(int j = 0; j<relMatrix[i].length; j++)
				System.err.print(relMatrix[i][j]+"\t");
			System.err.println();
		}
		
		return relMatrix;
	}

	public static void main(String[] argv) throws Exception {
		if(!Arrays.asList(argv).toString().toLowerCase().matches(".*[\\[,] *-silent[,\\]].*")) {
			System.err.println("synopsis: CoNLLAlignWithLevenshtein FILE1.tsv FILE2.tsv [COL1 COL2] [-silent] [-f] [-drop none | -drop COLx..z]\n"+
				"extract the contents of the specified column, run diff\n"+
				"and integrate the content of FILE1 and FILE2 on that basis\n"+
				"(similar to sdiff, but optimized for CoNLL)");
			if(argv.length==0) System.err.println("\tFILEi.tsv tab-separated text files, e.g. CoNLL format\n"+
				"\tCOLi      column number to be used for the alignment,\n"+
				"\t          defaults to 0 (first)\n"+
				"\t-silent   suppress synopsis\n"+
				"\t-f        forced merge: mismatching FILE2 tokens are merged with last FILE1 token (lossy)\n"+
				"\t          suppresses *RETOK* nodes, thus keeping the token sequence intact\n"+
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
		
		CoNLLAlignWithLevenshtein me = new CoNLLAlignWithLevenshtein(new File(argv[0]), new File(argv[1]),col1,col2);
		if(force) {
			me.prune(new OutputStreamWriter(System.out), !split, dropCols);
		} else if(split) {
			me.split(new OutputStreamWriter(System.out), dropCols);
		} else 
			me.merge(new OutputStreamWriter(System.out),dropCols);
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
		return result;
	}
	
	List<String> getCol(Vector<String[]> conll, int col) {
		List<String> result = new ArrayList<String>();
		for(int i = 0; i<conll.size(); i++)
			if(conll.get(i).length==0) result.add(""); else result.add(conll.get(i)[col]);
		return result;
	}
	
}