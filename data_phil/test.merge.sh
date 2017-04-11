#!/bin/bash
# align all argument files with each other
# apply the flags specified as arguments

FLAGS=`for arg in $*; do if [ ! -e $arg ]; then echo $arg; fi; done;`;
FILES=`for arg in $*; do if [ -e $arg ]; then echo $arg; fi; done;`;
file1=`echo $FILES | sed s/'\([^ \t]\)[ \t].*'/'\1'/g`;

if echo $FILES | grep '^$' >&/dev/null; then 
	echo "synopsis: $0 [FLAG1..n] FILE1[..n]" 1>&2;
	echo "  FLAGi flags, cf. CoNLLAlign, optional" 1>&2;
	echo "  FILEi files to be merged, required" 1>&2;
else
	PATH_SEPARATOR=':';
	if echo $OSTYPE | grep -i 'cygwin' >&/dev/null; then PATH_SEPARATOR=';'; fi;
	if 
		javac -classpath ../src/$PATH_SEPARATOR../lib/diffutils-1.2.1.jar ../src/org/acoli/conll/*.java;
	then
		(echo -n '#';
		for file in $FILES; do
			echo -n '<t>'$file'<t>';
		done;
		echo) | sed -e s/'<t>'/'\t'/g -e s/'\.[^\t\.]*'//g -e s/'[^\t]*[\\\/]'//g;

		TMP=$0.tmp;
		if [ -e $TMP ]; then
			TMP=$0.`ls $0* | wc -l`.tmp;
			echo > $TMP;
		fi;
		TMP2=$0.`ls $0* | wc -l`.tmp;
		echo > $TMP2;
		
		cp $file1 $TMP;
		for file in $FILES; do
			if [ $file != $file1 ]; then
				java -classpath ../src/$PATH_SEPARATOR../lib/diffutils-1.2.1.jar \
					org/acoli/conll/CoNLLAlign $TMP $file $FLAGS | sed s/'^\*RETOK\*-'//g > $TMP2; 
					cp $TMP2 $TMP;
			fi;
		done;
		sed -e s/'^[^\t]*\t'// $TMP;

		rm $TMP $TMP2;
	fi;
fi;