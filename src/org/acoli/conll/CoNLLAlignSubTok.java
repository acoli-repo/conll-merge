package org.acoli.conll;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import difflib.Delta;
import difflib.DiffUtils;


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
	
	for improved readability of the output, columns of the second file may be dropped. In particular, the second FORM column is dropped<br/>
	
	@TODO: 
	- don't add columns to comments
	- consolidate IOBES repair routines
	- integrate prune() into write()?
*/ 
public class CoNLLAlignSubTok extends CoNLLAlign {
	
	public CoNLLAlignSubTok(File file, File file2, int col1, int col2) throws IOException {
		super(file,file2,col1,col2);
	}

	/** given two CoNLL files, perform subtoken-level merge:
	 * merge CoNLL files by splitting tokens into maximal common subtokens:
	 * instead of enforcing one tokenization over another, split both tokenizations to minimal common strings and add this as a new first column
	 * to the output<br/>
	 * annotations are split according to IOBES (this may lead to nested IOBES prefixes that should be post-processed) */
	void merge(Writer out, Set<Integer> dropCols, boolean force) throws IOException {
		int i = 0;
		int j = 0;
		int d = 0;

		Writer myOut = out;
		if(force)
			myOut = new StringWriter();
		
		// boolean split=true;
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
				
				/** BEGIN subtoken modification **/

																				// sub-token merge				
					List<String> chars1 = new Vector<String>();								
					List<String> chars2 = new Vector<String>();
					List<Integer> chars2conll1 = new Vector<Integer>();
					List<Integer> chars2conll2 = new Vector<Integer>();
																							// prepare character-level diff between chars1 and chars2
					if(debug) {
						left.add(new String[] { "# "+delta});			// DEBUG
						right.add(null);
					}
					
					for(int o=0; o<delta.getOriginal().size(); o++) {
						if(forms1.get(i).trim().startsWith("#")) {							// exclude comments
							left.add(conll1.get(i));
							right.add(null);						
						} else
							for(String c : forms1.get(i).replaceAll("(.)","$1\t").trim().split("\t")) {
								chars1.add(c);
								chars2conll1.add(i);
							}
						i++;
					}
					
					for(int r = 0; r<delta.getRevised().size(); r++) {
						if(forms2.get(j).trim().startsWith("#")) {
							left.add(conll2.get(j));
							right.add(null);
						} else
							for(String c : forms2.get(j).replaceAll("(.)","$1\t").trim().split("\t")) {
								chars2.add(c);
								chars2conll2.add(j);
							}
						j++;
					}

					Vector<Vector<String[]>> updateLR = split(chars1,chars2,chars2conll1, chars2conll2);
					left.addAll(updateLR.get(0));
					right.addAll(updateLR.get(1));
					
					/** END subtoken modification **/
			}
			
			// write left and right if at sentence break or at end
			if(i>=conll1.size() || j>=conll2.size() || (forms1.get(i).trim().equals("") && forms2.get(j).trim().equals(""))) {
				
				/** BEGIN subtoken modification **/
				
					left=undoIOBES4syntax(left);
					right=undoIOBES4syntax(right);
					
					left=repairIOBES(left);
					right=repairIOBES(right);

				/** END subtoken modification **/

				write(left,right,dropCols,myOut);
				left.clear();
				right.clear();
			}
		}
		
		if(force)
			prune(out,new StringReader(myOut.toString()));
	}

				
	/** to be called with a character-wise alignment, return update vectors for left and right in split */
	protected Vector<Vector<String[]>> split(List<String> chars1, List<String> chars2, List<Integer> chars2conll1, List<Integer> chars2conll2) {
				Vector<String[]> left = new Vector<String[]>();						// (1) initialized with character sequences => Diff
				Vector<String[]> right = new Vector<String[]>();
			
				boolean debug=false;
		
				List<Delta> cDeltas = DiffUtils.diff(chars1, chars2).getDeltas();	// (2) aggregate into maximal common subtokens

				if(debug) {
					left.add(new String[] { "# "+chars1 });					// DEBUG
					right.add(new String[] {chars2.toString()});
					left.add(new String[] { "# "+chars2conll1 });
					right.add(new String[] {chars2conll2.toString()});				
					left.add(new String[]{ "# cDeltas: "+cDeltas.toString() });
					right.add(null);
				}
					
				
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
							if(ci>=chars2conll1.size()-1 && cj>=chars2conll2.size()-1) {
								for(int f = 0; f<left.get(left.size()-1).length; f++)
									if(f!=col1)
										left.get(left.size()-1)[f]=left.get(left.size()-1)[f].replaceFirst("^B-","S-").replaceFirst("^I-","E-");
								for(int f = 0; f<right.get(right.size()-1).length; f++)
									if(f!=col2)
										right.get(right.size()-1)[f]=right.get(right.size()-1)[f].replaceFirst("^B-","S-").replaceFirst("^I-","E-");
							}
						} else { // new subtoken
							left.add(Arrays.copyOf(conll1.get(chars2conll1.get(ci)),conll1.get(chars2conll1.get(ci)).length));
							if(left.get(left.size()-1).length>col1) left.get(left.size()-1)[col1]=chars1.get(ci);
							right.add(Arrays.copyOf(conll2.get(chars2conll2.get(cj)),conll2.get(chars2conll2.get(cj)).length));
							if(right.get(right.size()-1).length>0) right.get(right.size()-1)[col2]=chars2.get(cj);
							
							// IOBE(S) left
							String iobes="I-";
							if(ci==0 || !chars2conll1.get(ci).equals(chars2conll1.get(ci-1)))
								iobes="B-";
							if(ci==chars2conll1.size()-1) iobes=iobes.replaceFirst("^I-","E-").replaceFirst("^B-","S-");
							for(int f = 0; f<left.get(left.size()-1).length; f++)
								if(f!=col1)
									left.get(left.size()-1)[f]=(iobes+left.get(left.size()-1)[f]);							
							if(ci>0 && !chars2conll1.get(ci).equals(chars2conll1.get(ci-1)) && left.size()>2 && left.get(left.size()-2)!=null) {
								for(int f = 0; f<left.get(left.size()-2).length; f++)
									if(f!=col1)
										left.get(left.size()-2)[f]=left.get(left.size()-2)[f].replaceFirst("^B-","S-").replaceFirst("^I-","E-");
							}

							// IOBE(S) right
							iobes="I-";
							if(cj==0 || !chars2conll2.get(cj).equals(chars2conll2.get(cj-1)))
								iobes="B-";
							if(cj==chars2conll2.size()-1) iobes=iobes.replaceFirst("^I-","E-").replaceFirst("^B-","S-");
							for(int f = 0; f<right.get(right.size()-1).length; f++)
								if(f!=col2)
									right.get(right.size()-1)[f]=(iobes+right.get(right.size()-1)[f]);							
							if(cj>0 && !chars2conll2.get(cj).equals(chars2conll2.get(cj-1)) && right.size()>2 && right.get(right.size()-2)!=null) {
								for(int f = 0; f<right.get(right.size()-2).length; f++)
									if(f!=col2)
										right.get(right.size()-2)[f]=right.get(right.size()-2)[f].replaceFirst("^B-","S-").replaceFirst("^I-","E-");
							}
							
							ci++;
							cj++;
							
						}
					} else if (cDelta.getOriginal().size()*cDelta.getRevised().size()==1) { 		// 1:1 replacements => append to last subtoken or create a new one
						if(ci>0 && cj>0 && chars2conll1.get(ci-1).equals(chars2conll1.get(ci)) && chars2conll2.get(cj-1).equals(chars2conll2.get(cj))) {
							left.get(left.size()-1)[col1]=left.get(left.size()-1)[col1]+chars1.get(ci);
							right.get(right.size()-1)[col2]=right.get(right.size()-1)[col2]+chars2.get(cj);
							
							// IOBE(S)
							if(ci==chars2conll1.size() && cj==chars2conll2.size()) {
								for(int f = 0; f<left.get(left.size()-1).length; f++)
									if(f!=col1)
										left.get(left.size()-1)[f]=left.get(left.size()-1)[f].replaceFirst("^B-","S-").replaceFirst("^I-","E-");
								for(int f = 0; f<right.get(right.size()-1).length; f++)
									if(f!=col2)
										right.get(right.size()-1)[f]=right.get(right.size()-1)[f].replaceFirst("^B-","S-").replaceFirst("^I-","E-");
							}									
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
							if(ci==chars2conll1.size()-1) iobes=iobes.replaceFirst("^I-","E-").replaceFirst("^B-","S-");
							for(int f = 0; f<left.get(left.size()-1).length; f++)
								if(f!=col1)
									left.get(left.size()-1)[f]=(iobes+left.get(left.size()-1)[f]);							
							if(ci>0 && !chars2conll1.get(ci).equals(chars2conll1.get(ci-1)) && left.size()>2 && left.get(left.size()-2)!=null) {
								for(int f = 0; f<left.get(left.size()-2).length; f++)
									if(f!=col1)
										left.get(left.size()-2)[f]=left.get(left.size()-2)[f].replaceFirst("^B-","S-").replaceFirst("^I-","E-");
							}

							// IOBE(S) right
							iobes="I-";
							if(cj==0 || !chars2conll2.get(cj).equals(chars2conll2.get(cj-1)))
								iobes="B-";
							if(cj==chars2conll2.size()-1) iobes=iobes.replaceFirst("^I-","E-").replaceFirst("^B-","S-");
							for(int f = 0; f<right.get(right.size()-1).length; f++)
								if(f!=col2)
									right.get(right.size()-1)[f]=(iobes+right.get(right.size()-1)[f]);							
							if(cj>0 && !chars2conll2.get(cj).equals(chars2conll2.get(cj-1)) && right.size()>2 && right.get(right.size()-2)!=null) {
								for(int f = 0; f<right.get(right.size()-2).length; f++)
									if(f!=col2)
										right.get(right.size()-2)[f]=right.get(right.size()-2)[f].replaceFirst("^B-","S-").replaceFirst("^I-","E-");
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
											left.get(left.size()-1)[f]=left.get(left.size()-1)[f].replaceFirst("^B-","S-").replaceFirst("^I-","E-");
							
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
								if(ci==chars2conll1.size()-1) iobes=iobes.replaceFirst("^I-","E-").replaceFirst("^B-","S-");
								for(int f = 0; f<left.get(left.size()-1).length; f++)
									if(f!=col1)
										left.get(left.size()-1)[f]=(iobes+left.get(left.size()-1)[f]);							
								if(ci>0 && !chars2conll1.get(ci).equals(chars2conll1.get(ci-1)) && left.size()>2 && left.get(left.size()-2)!=null) {
									for(int f = 0; f<left.get(left.size()-2).length; f++)
										if(f!=col1)
											left.get(left.size()-2)[f]=left.get(left.size()-2)[f].replaceFirst("^B-","S-").replaceFirst("^I-","E-");
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
											right.get(right.size()-1)[f]=right.get(right.size()-1)[f].replaceFirst("^B-","S-").replaceFirst("^I-","E-");

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
								if(cj==chars2conll2.size()-1) iobes=iobes.replaceFirst("^I-","E-").replaceFirst("^B-","S-");
								for(int f = 0; f<right.get(right.size()-1).length; f++)
									if(f!=col2)
										right.get(right.size()-1)[f]=(iobes+right.get(right.size()-1)[f]);							
								if(cj>0 && !chars2conll2.get(cj).equals(chars2conll2.get(cj-1)) && right.size()>2 && right.get(right.size()-2)!=null) {
									for(int f = 0; f<right.get(right.size()-2).length; f++)
										if(f!=col2)
											right.get(right.size()-2)[f]=right.get(right.size()-2)[f].replaceFirst("^B-","S-").replaceFirst("^I-","E-");
								}
								
								cj++;
								left.add(null);
							}
						}
					}
				}
				
				Vector<Vector<String[]>> result = new Vector<Vector<String[]>>();
				result.add(left);
				result.add(right);
				return result;
			}

	
	/** helper routine for split():
		(1) undo nested IOBES (may be lossy)
		(2) enforce IOBES validity (proper opening and closing)
		side-effect: converts IOB to IOBES <br/>
		may duplicate annotations, hence apply after undoIOBES4syntax() <br/>
		note that this routine may corrupt annotations if they contain I-,O-,B-,E-,S- as part of the annotation */
	protected Vector<String[]> repairIOBES(Vector<String[]> lines) {
		// (1) undo nested IOBES
		for(int i = 0; i<lines.size(); i++) 
			if(lines.get(i)!=null && lines.get(i).length>0 && !lines.get(i)[0].trim().startsWith("#"))
				for(int j = 0; j<lines.get(i).length; j++) {
					String anno = lines.get(i)[j];
					while(anno.matches("^[IOBES]-[IOBES]-.*")) 
						anno=anno.replaceFirst("^[IOBES]-I-","I-")
								 .replaceFirst("^[IOBES]-O-","O-")
								 .replaceFirst("^I-S-","I-")
								 .replaceFirst("^B-S-","B-")
								 .replaceFirst("^E-S-","E-")
								 .replaceFirst("^S-S-","S-")
								 .replaceFirst("^[SB]-B-","B-")
								 .replaceFirst("^[IOE]-B-","I-")
								 .replaceFirst("^[ES]-E-","E-")
								 .replaceFirst("^[IOB]-E-","I-");
					anno=anno.replaceFirst("^[IOBES]-\\*$","*")
							 .replaceFirst("^[IOBES]-_","_");
					while(anno.matches("^(.*\\+)?[IOBES]-O(\\+.*)?$")) 												// don't split (I)O(BES)
						anno=anno.replaceAll("^(.*\\+)?[IOBES]-O(\\+.*)?$","$1O$2");
					while(anno.endsWith("+O") || anno.startsWith("O+") || anno.contains("+O+"))														// simplify X+O+Y to X+Y
						anno=anno.replaceAll("\\+O\\+","+").replaceAll("^O\\+","").replaceAll("\\+O$","");
					lines.get(i)[j]=anno;
				}


				
		// (2) enforce IOBES validity
		for(int i = 0; i<lines.size(); i++) 
			if(lines.get(i)!=null && lines.get(i).length>0 && !lines.get(i)[0].trim().startsWith("#"))
				for(int j = 0; j<lines.get(i).length; j++) {
					String anno = lines.get(i)[j];
					String nextAnno = null;
					for(int k = i+1;k<lines.size() && nextAnno==null; k++)
						if(lines.get(k)!=null && lines.get(k).length>j && !lines.get(k)[j].equals("?") && !lines.get(k)[j].equals(""))
							nextAnno = lines.get(k)[j];
					String lastAnno = null;
					for(int k = i-1;k>=0 && lastAnno==null; k--)
						if(lines.get(k)!=null && lines.get(k).length>j && !lines.get(k)[j].equals("?") && !lines.get(k)[j].equals(""))
							lastAnno = lines.get(k)[j];
					if(anno.matches("^[IOBES]-.+")) {
						String iobesBody = anno.substring(2);
						if(lastAnno==null || !lastAnno.replaceFirst("^[BI]-","").equals(iobesBody))
							anno=anno.replaceFirst("^I-","B-").replaceFirst("^E-","S-");
						if(nextAnno==null || !nextAnno.replaceFirst("^[EI]-","").equals(iobesBody))
							anno=anno.replaceFirst("^I-","E-").replaceFirst("^B-","S-");
					}
					lines.get(i)[j]=anno;
				}
		
		return lines;
	}

	/** helper routine for split(): 
		undo IOBES prefixing for syntax (i.e., annotations with non-balanced parentheses)
		to make sure that parentheses match <br/>
		also, we remove IOBES marking for _<br/>
		
		IOBES fixing routine
	*/ 
	protected Vector<String[]> undoIOBES4syntax(Vector<String[]> lines) {
		for(int i = 0; i<lines.size(); i++)
			if(lines.get(i)!=null && lines.get(i).length>0 && !lines.get(i)[0].trim().startsWith("#"))
				for(int j = 0; j<lines.get(i).length; j++) {
					String anno = lines.get(i)[j];
					
					int leftPar = anno.replaceAll("[^\\(]","").length();
					int rightPar = anno.replaceAll("[^\\)]","").length();
					if(leftPar!=rightPar || anno.matches("^[IOBES]-[\\?_]$")) { // possible for syntax => we remove IOBES, note that we keep it for matching sequences

						String nextAnno = null;		// fix IOBES (we don't generally apply this fix, might interfere with nested IOBES annotations)
						String lastAnno = null;
						for(int k = i+1;k<lines.size() && nextAnno==null; k++)
							if(lines.get(k)!=null && lines.get(k).length>j && !lines.get(k)[j].equals("?") && !lines.get(k)[j].equals(""))
								nextAnno = lines.get(k)[j];							
						for(int k = i-1;k>=0 && lastAnno==null; k--)
							if(lines.get(k)!=null && lines.get(k).length>j && !lines.get(k)[j].equals("?") && !lines.get(k)[j].equals(""))
								lastAnno = lines.get(k)[j];							

						if(anno.startsWith("B-") && lastAnno!=null && lastAnno.equals(anno)) anno=anno.replaceFirst("B-","I-");
						if(anno.startsWith("B-") && (nextAnno==null || !nextAnno.matches("^[IE]-.*"))) anno=anno.replaceFirst("B","S");
						if(anno.startsWith("I-") && (nextAnno==null || !nextAnno.matches("^[IE]-.*"))) anno=anno.replaceFirst("I","E");
						if(anno.startsWith("E-") && nextAnno!=null && nextAnno.equals(anno)) anno=anno.replaceFirst("E-","I-");
					
						if(anno.startsWith("B-")) {
							anno=anno.substring(2);
							if(anno.contains("*")) {
								anno=anno.replaceFirst("\\*.*","*");
							} else anno=anno;
						} else if(anno.startsWith("I-")) {
							if(anno.contains("*"))
								anno="*";
							else anno="_";
						} else if(anno.startsWith("E-")) {
							anno=anno.substring(2);
							if(anno.contains("*"))
								anno=anno.replaceFirst("^[^\\*]*\\*","\\*");
							else anno="_";
						}
						else if(anno.startsWith("S-"))
							anno=anno.substring(2);

						// lines.get(i)[j]="("+lines.get(i)[j]+" after "+lastAnno+" before "+nextAnno+" >) "+anno; // DEBUG
						lines.get(i)[j]=anno;
					}
				}
		return lines;
	}
	
	/** calls CoNLLAlign with additional flag -split */
	public static void main(String[] argv) throws Exception {
		Vector<String> args = new Vector<String>(Arrays.asList(argv));
		boolean inserted = false;
		for(int i = 0; i<args.size() && !inserted; i++) {
			if(args.get(i).toLowerCase().startsWith("-drop")) {
				args.insertElementAt("-split", i);
				inserted=true;
			}
		}
		if(!inserted) args.add("-split");
		CoNLLAlign.main(args.toArray(new String[args.size()]));
	}
}