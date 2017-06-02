<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">

    <!-- convert FrameNet SRL annotations as produced by Semafor (http://www.cs.cmu.edu/~ark/SEMAFOR/README) to PropBank-like CoNLL format -->
    <xsl:output method="text"/>
    
    <xsl:template match="/">
            <xsl:text>WORD&#9;FRAME&#9;FRAME-ARGs&#10;&#10;</xsl:text>
        <xsl:for-each select="//sentence">
            <xsl:variable name="sentence" select="." as="node()*"/>
            
            <!-- we assume the text is whitespace-tokenized 
                 and that all labels operate with respect to 
                 these offsets -->
            <xsl:variable name="text" select="text/text()"/>
            <xsl:for-each select="tokenize($text,' ')">
                <xsl:variable name="id" select="position()"/>
                <xsl:variable name="start">
                    <xsl:variable name="pre">
                        <xsl:for-each select="tokenize($text,' ')[position()&lt;$id]">
                            <xsl:value-of select="."/>
                            <xsl:text> </xsl:text>
                        </xsl:for-each>
                    </xsl:variable>
                    <xsl:value-of select="string-length($pre)"/>
                </xsl:variable>
                <xsl:variable name="end" select="$start+string-length(.)-1"/>
                
                <!-- target ("predicate") column:
                     For all annosets (in the order of their first introduction: 
                     identify a token that serves as their anchor (cf. PropBank predicates).
                     Here, this is last token of the first target (FE), for nominals,
                     this is likely the syntactic head.
                     Unlike PropBank, we do not rule out permit multiple targets in the same cell, separated by whitespace.
                     In practice, this does, however, not occur.
                -->
                <xsl:variable name="targets">
                    <xsl:variable name="tmp">
                        <xsl:for-each select="$sentence//annotationSet"> 
                            <xsl:sort select="min(.//layer[@name='Target']//@end)"/>
                            <xsl:variable name="targetOffset" select="min(.//layer[@name='Target']//@end)"/>
                                                
                            <xsl:if test="$targetOffset=$end">
                                <xsl:value-of select="@frameName"/>
                                <xsl:text> </xsl:text>
                            </xsl:if>
                            <xsl:if test="$targetOffset=$end and exists(.//layer[@name='Target'][@end=$targetOffset])">
                                <xsl:value-of select="@frameName"/>
                                <xsl:text> </xsl:text>
                            </xsl:if>
                        </xsl:for-each>
                    </xsl:variable>
                    <xsl:choose>
                        <xsl:when test="normalize-space($tmp)=''">
                            <xsl:text>_</xsl:text>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="normalize-space($tmp)"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:variable>

                <!-- fills argument columns for every annoSet in the current sentence 
                     note that this comes with a trailing tabulator
                -->
                <xsl:variable name="args">
                    <xsl:for-each select="$sentence//annotationSet"> 
                        <xsl:sort select="min(.//layer[@name='FE']//@end)"/>
                        <xsl:text>&#9;</xsl:text>
                        <xsl:variable name="tmp">
                            <xsl:for-each select=".//layer//label">
                                <xsl:variable name="annotation" 
                                    select="concat(../../@name,'_',@name)"/>
                                <xsl:choose>
                                    <xsl:when test="@start=$start and @end=$end">
                                        <xsl:value-of select="concat('S-',$annotation)"/>
                                    </xsl:when>
                                    <xsl:when test="@start=$start">
                                        <xsl:value-of select="concat('B-',$annotation)"/>
                                    </xsl:when>
                                    <xsl:when test="@end=$end">
                                        <xsl:value-of select="concat('E-',$annotation)"/>
                                    </xsl:when>
                                    <xsl:when test="number(@start)&lt;number($start) and number(@end)&gt;number($end)">
                                        <xsl:value-of select="concat('I-',$annotation)"/>
                                    </xsl:when>
                                </xsl:choose>
                                <xsl:text> </xsl:text>
                            </xsl:for-each>
                        </xsl:variable>
                        <xsl:choose>
                            <xsl:when test="normalize-space($tmp)=''">
                                <xsl:text>_</xsl:text>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of select="normalize-space($tmp)"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:for-each>
                </xsl:variable>
                
                <xsl:value-of select="."/>
                <xsl:text>&#9;</xsl:text>
                <xsl:value-of select="$targets"/>
                <xsl:value-of select="$args"/>
                <xsl:text>&#10;</xsl:text>
            </xsl:for-each>
            
            <xsl:text>&#10;</xsl:text>
        </xsl:for-each>
        
    </xsl:template>

</xsl:stylesheet>
