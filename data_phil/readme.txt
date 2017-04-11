Alignment sample pipeline for computational philology (digital editions)

Historical documents, e.g., manuscripts, often come in multiple versions, copied one from another, but often with considerable variations.
The resulting text is thus similar rather than identical. A straight-forward application of CoNLLAlign turned out to be insightful for a 
sample document (for testing purposes, we used Middle Low German editions of the Interrogatio Sancti Anselmi de Passione Domini), but also
too restrictive (a lot of purely orthographic variation was not captured). Accordingly, we developed an extension where minimal relative 
Levenshtein distance (with greedy decoding) is used to resolve n:m alignments produced by the original CoNLLAlign. But even then, langage-
specific adaptations are recommended to achieve optiomal results (and provided here for Middle Low German).

Run ./build.sh.

Christian Chiarcos, 2017-04-11
chiarcos@informatik.uni-frankfurt.de

Sample data
-----------
We illustrate different merging routines on Middle Low German editions of the Interrogatio Sancti Anselmi de Passione Domini (https://www.linguistics.rub.de/anselm/corpus/german.html). For reasons of copyright, we cannot ship the sample data, so we provide a download script for the original PDFs, scripts for extracting text (see build.sh) and for formatting it in a CoNLL-compliant way with three columns (xml2conll.sh):

SIMPLIFIED
An artificial, unorthographic string that generalizes over different orthographical traditions, e.g., diagraphs to express vowel length or umlaut (MLG oe may be ô or ö, but can also be spelled oo or o), or language specific character alternations (MLG w and v alternate with u, and v alternates with f, all are reduced to u). While being language-specific, this generalization is conservative and minimal. We do not attempt to cover phonology-driven alternation between a and o, apocope or syncope. 

ORIGINAL
The original string. We do not tokenize, but apply whitespace tokenization.* ORIGINAL thus includes word tokens with non-separated punctuation characters.
(* punctuation-sensitive tokenization and sentence splitting on medieval text is problematic as modern punctuation conventions do not systematically apply.)

NORMALIZED
The original string with a number of orthographical conventions being harmonized (lowercase, ASCII text with diacritics, duplicate characters removed, punctuation removed). Note that this harmonization is not language-specific, but requires a Latin-based orthography. Adaptation to other languages may require extensions for additional diacritics and special characters.

For adapting xml2conll.sh for other medieval languages, run it on your data as is, look on a well-aligned block in your data and check the output for orthographic alternations it didn't get and extend xml2conll.sh accordingly.

Applying xml2conll.sh to other PDF files requires adjusting the extraction criteria, e.g., the font information used to spot the actual text.

Merging (test.merge.sh)
-----------------------
Merging is the iterated application of CoNLLAlign on the sample files in CoNLL format.
SIMPLIFIED is used for merging, but dropped from the output, so these are alternating columns of ORIGINAL and NORMALIZED (in that order) for all merged files.

Requirements
------------
standard shell programs (bash, wget, perl, sed; under Windows, consider https://www.cygwin.com/)
java
pdftohtml (https://poppler.freedesktop.org/)