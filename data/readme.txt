data sample, test cases and usage example

orig.zip
	data sample, password-protected (contact christian.chiarcos@web.de or ask LDC for the original corpora)
	contains wsj_0655 from PTB3, PDTB2, RSTDTB, OntoNotes, PDGB, PropBank, Nombank

test.*2conll.sh
	calls the converters on the data sample, illustrates their usage
	
build-and-test.ALL.sh
	create conll directory
	runs all converters
	
test.CoNLLAlign.sh
	calls CoNLLAlign for pairwise merge of *all* files in conll/
	with default options (no -f, no -split), no severe errors are reported by CoNLLChecks, except the ones listed below
	-f and -split merges need to be evaluated

	known error messages (not bugs ;)
		CLOSE PAR ERROR / OPEN PAR ERROR for every merge where semantic roles (PropBank) precede RST
			this is because propbank annotations have variable length across different sentences
			whereas RST provides a LISP-like ()-annotation over all sentences of a text
			this *cannot* be solved except by the general recommendation to put propbank annotations in the last columns
			alternatively, use a fixed-width representation of semantic roles (akin to our PDTB representation) - but this is well established
			
	observed warnings
		IOBES WARNING: IOBES expression "I-GPE" not closed with sentence / IOBES expression "E-GPE" used sentence-initially
			Here, because of tokenization differences, a token from the second file (name) had to be inserted at (i.e., after) a sentence break 
			in the first. A rare case and hard to treat automatically, hence left for manual correction (thus a warning, not an error).
		EMPTY_CELL WARNING when merging X and PDTB
			This is not a problem per se, but may be indicative of extraction/conversion errors (in this case, it isn't).
			I didn't find the reason, but it seems PDTB-specific.