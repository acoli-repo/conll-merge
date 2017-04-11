#!/bin/bash
# pdftohtml.xml to txt for MLG Anselm
# (this is domain and language specific, hence not in cmd)
cat $* | \
iconv -f utf-8 -t utf-8 | \
grep '<text[^>]*font="1"' | \
perl -pe 's/([^ ])(<\/text>)/$1-$2/;' | \
sed -e s/'<[^>]*>'//g \
	-e s/'&lt;'//g \
	-e s/'&gt;'//g \
	-e s/'[()*]'//g \
	-e s/'\[\([^0-9]*\)\]'/'\1'/g \
	-e s/'\[.*\]'//g | \
perl -pe 's/\s+/ /gs;' |\
sed -e s/'[=-] '//g \
	-e s/'  *'/'\n'/g | \
#	-e s/' *\([\/\\.;?!:]\) *'/' \1 '/g \
	# add first column with normalized orthography
perl -e '
	while(<>) {
		s/\n/\t/g;
		print $_;					# original string, incl. punctuation, diacritics, capital case, etc.
		
		s/(.)/\l$1/g;				# lowercase, ascii, remove duplicate graphemes, remove punctuation
		s/ā/a/g;					# (not language-specific)
		s/ö/o/g;
		s/ü/u/g;
		s/ü/u/g;
		s/[ëė]/e/g;
		s/ſ/s/g;
		s/ʒ/z/g;
		s/[ýẏÿẏẏÿẏÿẏẏẏÿȳ]/y/g;
		s/[^\ta-z]+//g;		
		while(m/.*(.)\1.*/) {
			s/(.)\1/\1/g;			# remove duplicates
		}
		print;						# out: "regular" normalized MLG
		
									# simplified orthography (for matching only, language-specific)
		s/cz/tz/g;
		s/oe/o/g;
		s/ae/a/g;
		s/ao/a/g;
		s/ue/u/g;
		s/[jy]/i/g;
		s/c?k/c/g;
		s/uw/u/g;
		s/[fwv]/u/g;
		s/sch/sc/g;
		s/th/t/g;
		s/[cg]h/g/g;
		s/^ih/i/g;
		s/z/s/g;
		s/dt/d/g;
		s/d\t/t\t/g;				# auslautverhärtung, often written dt => d, e.g., godt
		print;
		print "\n";
	}' | \
sed -e s/'^\([^\t]*\)\t\([^\t]*\)\t\([^\t]*\)\t$'/'\3\t\1\t\2'/g
