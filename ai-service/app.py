from flask import Flask, request

import joblib


app = Flask(__name__)


MODEL_PATH = "model/isolation_forest.joblib"
VECTORIZER_PATH = "model/vectorizer.joblib"


model = joblib.load(MODEL_PATH)
vectorizer = joblib.load(VECTORIZER_PATH)


def rule_based_risk(severity, event_type, message):

    severity = severity.upper()
    event_type = event_type.upper()
    message = message.lower()

    if severity == "CRITICAL":
        return "CRITICAL", "Critical security event detected"

    if "UNAUTHORIZED" in event_type:
        return "CRITICAL", "Unauthorized activity detected"

    if severity == "HIGH":
        return "HIGH", "High-risk security event detected"

    if "FAILED" in event_type:
        return "HIGH", "Failed authentication activity detected"

    if "suspicious" in message:
        return "MEDIUM", "Suspicious activity detected"

    return "LOW", "Normal security activity"


@app.route("/analyze", methods=["POST"])
def analyze_log():

    data = request.get_json() or {}

    severity = str(
        data.get("severity", "LOW")
    )

    event_type = str(
        data.get("eventType", "UNKNOWN")
    )

    event_code = str(
        data.get("eventCode", "UNKNOWN")
    )

    message = str(
        data.get("message", "")
    )

    outcome = str(
        data.get("outcome", "UNKNOWN")
    )

    channel = str(
        data.get("channel", "Security")
    )

    logon_type = str(
        data.get("logonType", "UNKNOWN")
    )


    features = [{
        "event_code": event_code,
        "event_type": event_type,
        "outcome": outcome,
        "channel": channel,
        "severity": severity,
        "logon_type": logon_type
    }]


    X = vectorizer.transform(features)

    prediction = model.predict(X)[0]

    anomaly_score = model.decision_function(X)[0]


    if prediction == -1:
        anomaly = True
    else:
        anomaly = False


    risk, reason = rule_based_risk(
        severity,
        event_type,
        message
    )


    if anomaly and risk == "LOW":
        risk = "MEDIUM"
        reason = "ML model detected unusual event behavior"


    return {
        "anomaly": bool(anomaly),
        "risk": risk,
        "eventCode": event_code,
        "eventType": event_type,
        "reason": reason,
        "anomalyScore": float(anomaly_score),
        "model": "Isolation Forest"
    }


@app.route("/health", methods=["GET"])
def health():

    return {
        "status": "ONLINE",
        "service": "SmartLog Local ML AI",
        "model": "Isolation Forest"
    }


if __name__ == "__main__":

    app.run(
        host="127.0.0.1",
        port=5000
    )