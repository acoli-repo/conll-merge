data sample, test cases and usage example

content
=======

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
	no severe errors are reported by CoNLLChecks on the sample data, except the ones listed below
	
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
			It points to a problem only if these warnings don't occur pair-wise.
		OPEN PAR WARNING: found no opening (, possibly due to retokenization
			Such mismatches normally produce an error. It is a warning only if the *RETOK* string itself (i.e., not its annotation) comprises 
			unpaired parentheses. This is not an error by itself, but may be an indicator for one. It is unproblematic if it means that one 
			of the parentheses failed to be merged with a neighboring token.