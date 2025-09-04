$(function () {
    const today = new Date();
    const maxDate = new Date();
    maxDate.setDate(today.getDate() + 3);

    $("#datepicker").datepicker({
        minDate: today,
        maxDate: maxDate,
        dateFormat: "yy-mm-dd"
    });
});