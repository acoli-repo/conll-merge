#!/bin/bash
echo normalize NomBank annotations to common representations 1>&2;
echo synopsis $0 nombank 1>&2;
echo writes unified/simplified pb-nb representation to stdout '(unsorted)' 1>&2;

NB=$1;

iconv -f utf-8 -t utf-8 -c $NB | \
sed -e s/'^\([^ ]* [0-9]* [0-9]*\) \([^ ]*\) \([0-9A-Za-z]*\) [^ ]* '/'\1 \2.n.\3 '/;
