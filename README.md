# CoNLL
Tools for manipulating CoNLL TSV and related formats
* special focus on processing multi-layer corpora, including annotations with conflicting tokenizations and/or textual variations

Subdirectories
* cmd converters from different source formats to CoNLL
* src java classes for manipulating CoNLL files, in particular a tokenization-independent merging routine
* lib jars for src
* data_nlp sample data for merging various linguistic and semantic annotations of the same text
* data_phil sample data and pipeline for merging multiple versions of the same text (i.e., similar text)

History
* May 2009 version 0.1 ("PAULA merge", internal use only)
  Original implementation of different strategies for merging multi-layer annotations with different tokenizations as described by 
  Chiarcos et al. (2009, 2012), developed at the University Potsdam, Germany, funded by the DFG Collaborative Research Center 632.
  Originally, this has been part of the PAULA framework (https://www.sfb632.uni-potsdam.de/paula.html), using a standoff XML format.
  While it was used locally, processing standoff XML is cumbersome, the implementation was thus not widely adapted.
* June 2012 version 0.2 ("inline merge", unpublished)
  Reimplementation of the merging using an inline XML format, specific to the Penn Treebank and its subcorpora (OntoNotes, PropBank, 
  RST-DTB, PDTB, PDGB, etc.), conducted at the Information Sciences Institute of the University of Southern California (ISI/USC), 
  funded by a DAAD PostDoc stipend.
  This reimplementation was used for a number of experiments (e.g., Chiarcos 2012), but specific to these and never formally released.
  In particular, dependency annotation was used to convert original span-based annotation to token-level annotations.
* Oct 2016 version 0.3 ("CoNLL merge")
  Reimplementation of the merging routine using generic CoNLL data structures, conducted at the Applied Computational Linguistics 
  (ACoLi) Lab at Goethe University Frankfurt, supported by the LOEWE cluster "Digital Humanities". The intention of the reimplementation
  is to separate application-specific and generic aspects of the 0.2 processing pipeline. Application-specific components are to be
  published at a later stage.
  The code for the merging and a number of converters is now published under an Apache license via Github. The release includes a
  data sample for an NLP/semantic annotation workflow (wsj_0655), which is, however, password protected for reasons of copyright. 
  Contact us to check whether we can give you access, alternatively, ask the LDC for file wsj_0655 in PTB3, PDTB2, RST-DTB, PDGB, 
  OntoNotes, PropBank, NomBank.
* Apr 2017 version 0.31 ("CoNLL merge")
  Code base partially restructured, with backward-compatible functionality and parameters. Added an philological use case (editions of
  historical texts, see data_phil): New flag -lev for Levenshtein-based alignment (to be used when working with similar text rather
  than different annotations of the same text).

References
* Chiarcos, C., Ritz, J. & Stede, M. (2009), By all these lovely tokens ... Merging conflicting tokenizations. In: Proceedings of the Third Linguistic Annotation Workshop (LAW-2009), held in conjunction with ACL-IJCNLP 2009, Suntec, Singapore, p. 35-43.
* Chiarcos, C., Ritz, J. & Stede, M. (2012), By all these lovely tokens ... Merging conflicting tokenizations. Language Resources & Evaluation 46(53).
* Chiarcos, C. (2012), Towards the unsupervised acquisition of discourse relations, In: Proceedings of the 50th Annual Meeting of the Association for Computational Linguistics (ACL-IJCNLP 2012), Jeju Island, Korea, p. 213-217.

Christian Chiarcos, christian.chiarcos@web.de, Applied Computational Linguistics (ACoLi) lab, Goethe University Frankfurt, Germany