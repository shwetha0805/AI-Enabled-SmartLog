# AI-Enabled SmartLog

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

**System Architecture**

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
 
**Technology Stack**

**Backend**
Java
Spring Boot
Spring Data JPA
REST APIs
MySQL

**AI / Machine Learning**
Python
Flask
Scikit-learn
Isolation Forest

**Log Processing**
Winlogbeat
Logstash
Elasticsearch

**Frontend**
HTML
CSS
JavaScript

**Development**
IntelliJ IDEA
Git
GitHub
Tailscale for development-time connectivity

**AI Analysis**

SmartLog uses an Isolation Forest model to identify unusual event behavior.

The AI service also applies security rules to classify events into risk levels such as:

LOW
MEDIUM
HIGH
CRITICAL

For example, failed authentication events can be classified as high-risk events, while unauthorized activity can be classified as critical.

**Failure Handling**

SmartLog is designed to avoid losing events when the AI service becomes unavailable.

If the AI service is unavailable:

The event is retained by the backend.
Fallback analysis is performed.
The event is stored with a fallback status.
The system periodically retries AI processing.
Once the AI service becomes available, pending events are reprocessed.

The application also monitors the availability of backend and infrastructure services.

**Project Structure**
SmartLog/
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
├── .env.example
├── .gitignore
├── pom.xml
└── README.md

**Configuration**

Sensitive configuration values are supplied through environment variables.

Example:

DB_PASSWORD=your_mysql_password
SMARTLOG_ES_PASSWORD=your_elasticsearch_password
SMARTLOG_AI_URL=http://127.0.0.1:5000/analyze

Refer to .env.example for the required configuration variables.

Do not commit real passwords, API keys, or other credentials to the repository.

**Running the Project**

Spring Boot Backend

Configure the required environment variables and run the Spring Boot application.

The backend runs on:

http://localhost:8080

**AI Service**

The Python AI service runs on:

http://127.0.0.1:5000

The analysis endpoint is:

POST /analyze

The health endpoint is:

GET /health

**Dashboard** 
The SmartLog dashboard communicates with the Spring Boot REST API to display security events, analytics, alerts, and system status.

**Security Event Example**

A failed Windows authentication event such as Windows Event ID 4625 can travel through the complete pipeline:

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

The event can then appear as a HIGH-risk anomaly in the SmartLog application.

**Project Team**
Member	Responsibility
Member 1	Java, Spring Boot, MySQL, Backend Integration
Member 2	Python, Machine Learning, AI Service
Member 3	Winlogbeat, Logstash, Elasticsearch, Log Processing
Member 4	Dashboard, Frontend, Integration and Testing

**Project Goal**

The goal of SmartLog is to reduce the difficulty of manually monitoring large volumes of security logs by combining automated log processing, machine-learning-based anomaly detection, risk classification, and centralized visualization.

**Future Scope**
Cloud deployment
Advanced sequence-based anomaly detection
Improved event correlation
Role-based access control
Audit logging
Advanced incident detection
Larger-scale performance evaluation
Integration with additional log sources

**Disclaimer**

SmartLog is an academic project developed for security log monitoring, anomaly detection, and visualization research.
