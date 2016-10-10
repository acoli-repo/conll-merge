#!/bin/bash
echo normalize PropBank annotations 1>&2;
echo synopsis $0 FILE1[..n] 1>&2;
echo writes unified/simplified pb-nb representation to stdout '(unsorted)' 1>&2;

for file in $*; do
	iconv -f utf-8 -t utf-8 -c $file | \
	sed -e s/'^\([^ ]* [0-9]* [0-9]*\) [^ ][^ ]* \([^ ]*\)\.\([0-9A-Za-z]*\) [^ ]* '/'\1 \2.v.\3 '/;
done