#!/bin/bash
echo 'use case for historical text: align medieval Low German passion commentaries (Interrogatio Sancti Anselmi de Passione Domini)' 1>&2;
echo 'source: https://www.linguistics.rub.de/anselm/corpus/german.html' 1>&2;
echo extract data sample, build CoNLL files and validates them 1>&2;

FILES="D2 D O Kh SP HA1521 StA1495";

BASE=`echo $0 | sed s/'.*\/'//g`;
TMP=/tmp/$BASE.tmp;
while [ -e $TMP ]; do
	TMP=/tmp/$BASE.`ls /tmp/$BASE* | wc -l`.tmp;
done;
echo > $TMP;

# (1) retrieve PDFs
if [ ! -d pdf ]; then mkdir pdf; fi;
cd pdf;
for file in $FILES; do
	if [ ! -e $file.pdf ]; then
		echo -n wget $file.pdf .. 1>&2;
		if wget https://www.linguistics.rub.de/anselm/pub/ed/$file.pdf >& $TMP; then
			echo . ok 1>&2;
		else
			echo .failed 1>&2;
			cat $TMP 1>&2;
		fi;
	fi;
done;
cd ..;

# (2) convert via (PDF) XML to conll (one token per line, 3 cols with reduced, original and simplified orthographies, simplified is a compromise)
if [ ! -d conll ]; then mkdir conll; fi;
for file in $FILES; do
	if [ -e pdf/$file.pdf ]; then
		if [ ! -e conll/$file.conll ]; then 
			echo -n $file.pdf via xml' ' 1>&2;
			if pdftohtml -xml -noframes -stdout pdf/$file.pdf; then 
				echo -n to $file.conll' ' 1>&2; 
			else echo "(check your pdftohtml installation, https://poppler.freedesktop.org/) " 1>&2;
			fi | \
			./xml2conll.sh > conll/$file.conll;
			if [ -s conll/$file.conll ]; then
				echo ... ok 1>&2;
			else 
				echo ... failed 1>&2;
			fi;
		fi;
	fi;
done;

# (3) merge with different configurations
if [ ! -d merged ]; then mkdir merged; fi;

echo -n CoNLLAlign conll/*.conll .. 1>&2;
./test.merge.sh -silent conll/*.conll > merged/merged.conll 2> merged/merged.log;
echo . ok 1>&2

echo -n CoNLLAlign -f .. 1>&2;
./test.merge.sh -silent -f conll/*.conll > merged/merged.f.conll 2> merged/merged.f.log;
echo . ok 1>&2;

echo -n CoNLLAlign -lev .. 1>&2;
./test.merge.sh -silent -lev conll/*.conll > merged/merged.lev.conll 2> merged/merged.lev.log;
echo . ok 1>&2;

echo -n CoNLLAlign -f -lev .. 1>&2;
./test.merge.sh -silent -f -lev conll/*.conll > merged/merged.lev.conll 2> merged/merged.f.lev.log;
echo . ok 1>&2;

# (4) pruning
rm $TMP;
for log in `find | grep '.log$'`; do
	if [ ! -s $log ]; then rm $log; fi;
done;