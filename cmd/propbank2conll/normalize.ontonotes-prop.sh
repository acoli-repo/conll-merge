#!/bin/bash
echo simplify OntoNotes props in accordance with merged nombank-propbank format in split version 1>&2;
echo synopsis $0 FILE.prop 1>&2;
echo writes unified/simplified pb-nb representation to stdout '(unsorted)' 1>&2;
echo note that OntoNotes files are split per files only, hence, the file name is skipped in the output (cf. splitter.sh output) 1>&2;

iconv -f utf-8 -t utf-8 -c $1 | \
sed -e s/'^nw\/\([^@ ]*\)@[^ ]* \([0-9]* [0-9]*\) [^ ][^ ]* [^ ]*-\([vn]\) \([^ ]*\.\)\([0-9]*\) [^ ]*'/'\2 \4\3.\5'/;