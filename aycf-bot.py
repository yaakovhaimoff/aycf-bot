from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.chrome.service import Service
from selenium.common.exceptions import StaleElementReferenceException, TimeoutException
from webdriver_manager.chrome import ChromeDriverManager
import time


def launch_browser():
	print("[1] Launching browser...")
	driver = webdriver.Chrome(service=Service(ChromeDriverManager().install()))
	driver.maximize_window()
	return driver


def open_homepage(driver):
	print("[2] Opening Wizz homepage...")
	driver.get("https://multipass.wizzair.com")


def login(driver, wait, email, password):
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


def fill_route(driver, wait, origin_query, origin_full, dest_query, dest_full):
	print("[6] Filling in origin and destination...")
	select_location_input(driver, wait, "autocomplete-origin", origin_query, origin_full)
	select_location_input(driver, wait, "autocomplete-destination", dest_query, dest_full)


def select_calendar_date(driver, wait, date_str: str):
	print(f"🗓 Selecting date: {date_str}")
	xpath = f'//td[@title="{date_str}" and contains(@class, "cell") and not(contains(@class, "disabled"))]'
	try:
		date_cell = wait.until(EC.element_to_be_clickable((By.XPATH, xpath)))
		date_cell.click()
		print(f"✅ Clicked date {date_str}")
	except Exception as e:
		raise Exception(f"Could not select date {date_str}: {e}")


def select_date(driver, wait, date):
	print("[7] Selecting date...")
	date_input = wait.until(EC.element_to_be_clickable((By.ID, "Departure-date")))
	date_input.click()
	select_calendar_date(driver, wait, date)


def click_search(driver, wait):
	print("[8] Submitting search...")
	search_button = wait.until(EC.element_to_be_clickable((By.CSS_SELECTOR, 'button.SearchCombo-submit')))
	driver.execute_script("arguments[0].scrollIntoView(true);", search_button)
	search_button.click()
	print("✅ Clicked Search button.")


def scrape_results(driver):
	print("[9] Waiting for flight results to load...")
	time.sleep(8)
	print("[10] Scraping results...")

	try:
		no_results = driver.find_elements(By.CSS_SELECTOR, "article.AvailabilityPage-noResultMessage")
		if no_results:
			print("❌ No flights found for the selected date.")
			return

		flights = driver.find_elements(By.CLASS_NAME, "CvoCollapsibleDirectFlightRow-content")
		if not flights:
			print("⚠️ No flight rows found but no 'no results' message either.")
			return

		print(f"✅ Found {len(flights)} flight(s):")
		for flight in flights:
			try:
				dep_time = flight.find_element(By.CLASS_NAME, "CvoCollapsibleDirectFlightRow-departure").text
				arr_time = flight.find_element(By.CLASS_NAME, "CvoCollapsibleDirectFlightRow-arrival").text
				price = flight.find_element(By.CLASS_NAME, "CvoCollapsibleDirectFlightRow-price").text
				print(f"✈️  Flight: {dep_time} → {arr_time}, Price: {price}")
			except Exception as e:
				print(f"⚠️ Error parsing flight: {e}")
	except Exception as e:
		print(f"❌ Unexpected error while scraping results: {e}")


def finalize(driver):
	print("[11] Done. Closing browser.")
	driver.quit()


def find_aycf_flights(email, password, origin_query, origin_full, dest_query, dest_full, date):
	driver = launch_browser()
	wait = WebDriverWait(driver, 20)

	open_homepage(driver)
	login(driver, wait, email, password)
	fill_route(driver, wait, origin_query, origin_full, dest_query, dest_full)
	select_date(driver, wait, date)
	click_search(driver, wait)
	scrape_results(driver)
	finalize(driver)


if __name__ == "__main__":
	find_aycf_flights(
	email="email",
	password="password",
	origin_query="rome", origin_full="Rome Fiumicino (FCO)",
	dest_query="tel", dest_full="Tel-Aviv (TLV)",
	date="2025-06-01"
)
