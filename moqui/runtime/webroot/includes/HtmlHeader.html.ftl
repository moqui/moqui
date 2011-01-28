<!DOCTYPE HTML>
<html>
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html">
<#t/><#if html_keywords?has_content><meta name="keywords" content="${html_keywords}"></#if>
<#t/><#if html_description?has_content><meta name="description" content="${html_description}"></#if>
<#t/><#if html_title?has_content><title>${html_title}</title></#if>
<#list sri.getThemeValues("STRT_SCRIPT") as scriptLocation>
    <script language="javascript" src="${sri.buildUrl(scriptLocation).url}" type="text/javascript"></script>
</#list>
<#assign styleSheetLocationList = sri.getThemeValues("STRT_STYLESHEET")/>
<#list styleSheetLocationList as styleSheetLocation>
    <link rel="stylesheet" href="${sri.buildUrl(styleSheetLocation).url}" type="text/css">
</#list>
<#if !styleSheetLocationList?has_content>
    <link rel="stylesheet" href="${sri.buildUrl('/theme/default.css').url}" type="text/css">
</#if>
</head>

<body>
