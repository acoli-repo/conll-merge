#!/bin/bash
# no arguments, called from build-and-test.ALL.sh
# merge all converted files
mkdir mergeall >&/dev/null;

MERGED=mergeall/merged.conll;
SPLIT=mergeall/split.conll;
MERGED_F=mergeall/merged.f.conll;
SPLIT_F=mergeall/split.f.conll;
SPLIT_RETOK=mergeall/split.retok.conll;

if javac ../src/org/acoli/conll/CoNLLChecks.java; then
	
	if [ -e $MERGED ]; then echo found $MERGED, skipping 1>&2;
		else
		../cmd/merge.sh \
			conll/wsj_0655.ptb.on.conll \
			conll/wsj_0655.name.conll \
			conll/wsj_0655.coref.conll \
			conll/wsj_0655.rst.conll \
			conll/wsj_0655.pdgb.conll | \
		../cmd/merge.sh -- \
			conll/wsj_0655.pdtb.conll -drop 0 1 | \
		../cmd/merge.sh -- \
			conll/wsj_0655.prop.on.conll -drop 0 1 2 \
		> $MERGED;
		java -classpath ../src org.acoli.conll.CoNLLChecks $MERGED;
		echo 1>&2;
	fi;

	if [ -e $MERGED_F ]; then echo found $MERGED_F, skipping 1>&2;
		else
		../cmd/merge.sh \
			conll/wsj_0655.ptb.on.conll \
			conll/wsj_0655.name.conll \
			conll/wsj_0655.coref.conll \
			conll/wsj_0655.rst.conll \
			conll/wsj_0655.pdgb.conll -f | \
		../cmd/merge.sh -- \
			conll/wsj_0655.pdtb.conll -f -drop 0 1 | \
		../cmd/merge.sh -- \
			conll/wsj_0655.prop.on.conll -f -drop 0 1 2 \
		> $MERGED_F;
		java -classpath ../src org.acoli.conll.CoNLLChecks $MERGED_F;
		echo 1>&2;
	fi;

	if [ -e $SPLIT ]; then echo found $SPLIT, skipping 1>&2;
		else
		../cmd/merge.sh \
			conll/wsj_0655.ptb.on.conll \
			conll/wsj_0655.name.conll \
			conll/wsj_0655.coref.conll \
			conll/wsj_0655.rst.conll \
			conll/wsj_0655.pdgb.conll -split | \
		../cmd/merge.sh -- \
			conll/wsj_0655.pdtb.conll -split -drop 0 1 | \
		../cmd/merge.sh -- \
			conll/wsj_0655.prop.on.conll -split -drop 0 1 2 \
		> $SPLIT;
		java -classpath ../src org.acoli.conll.CoNLLChecks $SPLIT;
		echo 1>&2;
	fi;

	if [ -e $SPLIT_F ]; then echo found $SPLIT_F, skipping 1>&2;
		else
		../cmd/merge.sh \
			conll/wsj_0655.ptb.on.conll \
			conll/wsj_0655.name.conll \
			conll/wsj_0655.coref.conll \
			conll/wsj_0655.rst.conll \
			conll/wsj_0655.pdgb.conll -split -f | \
		../cmd/merge.sh -- \
			conll/wsj_0655.pdtb.conll -split -f -drop 0 1 | \
		../cmd/merge.sh -- \
			conll/wsj_0655.prop.on.conll -split -f -drop 0 1 2 \
		> $SPLIT_F;
		java -classpath ../src org.acoli.conll.CoNLLChecks $SPLIT_F;
		echo 1>&2;
	fi;

	if [ -e $SPLIT_RETOK ]; then echo found $SPLIT_RETOK, skipping 1>&2;
		else
		../cmd/retokenize.sh \
			$SPLIT \
			conll/wsj_0655.name.conll > $SPLIT_RETOK;
		java -classpath ../src org.acoli.conll.CoNLLChecks $SPLIT_RETOK;
		echo 1>&2;
	fi;
fi;
