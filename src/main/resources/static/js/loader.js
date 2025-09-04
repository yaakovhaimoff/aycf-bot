document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll("form").forEach(form => {
        const loader = form.querySelector(".loader");
        const submitBtn = form.querySelector("button[type='submit']");
        if (loader && submitBtn) {
            form.addEventListener("submit", function () {
                loader.style.display = "block";
                submitBtn.disabled = true;
                submitBtn.style.backgroundColor = "#cccccc";
                submitBtn.style.cursor = "not-allowed";
            });
        }
    });
});