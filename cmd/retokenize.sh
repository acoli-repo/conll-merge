#!/bin/bash
echo 'wrap merge.sh to apply CoNLLAlign to retokenize the first conll file according to the second, write to stdout' 1>&2
echo 'note that we do *not* copy the annotations of the second file to the first' 1>&2
echo 'synopsis: '`echo $0 | sed s/'.*\/'//`' FILE1 FILE2' 1>&2;
echo '  FILE1 CoNLL source file, if --, then read from stdin '1>&2;
echo '  FILE2 CoNLL file with target tokenization in the first (!) column' 1>&2;

###############################
# extract target tokenization #
###############################

	HOME=`echo $0 | sed s/'\/[^\/]*$'/'\/'/`./;
	HOME=`cd $HOME; pwd`;

	TMP=$0.tmp;
	while [ -e $TMP ]; do
		TMP=$0.`ls $0*tmp |wc -l`.tmp;
	done;
	echo > $TMP;
	
	sed s/'\t.*'//g $2 > $TMP;

	TMP2=$TMP.bak;
	
	if [ $1 = '--' ]; then
		echo read from stdin 1>&2;
		cat > $TMP2;
	else 
		echo read from $1 1>&2;
		cp $1 $TMP2;
	fi;

##############################
# retokenization and cleanup #
##############################
	
	TMP_U=$TMP;			# unix version (original will be OS version)
	TMP2_U=$TMP2;
	
	if echo $OSTYPE | grep -i cygwin >/dev/null; then
		TMP=`cygpath -wa $TMP | perl -pe 's/\n//g;'`;
		TMP2=`cygpath -wa $TMP2 | perl -pe 's/\n//g;'`;
	fi;

	$HOME/merge.sh $TMP $TMP2 -f;
	
	rm $TMP_U $TMP2_U;
