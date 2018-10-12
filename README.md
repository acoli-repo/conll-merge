
# CoNLL
Tools for manipulating CoNLL TSV and related formats
* special focus on processing multi-layer corpora, including annotations with conflicting tokenizations and/or textual variations (CoNLL-Merge)
* converters from manifold source representations, incl.
** Penn Treebank syntax
** PropBank/NomBank semantic role annotations (standoff)
** OntoNotes named entity annotations
** OntoNotes coreference coreference
** Penn Discourse Graph
** Penn Discourse Treebank (PDTB 2)
** RST Discourse Treebank
** Aside from these, TSV formats as previously used for parts-of-speech, morphosyntactic features, chunking, dependency syntax, named entity annotation, semantic roles, etc. do not require conversion.
* For routines for parsing, transforming and manipulating annotation graphs, see our CoNLL-RDF library under https://github.com/acoli-repo/conll-rdf.

Usage
* Open Source, Apache license 2.0, see LICENSE
* In scientific publications, please refer to *both* following publications:
  * Chiarcos, Christian, Julia Ritz, and Manfred Stede (2012), <i>By all these lovely tokens ... Merging conflicting tokenizations</i>. Journal of Language Resources & Evaluation  46(1):53-74.
  * Chiarcos, Christian, and Niko Schenk (2018), The ACoLi CoNLL Libraries: Beyond Tab-Separated Values, In: <i>Proceedings of the Eleventh International Conference on Language Resources and Evaluation (LREC 2018)</i>, Miyazaki, Japan, May 7-12, 2018, p.571-576.
* For other forms of usage and redistribution, please refer to https://github.com/acoli-repo/conll and preserve the attached NOTICE file

Subdirectories
* cmd converters from different source formats to CoNLL
* src java classes for manipulating CoNLL files, in particular a tokenization-independent merging routine
* lib jars for src
* data sample data for merging various linguistic and semantic annotations of the same text
* data_phil sample data and pipeline for merging multiple versions of the same text (i.e., similar text)

History
* May 2009 version 0.1 ("PAULA merge")
  Original implementation of different strategies for merging multi-layer annotations with different tokenizations as described by   Chiarcos et al. ([2009](https://aclanthology.coli.uni-saarland.de/papers/W09-3005/w09-3005), [2012](https://link.springer.com/article/10.1007/s10579-011-9161-0)), developed at the University Potsdam, Germany, funded by the DFG Collaborative Research Center 632.  Originally, this has been part of the [PAULA framework](https://www.sfb632.uni-potsdam.de/paula.html), using a standoff XML format.   While it was used locally, processing standoff XML is cumbersome, the implementation was thus not widely adapted.
* June 2012 version 0.2 ("inline merge")
  Reimplementation of the merging using an inline XML format, specific to the Penn Treebank and its subcorpora (OntoNotes, PropBank,   RST-DTB, PDTB, PDGB, etc.), conducted at the Information Sciences Institute of the University of Southern California (ISI/USC),   funded by a DAAD PostDoc stipend.
  This reimplementation was used for a number of experiments (e.g., [Chiarcos 2012](https://aclanthology.coli.uni-saarland.de/papers/P12-2042/p12-2042)), but specific to these and never formally released.   In particular, dependency annotation was used to convert original span-based annotation to token-level annotations.
* Oct 2016 version 0.3 ("CoNLL merge")
  Reimplementation of the merging routine using generic CoNLL data structures, conducted at the Applied Computational Linguistics   (ACoLi) Lab at Goethe University Frankfurt, supported by the LOEWE cluster "Digital Humanities". The intention of the reimplementation  is to separate application-specific and generic aspects of the 0.2 processing pipeline. Application-specific components are to be  published at a later stage.
  The code for the merging and a number of converters is now published under an Apache license via Github. The release includes a  data sample for an NLP/semantic annotation workflow (wsj_0655), which is, however, password protected for reasons of copyright.   Contact us to check whether we can give you access, alternatively, ask the LDC for file wsj_0655 in PTB3, PDTB2, RST-DTB, PDGB, 
  OntoNotes, PropBank, NomBank.
* Apr 2017 version 0.31 ("CoNLL merge")
  Code base partially restructured, with backward-compatible functionality and parameters. Added an philological use case (editions of   historical texts, see data_phil): New flag -lev for Levenshtein-based alignment (to be used when working with similar text rather   than different annotations of the same text).
  This version is documented in [Chiarcos and Schenk (2018)](http://www.lrec-conf.org/proceedings/lrec2018/pdf/869.pdf)
  
References
* Chiarcos, C., Ritz, J. & Stede, M. (2009), [By all these lovely tokens ... Merging conflicting tokenizations](https://aclanthology.coli.uni-saarland.de/papers/W09-3005/w09-3005). In: Proceedings of the Third Linguistic Annotation Workshop (LAW-2009), held in conjunction with ACL-IJCNLP 2009, Suntec, Singapore, p. 35-43.
* Chiarcos, C., Ritz, J. & Stede, M. (2012), [By all these lovely tokens ... Merging conflicting tokenizations](https://link.springer.com/article/10.1007/s10579-011-9161-0). Journal of Language Resources & Evaluation  46(1):53-74.
* Chiarcos, C. (2012), [Towards the unsupervised acquisition of discourse relations](https://aclanthology.coli.uni-saarland.de/papers/P12-2042/p12-2042), In: Proceedings of the 50th Annual Meeting of the Association for Computational Linguistics (ACL-IJCNLP 2012), Jeju Island, Korea, p. 213-217.
* Chiarcos, C. & Schenk, N. (2018), [The ACoLi CoNLL Libraries: Beyond Tab-Separated Values](http://www.lrec-conf.org/proceedings/lrec2018/pdf/869.pdf), In: Proceedings of the Eleventh International Conference on Language Resources and Evaluation (LREC 2018), Miyazaki, Japan, May 7-12, 2018, p.571-576.

Contributors
* CC - Christian Chiarcos, christian.chiarcos@web.de, Applied Computational Linguistics (ACoLi) lab, Goethe University Frankfurt, Germany
