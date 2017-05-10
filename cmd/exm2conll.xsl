<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

    <!-- Exmaralda to CoNLL(esque) format, 
        developed for T-CODEX (http://www.laudatio-repository.org/repository/corpus/B4%3AT-Codex) 
    Christian Chiarcos, chiarcos@informatik.uni-frankfurt.de, 2017-05-10 
    -->
    
    <xsl:output method="text" indent="no"/>
    
    <xsl:template match="/">
        <!-- metadata -->
        <xsl:text>#</xsl:text>
        <xsl:for-each select="//ud-information">
            <xsl:value-of select="@attribute-name"/>
            <xsl:text>:</xsl:text>
            <xsl:value-of select="normalize-space(.//text())"/>
            <xsl:if test="count(./following-sibling::ud-information[1])=1">
                <xsl:text>, </xsl:text>
            </xsl:if>
        </xsl:for-each>
        <xsl:text>&#10;&#10;</xsl:text>
        
        <!-- header -->
        <xsl:text># ID</xsl:text>
        <xsl:for-each select="//tier">
            <xsl:text>&#9;</xsl:text>
            <xsl:value-of select="@category"/>
        </xsl:for-each>
        <xsl:text>&#10;</xsl:text>
        
        <!-- body -->
        <xsl:for-each select="//common-timeline/tli">
            <xsl:variable name="tli" select="@id"/>
            <xsl:variable name="last-tli" select="./preceding-sibling::tli[1]/@id"/>
            <xsl:variable name="next-tli" select="./following-sibling::tli[1]/@id"/>
            <xsl:variable name="pre-tlis">
                <xsl:text> </xsl:text>
                <xsl:for-each select="./preceding-sibling::tli">
                    <xsl:value-of select="@id"/>
                    <xsl:text> </xsl:text>
                </xsl:for-each>
            </xsl:variable>
            <xsl:variable name="post-tlis">
                <xsl:text> </xsl:text>
                <xsl:for-each select="./following-sibling::tli">
                    <xsl:value-of select="@id"/>
                    <xsl:text> </xsl:text>
                </xsl:for-each>
            </xsl:variable>
            <xsl:if test="count(//tier//event[@start=$tli or @end=$next-tli]//text()[normalize-space(.)!=''][1])&gt;0">         <!-- skip empty elements -->
                <xsl:value-of select="$tli"/>
                <xsl:for-each select="//tier">
                    <!-- we use a reduced (IO)BE(S) schema for span annotations, 
                         but only if the annotation does not contain spaces 
                         (these are metadata in the T-CODEX corpus, not proper annotations) -->
                    <xsl:text>&#9;</xsl:text>
                    <xsl:choose>
                        <xsl:when test="count(.//event[@end=$next-tli][1])&gt;0 or count(.//event[@start=$tli][1])&gt;0">
                            <xsl:for-each select=".//event[@end=$next-tli and @start!=$tli][1]">
                                <xsl:variable name="value" select="normalize-space(text())"/>
                                <xsl:if test="not(contains($value,' ')) and $value!=''">
                                    <xsl:text>E-</xsl:text>
                                    <xsl:value-of select="$value"/>
                                </xsl:if>
                            </xsl:for-each>
                            <xsl:for-each select=".//event[@start=$tli and @end=$next-tli][1]">
                                <xsl:variable name="value" select="normalize-space(text())"/>
                                <xsl:value-of select="$value"/>
                            </xsl:for-each>
                            <xsl:for-each select=".//event[@start=$tli and @end!=$next-tli][1]">
                                <xsl:variable name="value" select="normalize-space(text())"/>
                                <xsl:if test="not(contains($value,' ')) and $value!=''">
                                    <xsl:text>B-</xsl:text>
                                </xsl:if>
                                <xsl:value-of select="$value"/>
                            </xsl:for-each>
                        </xsl:when>
                        <xsl:when test="count(.//event[contains($pre-tlis,concat(' ',@start,' ')) and contains($post-tlis,concat(' ',@end,' '))][1])&gt;0">
                            <xsl:for-each select=".//event[contains($pre-tlis,concat(' ',@start,' ')) and contains($post-tlis,concat(' ',@end,' '))][1]">
                                <xsl:variable name="value" select="normalize-space(text())"/>
                                <xsl:if test="not(contains($value,' ')) and $value!=''">
                                    <xsl:text>I-</xsl:text>
                                    <xsl:value-of select="$value"/>
                                </xsl:if>
                            </xsl:for-each>
                            <xsl:text></xsl:text>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:text>_</xsl:text>
                        </xsl:otherwise>
                    </xsl:choose>                
                </xsl:for-each>
                <xsl:text>&#10;</xsl:text>
            </xsl:if>
        </xsl:for-each>
   </xsl:template>
</xsl:stylesheet>
