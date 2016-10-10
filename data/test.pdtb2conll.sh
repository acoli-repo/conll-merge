#!/bin/bash
if 
	javac ../cmd/pdtb2conll/PDTB2conll.java;
then
	cat orig/pdtb2/wsj_0655.pdtb | java -classpath ../cmd/pdtb2conll PDTB2conll > conll/wsj_0655.pdtb.conll
fi;