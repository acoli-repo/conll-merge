#!/bin/bash
# read OntoNotes name file from arg file
# write CoNLL to stdout, using an IOBBES scheme
iconv -f utf-8 -t utf-8 -c $1 | \
perl -pe '
	s/<DOC[^>]*>\n//gs;									# remove meta
	s/<\/DOC>\n//gs;
	s/[\t ]+/ /g;										# norm spacing
	s/ <\//<\//g;
	s/\n/\n\n/g;										# sentence breaks
	s/<ENAMEX TYPE="([^"]*)">([^ <]*)/$2\tB-$1/g;		# I(O)BES encoding
	while(m/.*<\/ENAMEX>.*/) {
		#print;
		s/\tB(-[^ ]*)<\/ENAMEX>/\tS$1/g;
		s/\tI(-[^ ]*)<\/ENAMEX>/\tE$1/g;
		s/(\t[BI])(-[^ \t<]*) ([^ \t<]*)([ <])/$1$2 $3\tI$2$4/g;
	}
	s/ /\n/g;
' | \
sed 's/^[^\t][^\t]*$/&\tO/g'						# (I)O(BES) encoding
