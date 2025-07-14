from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.common.exceptions import StaleElementReferenceException, TimeoutException
from selenium.webdriver.firefox.options import Options as FirefoxOptions
from selenium.webdriver.firefox.service import Service as FirefoxService
import os
import time
from datetime import datetime, timedelta
from logger import logger


MINIMUM_LAYOVER_MINUTES = 60
MAXIMUM_LAYOVER_MINUTES = 60 * 8


def launch_browser():
	logger.info("Launching Firefox browser...")
	options = FirefoxOptions()
	options.add_argument("--headless")
	options.add_argument("--no-sandbox")
	options.add_argument("--disable-dev-shm-usage")
	driver = webdriver.Firefox(service=FirefoxService(), options=options)
	logger.info("Browser launched successfully.")
	return driver


def open_homepage(driver):
	logger.info("Opening Wizz homepage...")
	driver.get(os.getenv("MULTIPASS_WIZZAIR_URL"))


def login(driver, wait, email, password):
	logger.info(f"Got email: {email}, password: {password}")
	open_homepage(driver)
	logger.info("Clicking login button...")
	login_btn = wait.until(EC.element_to_be_clickable((By.CSS_SELECTOR, "button.CvoHeader-loginButton")))
	login_btn.click()

	logger.info("Filling in credentials...")
	wait.until(EC.visibility_of_element_located((By.NAME, "username"))).send_keys(email)
	driver.find_element(By.NAME, "password").send_keys(password)
	driver.find_element(By.ID, "kc-login").click()

	logger.info("Waiting for login result...")
	time.sleep(2)

	try:
		error_element = driver.find_element(By.ID, "input-error")
		if "invalid email address or password" in error_element.text.strip().lower():
			logger.warning("Login failed: Invalid credentials detected.")
			return False
	except:
		logger.info("No error message found. Proceeding...")

	try:
		close_btn = wait.until(EC.element_to_be_clickable((By.CSS_SELECTOR, 'button[data-testid="cvo-close"]')))
		close_btn.click()
		logger.info("Login succeeded — modal closed.")
	except:
		logger.info("Login succeeded — no modal.")

	return True


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
	logger.info(f"Starting selection for '{exact_text}'")

	input_selector = f'input[id^="{input_id_prefix}"]'
	logger.info(f"Waiting for input field: {input_selector}")
	input_field = wait.until(EC.element_to_be_clickable((By.CSS_SELECTOR, input_selector)))

	logger.info("Clearing and typing query...")
	input_field.clear()
	input_field.send_keys(query)

	logger.info("Searching for matching dropdown item...")
	try:
		dropdown_ul = wait_for_visible_dropdown_with_items(driver)
		options = dropdown_ul.find_elements(By.TAG_NAME, "li")
	except TimeoutException:
		logger.error(f"No dropdown with visible items appeared for: {exact_text}")
		return

	for option in options:
		try:
			option_text = option.text.strip()
			logger.info(f"   {option_text}")
			if exact_text.lower() in option_text.lower():
				logger.info(f"Match found: {option_text} — attempting click...")
				option.click()
				logger.info(f"Selected location: {option_text}")
				return
			else:
				logger.info("Not this option!")
		except StaleElementReferenceException:
			continue

	logger.error(f"Could not find dropdown option for: {exact_text}")


def fill_route(driver, wait, origin_query, origin_full, dest_query, dest_full):
	logger.info("Filling in origin and destination...")
	select_location_input(driver, wait, "autocomplete-origin", origin_query, origin_full)
	select_location_input(driver, wait, "autocomplete-destination", dest_query, dest_full)


def select_calendar_date(wait, date_str: str):
	logger.info(f"Selecting date: {date_str}")
	xpath = f'//td[@title="{date_str}" and contains(@class, "cell") and not(contains(@class, "disabled"))]'
	try:
		date_cell = wait.until(EC.element_to_be_clickable((By.XPATH, xpath)))
		date_cell.click()
		logger.info(f"Clicked date {date_str}")
	except Exception as e:
		logger.error(f"Could not select date {date_str}: {e}")


def select_date(wait, date):
	logger.info("Selecting date...")
	date_input = wait.until(EC.element_to_be_clickable((By.ID, "Departure-date")))
	date_input.click()
	select_calendar_date(wait, date)


def click_search(driver, wait):
	logger.info("Submitting search...")
	search_button = wait.until(EC.element_to_be_clickable((By.CSS_SELECTOR, 'button.SearchCombo-submit')))
	driver.execute_script("arguments[0].scrollIntoView(true);", search_button)
	search_button.click()
	logger.info("Clicked Search button.")


def scrape_results(driver):
	logger.info("Waiting for flight results to load...")
	time.sleep(8)
	logger.info("Scraping results...")

	try:
		no_results = driver.find_elements(By.CSS_SELECTOR, "article.AvailabilityPage-noResultMessage")
		if no_results:
			logger.warning("No flights found for the selected date.")
			return None

		flights = driver.find_elements(By.CLASS_NAME, "CvoCollapsibleDirectFlightRow-content")
		if not flights:
			logger.info("No flight rows found but no 'no results' message either.")
			return None

		logger.info(f"Found {len(flights)} flight(s):")
		flights_list = []
		for flight in flights:
			try:
				dep_time = flight.find_element(By.CLASS_NAME, "CvoCollapsibleDirectFlightRow-departure").text
				arr_time = flight.find_element(By.CLASS_NAME, "CvoCollapsibleDirectFlightRow-arrival").text
				price = flight.find_element(By.CLASS_NAME, "CvoCollapsibleDirectFlightRow-price").text
				logger.info(f"✈️  Flight: {dep_time} → {arr_time}, Price: {price}")
				flights_list.append({
					"departure": dep_time,
					"arrival": arr_time,
					"price": price
				})
			except Exception as e:
				logger.error(f" Error parsing flight: {e}")
				return None
		return flights_list
	except Exception as e:
		logger.error(f"Unexpected error while scraping results: {e}")
		return None


def check_flight_availabilty(driver, wait, origin_query, origin_full, dest_query, dest_full, date):
	fill_route(driver, wait, origin_query, origin_full, dest_query, dest_full)
	select_date(wait, date)
	click_search(driver, wait)
	return scrape_results(driver)


def get_available_destinations(driver, wait, input_id_prefix, destination_id_prefix, origin_query, origin_full):
	open_homepage(driver)
	logger.info(f"Getting destinations available from: {origin_full}")

	select_location_input(driver, wait, input_id_prefix, origin_query, origin_full)

	dest_input_selector = f'input[id^="{destination_id_prefix}"]'
	dest_input = wait.until(EC.element_to_be_clickable((By.CSS_SELECTOR, dest_input_selector)))
	dest_input.click()

	try:
		dropdown_ul = wait_for_visible_dropdown_with_items(driver)
		options = dropdown_ul.find_elements(By.TAG_NAME, "li")
	except TimeoutException:
		logger.error("No destination dropdown options appeared.")

	destinations = []
	logger.info("Available destinations:")
	for option in options:
		try:
			text = option.text.strip()
			logger.info(f" - {text}")
			prefix = text.split("(")[0].strip().split()[0].lower()
			full = text.strip()
			destinations.append( (prefix, full) )
		except StaleElementReferenceException:
			continue

	logger.info(f"Found {len(destinations)} destinations.")
	return destinations


def filter_connctions_that_are_not_in_destinations(destinations, connections):
	destinations_set = {prefix for prefix, _ in destinations}
	return [conn for conn in connections if conn[0] in destinations_set]


def print_possible_connections(connections):
	logger.info("\nPossible connections:")
	for prefix, full in connections:
		logger.info(f" - {prefix} ({full})")
	logger.info(f"Total connections found: {len(connections)}\n")
 

def parse_time(time_str, base_date=None):
	"""Parses 'HH:MM' format with optional base date."""
	base_date = base_date or datetime.today().date()
	time_obj = datetime.strptime(time_str.strip(), "%H:%M").time()
	return datetime.combine(base_date, time_obj)


def is_valid_connection(arrival_time_str, departure_time_str, arrival_date, departure_date,
						min_layover_minutes=MINIMUM_LAYOVER_MINUTES,
						max_layover_minutes=MAXIMUM_LAYOVER_MINUTES):
	try:
		arrival = parse_time(arrival_time_str, arrival_date)
		departure = parse_time(departure_time_str, departure_date)
		layover = departure - arrival
		return timedelta(minutes=min_layover_minutes) <= layover <= timedelta(minutes=max_layover_minutes)
	except Exception as e:
		logger.warning(f"Layover calculation failed: {e}")
		return False


def get_valid_connection_dates(base_date: datetime.date):
	today = datetime.today().date()
	max_date = today + timedelta(days=3)

	first_leg_dates = []
	second_leg_dates = []

	before_date = base_date - timedelta(days=1)
	next_date = base_date + timedelta(days=1)

	if today <= before_date <= max_date:
		first_leg_dates.append(before_date)
	if today <= base_date <= max_date:
		first_leg_dates.append(base_date)

	if today <= base_date <= max_date:
		second_leg_dates.append(base_date)
	if today <= next_date <= max_date:
		second_leg_dates.append(next_date)

	return first_leg_dates, second_leg_dates


def find_connections_flights(driver, wait, origin_query, origin_full, dest_query, dest_full, date_str):
	base_date = datetime.strptime(date_str, "%Y-%m-%d").date()
	first_leg_dates, second_leg_dates = get_valid_connection_dates(base_date)

	flight_cache = {}

	def get_cached_flights(origin_query, origin_full, destination_query, destination_full, date):
		key = (origin_full, destination_full, date)
		if key not in flight_cache:
			open_homepage(driver)
			flight_cache[key] = check_flight_availabilty(driver, wait, origin_query, origin_full, destination_query, destination_full, date.isoformat()) or []
		return flight_cache[key]

	origin_to_connection_destinations = get_available_destinations(driver, wait, "autocomplete-origin", "autocomplete-destination", origin_query, origin_full)
	connection_to_destination_destinations = get_available_destinations(driver, wait, "autocomplete-destination", "autocomplete-origin", dest_query, dest_full)

	connection_to_destinations_flights = filter_connctions_that_are_not_in_destinations(
		connection_to_destination_destinations, origin_to_connection_destinations)
	logger.info(f"Found {len(connection_to_destinations_flights)} possible connections from {origin_full} to {dest_full}.")
	for prefix, full in connection_to_destinations_flights:
		logger.info(f" - {prefix} ({full})")
	
	valid_connections = []

	for connection_prefix, connection_full in connection_to_destinations_flights:
		for first_leg_date in first_leg_dates:
			first_leg_flights = get_cached_flights(origin_query, origin_full, connection_prefix, connection_full, first_leg_date)
			if not first_leg_flights:
				continue

			for second_leg_date in second_leg_dates:
				layover = second_leg_date - first_leg_date
				if layover > timedelta(days=1):
					logger.info(f"Skipping connection {connection_full} due to too long layover: {layover}")
					continue

				second_leg_flights = get_cached_flights(connection_prefix, connection_full, dest_query, dest_full, second_leg_date)
				if not second_leg_flights:
					continue

				for first in first_leg_flights:
					for second in second_leg_flights:
						if is_valid_connection(first["arrival"], second["departure"], arrival_date=first_leg_date, departure_date=second_leg_date):
							logger.info("----------------------------------------")
							logger.info(f"Valid connection found: {connection_full} on {first_leg_date} → {second_leg_date}")
							logger.info(f"  First leg: {first['departure']} → {first['arrival']}, Price: {first['price']}")
							logger.info(f"  Second leg: {second['departure']} → {second['arrival']}, Price: {second['price']}")
							logger.info(f"  Layover: {second['departure']} - {first['arrival']} = {second['departure']} - {first['arrival']} = {second['departure'] - first['arrival']}")
							logger.info("  ----------------------------------------")
							valid_connections.append({
								"via": connection_full,
								"first_leg": first,
								"second_leg": second
							})

	return valid_connections


def find_aycf_flights(email, password, origin_query, origin_full, dest_query, dest_full, date):
	driver = launch_browser()
	wait = WebDriverWait(driver, 20)

	login(driver, wait, email, password)
	if not check_flight_availabilty(driver, wait, origin_query, origin_full, dest_query, dest_full, date):
		find_connections_flights(driver, wait, origin_query, origin_full, dest_query, dest_full, date)
	finalize(driver)


def finalize(driver):
	logger.info("Done. Closing browser.")
	driver.quit()