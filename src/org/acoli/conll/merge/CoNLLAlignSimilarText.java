package org.acoli.conll.merge;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Set;
import java.util.Vector;

import difflib.Delta;


/** adaptation of CoNLLAlign when working with massively restructured text, e.g., compiling
	editions of multiple manuscript versions of a medieval tale
	Here, identical strings are an exception rather than a rule, so after performing regular CoNLLAlign,
	we use the established segments and add new links with the most similar unaligned string, using relative 
	Levenshtein distance
*/
public class CoNLLAlignSimilarText extends CoNLLAlign {

	public CoNLLAlignSimilarText(File file1, File file2, int col1, int col2) throws IOException {
		super(file1, file2, col1, col2);
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
	
	/** given two CoNLL files, perform token-level merge, 
	 *  adopt the tokenization of the first (no subtokens, here)<br/>
	 *  inserted words (tokenization mismatches) from the second are represented by "empty" PTB words prefixed with *RETOK*-...<br/>
	 *  treatment of annotations follows CoNLLAlign<br/>
	 *  when identity matching fails, apply Levenshtein distance with greedy decoding, no crossing	
	 **/
	public void merge(Writer out, Set<Integer> dropCols, boolean force) throws IOException {
		int i = 0;
		int j = 0;
		int d = 0;

		Writer myOut = out;
		if(force)
			myOut = new StringWriter();

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
				left.add(conll1.get(i++));
				right.add(null);
				for(int r = 0; r<delta.getRevised().size(); r++) {
					left.add(null);
					right.add(conll2.get(j++));
				}
				d++;
			} else if(delta!=null && delta.getOriginal().getPosition()==i && delta.getOriginal().size()==1 && 
				conll1.get(i).length>0 && conll1.get(i)[0].trim().startsWith("#") && delta.getType().equals(Delta.TYPE.CHANGE)) {
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

				/** BEGIN levenshtein modification **/
				
				// perform Levenshtein-based alignment among deltas
				int[][] levMatrix = new int[delta.getOriginal().size()][delta.getRevised().size()];
				double[][] relMatrix = new double[delta.getOriginal().size()][delta.getRevised().size()];;
				
				//DEBUG
				/* for(int o=0; o<delta.getOriginal().size(); o++)
					System.err.print(forms1.get(i+o)+"\t");
				System.err.println();
				for(int r = 0; r<delta.getRevised().size(); r++)
					System.err.print(forms2.get(j+r)+"\t");
				System.err.println();
				*/
				
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
			}
			
			/** END levenshtein modification **/
			
			// write left and right if at sentence break or at end
			if(i>=conll1.size() || j>=conll2.size() || (forms1.get(i).trim().equals("") && forms2.get(j).trim().equals(""))) {
				
				write(left,right,dropCols,myOut);
				left.clear();
				right.clear();
			}
		}
		if(force)
			prune(out,new StringReader(myOut.toString()));
	}

	/** greedy non-crossing alignment decoding for a distance matrix: 
	 * 	- find the alignment pair with minimal distance 
	 *    (if equal, with larger Math.min(weights1,weights2), if equal, with larger Math.max(weights1, weights2), if equal, using geometric alignment)
	 *  - eliminate crossing alignment candidates
	 *  - iterate for the remaining rectangles */
	protected double[][] decode(double[][] relMatrix, int[] weights1, int[] weights2) {
		if(relMatrix.length<1 || relMatrix[0].length<1) return relMatrix;
		
		
		// DEBUG
		/* System.err.println("decode("+relMatrix.length+"x"+relMatrix[0].length+", "+weights1.length+", "+weights2.length+")");

		System.err.println("decode() in ");
		for(int i = 0; i<relMatrix.length; i++) {
			for(int j = 0; j<relMatrix[i].length; j++)
				System.err.print(relMatrix[i][j]+"\t");
			System.err.println();
		}*/
		
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

		// DEBUG
		// System.err.println("max "+maxI+" "+maxJ);
		
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
		
		// DEBUG
		/* System.err.println("decode() out ");
		for(int i = 0; i<relMatrix.length; i++) {
			for(int j = 0; j<relMatrix[i].length; j++)
				System.err.print(relMatrix[i][j]+"\t");
			System.err.println();
		}*/
		
		return relMatrix;
	}
	
	/** calls CoNLLAlign with additional flag -split, if -drop isn't specified, it is set to none (different default) */
	public static void main(String[] argv) throws Exception {
		Vector<String> args = new Vector<String>(Arrays.asList(argv));
		boolean inserted = false;
		for(int i = 0; i<args.size() && !inserted; i++) {
			if(args.get(i).toLowerCase().startsWith("-drop")) {
				args.insertElementAt("-lev", i);
				inserted=true;
			}
		}
		if(!inserted) {
			args.add("-split");
			args.add("-drop");
			args.add("none");
		}
		CoNLLAlign.main(args.toArray(new String[args.size()]));
	}
}