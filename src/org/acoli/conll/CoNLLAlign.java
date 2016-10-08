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
public class CoNLLAlign {

	final private List<String[]> conll1;
	final private List<String[]> conll2;
	final private List<String> forms1;
	final private List<String> forms2;
	final private List<Delta> deltas;
	
	final private int col1;
	final private int col2;
	
	public CoNLLAlign(File file1, File file2, int col1, int col2) throws IOException {
		conll1=read(file1);
		conll2=read(file2);
		this.col1=col1;
		this.col2=col2;
		forms1 = getCol(conll1,col1);
		forms2 = getCol(conll2,col2);		
		deltas = DiffUtils.diff(forms1, forms2).getDeltas();		
	}
	
	/** merge CoNLL files by splitting tokens into maximal common subtokens:
	    instead of enforcing one tokenization over another, split both tokenizations to minimal common strings and add this as a new first column
	    to the output<br/>
		annotations are split according to IOBES (this may lead to nested IOBES prefixes that should be post-processed) */
	public void split(Writer out, Set<Integer> dropCols) throws IOException {
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
			} else if (delta.getOriginal().size()*delta.getRevised().size()==1) { 		// 1:1 replacements
				left.add(conll1.get(i++));
				right.add(conll2.get(j++));
				d++;
			} else { 																	// n:m replacements
				d++;																	// (1) iterated character-level diff between chars1 and chars2
				List<String> chars1 = new Vector<String>();
				List<String> chars2 = new Vector<String>();
				List<Integer> chars2conll1 = new Vector<Integer>();
				List<Integer> chars2conll2 = new Vector<Integer>();
				
				// left.add(new String[] { "# "+delta});			// DEBUG
				// right.add(null);
				
				for(int o=0; o<delta.getOriginal().size(); o++) {
					for(String c : forms1.get(i).replaceAll("(.)","$1\t").trim().split("\t")) {
						chars1.add(c);
						chars2conll1.add(i);
					}
					i++;
				}
				
				for(int r = 0; r<delta.getRevised().size(); r++) {
					for(String c : forms2.get(j).replaceAll("(.)","$1\t").trim().split("\t")) {
						chars2.add(c);
						chars2conll2.add(j);
					}
					j++;
				}
				
				List<Delta> cDeltas = DiffUtils.diff(chars1, chars2).getDeltas();	// (2) aggregate into maximal common subtokens

				// left.add(new String[] { "# "+chars1 });					// DEBUG
				// right.add(new String[] {chars2.toString()});
				// left.add(new String[] { "# "+chars2conll1 });
				// right.add(new String[] {chars2conll2.toString()});				
				// left.add(new String[]{ "# cDeltas: "+cDeltas.toString() });
				// right.add(null);
				
				int ci=0;
				int cj=0;
				int cd=0;
				
				while(ci<chars1.size() || cj<chars2.size()) {							// use I(O)BE(S) encoding to represent split annotations 
					
					// build left and right
					Delta cDelta = null;
					if(cd<cDeltas.size()) cDelta = cDeltas.get(cd);
					
					if(cDelta==null || cDelta.getOriginal().getPosition()>ci) {					// no change => append to last subtoken or create a new one
						if(ci>0 && cj>0 && chars2conll1.get(ci-1).equals(chars2conll1.get(ci)) && chars2conll2.get(cj-1).equals(chars2conll2.get(cj)) && left.get(left.size()-1)!=null && right.get(right.size()-1)!=null) {
							left.get(left.size()-1)[col1]=left.get(left.size()-1)[col1]+chars1.get(ci++);
							if(right.get(right.size()-1).length>col2) right.get(right.size()-1)[col2]=right.get(right.size()-1)[col2]+chars2.get(cj++);
							
							// IOBE(S)
							if(ci==chars2conll1.size()-1) 
								for(int f = 0; f<left.get(left.size()-1).length; f++)
									if(f!=col1)
										left.get(left.size()-1)[f]=left.get(left.size()-1)[f].replaceFirst("^B-","").replaceFirst("^I-","E-");
							if(cj==chars2conll2.size()-1) 
								for(int f = 0; f<right.get(right.size()-1).length; f++)
									if(f!=col2)
										right.get(right.size()-1)[f]=right.get(right.size()-1)[f].replaceFirst("^B-","").replaceFirst("^I-","E-");

						} else { // new subtoken
							left.add(Arrays.copyOf(conll1.get(chars2conll1.get(ci)),conll1.get(chars2conll1.get(ci)).length));
							if(left.get(left.size()-1).length>col1) left.get(left.size()-1)[col1]=chars1.get(ci);
							right.add(Arrays.copyOf(conll2.get(chars2conll2.get(cj)),conll2.get(chars2conll2.get(cj)).length));
							if(right.get(right.size()-1).length>0) right.get(right.size()-1)[col2]=chars2.get(cj);
							
							// IOBE(S) left
							String iobes="I-";
							if(ci==0 || !chars2conll1.get(ci).equals(chars2conll1.get(ci-1)))
								iobes="B-";
							if(ci==chars2conll1.size()-1) iobes=iobes.replaceFirst("^I-","E-").replaceFirst("^B-","");
							for(int f = 0; f<left.get(left.size()-1).length; f++)
								if(f!=col1)
									left.get(left.size()-1)[f]=(iobes+left.get(left.size()-1)[f]);							
							if(ci>0 && !chars2conll1.get(ci).equals(chars2conll1.get(ci-1)) && left.size()>2 && left.get(left.size()-2)!=null) {
								for(int f = 0; f<left.get(left.size()-2).length; f++)
									if(f!=col1)
										left.get(left.size()-2)[f]=left.get(left.size()-2)[f].replaceFirst("^B-","").replaceFirst("^I-","E-");
							}

							// IOBE(S) right
							iobes="I-";
							if(cj==0 || !chars2conll2.get(cj).equals(chars2conll2.get(cj-1)))
								iobes="B-";
							if(cj==chars2conll2.size()-1) iobes=iobes.replaceFirst("^I-","E-").replaceFirst("^B-","");
							for(int f = 0; f<right.get(right.size()-1).length; f++)
								if(f!=col2)
									right.get(right.size()-1)[f]=(iobes+right.get(right.size()-1)[f]);							
							if(cj>0 && !chars2conll2.get(cj).equals(chars2conll2.get(cj-1)) && right.size()>2 && right.get(right.size()-2)!=null) {
								for(int f = 0; f<right.get(right.size()-2).length; f++)
									if(f!=col2)
										right.get(right.size()-2)[f]=right.get(right.size()-2)[f].replaceFirst("^B-","").replaceFirst("^I-","E-");
							}
							
							ci++;
							cj++;
							
						}
					} else if (cDelta.getOriginal().size()*cDelta.getRevised().size()==1) { 		// 1:1 replacements => append to last subtoken or create a new one
						if(ci>0 && cj>0 && chars2conll1.get(ci-1).equals(chars2conll1.get(ci)) && chars2conll2.get(cj-1).equals(chars2conll2.get(cj))) {
							left.get(left.size()-1)[col1]=left.get(left.size()-1)[col1]+chars1.get(ci);
							right.get(right.size()-1)[col2]=right.get(right.size()-1)[col2]+chars2.get(cj);
							
							// IOBE(S)
							if(ci==chars2conll1.size()-1) 
								for(int f = 0; f<left.get(left.size()-1).length; f++)
									if(f!=col1)
										left.get(left.size()-1)[f]=left.get(left.size()-1)[f].replaceFirst("^B-","").replaceFirst("^I-","E-");
							if(cj==chars2conll2.size()-1) 
								for(int f = 0; f<right.get(right.size()-1).length; f++)
									if(f!=col2)
										right.get(right.size()-1)[f]=right.get(right.size()-1)[f].replaceFirst("^B-","").replaceFirst("^I-","E-");
									
							ci++;
							cj++;
						} else { // new subtoken
							left.add(Arrays.copyOf(conll1.get(chars2conll1.get(ci)),conll1.get(chars2conll1.get(ci)).length));
							if(left.get(left.size()-1).length>col1) left.get(left.size()-1)[col1]=chars1.get(ci);
							right.add(Arrays.copyOf(conll2.get(chars2conll2.get(cj)),conll2.get(chars2conll2.get(cj)).length));
							if(right.get(right.size()-1).length>0) right.get(right.size()-1)[col2]=chars2.get(cj);
							
							// IOBE(S) left
							String iobes="I-";
							if(ci==0 || !chars2conll1.get(ci).equals(chars2conll1.get(ci-1)))
								iobes="B-";
							if(ci==chars2conll1.size()-1) iobes=iobes.replaceFirst("^I-","E-").replaceFirst("^B-","");
							for(int f = 0; f<left.get(left.size()-1).length; f++)
								if(f!=col1)
									left.get(left.size()-1)[f]=(iobes+left.get(left.size()-1)[f]);							
							if(ci>0 && !chars2conll1.get(ci).equals(chars2conll1.get(ci-1)) && left.size()>2 && left.get(left.size()-2)!=null) {
								for(int f = 0; f<left.get(left.size()-2).length; f++)
									if(f!=col1)
										left.get(left.size()-2)[f]=left.get(left.size()-2)[f].replaceFirst("^B-","").replaceFirst("^I-","E-");
							}

							// IOBE(S) right
							iobes="I-";
							if(cj==0 || !chars2conll2.get(cj).equals(chars2conll2.get(cj-1)))
								iobes="B-";
							if(cj==chars2conll2.size()-1) iobes=iobes.replaceFirst("^I-","E-").replaceFirst("^B-","");
							for(int f = 0; f<right.get(right.size()-1).length; f++)
								if(f!=col2)
									right.get(right.size()-1)[f]=(iobes+right.get(right.size()-1)[f]);							
							if(cj>0 && !chars2conll2.get(cj).equals(chars2conll2.get(cj-1)) && right.size()>2 && right.get(right.size()-2)!=null) {
								for(int f = 0; f<right.get(right.size()-2).length; f++)
									if(f!=col2)
										right.get(right.size()-2)[f]=right.get(right.size()-2)[f].replaceFirst("^B-","").replaceFirst("^I-","E-");
							}							

							ci++;
							cj++;
						}
						cd++;
					} else {																	// n:m replacements
						cd++;
						for(int o=0; o<cDelta.getOriginal().size(); o++) {
							if(ci>0 && right.get(right.size()-1)==null && chars2conll1.get(ci-1).equals(chars2conll1.get(ci))) {
								left.get(left.size()-1)[col1]=left.get(left.size()-1)[col1]+chars1.get(ci);		// append to last stok
								
								// IOBE(S)
								if(ci==chars2conll1.size()-1) 
									for(int f = 0; f<left.get(left.size()-1).length; f++)
										if(f!=col1)
											left.get(left.size()-1)[f]=left.get(left.size()-1)[f].replaceFirst("^B-","").replaceFirst("^I-","E-");
							
								ci++;									
							} else {																	// create new stok
								String[] array = Arrays.copyOf(conll1.get(chars2conll1.get(ci)),conll1.get(chars2conll1.get(ci)).length);
								if(array.length==0) {
									array=new String[col1+1];
									Arrays.fill(array,"");	
								}
								array[col1]=chars1.get(ci);
								left.add(array);
								
								// IOBE(S) left
								String iobes="I-";
								if(ci==0 || !chars2conll1.get(ci).equals(chars2conll1.get(ci-1)))
									iobes="B-";
								if(ci==chars2conll1.size()-1) iobes=iobes.replaceFirst("^I-","E-").replaceFirst("^B-","");
								for(int f = 0; f<left.get(left.size()-1).length; f++)
									if(f!=col1)
										left.get(left.size()-1)[f]=(iobes+left.get(left.size()-1)[f]);							
								if(ci>0 && !chars2conll1.get(ci).equals(chars2conll1.get(ci-1)) && left.size()>2 && left.get(left.size()-2)!=null) {
									for(int f = 0; f<left.get(left.size()-2).length; f++)
										if(f!=col1)
											left.get(left.size()-2)[f]=left.get(left.size()-2)[f].replaceFirst("^B-","").replaceFirst("^I-","E-");
								}
								
								ci++;
								right.add(null);
							}
						}
						
						for(int r = 0; r<cDelta.getRevised().size(); r++) {
							if(cj>0 && left.get(left.size()-1)==null && chars2conll2.get(cj-1).equals(chars2conll2.get(cj))) {
								right.get(right.size()-1)[col2]=right.get(right.size()-1)[col2]+chars2.get(cj);		// append to last stok

								// IOBE(S)
								if(cj==chars2conll2.size()-1) 
									for(int f = 0; f<right.get(right.size()-1).length; f++)
										if(f!=col2)
											right.get(right.size()-1)[f]=right.get(right.size()-1)[f].replaceFirst("^B-","").replaceFirst("^I-","E-");

								cj++;
								
							} else {
								String[] array = Arrays.copyOf(conll2.get(chars2conll2.get(cj)),conll2.get(chars2conll2.get(cj)).length);
								if(array.length==0) {
									array=new String[col2+1];
									Arrays.fill(array,"");	
								}
								array[col2]=chars2.get(cj);
								right.add(array);
								
								// IOBE(S) right
								String iobes="I-";
								if(cj==0 || !chars2conll2.get(cj).equals(chars2conll2.get(cj-1)))
									iobes="B-";
								if(cj==chars2conll2.size()-1) iobes=iobes.replaceFirst("^I-","E-").replaceFirst("^B-","");
								for(int f = 0; f<right.get(right.size()-1).length; f++)
									if(f!=col2)
										right.get(right.size()-1)[f]=(iobes+right.get(right.size()-1)[f]);							
								if(cj>0 && !chars2conll2.get(cj).equals(chars2conll2.get(cj-1)) && right.size()>2 && right.get(right.size()-2)!=null) {
									for(int f = 0; f<right.get(right.size()-2).length; f++)
										if(f!=col2)
											right.get(right.size()-2)[f]=right.get(right.size()-2)[f].replaceFirst("^B-","").replaceFirst("^I-","E-");
								}
								
								cj++;
								left.add(null);
							}
						}
					}
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
						if((right.get(line)!=null && right.get(line).length>0 && !right.get(line)[0].trim().startsWith("#")) || (left.get(line)!=null && left.get(line).length>0 && !left.get(line)[0].trim().startsWith("#")))
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

	/** simplify IOBES annotations in merged lines produced by prune() */
	protected static String simplifyIOBES(String line) {
		while(line.matches(".*\t[IB]-([^+]*)\\+I-\\1.*"))
			line=line.replaceAll("\t([IB)-([^+]*)\\+I-\\2","\t$1-$2");
		while(line.matches(".*\tB-([^+]*)\\+E-\\1.*"))
			line=line.replaceAll("\tB-([^+]*)\\+E-\\1","\t$1");
		while(line.matches(".*\tI-([^+]*)\\+E-\\1.*"))
			line=line.replaceAll("\tI-([^+]*)\\+E-\\1","\tE-$1");
		return line;
	}
	
	/** run merge() or split(), remove all comments, merge *RETOK*-... tokens with preceding token (or following, if no preceding found)<br>
		Note: this is lossy for n:m matches, e.g.
		 <code>
		 a       DT      (NP (NP *       DT      (NP (NP *
		 19-month        JJ      *       ?       ?
		 cease-fire      NN      *)      CD+HYPH+NN+NN+HYPH+NN   (NML *)+*)
		</code>
		
		However, it will keep index references intact (better: provide an ID column)
	*/
	public void prune(Writer out, boolean useMerge, Set<Integer> dropCols) throws IOException {
		StringWriter merged = new StringWriter();
		if(useMerge) merge(merged,dropCols);
		else split(merged,dropCols);
		
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
								if(last[i].equals("?")) last[i]=fields[i];
								else if(!fields[i].equals("?")) last[i]=(last[i]+"+"+fields[i]).replaceAll("\\*\\+\\*","*");
							}
						lastLine="";
						for(String s : last)
							lastLine=lastLine+s+"\t";
						lastLine=lastLine.replaceFirst("\t$","");
					}
				} else if(last[col1].startsWith("*RETOK*-")) {	// only if sentence initial
						for(int i = 0; i<last.length; i++)
							if(i!=col1 && i<fields.length) {
								if(fields[i].equals("?")) fields[i]=last[i];
								else if(!last[i].equals("?")) fields[i]=(last[i]+"+"+fields[i]).replaceAll("\\*\\+\\*","*");
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
	
	/** merge two CoNLL files and adopt the tokenization of the first<br/>
		tokenization mismatches from the second are represented by "empty" PTB words prefixed with *RETOK*-... */
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
							if(right.get(line)!=null && right.get(line).length>0 && !right.get(line)[0].trim().startsWith("#"))
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
						if((right.get(line)!=null && right.get(line).length>0 && !right.get(line)[0].trim().startsWith("#")) || (left.get(line)!=null && left.get(line).length>0 && !left.get(line)[0].trim().startsWith("#")))
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
		System.err.println("synopsis: CoNLLAlign FILE1.tsv FILE2.tsv [COL1 COL2] [-f] [-split] [-drop none | -drop COLx..z]\n"+
			"\tFILEi.tsv tab-separated text files, e.g. CoNLL format\n"+
			"\tCOLi      column number to be used for the alignment,\n"+
			"\t          defaults to 0 (first)\n"+
			"\t-f        forced merge: mismatching FILE2 tokens are merged with last FILE1 token (lossy)\n"+
			"\t          suppresses *RETOK* nodes, thus keeping the token sequence intact\n"+
			"\t-split    by default, the tokenization of the first file is adopted for the output\n"+
			"\t          with this flag, split tokens from both files into longest common subtokens\n"+
			"\t-drop     drop specified FILE2 columns, by default, this includes COL2\n"+
			"\t          default behavior can be suppressed by defining another set of columns\n"+
			"\t          or -drop none\n"+
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
		
		CoNLLAlign me = new CoNLLAlign(new File(argv[0]), new File(argv[1]),col1,col2);
		if(force) {
			me.prune(new OutputStreamWriter(System.out), !split, dropCols);
		} else if(split) {
			me.split(new OutputStreamWriter(System.out), dropCols);
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
				result.add(line.replaceAll("[\t ]*$","").split(" *\t+ *"));
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