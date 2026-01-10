# Use an official OpenJDK base image
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy the JAR file (adjust target path if needed)
COPY target/flightFinder-*.jar app.jar

# Expose the port your app runs on (adjust if not 8080)
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
