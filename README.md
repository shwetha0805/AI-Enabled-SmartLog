# AI enabled SmartLog

AI enabled SmartLog is a software-based security log monitoring and analysis system designed to collect, process, analyze, and visualize Windows security events.

The system integrates Windows event collection, centralized log processing, machine-learning-based anomaly detection, risk classification, database storage, and a web-based monitoring dashboard.

## 1. Features

- Windows event collection using Winlogbeat.
- Centralized log processing using Logstash.
- Elasticsearch-based event indexing and storage.
- Event-level anomaly detection using Isolation Forest.
- Minute-window feature aggregation and anomaly analysis.
- Rule-based security risk classification.
- Flask-based AI service.
- Spring Boot REST backend.
- MySQL persistence for processed logs and window predictions.
- Security monitoring dashboard.
- High- and Critical-risk alert monitoring.
- System health monitoring.
- AI failure fallback and retry handling.
- Analytics, recent events, and severity filtering.

## 2. System Architecture

### 2.1 Log collection and processing

Windows Event Logs are collected by Winlogbeat and forwarded to Logstash. Logstash processes the events and forwards them to Elasticsearch.

### 2.2 Event-level analysis

The Spring Boot Elasticsearch processor retrieves indexed events and sends them to the Flask AI service for individual event analysis. The AI service uses the production Isolation Forest model and security rules to determine anomaly status and risk information.

### 2.3 Minute-window analysis

A separate scheduled processing path retrieves events from completed time windows, aggregates event information into numerical features, and sends the resulting feature vectors to the candidate Isolation Forest model through the Flask `/analyze-window` endpoint.

Window predictions are stored through the Spring Boot application in MySQL.

### 2.4 Visualization

The Spring Boot backend exposes REST APIs used by the frontend dashboard to display logs, alerts, analytics, predictions, and system health.

```text
                 WINDOWS EVENT LOGS
                          |
                       Winlogbeat
                          |
                       Logstash
                          |
                    Elasticsearch
                     /          \
                    /            \
          Event-level path     Minute-window path
                  |                  |
          Spring Boot             Spring Boot
          Event Processor         Window Processor
                  |                  |
                  v                  v
             Flask /analyze    Feature Aggregation
                  |                  |
          Production Model           |
          Isolation Forest           v
                  |           Flask /analyze-window
                  |                  |
                  |           Candidate Model
                  |           Isolation Forest
                  |                  |
                  v                  v
               MySQL <---------- MySQL
                  |
             Spring Boot APIs
                  |
           SmartLog Dashboard
```

## 3. Technology Stack

| Component | Technologies |
|---|---|
| Backend | Java 21, Spring Boot 4.1.0, Spring Data JPA |
| Database | MySQL 8 |
| AI service | Python 3.11, Flask |
| Machine learning | Scikit-learn, Isolation Forest, NumPy, Pandas |
| Log collection | Winlogbeat |
| Log processing | Logstash 8.19 |
| Log storage and search | Elasticsearch 8.19 |
| Frontend | HTML, CSS, JavaScript |
| Build tool | Maven Wrapper |
| Container platform | Docker |

## 4. Machine Learning and Analysis

### 4.1 Event-level anomaly detection

The production AI model uses Isolation Forest to identify unusual individual security events.

The event-level model and its corresponding vectorizer are stored in:

```text
ai-service/
    model/
        isolation_forest.joblib
        vectorizer.joblib
```

The AI service also applies security rules to support risk classification.

### 4.2 Minute-window anomaly detection

The minute-window processing component aggregates Windows events into numerical features, including event counts, event-code distributions, process-related information, and event ratios.

The candidate model and metadata are stored in:

```text
ai-service/
    candidate_model/
        isolation_forest_candidate.joblib
        metadata.json
```

Count-based features are transformed using logarithmic scaling before candidate-model inference.

The minute-window model is a separate analysis path from the production event-level model.

### 4.3 Risk classification

SmartLog supports four risk categories:

- LOW – Events classified as low risk.
- MEDIUM – Events requiring additional attention.
- HIGH – Events that may indicate suspicious activity.
- CRITICAL – Events requiring urgent investigation.

Anomaly status and risk classification are related but distinct outputs. A risk category should not automatically be interpreted as proof of a confirmed security incident.

## 5. Project Structure

```text
AI enabled SmartLog/
|
|-- ai-service/
|   |-- model/
|   |-- candidate_model/
|   |-- app.py
|   |-- requirements.txt
|   |-- train_model.py
|   |-- train_candidate_model.py
|   |-- build_window_dataset.py
|   |-- prepare_training_data.py
|   |-- prepare_training_data_extended.py
|   |-- evaluate.py
|   |-- evaluate_model.py
|   |-- benchmark_api.py
|   |-- benchmark_inference.py
|   |-- analyze_feature_relationships.py
|   |-- analyze_time_windows.py
|
|-- benchmark/
|   |-- SmartLogIntegrationBenchmark.java
|
|-- frontend/
|   |-- index.html
|   |-- script.js
|   |-- style.css
|
|-- log-pipeline/
|   |-- elasticsearch.yml.txt
|   |-- logstash.yml.txt
|   |-- smartlog.conf
|   |-- winlogbeat.yml.txt
|
|-- src/
|   |-- main/
|   |   |-- java/com/smartlog/smartlog/
|   |   |   |-- controller/
|   |   |   |-- exception/
|   |   |   |-- model/
|   |   |   |-- repository/
|   |   |   |-- service/
|   |   |
|   |   |-- resources/
|   |       |-- application.properties
|   |
|   |-- test/
|
|-- .env.example
|-- .gitignore
|-- mvnw
|-- mvnw.cmd
|-- pom.xml
|-- test.http
|-- README.md
```

## 6. Prerequisites

Install and configure the following software:

- Java Development Kit (JDK) 21.
- Python 3.11.
- MySQL 8.
- Docker Desktop.
- Elasticsearch 8.19.
- Logstash 8.19.
- Winlogbeat compatible with the configured Beats input.
- A modern web browser.

The project includes Maven Wrapper scripts, so a separate global Maven installation is not required.

## 7. Configuration

### 7.1 Database

Create the MySQL database:

```sql
CREATE DATABASE smartlog;
```

Configure the database credentials using the environment variable expected by the Spring Boot application:

```powershell
$env:DB_PASSWORD = "your_mysql_password"
```

The application connects to:

```text
jdbc:mysql://localhost:3306/smartlog
```

### 7.2 Environment variables

The repository includes `.env.example` as a configuration reference.

| Variable | Purpose |
|---|---|
| `DB_PASSWORD` | MySQL password |
| `SMARTLOG_ES_PASSWORD` | Elasticsearch password for authenticated model-training connections |
| `SMARTLOG_AI_URL` | Flask event-analysis endpoint |

Example:

```powershell
$env:DB_PASSWORD = "your_mysql_password"
$env:SMARTLOG_AI_URL = "http://127.0.0.1:5000/analyze"
```

Set environment variables in the shell or development environment that launches each service. A `.env` file is not automatically loaded by every application.

Never commit real passwords, API keys, tokens, or other credentials.

## 8. Running the Project

The services can be started independently. Ensure that their dependencies are running before starting the corresponding application.

### 8.1 Start infrastructure

Start and configure MySQL, Elasticsearch, and Logstash.

Configure Elasticsearch using the supplied configuration reference and make sure the configured Elasticsearch endpoint is reachable.

Configure Logstash using:

```text
log-pipeline/smartlog.conf
```

The supplied pipeline accepts Beats events on port `5044` and writes processed events to the `smartlog-windows` Elasticsearch index.

Configure Winlogbeat on the Windows host to collect the required Windows event channels and forward events to:

```text
127.0.0.1:5044
```

The configuration files in `log-pipeline/` are provided as project configuration references. Configure the corresponding services using their supported installation or container procedures.

### 8.2 Start the AI service

Open PowerShell in the project directory:

```powershell
cd ai-service

py -3.11 -m venv .venv

.\.venv\Scripts\Activate.ps1

python -m pip install --upgrade pip

pip install -r requirements.txt

python app.py
```

The AI service is expected to run at:

```text
http://127.0.0.1:5000
```

Available endpoints:

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/analyze` | Analyze an individual log event |
| POST | `/analyze-window` | Analyze aggregated minute-window features |
| GET | `/health` | Check AI service health |

The trained model files must be present in their respective model directories before starting the AI service.

### 8.3 Start the Spring Boot backend

Open a separate PowerShell window in the project root:

```powershell
$env:DB_PASSWORD = "your_mysql_password"

$env:SMARTLOG_AI_URL = "http://127.0.0.1:5000/analyze"

.\mvnw.cmd spring-boot:run
```

The backend is configured to use:

```text
http://localhost:8080
```

To build the application:

```powershell
.\mvnw.cmd clean package
```

### 8.4 Start the frontend dashboard

Open the `frontend` directory in Visual Studio Code and launch `index.html` using the Live Server extension or another local static HTTP server.

The frontend communicates with the Spring Boot backend through its configured API base URL.

```text
http://localhost:8080/api
```

### 8.5 Verify service health

The Spring Boot health endpoint is:

```text
GET http://localhost:8080/api/health
```

It reports the availability of the monitored infrastructure and application services.

## 9. REST API Overview

The Spring Boot application exposes the following principal endpoints:

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/health` | System health status |
| GET | `/api/logs` | Retrieve security logs |
| POST | `/api/logs` | Submit a log event |
| GET | `/api/dashboard/stats` | Dashboard statistics |
| GET | `/api/dashboard/recent` | Recent security events |
| GET | `/api/dashboard/alerts` | Security alerts |
| GET | `/api/window-predictions` | Retrieve minute-window predictions |

Refer to the application controllers and `test.http` for additional API details.

## 10. Dashboard

The SmartLog dashboard communicates with the Spring Boot backend to present:

- Total processed logs.
- Anomaly statistics.
- High-risk event information.
- Security alerts.
- Recent security activity.
- Analytics and event filtering.
- Service health information.

The dashboard provides a centralized interface for monitoring and investigating security events.

## 11. Example Security Event Flow

A Windows failed-authentication event, such as Event ID 4625, can be collected from the Windows Security event channel.

The event is forwarded through Winlogbeat and Logstash to Elasticsearch. The Spring Boot processor retrieves the indexed event and forwards it for AI analysis and risk classification.

The resulting information is stored in MySQL and made available to the dashboard.

The final anomaly status and risk classification depend on the configured processing and analysis logic.

## 12. Benchmarking and Evaluation

The repository includes scripts and source code for evaluating different parts of the application:

- `benchmark_api.py` – Measures Flask API response time and throughput.
- `benchmark_inference.py` – Measures local machine-learning inference performance.
- `SmartLogIntegrationBenchmark.java` – Measures Java-to-Flask integration performance.
- `evaluate.py` – Evaluates event-level predictions against heuristic reference labels.
- `evaluate_model.py` – Generates model prediction outputs.
- `analyze_feature_relationships.py` – Examines relationships between selected event features.
- `analyze_time_windows.py` – Examines event distributions across minute-based windows.

Evaluation results must be interpreted according to the experiment's dataset, reference-label methodology, and measurement scope.

Heuristic reference labels are not equivalent to independently verified security ground truth. Anomaly detection metrics should not be presented as proof of real-world attack detection accuracy.

Training and evaluation datasets are not included in the repository. Supply the required dataset paths when running the corresponding scripts.

## 13. Project Team

| Member | Responsibility |
|---|---|
| Member 1 | Java, Spring Boot, MySQL, backend integration |
| Member 2 | Python, machine learning, AI service |
| Member 3 | Winlogbeat, Logstash, Elasticsearch, log processing |
| Member 4 | Dashboard, frontend, integration and testing |

## 14. Future Scope

Potential improvements include:

- Integration with additional log sources.
- Expanded security event analysis.
- Improved model evaluation using independently labelled datasets.
- Advanced threat correlation and detection.
- Cloud deployment and scalable processing.
- Enhanced alert investigation and reporting.

## 15. Disclaimer

AI enabled SmartLog is an academic software project developed for security log monitoring, anomaly detection, and visualization research.

Its anomaly and risk classifications are intended to assist security monitoring and investigation. They should not be treated as definitive proof of malicious activity.
