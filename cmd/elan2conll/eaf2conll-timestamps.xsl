<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

    <!-- simplified ELAN extractor, works for all annotations grounded in time slot -->
    <xsl:output method="text"/>
    
    <xsl:template match="/">
        <!-- column labels -->
        <xsl:text># TIME</xsl:text>
        <xsl:for-each select="//TIER">
            <xsl:value-of select="concat('&#9;',@TIER_ID)"/>
        </xsl:for-each>
        <xsl:text>&#10;</xsl:text>
        
        <xsl:for-each select="//TIME_ORDER/TIME_SLOT">
            <xsl:variable name="id" select="@TIME_SLOT_ID"/>
            <xsl:value-of select="$id"/>
            <xsl:variable name="next" select="./following-sibling::TIME_SLOT[1]/@TIME_SLOT_ID"/>
            <xsl:for-each select="//TIER">
                <xsl:text>&#9;</xsl:text>
                    <xsl:for-each select="ANNOTATION/ALIGNABLE_ANNOTATION[@TIME_SLOT_REF2=$id][1]">
                        <xsl:text>(</xsl:text>
                        <xsl:value-of select="ANNOTATION_VALUE[1]/text()"/>
                    </xsl:for-each>
                    <xsl:text> * </xsl:text>
                    <xsl:for-each select="ANNOTATION/ALIGNABLE_ANNOTATION[@TIME_SLOT_REF2=$next][1]">
                        <xsl:text>)</xsl:text>
                    </xsl:for-each>
            </xsl:for-each>
            <xsl:text>&#10;</xsl:text>
        </xsl:for-each>
        
    </xsl:template>

</xsl:stylesheet>
