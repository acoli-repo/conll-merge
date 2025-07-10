#!/bin/bash

if [ -e orig/ptb3/wsj_0655.mrg.srl ]; then rm orig/ptb3/wsj_0655.mrg.srl ; fi;
if [ -e orig/ontonotes/wsj_0655.prop.srl ]; then rm orig/ontonotes/wsj_0655.prop.srl; fi;

if javac ../cmd/propbank2conll/*.java; then

	# (A) PTB+PropBank-NomBank workflow
		# (0) extract PropBank-style be.01/be.02 annotations from PTB
		java -classpath ../cmd/propbank2conll AddBe orig/ptb3/wsj_0655.mrg > orig/propbank/bes.excerpt.txt;

		# (1) preprocessing
		(
			# (1.a) preprocess PropBank (and bes)
			../cmd/propbank2conll/normalize.propbank.sh orig/propbank/propbank.1.prop.excerpt.txt orig/propbank/bes.excerpt.txt;
			
			# (1.b) preprocess NomBank
			../cmd/propbank2conll/normalize.nombank.sh orig/propbank/nombank.1.0.excerpt.txt
		) | \
		sed -e s/'^.*\/wsj_0655[^ ]*'/'orig\/ptb3\/wsj_0655.mrg'/ | 			# we need to redirect to the actual PTB file location
		../cmd/propbank2conll/splitter.sh;										# writes (apppends) to orig/ptb3/wsj_0655.mrg.srl as defined in last line

		# (2) CoNLL conversion
		if [ -e conll/wsj_0655.ptb.conll ]; then
			java -classpath ../cmd/propbank2conll SRL2Conll orig/ptb3/wsj_0655.mrg.srl conll/wsj_0655.ptb.conll > conll/wsj_0655.prop.conll
		else
			echo did not find conll/wsj_0655.ptb.conll, please run ./test.ptb2conll.sh 1>&2;
		fi;
		
	# (B) OntoNotes workflow
		# (1) preprocess OntoNotes
		../cmd/propbank2conll/normalize.ontonotes-prop.sh orig/ontonotes/wsj_0655.prop > orig/ontonotes/wsj_0655.prop.srl

		# (2.b) CoNLL conversion
		if [ -e conll/wsj_0655.ptb.on.conll ]; then
			java -classpath ../cmd/propbank2conll SRL2Conll orig/ontonotes/wsj_0655.prop.srl conll/wsj_0655.ptb.on.conll > conll/wsj_0655.prop.on.conll
		else
			echo conll/wsj_0655.ptb.on.conll, please run ./test.ptb2conll.sh first 1>&2;
		fi;
fi;
