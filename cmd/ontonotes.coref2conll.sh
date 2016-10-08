#!/bin/bash
# read OntoNotes coref file from arg file
# write CoNLL to stdout (following the CoNLL-2012 format)
# note that unlike the CoNLL 2012 Shared Task, we keep APPOS
# otherwise, the CoNLL format follows that of the ST
iconv -f utf-8 -t utf-8 $1 | \
perl -pe '
	s/<(DOC|TEXT)[^>]*>\n//gs;	# clear markup
	s/<\/(DOC|TEXT)>\n*//gs;
	s/[ \t]+/ /g;
	s/\n/\n\n/g;
	while(m/<COREF/) {
		s/<COREF[^>]*ID="[^"-]*-([^"]+)"[^>]*>([^<]*)<\/COREF>/\t\($1\t$2\t$1\)\t/g;
	}
	s/ /\n/g;
' | \
perl -pe '
	while(m/^\t*\(.*/) {
		s/(\([^\t\n ]+)?\t+([^\(\)\n ]+)([\t\n])/$2\t$1$3/g;
	}
	s/^\t*//g;
	s/\t*$//g;
	s/\t+/\t/g;
	s/\(([^ \t\n]+)\t+\1\)/\($1\)/g;
	while(m/\t.*\t/) {
		s/^([^\t]+\t[^\t]+)\t/$1|/g;
	}
	s/^([^\t\n]+)$/$1\t_/g;
'
