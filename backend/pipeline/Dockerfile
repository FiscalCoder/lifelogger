FROM python:3.11-slim

# ffmpeg for AAC→WAV conversion
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Real-time worker replaces cron — processes uploads as they arrive
CMD ["python", "-u", "worker.py"]
