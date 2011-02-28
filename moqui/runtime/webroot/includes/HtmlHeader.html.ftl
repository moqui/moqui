<!DOCTYPE HTML>
<html>
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html">
    <meta name="keywords" content="${html_keywords?if_exists}">
    <meta name="description" content="${html_description?if_exists}">
    <title>${html_title!("Moqui - " + (sri.screenUrlInfo.targetScreen.getDefaultMenuName())!"Page")}</title>
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
