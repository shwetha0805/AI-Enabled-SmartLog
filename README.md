# AI-Enabled-SmartLog

AI-Enabled SmartLog is a software-based security log monitoring and management system designed to collect, analyze, classify, and visualize security events from Windows systems.

The system combines real-time log collection, centralized log processing, machine-learning-based anomaly detection, risk classification, and a monitoring dashboard.

## Features

- Real-time Windows security event collection
- Winlogbeat-based log shipping
- Logstash-based log processing
- Elasticsearch-based centralized log storage
- Machine learning anomaly detection using Isolation Forest
- Rule-based security risk classification
- Spring Boot REST backend
- MySQL storage for processed security events
- Real-time security dashboard
- Threat detection and investigation
- High and Critical risk alerts
- System health monitoring
- AI service failure fallback
- Automatic AI retry and recovery
- Search and severity filtering
- Analytics and recent-event monitoring

## System Architecture

```text
Windows Event Logs
        ↓
    Winlogbeat
        ↓
     Logstash
        ↓
 Elasticsearch
        ↓
 AI Anomaly Detection
   (Isolation Forest)
        ↓
 Risk Classification
        ↓
 Spring Boot Backend
        ↓
      MySQL
        ↓
 SmartLog Dashboard
 ```

## Technology Stack

- Backend:

```
- Java
- Spring Boot
- Spring Data JPA
- REST APIs
- MySQL
```

- AI / Machine Learning:

```
- Python
- Flask
- Scikit-learn
- Isolation Forest
```
Log Processing:
```
- Winlogbeat
- Logstash
- Elasticsearch
```
Frontend:
```
HTML
CSS
JavaScript
```
Development:
```
- IntelliJ IDEA
- Git
- GitHub
- Tailscale for development-time connectivity

```
## AI Analysis

SmartLog uses a machine-learning model based on Isolation Forest to identify potentially unusual events. The AI service also applies security rules to help classify events according to their risk levels.

The AI service also applies security rules to classify events into risk levels such as:

```
- LOW - Events classified as low risk.
- MEDIUM - Events requiring additional attention.
- HIGH - Events that may indicate suspicious activity.
- CRITICAL - Events requiring urgent investigation.
```

The final classification depends on the model output and the security rules implemented in the application. These classifications are intended to support investigation and should not be treated as proof of a security incident.
## Failure Handling

SmartLog uses configuration values to connect its backend, AI service, and supporting infrastructure.

Example environment-variable names:

- DB_PASSWORD=your_mysql_password
- SMARTLOG_ES_PASSWORD=your_elasticsearch_password
- SMARTLOG_AI_URL=http://127.0.0.1:5000/analyze

These values are illustrative placeholders. Set the actual values in your local environment according to the application's configuration.

Refer to .env.example for the variables documented by the project. Environment variables must be configured in the process or development environment that launches the relevant service; creating a .env file alone does not guarantee that every service will load it.

Never commit real passwords, API keys, tokens, or other credentials to the repository.
## Project Structure

```
AI-Enabled-SmartLog/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/smartlog/smartlog/
│   │   │       ├── controller/
│   │   │       ├── exception/
│   │   │       ├── model/
│   │   │       ├── repository/
│   │   │       └── service/
│   │   └── resources/
│   │       └── application.properties
│   └── test/
├── ai-service/
├── frontend/
├── log-pipeline/
├── .env.example
├── .gitignore
├── pom.xml
└── README.md
```

The Java application contains the backend APIs and database integration. The other directories contain the AI service, dashboard files, and log-pipeline configuration files.

## Configuration

Sensitive configuration values are supplied through environment variables.

Example:

DB_PASSWORD=your_mysql_password
SMARTLOG_ES_PASSWORD=your_elasticsearch_password
SMARTLOG_AI_URL=http://127.0.0.1:5000/analyze

Refer to .env.example for the required configuration variables.

Do not commit real passwords, API keys, or other credentials to the repository.

## Running the Project


### Spring Boot Backend

1. Configure the MySQL connection and required environment variables.
2. Ensure that the MySQL server is running and the `smartlog` database is available.
3. Start the Spring Boot application using the project's configured development environment.

The backend is configured to use:

`http://localhost:8080`

### AI Service

1. Install the Python dependencies required by the AI service.
2. Configure the required environment variables.
3. Start the Python service using its implemented startup procedure.

The AI service is expected to use:

`http://127.0.0.1:5000`

Available endpoints documented by the project:

* `POST /analyze` — Analyze a log event.
* `GET /health` — Check AI service health.

### Log Pipeline

Configure Elasticsearch, Logstash, and Winlogbeat using the files and settings provided in `log-pipeline/`. Verify that each service is running and that the configured addresses and ports are reachable.

### Frontend Dashboard

Open the frontend using the appropriate local development method and verify that its configured API URL points to the running Spring Boot backend.

### Integration Check

After starting the required services, submit a test event and verify the complete flow: ingestion, processing, AI analysis or fallback classification, database storage, and dashboard display.


## Dashboard

The SmartLog dashboard communicates with the Spring Boot REST API to display security events, analytics, alerts, and system status.

## Security Event Example

A failed Windows authentication event such as Windows Event ID 4625 can travel through the complete pipeline:

```
Windows Event 4625
      ↓
Winlogbeat
      ↓
Logstash
      ↓
Elasticsearch
      ↓
SmartLog AI
      ↓
Spring Boot
      ↓
MySQL
      ↓
Dashboard
```

The event can then appear as a HIGH-risk anomaly in the SmartLog application.

## Project Team
- Member :	Responsibility
- Member 1 : 	Java, Spring Boot, MySQL, Backend Integration
- Member 2 :	Python, Machine Learning, AI Service
- Member 3 :	Winlogbeat, Logstash, Elasticsearch, Log Processing
- Member 4 :	Dashboard, Frontend, Integration and Testing

## Project Goal

The goal of SmartLog is to reduce the difficulty of manually monitoring large volumes of security logs by combining automated log processing, machine-learning-based anomaly detection, risk classification, and centralized visualization.

## Future Scope
Testing and Evaluation

SmartLog can be evaluated using the following criteria:

- Log ingestion: Whether supported events reach the processing pipeline.
- API correctness: Whether backend endpoints return expected responses.
- Classification performance: Precision, recall, and F1-score, where labelled test data is available.
- Processing latency: Time taken to process an event through the relevant components.
- Dashboard correctness: Whether displayed logs, counts, and alerts match the backend data.
- Failure recovery: Whether fallback processing and retry behaviour work as intended.

Report numerical performance results only after conducting the corresponding experiments.
## Disclaimer

SmartLog is an academic project developed for security log monitoring, anomaly detection, and visualization research.