<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

    <!-- simplified ELAN extractor, assume that first TIER defines tokens -->
    <xsl:output method="text"/>
    
    <xsl:template match="/">
        <!-- column labels -->
		<xsl:text># </xsl:text>
        <xsl:for-each select="//TIER">
            <xsl:value-of select="@TIER_ID"/>
					<xsl:if test="count(./following-sibling::TIER[1])=1">
					                <xsl:text>&#9;</xsl:text>
					</xsl:if>
        </xsl:for-each>
        <xsl:text>&#10;</xsl:text>

		<!-- retrieve all annotations in the order of their time slot anchoring, then aggregate in postprocessing -->
        <xsl:for-each select="//TIME_SLOT">
            <xsl:variable name="start" select="@TIME_SLOT_REF1"/>
            <xsl:variable name="end" select="@TIME_SLOT_REF2"/>
            <xsl:variable name="id" select="@TIME_SLOT_ID"/>
            <xsl:for-each select="//TIER">
                    <xsl:for-each select="ANNOTATION/ALIGNABLE_ANNOTATION[@TIME_SLOT_REF2=$id][1]">
                        <xsl:value-of select="ANNOTATION_VALUE[1]/text()"/>
                    </xsl:for-each>
					<xsl:if test="count(./following-sibling::TIER[1])=1">
					                <xsl:text>&#9;</xsl:text>
					</xsl:if>

            </xsl:for-each>
            <xsl:text>&#10;</xsl:text>
        </xsl:for-each>
        
    </xsl:template>

</xsl:stylesheet>
