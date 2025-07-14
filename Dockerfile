FROM --platform=linux/amd64 python:3.10-slim

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y \
    firefox-esr \
    wget curl unzip gnupg ca-certificates \
    libgtk-3-0 libdbus-glib-1-2 libasound2 libx11-xcb1 libnss3 \
    libxss1 libxrandr2 libatk-bridge2.0-0 libatk1.0-0 libpangocairo-1.0-0 \
    libxcomposite1 libxcursor1 libxdamage1 libxi6 libxtst6 libgbm1 \
    && rm -rf /var/lib/apt/lists/*

# Install Geckodriver
RUN GECKODRIVER_VERSION=$(curl -s https://api.github.com/repos/mozilla/geckodriver/releases/latest | grep '"tag_name"' | sed -E 's/.*"v([^"]+)".*/\1/') && \
    wget "https://github.com/mozilla/geckodriver/releases/download/v${GECKODRIVER_VERSION}/geckodriver-v${GECKODRIVER_VERSION}-linux64.tar.gz" && \
    tar -xzf geckodriver-v${GECKODRIVER_VERSION}-linux64.tar.gz && \
    mv geckodriver /usr/local/bin/ && \
    rm geckodriver-v${GECKODRIVER_VERSION}-linux64.tar.gz

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 5000

CMD ["flask", "run", "--host=0.0.0.0"]