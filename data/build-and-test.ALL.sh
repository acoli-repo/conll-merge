#!/bin/bash
echo extract data sample, build CoNLL files and validates them 1>&2;

mkdir conll >&/dev/null;

ERROR=false;
if [ `find orig 2>/dev/null | grep wsj_0655 | wc -l` -gt 0 ]; then 
	ERROR=false;
else 
	if unzip data.zip ; then 
		ERROR=false; 
	else 
		ERROR=true;
	fi;
fi;

if echo $ERROR | grep true >&/dev/null ; then
	echo 1>&2
	echo For reasons of copyright, the data samples are PASSWORD-PROTECTED. 1>&2;
	echo To acquire the password, please make sure you have the rights to 1>&2;
	echo work with the data and contact christian.chiarcos@web.de. 1>&2;
	echo 1>&2
	echo Alternatively, contact the LDC directly to acquire the file wsj_0655 1>&2;
	echo from PTB3, OntoNotes, PDTB2, RST-DTB, PDGB '(file 123)', PropBank and NomBank 1>&2;
	echo 'PS: internal password hint: i' 1>&2;
else
	for file in \
	test.ptb2conll.sh \
	test.name2conll.sh \
	test.prop2conll.sh \
	test.coref2conll.sh \
	test.pdtb2conll.sh \
	test.rst2conll.sh \
	test.pdgb2conll.sh; do
		echo test `echo $file | sed s/'^test\.'//` 1>&2;
		./$file 2>&1 | tee $file.log | \
		sed s/'...............................................................................'/'&\n'/g | \
		sed s/'^'/'    '/g 1>&2;
		echo 1>&2;
	done;
fi;
