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
driver = carmelit.launch_browser()
wait = WebDriverWait(driver, 20)

@app.route("/login", methods=["GET", "POST"])
def login_route():
	logger.info("Login route accessed")

	if request.method == "POST":
		email = request.form["email"]
		password = request.form["password"]
		logger.info("Attempting login with provided credentials")

		success = carmelit.login(driver, wait, email, password)
		if success:
			session["email"] = email
			session["password"] = password
			logger.info("Login successful, redirecting to /search")
			return redirect("/search")
		else:
			logger.warning("Login failed: Invalid email or password")
			return render_template("login.html", error="Invalid email address or password.")

	logger.info("Rendering login page")
	return render_template("login.html")

@app.route("/search", methods=["GET", "POST"])
def search():
	if "email" not in session:
		return redirect("/login")

	if request.method == "POST":
		origin_query = request.form.get("origin_query", "").strip()
		origin_full = request.form.get("origin_full", "").strip()
		dest_query = request.form.get("dest_query", "").strip()
		dest_full = request.form.get("dest_full", "").strip()
		date = request.form.get("date", "").strip()
		logger.info(f"Search initiated with origin: {origin_full}, destination: {dest_full}, date: {date}")

		flights = carmelit.check_flight_availabilty(driver, wait, origin_query, origin_full, dest_query, dest_full, date)
		if not flights:
			logger.info("No direct flights found, checking for connections flights")
			flights = carmelit.find_connections_flights(driver, wait, origin_query, origin_full, dest_query, dest_full, date)
		if flights:
			logger.info(f"Flights found: {flights}")
			return render_template("search.html", flights=flights, origin_full=origin_full, dest_full=dest_full, date=date, show_results=True)
		else:
			logger.info("No flights found, returning empty results")
			return render_template("search.html", flights=[], origin_full=origin_full, dest_full=dest_full, date=date, show_results=True)

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