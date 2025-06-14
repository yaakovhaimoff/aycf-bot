import logging
from flask import Flask, render_template, request, redirect, session, jsonify
from selenium.webdriver.support.ui import WebDriverWait
from dotenv import load_dotenv
import carmelit
from airports import airports
from logger import logger

app = Flask(__name__)
app.secret_key = "something-secret"
load_dotenv()
driver = None
wait = None

@app.route("/login", methods=["GET", "POST"])
def login_route():
	global driver, wait
	logger.info("Login route accessed")

	if request.method == "POST":
		data = request.get_json() if request.is_json else request.form
		email = data.get("email")
		password = data.get("password")

		if not email or not password:
			logger.warning("Login attempt with empty email or password")
			error_msg = "Email and password cannot be empty."
			if request.is_json:
				return jsonify({ "success": False, "error": error_msg }), 400
			return render_template("login.html", error=error_msg)

		logger.info("Attempting login with provided credentials")

		if driver is None:
			driver = carmelit.launch_browser()
			wait = WebDriverWait(driver, 20)

		success = carmelit.login(driver, wait, email, password)

		if success:
			session["email"] = email
			session["password"] = password
			logger.info("Login successful, redirecting to /search")
			if request.is_json:
				return jsonify({ "success": True })
			return redirect("/search")
		else:
			logger.warning("Login failed: Invalid email or password")
			error_msg = "Invalid email address or password."
			if request.is_json:
				return jsonify({ "success": False, "error": error_msg }), 401
			return render_template("login.html", error=error_msg)

	logger.info("Rendering login page")
	return render_template("login.html")

@app.route("/search", methods=["GET", "POST"])
def search():
	global driver, wait

	if "email" not in session:
		if request.is_json:
			return jsonify({ "error": "Unauthorized" }), 401
		return redirect("/login")

	if driver is None or wait is None:
		logger.warning("Search attempted before browser initialization.")
		if request.is_json:
			return jsonify({ "error": "Browser not initialized. Please log in first." }), 400
		return redirect("/login")

	if request.method == "POST":
		data = request.get_json() if request.is_json else request.form

		origin_query = data.get("origin_query", "").strip()
		origin_full = data.get("origin_full", "").strip()
		dest_query = data.get("dest_query", "").strip()
		dest_full = data.get("dest_full", "").strip()
		date = data.get("date", "").strip()

		logger.info(f"Search initiated with origin: {origin_full}, destination: {dest_full}, date: {date}")

		flights = carmelit.check_flight_availabilty(driver, wait, origin_query, origin_full, dest_query, dest_full, date)
		if not flights:
			logger.info("No direct flights found, checking for connection flights...")
			flights = carmelit.find_connections_flights(driver, wait, origin_query, origin_full, dest_query, dest_full, date)
			if not flights:
				logger.info("No connection flights found either.")

		if request.is_json:
			return jsonify({
				"origin": origin_full,
				"destination": dest_full,
				"date": date,
				"flights": flights or []
			})

		return render_template("search.html",
			flights=flights or [],
			origin_full=origin_full,
			dest_full=dest_full,
			date=date,
			show_results=True
		)

	return render_template("search.html", show_results=False)

@app.route("/autocomplete/<input_type>")
def autocomplete(input_type):
	query = request.args.get("q", "").lower()
	if not query:
		return jsonify([])

	suggestions = [airport for airport in airports if query in airport.lower()]
	return jsonify(suggestions)

if __name__ == '__main__':
	app.run(debug=True)