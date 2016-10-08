#!/bin/bash
echo $0: read RSTDTB *dis file from stdin and write PTB-CoNLL-style lisp file to stdout 1>&2;
perl -pe '
	s/\/\/TT_ERR//g;																# some kind of errors
	
	s/(Nucleus|Satellite) \((span|leaf)[^\)]*\) \(rel2par ([^\)]*)\)/$3_\l$1/g;		# map to PTB-like lisp structures (text-level)
	s/_satellite//g;
	s/span_(nucleus)/$1/g;
	s/(\() Root \([^\)]*\)/$1/g;
	while (m/.*\(text _![^_ ]* .*/) {
		s/(\(text _!)([^_ ]*) /$2 $1/g;
	}
	s/\(text _!([^_ ]*)_!\)/$1/g;
	s/\n/ /gs;
	s/\(\s+/\(/gs;
	s/\s+\)/ \)/gs;
	s/\s+/ /gs;
' | \
perl -pe '
	s/(\))\s*(\()/$1\n\n$2/g; 														# split edus
	s/\(\s+/\(/gs;
	while(m/.*( [a-zA-Z0-9]*)([^_ \(\)\na-zA-Z0-9]+)([a-zA-Z0-9]+ ).*/) {			# heuristic/greedy/orthography-based tokenization
		s/( [a-zA-Z0-9]*)([^_ \(\)\na-zA-Z0-9]+)([a-zA-Z0-9]+ )/$1 $2 $3/g;
	}
	while(m/.*( [a-zA-Z0-9]+)([^_ \(\)\na-zA-Z0-9]+)([a-zA-Z0-9]* ).*/) {
		s/( [a-zA-Z0-9]+)([^_ \(\)\na-zA-Z0-9]+)([a-zA-Z0-9]* )/$1 $2 $3/g;
	}
	s/([^_ \(\)\na-zA-Z0-9])([^_ \(\)\na-zA-Z0-9] +\))/$1 $2/g;
	s/  +/ /g;
' | \
perl -pe '																			# create conll-like columns
	s/^/\t/g;
	s/(\t.* )([^\(\) ]+)( )([^\(\) ]+)( )/$2$1*$3\n$4\t*$5/;
	while(m/^([^\t\n]*)(\t.* )([^\(\) \n]+)( \*).*/) {
		s/^([^\t\n]*)(\t.* )([^\(\) \n]+)( \*)/$3$2*\n$1\t$4/;
	};
	s/(\*) +([^\)\( \n\*]+) +\)/$1\n$2\t\* \)/g;
'
# tokenize