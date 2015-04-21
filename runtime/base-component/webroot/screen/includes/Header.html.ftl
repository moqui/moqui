<!DOCTYPE HTML>
<html>
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="keywords" content="${html_keywords?if_exists}">
    <meta name="description" content="${html_description?if_exists}">
    <title>${html_title!(((ec.tenant.tenantName)!'Moqui') + " - " + (sri.screenUrlInfo.targetScreen.getDefaultMenuName())!"Page")}</title>
    <link rel="apple-touch-icon" href="/MoquiLogo100.png"/>
<#-- Style Sheets -->
<#list sri.getThemeValues("STRT_STYLESHEET") as styleSheetLocation>
    <link rel="stylesheet" href="${sri.buildUrl(styleSheetLocation).url}" type="text/css">
</#list>
<#list html_stylesheets?if_exists as styleSheetLocation>
    <link rel="stylesheet" href="${sri.buildUrl(styleSheetLocation).url}" type="text/css">
</#list>
<#-- JavaScript -->
<#list sri.getThemeValues("STRT_SCRIPT") as scriptLocation>
    <script language="javascript" src="${sri.buildUrl(scriptLocation).url}" type="text/javascript"></script>
</#list>
<#list html_scripts?if_exists as scriptLocation>
    <script language="javascript" src="${sri.buildUrl(scriptLocation).url}" type="text/javascript"></script>
</#list>
<#-- Icon -->
<#list sri.getThemeValues("STRT_SHORTCUT_ICON") as iconLocation>
    <link rel="shortcut icon" href="${sri.buildUrl(iconLocation).url}">
</#list>
</head>

<#assign bodyClassList = sri.getThemeValues("STRT_BODY_CLASS")>
<body class="${(ec.user.getPreference("OUTER_STYLE")!(bodyClassList?first))!"bg-light lter"}"><!-- try "bg-dark dk" or "bg-light lter" -->
