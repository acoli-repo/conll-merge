#!/bin/bash
# simplified elan2conll converter, expects first tier to contain tokens, supports time-anchored annotations only
# synopsis: eaf2conll.sh file1[..n].eaf
# append file name as last column

for file in $*; do
	echo processing $file 1>&2;
	FILE=`echo $file |sed s/'.*\/'//g`;
	xsltproc eaf2tsv.xsl $file | \
	./aggregate.py  | \
	cut -f 1,3-4,6-16 | \
	sed s/'\(..*\)$'/'\1\t'$FILE/;
	echo
done |\
# split sentences
sed s/'\(\$\..*\)$'/'\1\n'/;
echo "done" 1>&2