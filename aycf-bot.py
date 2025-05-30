from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.chrome.service import Service
from selenium.common.exceptions import TimeoutException, StaleElementReferenceException
from webdriver_manager.chrome import ChromeDriverManager
import time


def wait_for_visible_dropdown_with_items(driver, timeout=10):
    wait = WebDriverWait(driver, timeout)

    def condition(_):
        dropdowns = driver.find_elements(By.CSS_SELECTOR, 'ul[role="listbox"]')
        for dropdown in dropdowns:
            if dropdown.is_displayed():
                items = dropdown.find_elements(By.TAG_NAME, 'li')
                if items:
                    return dropdown
        return False

    return wait.until(condition)

def select_location_input(driver, wait, input_id_prefix: str, query: str, exact_text: str):
    print(f"▶️ Starting selection for '{exact_text}'")

    input_selector = f'input[id^="{input_id_prefix}"]'
    print(f"🔍 Waiting for input field: {input_selector}")
    input_field = wait.until(EC.element_to_be_clickable((By.CSS_SELECTOR, input_selector)))

    print("🧹 Clearing and typing query...")
    input_field.clear()
    input_field.send_keys(query)

    print("⏳ Searching for matching dropdown item...")
    try:
        dropdown_ul = wait_for_visible_dropdown_with_items(driver)
        options = dropdown_ul.find_elements(By.TAG_NAME, "li")
    except TimeoutException:
        raise Exception(f"❌ No dropdown with visible items appeared for: {exact_text}")

    for option in options:
        try:
            option_text = option.text.strip()
            print(f"   {option_text}")
            if exact_text.lower() in option_text.lower():
                print(f"👉 Match found: {option_text} — attempting click...")
                option.click()
                print(f"✅ Selected location: {option_text}")
                return
            else:
                print("     Not this option!")
        except StaleElementReferenceException:
            continue

    raise Exception(f"❌ Could not find dropdown option for: {exact_text}")

def select_calendar_date(driver, wait, date_str: str):
    print(f"🗓 Selecting date: {date_str}")
    xpath = f'//td[@title="{date_str}" and contains(@class, "cell") and not(contains(@class, "disabled"))]'
    try:
        date_cell = wait.until(EC.element_to_be_clickable((By.XPATH, xpath)))
        date_cell.click()
        print(f"✅ Clicked date {date_str}")
    except Exception as e:
        raise Exception(f"❌ Could not select date {date_str}: {e}")


def find_aycf_flights(email: str, password: str, origin_query: str, origin_full: str, dest_query: str, dest_full: str, date: str):
    print("[1] Launching browser...")
    driver = webdriver.Chrome(service=Service(ChromeDriverManager().install()))
    driver.maximize_window()

    print("[2] Opening Wizz homepage...")
    driver.get("https://multipass.wizzair.com")

    wait = WebDriverWait(driver, 20)

    print("[3] Clicking login button...")
    login_btn = wait.until(EC.element_to_be_clickable((By.CSS_SELECTOR, "button.CvoHeader-loginButton")))
    login_btn.click()

    print("[4] Filling in credentials...")
    wait.until(EC.visibility_of_element_located((By.NAME, "username"))).send_keys(email)
    driver.find_element(By.NAME, "password").send_keys(password)
    driver.find_element(By.ID, "kc-login").click()

    print("[5] Waiting for login to complete...")
    try:
        close_btn = wait.until(EC.element_to_be_clickable((By.CSS_SELECTOR, 'button[data-testid="cvo-close"]')))
        close_btn.click()
        print("    Modal closed successfully.")
        time.sleep(1)
    except:
        print("    No modal or already closed.")

    print("[6] Filling in origin and destination...")
    select_location_input(driver, wait, "autocomplete-origin", origin_query, origin_full)
    select_location_input(driver, wait, "autocomplete-destination", dest_query, dest_full)

    print("[7] Selecting date...")
    date_input = wait.until(EC.element_to_be_clickable((By.ID, "Departure-date")))
    date_input.click()
    select_calendar_date(driver, wait, date)

    search_button = wait.until(EC.element_to_be_clickable((By.CSS_SELECTOR, 'button.SearchCombo-submit')))
    driver.execute_script("arguments[0].scrollIntoView(true);", search_button)
    search_button.click()
    print("✅ Clicked Search button.")

    print("[9] Waiting for flight results to load...")
    time.sleep(8)

    print("[10] Scraping results...")
    flights = driver.find_elements(By.CLASS_NAME, "flight-select__flight")
    print(f"🛫 Found {len(flights)} flight(s):")
    for flight in flights:
        try:
            dep_time = flight.find_element(By.CLASS_NAME, "flight-select__departure-time").text
            arr_time = flight.find_element(By.CLASS_NAME, "flight-select__arrival-time").text
            price = flight.find_element(By.CLASS_NAME, "flight-select__price").text
            print(f"✈️  {dep_time} → {arr_time} | Price: {price}")
        except:
            print("⚠️  Skipped a flight card due to missing data.")
            continue

    print("[11] Done. Closing browser.")
    driver.quit()


if __name__ == "__main__":
    find_aycf_flights(
    email="hyaakov@aol.com",
    password="Yh160716",
    origin_query="rome", origin_full="Rome Fiumicino (FCO)",
    dest_query="tel", dest_full="Tel-Aviv (TLV)",
    date="2025-06-01"
)
  