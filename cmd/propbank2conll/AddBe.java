import java.io.*;
import java.util.*;
import javax.xml.xpath.*;
import org.w3c.dom.*;
import org.xml.sax.*;

/** adds SRL information for non-auxiliar be from (gold) PTB annotations<br/>
 * 
 * PB definition (http://verbs.colorado.edu/propbank/framesets-english-aliases/be.html)
 *
 * be.01 (copula)
 * - Arg1-PPT: topic (vnrole: 109-1-1-theme)
 * - Arg2-PRD: comment (vnrole: 109-1-1-attribute) 
 * 	[syntactically, ARG2 should be PRD, but never VP]
 * e.g. 
 * John_A1 is_REL an idiot_A2
 * George_A1 's being_REL a brat_A2
 * 
 * be.02 (existential)
 * - Arg1-PPT: thing that is
 * 
 * e.g.
 * He_A1 is_REL n't_AM-NEG
 * a foul odor_A1 in the air 's_REL whenever_AM-TMP John's around
 * its_A1 very_AM-ADJ being_REL
 * 
 * be.03 (aux, do not tag)
 * syntactically followed by VP
 * 
 * be_like.04 (multiword expression, primarily spoken)
 * 
 * be_all.05 (multiword expression, primarily SMS)
 * 
 * => consider only be.01 and be.02, focus on verbs
 * => filter out bes not preceding VPs
 * 
 * */
public class AddBe {

	public static void main(String[] argv) throws Exception {
		System.err.println("AddBe [PTB-MRG1..n]\n"+
			"read PTB (mrg, not conll) files from args\n"+
			"write annotations in PropBank format");
				
		for(String file : argv) {
			BufferedReader in = new BufferedReader(new FileReader(file));
			String parse = "";
			int sentence=0;
			for(String line=""; line!=null; line=in.readLine()) {
				parse=parse+line;
				int open=parse.replaceAll("[^\\(]","").length();
				int close=parse.replaceAll("[^\\)]","").length();
				if(open==close && open>0) {
					// System.err.println(sentence+":"+parse);
					process(file, parse, sentence++);
					parse="";
				}
			}
			process(file, parse, sentence);
			in.close();
		}
	}

	/** return constituent "height" as used in PropBank<br/>
        Note that the XPath expression "count(.//text()[1]/ancestor::*)-count(./ancestor::*)" fails in Java because it natively supports XPath 1.0, only <br/>
		this is a nasty hack, only for my PTB trees */
	private static int getHeight(Node n) {
		if(n.getNodeType()==Node.ELEMENT_NODE)
			return 1 + getHeight(n.getFirstChild());
		else return -1;
	}
	
	protected static String writeXML(NodeList nl) {
		String result="";
		for(int i = 0; i<nl.getLength(); i++)
			result=result+(writeXML(nl.item(i)).replaceAll("\n","\n  "));
		return result;
	}
	
	protected static String writeXML(Node n) {
		String result = "";
		switch(n.getNodeType()) {
			case Node.ATTRIBUTE_NODE: 		result=result+" "+n.getNodeName()+"=\""+n.getNodeValue()+"\""; break;
			case Node.TEXT_NODE:
			case Node.CDATA_SECTION_NODE: 	result=result+n.getNodeValue(); break;
			case Node.COMMENT_NODE:			result=result+"<!-- "+n.getNodeValue()+" -->"; break;
			case Node.DOCUMENT_FRAGMENT_NODE:
			case Node.DOCUMENT_NODE:
				result = writeXML(n.getFirstChild()); // only one child;
				break;
			case Node.ELEMENT_NODE:			result=result+"<"+n.getNodeName();
				NamedNodeMap atts = n.getAttributes();
				NodeList children = n.getChildNodes();
				for(int i = 0; i<atts.getLength(); i++)
					result=result+writeXML(atts.item(i));
				if(children.getLength()==0) result = result+"/";
				result=result+">";
				if(children.getLength()>0) {
					if(children.item(0).getNodeType()==Node.ELEMENT_NODE)
						result=result+"\n  ";
					result=result+
						   writeXML(children)+
						   "</"+n.getNodeName()+">";
				}
				result=result+"\n";
				break;
		}
		return result;
	}
	
	protected static String getString(Node n) {
		return writeXML(n)
			.replaceAll("<[^>]*>"," ")
			.replaceAll("\\s+"," ").trim();
	}
	
	protected static void process(String file, String parse, int sentid) throws IOException {
		try {
			int open=parse.replaceAll("[^\\(]","").length();		// fix parse trees (should not be necessary)
			int close=parse.replaceAll("[^\\)]","").length();
			if(open!=close) System.err.println("warning for sent "+sentid+" in "+file+": "+open+" \"(\" vs. "+close+" \")\"");
			while(open>close) { parse=parse+")"; close++; }
			while(close>open) { parse="("+parse; open++; }
			if(open>0) {											// map to XML
				XPath xpath = XPathFactory.newInstance().newXPath();
				Element root = (Element)xpath.evaluate("/node[1]",
					new InputSource(new StringReader(
						parse
						 .replaceAll("&","&amp;")
						 .replaceAll("\\s*\\(([^\\(\\)\\s]+)\\s+","<node cat=\"$1\">")
						 .replaceAll("\\s*\\(\\s*","<node>")
						 .replaceAll("\\s*\\)\\s*","</node>"))),
					XPathConstants.NODE);
				
				/***********
				 | preproc |
				 ***********/
				// lowercase all text data (we work with XPath 1.0, below)
				NodeList texts = (NodeList)xpath.evaluate("//text()",root, XPathConstants.NODESET);
				for(int i = 0; i<texts.getLength(); i++)
					texts.item(i).setNodeValue(texts.item(i).getNodeValue().toLowerCase());
				
				/****************************
				 | predicate identification |
				 | and sense disambiguation |
				 ****************************/
				NodeList bes = (NodeList)xpath.evaluate("//*[starts-with(@cat,'V')][count(*)=0][text()='be' or text()='am' or text()='are' or text()='art' or text()='is' or text()='was' or text()='were' or text()='being' or text()='been']", root, XPathConstants.NODESET);
				for(int i = 0; i<bes.getLength(); i++) {
					// note that the order of rules is significant
					 Element be = (Element)bes.item(i);					
					
					// unknown: 4 instances (mis-matches such as Pan *Am*)
					be.setAttribute("rel","be.XX");  // for corpora other than PTB, use this for completeness checks
					
					// existential be: 29 instances
					if((Boolean)xpath.evaluate("count(./following-sibling::*[1])=0", be, XPathConstants.BOOLEAN))
						be.setAttribute("rel","be.02");	// CC: existential, main predicate
					
					// copular be with nominal non-PRD argument: 9 instances
					if((Boolean)xpath.evaluate("starts-with(./following-sibling::*[1]/@cat,'NP')", be, XPathConstants.BOOLEAN))
						be.setAttribute("rel","be.01a");	// CC: copula with nominal argument
		
					// copular be with clausal non-PRD argument other than infinitive with to: 4 instances
					if((Boolean)xpath.evaluate("starts-with(./following-sibling::*[1]/@cat,'S')", be, XPathConstants.BOOLEAN))
						be.setAttribute("rel","be.01b");	// CC: copula with clausal argument (if it is a to-construction, overwritten below)					

					// copular be with adjectival non-PRD argument: 4 instances
					if((Boolean)xpath.evaluate("count(./following-sibling::*[starts-with(@cat,'ADJ')][1])=1", be, XPathConstants.BOOLEAN))
						be.setAttribute("rel","be.01c");	// CC: copula with adjectival predicate

					// copular be with adverbial non-PRD argument: 1 instance
					if((Boolean)xpath.evaluate("count(./following-sibling::*[starts-with(@cat,'ADV')][1])=1", be, XPathConstants.BOOLEAN))
						be.setAttribute("rel","be.01d");	// CC: copula with adverbial (e.g., locative) predicate

					// auxiliar be in "be to" constructions: 108 instances, e.g. "But that *was* not to be.", "the auctions *are* to be rescheduled", "the segment *is*					soon to be broadcast"
					if((Boolean)xpath.evaluate("./following-sibling::node[starts-with(@cat,'S')][1][starts-with(node[1]/@cat,'NP-SBJ')][starts-with(node[1]//text()[1],'*')]/node[starts-with(@cat,'VP')][1]//text()[1]='to'", be, XPathConstants.BOOLEAN))
						be.setAttribute("rel","be.03a");	// CC
					
					// auxiliar be in nested "be to" construction: 1 instance "they are to be attracted"
					if((Boolean)xpath.evaluate("./following-sibling::node[1][starts-with(@cat,'S')]/node[1][starts-with(@cat,'S')][starts-with(node[1]/@cat,'NP-SBJ')][starts-with(node[1]//text()[1],'*')]/node[2][starts-with(@cat,'VP')]/node[1]/@cat='TO'", be, XPathConstants.BOOLEAN))
						be.setAttribute("rel","be.03b");	// CC
					
					// auxiliar be preceding VP node: 12717 instances
					if((Boolean)xpath.evaluate("count(./following-sibling::*[starts-with(@cat,'VP')][1])=1", be, XPathConstants.BOOLEAN))
						be.setAttribute("rel","be.03");	// according to propbank-definition

					// copular be preceding PRD: 13127 instances
					if((Boolean)xpath.evaluate("count(./following-sibling::*[contains(@cat,'PRD')][1])=1", be, XPathConstants.BOOLEAN))
						be.setAttribute("rel","be.01"); 	// according to propbank definition
					
					// meta-rule for parentheticals (PRN): 2 (1 be.01, 1 be.03), e.g., "which was (and *is*) regarded"
					if((Boolean)xpath.evaluate("@rel='be.XX' and ../@cat='PRN' and starts-with(../preceding-sibling::*[1]/@rel,'be')", be, XPathConstants.BOOLEAN))
						be.setAttribute("rel",xpath.evaluate("../preceding-sibling::*[1]/@rel",bes.item(i))+"-prn");

					// existential be with exisistential there: 652 instances
					if((Boolean)xpath.evaluate("not(starts-with(@rel,'be.03')) and ./preceding::*[text()!=''][1]/@cat='EX'", be, XPathConstants.BOOLEAN))
						be.setAttribute("rel","be.02a"); 	// only for non-auxiliaries
					
					// copular be in "that is" as a discourse marker: 1 instance
					if(xpath.evaluate("./preceding::text()[1]", be).equals("that") && xpath.evaluate("text()",be).equals("is") && xpath.evaluate("./following::text()[1]",be).equals(","))
						be.setAttribute("rel","be.01e");
				
					/**************
					 | core roles |
					 **************/
					 
					// be.01 (Arg1-PPT [topic/theme], Arg2-PRD [comment/attribute])
					if(be.getAttribute("rel").startsWith("be.01")) {
						be.setAttribute("ARG1-PPT","missing");
						be.setAttribute("ARG2-PRD","missing");
					}
					
					// be.02 (Arg1-PPT: thing that is)
					if(be.getAttribute("rel").startsWith("be.02")) {
						be.setAttribute("ARG1-PPT","missing");
					}
					
					Element a1 = (Element)xpath.evaluate("./ancestor::*[starts-with(@cat,'S') or contains(@cat,'FRAG')][1]/*[contains(@cat,'SBJ')][1]",be,XPathConstants.NODE);
					Element a2 = (Element)xpath.evaluate("./following-sibling::*[contains(@cat,'PRD')][1]", be, XPathConstants.NODE);
					if(a2==null) a2 = (Element)xpath.evaluate("./following-sibling::*[contains(@cat,'NP')][1]", be, XPathConstants.NODE);
					if(a2==null) a2 = (Element)xpath.evaluate("./following-sibling::*[contains(@cat,'ADJ')][1]", be, XPathConstants.NODE);
					if(a2==null) a2 = (Element)xpath.evaluate("./following-sibling::*[contains(@cat,'S')][1]", be, XPathConstants.NODE);
					if(a2==null) a2 = (Element)xpath.evaluate("./following-sibling::*[contains(@cat,'ADV')][1]", be, XPathConstants.NODE);

					// special rules for "that is"
					if(xpath.evaluate("./preceding::text()[1]", be).equals("that") && xpath.evaluate("text()",be).equals("is") && xpath.evaluate("./following::text()[1]",be).equals(",")) {
						if(a2==null) a2 = (Element)xpath.evaluate("./following::*[text()=','][1]/following::*[1]", be, XPathConstants.NODE);
						if(a1==null) a1 = (Element)xpath.evaluate("./preceding::*[1]", be, XPathConstants.NODE);
					}
					
					if(be.getAttribute("rel").equals("be.02a")) // existential there
						a1=a2;
					
					if(be.getAttribute("rel").startsWith("be.02"))
						a2=null;								// this must be a wrong match or a remainder from existential there
					
					if(be.getAttribute("rel").matches("be.0[12].*")) {
						if(a1!=null)
							be.setAttribute("ARG1-PPT",xpath.evaluate("count(preceding::text())",a1)+" "+getHeight(a1));				
						if(a2!=null)
						be.setAttribute("ARG2-PRD", xpath.evaluate("count(preceding::text())",a2)+" "+getHeight(a2));
					}
					
					// fill up parentheticals
					if(be.getAttribute("rel").contains("-prn")) {
						Element lastBe = (Element)xpath.evaluate("./preceding::*[starts-with(@rel,'be.01') or starts-with(@rel,'be.02')][1]",be,XPathConstants.NODE);
						if(be.getAttribute("ARG1-PPT").equals("missing") && !lastBe.getAttribute("ARG1-PPT").equals("missing")) be.setAttribute("ARG1-PPT",lastBe.getAttribute("ARG1-PPT"));
						if(be.getAttribute("ARG2-PRD").equals("missing") && !lastBe.getAttribute("ARG2-PRD").equals("missing")) be.setAttribute("ARG2-PRD",lastBe.getAttribute("ARG2-PRD"));
					}
					
					// clean up missing core arguments (the only WSJ instance is a fragment)
					if(be.getAttribute("ARG1-PPT").equals("missing")) be.removeAttribute("ARG1-PPT");
					if(be.getAttribute("ARG2-PRD").equals("missing")) be.removeAttribute("ARG2-PRD");
				
					/******************
					 | non-core roles |
					 ******************/
 					if(be.getAttribute("rel").matches("be.0[12].*")) {

						// AM-DIR Directional modifiers show motion along some path. n/a
						
						// AM-LOD: Locative modifiers indicate where some action takes place. 
						// approx by PTB LOC role
						Vector<NodeList> amss = new Vector<NodeList>();
						for(Node vp = be; xpath.evaluate("@cat",vp).startsWith("V"); vp = vp.getParentNode())
							amss.add((NodeList)xpath.evaluate("../*[contains(@cat,'LOC')]", vp, XPathConstants.NODESET));
						String am = "";
						for(NodeList ams : amss)
							for(int j = 0; j<ams.getLength(); j++)
								am=am+xpath.evaluate("count(preceding::text())",ams.item(j))+" "+getHeight(ams.item(j))+",";
						am=am.replaceFirst(",$","");
						if(!am.equals("")) be.setAttribute("ARGM-LOC",am);
						
						// AM-MNR: Manner adverbs specify how an action is performed. Manner tags should be used when an adverb be an answer to a question starting with ‘how?’.
						// approx by PTB MNR role
						amss.clear();
						for(Node vp = be; xpath.evaluate("@cat",vp).startsWith("V"); vp = vp.getParentNode())
							amss.add((NodeList)xpath.evaluate("../*[contains(@cat,'MNR')]", vp, XPathConstants.NODESET));
						am = "";
						for(NodeList ams : amss)
							for(int j = 0; j<ams.getLength(); j++)
								am=am+xpath.evaluate("count(preceding::text())",ams.item(j))+" "+getHeight(ams.item(j))+",";
						am=am.replaceFirst(",$","");
						if(!am.equals("")) be.setAttribute("ARGM-MNR",am);

						// AM-TMP: Temporal ArgMs show when an action took place, such as "in 1987", "last Wednesday", "soon" or "immediately".
						// approx by PTB TMP role (but note that PTB TMP includes never, which is NEG in PropBank)
						amss.clear();
						for(Node vp = be; xpath.evaluate("@cat",vp).startsWith("V"); vp = vp.getParentNode())
							amss.add((NodeList)xpath.evaluate("../*[contains(@cat,'TMP')]", vp, XPathConstants.NODESET));
						am = "";
						for(NodeList ams : amss)
							for(int j = 0; j<ams.getLength(); j++)
								if(!getString(ams.item(j)).matches("^(never|no longer)$"))
									am=am+xpath.evaluate("count(preceding::text())",ams.item(j))+" "+getHeight(ams.item(j))+",";
						am=am.replaceFirst(",$","");
						if(!am.equals("")) be.setAttribute("ARGM-TMP",am);

						// AM-EXT: Extent Markers indicate the amount of change occurring from an action, and are used mostly for
						// - numerical adjuncts like "(raised prices) by 15%",
						// - quantifiers such as "a lot"
						// - and comparatives such as "(he raised prices) more than she did.".
						// approx by PTB EXT role
						amss.clear();
						for(Node vp = be; xpath.evaluate("@cat",vp).startsWith("V"); vp = vp.getParentNode())
							amss.add((NodeList)xpath.evaluate("../*[contains(@cat,'EXT')]", vp, XPathConstants.NODESET));
						am = "";
						for(NodeList ams : amss)
							for(int j = 0; j<ams.getLength(); j++)
								am=am+xpath.evaluate("count(preceding::text())",ams.item(j))+" "+getHeight(ams.item(j))+",";
						am=am.replaceFirst(",$","");
						if(!am.equals("")) be.setAttribute("ARGM-EXT",am);

						// AM-PNC: Purpose clauses are used to show the motivation for some action.
						// does not apply, neither copula nor existentials require motivations
						// yet, approx. by PTB role PRP
						amss.clear();
						for(Node vp = be; xpath.evaluate("@cat",vp).startsWith("V"); vp = vp.getParentNode())
							amss.add((NodeList)xpath.evaluate("../*[contains(@cat,'PRP')]", vp, XPathConstants.NODESET));
						am = "";
						for(NodeList ams : amss)
							for(int j = 0; j<ams.getLength(); j++)
								am=am+xpath.evaluate("count(preceding::text())",ams.item(j))+" "+getHeight(ams.item(j))+",";
						am=am.replaceFirst(",$","");
						if(!am.equals("")) be.setAttribute("ARGM-PNC",am);
						
						// AM-CAU: Cause clauses indicate the reason for an action. Clauses beginning with "because" or "as a result of" are canonical cause clauses. Also questions starting with ‘why’
						// might apply, but we expect this to be covered by PDTB, already
						// apparently overlaps with PRP (~ PNC)
						
						// AM-ADV: Adverbials, syntactic elements which clearly modify the event structure of the verb in question, but which do not fall under any of the headings above; usually modify the entire sentence.
						// approx by ADVP without PTB role and PTB ADV role (if not ARG2, already)
						amss.clear();
						for(Node vp = be; xpath.evaluate("@cat",vp).startsWith("V"); vp = vp.getParentNode())
							amss.add((NodeList)xpath.evaluate("../*[contains(@cat,'-ADV') or @cat='ADVP']", vp, XPathConstants.NODESET));
						am = "";
						for(NodeList ams : amss)
							for(int j = 0; j<ams.getLength(); j++) {
								String address = ams.item(j)+" "+getHeight(ams.item(j));
								if(!be.getAttribute("ARG1-PPT").equals(address) && !be.getAttribute("ARG2-PRD").equals(address))
									am=am+xpath.evaluate("count(preceding::text())",ams.item(j))+" "+getHeight(ams.item(j))+",";
							}
						am=am.replaceFirst(",$","");
						if(!am.equals("")) be.setAttribute("ARGM-ADV",am);
						
						// AM-MOD: Modals are: will, may, can, must, shall, might, should, could, would.
						// approx by PTB cat MD
						am="";
						Vector<NodeList> mdss = new Vector<NodeList>();
						for(Node vp = be.getParentNode(); xpath.evaluate("@cat",vp).startsWith("VP"); vp = vp.getParentNode())
							mdss.add((NodeList)xpath.evaluate("preceding-sibling::*[@cat='MD']", vp, XPathConstants.NODESET));
						for(NodeList mds : mdss)
							for(int j = 0; j<mds.getLength(); j++)
								am=am+xpath.evaluate("count(preceding::text())",mds.item(j))+" "+getHeight(mds.item(j))+",";
						am=am.replaceFirst(",$","");
						if(!am.equals("")) be.setAttribute("ARGM-MOD",am);
						
						// AM-NEG: This tag is used for elements such as "not", "n't", "never", "no longer" and other markers of negative sentences.
						// implemented by string match
						amss.clear();
						for(Node vp = be; xpath.evaluate("@cat",vp).startsWith("V"); vp = vp.getParentNode())
							amss.add((NodeList)xpath.evaluate("../*[contains(@cat,'ADV') or @cat='RP']", vp, XPathConstants.NODESET));
						am = "";
						for(NodeList ams : amss)
							for(int j = 0; j<ams.getLength(); j++) {
								String form = getString(ams.item(j));
								if(form.equals("not") || 
								   form.equals("n't") || 
								   form.equals("never") || 
								   form.equals("no longer"))
									am=am+xpath.evaluate("count(preceding::text())",ams.item(j))+" "+getHeight(ams.item(j))+",";
							}
						am=am.replaceFirst(",$","");
						if(!am.equals("")) be.setAttribute("ARGM-NEG",am);
						
						// AM-REC: Reciprocals include reflexives and reciprocals such as himself, itself, themselves, together, each other, jointly, both, which refer back to one of the other arguments.
						// implemented by string match
						amss.clear();
						for(Node vp = be; xpath.evaluate("@cat",vp).startsWith("V"); vp = vp.getParentNode())
							amss.add((NodeList)xpath.evaluate("../*", vp, XPathConstants.NODESET));
						am = "";
						for(NodeList ams : amss)
							for(int j = 0; j<ams.getLength(); j++) {
								String form = getString(ams.item(j));
								if(form.matches("^[^ ]self") || 
								   form.matches("^[^ ]selves") ||
								   form.matches("together") ||
								   form.matches("each other") ||
								   form.matches("jointly") ||
								   form.matches("both")) 
									am=am+xpath.evaluate("count(preceding::text())",ams.item(j))+" "+getHeight(ams.item(j))+",";
							}
						am=am.replaceFirst(",$","");
						if(!am.equals("")) be.setAttribute("ARGM-REC",am);
						
						// AM-DIS: Discourse Markers connect a sentence to a preceding sentence.
						// not needed here, provided by PDTB
						
						// AM-PRD: Markers of secondary predication (PRD)
						// does not apply, cf. ARG2
						
						// fill up parentheticals
						if(be.getAttribute("rel").contains("-prn")) {
							Element lastBe = (Element)xpath.evaluate("./preceding::*[starts-with(@rel,'be.01') or starts-with(@rel,'be.02')][1]",be,XPathConstants.NODE);
							NamedNodeMap atts = lastBe.getAttributes();
							for(int j = 0; j<atts.getLength(); j++)
								if(atts.item(j).getNodeName().startsWith("ARGM") && xpath.evaluate("@"+atts.item(j).getNodeName(), be).equals(""))
									be.setAttribute(atts.item(j).getNodeName(),lastBe.getAttribute(atts.item(j).getNodeName()));
						}
					}
										
					/************
					 | postproc |
					 ************/
					// resolve coindexation
					NamedNodeMap atts = be.getAttributes();
					for(int j = 0; j<atts.getLength(); j++)
						if(atts.item(j).getNodeName().startsWith("ARG")) {
							String[] targets = atts.item(j).getNodeValue().split(",");
							String update  = "";
							for(String target : targets) {
								int term = Integer.parseInt(target.replaceAll(" .*",""));
								Node n = (Node)xpath.evaluate("//text()[count(preceding::text())="+term+"][1]/..",be,XPathConstants.NODE);
								for(int height = Integer.parseInt(target.replaceAll(".* ","")); height>0; height--)
									n=n.getParentNode();
								HashSet<String> newTargets = new HashSet<String>();
								update=update+target;
								HashSet<String> crossIndices = new HashSet<String>();
								//be.appendChild(be.getOwnerDocument().createComment(target+":"+(getString(n)+writeXML(n).replaceAll(">[^<]*<","><").replaceAll("<[^>]*cat=\"([^\"]*)\"[^>]*>"," $1").replaceAll("<[^>]*>",""))));
							for(String s : (getString(n)+writeXML(n).replaceAll(">[^<]*<","><").replaceAll("<[^>]*cat=\"([^\"]*)\"[^>]*>"," $1").replaceAll("<[^>]*>","")).split("\\s+"))
									if(s.matches(".*-[0-9]+$"))		// TODO: fix it, mixing cat and text is a dirty hack
										crossIndices.add(s.replaceAll(".*-",""));
								//be.appendChild(be.getOwnerDocument().createComment(target+":"+crossIndices+""));
								for(String s : crossIndices) {
									NodeList crossReffed = (NodeList)xpath.evaluate("//*[contains(@cat,'-"+s+"') or contains(text(),'-"+s+"')]", be, XPathConstants.NODESET);
									for(int k = 0; k<crossReffed.getLength(); k++)
										if(!crossReffed.item(k).equals(n))
											update=update+"*"+xpath.evaluate("count(preceding::text())",crossReffed.item(k))+" "+getHeight(crossReffed.item(k));
								}
								update=update+",";
							}
							update=update.replaceFirst(",$","");
							atts.item(j).setNodeValue(update);
						}
				 
					// be.01* > be.01, be.02* > be.02, be.XX and be.03* deleted
					String pred = xpath.evaluate("@rel",bes.item(i));
					pred=pred.replaceFirst("^(.....).*$","$1");
					be.setAttribute("rel",pred);
					if(pred.equals("be.XX") || pred.equals("be.03")) 
						be.removeAttribute("rel");
					
					// debugging output (duplicated for every be ;)
					// System.err.println(writeXML(root));
				
					// create PropBank output
					if(be.getAttribute("rel").matches("^be.0[12].*")) {
						System.out.print(file+" "+
										   sentid+" "+
										   xpath.evaluate("count(preceding::text())",be)+" "+
										   "add-be "+
										   be.getAttribute("rel")+" "+
										   "_");
						NodeList attrs = (NodeList)xpath.evaluate("@*[starts-with(name(),'ARG') or name()='rel']",be,XPathConstants.NODESET);
						for(int j = 0; j<attrs.getLength(); j++) {
							if(attrs.item(j).getNodeName().equals("rel")) {
								System.out.print(" "+xpath.evaluate("count(preceding::text())",be)+":"+getHeight(be)+"-rel");
							} else
								System.out.print(" "+attrs.item(j).getNodeValue().replaceAll(" ",":")+"-"+attrs.item(j).getNodeName());
						}
						System.out.println();
					}
				}				
			}
		} catch (XPathExpressionException e) {
			e.printStackTrace(); // doesn't happen
		}
	}
}