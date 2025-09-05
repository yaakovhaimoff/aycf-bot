function toggleDropdown() {
    const menu = document.getElementById("dropdown-menu");
    menu.style.display = menu.style.display === "block" ? "none" : "block";
}

document.addEventListener("click", function(event) {
    const dropdown = document.querySelector(".user-dropdown");
    if (!dropdown.contains(event.target)) {
        document.getElementById("dropdown-menu").style.display = "none";
    }
});
