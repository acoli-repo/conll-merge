<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0"  xmlns:tei="http://www.tei-c.org/ns/1.0">

    <!-- PROIEL to CoNLL conversion -->
    <!-- developed for Old Norse (Cod. Regius, ed. 2017-06-07) and XSD scripts for PROIEL 1.0, 2.0, 2.01 
         data export is exhaustive, but we do not export the TEI header (which really shouldn't be stored any way other than in XML ;)
    -->
    <xsl:output method="text" indent="no"/>
    
    <!-- annotations for token and token/slash -->
    <!-- provide as parameter, otherwise, this is determined by *exhaustive* search and *SLOW*;
        if provided as parameter, tokenFeats must be tokenized first, hence use for-each select="$splitTokenFeats", instead
    -->
    <xsl:param name="tokenFeats" select="//token/@*[count(.|key('tokenFeatKeys',name())[1])=1]/name()"/>
    <xsl:key name="tokenFeatKeys" match="//token/@*" use="name()"/>
    <xsl:variable name="splitTokenFeats" select="tokenize(normalize-space(string-join($tokenFeats,' ')),' ')"/>

    <!-- slash annotations are aggregated per attribute, and separated by whitespace -->
    <xsl:param name="slashFeats" select="//token/slash/@*[count(.|key('slashFeatKeys',name())[1])=1]/name()"/>
    <xsl:key name="slashFeatKeys" match="//token/slash/@*" use="name()"/>
    <xsl:variable name="splitSlashFeats" select="tokenize(normalize-space(string-join($slashFeats,' ')),' ')"/>
    
    <xsl:template match="/">
        <!-- metadata -->
        <xsl:text># PROIEL export&#10;</xsl:text>
        <xsl:for-each select="/proiel/@*">
            <xsl:text># </xsl:text>
            <xsl:value-of select="name()"/>
            <xsl:text> </xsl:text>
            <xsl:value-of select="."/>
            <xsl:text>&#10;</xsl:text>
        </xsl:for-each>
        <xsl:text>&#10;</xsl:text>
        
        <!-- annotation info disabled -->
        <xsl:for-each select="/proiel/annotation/*">
            <xsl:text># </xsl:text>
            <xsl:value-of select="name()"/>
            <xsl:text>&#10;</xsl:text>
            <xsl:for-each select="*/value|value">
                <xsl:text># </xsl:text>
                <xsl:if test="../@tag!=''">
                    <xsl:value-of select="../@tag"/>
                    <xsl:text>=</xsl:text>
                </xsl:if>
                <xsl:value-of select="@tag"/>
                <xsl:text> "</xsl:text>
                <xsl:value-of select="@summary"/>
                <xsl:text>"&#10;</xsl:text>
            </xsl:for-each>
            <xsl:text>&#10;</xsl:text>
        </xsl:for-each>

        <!-- header -->
        <xsl:text># </xsl:text>
        <xsl:for-each select="$splitTokenFeats">
            <xsl:value-of select="."/>
            <xsl:text>&#9;</xsl:text>
        </xsl:for-each>
        <xsl:for-each select="$splitSlashFeats">
            <xsl:text>slash/</xsl:text>
            <xsl:value-of select="."/>
            <xsl:text>&#9;</xsl:text>
        </xsl:for-each>
        <xsl:text>&#10;&#10;</xsl:text>

        <!-- data -->
        <xsl:apply-templates select="/proiel/source"/>
    </xsl:template>
    
    <xsl:template match="source">
        <xsl:text># </xsl:text>
        <xsl:for-each select="@*">
            <xsl:value-of select="name()"/>
            <xsl:text>: </xsl:text>
            <xsl:value-of select="."/>
            <xsl:text> </xsl:text>
        </xsl:for-each>
        <xsl:text>&#10;</xsl:text>
        
        <xsl:apply-templates/>
        
        <xsl:text>&#10;</xsl:text>
    </xsl:template>

    <xsl:template match="div">
        <xsl:text>&#10;</xsl:text>
        <xsl:apply-templates/>
        <xsl:text>&#10;</xsl:text>
    </xsl:template>
    
    <xsl:template match="sentence">
        <xsl:text># </xsl:text>
        <xsl:for-each select="@*">
            <xsl:value-of select="name()"/>
            <xsl:text>: </xsl:text>
            <xsl:value-of select="."/>
            <xsl:text> </xsl:text>
        </xsl:for-each>
        <xsl:text>&#10;</xsl:text>
        <xsl:apply-templates/>
        <xsl:text>&#10;</xsl:text>
    </xsl:template>
    
    <xsl:template match="token">
        <xsl:variable name="token" select="." as="node()"/>
        <xsl:for-each select="$splitTokenFeats">
            <!-- we need to make sure we check all annotations in a constant order, including omitted ones -->
            <xsl:variable name="feat" select="."/>
            <xsl:value-of select="$token[1]/@*[name()=$feat]"/>
            <xsl:if test="normalize-space($token[1]/@*[name()=$feat])=''">
                <xsl:text>_</xsl:text>
            </xsl:if>
            <xsl:text>&#9;</xsl:text>
        </xsl:for-each>
        <xsl:for-each select="$splitSlashFeats">
            <xsl:variable name="feat" select="."/>
            <xsl:for-each select="$token/slash">
                <xsl:value-of select="@*[name()=$feat]"/>
                <xsl:if test="normalize-space(@*[name()=$feat])=''">
                    <xsl:text>_</xsl:text>
                </xsl:if>
                <xsl:text> </xsl:text>
            </xsl:for-each>
            <xsl:if test="not(exists($token/slash))">
                <xsl:text>_</xsl:text>
            </xsl:if>
            <xsl:text>&#9;</xsl:text>
        </xsl:for-each>
        <xsl:text>&#10;</xsl:text>
    </xsl:template>

    <!-- as used in PROIEL 1.0, not exported to CoNLL -->
    <xsl:template match="tei:teiHeader"/>
    
    <xsl:template match="*">
        <xsl:text># </xsl:text>
        <xsl:value-of select="name()"/>
        <xsl:text>: </xsl:text>
        <xsl:value-of select="."/>
        <xsl:text>&#10;</xsl:text>
        <xsl:apply-templates/>
    </xsl:template>
    
    <xsl:template match="text()"/>
    
</xsl:stylesheet>
