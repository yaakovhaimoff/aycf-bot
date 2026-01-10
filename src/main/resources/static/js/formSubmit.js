document.addEventListener('DOMContentLoaded', () => {
    // 🟠 Handle normal search
    const searchForm = document.getElementById('search-form');
    if (searchForm) {
        searchForm.addEventListener('submit', async (e) => {
            e.preventDefault();

            const body = {
                origin_query: document.getElementById('origin_query').value,
                origin_full: document.getElementById('origin_full').value,
                dest_query: document.getElementById('dest_query').value,
                dest_full: document.getElementById('dest_full').value,
                date: document.getElementById('datepicker').value
            };

            await sendPost('/search', body);
        });
    }

    // Handle next 3 days search
    const nextDaysForm = document.getElementById('next-days-form');
    if (nextDaysForm) {
        nextDaysForm.addEventListener('submit', async (e) => {
            e.preventDefault();

            const body = {
                origin_query: document.getElementById('origin_query_next').value,
                origin_full: document.getElementById('origin_full_next').value,
                dest_query: document.getElementById('dest_query_next').value,
                dest_full: document.getElementById('dest_full_next').value
            };

            await sendPost('/search-next-days', body);
        });
    }

    // 🔵 Handle search connections
    const connectionsForm = document.getElementById('connections-form');
    if (connectionsForm) {
        connectionsForm.addEventListener('submit', async (e) => {
            e.preventDefault();

            const body = {
                origin_query: document.querySelector('[name="origin_query"]').value,
                origin_full: document.querySelector('[name="origin_full"]').value,
                dest_query: document.querySelector('[name="dest_query"]').value,
                dest_full: document.querySelector('[name="dest_full"]').value,
                date: document.querySelector('[name="date"]').value
            };

            await sendPost('/search-connections', body);
        });
    }
});

// 📦 Helper function to send JSON POST
async function sendPost(endpoint, data) {
    try {
        const response = await fetch(endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });

        if (response.redirected) {
            window.location.href = response.url;
        } else {
            const html = await response.text();
            document.body.innerHTML = html;
        }
    } catch (err) {
        console.error(`Request to ${endpoint} failed:`, err);
    }
}
