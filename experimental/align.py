import sys,os,re,traceback,myers
from pprint import pprint
from weighted_levenshtein import lev
import unicodedata

"""
	Given two CoNLL files, perform an alignment based on string identity (Myer's
	diff) and similarity (Levenshtein distance). This is to be used to align
	different versions of the same text *in the same language*.
"""

# arguments: conll files
# given a context window
# partial reimplementation of CoNLL merge in Python

def norm(input_str):
	""" levenshtein implementation requires ascii """
	try:
		input_str=input_str.decode("utf-8")
	except:
		pass
	nfkd_form = unicodedata.normalize('NFKD', input_str)
	no_accents= u"".join([c for c in nfkd_form if not unicodedata.combining(c)])
	only_ascii = no_accents.encode('ASCII', 'ignore')
	return only_ascii.lower()

def merge_levenshtein(pool,buffers,cols,lens,matrix, *flags):
	""" pool contains offsets of buffers that are to be aligned """
	#print("merge_levenshtein(",pool,"buffers",cols,lens,matrix,flags,")")
	if len(buffers)!=2:
		raise Exception("no support for aligning more than two files, yet")

	result=[]
	if len(pool[0])==0:
		for y in pool[1]:
			if "force" in flags and len(result)>0:
				result[-1]=result[-1][0:lens[0]]+[ re.sub(r"(^[\?_\*]\+)?(.*)(\+[\?_\*])?$",r"\2",val1+"+"+val2) for val1,val2 in zip(result[-1][lens[0]:],buffers[1][y]) ]
			else:	# default mode
				if len(buffers[1][y])>cols[1]:		# we skip empty lines
					newrow=[ "?" ] * (cols[0]) + ["*"+buffers[1][y][cols[1]]+"*"] + ([ "?" ] * (lens[0]-cols[0]-1)) + buffers[1][y]
					result.append(newrow)

		return result


	if len(pool[1])==0:
		for x in pool[0]:
			if len(buffers[0][x])==1 and buffers[0][x][0]=="":
				result.append(buffers[0][x])
			else:
				result.append( buffers[0][x]+["?"]*lens[1])
		return result

	if matrix==None:
		matrix=[]
		for x in pool[0]:
			matrix.append([])
			src=buffers[0][x][0]
			try:
				src=buffers[0][x][cols[0]]
			except:
				pass
			for y in pool[1]:
				tgt=buffers[1][y][0]
				try:
					tgt=buffers[1][y][cols[1]]
				except:
					pass
				src=norm(src)
				tgt=norm(tgt)
				if(max(len(src), len(tgt))==0):
					matrix[-1].append(1.0)
				else:
					#print(src,tgt,lev(src,tgt))
					matrix[-1].append(1.0-lev(src,tgt)/max(len(src),len(tgt)))	# leventhein similarity!

	max_x=0
	max_y=0
	min_dist=0
	max_sim=0
	try:
		min_dist=abs(max_x/len(pool[0]) - max_y/len(pool[1]))	# secondary criterion for equal similarity
		max_sim=matrix[0][0]
	except:
		pass

	#print("matrix:",matrix)
	for x in range(len(pool[0])):
		for y in range(len(pool[1])):
			sim=matrix[x][y]
			dist=abs(x/len(pool[0]) - y/len(pool[1]))
			#print(sim,max_sim,dist,min_dist)
			if(sim>max_sim or (sim==max_sim and min_dist>dist)):
				min_dist=dist
				max_sim=sim
				max_x=x
				max_y=y

	result=[]

	# "i" and "r" before alignment
	if max_x==0: # "i"
		for y in pool[1][0:max_y]:
			if "force" in flags and len(result)>0:
				result[-1]=result[-1][0:lens[0]]+[ re.sub(r"(^[\?_\*]\+)?(.*)(\+[\?_\*])?$",r"\2",val1+"+"+val2) for val1,val2 in zip(result[-1][lens[0]:],buffers[1][y]) ]
			else:	# default mode
				if len(buffers[1][y])>cols[1]:
					# print(buffers[1][y])
					result.append( [ "?" ] * cols[0] + ["*"+buffers[1][y][cols[1]]+"*"] + [ "?" ] * (lens[0]-cols[0]-1) + buffers[1][y])
	elif max_y==0: # "r"
		for x in pool[0][0:max_x]:
			if len(buffers[0][x])==1 and buffers[0][x][0]=="":
				result.append(buffers[0][x])
			else:
				result.append( buffers[0][x]+["?"]*lens[1])
	else:
		sub_pool=[ pool[0][0:max_x], pool[1][0:max_y] ]
		#print(pool,max_x,max_y,"=>",sub_pool)
		result=merge_levenshtein(sub_pool, buffers,cols,lens,matrix,*flags)

	# max alignment
	if len(pool[0])>0 and len(pool[1])>0:
		result.append(buffers[0][pool[0][max_x]]+buffers[1][pool[1][max_y]])

	# align final elements
	if max_x==len(pool[0])-1: # "i"
		for y in pool[1][max_y+1:]:
			if "force" in flags and len(result)>0:
				result[-1]=result[-1][0:lens[0]]+[ re.sub(r"(^[\?_\*]\+)?(.*)(\+[\?_\*])?$",r"\2",val1+"+"+val2) for val1,val2 in zip(result[-1][lens[0]:],buffers[1][y]) ]
			else:	# default mode
				if len(buffers[1][y])>cols[1]:
					result.append( [ "?" ] * cols[0] + ["*"+buffers[1][y][cols[1]]+"*"] + [ "?" ] * (lens[0]-cols[0]-1) + buffers[1][y])
	elif max_y==len(pool[1])-1: # "r"
		for x in pool[0][max_x+1:]:
			if len(buffers[0][x])==1 and buffers[0][x][0]=="":
				result.append(buffers[0][x])
			else:
				result.append( buffers[0][x]+["?"]*lens[1])
	else:
		# recursion for non final elements
		sub_pool=[ pool[0][max_x+1:], pool[1][max_y+1:] ]
		result=result+merge_levenshtein(sub_pool, buffers,cols,lens,matrix,*flags)

	return result


def fill_buffers(window, buffers, inputs, cols):
	#print("fill_buffers(",window,buffers,inputs,cols,")")
	additions=0
	for x,input in enumerate(inputs):
		try:
			line="\n"
			while(len(buffers[x])<window):
				line=input.readline()
				if line=="":
					raise Exception("end of file")
				else:
					line=line.rstrip("\n")
					if line=="":
						buffers[x].append("".split("\t"))
					else:
						if x > 0:	# we keep comments of the first file only
							if line.strip().startswith("#"):
								line=""
						if x <len(buffers)-1:
							line=re.sub(r"([^\\])#.*",r"\1",line) #  we keep inline comments of *last* file only

						if x==0 or len(line)>0:	#	we drop line breaks of non-first files
							row=line.split("\t")
							buffers[x].append(row)
							additions+=1

		except:
			pass
	if additions==0:
		raise Exception("no additions made")
	else:
		return buffers

def align(buffers,cols):
	# print("align(",buffers,cols,")")
	if len(buffers)==2:
		return [ myers.diff([ row[cols[0]] if len(row)>cols[0] else row[0] for row in buffers[0]  ], [row[cols[1]] if len(row)>cols[1] else row[0] for row in buffers[1]  ]) ]
	if len(buffers)>2:
		return align(buffers[0:2], cols[0:2]) + align([buffers[0]]+buffers[2:], [cols[0]]+cols[2:])
	raise Exception("alignment requires two arguments, at least")

def merge(buffers,cols,align_all, *flags):
	""" calls align(), then performs integration, keep merged column, return merge and remaining buffer.
		align_all means that the buffer is competely aligned, otherwise, we terminate at the last k alignment
		flags include "lev" and "force". If combined, run lev mode, then apply force merging to the result.
	"""
	#print("merge(buffers,cols,align_all=",align_all,", min_size=",min_size,",",", ".join(flags))

	# print("merge(",buffers,cols,align_all,")")
	alignment=align(buffers,cols)

	result=[]
	offsets=[0 for buffer in buffers ]
	pool=[ [] for buffer in buffers ]	#	alignment pool (for levenshtein)
	if len(buffers)==2:
		alignment=alignment[0]
		codes=[ c for c,v in alignment ]
		for x,code in enumerate(codes):
			if len(result)>0 and not align_all and set(alignment[x:]) in [set(["r"]), set(["i"])]:
				return result, [buffer[offset:] for buffer,offset in zip(buffers,offsets)]
			else:
				if(code=="i"):
					if "lev" in flags:
						pool[1].append(offsets[1])
					elif "force" in flags and len(result)>0:
						result[-1]=result[-1][0:lens[0]]+[ re.sub(r"(^[\?_\*]\+)?(.*)(\+[\?_\*])?$",r"\2",val1+"+"+val2) for val1,val2 in zip(result[-1][lens[0]:],buffers[1][offsets[1]]) ]
					else:	# default mode
						result.append( [ "?" ] * cols[0] + ["*"+buffers[1][offsets[1]][cols[1]]+"*"] + [ "?" ] * (lens[0]-cols[0]) + buffers[1][offsets[1]])
					offsets[1]+=1
				elif code=="r":
					if "lev" in flags:
						pool[0].append(offsets[0])
					elif len(buffers[0][offsets[0]])==1 and buffers[0][offsets[0]][0]=="":
						result.append(buffers[0][offsets[0]])
					else:
						result.append( buffers[0][offsets[0]]+["?"]*lens[1])
					offsets[0]+=1
				elif code=="k":
					if "lev" in flags: # lev
						#print("k")
						result=result+merge_levenshtein(pool,buffers,cols,lens,None,*flags)
						pool=[ [] for buffer in buffers ]
					result.append(buffers[0][offsets[0]]+buffers[1][offsets[1]])
					offsets[0]+=1
					offsets[1]+=1
					if(not align_all and not "k" in codes[x+1:]):
						break
				else:
					if "lev" in flags: # lev
						#print("o")
						result=result+merge_levenshtein(pool,buffers,cols,lens,None,*flags)
						pool=[ [] for buffer in buffers ]
					raise Exception("unknown code \""+str(code)+"\"")
					# should be "o" for omit (how is that different from remove?)

				# result[-1]=[code]+result[-1]
				# result[-1]=result[-1]+[str(len(result[-1]))]

		if "lev" in flags and (align_all==True or len(result)==0): # lev
			#print("other")
			result=result+merge_levenshtein(pool,buffers,cols,lens,None,flags)
			pool=[ [] for buffer in buffers ]

		if max([len(p) for p in pool]) > 0:
			for x,p in enumerate(pool):
				if len(p)>0:
					offsets[x]=p[0]

		return result, [buffer[offset:] for buffer,offset in zip(buffers,offsets)]

	else:
		raise Exception("alignment between more than 2 buffers not yet implemented")

def help():
	sys.stderr.write(sys.argv[0]+" WINDOW FILE1.conll[=COL] .. FILEn.conll[=COL]\n"+
		"\tWINDOW      integer that defines the context size\n"+
		"\tFILEi.conll CoNLL (TSV) file, can be replaced by -- when reading input from stdin\n"+
		"\tCOL         column to be used for alignment, defaults to 0 (first column)\n")

try:
	window=int(sys.argv[1])
except:
	help()
	sys.exit(1)

cols=[]

inputs=[]
sys.stderr.flush()

for input in sys.argv[2:]:
	col=0
	try:
		col=int(re.sub(r".*=","",input))
		input=re.sub(r"=[0-9]+$","",input)
	except:
		pass
	if input in ["--","-"]:
		inputs.append(sys.stdin)
	else:
		inputs.append(open(input))
	cols.append(col)

# sys.stderr.write(str(dict(zip(inputs,cols)))+"\n")
# sys.stderr.flush()

buffers=[ [] for input in inputs ]
lens=None

while buffers!=None:
	merged=[]
	try:
		#print("f")
		buffers=fill_buffers(window, buffers, inputs,cols)
		#print(".",end="")
		if not lens:
			lens=[]
			for b in buffers:
				lens.append(max([len(row) for row in b ]))

		#print(buffers)
		#print("m",end="")
		merged,buffers=merge(buffers,cols,False,window*2.0/3.0,"lev")
		#print("LEN(MERGED):",len(merged))
		if len(merged)==0:
			#print("l",end="")
			merged,buffers=merge(buffers,cols,True,window,"lev")
			if(len(merged)==0):
				#print("b",end="")
				break
		#print("p",end="")
	except:
		#print("L")
		merged,buffers=merge(buffers,cols,True,window,"lev")

	#pprint(merged)
	print("\n".join( [ "\t".join(row) for row in merged ] ) )
	if len(merged)==0:
		break

#print("c")
merged,_=merge(buffers,cols, True, window, "lev")
print("\n".join( [ "\t".join(row) for row in merged ] ) )

for input in inputs:
	input.close()
