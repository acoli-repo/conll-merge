#!/bin/bash
if 
	javac ../cmd/pdgb2conll/PDGB2conll.java;
then
	java -classpath ../cmd/pdgb2conll PDGB2conll orig/pdgb/wsj_0655.pdgb.123 orig/pdgb/wsj_0655.pdgb.123-annotation1 > conll/wsj_0655.pdgb.conll
fi;