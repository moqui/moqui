(function($) { $.widget("ui.combobox", {
    _create: function() {
        var self = this, select = this.element.hide(), selected = select.children(":selected"),
            value = selected.val() ? selected.text() : "";
        var input = this.input = $("<input>")
            .insertAfter(select).val(value)
            .autocomplete({
                delay: 0, minLength: 0,
                source: function(request, response) {
                    var matcher = new RegExp($.ui.autocomplete.escapeRegex(request.term), "i");
                    response(select.children("option").map(function() {
                        var text = $(this).text();
                        if (this.value && (!request.term || matcher.test(text)))
                            return {
                                label: text.replace(
                                    new RegExp("(?![^&;]+;)(?!<[^<>]*)(" + $.ui.autocomplete.escapeRegex(request.term) +
                                        ")(?![^<>]*>)(?![^&;]+;)", "gi"), "<strong>$1</strong>"),
                                value: text,
                                option: this
                            };
                    }));
                },
                select: function(event, ui) { ui.item.option.selected = true; self._trigger("selected", event, { item: ui.item.option }); },
                change: function(event, ui) {
                    if (!ui.item) {
                        var matcher = new RegExp("^" + $.ui.autocomplete.escapeRegex($(this).val()) + "$", "i");
                        var valid = false;
                        select.children("option").each(function() {
                            if ($(this).text().match(matcher)) { this.selected = valid = true; return false; }
                        });
                        // old code: if (!valid) { $(this).val(""); select.val(""); input.data("autocomplete").term = ""; return false; }
                        // customization from the original script on jquery.com: allow text not originally in the select
                        if (!valid) {
                            var text = $(this).val();
                            select.append("<option value=\"" + text  + "\">" + text + "</option>");
                            select.children("option").each(function() {
                                if ($(this).text().match(matcher)) { this.selected = valid = true; return false; }
                            });
                        }
                    }
                }
            }).addClass("ui-widget ui-widget-content ui-corner-left");
        input.data("autocomplete")._renderItem = function(ul, item) {
            return $("<li></li>").data("item.autocomplete", item).append("<a>" + item.label + "</a>").appendTo(ul);
        };
        this.button = $("<button type='button'>&nbsp;</button>").attr("tabIndex", -1).attr("title", "Show All")
            .insertAfter(input).button({ icons: { primary: "ui-icon-triangle-1-s" }, text: false })
            .removeClass("ui-corner-all").addClass("ui-corner-right ui-button-icon")
            .click(function() {
                if (input.autocomplete("widget").is(":visible")) { input.autocomplete("close"); return; }
                input.autocomplete("search", ""); input.focus();
            });
    },
    destroy: function() { this.input.remove(); this.button.remove(); this.element.show(); $.Widget.prototype.destroy.call(this); }
}); })(jQuery);
