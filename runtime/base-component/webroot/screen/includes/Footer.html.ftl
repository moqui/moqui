${sri.getAfterScreenWriterText()}

<#-- Footer JavaScript -->
<#list footer_scripts?if_exists as scriptLocation>
<script language="javascript" src="${sri.buildUrl(scriptLocation).url}" type="text/javascript"></script>
</#list>
<script>
${sri.getScriptWriterText()}
$(window).unload(function(){}); // Does nothing but break the bfcache
</script>
</body>
</html>
