# ELAN (EAF) to CoNLL converter

Heuristic converter for the ELAN Annotation Format (EAF).

	eaf2conll.sh file1[..n].eaf
	
This comes without any warranties, but this is a best effort solution, only.

This basically works by 
- identifying all tiers in an eaf file (`eaf2tsv.xsl`), 
- mapping every tier to a column (i.e., transposition, `eaf2tsv.xsl`),
- iterating over the TIME_SLOT axis to find all base segments (`eaf2tsv.xsl`),
- for every base segment and every TIER, retrieve the (only!) *first* alignable annotation (`ANNOTATION/ALIGNABLE_ANNOTATION`) or leave it open (`eaf2tsv.xsl`),
- heuristic merging over all subsequent lines generated in this way as long as every cell is assigned at most one value (`aggregate.py`).

Note that the heurstic mapping works well in this case, but may be specific to the type of files used to develop this converter.
Also note that span annotations are reduced to token-level annotations.

EAF sample taken from DDD v1.1 Heliand corpus, Karin Donhauser, Jost Gippert, Rosemarie Lühr; Deutsch Diachron Digital - Referenzkorpus Altdeutsch Version 1.1; Humboldt-Universität zu Berlin, https://www.laudatio-repository.org/browse/corpus/WiWkDnMB7CArCQ9CyBEw/corpora, CC BY-NC-SA 3.0

## History

2021-06-05 GitHub release [CC]
2020-07-26 implemented and tested for the DDD v1.1 Heliand corpus [CC]

## Contributors

CC - Christian Chiarcos