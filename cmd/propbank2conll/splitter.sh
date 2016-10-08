#!/bin/bash
# split PropBank/NomBank files such that one file per source document is created
cat $* | \
iconv -f utf-8 -t utf-8 -c | \
sed s/'^\([^ ]*\) \(.*\)$'/'echo "\2" >> \1.srl'/ | \
bash -e