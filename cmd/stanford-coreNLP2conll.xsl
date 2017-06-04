<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:d="http://nlp.stanford.edu/CoreNLP/v1">
    
    <!-- 
        convert Stanford CoreNLP XML to a CoNLL-like representation
        developed for version 3.7.0
        
        Christian Chiarcos, chiarcos@informatik.uni-frankfurt.de
        2017-06-03
        
        (as the Stanford CoreNLP does not provide a schema, this 
        implementation follows the provided example and
        https://stanfordnlp.github.io/CoreNLP/files/CoreNLP-to-HTML.xsl)
        
        Unlike the native CoNLL export, this conversion is lossless,
        however, we only return columns for which annotations do exist.
        For the exact columns, thus consult the header of the generated file
    -->
    <xsl:output method="text" indent="no"/>

    <!-- annotation layers for tokens -->
    <!-- if not provided as parameter, tokenFeats is determined by *exhaustive* search; 
         if provided as parameter, tokenFeats must be tokenized first, hence use for-each select="$splitTokenFeats", instead
    -->
    <xsl:param name="tokenFeats" select="//token/*[count(.|key('tokenFeatKeys',name())[1])=1]/name()"/>
    <xsl:key name="tokenFeatKeys" match="//token/*" use="name()"/>
    <xsl:variable name="splitTokenFeats" select="tokenize(normalize-space(string-join($tokenFeats,' ')),' ')"/>
    
    <!-- dependencies types -->
    <!-- not a parameter, but determined from the first sentence.
    we assume this is possible in this case because the levels of syntactic annotation are the same all over, whereas token-level annotations may have gaps -->
    <xsl:variable name="dependencyTypes" select="(//dependencies)[1]/../dependencies/@type"/>
    
    <xsl:template match="/">
        <!-- header -->
        <xsl:text># Id</xsl:text>
        
        <!-- token-level features -->
        <xsl:for-each select="$splitTokenFeats">
            <xsl:text>&#9;</xsl:text>
            <xsl:value-of select="."/>
        </xsl:for-each>
        
        <!-- phrase structure parse tree -->
        <xsl:if test="exists(//parse)">
            <xsl:text>&#9;parse</xsl:text>
        </xsl:if>
            
        <!-- dependency types -->
        <xsl:for-each select="$dependencyTypes">
            
            <!-- we number HEAD and EDGE pairs rather than giving the full (rather unreadable) name -->
            <xsl:text>&#9;HEAD</xsl:text>
            <xsl:if test="position()>1">
                <xsl:value-of select="position()"/>
            </xsl:if>
            <xsl:text>&#9;EDGE</xsl:text>
            <xsl:if test="position()>1">
                <xsl:value-of select="position()"/>
            </xsl:if>
        </xsl:for-each>
        
        <!-- coreference -->
        <xsl:if test="exists(//coreference)">
            <xsl:text>&#9;coreference</xsl:text>
            <xsl:text>&#9;representative</xsl:text>
        </xsl:if>
        
        <xsl:text>&#10;</xsl:text>
        
        <!-- explain levels of dependency annotation -->
        <xsl:for-each select="$dependencyTypes">
            
            <xsl:text># HEAD</xsl:text>
            <xsl:if test="position()>1">
                <xsl:value-of select="position()"/>
            </xsl:if>
            <xsl:text>, EDGE</xsl:text>
            <xsl:if test="position()>1">
                <xsl:value-of select="position()"/>
            </xsl:if>
            <xsl:text>: </xsl:text>
            <xsl:value-of select="."/>
            <xsl:text>&#10;</xsl:text>
        </xsl:for-each>
        
        <xsl:text>&#10;</xsl:text>

        <!-- data -->
        
        <xsl:for-each select="//sentences/sentence">
            <xsl:text># </xsl:text>
            <xsl:choose>
                <xsl:when test="@id!=''">
                    <!-- this is found in the sample data -->
                    <xsl:value-of select="@id"/>
                </xsl:when>
                <xsl:otherwise>
                    <!-- this is the strategy of the HTML generator -->
                    <xsl:value-of select="position()"/>
                </xsl:otherwise>
            </xsl:choose>
            <xsl:text>&#10;</xsl:text>
            
            <!-- preprocess sentence-level annotations, i.e., parse -->
            <xsl:variable name="parse" select="./parse/text()"/>   <!-- original parse -->
            
            <xsl:variable name="starParse">  <!-- replace every terminal node with a * -->
                <!-- this is tricky because the parse might contain * as a word or as an 
                     empty element, so "our" * are the only ones surrounded by spaces -->
                <xsl:value-of select="
                    replace(
                        replace(
                            replace(
                                normalize-space($parse),
                                '[*] +\)','*)'),
                            ' +[*] ','* '),
                            '\([^\(\)*]*\)',' * ')"/>
            </xsl:variable>
            
            <!-- annotate one token per line -->
            <xsl:for-each select="tokens/token">
                <xsl:variable name="token" select="." as="node()*"/>
                
                <!-- Id -->
                <xsl:value-of select="@id"/>
                <xsl:if test="@id=''">
                    <xsl:value-of select="position()"/>
                </xsl:if>
                
                <!-- token-level annotations -->
                <xsl:for-each select="$splitTokenFeats">
                    <xsl:text>&#9;</xsl:text>
                    <xsl:variable name="feat" select="."/>
                    <xsl:value-of select="normalize-space(string-join($token[1]/*[name()=$feat]/text(),' '))"/>
                    <xsl:if test="count($token[1]/*[name()=$feat][1])=0">
                        <xsl:text>_</xsl:text>
                    </xsl:if>
                </xsl:for-each>
                
                <!-- phrase structure parse tree -->
                <!-- The parse is given as a plain string in accorance to the PTB mrg notation (terminal nodes include POS and WORD. This is decomposed into the word-based notation used since CoNLL-2005.-->
                 <xsl:if test="exists(//parse)">
                     <xsl:text>&#9;</xsl:text>
                     <xsl:choose>
                         <xsl:when test="normalize-space($parse)=''">
                             <xsl:text>_</xsl:text>
                         </xsl:when>
                         <xsl:otherwise>
                             <xsl:variable name="me"
                             select="tokenize($starParse,' \* ')[position()=count($token[1]/preceding-sibling::token)+1]"/>
                             <xsl:variable name="next"  select="tokenize($starParse,' \* ')[position()=count($token[1]/preceding-sibling::token)+2]"/>
                             <xsl:variable name="syntax">
                                 <xsl:value-of select="replace($me,'^[\) ]+','')"/>
                                 <xsl:text> *</xsl:text>
                                 <xsl:value-of select="replace($next,'[^\) ].*','')"/>
                             </xsl:variable>
                             <xsl:value-of select="normalize-space($syntax)"/>
                         </xsl:otherwise>
                     </xsl:choose>
                </xsl:if>
                
                <!-- dependency annotations -->
                <!-- Note that in addition to traditional CoNLL dependencies, we permit multiple HEADS and EDGES in a cell, separated by whitespace. This may occur for collapsed dependencies, but is not necessary for `conventional' basic dependencies.
                -->
                <xsl:for-each select="$dependencyTypes">
                    <xsl:variable name="type" select="."/>
                    <xsl:variable name="HEADS" select="string-join($token[1]/../../dependencies[@type=$type]/dep/dependent[@idx=$token/@id]/../governor[1]/@idx,' ')"/>
                    <xsl:variable name="EDGES" select="string-join($token[1]/../../dependencies[@type=$type]/dep/dependent[@idx=$token/@id]/../@type,' ')"/>
                    <xsl:text>&#9;</xsl:text>
                    <xsl:value-of select="$HEADS"/>
                    <xsl:if test="$HEADS=''">
                        <xsl:text>_</xsl:text>
                    </xsl:if>
                    <xsl:text>&#9;</xsl:text>
                    <xsl:value-of select="$EDGES"/>
                    <xsl:if test="$EDGES=''">
                        <xsl:text>_</xsl:text>
                    </xsl:if>
                </xsl:for-each>
                
                <!-- Coreference resolution graph -->
                <!-- Stanford coreference representation:
                     outer coreference container represents coreference annotation
                     for the document, inner coreference container represents one
                     referential chain each, i.e., a sequence of coreferent mentions 
                -->
                <!-- CoNLL representation follows CoNLL-2011/2012 specifications:
                     (elements of) coreference chains are marked by the same numerical ID. Here, this numerical ID corresponds to the position of the coreference chain.
                     We only use head information: start and end can be extrapolated from basic dependencies and by dropping them, and we avoid difficulties with overlapping spans as all spans are single words, then. This approach corresponds to the HTML visualization.
                -->
                <xsl:if test="exists(//coreference)">
                    <xsl:text>&#9;</xsl:text>
                    <xsl:variable name="sentenceId" select="./ancestor::sentence[1]/@id"/>
                    <xsl:variable name="coreference">
                        <xsl:for-each select="./ancestor::document/coreference/coreference/mention[sentence/text()=$sentenceId][head/text()=$token[1]/@id]">
                            <xsl:text>(</xsl:text>
                            <xsl:value-of select="count(./preceding::coreference)+1"/>
                            <xsl:text>) </xsl:text>
                        </xsl:for-each>
                    </xsl:variable>
                    <xsl:value-of select="normalize-space($coreference)"/>
                    <xsl:if test="normalize-space($coreference)=''">
                        <xsl:text>_</xsl:text>
                    </xsl:if>
                    
                    <xsl:text>&#9;</xsl:text>
                    <xsl:variable name="coreference-representative">
                        <xsl:for-each select="./ancestor::document/coreference/coreference/mention[@representative][sentence/text()=$sentenceId][head/text()=$token[1]/@id]">
                            <xsl:text>(gov </xsl:text>
                            <xsl:value-of select="count(../preceding::coreference)+1"/>
                            <xsl:text>) </xsl:text>
                        </xsl:for-each>
                    </xsl:variable>
                    <xsl:value-of select="normalize-space($coreference-representative)"/>
                    <xsl:if test="normalize-space($coreference-representative)=''">
                        <xsl:text>_</xsl:text>
                    </xsl:if>
                    
                </xsl:if>
                <xsl:text>&#10;</xsl:text>
            </xsl:for-each>
            
            <xsl:text>&#10;</xsl:text>
        
        </xsl:for-each>

    </xsl:template>    
</xsl:stylesheet>
