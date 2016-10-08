#!/bin/bash
echo restore conventional names for PDGB files
echo synopsis: $0 file-correspondence-table file1[..n] 1>&2
for file in $*; do
	if [ $file != $1 ]; then
		nr=`echo $file | sed -e s/'.*\/'//g -e s/'[^0-9].*'//g`;
		orig=`egrep -m 1 '^'$nr'[^0-9]' $1 | sed -e s/'^'$nr'[ \t]*'//`
		mv $file `echo $file | sed s/'\/[^\/]*$'//`/$orig'.pdgb.'`echo $file | sed -e s/'.*\/'//g`;
		echo -n $nr' ' 1>&2;
	fi;
done;