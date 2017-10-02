package org.acoli.conll.merge;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.TreeSet;
import java.util.Vector;

/** 
 * Selected validity checks on CoNLL files
*/ 
public class CoNLLChecks {

	public static void main(String[] argv) throws Exception {
		System.err.println("CoNLLChecks FILE1[..n]\n"+
				"reads tab-separated CoNLL files from stdin or argument files");
		if(argv.length==0) System.err.println(
				"runs validity checks:\n"
				+ "(1) is the number of columns (within a sentence) constant\n"						// ok
				+ "(2) any mismatches between opening and closing round parentheses, i.e. ()\n"
				+ "    (document-wide)\n"
				+ "(3) invalid IOBES statements\n"													// ok
				+ "    we expect B-I-E in exactly this order and for sentence-internal annotations, only\n"
				+ "(4) incorrect uses of special character *\n"										// ok
				+ "    (limited to empty elements in PTB primary data [WORD aka FORM column] and\n"
				+ "    WORD place holders in LISP-style annotations [e.g., CFG parses, e.g., PSD column])\n"
				+ "    We only permit * to occur exactly once in a cell (as in PSD column) or in the\n"
				+ "    pattern \"^\\*[a-zA-Z0-9]+\\*.*\" (empty tokens in PTB WORD). We also add an\n"
				+ "    exception for *RETOK*- (which may be iterated), and aggregated lines (containing\n"
				+ "    +, produced by CoNLLAlign -f).\n"
				+ "(5) cells without content (empty cells should have _)\n"					// ok
				+ "(6) use full-line comments, not in-line comments (to ease merging)\n");			// ok
		
		List<Reader> ins = new Vector<Reader>();
		List<String> files = Arrays.asList(argv);
		for(String arg : argv)
			ins.add(new FileReader(arg));

		if(ins.size()==0) {
			System.err.println("\nreading from stdin");
			ins.add(new InputStreamReader(System.in));
			files.add("<stdin>");
		}
		
		for(int n = 0; n<ins.size(); n++) {
			String file = files.get(n);
			BufferedReader in = new BufferedReader(ins.get(n));
			System.err.println("\nchecking "+file);
			int errors = 0;
			int warnings = 0;
			int linenr = 0;
			int cols = -1;
			String[] lastFields = null;
			Vector<String> report = new Vector<String>();
			Vector<Stack<Integer>> openPars = new Vector<Stack<Integer>>();
			for(String line = in.readLine(); line!=null; line=in.readLine()) {
				if(line.trim().equals("")) {
					if(lastFields!=null) {
					// test (3)
						for(int i = 0; i<lastFields.length; i++)
							if(lastFields[i].matches("^[IB]-.*")) {
								report.add(file+", line "+linenr+": IOBES WARNING in column "+(i-1)+": IOBES expression \""+lastFields[i]+"\" not closed with sentence");
								warnings++;
							}
					}
					lastFields=null;
				} else if(!line.trim().startsWith("#")) {
					String[] fields = line.split("\t");
					
					// test (1)
					if(lastFields!=null && fields.length!=cols) {
						report.add(file+", line "+linenr+": COL_MISMATCH ERROR "+fields.length+" columns, last line had "+cols);
						errors++;
					}

					// test (6)
					if(line.contains("#")) {
						report.add(file+", line "+linenr+": INLINE_COMMENT WARNING: avoid inline comments, use full-line comments, instead");
						warnings++;
					}
					
					cols=fields.length;
					
					for(int i = 0; i<cols; i++) {
						// test (2)
						for(int j = 0; j<fields[i].length(); j++) {
							while(openPars.size()<j+1) openPars.add(new Stack<Integer>());
							String s = fields[i].substring(j,j+1);
							if(s.equals("(")) openPars.get(i).push(linenr);
							if(s.equals(")")) {
								if(openPars.get(i).isEmpty()) {
									if(!fields[i].matches(".*\\*RETOK\\*-[^\\s]*\\).*")) { // these should be surrounded by whitespaces
										report.add(file+", line "+linenr+": OPEN PAR ERROR in column "+i+": found no opening (");
										errors++;
									} else {
										report.add(file+", line "+linenr+": OPEN PAR WARNING in column "+i+": found no opening (, possibly due to retokenization");
										warnings++;
									}
								} else
									openPars.get(i).pop();
							}
						}

						// test (3) (extended for -split + -f, i.e., multiple, +-concatenated IOBES statements)
						if(fields[i].split("\\-").length>1) {
							String iobesPfx = fields[i].replaceFirst("\\-.*","");
							String iobesBody = fields[i].replaceFirst("^[^\\-]*\\-","");
							if(lastFields==null && iobesPfx.matches("^[IE]$")) {
								report.add(file+", line "+linenr+": IOBES WARNING in column "+i+": IOBES expression \""+fields[i]+"\" used sentence-initially");
								warnings++;
							}
							if(lastFields!=null && lastFields.length>i) {
								if(iobesPfx.matches("^[BOS]$"))
									if(lastFields[i].matches("^[IB]-.*")) {
										report.add(file+", line "+linenr+": IOBES ERROR in column "+i+": IOBES expression \""+lastFields[i]+"\" not continued in \""+fields[i]+"\"");
										errors++;
									}
								if(iobesPfx.matches("^[IE]$"))
									if(lastFields[i].replaceAll(".*\\+ *","").matches("^[SEO]-.*")) {
										report.add(file+", line "+linenr+": IOBES ERROR in column "+i+": IOBES expression \""+fields[i]+"\" does not continue \""+lastFields[i]+"\"");
										errors++;
									}
								if(iobesPfx.matches("^[EI]") && 
								   !lastFields[i].replaceAll(".*\\+ *","").replaceFirst("^[BI]-","").replaceFirst("\\+.*","").replaceFirst("\\*RETOK\\*-[^ \\)]*","\\*").equals(iobesBody.replaceFirst("\\+.*","").replaceFirst("\\*RETOK\\*-[^ \\)]*","\\*"))) {
									report.add(file+", line "+linenr+": IOBES ERROR in column "+i+": IOBES expression \""+fields[i]+"\" does not continue \""+lastFields[i]+"\"");
									errors++;
								}
							}
						}

					// test (4)
						if(fields[i].matches(".*\\*[^\\+]*\\*.*")) { 	// permitted in aggregated lines (CoNLLAlign -f)
							if(fields[i].replaceAll("\\*RETOK\\*-[^ ]*","").replaceAll("[^\\*]","").length()>2) {
								report.add(file+", line "+linenr+": STAR ERROR in colum "+i+": found more than two * in \""+fields[i]+"\"");
								errors++;
							}
							if(fields[i].replaceAll("\\*RETOK\\*-[^ ]*","").matches(".*\\*.*\\*.*") && !fields[i].matches("^\\*[a-zA-Z0-9]+\\*.*")) {
								report.add(file+", line "+linenr+": STAR ERROR in colum "+i+": invalid empty element marker in \""+fields[i]+"\"");						   
								errors++;
							}
						}
						
					// test (5)
						if(fields[i].equals("")) {
							report.add(file+", line "+linenr+": EMPTY_CELL WARNING in column "+i+" ("+cols+" columns in total): use _ instead");
							warnings++;
						}
					}
					
					lastFields=fields;
				}
				
				linenr++;
			}

			// test (2)
			for(int i = 0; i<openPars.size(); i++)
				while(!openPars.get(i).isEmpty()) {
					report.add(file+", line "+openPars.get(i).pop()+": CLOSE PAR ERROR in column "+i+": found no closing )");
					errors++;
				}
								
			if(lastFields!=null) {
			// test (3)
				for(int i = 0; i<lastFields.length; i++)
					if(lastFields[i].matches("^[IB]-.*")) {
						report.add(file+", line "+linenr+": IOBES WARNING in column "+(i-1)+": IOBES expression \""+lastFields[i]+"\" not closed with sentence");
						warnings++;
					}
			}
			
			//Collections.sort(report);
			for(String r : report)
				System.err.println(r);
			
			System.err.println("diagnosis: "+errors+" errors, "+warnings+" warnings");
			if(errors+warnings > 0) {
				String allReports = report.toString().replaceAll(", ","\n").replaceAll("[^\n]*: ([^:\n]* (WARNING|ERROR))[^\n]*","$1");
				for(String msg : new TreeSet<String>(Arrays.asList(allReports.split("\n"))))
					if(msg.matches(".*(ERROR|WARNING).*"))
						System.err.println(msg+":"+(allReports.split(msg).length));
			}
		}
	}
}