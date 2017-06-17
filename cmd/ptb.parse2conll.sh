#!/bin/bash
# read PTB lisp file format (*.mrg, i.e., POS+PSD, also used for OntoNotes parse files, etc.) from stdin or args
# write conll to stdout: <WORD>\t<POS>\t<PSD>
# note that we do not check whether special characters (e.g., #) are unescaped
cat $* | \
iconv -f utf-8 -t utf-8 -c | \
perl -pe '
	s/\t/    /g;								# TAB > 4 spaces (tab is special character in CoNLL)
	s/\n/ /g; 									# one sentence per line
	s/^(\()/\n$1/;
' | \
#perl -pe '
#	while(m/\([^\(\)]*\*[^\(\)]*\)/) {			# uncomment this to remove empty elements
#		s/\([^\(\)]*\*[^\(\)]*\)/*empty*/g;
#	}
#	s/ *\*empty\* */ /g;
#' |\
perl -pe '
	s/\) +/\)/g; 								# one word per line
	s/ +\(/ \(/g; 
	s/\n/\n\n/g; 
	s/(\)+)([^\)])/$1\n$2/g;
' | \
perl -pe 's/^(.*)\(([^\(\) ]+) ([^\)\( ]+)\)(.*)/$3\t$2\t$1*$4/;' |\
cat;