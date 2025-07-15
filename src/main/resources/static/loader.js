document.addEventListener("DOMContentLoaded", function () {
    const form = document.querySelector("form");
    const loader = document.getElementById("loader");
    const submitBtn = form?.querySelector("button[type='submit']");

    if (form && loader && submitBtn) {
        form.addEventListener("submit", function () {
            loader.style.display = "block";
            submitBtn.disabled = true;
            submitBtn.style.backgroundColor = "#cccccc"; // grey color
            submitBtn.style.cursor = "not-allowed";
        });
    }
});