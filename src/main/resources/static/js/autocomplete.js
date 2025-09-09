function setupAutocomplete(visibleId, queryField, fullField) {
    $("#" + visibleId).autocomplete({
        source: function (request, response) {
            const results = airports.filter(item =>
                item.toLowerCase().includes(request.term.toLowerCase())
            );
            response(results);
        },
        minLength: 1,
        select: function (event, ui) {
            const full = ui.item.value;
            const match = full.match(/\(([^)]+)\)/);
            const code = match ? match[1].toLowerCase() : full.toLowerCase();

            $("#" + visibleId).val(full);
            $("#" + queryField).val(code);
            $("#" + fullField).val(full);
            return false;
        }
    });
}

setupAutocomplete("origin", "origin_query", "origin_full");
setupAutocomplete("destination", "dest_query", "dest_full");

setupAutocomplete("originNext", "origin_query_next", "origin_full_next");
setupAutocomplete("destinationNext", "dest_query_next", "dest_full_next");
