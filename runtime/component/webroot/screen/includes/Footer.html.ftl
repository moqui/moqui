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
    $(".chzn-select").chosen({ search_contains:true, disable_search_threshold:10 });
    ${sri.getScriptWriterText()}
</script>
</body>
</html>
