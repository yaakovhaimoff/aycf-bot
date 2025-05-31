from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.chrome.service import Service
from selenium.common.exceptions import StaleElementReferenceException, TimeoutException
from webdriver_manager.chrome import ChromeDriverManager
import time
from dotenv import load_dotenv
import os


def launch_browser():
	print("[1] Launching browser...")
	driver = webdriver.Chrome(service=Service(ChromeDriverManager().install()))
	driver.maximize_window()
	return driver


def open_homepage(driver):
	print("[2] Opening Wizz homepage...")
	driver.get(os.getenv("MULTIPASS_WIZZAIR_URL"))


def login(driver, wait, email, password):
	open_homepage(driver)
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


def wait_for_visible_dropdown_with_items(driver, timeout=5):
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
			return False

		flights = driver.find_elements(By.CLASS_NAME, "CvoCollapsibleDirectFlightRow-content")
		if not flights:
			print("⚠️ No flight rows found but no 'no results' message either.")
			return False

		print(f"✅ Found {len(flights)} flight(s):")
		for flight in flights:
			try:
				dep_time = flight.find_element(By.CLASS_NAME, "CvoCollapsibleDirectFlightRow-departure").text
				arr_time = flight.find_element(By.CLASS_NAME, "CvoCollapsibleDirectFlightRow-arrival").text
				price = flight.find_element(By.CLASS_NAME, "CvoCollapsibleDirectFlightRow-price").text
				print(f"✈️  Flight: {dep_time} → {arr_time}, Price: {price}")
			except Exception as e:
				print(f"⚠️ Error parsing flight: {e}")
				return False
		return True
	except Exception as e:
		print(f"❌ Unexpected error while scraping results: {e}")
		return False


def check_flight_availabilty(driver, wait, origin_query, origin_full, dest_query, dest_full, date):
	fill_route(driver, wait, origin_query, origin_full, dest_query, dest_full)
	select_date(driver, wait, date)
	click_search(driver, wait)
	return scrape_results(driver)


def get_available_destinations(driver, wait, input_id_prefix, destination_id_prefix, origin_query, origin_full):
	open_homepage(driver)
	print(f"🌍 Getting destinations available from: {origin_full}")

	# Select origin airport
	select_location_input(driver, wait, input_id_prefix, origin_query, origin_full)

	# Click destination input to trigger dropdown
	dest_input_selector = f'input[id^="{destination_id_prefix}"]'
	dest_input = wait.until(EC.element_to_be_clickable((By.CSS_SELECTOR, dest_input_selector)))
	dest_input.click()

	# Wait for dropdown to appear and collect destination options
	try:
		dropdown_ul = wait_for_visible_dropdown_with_items(driver)
		options = dropdown_ul.find_elements(By.TAG_NAME, "li")
	except TimeoutException:
		raise Exception("❌ No destination dropdown options appeared.")

	destinations = []
	print("📋 Available destinations:")
	for option in options:
		try:
			text = option.text.strip()
			print(f" - {text}")
			prefix = text.split("(")[0].strip().split()[0].lower()  # e.g. "rome"
			full = text.strip()  # e.g. "Rome Fiumicino (FCO)"
			destinations.append( (prefix, full) )
		except StaleElementReferenceException:
			continue

	print(f"✅ Found {len(destinations)} destinations.")
	return destinations


def filter_connctions_that_are_not_in_destinations(destinations, connections):
	destinations_set = {prefix for prefix, _ in destinations}
	return [conn for conn in connections if conn[0] in destinations_set]

def print_possible_connections(connections):
	print("\nPossible connections:")
	for prefix, full in connections:
		print(f" - {prefix} ({full})")
	print(f"Total connections found: {len(connections)}\n")
 

def find_connections_flights(driver, wait, origin_query, origin_full, dest_query, dest_full, date):
	origin_to_connection_destinations = get_available_destinations(driver, wait, "autocomplete-origin", "autocomplete-destination", origin_query, origin_full)
	connection_to_destination_destinations = get_available_destinations(driver, wait, "autocomplete-destination", "autocomplete-origin", dest_query, dest_full)
	
	connection_to_destinations_flights = filter_connctions_that_are_not_in_destinations(connection_to_destination_destinations, origin_to_connection_destinations)
	print_possible_connections(connection_to_destinations_flights)
 
	origin_to_connection_flights = []
	for connection_prefix, connection_full in connection_to_destinations_flights:
		open_homepage(driver)
		if check_flight_availabilty(driver, wait, origin_query, origin_full, connection_prefix, connection_full, date):
			open_homepage(driver)
			if check_flight_availabilty(driver, wait, connection_prefix, connection_full, dest_query, dest_full, date):
				origin_to_connection_flights.append((connection_prefix, connection_full))
	print(f"\n✈️ Found {len(origin_to_connection_flights)} connection flights from {origin_full} to {dest_full} via other airports:")
	for flight in origin_to_connection_flights:
		print(f" - {flight}")


def finalize(driver):
	print("[11] Done. Closing browser.")
	driver.quit()


def find_aycf_flights(email, password, origin_query, origin_full, dest_query, dest_full, date):
	driver = launch_browser()
	wait = WebDriverWait(driver, 20)

	login(driver, wait, email, password)
	if not check_flight_availabilty(driver, wait, origin_query, origin_full, dest_query, dest_full, date):
		find_connections_flights(driver, wait, origin_query, origin_full, dest_query, dest_full, date)
	finalize(driver)


if __name__ == "__main__":
	load_dotenv()
	find_aycf_flights(
		email=os.getenv("EMAIL"),
		password=os.getenv("PASSWORD"),
		origin_query="rome", origin_full="Rome Fiumicino (FCO)",
		dest_query="tel", dest_full="Tel-Aviv (TLV)",
		date="2025-06-02"
	)
