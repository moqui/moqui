    </div>
${sri.getAfterScreenWriterText()}


<script>
    <#-- no longer needed for Metis buttons
    function activateAllButtons() {
        $("input[type=submit], input[type=reset], a.button, button").each(function() {
            $(this).button({icons: {primary: $(this).attr("iconcls")}});
        })
    }
    activateAllButtons();
    -->
    ${sri.getScriptWriterText()}
</script>
</body>
</html>
