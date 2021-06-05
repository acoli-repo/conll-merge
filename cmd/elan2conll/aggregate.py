#!/usr/bin/python3.6
#!/bin/bash
# simplified ELAN converter (no warranty, but should work with DDD Heliand)
# synopsis: eaf2conll.sh FILE1[..n].eaf

# for file in $*; do
	# xsltproc eaf2conll.xsl $file 


# done
import sys

#input=open("Hel_38.conll","r")

fields=sys.stdin.readline().replace("\n","").split("\t")
f1=sys.stdin.readlines()
for line in f1:
	additions=line.replace("\n","").split("\t")
	if(len(additions[0])>0):
		for f in fields:
			if(f.strip()==''):
				f="*"
			print(f,end="\t")
		print("")
		fields=additions
	else:
		for i in range(len(fields)):
			if(fields[i].strip()==''):
				fields[i]=additions[i]
			elif(additions[i].strip()!=''):
				fields[i]=fields[i]+"+"+additions[i]
for f in fields:
	if(f.strip()==''):
		f="*"
	print(f,end="\t")
print("")