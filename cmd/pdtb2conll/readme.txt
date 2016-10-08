CoNLL converter for the Penn Discourse Tree Bank II (Prasad et al. 2006)

annotations for discourse relations
- standoff annotations with references to PTB3 mrg and PTB1 txt files, contains partial text
- Arg2: "internal argument" (where the discourse cue is attached)
- Arg1: "external argument" (which the discourse cue points to, often preceding Arg2)

conversion strategy
- PDTB2conll.java: read annotation file, write CoNLL format
- Note that we do not resolve standoff references, instead, we provide a *partial* reconstruction of the original text. Using a tokenization-robust merging routine, this can then be resolved against the full text, regardless of whether PTB1 or PTB3 (or, say, RSTDTB or PDTB) is used as a basis.
- Note that the partial text conveyed in the file is untokenized, we perform a simple, orthography-based tokenization (i.e., not PTB-style tokenization!)

output format:
	FORM<TAB>POSITION<TAB>[REL1[; REL2[; ...]]]
	- FORM original text, heuristically tokenized
	- POSITION character offsets in the original (PTB1) txt file, preserved for debugging purposes
	- RELi annotation, consisting of REL_ID:ARG_TYPE (ANNOTATION)
	- REL_ID numerical id, uniquely identifying a discourse relation in the text
	- ARG_TYPE Arg1, Arg2 or Explicit (for discourse cues)
	- ANNOTATION PDTB annotation, e.g.
		EntRel
		Explicit CUE, PDTB_RELATION[, PDTB_RELATION2]
		Implicit CUE, PDTB_RELATION[, PDTB_RELATION2]
		AltLex, PDTB_RELATION[, PDTB_RELATION2]
		
The original spans can be reconstructed as sequences with identical REL_ID:ARG_TYPE
Note that PDTB annotations can comprise spaces. Normally, these must be escaped in CoNLL format, because spaces have been used as column separators in earlier versions of the format.
We *require* that columns are separated by TAB, and thus permit spaces in annotations.

Attribution information is not preserved.

Note that our CoNLL representation differs from the PDTB CoNLL format used for the 2015/2016 Shared Tasks. This "traditional" CoNLL representation was characterized by its authors as "wickedly sparse", it was not fully supported in the task setup and IMHO not used by the participants. Instead, a JSON format was adopted.