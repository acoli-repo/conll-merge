routines to create CoNLL annotations from PropBank, NomBank, OntoNotes prop annotations plus PTB-style parses

- original annotations are standoff with reference to PTB files (tokenization-sensitive!)
- all of these require a PTB file as reference, the PTB file is thus preserved in the output, the SRL information is appended
- input: prop file(s), PTB files (FORM<TAB>POS<TAB>PSD)
- output: CoNLL files (one per source PTB file), format: FORM<TAB>POS<TAB>PSD<TAB>PRED<TAB>ARG1<TAB>ARG2...

Notes:
- for NomBank and PropBank, all SRL annotations for different files are merged into a single file, these need to be split
- note that PropBank does not contain annotations for be.01 and be.02. These can be extrapolated from syntax annotations => AddBe

Workflows:

  OntoNotes: (1) normalize.ontonotes-prop.sh
             (2) SRL2CoNLL

  NomBank:   (1) normalize.nombank.sh
             (2) splitter.sh
			 (3) SRL2CoNLL
			 
  PropBank:  (0) AddBe (to create additional annotations for be.01, be.02)
             (1) normalize.propbank.sh
			 (2) splitter.sh
			 (3) SRL2CoNLL