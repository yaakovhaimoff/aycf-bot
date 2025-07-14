$(function () {
  function setupAutocomplete(visibleId, queryField, fullField, type) {
  $("#" + visibleId).autocomplete({
    source: function (request, response) {
    $.getJSON(`/autocomplete/${type}`, { q: request.term }, function (data) {
      response(data);
    });
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

  setupAutocomplete("origin", "origin_query", "origin_full", "origin");
  setupAutocomplete("destination", "dest_query", "dest_full", "destination");
});