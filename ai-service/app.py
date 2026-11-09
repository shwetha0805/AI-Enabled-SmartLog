from flask import Flask, request
import json
import os

import joblib
import numpy as np
from flask import Flask, request

app = Flask(__name__)

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

MODEL_PATH = os.path.join(BASE_DIR, "model", "isolation_forest.joblib")
VECTORIZER_PATH = os.path.join(BASE_DIR, "model", "vectorizer.joblib")

CANDIDATE_MODEL_PATH = os.path.join(
    BASE_DIR, "candidate_model", "isolation_forest_candidate.joblib"
)

CANDIDATE_METADATA_PATH = os.path.join(
    BASE_DIR, "candidate_model", "metadata.json"
)

model = joblib.load(MODEL_PATH)
vectorizer = joblib.load(VECTORIZER_PATH)

candidate_model = joblib.load(CANDIDATE_MODEL_PATH)

with open(CANDIDATE_METADATA_PATH, "r", encoding="utf-8") as f:
    candidate_metadata = json.load(f)

candidate_features = candidate_metadata["feature_names"]
candidate_count_features = candidate_metadata["count_features_log_transformed"]
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


    return {
        "anomaly": bool(anomaly),
        "risk": risk,
        "eventCode": event_code,
        "eventType": event_type,
        "reason": reason,
        "anomalyScore": float(anomaly_score),
        "model": "Isolation Forest"
    }

@app.route("/analyze-window", methods=["POST"])
def analyze_window():

    data = request.get_json() or {}

    try:
        features = []

        for name in candidate_features:
            value = float(data.get(name, 0))

            if not np.isfinite(value):
                return {"error": f"Invalid feature value: {name}"}, 400

            if value < 0:
                return {"error": f"Negative feature value: {name}"}, 400

            if name in candidate_count_features:
                value = np.log1p(value)

            features.append(value)

        X = np.array(features, dtype=float).reshape(1, -1)

        prediction = candidate_model.predict(X)[0]
        anomaly_score = candidate_model.decision_function(X)[0]

        return {
            "anomaly": bool(prediction == -1),
            "prediction": "ANOMALY" if prediction == -1 else "NORMAL",
            "anomalyScore": float(anomaly_score),
            "model": "Candidate Isolation Forest",
            "featureCount": len(candidate_features)
        }

    except (ValueError, TypeError) as e:
        return {"error": str(e)}, 400


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