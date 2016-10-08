CoNLL converter for the Penn Discourse Graph Bank (Wolf & Gibson 2003)

annotations for discourse relations
- double annotation
- standoff format: txt file and relations with offset

files
- restore-names.sh: restore ap, resp. wsj names using the accompanying file-correspondence table
- PDGB2conll.java: read txt and (one) annotation file, write CoNLL format
- note that the txt file is untokenized, we perform a simple, orthography-based tokenization (i.e., not PTB-style tokenization!)

input format
	wsj_0004.pdgb.4
	- text with line-based segmentation
	- empty lines only between sentences (but not every) => paragraph boundaries?
	- segments are clauses (incl. appositions) or interrupted by clauses
	 
	wsj_0004.pdgb.4-annotation1
	- <START1> <END1> <START2> <END2> <REL>
	- START/END1: source of relation (normally after START/END2)
	- first segment has nr 0, empty lines are not numbered

	conversion
	- to (our own) PDTB-style CoNLL representation
	- first segment is represented as Arg2 (cf. PDTB)
	- both annotators in separate files
	- number relations

output format:
	FORM<TAB>[REL1[; REL2[; ...]]]
	- FORM original text, heuristically tokenized
	- RELi annotation, consisting of REL_ID:ARG_TYPE:RELATION
	- REL_ID numerical id, uniquely identifying a discourse relation in the text
	- ARG_TYPE Arg1 ("nucleus") or Arg2 ("satellite")
	- RELATION PDGB relation labels
The original spans can be reconstructed as sequences with identical REL_ID:ARG_TYPE